# -*- coding: utf-8 -*-
"""把当前构建发布到 GitHub Releases。

与同目录的 release_gitee.py 是**姊妹脚本**，职责一致、流程一致，只是换了服务端。
两个都发，App 的双源更新才能真正生效（目前 GitHub 备源一直是空的）。

用法：
    python release_github.py --notes "本次更新内容"
    python release_github.py --dry-run
    python release_github.py --prune 3

令牌来源（按优先级）：
    1. 环境变量 GITHUB_TOKEN
    2. 本目录下的 .github-token 文件（已 gitignore）

⚠️ 权限：令牌需要 `repo` 权限（写发行版 + 传附件）。
   本机若已用 git 登录过 GitHub，可用
   `printf "protocol=https\\nhost=github.com\\n\\n" | git credential fill`
   取出凭据里的 password（gho_ 开头的 OAuth 令牌）当作 GITHUB_TOKEN 使用。
"""
import argparse
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request

# ---------------------------------------------------------------- 路径 / 常量

HERE = os.path.dirname(os.path.abspath(__file__))
SOURCE_KT = os.path.join(HERE, "app", "src", "main", "java",
                         "io", "github", "molishadaze", "weijing", "data", "AppUpdateSource.kt")
GRADLE_KTS = os.path.join(HERE, "app", "build.gradle.kts")
APK = os.path.join(HERE, "app", "build", "outputs", "apk", "release", "app-release.apk")
TOKEN_FILE = os.path.join(HERE, ".github-token")

API = "https://api.github.com"
TIMEOUT = 300          # 上传 21MB 包，别用短超时

# 必须与 AppUpdateSource.kt 的 TAG_PATTERN 一致 —— 不一致就会出现
# 「发布成功、App 却永远认不出这个版本」这种最难查的故障。
TAG_PATTERN = re.compile(r"^v?(\d{1,3})\.(\d{1,2})(?:\.(\d{1,2}))?$")


# ---------------------------------------------------------------- 小工具

def die(msg):
    print("\n[×] " + msg)
    sys.exit(1)


def ok(msg):
    print("[✓] " + msg)


def info(msg):
    print("    " + msg)


def scrub(text):
    """把令牌从任何要打印的文本里抹掉 —— 出错时服务端可能把请求原样回显。"""
    token = read_token(missing_ok=True)
    if token and len(token) > 8:
        return str(text).replace(token, "***")
    return str(text)


def read_token(missing_ok=False):
    token = os.environ.get("GITHUB_TOKEN", "").strip()
    if token:
        return token
    if os.path.isfile(TOKEN_FILE):
        try:
            with open(TOKEN_FILE, "r", encoding="utf-8") as f:
                token = f.read().strip()
        except OSError as e:
            die("读不了 %s：%s" % (TOKEN_FILE, e))
    if token:
        return token
    # 兜底：从 Windows 凭据管理器 / git credential 里取
    if not missing_ok:
        try:
            out = subprocess.run(
                ["git", "credential", "fill"],
                input="protocol=https\nhost=github.com\n\n",
                capture_output=True, text=True, encoding="utf-8", timeout=20)
            for line in out.stdout.splitlines():
                if line.startswith("password="):
                    return line.split("=", 1)[1].strip()
        except Exception:
            pass
        die("找不到 GitHub 令牌。请设置环境变量 GITHUB_TOKEN，"
            "或把令牌写进 %s（需要 repo 权限）" % TOKEN_FILE)
    return ""


def http(method, path, token, query=None, json_body=None, raw_data=None, raw_headers=None):
    """发一个 API 请求。返回 (status, 解析后的 JSON 或原始文本)。"""
    q = dict(query or {})
    url = API + path + ("?" + urllib.parse.urlencode(q) if q else "")

    headers = {
        "Authorization": "Bearer " + token,
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        # GitHub 强制要求 User-Agent，缺了直接 403。
        "User-Agent": "Weijing-Release-Script",
    }
    if raw_headers:
        headers.update(raw_headers)

    data = None
    if json_body is not None:
        data = json.dumps(json_body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    elif raw_data is not None:
        data = raw_data

    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT) as resp:
            body = resp.read()
            status = resp.status
    except urllib.error.HTTPError as e:
        body = e.read()
        status = e.code
    except urllib.error.URLError as e:
        die("网络请求失败：%s\n（%s 在国内可能受限，请确认代理或改用 Gitee 源）"
            % (scrub(e), url))

    if not body:
        return status, None
    try:
        return status, json.loads(body.decode("utf-8"))
    except (ValueError, UnicodeDecodeError):
        return status, body.decode("utf-8", "replace")


# ---------------------------------------------------------------- 读工程信息

def read_github_repo():
    """从 AppUpdateSource.kt 里读 GITHUB_REPO，避免脚本里另写一份。"""
    try:
        with open(SOURCE_KT, "r", encoding="utf-8") as f:
            text = f.read()
    except OSError as e:
        die("读不了 AppUpdateSource.kt：%s" % e)

    m = re.search(r'GITHUB_REPO\s*=\s*"([^"]*)"', text)
    if not m or not m.group(1).strip():
        die("AppUpdateSource.kt 里的 GITHUB_REPO 是空的。\n"
            "    留空 = 不启用 GitHub 源，先填上仓库路径（形如 用户名/仓库名）再发版。")
    return m.group(1).strip()


def read_version():
    """从 build.gradle.kts 读 versionCode / versionName。"""
    try:
        with open(GRADLE_KTS, "r", encoding="utf-8") as f:
            text = f.read()
    except OSError as e:
        die("读不了 build.gradle.kts：%s" % e)

    code_m = re.search(r"^\s*versionCode\s*=\s*(\d+)", text, re.M)
    name_m = re.search(r'^\s*versionName\s*=\s*"([^"]+)"', text, re.M)
    if not code_m or not name_m:
        die("在 build.gradle.kts 里找不到 versionCode / versionName")
    return int(code_m.group(1)), name_m.group(1).strip()


def check_version(code, name):
    """校验 versionCode 与 versionName 自洽。"""
    m = re.fullmatch(r"(\d{1,3})\.(\d{1,2})(?:\.(\d{1,2}))?", name)
    if not m:
        die("versionName「%s」不符合 major.minor[.patch] 形式，App 的版本解析器认不出来。" % name)
    major = int(m.group(1))
    minor = int(m.group(2))
    patch = int(m.group(3) or 0)
    expect = major * 10000 + minor * 100 + patch
    if expect != code:
        die("versionCode 与 versionName 不自洽：\n"
            "    versionName = %s  →  按规则应为 versionCode = %d\n"
            "    但文件里写的是 %d\n"
            "    只改 name 不改 code 等于没发新版。" % (name, expect, code))
    ok("版本自洽：%s / versionCode %d" % (name, code))
    return "v" + name


def check_apk(version_name):
    if not os.path.isfile(APK):
        die("找不到 APK：%s\n    先跑 `./gradlew :app:assembleRelease`。" % APK)
    size = os.path.getsize(APK)
    if size < 1024 * 1024:
        die("APK 只有 %d 字节，看着不像正常产物，拒绝上传。" % size)
    ok("APK 就绪：%.2f MB" % (size / 1024.0 / 1024.0))
    return size


# ---------------------------------------------------------------- 发布流程

def get_repo_info(repo, token):
    status, data = http("GET", "/repos/" + repo, token)
    if status != 200:
        die("拿不到仓库信息（HTTP %d）：%s\n"
            "    确认仓库存在、公开、且令牌有 repo 权限。" % (status, scrub(data)))
    return data


def list_releases(repo, token):
    status, data = http("GET", "/repos/%s/releases" % repo, token, query={"per_page": 100})
    if status != 200:
        die("拉发行版列表失败（HTTP %d）：%s" % (status, scrub(data)))
    return data if isinstance(data, list) else []


def find_by_tag(releases, tag):
    for r in releases:
        if r.get("tag_name") == tag:
            return r
    return None


def create_release(repo, tag, title, notes, token, branch):
    status, data = http("POST", "/repos/%s/releases" % repo, token, json_body={
        "tag_name": tag,
        "name": title,
        "body": notes or "",
        "draft": False,
        "prerelease": False,
        # 🚨 必须显式给 target_commitish，且要读仓库真实默认分支。
        "target_commitish": branch,
    })
    if status not in (200, 201):
        die("建发行版失败（HTTP %d）：%s" % (status, scrub(data)))
    return data


def upload_asset(repo, release, apk_path, upload_name, token):
    """上传 APK 作为发行版附件。

    🚨 用 uploads.github.com 域名（走 release-assets 链路），不要用 api.github.com。
    """
    url = release["upload_url"]
    url = url[:url.index("{")]  # 去掉 {?name,label} 模板

    with open(apk_path, "rb") as f:
        blob = f.read()

    q = urllib.parse.urlencode({"name": upload_name})
    req = urllib.request.Request(
        url + "?" + q, data=blob, method="POST",
        headers={
            "Authorization": "Bearer " + token,
            "Content-Type": "application/vnd.android.package-archive",
            "Content-Length": str(len(blob)),
            "Accept": "application/vnd.github+json",
            "User-Agent": "Weijing-Release-Script",
        })
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", "replace")
        try:
            return e.code, json.loads(body)
        except ValueError:
            return e.code, body
    except urllib.error.URLError as e:
        die("上传失败：%s" % scrub(e))


def delete_asset(repo, asset_id, token):
    status, _ = http("DELETE", "/repos/%s/releases/assets/%d" % (repo, asset_id), token)
    return status in (204, 200)


def main():
    ap = argparse.ArgumentParser(description="把当前构建发布到 GitHub Releases")
    ap.add_argument("--notes", default=None, help="发布说明；会原样显示成 App 里的更新内容")
    ap.add_argument("--notes-file", default=None, help="从文件读发布说明")
    ap.add_argument("--title", default=None, help="发行版标题，默认 `未竟 v<版本>`")
    ap.add_argument("--prune", type=int, default=0, metavar="N",
                    help="只保留最近 N 个发行版的附件，更旧的删掉")
    ap.add_argument("--dry-run", action="store_true", help="只做本地检查，不发任何请求")
    args = ap.parse_args()

    print("\n=== 未竟 · GitHub 发版 ===\n")

    repo = read_github_repo()
    ok("目标仓库：%s" % repo)

    code, name = read_version()
    tag = check_version(code, name)

    size = check_apk(name)

    notes = args.notes
    if args.notes_file:
        try:
            with open(args.notes_file, "r", encoding="utf-8") as f:
                notes = f.read().strip()
        except OSError as e:
            die("读不了发布说明文件：%s" % e)
    if notes is None:
        notes = ""

    title = args.title or ("未竟 v%s" % name)

    if args.dry_run:
        info("--- dry-run，以下内容不会真的发出 ---")
        info("tag         : %s" % tag)
        info("标题        : %s" % title)
        info("附件        : app-release.apk (%.2f MB)" % (size / 1024.0 / 1024.0))
        info("发布说明    : %s" % (notes[:60] + ("…" if len(notes) > 60 else "") or "(空)"))
        info("prune       : %s" % (args.prune if args.prune else "不清理"))
        print()
        ok("本地检查全部通过。")
        return

    token = read_token()

    repo_info = get_repo_info(repo, token)
    branch = repo_info.get("default_branch") or "main"
    # 若仓库被改过名，GitHub 会返回新名字，这里同步一下，避免后续 404。
    real_repo = repo_info.get("full_name", repo)
    if real_repo != repo:
        info("注意：%s 已更名为 %s，本次用新名字。" % (repo, real_repo))
        repo = real_repo
    info("默认分支：%s" % branch)

    releases = list_releases(repo, token)
    existing = find_by_tag(releases, tag)

    if existing:
        ok("发行版 %s 已存在，复用它。" % tag)
        release = existing
    else:
        release = create_release(repo, tag, title, notes, token, branch)
        ok("已创建发行版 %s" % tag)

    # 🚨 附件名必须显式指定。不指定的话 GitHub 会用文件名，
    # 而「是否已存在」的判断就永远对不上 → 每跑一次多传一份。
    asset_name = "weijing-%s.apk" % name
    assets = release.get("assets") or []
    if any(a.get("name") == asset_name for a in assets):
        ok("附件 %s 已存在，跳过上传。" % asset_name)
    else:
        status, data = upload_asset(repo, release, APK, asset_name, token)
        if status not in (200, 201):
            die("上传附件失败（HTTP %d）：%s" % (status, scrub(data)))
        ok("附件已上传：%s" % asset_name)

    # 回读校验：确认附件真的挂上去了，且大小对得上。
    status, data = http("GET", "/repos/%s/releases/tags/%s" % (repo, tag), token)
    if status != 200:
        die("回读校验失败（HTTP %d）：%s" % (status, scrub(data)))
    got = [a for a in (data.get("assets") or []) if a.get("name") == asset_name]
    if not got:
        die("回读校验失败：发行版里找不到 %s" % asset_name)
    remote_size = got[0].get("size", 0)
    if remote_size != size:
        die("回读校验失败：远端 %d 字节，本地 %d 字节。" % (remote_size, size))
    ok("回读校验通过：%d 字节" % remote_size)

    print()
    info("发布页：%s" % ("https://github.com/%s/releases/tag/%s" % (repo, tag)))
    info("App 内会读到：versionCode %d → %s" % (code, name))

    # 清理旧附件（GitHub 没有总量上限，但清单太长不好看）
    if args.prune > 0:
        print()
        releases = list_releases(repo, token)
        # GitHub 的 /releases 是**降序**（新在前），与 Gitee 相反。
        for r in releases[args.prune:]:
            for a in r.get("assets") or []:
                if delete_asset(repo, a["id"], token):
                    info("已删除旧附件：%s / %s" % (r.get("tag_name"), a.get("name")))

    print()
    ok("完成。")


if __name__ == "__main__":
    main()

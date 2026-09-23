#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""一键把当前构建发到 Gitee 发行版（应用内自更新的更新源）。

用法（在 android/ 目录下）：
    python release_gitee.py                      # 用默认说明发布
    python release_gitee.py --notes "修复了 XXX"  # 发布说明（会原样显示成 App 里的更新内容）
    python release_gitee.py --notes-file notes.md
    python release_gitee.py --dry-run            # 只做检查，不碰网络
    python release_gitee.py --prune 3            # 发完顺手清理旧发行版的附件，只留最近 3 个

令牌（二选一，**不要**写进这个文件）：
    1. 环境变量  GITEE_TOKEN=xxxx python release_gitee.py
    2. 文件      android/.gitee-token（已 gitignore），里面只放令牌本身
   令牌在 Gitee「头像 → 设置 → 私人令牌 → 生成新令牌」生成，勾 **projects** 权限即可。

本脚本刻意只依赖 Python 标准库 —— 本机没有 requests，也不该为发版引入依赖。
"""

import argparse
import json
import os
import re
import sys
import uuid
import zipfile
import urllib.error
import urllib.parse
import urllib.request

# ---------------------------------------------------------------- 路径 / 常量

HERE = os.path.dirname(os.path.abspath(__file__))
SOURCE_KT = os.path.join(HERE, "app", "src", "main", "java",
                         "io", "github", "molishadaze", "weijing", "data", "AppUpdateSource.kt")
GRADLE_KTS = os.path.join(HERE, "app", "build.gradle.kts")
APK = os.path.join(HERE, "app", "build", "outputs", "apk", "release", "app-release.apk")
TOKEN_FILE = os.path.join(HERE, ".gitee-token")

API = "https://gitee.com/api/v5"
TIMEOUT = 120          # 上传 21MB 包，别用短超时

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
    token = os.environ.get("GITEE_TOKEN", "").strip()
    if token:
        return token
    if os.path.isfile(TOKEN_FILE):
        try:
            with open(TOKEN_FILE, "r", encoding="utf-8") as f:
                token = f.read().strip()
        except OSError as e:
            die("读不了 %s：%s" % (TOKEN_FILE, e))
    if not token and not missing_ok:
        die("找不到 Gitee 令牌。请设置环境变量 GITEE_TOKEN，"
            "或把令牌写进 %s\n（令牌在 Gitee「设置 → 私人令牌」生成，勾 projects 权限）" % TOKEN_FILE)
    return token


def http(method, path, token, query=None, json_body=None, multipart=None):
    """发一个 API 请求。返回 (status, 解析后的 JSON 或原始文本)。"""
    q = dict(query or {})
    q["access_token"] = token
    url = API + path + "?" + urllib.parse.urlencode(q)

    headers = {"User-Agent": "Weijing-Release-Script"}
    data = None
    if json_body is not None:
        data = json.dumps(json_body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json; charset=utf-8"
    elif multipart is not None:
        boundary, data = multipart
        headers["Content-Type"] = "multipart/form-data; boundary=" + boundary

    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT) as resp:
            raw = resp.read().decode("utf-8", "ignore")
            status = resp.status
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "ignore")
        status = e.code
    except Exception as e:                     # noqa: BLE001 —— 网络问题一律当失败处理
        return 0, "%s: %s" % (type(e).__name__, e)

    if status == 204 or not raw.strip():
        return status, None
    try:
        return status, json.loads(raw)
    except ValueError:
        return status, raw


def build_multipart(fields, file_field, file_path, mime, upload_name=None):
    """手搓 multipart —— 标准库没有现成的，而这是唯一需要它的地方。

    🚨 [upload_name] 必须显式给：附件名取的是**本地构建产物**的文件名（`app-release.apk`），
    直接用它会让「已存在则跳过」的判断（按 `weijing-<版本>.apk` 匹配）永远匹配不上，
    重跑一次就多传一份 —— 慢慢把 Gitee 的 1GB 附件额度撑满。
    """
    boundary = "----WeijingReleaseBoundary" + uuid.uuid4().hex
    buf = bytearray()
    for k, v in fields.items():
        buf += ("--%s\r\n" % boundary).encode()
        buf += ('Content-Disposition: form-data; name="%s"\r\n\r\n' % k).encode()
        buf += str(v).encode("utf-8") + b"\r\n"

    filename = upload_name or os.path.basename(file_path)
    buf += ("--%s\r\n" % boundary).encode()
    buf += ('Content-Disposition: form-data; name="%s"; filename="%s"\r\n'
            % (file_field, filename)).encode("utf-8")
    buf += ("Content-Type: %s\r\n\r\n" % mime).encode()
    with open(file_path, "rb") as f:
        buf += f.read()
    buf += b"\r\n"
    buf += ("--%s--\r\n" % boundary).encode()
    return boundary, bytes(buf)


# ---------------------------------------------------------------- 读取项目配置

def read_gitee_repo():
    """从 AppUpdateSource.kt 读仓库路径 —— 单一真相源，避免脚本和 App 各写一份然后走偏。"""
    try:
        src = open(SOURCE_KT, "r", encoding="utf-8").read()
    except OSError as e:
        die("读不了 %s：%s" % (SOURCE_KT, e))
    m = re.search(r'GITEE_REPO\s*=\s*"([^"]*)"', src)
    if not m:
        die("在 AppUpdateSource.kt 里找不到 GITEE_REPO 常量")
    repo = m.group(1).strip()
    if not repo:
        die("AppUpdateSource.kt 里的 GITEE_REPO 还是空的。\n"
            "    请先填成 `用户名/仓库名`（例如 weijingzhishi/weijing）再发版，")
    if repo.count("/") != 1:
        die("GITEE_REPO 必须形如 `用户名/仓库名`，当前是：%r" % repo)
    return repo


def read_version():
    try:
        src = open(GRADLE_KTS, "r", encoding="utf-8").read()
    except OSError as e:
        die("读不了 %s：%s" % (GRADLE_KTS, e))
    code = re.search(r"versionCode\s*=\s*(\d+)", src)
    name = re.search(r'versionName\s*=\s*"([^"]+)"', src)
    if not code or not name:
        die("在 build.gradle.kts 里找不到 versionCode / versionName")
    return int(code.group(1)), name.group(1).strip()


def check_version(code, name):
    """把 App 侧那套版本号规则在发版前先跑一遍 —— 在这里失败，好过在用户手机上失败。"""
    m = TAG_PATTERN.match("v" + name)
    if not m:
        die("versionName %r 不符合 App 的 tag 规则（%s）。\n"
            "    App 认不出这个版本号 → 用户永远收不到更新。"
            % (name, TAG_PATTERN.pattern))
    major, minor, patch = int(m.group(1)), int(m.group(2)), int(m.group(3) or 0)
    if minor > 99 or patch > 99:
        die("versionName %r 的 minor/patch 超过两位，编码会串号。" % name)
    expect = major * 10000 + minor * 100 + patch
    if expect != code:
        die("versionCode 与 versionName 对不上：build.gradle.kts 里是 %d / %s，\n"
            "    按规则 major*10000+minor*100+patch 应该是 %d。\n"
            "    ⚠️ 只改 name 不改 code = 没发新版。" % (code, name, expect))
    return "v" + name


def check_apk():
    if not os.path.isfile(APK):
        die("找不到 APK：%s\n    先跑：gradle -p . :app:assembleRelease" % APK)
    size = os.path.getsize(APK)
    if size <= 0:
        die("APK 是空文件：%s" % APK)
    try:
        z = zipfile.ZipFile(APK)
        names = z.namelist()
        if z.testzip() is not None:
            die("APK 的 zip 结构损坏，先重新构建")
        if "AndroidManifest.xml" not in names:
            die("这个文件里没有 AndroidManifest.xml，不像是 APK")
    except zipfile.BadZipFile:
        die("这个文件不是合法 zip，不像是 APK")
    if size > 100 * 1024 * 1024:
        die("APK 有 %.1fMB，超过 Gitee 单附件 100MB 上限" % (size / 1048576))
    return size


# ---------------------------------------------------------------- Gitee 操作

def list_releases(repo, token):
    """列出全部发行版。⚠️ 接口是**升序**（旧在前），且没有可用的倒序参数，所以自己排。"""
    out, page = [], 1
    while True:
        st, data = http("GET", "/repos/%s/releases" % repo, token,
                        query={"per_page": 100, "page": page})
        if st != 200:
            die("列出发行版失败（HTTP %s）：%s" % (st, scrub(data)))
        if not isinstance(data, list) or not data:
            break
        out.extend(data)
        if len(data) < 100:
            break
        page += 1
    return out


def list_attachments(repo, release_id, token):
    st, data = http("GET", "/repos/%s/releases/%s/attach_files" % (repo, release_id), token)
    if st != 200:
        return []
    return data if isinstance(data, list) else []


def find_by_tag(releases, tag):
    for r in releases:
        if (r.get("tag_name") or "").strip() == tag:
            return r
    return None


def repo_default_branch(repo, token):
    """取仓库的默认分支。

    🚨 不能写死 `master`：Gitee 新建仓库默认是 `main`，而发行版的 tag 是打在
    `target_commitish` 上的 —— 写错会直接导致「建 tag 失败」，
    报错却只说 tag 有问题，很容易往别处查。
    """
    st, data = http("GET", "/repos/%s" % repo, token)
    if st == 200 and isinstance(data, dict):
        branch = (data.get("default_branch") or "").strip()
        if branch:
            return branch
    return "main"


def create_release(repo, tag, title, notes, token, branch):
    body = {
        "tag_name": tag,
        "name": title,
        "body": notes,
        "target_commitish": branch,
        "prerelease": False,
    }
    # Gitee 对 POST 的编码方式在不同版本上表现不一致：先按 JSON 发，
    # 不是 2xx 再退回表单编码。两条路都试过才敢说「发不上去」。
    st, data = http("POST", "/repos/%s/releases" % repo, token, json_body=body)
    if not (200 <= st < 300):
        first = scrub(data)
        st2, data2 = http("POST", "/repos/%s/releases" % repo, token,
                          query={"tag_name": tag, "name": title, "body": notes,
                                 "target_commitish": branch, "prerelease": "false"})
        if not (200 <= st2 < 300):
            die("创建发行版失败。\n    JSON 方式 HTTP %s：%s\n    表单方式 HTTP %s：%s"
                % (st, first, st2, scrub(data2)))
        data = data2
        st = st2
    if not isinstance(data, dict) or not data.get("id"):
        die("创建发行版返回了异常内容：%s" % scrub(data))
    return data


def upload_attachment(repo, release_id, tag, apk_path, upload_name, token):
    fields = {"owner": repo.split("/")[0], "repo": repo.split("/")[1],
              "release_id": str(release_id)}
    mp = build_multipart(fields, "file", apk_path,
                         "application/vnd.android.package-archive",
                         upload_name=upload_name)
    st, data = http("POST", "/repos/%s/releases/%s/attach_files" % (repo, release_id),
                    token, multipart=mp)
    if not (200 <= st < 300):
        die("上传附件失败（HTTP %s）：%s" % (st, scrub(data)))
    return data


def delete_attachment(repo, release_id, file_id, token):
    st, data = http("DELETE",
                    "/repos/%s/releases/%s/attach_files/%s" % (repo, release_id, file_id),
                    token)
    return st in (200, 204), st, data


def is_uploaded(asset_url):
    """用户上传的附件与「自动生成的源码包」靠 URL 区分：源码包走 /archive/refs/。"""
    return "/releases/download/" in (asset_url or "")


# ---------------------------------------------------------------- 主流程

def main():
    ap = argparse.ArgumentParser(description="把当前构建发布到 Gitee 发行版")
    ap.add_argument("--notes", default=None, help="发布说明；会原样显示成 App 里的更新内容")
    ap.add_argument("--notes-file", default=None, help="从文件读发布说明")
    ap.add_argument("--title", default=None, help="发行版标题，默认 `未竟 v<版本>`")
    ap.add_argument("--prune", type=int, default=0, metavar="N",
                    help="发布后清理旧发行版的附件，只保留最近 N 个版本（0=不清理）")
    ap.add_argument("--dry-run", action="store_true", help="只做本地检查，不发任何请求")
    args = ap.parse_args()

    print("=== 未竟 · Gitee 发版 ===\n")

    repo = read_gitee_repo()
    code, name = read_version()
    tag = check_version(code, name)
    apk_size = check_apk()

    ok("仓库      %s" % repo)
    ok("版本      %s（versionCode %d）" % (name, code))
    ok("tag       %s" % tag)
    ok("APK       %.2f MB" % (apk_size / 1048576))

    if args.dry_run:
        print("\n[dry-run] 本地检查全部通过，未发起任何网络请求。")
        return

    token = read_token()
    info("令牌      已读取（%d 字符，不回显）" % len(token))

    # --- 1. 发行版（已存在则复用，保证脚本可重复执行）---
    releases = list_releases(repo, token)
    existing = find_by_tag(releases, tag)
    if existing:
        release = existing
        info("发行版 %s 已存在（id=%s），复用它" % (tag, release.get("id")))
    else:
        notes = args.notes
        if args.notes_file:
            try:
                notes = open(args.notes_file, "r", encoding="utf-8").read()
            except OSError as e:
                die("读不了 %s：%s" % (args.notes_file, e))
        if notes is None:
            notes = "本次更新内容待补充。\n\n（发版时可加 --notes \"...\" 填写，它会原样显示在 App 的更新弹窗里。）"
        title = args.title or ("未竟 v" + name)
        branch = repo_default_branch(repo, token)
        info("默认分支   %s" % branch)
        release = create_release(repo, tag, title, notes, token, branch)
        ok("已创建发行版 %s（id=%s）" % (tag, release.get("id")))

    release_id = release.get("id")

    # --- 2. 附件 ---
    apk_name = "weijing-%s.apk" % name
    atts = list_attachments(repo, release_id, token)
    apk_atts = [a for a in atts if (a.get("name") or "").lower().endswith(".apk")]
    already = [a for a in apk_atts if (a.get("name") or "") == apk_name]
    if already:
        info("附件 %s 已存在（id=%s），跳过上传" % (apk_name, already[0].get("id")))
    else:
        # 除了预期名之外的 APK 附件一律先提醒：多半是历史遗留的重复包，
        # 留着既占额度，App 选包时也可能选到它。
        for a in apk_atts:
            info("⚠️ 这条发行版上已存在另一个 APK 附件：%s（id=%s）"
                 % (a.get("name"), a.get("id")))
            info("   它不是本脚本的命名规范，确认无用就去网页删掉，别让它一直占额度")
        upload_attachment(repo, release_id, tag, APK, apk_name, token)
        ok("已上传附件 %s" % apk_name)

    # --- 3. 回读校验（这一步才是真正证明「App 能拿到」）---
    atts = list_attachments(repo, release_id, token)
    uploaded = [a for a in atts if is_uploaded(a.get("browser_download_url"))]
    if not uploaded:
        die("回读校验失败：这条发行版上看不到任何「用户上传的附件」。\n"
            "    App 会只看到两个自动源码包 → 检查更新永远返回「已是最新」。")
    print()
    ok("回读校验通过，附件 %d 个：" % len(uploaded))
    for a in uploaded:
        info("- %s" % a.get("name"))
        info("  %s" % a.get("browser_download_url"))

    print("\n=== 下一步 ===")
    print("  1. 装上一个 versionCode 更小的包，打开 App 看更新弹窗是否出现。")
    print("  2. ⚠️ 你正在用的旧版本里没有更新功能，**这一版仍需手动发一次**给朋友；")
    print("     从下一个版本起才会自动更新。")
    print("  3. Gitee 免费版仓库附件总量上限 1GB：用 --prune 3 只留最近 3 个版本。")

    if args.prune > 0:
        print()
        ordered = sorted(releases + ([release] if not existing else []),
                         key=lambda r: r.get("created_at") or "", reverse=True)
        # 去重（刚创建的 release 可能已经从列表里读到了）
        seen, uniq = set(), []
        for r in ordered:
            rid = r.get("id")
            if rid in seen:
                continue
            seen.add(rid)
            uniq.append(r)

        keep = uniq[:args.prune]
        drop = uniq[args.prune:]
        info("保留最近 %d 个版本，清理其余 %d 个的附件" % (len(keep), len(drop)))
        removed = 0
        for r in drop:
            for a in list_attachments(repo, r.get("id"), token):
                if not is_uploaded(a.get("browser_download_url")):
                    continue
                good, st, data = delete_attachment(repo, r.get("id"), a.get("id"), token)
                if good:
                    removed += 1
                    info("  已删 %s / %s" % (r.get("tag_name"), a.get("name")))
                else:
                    info("  删不掉 %s / %s（HTTP %s）—— 手动去网页删一下"
                         % (r.get("tag_name"), a.get("name"), st))
        ok("清理完成，共删除 %d 个旧附件" % removed)


if __name__ == "__main__":
    main()

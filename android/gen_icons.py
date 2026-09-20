# -*- coding: utf-8 -*-
"""从用户提供的日间/夜间 JPG 生成安卓启动图标资源（自适应图标 + 传统兜底）。

日间图：clipboard-2026-09-19T15-34-51-956Z-0b24b014.jpg（白底红日 + 蓝波浪）
夜间图：clipboard-2026-09-19T15-34-51-957Z-2dd68481.jpg（深蓝底金月 + 蓝波浪）

⚠️ 自适应图标（adaptive icon）的几何规则 —— 本文件唯一容易翻车的地方
--------------------------------------------------------------------------------
    画布 108dp x 108dp，但启动器**只显示中间 72dp x 72dp**；
    「任何蒙版形状下都保证不被裁掉」的安全区还要小，只有**中间 66dp 直径的圆**。
    所以前景层里的图案必须缩到 ~66dp，四周留白 ——
    留白不是审美取舍，是平台硬规则。

    第一版把整张源图「全出血」铺满 108dp，图案实测宽 **85.7dp**，
    左右各被 72dp 的显示区切掉 **6.8dp（约 8%）** ——
    用户看到的就是「主体太大、留白太少、像被挡住了」。

    注意：**「全出血」是背景层的规则**（背景层被蒙版裁掉无所谓），
    前景层恰恰相反，必须往中间收。两层别搞混 —— 这是本次翻车的原因。

第二版（当前）在此之上多做了两件事，都是被预览图抓出来的：
  1. 图案按**外接框中心**居中，不是按源图中心 —— 源图里图案本身偏上（离底边更远）。
  2. 先把**圆角底板外圈那层薄底色裁掉再缩放**。不裁的话，缩放后底板边缘会落进
     72dp 显示区：日间白→白（差 2/255）看不出来，夜间外圈 #051338 与底板 #091749
     差 11/255，圆形蒙版顶部会露出一条深色弧。这个只有渲染出来才看得见。

    改完别只看代码，务必渲染一遍预览核对：
    自适应图标的坑和 Compose 那次自绘图标一样 ——
    「参数看着对」不等于「屏幕上对」。
"""
from PIL import Image, ImageChops, ImageDraw
import os

DAY_SRC = r"C:\Users\admin\.workbuddy\clipboard-images\clipboard-2026-09-19T15-34-51-956Z-0b24b014.jpg"
NIGHT_SRC = r"C:\Users\admin\.workbuddy\clipboard-images\clipboard-2026-09-19T15-34-51-957Z-2dd68481.jpg"
RES = r"C:\Users\admin\Documents\trae_projects\lunwen\dailywork\android\app\src\main\res"

DENSITY = 4           # xxxhdpi：1dp = 4px
CANVAS_DP = 108       # 自适应图标画布（前景/背景层都是这个尺寸）
VISIBLE_DP = 72       # 启动器实际显示的区域，居中
SAFE_DP = 66          # 保证不被任何蒙版切到的安全圆直径
ART_DP = SAFE_DP      # 图案目标尺寸：正好卡在安全区（想更大改这里，上限 72）

ART_THR = 45          # 判定「图案」的色差阈值：要高于圆角底板与外围底色的差（夜图约 11~17）
PROBE_OFFSET = 45     # 取底色探针时距图案外接框的偏移（px）
TILE_THR = 3          # 判定「底板边界」的色差阈值（日图底板与外圈只差 2 → 判不出来就整张用）
TILE_RUN = 6          # 连续多少个像素超阈值才算边界，用来滤 JPEG 噪点
TILE_RADIUS = 0.25    # 抹圆角时用的半径（占裁切框短边比例；实测底板约 0.22，取大一点无害）

CANVAS_PX = CANVAS_DP * DENSITY
ART_PX = ART_DP * DENSITY


def _median(seq):
    return tuple(sorted(c[i] for c in seq)[len(seq) // 2] for i in range(3))


def content_bbox(im, bg, thr):
    """相对底色 bg 色差超过 thr 的内容外接框。"""
    diff = ImageChops.difference(im, Image.new("RGB", im.size, bg))
    r, g, b = diff.split()
    mask = ImageChops.lighter(ImageChops.lighter(r, g), b).point(lambda v: 255 if v > thr else 0)
    return mask.getbbox()


def patch_color(im, cx, cy, rad=6):
    W, H = im.size
    px = [im.getpixel((min(max(x, 0), W - 1), min(max(y, 0), H - 1)))
          for y in range(cy - rad, cy + rad + 1) for x in range(cx - rad, cx + rad + 1)]
    return _median(px)


def bg_probe_color(im, art):
    """采样图案四周（圆角底板内部、图案外部）的颜色，作为补白底色。

    故意取**四边中点**：那里不受圆角底板圆角的影响。
    """
    l, t, r, b = art
    cx, cy = (l + r) // 2, (t + b) // 2
    m = PROBE_OFFSET
    probes = [(cx, t - m), (cx, b + m), (l - m, cy), (r + m, cy)]
    return _median([patch_color(im, x, y) for x, y in probes]), probes


def _edge_scan(get, n, ref, thr, run):
    """从外往里走，返回第一个「连续 run 个像素与 ref 色差 > thr」的位置。"""
    hit = 0
    for i in range(n):
        c = get(i)
        if max(abs(c[k] - ref[k]) for k in range(3)) > thr:
            hit += 1
            if hit >= run:
                return i - run + 1
        else:
            hit = 0
    return None


def tile_bounds(im, thr=TILE_THR, run=TILE_RUN):
    """找圆角方形底板的外接框。

    只沿**过中心的两条中线**往两边扫 —— 圆角落在四个角上，不影响外接框。
    每侧用该侧自身的中点色作参考，避免外围底色本身有渐变时误判。
    底板与外围底色几乎同色时（日图只差 2/255）返回 None：那种情况裁不裁都看不出来。
    """
    W, H = im.size
    cx, cy = W // 2, H // 2
    left = _edge_scan(lambda i: im.getpixel((i, cy)), W, patch_color(im, 4, cy, 4), thr, run)
    right = _edge_scan(lambda i: im.getpixel((W - 1 - i, cy)), W, patch_color(im, W - 5, cy, 4), thr, run)
    top = _edge_scan(lambda i: im.getpixel((cx, i)), H, patch_color(im, cx, 4, 4), thr, run)
    bot = _edge_scan(lambda i: im.getpixel((cx, H - 1 - i)), H, patch_color(im, cx, H - 5, 4), thr, run)
    if None in (left, right, top, bot):
        return None
    return (left, top, W - 1 - right, H - 1 - bot)


def render_foreground(im, art_dp=ART_DP, crop_tile=True):
    """把源图变成一张 108dp 的自适应图标前景层（432x432 @xxxhdpi）。

    art_dp=None 复现旧版「全出血」行为，仅供预览对比用。
    """
    W, H = im.size
    outer = im.getpixel((0, 0))
    art = content_bbox(im, outer, ART_THR)
    if art is None:
        raise RuntimeError(f"没找到图案内容，检查 ART_THR={ART_THR}")
    pad, probes = bg_probe_color(im, art)

    tb = tile_bounds(im) if crop_tile else None
    src = im.crop(tb) if tb else im
    if tb:                       # 裁掉底板外圈后，图案坐标要跟着平移
        art = (art[0] - tb[0], art[1] - tb[1], art[2] - tb[0], art[3] - tb[1])
        # 裁出来的是圆角底板的**外接矩形**，四角仍带着外圈底色。
        # 不抹掉的话，这四角会落进 72dp 可见区的四角 ——
        # 圆/圆角方蒙版下看不到，但纯方形蒙版下会露出四片偏深的角。
        # 用圆角蒙版抹成补白底色即可（半径取大一点无害：那儿本来就只有背景）。
        keep = Image.new("L", src.size, 0)
        ImageDraw.Draw(keep).rounded_rectangle(
            (0, 0, src.size[0] - 1, src.size[1] - 1),
            radius=round(min(src.size) * TILE_RADIUS), fill=255)
        flat = Image.new("RGB", src.size, pad)
        flat.paste(src, (0, 0), keep)
        src = flat

    aw, ah = art[2] - art[0], art[3] - art[1]
    acx, acy = (art[0] + art[2]) / 2, (art[1] + art[3]) / 2
    s = (CANVAS_PX / src.size[0]) if art_dp is None else (art_dp * DENSITY / max(aw, ah))

    canvas = Image.new("RGB", (CANVAS_PX, CANVAS_PX), pad)
    scaled = src.resize((round(src.size[0] * s), round(src.size[1] * s)), Image.LANCZOS)
    off = (round(CANVAS_PX / 2 - acx * s), round(CANVAS_PX / 2 - acy * s))
    canvas.paste(scaled, off)

    meta = dict(pad=pad, probes=probes, tile=tb, art=art, art_px=(aw, ah), scale=s,
                offset=off, pasted=scaled.size, outer=outer)
    return canvas, meta


def build_foreground(im, name, **kw):
    """渲染 + 打自检信息（图案落位、贴图是否盖住可见区）。"""
    canvas, m = render_foreground(im, **kw)
    vis_lo = (CANVAS_PX - VISIBLE_DP * DENSITY) // 2
    vis_hi = (CANVAS_PX + VISIBLE_DP * DENSITY) // 2
    x0, y0 = m["offset"]
    x1, y1 = x0 + m["pasted"][0], y0 + m["pasted"][1]
    # 贴图不一定盖满可见区，但没盖到的地方是 pad 色补的 ——
    # 所以真正要验的是「露出补白的那几条边有多厚」，只要厚到看得见就说明裁多/缩多了。
    gaps = {"上": max(0, y0 - vis_lo), "下": max(0, vis_hi - y1),
            "左": max(0, x0 - vis_lo), "右": max(0, vis_hi - x1)}
    print(f"  [{name}] 外圈底色={m['outer']} 补白底色={m['pad']} 探针={m['probes']}")
    print(f"  [{name}] 圆角底板外接框={m['tile']} -> {'已裁掉外圈' if m['tile'] else '未检测到（与外围同色，无所谓）'}")
    print(f"  [{name}] 图案 {m['art_px'][0]}x{m['art_px'][1]}px = "
          f"{m['art_px'][0]/DENSITY:.1f}dp x {m['art_px'][1]/DENSITY:.1f}dp（铺满画布时的尺寸）")
    print(f"  [{name}] 缩放 {m['scale']:.4f}，贴图 {m['pasted']} @{m['offset']}，可见区 {vis_lo}..{vis_hi}")
    print(f"  [{name}] 可见区内露出的补白厚度(px)：{gaps}（补白色与底板同色，≤8px 看不出接缝）")
    print(f"  [{name}] 图案落位 {m['art_px'][0]*m['scale']/DENSITY:.1f}dp x {m['art_px'][1]*m['scale']/DENSITY:.1f}dp，"
          f"居中画布 ({CANVAS_PX/2}) ✓")
    return canvas


def main():
    day = Image.open(DAY_SRC).convert("RGB")
    night = Image.open(NIGHT_SRC).convert("RGB")
    print(f"源图 day={day.size} night={night.size}")
    if day.size != night.size:
        side = min(min(day.size), min(night.size))
        day, night = day.crop((0, 0, side, side)), night.crop((0, 0, side, side))
        print(f"尺寸不一致，已统一裁到 {side}x{side}")

    os.makedirs(os.path.join(RES, "mipmap-xxxhdpi"), exist_ok=True)
    os.makedirs(os.path.join(RES, "mipmap-night-xxxhdpi"), exist_ok=True)
    build_foreground(day, "day").save(os.path.join(RES, "mipmap-xxxhdpi", "ic_launcher_foreground.png"))
    build_foreground(night, "night").save(os.path.join(RES, "mipmap-night-xxxhdpi", "ic_launcher_foreground.png"))

    # ---- 传统 PNG 兜底（API<26 或部分场景；minSdk 29 后基本用不到）----
    # 传统图标本来就是整块出血的圆角方块，不套安全区规则。
    for dpi, px in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
        d = os.path.join(RES, "mipmap-" + dpi)
        os.makedirs(d, exist_ok=True)
        day.resize((px, px), Image.LANCZOS).save(os.path.join(d, "ic_launcher.png"))
        mask = Image.new("L", (px * 4, px * 4), 0)
        ImageDraw.Draw(mask).ellipse((0, 0, px * 4 - 1, px * 4 - 1), fill=255)
        rnd = day.resize((px, px), Image.LANCZOS).convert("RGBA")
        rnd.putalpha(mask.resize((px, px), Image.LANCZOS))
        rnd.save(os.path.join(d, "ic_launcher_round.png"))
    print("legacy mipmaps done")

    # ---- 夜间底色资源（前景层已铺满画布，底色只在极端兜底时才可见）----
    night_bg = "#%02X%02X%02X" % night.getpixel((8, 8))
    os.makedirs(os.path.join(RES, "values-night"), exist_ok=True)
    with open(os.path.join(RES, "values-night", "colors.xml"), "w", encoding="utf-8") as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n')
        f.write('    <!-- 夜间模式启动图标底色，取自用户提供的夜间图标外圈背景 -->\n')
        f.write('    <color name="ic_launcher_background">%s</color>\n' % night_bg)
        f.write('</resources>\n')
    print("values-night/colors.xml done, bg =", night_bg)


if __name__ == "__main__":
    main()

# -*- coding: utf-8 -*-
"""把模拟器实测截图拼成 README 用的展示图。

用法：先用 adb 截好四张 1920x1080 的图放进 .iconwork/shots/，再跑本脚本。
输出：docs/screenshots.png（两行两列，带页面名标签）。

依赖：Pillow（本机隔离环境 C:/Users/admin/.workbuddy/binaries/python/envs/default 已装）。
注意：这是**一次性**的文档生成脚本，不参与构建，也不进 APK。
"""
from PIL import Image, ImageDraw
import os

ROOT = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(os.path.dirname(ROOT), ".iconwork", "shots")
OUT = os.path.join(os.path.dirname(ROOT), "docs")

# 浅色（宣纸/雾霭系，默认「跟随系统」在白天就是这个样子）
SHOTS_LIGHT = [
    ("01-today.png", "今日打卡"),
    ("02-calendar.png", "打卡日历"),
    ("03-counters.png", "独立计数器"),
    ("04-settings.png", "管理中心"),
]

# 暗色（墨曜主题，手动切过去截的）
SHOTS_DARK = [
    ("09-dark-today.png", "今日打卡 · 墨曜"),
    ("10-dark-counters.png", "独立计数器 · 墨曜"),
]

W = 660          # 每张缩略图宽度
GAP = 16
PAD = 20
LABEL_H = 34
BG_LIGHT = (238, 240, 243)
BG_DARK = (24, 24, 27)


def render(shots, cols, bg, label_fg, out_name) -> None:
    cells = []
    for fn, label in shots:
        im = Image.open(os.path.join(SRC, fn)).convert("RGB")
        h = round(im.height * W / im.width)
        cells.append((im.resize((W, h), Image.LANCZOS), label))

    rows = (len(cells) + cols - 1) // cols
    cell_h = cells[0][0].height + LABEL_H
    canvas = Image.new(
        "RGB",
        (PAD * 2 + W * cols + GAP * (cols - 1), PAD * 2 + cell_h * rows + GAP * (rows - 1)),
        bg,
    )
    draw = ImageDraw.Draw(canvas)

    for i, (im, label) in enumerate(cells):
        row, col = divmod(i, cols)
        x = PAD + col * (W + GAP)
        y = PAD + row * (cell_h + GAP)

        mask = Image.new("L", im.size, 0)
        ImageDraw.Draw(mask).rounded_rectangle(
            [0, 0, im.width - 1, im.height - 1], radius=14, fill=255
        )
        canvas.paste(im, (x, y + LABEL_H), mask)
        draw.text((x + 4, y + 10), label, fill=label_fg)

    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, out_name)
    canvas.save(path, optimize=True)
    print(f"saved: {path}  {canvas.size}  {os.path.getsize(path) // 1024} KB")


def main() -> None:
    render(SHOTS_LIGHT, 2, BG_LIGHT, (70, 78, 90), "screenshots.png")
    render(SHOTS_DARK, 2, BG_DARK, (200, 205, 212), "screenshots-dark.png")


if __name__ == "__main__":
    main()

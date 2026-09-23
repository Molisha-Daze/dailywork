# -*- coding: utf-8 -*-
"""把模拟器实测截图拼成 README 用的展示图。

用法：
  1. 模拟器设成**竖屏**（MuMu：resolution_mode=custom / 1080x1920，density 440）
  2. 用 adb 截好图放进 .iconwork/shots-portrait/
  3. 跑本脚本

输出：
  docs/screenshots.png       四页并排（浅色·宣纸）
  docs/screenshots-dark.png  两页并排（暗色·墨曜）

🚨 分辨率教训：MuMu 默认是 tablet.1 / 1920x1080，那是**横屏**，截出来很丑；
   而改成 1080x1920 后 physical density 会掉到 10，界面挤成一团（字体还变点阵），
   必须 `adb shell wm density 440` 覆盖回来才正常。

依赖：Pillow（用隔离环境 C:/Users/admin/.workbuddy/binaries/python/envs/default）。
本脚本是一次性文档工具，不参与构建、不进 APK。
"""
from PIL import Image, ImageDraw
import os

ROOT = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(os.path.dirname(ROOT), ".iconwork", "shots-portrait")
OUT = os.path.join(os.path.dirname(ROOT), "docs")

SHOTS_LIGHT = [
    ("l01-today.png", "今日打卡"),
    ("l02-calendar.png", "打卡日历"),
    ("l03-counters.png", "独立计数器"),
    ("l04-settings.png", "管理中心"),
]
SHOTS_DARK = [
    ("p01-today.png", "今日打卡"),
    ("p02-calendar.png", "打卡日历"),
]

W = 270          # 每张缩略图宽度（手机竖屏，四张并排刚好一大行）
GAP = 22
PAD = 26
LABEL_H = 30
BG_LIGHT = (245, 243, 239)
BG_DARK = (22, 22, 25)


def render(shots, bg, label_fg, out_name) -> None:
    cells = []
    for fn, label in shots:
        im = Image.open(os.path.join(SRC, fn)).convert("RGB")
        h = round(im.height * W / im.width)
        cells.append((im.resize((W, h), Image.LANCZOS), label))

    cell_h = cells[0][0].height + LABEL_H
    canvas = Image.new(
        "RGB",
        (PAD * 2 + W * len(cells) + GAP * (len(cells) - 1), PAD * 2 + cell_h),
        bg,
    )
    draw = ImageDraw.Draw(canvas)

    for i, (im, label) in enumerate(cells):
        x = PAD + i * (W + GAP)
        y = PAD
        mask = Image.new("L", im.size, 0)
        ImageDraw.Draw(mask).rounded_rectangle(
            [0, 0, im.width - 1, im.height - 1], radius=10, fill=255
        )
        canvas.paste(im, (x, y + LABEL_H), mask)
        # 居中标签（粗略按每字 12px 估宽，够用）
        tw = len(label) * 13
        draw.text((x + (W - tw) // 2, y + 8), label, fill=label_fg)

    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, out_name)
    canvas.save(path, optimize=True)
    print(f"saved: {path}  {canvas.size}  {os.path.getsize(path) // 1024} KB")


def main() -> None:
    render(SHOTS_LIGHT, BG_LIGHT, (90, 84, 74), "screenshots.png")
    render(SHOTS_DARK, BG_DARK, (198, 202, 210), "screenshots-dark.png")


if __name__ == "__main__":
    main()

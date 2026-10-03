#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
禅息 (ZenXi) - 图标生成

生成：
  app/res/mipmap-*/ic_launcher.png            传统图标（含圆角）
  app/res/mipmap-*/ic_launcher_round.png      圆形图标
  app/res/mipmap-*/ic_launcher_foreground.png 自适应图标前景（108dp 画布）
  app/res/drawable-xxhdpi/ic_stat_bell.png    通知栏状态图标（纯白）

图形语言：深墨底 + 玉色圆环 + 「息」字。
"""

import os

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.normpath(os.path.join(HERE, "..", "app", "res"))

INK_TOP = (17, 26, 43, 255)
INK_BOTTOM = (6, 10, 18, 255)
JADE = (79, 227, 184, 255)
JADE_SOFT = (79, 227, 184, 120)
TEXT = (234, 241, 255, 255)

SS = 8  # 超采样倍数

FONT_CANDIDATES = [
    "C:/Windows/Fonts/msyhl.ttc",
    "C:/Windows/Fonts/msyh.ttc",
    "C:/Windows/Fonts/Deng.ttf",
    "C:/Windows/Fonts/simhei.ttf",
]


def font(size):
    for path in FONT_CANDIDATES:
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size)
            except Exception:
                continue
    return ImageFont.load_default()


def gradient_base(size, radius_ratio=0.24, circle=False):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    grad = Image.new("RGBA", (1, size))
    for y in range(size):
        t = y / max(1, size - 1)
        grad.putpixel((0, y), (
            int(INK_TOP[0] + (INK_BOTTOM[0] - INK_TOP[0]) * t),
            int(INK_TOP[1] + (INK_BOTTOM[1] - INK_TOP[1]) * t),
            int(INK_TOP[2] + (INK_BOTTOM[2] - INK_TOP[2]) * t),
            255))
    grad = grad.resize((size, size))
    mask = Image.new("L", (size, size), 0)
    md = ImageDraw.Draw(mask)
    if circle:
        md.ellipse([0, 0, size - 1, size - 1], fill=255)
    else:
        r = int(size * radius_ratio)
        md.rounded_rectangle([0, 0, size - 1, size - 1], radius=r, fill=255)
    img.paste(grad, (0, 0), mask)
    return img


def draw_mark(draw, size, ring_ratio=0.60, stroke_ratio=0.055,
              glyph_ratio=0.36, with_dot=True):
    """在 size x size 画布中央画圆环 + 「息」字。"""
    c = size / 2.0
    ring_r = size * ring_ratio / 2.0
    stroke = max(1, int(size * stroke_ratio))
    draw.ellipse([c - ring_r, c - ring_r, c + ring_r, c + ring_r],
                 outline=JADE, width=stroke)
    # 环上的光点
    if with_dot:
        dot = size * 0.055
        draw.ellipse([c - dot / 2, c - ring_r - dot / 2, c + dot / 2, c - ring_r + dot / 2],
                     fill=JADE)
    # 「息」字
    fs = int(size * glyph_ratio)
    f = font(fs)
    box = draw.textbbox((0, 0), "息", font=f)
    tw, th = box[2] - box[0], box[3] - box[1]
    draw.text((c - tw / 2 - box[0], c - th / 2 - box[1]), "息", font=f, fill=TEXT)


def legacy_icon(size, circle=False):
    big = size * SS
    img = gradient_base(big, circle=circle)
    d = ImageDraw.Draw(img)
    draw_mark(d, big)
    img = img.resize((size, size), Image.LANCZOS)
    return img


def adaptive_foreground(size):
    """108dp 画布，图形收在中间 66dp 安全区内。"""
    big = size * SS
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    draw_mark(d, big, ring_ratio=0.50, stroke_ratio=0.048, glyph_ratio=0.30)
    return img.resize((size, size), Image.LANCZOS)


def status_bell(size):
    """通知栏小图标：纯白铃铛。"""
    big = size * SS
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    w = big
    cx = w / 2.0
    white = (255, 255, 255, 255)
    # 铃身：上方圆顶 + 下方外扩
    dome_r = w * 0.24
    dome_cy = w * 0.40
    d.ellipse([cx - dome_r, dome_cy - dome_r, cx + dome_r, dome_cy + dome_r], fill=white)
    d.polygon([(cx - dome_r, dome_cy),
               (cx + dome_r, dome_cy),
               (cx + w * 0.34, w * 0.72),
               (cx - w * 0.34, w * 0.72)], fill=white)
    d.rounded_rectangle([cx - w * 0.40, w * 0.66, cx + w * 0.40, w * 0.76],
                        radius=w * 0.05, fill=white)
    # 铃舌
    d.ellipse([cx - w * 0.10, w * 0.78, cx + w * 0.10, w * 0.94], fill=white)
    return img.resize((size, size), Image.LANCZOS)


def main():
    densities = {
        "mdpi": 48,
        "hdpi": 72,
        "xhdpi": 96,
        "xxhdpi": 144,
        "xxxhdpi": 192,
    }
    for name, px in densities.items():
        out = os.path.join(RES, "mipmap-" + name)
        os.makedirs(out, exist_ok=True)
        legacy_icon(px).save(os.path.join(out, "ic_launcher.png"))
        legacy_icon(px, circle=True).save(os.path.join(out, "ic_launcher_round.png"))
        fg = int(round(px * 108.0 / 48.0))
        adaptive_foreground(fg).save(os.path.join(out, "ic_launcher_foreground.png"))
        print("mipmap-%-8s launcher=%dpx  foreground=%dpx" % (name, px, fg))

    for folder, px in (("drawable-xxhdpi", 72), ("drawable-xxxhdpi", 96)):
        out = os.path.join(RES, folder)
        os.makedirs(out, exist_ok=True)
        status_bell(px).save(os.path.join(out, "ic_stat_bell.png"))
        print("%-18s ic_stat_bell.png %dpx" % (folder, px))


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Draws the launcher banner (640x360) and icon (512x512) in the app's newspaper style, with the bundled fonts.
    .venv/bin/python tools/make_icons.py
"""
import os
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FONTS = os.path.join(ROOT, "app", "assets", "fonts")
OUT = os.path.join(ROOT, "app", "res", "drawable-nodpi")
NAME = "YourFrame"
PAPER, INK, MUTED = (241, 237, 228), (30, 28, 25), (107, 102, 92)


def font(name, size, weight):
    f = ImageFont.truetype(os.path.join(FONTS, name), size)
    try:
        f.set_variation_by_axes([weight])
    except Exception:
        pass
    return f


def gold_frame(d, box, width):
    x0, y0, x1, y1 = box
    stops = [(138, 106, 42), (230, 200, 120), (169, 130, 47), (243, 220, 149), (154, 116, 38), (217, 184, 102)]
    for i in range(width):
        t = i / max(1, width - 1)
        c = stops[min(len(stops) - 1, int(t * (len(stops) - 1)))]
        d.rectangle((x0 + i, y0 + i, x1 - i, y1 - i), outline=c)
    d.rectangle((x0 + width, y0 + width, x1 - width, y1 - width), outline=(92, 68, 22), width=2)


def painting(img, box):
    """A tiny landscape: sky, hills, water, in muted oil colours."""
    x0, y0, x1, y1 = box
    d = ImageDraw.Draw(img)
    h = y1 - y0
    for y in range(y0, y1):
        t = (y - y0) / h
        if t < 0.55:
            c = (int(196 - 40 * t), int(186 - 30 * t), int(150 + 20 * t))
        else:
            c = (int(92 - 30 * (t - 0.55)), int(98 - 20 * (t - 0.55)), int(70 - 10 * (t - 0.55)))
        d.line((x0, y, x1, y), fill=c)
    w = x1 - x0
    d.polygon([(x0, y0 + h * 0.62), (x0 + w * 0.3, y0 + h * 0.42), (x0 + w * 0.55, y0 + h * 0.58),
               (x0 + w * 0.8, y0 + h * 0.45), (x1, y0 + h * 0.6), (x1, y1), (x0, y1)], fill=(84, 92, 62))
    d.ellipse((x0 + w * 0.68, y0 + h * 0.12, x0 + w * 0.8, y0 + h * 0.12 + w * 0.12), fill=(236, 214, 160))
    d.rectangle((x0, y0 + h * 0.78, x1, y1), fill=(70, 86, 92))


def banner():
    W, H = 640, 360
    img = Image.new("RGB", (W, H), PAPER)
    d = ImageDraw.Draw(img)
    # The artwork in its frame, left.
    fx0, fy0, fx1, fy1 = 34, 74, 284, 286
    gold_frame(d, (fx0, fy0, fx1, fy1), 16)
    d.rectangle((fx0 + 18, fy0 + 18, fx1 - 18, fy1 - 18), fill=(233, 223, 201))
    painting(img, (fx0 + 34, fy0 + 34, fx1 - 34, fy1 - 34))
    # Masthead text, right.
    caps = font("LibreCaslonText.ttf", 19, 700)
    x = 312
    size = 74
    while True:
        title = font("PlayfairDisplay.ttf", size, 700)
        b = d.textbbox((0, 0), NAME, font=title)
        if b[2] - b[0] <= W - 30 - x or size <= 30:
            break
        size -= 2
    d.text((x - b[0], 204 - b[3]), NAME, font=title, fill=INK)
    d.line((x, 214, W - 30, 214), fill=INK, width=3)
    d.line((x, 220, W - 30, 220), fill=INK, width=1)
    label = "ART  MODE  ·  THE  MET"
    d.text((x, 234), label, font=caps, fill=MUTED)
    img.save(os.path.join(OUT, "banner.png"))


def icon():
    S = 512
    img = Image.new("RGB", (S, S), PAPER)
    d = ImageDraw.Draw(img)
    gold_frame(d, (0, 0, S - 1, S - 1), 40)
    d.rectangle((44, 44, S - 45, S - 45), fill=PAPER)
    f = font("PlayfairDisplay-Italic.ttf", 300, 600)
    text = "Y"
    b = d.textbbox((0, 0), text, font=f)
    d.text(((S - (b[2] - b[0])) / 2 - b[0], (S - (b[3] - b[1])) / 2 - b[1] - 10), text, font=f, fill=INK)
    d.line((150, 420, S - 150, 420), fill=INK, width=4)
    d.line((150, 430, S - 150, 430), fill=INK, width=2)
    img.save(os.path.join(OUT, "icon.png"))


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    banner()
    icon()
    print("banner.png and icon.png in", os.path.relpath(OUT, ROOT))

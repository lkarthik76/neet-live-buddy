#!/usr/bin/env python3
"""Resize design-assets/app_icon_source_1024.png into Android mipmaps, adaptive foregrounds, iOS set, Play 512.

Adaptive icons are masked (circle / squircle). Default scale (~72%) is larger than Google’s
~66% “safe” zone so the glyph reads bigger; outer edges may clip slightly on strict circle masks.
See https://developer.android.com/develop/ui/views/launch-icon-design-adaptive
"""
from __future__ import annotations

import os
import sys

try:
    from PIL import Image
except ImportError:
    print("Install Pillow: pip install Pillow", file=sys.stderr)
    sys.exit(1)

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
KMP_ROOT = os.path.dirname(SCRIPT_DIR)
SRC = os.path.join(KMP_ROOT, "design-assets", "app_icon_source_1024.png")
RES_BASE = os.path.join(KMP_ROOT, "composeApp", "src", "androidMain", "res")

# Must match res/values/ic_launcher_background.xml — fills legacy mipmaps so circle crops don’t show a white ring.
LAUNCHER_BG = (255, 255, 255, 255)  # #FFFFFF (matches ic_launcher_background)
# Fraction of layer size used by the artwork (higher = visually larger icon).
ADAPTIVE_ARTWORK_FRACTION = 0.72
LEGACY_MIPMAP_ARTWORK_FRACTION = 0.80


def main() -> None:
    if not os.path.isfile(SRC):
        print(f"Missing source: {SRC}", file=sys.stderr)
        sys.exit(1)

    img = Image.open(SRC).convert("RGBA")
    w, h = img.size
    side = min(w, h)
    left = (w - side) // 2
    top = (h - side) // 2
    img = img.crop((left, top, left + side, top + side))

    for folder, size in {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }.items():
        d = os.path.join(RES_BASE, folder)
        os.makedirs(d, exist_ok=True)
        canvas = Image.new("RGBA", (size, size), LAUNCHER_BG)
        icon_size = max(1, int(size * LEGACY_MIPMAP_ARTWORK_FRACTION))
        icon_r = img.resize((icon_size, icon_size), Image.LANCZOS)
        off = (size - icon_size) // 2
        canvas.paste(icon_r, (off, off), icon_r)
        canvas.save(os.path.join(d, "ic_launcher.png"))

    for folder, size in {
        "drawable-mdpi": 108,
        "drawable-hdpi": 162,
        "drawable-xhdpi": 216,
        "drawable-xxhdpi": 324,
        "drawable-xxxhdpi": 432,
    }.items():
        d = os.path.join(RES_BASE, folder)
        os.makedirs(d, exist_ok=True)
        fg = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        icon_size = max(1, int(size * ADAPTIVE_ARTWORK_FRACTION))
        icon_r = img.resize((icon_size, icon_size), Image.LANCZOS)
        off = (size - icon_size) // 2
        fg.paste(icon_r, (off, off), icon_r)
        fg.save(os.path.join(d, "ic_launcher_foreground.png"))

    # iPhone + App Store only (no iPad 76pt / 83.5pt). Add those in Xcode or extend this dict if you ship iPad.
    ios_dir = os.path.join(KMP_ROOT, "ios-app-icon")
    os.makedirs(ios_dir, exist_ok=True)
    for name, size in {
        "icon-20@2x.png": 40,
        "icon-20@3x.png": 60,
        "icon-29@2x.png": 58,
        "icon-29@3x.png": 87,
        "icon-40@2x.png": 80,
        "icon-40@3x.png": 120,
        "icon-60@2x.png": 120,
        "icon-60@3x.png": 180,
        "icon-1024.png": 1024,
    }.items():
        canvas = Image.new("RGBA", (size, size), LAUNCHER_BG)
        ir = img.resize((size, size), Image.LANCZOS)
        canvas.paste(ir, (0, 0), ir)
        canvas.save(os.path.join(ios_dir, name))

    ps = Image.new("RGBA", (512, 512), LAUNCHER_BG)
    ir512 = img.resize((512, 512), Image.LANCZOS)
    ps.paste(ir512, (0, 0), ir512)
    ps.save(os.path.join(KMP_ROOT, "playstore_icon_512.png"))
    print("OK: Android + iOS + playstore_icon_512.png")


if __name__ == "__main__":
    main()

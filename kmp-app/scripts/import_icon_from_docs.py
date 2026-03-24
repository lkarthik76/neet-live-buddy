#!/usr/bin/env python3
"""
Import a marketing / Copilot icon PNG into design-assets/app_icon_source_1024.png.

- **Square icon**: center-crop to square, then edge flood-remove flat canvas → 1024×1024.
- **Wide / tall logo**: edge flood-remove canvas, crop to opaque bbox,
  **letterbox** into 1024×1024 white (keeps full book/graphic without cropping sides).
- **Wide mockup** (side-by-side Android + iOS, typically ≥1000px tall): right panel + bbox path.

Then run: python3 scripts/regenerate_app_icons.py
"""
from __future__ import annotations

import argparse
import os
import sys
from collections import deque

try:
    import numpy as np
    from PIL import Image
except ImportError:
    print("Need: pip install Pillow numpy", file=sys.stderr)
    sys.exit(1)

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
KMP_ROOT = os.path.dirname(SCRIPT_DIR)
OUT = os.path.join(KMP_ROOT, "design-assets", "app_icon_source_1024.png")
DEFAULT_SRC = os.path.join(
    KMP_ROOT, "..", "docs", "images", "Copilot_20260323_103308.png"
)


def flood_transparent_edge_rgb(
    rgb: np.ndarray,
    ref: np.ndarray,
    tol: float,
) -> np.ndarray:
    h, w = rgb.shape[:2]
    vis = np.zeros((h, w), dtype=bool)
    q: deque[tuple[int, int]] = deque()

    def is_bg(y: int, x: int) -> bool:
        r, g, b = rgb[y, x].astype(np.float32)
        return (
            abs(r - ref[0]) < tol
            and abs(g - ref[1]) < tol
            and abs(b - ref[2]) < tol
        )

    for x in range(w):
        for y in (0, h - 1):
            if is_bg(y, x) and not vis[y, x]:
                vis[y, x] = True
                q.append((y, x))
    for y in range(h):
        for x in (0, w - 1):
            if is_bg(y, x) and not vis[y, x]:
                vis[y, x] = True
                q.append((y, x))
    while q:
        y, x = q.popleft()
        for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0)):
            ny, nx = y + dy, x + dx
            if 0 <= ny < h and 0 <= nx < w and not vis[ny, nx] and is_bg(ny, nx):
                vis[ny, nx] = True
                q.append((ny, nx))
    return vis


def flood_transparent_white(a: np.ndarray) -> np.ndarray:
    h, w = a.shape[:2]
    rgb = a[:, :, :3].astype(np.int16)

    def near_white(y: int, x: int) -> bool:
        r, g, b = rgb[y, x]
        return r > 245 and g > 245 and b > 245

    vis = np.zeros((h, w), dtype=bool)
    q: deque[tuple[int, int]] = deque()
    for x in range(w):
        for y in (0, h - 1):
            if near_white(y, x) and not vis[y, x]:
                vis[y, x] = True
                q.append((y, x))
    for y in range(h):
        for x in (0, w - 1):
            if near_white(y, x) and not vis[y, x]:
                vis[y, x] = True
                q.append((y, x))
    while q:
        y, x = q.popleft()
        for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0)):
            ny, nx = y + dy, x + dx
            if 0 <= ny < h and 0 <= nx < w and not vis[ny, nx] and near_white(ny, nx):
                vis[ny, nx] = True
                q.append((ny, nx))
    a2 = a.copy()
    a2[vis, 3] = 0
    return a2


def process_rectangular_letterbox(path: str) -> Image.Image:
    """Wide or tall artwork: remove flat edge background, fit entire graphic in 1024² with white bars."""
    raw = Image.open(path).convert("RGBA")
    a = np.array(raw)
    h, w = a.shape[:2]
    edge_px = np.vstack(
        [a[0, :, :3], a[-1, :, :3], a[:, 0, :3], a[:, -1, :3]]
    ).astype(np.float32)
    ref = edge_px.mean(axis=0)
    tol = max(30.0, float(np.std(edge_px)) * 2 + 12.0)
    vis = flood_transparent_edge_rgb(a[:, :, :3].astype(np.float32), ref, tol)
    a2 = a.copy()
    a2[vis, 3] = 0
    mask = a2[:, :, 3] > 15
    if not mask.any():
        return Image.fromarray(a2).resize((1024, 1024), Image.LANCZOS)
    ys, xs = np.where(mask)
    x0, x1 = xs.min(), xs.max()
    y0, y1 = ys.min(), ys.max()
    cropped = a2[y0 : y1 + 1, x0 : x1 + 1]
    ch, cw = cropped.shape[:2]
    margin = 0.92
    scale = min(1024 / cw, 1024 / ch) * margin
    nw = max(1, int(round(cw * scale)))
    nh = max(1, int(round(ch * scale)))
    pil_crop = Image.fromarray(cropped)
    resized = pil_crop.resize((nw, nh), Image.LANCZOS)
    canvas = Image.new("RGBA", (1024, 1024), (255, 255, 255, 255))
    ox = (1024 - nw) // 2
    oy = (1024 - nh) // 2
    canvas.paste(resized, (ox, oy), resized)
    return canvas


def process_square_icon(path: str) -> Image.Image:
    raw = Image.open(path).convert("RGBA")
    a = np.array(raw)
    h, w = a.shape[:2]
    side = min(h, w)
    left = (w - side) // 2
    top = (h - side) // 2
    a = a[top : top + side, left : left + side]
    h, w = a.shape[:2]
    edge_px = np.vstack(
        [a[0, :, :3], a[-1, :, :3], a[:, 0, :3], a[:, -1, :3]]
    ).astype(np.float32)
    ref = edge_px.mean(axis=0)
    tol = 24.0
    vis = flood_transparent_edge_rgb(a[:, :, :3].astype(np.float32), ref, tol)
    a2 = a.copy()
    a2[vis, 3] = 0
    out = Image.fromarray(a2)
    if side != 1024:
        out = out.resize((1024, 1024), Image.LANCZOS)
    return out


def process_wide_mockup(path: str) -> Image.Image:
    raw = np.array(Image.open(path).convert("RGB"))
    H, W, _ = raw.shape
    half = W // 2
    panel = raw[:, half:, :]
    ph, pw, _ = panel.shape
    b, g, r = panel[:, :, 2], panel[:, :, 1], panel[:, :, 0]
    mask = (b > 160) & (r < 240) & (g < 250) & (b > r + 20)
    if mask.sum() < 100:
        mask = (b > 140) & (b > r)
    ys, xs = np.where(mask)
    y0, y1 = ys.min(), ys.max()
    x0, x1 = xs.min(), xs.max()
    cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
    side = int(max(x1 - x0, y1 - y0) * 1.08)
    x0s = max(0, cx - side // 2)
    y0s = max(0, cy - side // 2)
    x1s = min(pw, x0s + side)
    y1s = min(ph, y0s + side)
    square_rgb = panel[y0s:y1s, x0s:x1s]
    sq = Image.fromarray(square_rgb).resize((1024, 1024), Image.LANCZOS)
    rgba = np.array(sq.convert("RGBA"))
    rgba = flood_transparent_white(rgba)
    return Image.fromarray(rgba)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument(
        "path",
        nargs="?",
        default=os.path.normpath(DEFAULT_SRC),
        help="Source PNG (square icon or wide mockup)",
    )
    args = ap.parse_args()
    src = os.path.abspath(args.path)
    if not os.path.isfile(src):
        print(f"Not found: {src}", file=sys.stderr)
        sys.exit(1)

    im = Image.open(src)
    w, h = im.size
    aspect = w / h
    # Side-by-side Android+iOS mockups are very wide AND ~1024px tall (e.g. 1536×1024).
    is_dual_mockup = aspect > 1.25 and h >= 700
    if is_dual_mockup:
        img = process_wide_mockup(src)
    elif aspect > 1.12 or aspect < 0.89:
        img = process_rectangular_letterbox(src)
    else:
        img = process_square_icon(src)

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT)
    print("Wrote", OUT)
    print("Next: cd kmp-app && python3 scripts/regenerate_app_icons.py")


if __name__ == "__main__":
    main()

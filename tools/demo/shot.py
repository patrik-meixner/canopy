#!/usr/bin/env python3
"""Captures the IDE window and presents it: rounded, shadowed, on a ground of its own.

    shot.py <name> [--match TEXT] [--width 2400] [--pad 96]

Writes docs/screenshots/<name>.png. Needs Pillow and pyobjc-framework-Quartz.
"""

import argparse
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

OUT = Path(__file__).resolve().parents[2] / "docs" / "screenshots"

# A ground that is neither white nor black: the IDE keeps its own contrast either way.
GROUND_TOP = (26, 24, 38)
GROUND_BOTTOM = (12, 12, 18)
# The IDE's own accent, thrown faintly on the wall behind it.
GLOW = (224, 68, 123)
GLOW_ALPHA = 46
RADIUS = 22
SHADOW_BLUR = 52
SHADOW_ALPHA = 165


def window_id(match: str) -> tuple[int, str]:
    import Quartz

    windows = Quartz.CGWindowListCopyWindowInfo(
        Quartz.kCGWindowListOptionAll | Quartz.kCGWindowListExcludeDesktopElements,
        Quartz.kCGNullWindowID,
    )
    found = []
    for window in windows:
        owner = (window.get("kCGWindowOwnerName") or "").lower()
        title = window.get("kCGWindowName") or ""
        bounds = window["kCGWindowBounds"]
        if "idea" not in owner and "intellij" not in owner:
            continue
        if bounds["Width"] < 600 or bounds["Height"] < 400:
            continue
        if match.lower() not in title.lower():
            continue
        found.append((window["kCGWindowNumber"], title, bounds["Width"] * bounds["Height"]))

    if not found:
        raise SystemExit(f"No IntelliJ window whose title contains {match!r}.")

    found.sort(key=lambda entry: -entry[2])

    return found[0][0], found[0][1]


def capture(identifier: int) -> Image.Image:
    with tempfile.NamedTemporaryFile(suffix=".png", delete=False) as handle:
        path = handle.name
    subprocess.run(["screencapture", "-x", "-o", "-l", str(identifier), path], check=True)

    return Image.open(path).convert("RGB")


def rounded(image: Image.Image, radius: int) -> Image.Image:
    mask = Image.new("L", image.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, image.width - 1, image.height - 1], radius, fill=255)
    out = Image.new("RGBA", image.size)
    out.paste(image, mask=mask)

    return out


def ground(size: tuple[int, int]) -> Image.Image:
    width, height = size
    column = Image.new("RGB", (1, height))
    for y in range(height):
        ratio = y / max(height - 1, 1)
        column.putpixel((0, y), tuple(
            int(GROUND_TOP[channel] + (GROUND_BOTTOM[channel] - GROUND_TOP[channel]) * ratio)
            for channel in range(3)
        ))
    base = column.resize((width, height)).convert("RGBA")

    # One soft light above the window, so the ground is not a flat field.
    glow = Image.new("RGBA", size, (0, 0, 0, 0))
    radius = int(width * 0.42)
    ImageDraw.Draw(glow).ellipse(
        [width // 2 - radius, -radius, width // 2 + radius, radius],
        fill=(*GLOW, GLOW_ALPHA),
    )

    return Image.alpha_composite(base, glow.filter(ImageFilter.GaussianBlur(radius // 2)))


def present(shot: Image.Image, pad: int, scale_to: int) -> Image.Image:
    if scale_to and shot.width != scale_to:
        height = round(shot.height * scale_to / shot.width)
        shot = shot.resize((scale_to, height), Image.LANCZOS)

    radius = round(RADIUS * shot.width / 1600)
    card = rounded(shot, radius)
    canvas_size = (shot.width + pad * 2, shot.height + pad * 2)
    canvas = ground(canvas_size)

    shadow = Image.new("RGBA", canvas_size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle(
        [pad, pad + round(pad * 0.18), pad + shot.width, pad + shot.height + round(pad * 0.18)],
        radius, fill=(0, 0, 0, SHADOW_ALPHA),
    )
    canvas = Image.alpha_composite(canvas, shadow.filter(ImageFilter.GaussianBlur(SHADOW_BLUR)))
    canvas.paste(card, (pad, pad), card)

    return canvas.convert("RGB")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("name")
    parser.add_argument("--match", default="CanopyDemo", help="text the window title must contain")
    parser.add_argument("--width", type=int, default=2400, help="width of the framed shot, 0 to keep native")
    parser.add_argument("--pad", type=int, default=96)
    arguments = parser.parse_args()

    identifier, title = window_id(arguments.match)
    print(f"window: {title}")

    framed = present(capture(identifier), arguments.pad, arguments.width)
    OUT.mkdir(parents=True, exist_ok=True)
    target = OUT / f"{arguments.name}.png"
    framed.save(target)
    print(f"{target}  {framed.width}x{framed.height}")

    return 0


if __name__ == "__main__":
    sys.exit(main())

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

# The ground is the window itself, blown up, blurred and darkened.
BACKDROP_ZOOM = 1.35
BACKDROP_BLUR = 0.045
BACKDROP_SHADE = (10, 9, 14)
BACKDROP_DARKEN = 0.62
RADIUS = 22
SHADOW_BLUR = 52
SHADOW_ALPHA = 175


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


def ground(shot: Image.Image, size: tuple[int, int]) -> Image.Image:
    """The window itself, blown up and blurred, so the ground is made of what stands on it."""
    width, height = size
    scale = max(width / shot.width, height / shot.height) * BACKDROP_ZOOM
    blown = shot.resize((round(shot.width * scale), round(shot.height * scale)), Image.LANCZOS)
    left = (blown.width - width) // 2
    top = (blown.height - height) // 2
    backdrop = blown.crop((left, top, left + width, top + height))
    backdrop = backdrop.filter(ImageFilter.GaussianBlur(round(width * BACKDROP_BLUR)))

    # Darkened, or the window would sit on something as bright as itself.
    return Image.blend(backdrop, Image.new("RGB", size, BACKDROP_SHADE), BACKDROP_DARKEN).convert("RGBA")


def present(shot: Image.Image, pad: int, scale_to: int) -> Image.Image:
    if scale_to and shot.width != scale_to:
        height = round(shot.height * scale_to / shot.width)
        shot = shot.resize((scale_to, height), Image.LANCZOS)

    radius = round(RADIUS * shot.width / 1600)
    card = rounded(shot, radius)
    canvas_size = (shot.width + pad * 2, shot.height + pad * 2)
    canvas = ground(shot, canvas_size)

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

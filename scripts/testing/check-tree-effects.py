#!/usr/bin/env python3
"""Check actual fixed-camera tree captures for visible cyan effects, not just full-bright items.

Requires Pillow. Run after ./gradlew runTreeValidation; thresholds scale with resolution.
The scene has no other cyan emitters. These broad checks complement visual review.
"""
import argparse
from pathlib import Path
from PIL import Image


def cyan_fraction(path):
    with Image.open(path) as image:
        image = image.convert("RGB")
        width, height = image.size
        crop = image.crop((int(width * .35), int(height * .3), int(width * .65), int(height * .77)))
        count = sum(g > 55 and g > r * 1.5 and b > r * 1.4 and b > g * .6
                    for r, g, b in crop.getdata())
        return count / (width * height)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("screenshots", type=Path)
    args = parser.parse_args()
    bounds = {
        "01-three-quiet.png": (0, .00015),
        "03-six-souls.png": (.00018, .0018),
        "04-awakening.png": (.003, .025),
        "05-ten-glow-night.png": (.002, .018),
        "06-ten-glow-day.png": (.0006, .015),
        "07-minimal-particles.png": (.0006, .015),
        "08-haunt-awakening.png": (.003, .025),
        "09-haunting.png": (.002, .018),
        "10-quiet-restored.png": (0, .00015),
    }
    failures = []
    for name, (low, high) in bounds.items():
        value = cyan_fraction(args.screenshots / name)
        ok = low <= value <= high
        print(f"{'PASS' if ok else 'FAIL'} {name}: cyan coverage {value:.5%}")
        if not ok:
            failures.append(name)
    if failures:
        raise SystemExit("Tree visual regression: " + ", ".join(failures))


if __name__ == "__main__":
    main()

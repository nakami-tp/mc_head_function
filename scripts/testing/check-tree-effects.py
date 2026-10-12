#!/usr/bin/env python3
"""Check actual fixed-camera tree captures for compact vanilla-style soul fire, without broad cyan halos.

Requires Pillow. Run after ./gradlew runTreeValidation; thresholds scale with resolution.
The scene has no other cyan emitters. Lower bounds catch missing embers; upper bounds reject the previous broad halos.
These broad checks complement visual review; they do not judge artistic style.
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
        "03-six-souls.png": (.000008, .0002),
        "04-awakening.png": (.0002, .0015),
        "05-ten-glow-night.png": (.00015, .0012),
        "06-ten-glow-day.png": (.00008, .0012),
        "07-minimal-particles.png": (.00008, .0012),
        "08-haunt-awakening.png": (.0002, .0015),
        "09-haunting.png": (.00015, .0012),
        "10-quiet-restored.png": (0, .00015),
    }
    failures = []
    for name, (low, high) in bounds.items():
        value = cyan_fraction(args.screenshots / name)
        ok = low <= value <= high
        print(f"{'PASS' if ok else 'FAIL'} {name}: cyan coverage {value:.5%}")
        if not ok:
            failures.append(name)
    # Fixed close camera: the zombie head below its flame. Head-top fire cannot satisfy this.
    for name in ("11-pixel-embers-day-close.png", "12-pixel-embers-night-close.png"):
        with Image.open(args.screenshots / name) as image:
            image = image.convert("RGB")
            width, height = image.size
            body = image.crop((int(width * .52), int(height * .71), int(width * .58), int(height * .75)))
            bright = sum(g > 100 and g > r * 1.2 and b > r * 1.2 for r, g, b in body.getdata())
            coverage = bright / (body.width * body.height)
            ok = .035 <= coverage <= .25
            print(f"{'PASS' if ok else 'FAIL'} {name}: head-body edge coverage {coverage:.2%}")
            if not ok:
                failures.append(name + " head body")
    occluded = cyan_fraction(args.screenshots / "13-glow-occluded.png")
    ok = occluded < .00002
    print(f"{'PASS' if ok else 'FAIL'} wall occlusion: cyan coverage {occluded:.5%}")
    if not ok:
        failures.append("wall occlusion")
    if failures:
        raise SystemExit("Tree visual regression: " + ", ".join(failures))


if __name__ == "__main__":
    main()

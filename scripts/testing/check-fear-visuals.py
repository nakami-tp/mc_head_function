#!/usr/bin/env python3
"""Assert actual hand pixels alternate between a green skull and its white fuse flash."""
import argparse
from pathlib import Path
from PIL import Image

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("screenshots", type=Path)
args = parser.parse_args()
green, white = [], []
for index in range(14):
    path = args.screenshots / f"warm-{index:03d}.png"
    with Image.open(path) as image:
        width, height = image.size
        pixels = list(image.convert("RGB").crop((int(width * .62), int(height * .57),
                                                 int(width * .77), int(height * .78))).getdata())
    green.append(sum(g - r > 25 and g - b > 15 for r, g, b in pixels) / len(pixels))
    white.append(sum(min(rgb) > 170 and max(rgb) - min(rgb) < 15 for rgb in pixels) / len(pixels))
assert max(green) > .3, f"No visible green held skull: {green}"
assert max(white) > .6 and min(white) < .2, f"No visible white flash alternation: {white}"
for index in range(36):
    assert (args.screenshots / f"retaliation-{index:03d}.png").is_file(), "Incomplete retaliation sequence"
print(f"PASS actualHandPixels greenPeak={max(green):.3f} whitePeak={max(white):.3f} warmFrames=14 retaliationFrames=36")

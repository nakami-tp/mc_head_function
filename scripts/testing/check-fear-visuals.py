#!/usr/bin/env python3
"""Assert a held skull shakes with the hand, then flashes green/white."""
import argparse
from pathlib import Path
from PIL import Image

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("screenshots", type=Path)
args = parser.parse_args()


def skull_box(path, x0=0.55, y0=0.50):
    with Image.open(path) as image:
        width, height = image.size
        pixels = image.convert("RGB").load()
        xs, ys = [], []
        # The fixture keeps targets above this region and the hotbar below it.
        for y in range(int(height * y0), int(height * .9)):
            for x in range(int(width * x0), width):
                r, g, b = pixels[x, y]
                if g - r > 25 and g - b > 15 and g > 50:
                    xs.append(x / width)
                    ys.append(y / height)
        if not xs:
            return None
        return min(xs), max(xs), min(ys), max(ys), sum(xs) / len(xs), sum(ys) / len(ys)


def compact(box, label):
    left, right, top, bottom, cx, cy = box
    width, height = right - left, bottom - top
    assert width > 0.06 and height > 0.08, f"{label} held skull is missing or too small"
    assert width < 0.28, f"{label} skull is too wide ({width:.3f}): a first-person cube, not a screen-filling plane"
    assert height < 0.38, f"{label} skull is too tall ({height:.3f})"
    assert cx > 0.55, f"{label} skull is not in the right hand ({cx:.3f})"
    assert cy > 0.48, f"{label} skull is not in the lower hand ({cy:.3f})"


# Fuse duration changes the phase at tick 66. Locate a real green sampled frame
# instead of assuming that one fixed tick is green for every supported duration.
warm_paths = [args.screenshots / f"warm-{index:03d}.png" for index in range(14)]
held_box = next((box for path in warm_paths if (box := skull_box(path)) is not None), None)
assert held_box is not None, "Missing green held skull"
compact(held_box, "held")
left, right, top, bottom, _, _ = held_box
# Sample the face of the actual held model, rather than a fixed large
# screen rectangle whose area used to require the oversized model to pass.
face = (left + .2 * (right - left), top + .4 * (bottom - top),
        left + .6 * (right - left), top + .75 * (bottom - top))
green, white = [], []
for path in warm_paths:
    with Image.open(path) as image:
        width, height = image.size
        pixels = list(image.convert("RGB").crop((int(width * face[0]), int(height * face[1]),
                                                 int(width * face[2]), int(height * face[3]))).getdata())
    green.append(sum(g - r > 25 and g - b > 15 for r, g, b in pixels) / len(pixels))
    white.append(sum(min(rgb) > 170 and max(rgb) - min(rgb) < 15 for rgb in pixels) / len(pixels))
assert max(green) > .3, f"No visible green held skull: {green}"
assert max(white) > .6 and min(white) < .2, f"No visible white flash alternation: {white}"

ignition_boxes = []
for index in range(10):
    path = args.screenshots / f"pin-{index:03d}.png"
    assert path.is_file(), f"Missing ignition frame {path.name}"
    box = skull_box(path)
    if box:
        compact(box, path.name)
        ignition_boxes.append((index, box))
assert len(ignition_boxes) == 10, "The held skull must stay visible throughout ignition"
centers = [box[4] for _, box in ignition_boxes]
travel = max(centers) - min(centers)
assert .012 < travel < .07, f"Expected small visible wrist shakes, x travel={travel:.3f}"
directions = [1 if b > a else -1 for a, b in zip(centers, centers[1:]) if abs(b - a) > .001]
turns = sum(a != b for a, b in zip(directions, directions[1:]))
assert 3 <= turns <= 4, f"Expected two smooth wrist shakes, got {turns} direction changes: {centers}"
for index in range(36):
    assert (args.screenshots / f"retaliation-{index:03d}.png").is_file(), "Incomplete retaliation sequence"
print(
    f"PASS actualHandPixels greenPeak={max(green):.3f} whitePeak={max(white):.3f} "
    f"warmFrames=14 ignitionFrames=10 shakeTravel={travel:.3f} shakeTurns={turns} retaliationFrames=36"
)

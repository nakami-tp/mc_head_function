#!/usr/bin/env python3
"""Real framebuffer regression: possessed cart weaves and later carries a cow."""
import argparse
from pathlib import Path
from PIL import Image


def frame(path):
    with Image.open(path) as image:
        return image.convert('RGB').resize((854, 480), Image.Resampling.LANCZOS)


def rms(a, b):
    with frame(a) as left, frame(b) as right:
        pairs = zip(left.getdata(), right.getdata())
        total = sum((p[0] - q[0]) ** 2 + (p[1] - q[1]) ** 2 + (p[2] - q[2]) ** 2 for p, q in pairs)
        return (total / (854 * 480 * 3)) ** 0.5


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('screenshots', type=Path)
    args = parser.parse_args()
    required = ['01-ready.png', '02-possessed.png', '03-wander.png', '04-boarded.png', '05-carrying.png']
    missing = [name for name in required if not (args.screenshots / name).is_file()]
    if missing:
        raise SystemExit('Missing keyframes: ' + ', '.join(missing))
    samples = sorted(args.screenshots.glob('sequence-*.png'))
    expected = [args.screenshots / f'sequence-{i:03d}.png' for i in range(1, 29)]
    if samples != expected:
        raise SystemExit(f'Expected sequence-001..028, found {[p.name for p in samples]}')
    wander = rms(args.screenshots / 'sequence-001.png', args.screenshots / 'sequence-010.png')
    carry = rms(args.screenshots / '04-boarded.png', args.screenshots / '05-carrying.png')
    print(f'wander frame rms={wander:.2f} carry frame rms={carry:.2f}')
    failures = []
    if wander < 4:
        failures.append(f'wander frames are almost identical (rms={wander:.2f}); cart did not visibly move')
    if carry < 3:
        failures.append(f'boarded and carrying frames are almost identical (rms={carry:.2f}); loaded cart looks frozen')
    if failures:
        raise SystemExit('; '.join(failures))
    print('PASS cart wander and carrying frames differ')


if __name__ == '__main__':
    main()

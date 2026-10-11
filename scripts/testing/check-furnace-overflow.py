#!/usr/bin/env python3
"""Real framebuffer regression: fireplace overflow on the hearth, not a 6-block spray."""
import argparse
from pathlib import Path
from PIL import Image, ImageFilter


def frame(path):
    # The validation window is 854x480 logical pixels; Retina framebuffers are 2x.
    with Image.open(path) as image:
        return image.convert('RGB').resize((854, 480), Image.Resampling.LANCZOS)


def fraction(path, box, predicate):
    with frame(path) as image:
        w, h = image.size
        crop = image.crop(tuple(int(v * (w if i % 2 == 0 else h)) for i, v in enumerate(box)))
        return sum(predicate(*p) for p in crop.getdata()) / (crop.width * crop.height)


def fire_bounds(path, background):
    with frame(path) as image:
        w, h = image.size
        points = [(x, y) for y in range(int(h * .18), int(h * .88))
                  for x in range(int(w * .32), int(w * .68))
                  if warm(*image.getpixel((x, y))) and not background.getpixel((x, y))]
        if not points:
            raise SystemExit(f'No hearth fire in {path}')
        return (min(x for x, y in points), min(y for x, y in points),
                max(x for x, y in points), max(y for x, y in points))


def warm(r, g, b):
    return r > 150 and r > g * 1.05 and r > b * 1.6 and g > 35


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('screenshots', type=Path)
    args = parser.parse_args()
    hearth = (.34, .28, .66, .86)
    far = (.02, .20, .18, .80)
    values = {name: fraction(args.screenshots / f'{name}.png', hearth, warm)
              for name in ['01-quiet', '02-overflow', '03-minimal-particles', '04-stopped']}
    far_values = {name: fraction(args.screenshots / f'{name}.png', far, warm)
                  for name in ['01-quiet', '02-overflow']}
    print('hearth fire fractions:', values)
    print('far-side fire fractions:', far_values)
    failures = []
    if values['02-overflow'] < values['01-quiet'] + .03:
        failures.append('hearth does not contain a large fireplace fire')
    if values['03-minimal-particles'] < values['01-quiet'] + .015:
        failures.append('core overflow disappears with minimal particles')
    if values['04-stopped'] > values['01-quiet'] + .008:
        failures.append('overflow did not disappear after stop')
    if far_values['02-overflow'] > far_values['01-quiet'] + .02:
        failures.append('fire reaches far from the hearth like a spray pillar')
    samples = sorted(args.screenshots.glob('motion-*.png'))
    expected = [args.screenshots / f'motion-{i:03d}.png' for i in range(24)]
    if samples != expected:
        failures.append('missing overflow motion samples')
    else:
        # A hanging blaze head is warm too. Exclude its baseline silhouette, with
        # room for its idle sway, so it cannot pin the measured flame apex.
        with frame(args.screenshots / '01-quiet.png') as quiet:
            background = Image.new('L', quiet.size)
            background.putdata([255 if warm(*p) else 0 for p in quiet.getdata()])
        background = background.filter(ImageFilter.MaxFilter(17))
        bounds = [fire_bounds(p, background) for p in samples]
        height = max(b[3] - b[1] for b in bounds)
        travel = max(b[1] for b in bounds) - min(b[1] for b in bounds)
        width = max(b[2] - b[0] for b in bounds)
        print('overflow silhouette height/top-travel/width:', height, travel, width, 'pixels')
        if height < 70:
            failures.append('overflow is too short to read as an uncontrolled fireplace fire')
        if travel < 8:
            failures.append('fire does not flicker or heave')
        if width > 280:
            failures.append('fire spreads like a directed stream instead of hearth overflow')
    if failures:
        raise SystemExit('Furnace visual regression: ' + '; '.join(failures))
    print('PASS fireplace overflow on the hearth, minimal particles, clean stop')


if __name__ == '__main__':
    main()

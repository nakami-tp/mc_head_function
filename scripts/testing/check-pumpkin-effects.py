#!/usr/bin/env python3
"""Real framebuffer regression: cold eye fire, visible body motion, clean stop."""
import argparse
from pathlib import Path
from PIL import Image


def fraction(path, box, predicate):
    with Image.open(path) as image:
        image = image.convert('RGB')
        w, h = image.size
        crop = image.crop(tuple(int(v * (w if i % 2 == 0 else h)) for i, v in enumerate(box)))
        return sum(predicate(*p) for p in crop.getdata()) / (crop.width * crop.height)


def body_bounds(path):
    with Image.open(path) as image:
        image = image.convert('RGB')
        w, h = image.size
        # Central pumpkin only; orange skin, excluding turquoise fire and quartz floor.
        points = [(x, y) for y in range(int(h * .60), int(h * .97))
                  for x in range(int(w * .40), int(w * .60))
                  if (lambda r, g, b: r > 45 and r > g * 1.35 and g > b * 1.5)(*image.getpixel((x, y)))]
        if not points:
            raise SystemExit(f'No pumpkin body in {path}')
        return (min(x for x, y in points), min(y for x, y in points),
                max(x for x, y in points), max(y for x, y in points))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('screenshots', type=Path)
    args = parser.parse_args()
    face = (.40, .60, .60, .97)
    cold = lambda r, g, b: g > 95 and b > 80 and g > r * 1.3 and b > r * 1.15
    values = {name: fraction(args.screenshots / f'{name}.png', face, cold)
              for name in ['01-quiet', '02-chorus', '04-minimal-particles', '05-stopped', '06-possessed']}
    print('cold fire fractions:', values)
    failures = []
    if values['02-chorus'] < values['01-quiet'] + .008:
        failures.append('eyes do not contain visible cold fire')
    if values['04-minimal-particles'] < values['01-quiet'] + .002:
        failures.append('core fire disappears with minimal particles')
    if values['05-stopped'] > values['01-quiet'] + .002:
        failures.append('fire did not disappear after stop')
    if values['06-possessed'] > values['01-quiet'] + .002:
        failures.append('possessed host still has restless cold fire')
    quiet_body = body_bounds(args.screenshots / '01-quiet.png')
    stopped_body = body_bounds(args.screenshots / '05-stopped.png')
    if max(abs(a - b) for a, b in zip(quiet_body, stopped_body)) > 2:
        failures.append('static body silhouette did not return after stop')
    samples = sorted(args.screenshots.glob('motion-*.png'))
    if len(samples) != 24:
        failures.append('missing 24-frame body motion sample')
    else:
        bounds = [body_bounds(p) for p in samples]
        travel = max(b[1] for b in bounds) - min(b[1] for b in bounds)
        bottom_travel = max(b[3] for b in bounds) - min(b[3] for b in bounds)
        print('pumpkin silhouette top/bottom travel:', travel, bottom_travel, 'pixels')
        if bottom_travel < 12:
            failures.append('static pumpkin remains behind the lifted body')
        lifted = min(range(len(bounds)), key=lambda i: bounds[i][3])
        with Image.open(samples[lifted]) as image:
            x = (quiet_body[0] + quiet_body[2]) // 2
            gap = image.convert('RGB').crop((x - 8, bounds[lifted][3] + 2, x + 8, quiet_body[3] - 2))
            if gap.height <= 0 or sum(sum(p) > 180 for p in gap.getdata()) < gap.width * gap.height * .7:
                failures.append('floor disappears underneath lifted pumpkin')
        if travel < 12:
            failures.append('pumpkin body remains visually stationary')
    if failures:
        raise SystemExit('Pumpkin visual regression: ' + '; '.join(failures))
    print('PASS cold eye fire, body movement, minimal particles, clean stop')


if __name__ == '__main__':
    main()

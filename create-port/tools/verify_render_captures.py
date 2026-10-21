#!/usr/bin/env python3
"""Reject real game captures where the test geometry or lava particles vanished.

The fixed camera fixture uses 1280x720. This checks image contents as well as
the Java lifecycle assertions, which alone can pass while nothing is drawn.
Pillow is a development-only dependency already present in the cloud.
"""
import argparse
import json
from pathlib import Path

from PIL import Image


def count_pixels(path, box, matches):
    with Image.open(path) as image:
        if image.size != (1280, 720):
            raise ValueError('Unexpected capture dimensions: ' + str(path))
        return sum(matches(*pixel) for pixel in image.convert('RGB').crop(box).get_flattened_data())


def verify(reports):
    counts = {}
    for backend in ['indirect', 'instancing']:
        path = reports / 'client-flywheel' / ('flywheel-' + backend + '-26.2.png')
        count = count_pixels(path, (500, 200, 710, 360),
                             lambda r, g, b: r > 150 and g > 115 and b < 120 and r > g * 1.08 and g > b * 1.2)
        if count < 500:
            raise ValueError('Flywheel cube missing from ' + backend + ' capture: ' + str(count))
        counts[backend + 'GoldPixels'] = count
    # The upward emitter reaches the top of this fixture. Its translucent lava
    # sprites appear pale yellow against the blue background and dark header.
    particles = count_pixels(reports / 'client-ponder/ponder-scene-5.png', (500, 20, 850, 260),
                             lambda r, g, b: r > 140 and g > 130 and b < 160 and r > g * 1.02 and g > b * 1.15)
    if particles < 8:
        raise ValueError('Lava particles missing from Ponder capture: ' + str(particles))
    counts['ponderLavaPixels'] = particles
    return {'status': 'PASSED', 'fixedFixture': '1280x720', 'pixelCounts': counts,
            'scope': 'Library rendering only, not Create gameplay, performance or exhaustive image equivalence'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('reports', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    result = verify(args.reports)
    text = json.dumps(result, indent=2) + '\n'
    if args.output:
        args.output.write_text(text)
    print(text, end='')


if __name__ == '__main__':
    main()

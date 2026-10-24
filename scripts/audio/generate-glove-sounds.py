#!/usr/bin/env python3
"""Author original mono punch Foley; only generated OGGs are shipped. Requires soundfile (pip install soundfile).

A brief filtered-noise slap sits over a falling low thump. The finisher adds a
short airy departure. Fixed seeds make rebuilding the assets reproducible.
"""
import math
from pathlib import Path
import random
import struct
import soundfile
import wave

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'src/main/resources/assets/mc_head_function/sounds/beheading'
RATE = 44100


def punch(index):
    duration = (0.18, 0.22, 0.38)[index - 1]
    rng = random.Random(8100 + index)
    phase = 0.0
    low_noise = 0.0
    samples = []
    for n in range(int(RATE * duration)):
        t = n / RATE
        noise = rng.uniform(-1, 1)
        low_noise += 0.23 * (noise - low_noise)
        attack = min(1.0, t / 0.0015)
        frequency = (58, 50, 43)[index - 1] + (115, 105, 95)[index - 1] * math.exp(-t * 36)
        phase += 2 * math.pi * frequency / RATE
        thump = math.sin(phase) * math.exp(-t * (32, 26, 22)[index - 1])
        slap = (noise - low_noise) * math.exp(-t * 115)
        body = low_noise * math.exp(-t * 35)
        value = attack * (0.72 * thump + 0.55 * slap + 0.65 * body)
        if index == 3 and t > 0.035:
            u = t - 0.035
            # A soft, fast whoosh, without a wet/cracking or explosive tail.
            value += 0.48 * low_noise * min(1, u / 0.025) * math.exp(-u * 15)
        value *= min(1, (duration - t) / 0.015)
        samples.append(value)
    peak = max(abs(x) for x in samples)
    return [x * (0.76, 0.83, 0.90)[index - 1] / peak for x in samples]


def write_wav(path, samples):
    with wave.open(str(path), 'wb') as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(RATE)
        out.writeframes(struct.pack('<' + 'h' * len(samples), *(round(x * 32767) for x in samples)))


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    demo = [0.0] * (RATE * 3)
    for index in (1, 2, 3):
        samples = punch(index)
        soundfile.write(OUT / f'punch_{index}.ogg', samples, RATE, format='OGG', subtype='VORBIS')
        offset = int((0.3 + (index - 1) * 0.65) * RATE)
        demo[offset:offset + len(samples)] = samples
    preview = ROOT / 'build/beheading-audio-preview.wav'
    preview.parent.mkdir(exist_ok=True)
    write_wav(preview, demo)
    print(preview)


if __name__ == '__main__':
    main()

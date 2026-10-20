#!/usr/bin/env python3
"""Reproduce the mod's original procedural mono effects; requires ffmpeg, no samples.

PCM synthesis uses deterministic noise, layered oscillators and soft limiting.
The generated Vorbis files are runtime assets, so Python/ffmpeg are not required to play.
"""
from pathlib import Path
import math
import random
import struct
import subprocess
import tempfile
import wave

RATE = 22050
DEST = Path(__file__).resolve().parents[1] / "src/main/resources/assets/celestial_dragon/sounds"


def synth(name, seconds):
    rng = random.Random(0xD6A60 + sum(map(ord, name)))
    low_noise = 0.0
    phase = 0.0
    samples = []
    for index in range(int(seconds * RATE)):
        t = index / RATE
        p = t / seconds
        noise = rng.uniform(-1, 1)
        low_noise = low_noise * 0.93 + noise * 0.07
        if name in ("roar", "hurt", "death"):
            attack = min(1.0, t / 0.09)
            envelope = attack * (1 - p) ** (0.65 if name == "death" else 0.9)
            fundamental = (76 if name == "roar" else 105 if name == "hurt" else 82) * (1 - p * 0.56)
            phase += 2 * math.pi * fundamental / RATE
            growl = sum(math.sin(phase * harmonic + math.sin(t * 19) * 0.9) / harmonic
                        for harmonic in (1, 2, 3, 5, 7))
            throat = math.sin(t * 2 * math.pi * (430 - p * 180)) * 0.15
            flutter = 0.72 + math.sin(t * 29) * 0.14 + math.sin(t * 47) * 0.08
            sample = envelope * flutter * (growl * 0.55 + low_noise * 2.1 + throat)
        elif name == "bite":
            snap = math.exp(-t * 27) * (noise * 0.58 + math.sin(t * 2 * math.pi * (320 - t * 600)) * 0.45)
            crunch = math.exp(-((t - 0.09) / 0.045) ** 2) * (noise * 0.55 + low_noise)
            sample = snap + crunch + math.sin(t * 2 * math.pi * 66) * math.exp(-t * 14) * 0.3
        elif name == "beam":
            envelope = min(1, t / 0.18) * min(1, (seconds - t) / 0.28)
            charge = math.sin(t * 2 * math.pi * (160 + 280 * p)) * (1 - p) * 0.15
            core = sum(math.sin(t * 2 * math.pi * frequency + math.sin(t * 12) * 1.5) * strength
                       for frequency, strength in ((72, 0.25), (144, 0.12), (432, 0.09), (864, 0.04)))
            sample = envelope * (core + low_noise * 1.1 + noise * 0.055 + charge)
        else:
            # Breath-like wind with nonverbal pentatonic bell partials, deliberately quiet.
            envelope = math.sin(math.pi * p) ** 0.7
            sample = low_noise * 0.22 * envelope
            for start, frequency in ((0.5, 523.25), (2.1, 783.99), (4.1, 659.25), (6.0, 1046.5)):
                elapsed = t - start
                if elapsed > 0:
                    bell = sum(math.sin(2 * math.pi * frequency * ratio * elapsed) * strength
                               for ratio, strength in ((1, 1), (2.01, 0.28), (3.97, 0.12)))
                    sample += bell * math.exp(-elapsed * 1.8) * min(1, elapsed * 25) * 0.13 * envelope
        samples.append(math.tanh(sample * 1.3))
    peak = max(abs(sample) for sample in samples) or 1
    gain = (0.55 if name == "ambience" else 0.78) / peak
    DEST.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="celestial-audio-") as temporary:
        pcm = Path(temporary) / f"{name}.wav"
        with wave.open(str(pcm), "wb") as output:
            output.setnchannels(1)
            output.setsampwidth(2)
            output.setframerate(RATE)
            output.writeframes(b"".join(struct.pack("<h", int(sample * gain * 32767)) for sample in samples))
        subprocess.run(["ffmpeg", "-nostdin", "-hide_banner", "-loglevel", "error", "-y", "-i", str(pcm),
                        "-c:a", "libvorbis", "-q:a", "4", str(DEST / f"{name}.ogg")], check=True)


if __name__ == "__main__":
    for sound, duration in (("roar", 2.8), ("bite", 0.40), ("beam", 1.35), ("hurt", 0.85),
                            ("death", 3.7), ("ambience", 8.5)):
        synth(sound, duration)
        print(f"Generated {sound}.ogg ({duration}s)")

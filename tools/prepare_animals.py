#!/usr/bin/env python3
"""Rebuild the public-domain / CC animal clips in app/src/main/res/raw (animal_<name>.ogg) from Wikimedia Commons.

  tools/prepare_animals.py [download dir]

Each clip: one cut from the source recording (start, length in s below), mono 44.1 kHz, 80 Hz high-pass, 8 ms fade-in and
60 ms fade-out, then loudness-matched to the private animal clips (RMS over the loud part about -19 dBFS, the same as
animal_dog/cow/lion) with the peak held at -1 dBFS, and encoded as Ogg Vorbis q4. Sources and licences: SOUND_CREDITS.md.
Needs ffmpeg and numpy.
"""
import os, subprocess, sys, urllib.parse, urllib.request
import numpy as np

RATE = 44100
RAW = "app/src/main/res/raw"
UA = {"User-Agent": "TapZoo-prepare-animals/1.0 (github.com/PeytonRingling/tap-zoo)"}
# name: (Commons file, start s, length s)
CLIPS = {
    "horse":   ("Mares-Prefer-the-Voices-of-Highly-Fertile-Stallions-pone.0118468.s002.oga", 0.66, 2.05),
    "sheep":   ("Sheep bleating.ogg", 0.58, 1.12),
    "rooster": ("Medium rooster crowing.ogg", 2.86, 2.25),
    "owl":     ("Barred Owl, Yellowstone National Park.oga", 1.80, 0.95),
    "donkey":  ("157763 felix-blume a-donkey-is-braying-in-his-enclosure-in-south-of-france.wav", 6.30, 2.10),
}
TARGET_RMS_DB = -19.0
PEAK = 10 ** (-1 / 20)

def fetch(title, d):
    path = os.path.join(d, title.replace(" ", "_"))
    if not os.path.exists(path):
        url = "https://commons.wikimedia.org/wiki/Special:FilePath/" + urllib.parse.quote(title.replace(" ", "_"))
        with urllib.request.urlopen(urllib.request.Request(url, headers=UA)) as r, open(path, "wb") as f: f.write(r.read())
    return path

def decode(path, start, length):
    pcm = subprocess.run(["ffmpeg", "-v", "error", "-ss", str(start), "-t", str(length), "-i", path, "-ac", "1", "-ar", str(RATE),
                          "-af", "highpass=f=80", "-f", "f32le", "-"], check=True, capture_output=True).stdout
    return np.frombuffer(pcm, dtype=np.float32).astype(np.float64)

def loud_rms(x):
    """RMS over the 10 ms windows within 30 dB of the loudest one (ignores the quiet gaps, like a person would)."""
    w = int(0.01 * RATE); r = np.sqrt([np.mean(x[i:i + w] ** 2) for i in range(0, len(x) - w, w)])
    return float(np.sqrt(np.mean(r[r > r.max() * 10 ** (-30 / 20)] ** 2)))

def shape(x):
    fi, fo = int(0.008 * RATE), int(0.06 * RATE)
    x = x.copy(); x[:fi] *= np.linspace(0, 1, fi); x[-fo:] *= np.linspace(1, 0, fo)
    g = 10 ** (TARGET_RMS_DB / 20) / loud_rms(x)
    g = min(g, PEAK / np.abs(x).max())
    return x * g, 20 * np.log10(g)

def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "/tmp/tapzoo-animals"; os.makedirs(d, exist_ok=True)
    for name, (title, start, length) in CLIPS.items():
        y, gdb = shape(decode(fetch(title, d), start, length))
        out = f"{RAW}/animal_{name}.ogg"
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "f64le", "-ac", "1", "-ar", str(RATE), "-i", "-", "-c:a", "libvorbis", "-q:a", "4",
                        "-map_metadata", "-1", out], input=y.tobytes(), check=True)
        print(f"{out}: {len(y) / RATE:.2f} s, gain {gdb:+.1f} dB, loud RMS {20 * np.log10(loud_rms(y)):.1f} dBFS, peak {20 * np.log10(np.abs(y).max()):.1f} dBFS, {os.path.getsize(out)} B")

if __name__ == "__main__":
    main()

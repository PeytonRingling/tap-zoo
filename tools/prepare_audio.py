#!/usr/bin/env python3
"""Rebuild the downloaded audio in app/src/main/res/raw from its free sources (see SOUND_CREDITS.md).

  tools/prepare_audio.py <dir with the incompetech mp3s> <free-pop-F.mp3>

* music_balloons.ogg: "Monkeys Spinning Monkeys" (Kevin MacLeod, CC BY 4.0). The whole track, which is written to loop: exactly
  75 bars (125.0 s at 144 BPM) from its first onset, so the MP3's leading/trailing encoder silence (~77 ms) doesn't stutter the loop.
* music_bubbles.ogg:  "Beachfront Celebration" (Kevin MacLeod, CC BY 4.0). 28 bars (56.0 s at 120 BPM) from 43.88 s, with a
  20 ms crossfade baked into the seam so MediaPlayer's loop is click-free.
* pop_balloon.wav:    the first hit of free-pop-F (LilMati 506546 pop layered with Breviceps 458398 crack, both CC0). It's 16-bit
  PCM so the app's own mixer can read it directly and pitch it per balloon.
Both tracks are loudness-matched (about -16 LUFS) and encoded as OGG Vorbis (it loops gaplessly on Android; MP3 doesn't).
Needs ffmpeg and numpy.
"""
import json, subprocess, sys, wave
import numpy as np

RATE = 44100
RAW = "app/src/main/res/raw"

def decode(path, ch):
    pcm = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-f", "f32le", "-ac", str(ch), "-ar", str(RATE), "-"],
                         check=True, capture_output=True).stdout
    return np.frombuffer(pcm, dtype=np.float32).reshape(-1, ch).copy()

def loudness_gain(x, target=-16.0):
    """Gain (linear) that brings x to the target integrated loudness, measured by ffmpeg's ebur128."""
    p = subprocess.run(["ffmpeg", "-v", "info", "-f", "f32le", "-ac", str(x.shape[1]), "-ar", str(RATE), "-i", "-",
                        "-af", "ebur128", "-f", "null", "-"], input=x.astype(np.float32).tobytes(), capture_output=True, check=True)
    line = [l for l in p.stderr.decode().splitlines() if l.strip().startswith("I:")][-1]
    lufs = float(line.split()[1])
    return 10 ** ((target - lufs) / 20), lufs

def encode_ogg(x, out):
    g, lufs = loudness_gain(x)
    y = x * g
    peak = float(np.abs(y).max())
    if peak > 0.97: y *= 0.97 / peak  # never clip
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ac", str(x.shape[1]), "-ar", str(RATE), "-i", "-",
                    "-c:a", "libvorbis", "-q:a", "4", "-map_metadata", "-1", out], input=y.astype(np.float32).tobytes(), check=True)
    return {"file": out, "seconds": round(len(x) / RATE, 3), "source_lufs": lufs, "gain_db": round(20 * np.log10(g), 2)}

def loop_cut(x, start_s, length_s, fade_s=0.02):
    s, n, f = int(round(start_s * RATE)), int(round(length_s * RATE)), int(round(fade_s * RATE))
    out = x[s:s + n].copy()
    ramp = np.linspace(0, 1, f, endpoint=False)[:, None]
    out[:f] = x[s:s + f] * ramp + x[s + n:s + n + f] * (1 - ramp)  # the start already "contains" the tail's continuation
    return out

def whole_track_loop(x, bars, bpm):
    on = int(np.argmax(np.abs(x).max(axis=1) > 0.003))
    return x[on:on + int(round(bars * 4 * 60 / bpm * RATE))].copy()

def pop_first_hit(path, out):
    x = decode(path, 1)[:, 0]
    env = np.abs(x) > 0.02
    on = int(np.argmax(env))
    # The preview repeats the hit 3x with 0.7 s gaps: the first hit ends where the signal stays quiet for 50 ms.
    quiet = np.convolve(~env[on:], np.ones(int(0.05 * RATE)), "valid") >= int(0.05 * RATE)
    end = on + int(np.argmax(quiet)) + 1
    y = x[max(0, on - 22):end].copy()
    fo = min(len(y), int(0.01 * RATE)); y[-fo:] *= np.linspace(1, 0, fo)
    y *= 0.89 / max(1e-9, np.abs(y).max())  # -1 dBFS peak
    with wave.open(out, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(RATE)
        w.writeframes((np.clip(y, -1, 1) * 32767).astype("<i2").tobytes())
    return {"file": out, "ms": round(len(y) * 1000 / RATE, 1)}

if __name__ == "__main__":
    src, pop = sys.argv[1], sys.argv[2]
    report = [
        encode_ogg(whole_track_loop(decode(f"{src}/Monkeys Spinning Monkeys.mp3", 2), 75, 144), f"{RAW}/music_balloons.ogg"),
        encode_ogg(loop_cut(decode(f"{src}/Beachfront Celebration.mp3", 2), 43.88, 56.0), f"{RAW}/music_bubbles.ogg"),
        pop_first_hit(pop, f"{RAW}/pop_balloon.wav"),
    ]
    print(json.dumps(report, indent=1))

"""Original music composed in code -> seamless loops. python3 music.py"""
import math, struct, wave, random
SR = 22050
def midi(n): return 440 * 2 ** ((n - 69) / 12)
def render(notes, length, out, timbre):
    buf = [0.0] * int(SR * length)
    for t0, dur, n, vel in notes:
        f = midi(n); s0 = int(t0 * SR); m = int((dur + 1.5) * SR)
        for i in range(m):
            t = i / SR; v = timbre(f, t, dur) * vel
            buf[(s0 + i) % len(buf)] += v          # wrap tails -> seamless loop
    pk = max(abs(x) for x in buf); buf = [x / pk * 0.8 for x in buf]
    with wave.open(out, 'w') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes(b''.join(struct.pack('<h', int(x * 32767)) for x in buf))
def musicbox(f, t, d):
    e = math.exp(-t * 3.2) * min(1, t / 0.004)
    return e * (math.sin(2*math.pi*f*t) + 0.3*math.sin(2*math.pi*2*f*t)*math.exp(-t*6) + 0.12*math.sin(2*math.pi*4.01*f*t)*math.exp(-t*10))
def pad(f, t, d):
    a = min(1, t / 0.4) * (1 if t < d else max(0, 1 - (t - d) / 1.2))
    return a * (math.sin(2*math.pi*f*t) + 0.2*math.sin(2*math.pi*2*f*t)) * 0.35
def mix(*fs): return lambda f, t, d: sum(fn(f, t, d) for fn in fs)

# --- Menu: cheerful music-box waltz-ish loop, C pentatonic, 96 bpm, 16 bars of 4 beats
b = 60 / 96; mel = []
phrase = [72,74,76,79, 76,74,72,74, 76,79,81,79, 76,74,76,-1,
          79,81,84,81, 79,76,74,76, 72,74,76,74, 72,-1,67,-1]
for k, n in enumerate(phrase * 2):
    if n > 0: mel.append((k * b, b * 0.9, n, 0.55 if k % 4 else 0.7))
chords = [48, 45, 41, 43] * 4
bass = [(k * 4 * b, 4 * b, c, 0.5) for k, c in enumerate(chords)]
arp = [(k * 4 * b + j * b, b, c + 12 + [0, 7, 12, 7][j], 0.18) for k, c in enumerate(chords) for j in range(4)]
render(mel + arp, 64 * b, 'menu.wav', musicbox)
# overlay soft pad
render(bass, 64 * b, 'menu_pad.wav', pad)

# --- Bedtime lullaby: slow 3/4 at 60 bpm, gentle sine bells over warm pad
b = 1.0; lul = [76,-1,74, 72,-1,-1, 74,76,79, 76,-1,-1, 72,-1,74, 72,69,-1, 67,-1,-1, -1,-1,-1,
                76,-1,79, 81,-1,79, 76,-1,74, 72,-1,-1, 74,-1,72, 69,-1,67, 72,-1,-1, -1,-1,-1]
notes = [(k * b, b * 1.6, n, 0.5) for k, n in enumerate(lul) if n > 0]
ch = [(k * 3 * b, 3 * b, c, 0.45) for k, c in enumerate([48, 41, 45, 43, 48, 41, 43, 48, 48, 45, 41, 43, 45, 41, 43, 48])]
render(notes, len(lul) * b, 'lullaby_mel.wav', lambda f, t, d: math.exp(-t*1.4)*min(1,t/0.02)*(math.sin(2*math.pi*f*t)+0.15*math.sin(2*math.pi*2*f*t)))
render(ch, len(lul) * b, 'lullaby_pad.wav', pad)
print('ok')

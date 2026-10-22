"""Formant synthesis of cartoon animal voices (original, generated here -> no license issues).
Usage: python3 voices.py  -> writes ribbit.wav, roar.wav"""
import math, random, struct, wave
SR = 22050
random.seed(3)

def bandpass(x, f, q):
    w = 2*math.pi*f/SR; a = math.sin(w)/(2*q); c = math.cos(w)
    b0, b2, a0, a1, a2 = a, -a, 1+a, -2*c, 1-a
    y = []; x1=x2=y1=y2=0.0
    for s in x:
        o = (b0*s + b2*x2 - a1*y1 - a2*y2)/a0
        x2, x1, y2, y1 = x1, s, y1, o; y.append(o)
    return y

def voice(dur, pitch, formants, amp, buzz=0.0, breath=0.0, am=None):
    """pitch/amp/formants are functions of normalized time u in [0,1]. Glottal-ish pulse source."""
    n = int(SR*dur); src = []; ph = 0.0
    for i in range(n):
        u = i/n; f0 = pitch(u); ph += f0/SR
        if ph >= 1: ph -= 1
        g = (1-ph)**3 * 2 - 0.5           # sharp glottal pulse (rich harmonics)
        g += buzz*(random.random()*2-1)
        src.append(g)
    out = [0.0]*n
    # time-varying formants: process in 3 overlapping segments, crossfade
    segs = 6
    for k in range(segs):
        u = (k+0.5)/segs
        fs = formants(u)
        part = [0.0]*n
        for f, q, gain in fs:
            y = bandpass(src, f, q)
            for i in range(n): part[i] += y[i]*gain
        for i in range(n):
            w = max(0.0, 1-abs(i/n*segs-(k+0.5)))  # triangular crossfade window
            out[i] += part[i]*w
    for i in range(n):
        u = i/n; e = amp(u)
        if am: e *= am(i/SR)
        out[i] = out[i]*e + breath*e*(random.random()*2-1)*0.3
    return out

def norm(x, peak=0.89):
    m = max(abs(v) for v in x) or 1; return [v*peak/m for v in x]

def env(a, s, r):  # attack/sustain/release fractions -> function
    return lambda u: min(1, u/a) if u < a else (1 if u < 1-r else max(0, (1-u)/r))

def save(name, x):
    with wave.open(name, 'w') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes(b''.join(struct.pack('<h', int(max(-1, min(1, v))*32767)) for v in x))

# Cartoon "rib-bit!": two croaky syllables with an 'i' vowel, croak = fast amplitude flutter.
def syll(dur, p0, p1):
    return voice(dur, lambda u: p0 + (p1-p0)*u,
                 lambda u: [(300 + 150*u, 6, 1.0), (2100, 8, 0.45), (2900, 10, 0.2)],
                 env(0.08, 0, 0.35), buzz=0.15, am=lambda t: 0.55 + 0.45*abs(math.sin(math.pi*38*t)))
rib = syll(0.13, 190, 230); bit = syll(0.17, 250, 200)
gap = [0.0]*int(SR*0.055)
click = [ (random.random()*2-1)*math.exp(-i/40) for i in range(200) ]
r = norm(click[:120] + rib + gap + click[:80] + bit + [0.0]*int(SR*0.05))
save('ribbit.wav', r)

# Friendly cartoon "rawr!": short, rising-then-falling pitch, open 'a' -> rounded 'r', light growl.
roar = voice(0.75, lambda u: 150 + 120*math.sin(math.pi*min(1, u*1.3)) - 40*u,
             lambda u: [(750 - 250*u, 5, 1.0), (1250 - 300*u, 7, 0.6), (2600, 9, 0.15)],
             env(0.12, 0, 0.4), buzz=0.25, breath=0.15, am=lambda t: 0.8 + 0.2*math.sin(2*math.pi*24*t))
save('roar.wav', norm(roar))
print('ok')

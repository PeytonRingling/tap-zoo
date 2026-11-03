"""Original upbeat toddler loop for Balloons mode, composed in code (Peyton's Tap Zoo). Seamless: tails wrap to start."""
import numpy as np, wave
SR=32000; BPM=116; b=60/BPM; BARS=16; L=int(SR*BARS*4*b)
buf=np.zeros(L); rng=np.random.default_rng(7)
def f(n): return 440*2**((n-69)/12)
def add(sig,t0,g=1.0):
    s=int(t0*SR); idx=(np.arange(len(sig))+s)%L; np.add.at(buf,idx,sig*g)
def marimba(n,d=0.5):
    t=np.arange(int(SR*0.9))/SR; fr=f(n)
    return (np.sin(2*np.pi*fr*t)*np.exp(-t*7)+0.35*np.sin(2*np.pi*4*fr*t)*np.exp(-t*30)+0.15*np.sin(2*np.pi*10*fr*t)*np.exp(-t*60))*np.minimum(1,t/0.002)
def glock(n):
    t=np.arange(int(SR*1.2))/SR; fr=f(n)
    return (np.sin(2*np.pi*fr*t)+0.25*np.sin(2*np.pi*2.76*fr*t)*np.exp(-t*8))*np.exp(-t*4)*np.minimum(1,t/0.001)
def pluck(n,dur=0.6):  # Karplus-Strong ukulele-ish
    N=int(SR/f(n)); x=rng.uniform(-1,1,N); out=np.zeros(int(SR*dur))
    for i in range(len(out)):
        out[i]=x[i%N]; x[i%N]=0.5*(x[i%N]+x[(i+1)%N])*0.996
    return out*np.minimum(1,np.arange(len(out))/(SR*0.003))
def bass(n,d):
    t=np.arange(int(SR*d))/SR; fr=f(n)
    s=np.sin(2*np.pi*fr*t)+0.3*np.sin(2*np.pi*2*fr*t)
    return s*np.exp(-t*3)*np.minimum(1,t/0.005)*np.minimum(1,(d-t)/0.03)
def shaker():
    t=np.arange(int(SR*0.06))/SR; n=rng.normal(0,1,len(t)); n=np.diff(n,prepend=0)
    return n*np.exp(-t*70)
def clap():
    t=np.arange(int(SR*0.15))/SR; n=rng.normal(0,1,len(t))
    env=np.exp(-t*40)+0.6*np.exp(-np.maximum(0,t-0.012)*45)*(t>0.012)
    return np.convolve(n*env,np.ones(3)/3,'same')
# F major: chords per bar (root midi): F C Dm Bb | F C Bb C  ... A then B section Bb F Gm C | Dm Bb C F
prog=[(41,[0,4,7]),(36,[0,4,7]),(38,[0,3,7]),(34,[0,4,7]),(41,[0,4,7]),(36,[0,4,7]),(34,[0,4,7]),(36,[0,4,7]),
      (34,[0,4,7]),(41,[0,4,7]),(43,[0,3,7]),(36,[0,4,7]),(38,[0,3,7]),(34,[0,4,7]),(36,[0,4,7]),(41,[0,4,7])]
# melody in eighths (-1 rest), 8 per bar
A=[77,-1,77,79,81,-1,77,-1, 79,-1,76,-1,72,-1,-1,-1, 74,-1,74,76,77,-1,81,-1, 82,-1,81,79,77,-1,-1,-1,
   77,-1,77,79,81,-1,84,-1, 79,-1,79,81,79,-1,76,-1, 74,-1,77,-1,79,-1,82,-1, 81,-1,79,-1,76,-1,-1,-1]
B=[82,-1,81,-1,79,-1,77,-1, 81,-1,77,-1,72,-1,77,-1, 79,-1,79,81,82,-1,79,-1, 76,-1,72,-1,-1,-1,72,74,
   77,-1,76,77,81,-1,77,-1, 74,-1,77,-1,82,-1,81,-1, 79,-1,84,-1,81,-1,79,-1, 77,-1,-1,-1,84,-1,-1,-1]
e=b/2
for k,n in enumerate(A+B):
    if n>0:
        sw=0.0 if k%2==0 else e*0.08  # light swing bounce
        add(marimba(n),k*e+sw,0.55)
        if k%8==0: add(glock(n+12),k*e,0.12)
for bar,(r,iv) in enumerate(prog):
    t0=bar*4*b
    add(bass(r,b*0.9),t0,0.55); add(bass(r+7,b*0.9),t0+2*b,0.45)
    add(bass(r+12 if bar%2 else r,b*0.4),t0+3.5*b,0.25)
    for beat in (1,2,3):  # ukulele off-beat strums
        for j,i in enumerate(iv): add(pluck(r+24+i,0.5),t0+beat*b+j*0.012,0.16 if beat!=2 else 0.12)
    for s in range(8): add(shaker(),t0+s*e,0.10 if s%2 else 0.05)
    for c in (1,3): add(clap(),t0+c*b,0.10)
buf=np.tanh(buf/np.max(np.abs(buf))*1.3); buf=buf/np.max(np.abs(buf))*0.89
with wave.open('balloons.wav','w') as w:  # scratch render (git-ignored); encode to res/raw/music_balloons.ogg
    w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes((buf*32767).astype('<i2').tobytes())
print(L/SR,'s')

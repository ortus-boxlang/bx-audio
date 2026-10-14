#!/usr/bin/env python3
"""Compare two WAV files: length, level, octave-band balance, loudness envelopes and timing.

This is how the BoxLang port in pop-drive.bxs was checked against the original numpy script. Usage:

    python3 compare-with-python.py reference.wav boxlang.wav

Needs numpy. Both files should be 16 bit PCM at the same sample rate.
"""
import sys, wave, numpy as np
def load(p):
    w=wave.open(p); n=w.getnframes(); ch=w.getnchannels(); sr=w.getframerate(); sw=w.getsampwidth()
    d=np.frombuffer(w.readframes(n),dtype=np.int16 if sw==2 else np.int32).reshape(-1,ch)/ (32768.0 if sw==2 else 2147483648.0)
    return d,sr
a,sr=load(sys.argv[1]); b,sr2=load(sys.argv[2])
print("format  ref:", a.shape, sr, " mine:", b.shape, sr2)
n=min(len(a),len(b))
print("frames  ref %d mine %d (diff %d)"%(len(a),len(b),len(a)-len(b)))
for name,x in (("ref",a),("mine",b)):
    print("%-5s peak %.3f rms %.4f  L/R rms %.4f/%.4f"%(name,np.abs(x).max(),np.sqrt((x**2).mean()),np.sqrt((x[:,0]**2).mean()),np.sqrt((x[:,1]**2).mean())))
ma=a[:n].mean(1); mb=b[:n].mean(1)
# octave band energy
def bands(x):
    X=np.abs(np.fft.rfft(x))**2; f=np.fft.rfftfreq(len(x),1/sr)
    edges=[20,40,80,160,320,640,1280,2560,5120,10240,20480]
    out=[]
    for lo,hi in zip(edges[:-1],edges[1:]):
        out.append(X[(f>=lo)&(f<hi)].sum())
    out=np.array(out); return 10*np.log10(out/out.sum()+1e-12)
ba,bb=bands(ma),bands(mb)
print("octave band share (dB re total)   band        ref     mine    diff")
for lo,hi,x,y in zip([20,40,80,160,320,640,1280,2560,5120,10240],[40,80,160,320,640,1280,2560,5120,10240,20480],ba,bb):
    print("                                  %5d-%-5d %7.1f %7.1f %7.1f"%(lo,hi,x,y,y-x))
# short time rms envelope correlation, whole mix and low band (kick)
def env(x,win=int(0.02*sr)):
    m=len(x)//win; return np.sqrt((x[:m*win].reshape(m,win)**2).mean(1))
ea,eb=env(ma),env(mb)
print("envelope correlation (20ms rms, full mix): %.3f"%np.corrcoef(ea,eb)[0,1])
def lowpass(x,fc):
    k=int(sr/fc); ker=np.ones(k)/k
    return np.convolve(x,ker,mode='same')
la,lb=env(lowpass(ma,150)),env(lowpass(mb,150))
print("envelope correlation (<150 Hz, kick/bass): %.3f"%np.corrcoef(la,lb)[0,1])
ha=env(ma-lowpass(ma,4000)); hb=env(mb-lowpass(mb,4000))
print("envelope correlation (>4 kHz, hats/claps): %.3f"%np.corrcoef(ha,hb)[0,1])
# timing: lag of the best cross-correlation of the kick/bass envelope, in ms (0 means perfectly aligned)
c=np.correlate(la-la.mean(),lb-lb.mean(),mode='full'); lag=(np.argmax(c)-(len(lb)-1))*0.02
print("kick/bass envelope lag: %.0f ms"%(lag*1000))
# loudness by section (4 seconds each) to see where any level difference lives
sec=int(4*sr); print("section rms ref/mine (dB diff):", " ".join("%+.1f"%(20*np.log10((np.sqrt((mb[i:i+sec]**2).mean())+1e-9)/(np.sqrt((ma[i:i+sec]**2).mean())+1e-9))) for i in range(0,n-sec,sec)))

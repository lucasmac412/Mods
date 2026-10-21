#!/usr/bin/env python3
"""Original layered creature/energy sound design. Build tool: numpy, scipy, ffmpeg.
No recordings, commercial samples, voices or model weights are used.
Runtime assets are mono Vorbis 44.1 kHz; the game needs no Python packages.
"""
from pathlib import Path
import hashlib, json, subprocess, tempfile, wave
import numpy as np
from scipy.signal import butter, sosfilt

RATE=44100
DEST=Path(__file__).resolve().parents[1]/'src/main/resources/assets/celestial_dragon/sounds'
DURATIONS={'roar':3.8,'roar_2':3.3,'hurt':1.15,'hurt_2':1.3,'death':5.1,'bite':.65,'bite_2':.7,
           'beam':1.6,'beam_charge':.5,'spirit_charge':2.,'spirit_launch':1.8,'spirit_impact':3.8,
           'breath':2.2,'flight':1.2,'ambience':10.}

def band(x,low,high):
    return sosfilt(butter(2,[low,high],btype='bandpass',fs=RATE,output='sos'),x)

def envelope(t,duration,attack=.08,release=.35):
    return np.minimum(1,t/attack)**1.5*np.minimum(1,np.maximum(0,duration-t)/release)**1.5

def synth(name,duration):
    rng=np.random.default_rng(int.from_bytes(hashlib.sha256(name.encode()).digest()[:4],'little'))
    t=np.arange(round(duration*RATE))/RATE;p=t/duration;n=rng.standard_normal(t.size)
    air=band(n,180,3200);rumble=band(n,28,180)
    if name.startswith(('roar','hurt')) or name=='death':
        hurt=name.startswith('hurt');death=name=='death'
        pitch=(92 if hurt else 59 if death else 67)*(1-.45*p)
        pitch*=1+.035*np.sin(2*np.pi*t*4.7)+.022*band(n,1,14)
        phase=2*np.pi*np.cumsum(pitch)/RATE
        # Irregular glottal pulses filtered into throat/mouth resonances, plus breath turbulence.
        glottis=sum(np.sin(phase*h+.13*np.sin(t*8+h))*(1/h**.7) for h in range(1,25))
        voice=.52*band(glottis,90,380)+.30*band(glottis,420,1050)+.13*band(glottis,1400,2600)
        rasp=air*(.22+.12*np.sin(phase*.48))
        form=np.exp(-((p-.22)/.20)**2)+.72*np.exp(-((p-.60)/.25)**2)
        x=(voice+rasp+.30*rumble)*form*envelope(t,duration,.07 if hurt else .18,.5)
        if death:x+=.3*air*np.exp(-((p-.7)/.15)**2)
    elif name.startswith('bite'):
        # Short jaw whoosh, bone-like impact and noisy crunch; no piercing pure tone.
        whoosh=air*np.exp(-((t-.07)/.045)**2)*.4
        impact=(band(n,90,1800)+.5*rumble)*np.exp(-np.maximum(0,t-.12)*22)*(t>=.12)
        cracks=sum(band(n,900,6500)*np.exp(-((t-start)/.009)**2)*gain for start,gain in [(.125,.5),(.17,.35),(.22,.2)])
        x=(whoosh+impact+cracks)*envelope(t,duration,.005,.12)
    elif name in ('beam','beam_charge','spirit_charge','spirit_launch','spirit_impact'):
        charge=name.endswith('charge');impact=name.endswith('impact')
        freq=(55+190*p*p) if charge else (58-20*p)
        phase=2*np.pi*np.cumsum(freq)/RATE
        low=.32*np.sin(phase)+.11*np.sin(phase*1.99)+.06*np.sin(phase*3.01)
        texture=band(n,100,2200 if charge else 5200)
        arcs=np.zeros_like(t)
        for at in rng.uniform(.05,duration-.05,12 if impact else 7):
            arcs+=band(n,1600,8500)*np.exp(-((t-at)/.008)**2)*.2
        if charge:x=(low*.5+texture*(.06+.22*p)+arcs)*np.sin(p*np.pi/2)*envelope(t,duration,.08,.08)
        elif impact:x=(rumble*1.4+texture*.65+low*.45+arcs)*np.exp(-t*1.0)*envelope(t,duration,.012,.8)
        else:x=(low*.5+texture*.55+arcs)*envelope(t,duration,.06,.4)
    elif name=='breath':
        x=(band(n,90,950)*.5+air*.08)*np.sin(np.pi*p)**2*(.85+.15*np.sin(t*5))
    elif name=='flight':
        x=(band(n,100,1900)*.6+rumble*.3)*envelope(t,duration,.15,.2)
    else:
        x=(band(n,90,1000)*.22+rumble*.18)*np.sin(np.pi*p)**1.5
        for at,freq in [(1.0,293.66),(3.5,440),(6.4,349.23)]:
            dt=np.maximum(t-at,0);x+=.035*np.sin(2*np.pi*freq*dt)*np.exp(-dt*.9)*(t>=at)*np.minimum(1,dt*15)
    # Small early reflections add body; high-pass removes DC/subsonic energy.
    dry=x.copy()
    for delay,gain in [(.037,.12),(.081,.09),(.137,.055)]:
        offset=round(delay*RATE);x[offset:]+=dry[:-offset]*gain
    x=sosfilt(butter(2,28,btype='highpass',fs=RATE,output='sos'),x)
    x=np.tanh(x*1.25);x*=envelope(t,duration,.008,.06)
    peak=max(float(np.max(np.abs(x))),1e-8)
    x*=.68/peak if name not in ('ambience','breath','flight') else .40/peak
    DEST.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='celestial-sound-') as temp:
        wav=Path(temp)/(name+'.wav')
        with wave.open(str(wav),'wb') as out:
            out.setnchannels(1);out.setsampwidth(2);out.setframerate(RATE);out.writeframes((x*32767).astype('<i2').tobytes())
        subprocess.run(['ffmpeg','-nostdin','-hide_banner','-loglevel','error','-y','-i',str(wav),'-c:a','libvorbis','-q:a','6',str(DEST/(name+'.ogg'))],check=True)
    return {'name':name,'seconds':duration,'sampleRate':RATE,'sha256':hashlib.sha256((DEST/(name+'.ogg')).read_bytes()).hexdigest()}

if __name__=='__main__':
    report=[synth(name,duration) for name,duration in DURATIONS.items()]
    print(json.dumps(report,indent=2))

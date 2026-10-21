#!/usr/bin/env python3
"""Recorded-source creature sound design; no oscillator/noise-generated vocal cords.
Sources, attribution and licenses: third-party/dragon-audio. Output: CC BY-SA 3.0.
Requires numpy/scipy/ffmpeg to regenerate; the game only loads the bundled Vorbis files.
"""
from pathlib import Path
import argparse,hashlib,json,subprocess,tempfile,wave
import numpy as np
from scipy.signal import butter,sosfilt

RATE=44100
ROOT=Path(__file__).resolve().parents[2]
SOURCES=ROOT/'third-party/dragon-audio/sources'
DEST=ROOT/'dragon-mod/src/main/resources/assets/celestial_dragon/sounds'
DURATIONS={'roar':6.8,'roar_2':6.8,'hurt':1.05,'hurt_2':1.1,'death':4.4,'bite':.42,'bite_2':.45,
           'beam':1.6,'beam_charge':.5,'spirit_charge':2.,'spirit_launch':1.8,'spirit_impact':3.8,
           'breath':2.2,'flight':1.2,'ambience':10.}


def filtered(x,low=45,high=4200):
    return sosfilt(butter(2,[low,high],btype='bandpass',fs=RATE,output='sos'),x)


def fade(x,attack=.03,release=.18):
    t=np.arange(len(x))/RATE;duration=len(x)/RATE
    return x*np.minimum(1,t/max(attack,1/RATE))*np.minimum(1,np.maximum(0,duration-t)/max(release,1/RATE))


def source(name,start=0,end=None,speed=1,tempo=1):
    raw=subprocess.check_output(['ffmpeg','-v','error','-i',str(SOURCES/name),'-ac','1','-ar',str(RATE),'-f','f32le','-'])
    x=np.frombuffer(raw,dtype='<f4').astype(float)
    x=x[round(start*RATE):None if end is None else round(end*RATE)]
    x=np.interp(np.arange(0,len(x)-1,speed),np.arange(len(x)),x)
    if tempo != 1:
        # High-quality independent time stretch: prolong the real articulation without
        # repeating the take or lowering its pitch yet another octave.
        raw=subprocess.check_output(['ffmpeg','-v','error','-f','f32le','-ar',str(RATE),'-ac','1',
            '-i','pipe:0','-af',f'rubberband=tempo={tempo}:transients=smooth',
            '-f','f32le','pipe:1'],input=x.astype('<f4').tobytes())
        x=np.frombuffer(raw,dtype='<f4').astype(float)
    return fade(filtered(x),.025,.12)


def add(output,clip,at=0,gain=1):
    start=round(at*RATE);count=min(len(clip),len(output)-start)
    if count>0:output[start:start+count]+=clip[:count]*gain


def tone(t,f0,f1):
    frequency=f0+(f1-f0)*np.linspace(0,1,len(t))
    return np.sin(2*np.pi*np.cumsum(frequency)/RATE)


def render(name,seconds):
    rng=np.random.default_rng(int.from_bytes(hashlib.sha256(('recorded-v3:'+name).encode()).digest()[:4],'little'))
    t=np.arange(round(seconds*RATE))/RATE;p=t/seconds;x=np.zeros(len(t))
    if name.startswith('roar'):
        second=name.endswith('_2')
        begin,end=(4.87,6.43) if second else (.42,2.24)
        # Foreground articulation is a real lion recording, not a waveform approximating one.
        primary=source('lion-roar.ogg',begin,end,.59 if second else .62,tempo=.70 if second else .72)
        add(x,filtered(primary,45,3000),.06,1.0)
        body=source('lion-roar.ogg',begin,end,.47,tempo=.82)
        add(x,filtered(body,42,240),.06,.24)
        # A different recorded exhalation carries the long tail, with overlap instead
        # of a looping waveform or a chain of electrical transients.
        tail_begin,tail_end=(.42,2.24) if second else (4.87,6.43)
        tail=source('lion-roar.ogg',tail_begin,tail_end,.68,tempo=.80)
        add(x,filtered(tail,48,2400),3.0 if second else 3.35,.68)
        throat=source('creature-death-2.wav' if second else 'creature-death-1.wav',speed=.78,tempo=.78)
        add(x,filtered(throat,65,1100),.12,.15)
    elif name.startswith('hurt'):
        n='2' if name.endswith('_2') else '1'
        add(x,source('creature-hit-'+n+'.wav',speed=.88),0,1.0)
        add(x,source('lion-roar.ogg',.65,1.05,.85),.04,.14)
    elif name=='death':
        add(x,source('creature-death-1.wav',speed=.70),0,1.0)
        add(x,filtered(source('lion-roar.ogg',1.25,2.22,.67),50,2400),1.9,.30)
    elif name.startswith('bite'):
        n='2' if name.endswith('_2') else '1'
        jaw=source('creature-hit-'+n+'.wav',0,.23,.96)
        add(x,filtered(jaw,90,1800),.01,.26)
        # Single dry jaw closure at the start of the sound, on the server hit tick.
        # Rounded pressure + a short band-limited crunch, not a delayed grunt.
        attack=1-np.exp(-t*900)
        x+=.25*tone(t,112,58)*np.exp(-t*32)*attack
        crunch=filtered(rng.standard_normal(len(t)),350,2400)
        x+=crunch*.12*np.exp(-t*65)*attack
    elif name=='breath':
        exhale=source('creature-death-2.wav',.85,1.9,.62)
        add(x,filtered(exhale,70,1400),.12,1)
    elif name in ('beam','beam_charge','spirit_charge','spirit_launch','spirit_impact'):
        # Energy remains designed, but smooth pressure and rounded tones replace noise spikes.
        charge=name.endswith('charge');impact=name.endswith('impact')
        air=filtered(rng.standard_normal(len(t)),80,1400)
        if charge:
            x=(.28*tone(t,86,190)+.12*tone(t,129,285)+.07*tone(t,172,380))*(np.sin(np.pi*p/2)**1.3)
            x+=air*.035*np.sin(np.pi*p)**2
        elif impact:
            x=.42*tone(t,68,30)*np.exp(-t*1.65)+air*.20*np.exp(-t*2.3)
            add(x,filtered(source('creature-death-2.wav',speed=.78),65,1500),.02,.20)
        else:
            x=(.30*tone(t,105,62)+.10*tone(t,157.5,93)+air*.10)*np.sin(np.pi*p)**.65
    elif name=='flight':
        x=filtered(rng.standard_normal(len(t)),65,1300)*np.sin(np.pi*p)**1.7
    else:
        x=filtered(rng.standard_normal(len(t)),80,900)*.06*np.sin(np.pi*p)**2
        for at,freq in [(1.,293.66),(3.5,440),(6.4,349.23)]:
            u=np.maximum(0,t-at);x+=.018*np.sin(2*np.pi*freq*u)*np.exp(-u*.9)*(t>=at)*np.minimum(1,u*20)
    # No waveshaping/saturation or random clicks. Linear gain and fades retain the recording.
    x=fade(filtered(x,40,4200),.025,.22 if seconds>1 else .10)
    peak=max(float(np.max(np.abs(x))),1e-9)
    x*= (.36 if name in ('ambience','breath','flight') else .64)/peak
    DEST.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='dragon-recorded-audio-') as temp:
        pcm=Path(temp)/(name+'.wav')
        with wave.open(str(pcm),'wb') as out:
            out.setnchannels(1);out.setsampwidth(2);out.setframerate(RATE);out.writeframes(np.rint(x*32767).astype('<i2').tobytes())
        subprocess.run(['ffmpeg','-nostdin','-hide_banner','-loglevel','error','-y','-i',str(pcm),'-c:a','libvorbis','-q:a','7',str(DEST/(name+'.ogg'))],check=True)
    return dict(name=name,seconds=seconds,sampleRate=RATE,sha256=hashlib.sha256((DEST/(name+'.ogg')).read_bytes()).hexdigest())

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--only',nargs='+',choices=DURATIONS,help='Regenerate selected sounds only')
    args=parser.parse_args()
    manifest=json.loads((SOURCES.parent/'sources.json').read_text())
    for name,expected in manifest['files'].items():
        if hashlib.sha256((SOURCES/name).read_bytes()).hexdigest()!=expected:raise SystemExit('Source checksum mismatch: '+name)
    print(json.dumps([render(name,seconds) for name,seconds in DURATIONS.items()
                      if args.only is None or name in args.only],indent=2))

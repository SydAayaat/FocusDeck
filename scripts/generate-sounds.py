#!/usr/bin/env python3
"""Original deterministic FocusDeck PCM samples, no external audio/licensing dependency."""
from pathlib import Path
import math, random, struct, wave
root=Path(__file__).resolve().parents[1]/'android/app/src/main/res/raw'
root.mkdir(parents=True,exist_ok=True)
rate=22050
for name,duration,frequencies in [('soft_tick',.075,[1100]),('mechanical_tick',.11,[1850,2800]),('wood_tick',.09,[530,880]),('digital_tick',.045,[1450]),('ui_click',.045,[680]),('chime',.65,[660,990,1320]),('bell',.85,[880,1320,1760])]:
    rng=random.Random(42); frames=[]
    for i in range(int(duration*rate)):
        t=i/rate; envelope=min(1,t/.002)*math.exp(-t/(duration/5))
        signal=sum(math.sin(2*math.pi*f*t)/(j+1) for j,f in enumerate(frequencies))/len(frequencies)
        if name=='mechanical_tick': signal=.65*signal+.35*rng.uniform(-1,1)
        frames.append(struct.pack('<h',int(max(-1,min(1,signal*envelope*.55))*32767)))
    with wave.open(str(root/(name+'.wav')),'wb') as out:
        out.setnchannels(1);out.setsampwidth(2);out.setframerate(rate);out.writeframes(b''.join(frames))

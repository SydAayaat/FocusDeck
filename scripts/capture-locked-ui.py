#!/usr/bin/env python3
"""Capture the actual isolated native Compose QA activity, never the real ledger.

python3 scripts/capture-locked-ui.py --serial R3CX90NC7HR --screens home home-popup
The default builds and installs ONLY com.hax.focusdeck.visualtest; --no-build reuses it.
"""
import argparse,json,subprocess,time,os,runpy,re
from PIL import Image
from pathlib import Path
ROOT=Path(__file__).resolve().parent.parent
PACKAGE='com.hax.focusdeck.visualtest'
REFS={'home':'home.jpeg','home-popup':'home_!.jpeg','day':'day.jpeg','week':'weekl.jpeg','month':'month.jpeg','year':'year.jpeg','achievements':'achivements.jpeg','timeline':'timeline_event.png','history':'sessions_history.png','summary':'session_detail.png','index':'achivements.jpeg'}
def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--serial',required=True)
    p.add_argument('--screens',nargs='+',choices=REFS,default=list(REFS))
    p.add_argument('--no-build',action='store_true')
    p.add_argument('--motion',choices=['FULL','REDUCED','OFF'],default='OFF')
    p.add_argument('--fixture',choices=['REFERENCE_3Y','REFERENCE','EMPTY','PARTIAL','COMPLETED','INCOMPLETE','LONG_TEXT','POSITIVE','NEGATIVE','LARGE','ZERO','ACTIVE','PAUSED','STOPPED','MIXED'],default='REFERENCE')
    p.add_argument('--scenario',choices=['idle','active','paused','ended_early','empty','zero','long_text'],default='idle')
    p.add_argument('--non-immersive',action='store_true')
    p.add_argument('--lower',action='store_true',help='Also capture a scrolled native viewport; never stitch/synthesize actual screens')
    p.add_argument('--orientation',choices=['portrait','landscape'],default='portrait')
    p.add_argument('--font-scale',type=float,default=1.0,help='QA composition only; never modifies phone settings')
    p.add_argument('--out',type=Path,default=ROOT/'artifacts/uiqa')
    a=p.parse_args();a.out.mkdir(parents=True,exist_ok=True)
    def run(args,**kwargs):return subprocess.run(args,check=True,**kwargs)
    adb=['adb','-s',a.serial]
    build_info=run(adb+['shell','getprop','ro.product.model'],capture_output=True,text=True).stdout.strip()
    if not build_info.startswith('SM-S928'): raise RuntimeError('Expected the explicitly selected physical S24 Ultra, got '+build_info)
    if not a.no_build:
        env=dict(os.environ);env.setdefault('ANDROID_HOME',str(Path.home()/'Android/Sdk'));env.setdefault('JAVA_HOME','/usr/lib/jvm/java-17-openjdk-amd64')
        with (a.out/'capture-build.log').open('w') as log:run([str(ROOT/'android/gradlew'),':app:assembleVisualtest'],env=env,stdout=log,stderr=subprocess.STDOUT)
        apk=ROOT/'android/app/build/outputs/apk/visualtest/app-visualtest.apk'
        aapt=sorted((Path(env['ANDROID_HOME'])/'build-tools').glob('*/aapt2'))[-1]
        badging=run([str(aapt),'dump','badging',str(apk)],capture_output=True,text=True).stdout
        if not re.search(r"package: name='"+re.escape(PACKAGE)+"'",badging):raise RuntimeError('Refusing to install an unexpected package')
        if 'android.permission.INTERNET' in badging: raise RuntimeError('Refusing a visual-test APK with network permission')
        run(adb+['install','-r',str(apk)])
    for screen in a.screens:
        run(adb+['shell','am','start','-S','-n',PACKAGE+'/com.hax.focusdeck.UiQaActivity','--es','screen',screen,'--es','motion',a.motion,'--es','orientation',a.orientation,'--ef','fontScale',str(a.font_scale),'--es','fixture',a.fixture,'--es','scenario',a.scenario,'--ez','immersive',str(not a.non_immersive).lower()])
        if a.fixture=='REFERENCE_3Y':
            # Wait for the real asset adapter, never capture its loading screen as a finished page.
            for _ in range(25):
                run(adb+['shell','uiautomator','dump','/sdcard/focusdeck-visualtest-window.xml'],stdout=subprocess.DEVNULL)
                xml=run(adb+['shell','cat','/sdcard/focusdeck-visualtest-window.xml'],capture_output=True,text=True).stdout
                if 'Validating synthetic assets' not in xml and ('SYNTHETIC' in xml):break
                time.sleep(.4)
            else:raise RuntimeError('Synthetic preview did not become ready')
        else:time.sleep(1.5)
        state=run(adb+['shell','dumpsys','window'],capture_output=True,text=True).stdout
        if not any(PACKAGE in line for line in state.splitlines() if 'mFocusedApp=' in line or 'mCurrentFocus=' in line):
            raise RuntimeError('QA activity is not foreground; refusing a misleading capture')
        with (a.out/f'{screen}.png').open('wb') as capture:run(adb+['exec-out','screencap','-p'],stdout=capture)
        if a.fixture=='REFERENCE_3Y':
            actual=Image.open(a.out/f'{screen}.png')
            (a.out/f'{screen}-capture.json').write_text(json.dumps({'screen':screen,'serial':a.serial,'package':PACKAGE,'source':'adb exec-out screencap -p','fixture':a.fixture,'scenario':a.scenario,'simulated_clock':'2026-10-12T23:59:30+02:00','motion':a.motion,'orientation':a.orientation,'font_scale':a.font_scale,'pixels':actual.size},indent=2))
            if a.lower:
                w,h=actual.size
                run(adb+['shell','input','swipe',str(w//2),str(int(h*.78)),str(w//2),str(int(h*.28)),'500'])
                time.sleep(.7)
                with (a.out/f'{screen}-lower.png').open('wb') as capture:run(adb+['exec-out','screencap','-p'],stdout=capture)
            print('Captured supplied synthetic dataset:',screen,flush=True)
            continue
        preferred=ROOT/'docs/design/omss'/({'home':'home','achievements':'achivements','week':'week'}.get(screen,screen)+'.png')
        # Final shared-card authority applies to Day; period-specific references remain intact.
        final_day=ROOT/'docs/design/omss/analytics-final/cards_finale.png'
        handoff=ROOT/'docs/design/omss/handoff'/({'home':'homepage_icons_fix.png','achievements':'achivements.png'}.get(screen,REFS[screen]))
        reference=handoff if handoff.is_file() else final_day if screen=='day' and final_day.is_file() else preferred if preferred.is_file() else ROOT/'codex_handoff/references'/REFS[screen]
        with (a.out/f'{screen}-compare.log').open('w') as log:run(['python3',str(ROOT/'codex_handoff/tools/compare_ui.py'),'--reference',str(reference),'--actual',str(a.out/f'{screen}.png'),'--out',str(a.out/f'{screen}_compare')],stdout=log)
        # The references are tall generated pages. Also compare their TOP viewport,
        # so ImageOps.fit cannot crop away the header while matching a phone capture.
        utility=runpy.run_path(str(ROOT/'codex_handoff/tools/compare_ui.py'))
        ref=Image.open(reference).convert('RGB')
        trimmed,gutters=utility['crop_light_gutters'](ref)
        actual=Image.open(a.out/f'{screen}.png')
        height=min(ref.height,round(trimmed.width*actual.height/actual.width))
        crop=f'{gutters[0]},0,{ref.width-gutters[1]},{height}'
        with (a.out/f'{screen}-aligned-compare.log').open('w') as log:
            run(['python3',str(ROOT/'codex_handoff/tools/compare_ui.py'),'--reference',str(reference),'--actual',str(a.out/f'{screen}.png'),'--out',str(a.out/f'{screen}_aligned_compare'),'--reference-crop',crop,'--mask-top','40','--mask-bottom','55'],stdout=log)
        (a.out/f'{screen}-capture.json').write_text(json.dumps({'screen':screen,'serial':a.serial,'package':PACKAGE,'source':'adb exec-out screencap -p','fixture':a.fixture,'provider':'isolated native VisualTestDataProvider','immersive':not a.non_immersive,'motion':a.motion,'orientation':a.orientation,'font_scale':a.font_scale,'reference':str(reference.relative_to(ROOT)),'review_required':True,'wm_size':run(adb+['shell','wm','size'],capture_output=True,text=True).stdout.strip(),'wm_density':run(adb+['shell','wm','density'],capture_output=True,text=True).stdout.strip()},indent=2)+'\n')
        if a.lower and screen!='home-popup':
            w,h=actual.size
            run(adb+['shell','input','swipe',str(w//2),str(int(h*.78)),str(w//2),str(int(h*.28)),'500'])
            time.sleep(.7)
            with (a.out/f'{screen}-lower.png').open('wb') as capture:run(adb+['exec-out','screencap','-p'],stdout=capture)
        # Width-normalized complete pages preserve all content and never use center-crop/height stretch.
        full=Image.new('RGB',(ref.width*2,max(ref.height,round(actual.height*ref.width/actual.width))),'#202328')
        full.paste(ref,(0,0));full.paste(actual.convert('RGB').resize((ref.width,round(actual.height*ref.width/actual.width))), (ref.width,0))
        full.save(a.out/f'{screen}-full-comparison.png')
        print('Captured and compared:',screen,flush=True)
if __name__=='__main__':main()

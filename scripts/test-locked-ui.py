#!/usr/bin/env python3
"""Run native interaction/geometry/pixel checks in the isolated .uiqa package.

Never installs the production application or initializes its repository.
"""
import argparse,os,re,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parent.parent
PACKAGE='com.hax.focusdeck.uiqa'
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--serial',required=True);a=p.parse_args()
    out=ROOT/'artifacts/uiqa';out.mkdir(parents=True,exist_ok=True)
    env=dict(os.environ);env.setdefault('ANDROID_HOME',str(Path.home()/'Android/Sdk'));env.setdefault('JAVA_HOME','/usr/lib/jvm/java-17-openjdk-amd64')
    def run(args,**kw):return subprocess.run(args,check=True,**kw)
    with (out/'m8-instrument-build.log').open('w') as log:
        run([str(ROOT/'android/gradlew'),'-PuiQaTests',':app:assembleUiqa',':app:assembleUiqaAndroidTest'],env=env,stdout=log,stderr=subprocess.STDOUT)
    aapt=sorted((Path(env['ANDROID_HOME'])/'build-tools').glob('*/aapt2'))[-1]
    adb=['adb','-s',a.serial]
    for relative,expected in [('uiqa/app-uiqa.apk',PACKAGE),('androidTest/uiqa/app-uiqa-androidTest.apk',PACKAGE+'.test')]:
        apk=ROOT/'android/app/build/outputs/apk'/relative
        badging=run([str(aapt),'dump','badging',str(apk)],capture_output=True,text=True).stdout
        if not re.search(r"package: name='"+re.escape(expected)+"'",badging):raise RuntimeError('Refusing to install an unexpected package: '+str(apk))
        run(adb+['install','-r',str(apk)])
    result=run(adb+['shell','am','instrument','-w','-r','-e','class','com.hax.focusdeck.LockedDashboardUiTest',PACKAGE+'.test/androidx.test.runner.AndroidJUnitRunner'],capture_output=True,text=True)
    (out/'m8-locked-device-tests.log').write_text(result.stdout+result.stderr)
    passed=re.search(r'OK \((\d+) tests\)',result.stdout)
    if not passed or 'FAILURES!!!' in result.stdout:raise RuntimeError('Native QA failed; inspect artifacts/uiqa/m8-locked-device-tests.log')
    print('Passed',passed.group(1),'native UI tests in',PACKAGE)
if __name__=='__main__':main()

"""Capture a package using an installed debug runtime and instrumentation on Android."""
import argparse,json,subprocess
from pathlib import Path

def capture(package,output,serial,adb='adb'):
    out=Path(output); out.mkdir(parents=True,exist_ok=True)
    def run(*args,**kwargs): return subprocess.run([adb,'-s',serial,*args],check=True,**kwargs)
    run('shell','run-as','dev.mwidget.runtime','mkdir','-p','files')
    run('exec-in','run-as','dev.mwidget.runtime','tee','files/qa-input.mwidget',input=Path(package).read_bytes(),stdout=subprocess.DEVNULL)
    result=run('shell','am','instrument','-w','-e','mode','capture','dev.mwidget.runtime.test/dev.mwidget.runtime.RuntimeSmoke',capture_output=True,text=True)
    if 'PASS: theme capture' not in result.stdout: raise RuntimeError(result.stdout+result.stderr)
    report=run('exec-out','run-as','dev.mwidget.runtime','cat','files/qa/android-report.json',capture_output=True).stdout
    (out/'android-report.json').write_bytes(report); frames=json.loads(report)['frames']
    names=[f'{kind}-{state}' for kind in ('renderer','host') for state in ('playing','paused','long-ko','missing','artwork')]+[f'frame-{n}' for n in range(frames)]
    for name in names:
        image=run('exec-out','run-as','dev.mwidget.runtime','cat',f'files/qa/{name}.png',capture_output=True).stdout
        (out/f'{name}.png').write_bytes(image)
    return json.loads(report)
if __name__=='__main__':
    p=argparse.ArgumentParser(); p.add_argument('package'); p.add_argument('output'); p.add_argument('--serial',required=True); p.add_argument('--adb',default='adb'); a=p.parse_args()
    print(json.dumps(capture(a.package,a.output,a.serial,a.adb),ensure_ascii=False,indent=2))

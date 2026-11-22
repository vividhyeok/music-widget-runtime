"""One command: package, structural/font/layout checks, portable samples, honest report."""
import argparse,io,json,sys
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
from fontTools.ttLib import TTFont
from pack import pack
from preview import preview
from validate import load
from scene import walk,hits,bounds

def build(source,output,serial=None,adb='adb'):
    out=Path(output).resolve(); out.mkdir(parents=True,exist_ok=True)
    # Never place derived artifacts inside the theme source.
    if Path(source).resolve()==out or Path(source).resolve() in out.parents: raise ValueError('Output must be outside theme directory')
    initial=json.loads((Path(source)/'manifest.json').read_text(encoding='utf-8-sig')); package=out/(initial['id']+'.mwidget')
    pack(source,package); m,files=load(package); errors=[]; warnings=[]; nodes=list(walk(m['layers']))
    samples=[('playing','Midnight in Seoul','Luna Park',True),('paused','달빛이 머무는 밤','새벽의 음악',False),('long-ko','아주 긴 한국어 노래 제목과 English Mixed Title 0123456789 — 잘림과 정렬 검사','이름이 아주 긴 아티스트와 함께하는 음악',True),('missing','','',False)]
    for index,(n,matrix,unstable) in enumerate(nodes):
        box=bounds(n,matrix)
        if box[0]<0 or box[1]<0 or box[2]>m['width'] or box[3]>m['height']: warnings.append(f'Node {index}: transformed content crosses canvas; inspect clipping')
        if n['type']=='text':
            face=TTFont(io.BytesIO(files[n['font']])); cmap=face.getBestCmap() or {}; face.close()
            required=n.get('text','') if n['field']=='literal' else '음악 재생 일시 정지 제목 아티스트 ABCabc0123456789'
            missing=''.join(sorted({c for c in required if not c.isspace() and ord(c) not in cmap}))
            if missing: errors.append(f'Font {n["font"]} missing glyphs: {missing}')
            font=ImageFont.truetype(io.BytesIO(files[n['font']]),round(n['size'])); ascent,descent=font.getmetrics()
            if ascent+descent>n['h']: warnings.append(f'Text {index}: font line height {ascent+descent} exceeds box {n["h"]}; inspect Android output')
            if n.get('letterSpacing',0): warnings.append(f'Text {index}: portable approximation omits letter spacing; Android capture required for exact review')
    for hit in hits(m):
        x,y,r,b=hit['bounds']
        if min((r-x)*360/m['width'],(b-y)*180/m['height'])<48: warnings.append(f'Touch {hit["action"]}: under 48dp at 360x180dp; enlarge or verify intended host size')
    active=[n['animation'] for n,_,_ in nodes if 'animation' in n]
    implicit=any(n.get('overflow')=='marquee' or n.get('mode')=='simulated' for n,_,_ in nodes)
    periods={a['durationMs'] for a in active}|({12000} if implicit else set())
    if len(periods)>1: warnings.append('Mixed motion periods: Android host uses static fallback; use a shared duration')
    for name,title,artist,playing in samples:
        preview(package,out/f'approx-{name}.png',title=title,artist=artist,playing=playing,duration=0 if name=='missing' else 222000)
    if active or implicit:
        period=next(iter(periods),12000)
        for n in range(4): preview(package,out/f'approx-frame-{n}.png',time_ms=n*period//4,animate=True)
    images=[Image.open(out/f'approx-{name}.png').convert('RGBA') for name,*_ in samples]
    sheet=Image.new('RGBA',(m['width']*2,(m['height']+30)*2),'#111111'); d=ImageDraw.Draw(sheet)
    for i,image in enumerate(images):
        x=(i%2)*m['width']; y=(i//2)*(m['height']+30); d.text((x+8,y+6),'APPROX / '+samples[i][0],fill='white'); sheet.alpha_composite(image,(x,y+30))
    sheet.save(out/'contact-sheet.png')
    report={'theme':m['id'],'runtimeMinimum':'0.3.0' if m['version']==2 else '0.2.0','packageValidation':'passed','fontAndLayoutErrors':errors,'warnings':warnings,'preview':'Pillow approximation; not Android evidence','android':'not-run','visualInspection':'AI must inspect generated images; script does not certify visual quality','motion':'experimental host frames; static fallback is mandatory','fonts':m.get('fonts',[])}
    if serial:
        from capture_android import capture
        report['android']=capture(package,out/'android',serial,adb)
    (out/'review.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(report,ensure_ascii=False,indent=2))
    if errors: raise ValueError('Fix font/layout errors and rerun before delivery')
    return package
if __name__=='__main__':
    p=argparse.ArgumentParser(); p.add_argument('source'); p.add_argument('output'); p.add_argument('--android-serial'); p.add_argument('--adb',default='adb'); a=p.parse_args()
    try: build(a.source,a.output,a.android_serial,a.adb)
    except Exception as e: print(str(e),file=sys.stderr); sys.exit(1)

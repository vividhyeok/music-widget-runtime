import argparse,io,os
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
from validate import load

def system_fallback(size, override):
    paths = [override, Path(os.environ.get('WINDIR','C:/Windows'))/'Fonts/malgun.ttf',
             '/System/Library/Fonts/AppleSDGothicNeo.ttc', '/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc']
    for path in paths:
        if path and Path(path).is_file():
            return ImageFont.truetype(path,size)
    return None

def runs(text, primary, fallback):
    if fallback is None:
        return [(text,primary)]
    missing=bytes(primary.getmask('\u0378'))
    groups=[]
    for char in text:
        font=fallback if bytes(primary.getmask(char))==missing else primary
        if groups and groups[-1][1] is font:
            groups[-1]=(groups[-1][0]+char,font)
        else:
            groups.append((char,font))
    return groups

def preview(package,output,title='Midnight in Seoul',artist='Luna Park',playing=True,artwork=None,fallback_font=None):
    m,files=load(package); im=Image.new('RGBA',(m['width'],m['height'])); d=ImageDraw.Draw(im)
    values={'title':title,'artist':artist,'album':'City Lights','player':'YouTube Music','status':'재생 중' if playing else '일시 정지'}
    for l in m['layers']:
        x,y,w,h=[int(l[k]) for k in ('x','y','w','h')]
        if l['type']=='text':
            font=ImageFont.truetype(io.BytesIO(files[l['font']]),int(l['size']))
            fallback=system_fallback(int(l['size']),fallback_font)
            text=values[l['field']]
            def width(line):
                return sum(d.textlength(chars,font=face) for chars,face in runs(line,font,fallback))
            if width(text)>w:
                while text and width(text+'…')>w: text=text[:-1]
                text+='…'
            tile=Image.new('RGBA',(w,h)); draw=ImageDraw.Draw(tile)
            baseline=font.getmetrics()[0]; offset=0
            for chars,face in runs(text,font,fallback):
                draw.text((offset,baseline),chars,font=face,fill=l['color'],anchor='ls')
                offset+=draw.textlength(chars,font=face)
            im.alpha_composite(tile,(x,y))
        else:
            src=l.get('src',l.get('fallback'))
            if l['type']=='icon' and l['action']=='toggle' and playing: src=l.get('playingSrc',src)
            asset=Image.open(artwork if l['type']=='artwork' and artwork else io.BytesIO(files[src])).convert('RGBA')
            if l['type']=='artwork':
                from PIL import ImageOps
                asset=ImageOps.fit(asset,(w,h))
            else: asset=asset.resize((w,h),Image.Resampling.LANCZOS)
            im.alpha_composite(asset,(x,y))
    im.save(output)

if __name__=='__main__':
    p=argparse.ArgumentParser(); p.add_argument('package'); p.add_argument('output'); p.add_argument('--title',default='Midnight in Seoul'); p.add_argument('--artist',default='Luna Park'); p.add_argument('--paused',action='store_true'); p.add_argument('--artwork'); p.add_argument('--fallback-font'); a=p.parse_args()
    preview(a.package,a.output,a.title,a.artist,not a.paused,a.artwork,a.fallback_font)

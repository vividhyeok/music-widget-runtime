"""Materialize original artwork and editable source for the two demo briefs."""
import argparse
import json
import math
from pathlib import Path
import random
import sys
import urllib.request
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / '.agents/skills/music-widget-designer/scripts'))
from pack import pack
from preview import preview

W, H = 720, 360
FONTS = {
    'nocturne': ('spacegrotesk', 'SpaceGrotesk%5Bwght%5D.ttf'),
    'tape-deck': ('dmmono', 'DMMono-Regular.ttf'),
}

def font_files(folder, name):
    family, filename = FONTS[name]
    base = f'https://raw.githubusercontent.com/google/fonts/main/ofl/{family}/'
    for remote, local in ((filename,'assets/type.ttf'),('OFL.txt','LICENSE.txt')):
        path=folder/local
        if not path.exists():
            with urllib.request.urlopen(base+remote,timeout=25) as source:
                path.write_bytes(source.read())

def background(night, font):
    rng=random.Random(730 if night else 423)
    im=Image.new('RGBA',(W,H)); px=im.load()
    for y in range(H):
        for x in range(W):
            grain=rng.uniform(-3,3)
            if night:
                glow=math.exp(-((x-620)**2+(y-30)**2)/68000)
                wave=math.exp(-((x-50)**2+(y-355)**2)/42000)
                rgb=(12+43*glow+10*wave,17+13*glow+27*wave,32+53*glow+31*wave)
            else:
                shade=1-(x/W+y/H)*.025
                rgb=(249*shade,232*shade,197*shade)
            px[x,y]=(*[int(max(0,min(255,c+grain))) for c in rgb],255)
    d=ImageDraw.Draw(im)
    if night:
        d.rounded_rectangle((1,1,718,358),28,outline='#52506c',width=2)
        d.line((39,76,681,76),fill='#4b4b67',width=1)
        d.line((250,238,680,238),fill='#4b4b67',width=1)
        d.ellipse((40,38,53,51),fill='#9ef7df')
        d.text((65,44),'NOCTURNE  /  NOW PLAYING',font=font,fill='#c8e7f7',anchor='lm')
        for i,h in enumerate([4,10,16,8,21,14,8,14,6]):
            d.line((501+i*8,45-h/2,501+i*8,45+h/2),fill='#a790d1',width=2)
        d.rounded_rectangle((38,99,226,287),14,outline='#7f79a9',width=2)
        d.ellipse((391,251,477,337),fill='#b6ffeb')
    else:
        d.rounded_rectangle((1,1,718,358),22,outline='#bfa584',width=2)
        d.rectangle((30,14,690,76),fill='#8e3426')
        d.text((46,45),'TAPE DECK   /   SIDE A',font=font,fill='#fff1d8',anchor='lm')
        d.line((37,241,683,241),fill='#ad9474',width=2)
        d.rounded_rectangle((488,90,679,232),10,fill='#39332d')
        d.rounded_rectangle((121,261,405,337),12,fill='#dfc29b',outline='#ab8a67',width=2)
        d.rounded_rectangle((220,259,306,339),11,fill='#8e3426')
        d.line((217,270,217,328),fill='#ab8a67',width=2)
        d.line((309,270,309,328),fill='#ab8a67',width=2)
        for x in (48,671):
            d.ellipse((x-4,294,x+4,302),fill='#a68d73')
    mask=Image.new('L',im.size); ImageDraw.Draw(mask).rounded_rectangle((0,0,W-1,H-1),28 if night else 22,fill=255)
    im.putalpha(mask); return im

def cover(night):
    im=Image.new('RGB',(320,320),'#1e1b36' if night else '#7d3929'); d=ImageDraw.Draw(im)
    if night:
        for y in range(320):
            s=y/320; d.line((0,y,319,y), fill=(int(30+56*s),int(26+32*s),int(60+64*s)))
        d.ellipse((111,46,213,148),fill='#f0b6a2')
        for i in range(7):
            d.line((110,104+7*i,214,104+7*i),fill='#70476e',width=2)
        for points,color in [([(0,184),(48,143),(94,193),(141,135),(206,202),(260,153),(320,173),(320,320),(0,320)],'#414467'), ([(0,219),(65,182),(110,218),(174,171),(234,221),(320,188),(320,320),(0,320)],'#172b49')]:
            d.polygon(points,fill=color)
        for x,y,w,h in [(16,236,28,54),(45,217,29,73),(77,243,29,47),(107,222,42,68),(150,253,21,37),(175,213,39,77),(216,234,33,56),(255,213,37,77),(294,243,26,47)]:
            d.rectangle((x,y,x+w,y+h),fill='#101a32')
            for xx in range(x+5,x+w-3,8):
                for yy in range(y+7,y+h-4,12): d.rectangle((xx,yy,xx+2,yy+3),fill='#cc9f9b')
        d.rectangle((0,290,320,320),fill='#11192d')
    else:
        d.rounded_rectangle((23,75,297,245),18,fill='#ebcd98',outline='#251c1a',width=7)
        d.rounded_rectangle((48,104,272,179),16,fill='#332b25')
        for x in (99,221):
            d.ellipse((x-26,115,x+26,167),fill='#d19b61')
            d.ellipse((x-15,126,x+15,156),fill='#302b23')
            for i in range(6):
                a=i*math.pi/3; xx=x+math.cos(a)*14; yy=141+math.sin(a)*14
                d.ellipse((xx-2,yy-2,xx+2,yy+2),fill='#e1bb85')
        d.polygon([(75,232),(95,192),(225,192),(245,232)],fill='#915339')
        d.line((105,213,215,213),fill='#e8c180',width=4)
    return im

def icon(action, ink):
    im=Image.new('RGBA',(128,128)); d=ImageDraw.Draw(im)
    if action=='play': d.polygon([(46,29),(98,64),(46,99)],fill=ink)
    if action=='pause':
        for x in (41,70): d.rounded_rectangle((x,29,x+17,99),3,fill=ink)
    if action=='next':
        d.polygon([(38,30),(85,64),(38,98)],fill=ink); d.rounded_rectangle((88,30,98,98),2,fill=ink)
    if action=='previous':
        d.polygon([(91,30),(44,64),(91,98)],fill=ink); d.rounded_rectangle((30,30,40,98),2,fill=ink)
    if action=='switch':
        d.line((26,48,96,48),fill=ink,width=8); d.polygon([(78,29),(103,48),(78,67)],fill=ink)
        d.line((32,80,102,80),fill=ink,width=8); d.polygon([(50,61),(25,80),(50,99)],fill=ink)
    if action=='settings':
        for y in (33,63,93): d.ellipse((57,y-7,71,y+7),fill=ink)
    return im.resize((64,64),Image.Resampling.LANCZOS)

def create(name):
    night=name=='nocturne'
    folder=ROOT/'themes'/name; assets=folder/'assets'; assets.mkdir(parents=True,exist_ok=True)
    font_files(folder,name)
    background(night,ImageFont.truetype(assets/'type.ttf',19)).save(assets/'background.png',optimize=True)
    cover(night).save(assets/'cover.png',optimize=True)
    for item in ('previous','play','pause','next','switch','settings'):
        if night: ink='#17323b' if item in ('play','pause') else '#d6e9f7'
        else: ink='#fff0d5' if item in ('play','pause','switch','settings') else '#562b22'
        icon(item,ink).save(assets/f'{item}.png',optimize=True)
    def rect(x,y,w,h): return dict(x=x,y=y,w=w,h=h)
    def text(field,x,y,w,h,size,color): return dict(type='text',field=field,**rect(x,y,w,h),size=size,color=color,font='assets/type.ttf')
    layers=[dict(type='image',**rect(0,0,W,H),src='assets/background.png')]
    if night:
        layers += [dict(type='artwork',**rect(43,104,178,178),fallback='assets/cover.png'),
                   text('player',251,96,430,33,22,'#a4e9d8'), text('title',249,130,435,61,43,'#fcf6ff'),
                   text('artist',251,192,429,39,27,'#c3bbdc'),text('status',43,301,181,32,19,'#a6a4ce')]
        buttons=[('previous',274,244,100,102),('toggle',384,244,100,102),('next',494,244,100,102),('switch',600,244,100,102),('settings',598,2,100,75)]
    else:
        layers += [dict(type='artwork',**rect(495,96,177,130),fallback='assets/cover.png'),
                   text('player',38,94,426,36,22,'#8e3426'),text('title',37,130,432,58,36,'#321e1c'),
                   text('artist',38,186,428,42,26,'#695243'),text('status',463,283,193,34,20,'#803527')]
        buttons=[('previous',112,246,100,104),('toggle',213,246,100,104),('next',314,246,100,104),('switch',497,0,100,78),('settings',598,0,100,78)]
    touch=[]; labels={'previous':'이전 곡','toggle':'재생 또는 일시 정지','next':'다음 곡','switch':'플레이어 전환','settings':'위젯 설정'}
    for action,x,y,w,h in buttons:
        size=57 if action=='toggle' else 49
        layers.append(dict(type='icon',action=action,**rect(x+(w-size)//2,y+(h-size)//2,size,size),src=f"assets/{'play' if action=='toggle' else action}.png"))
        if action=='toggle': layers[-1]['playingSrc']='assets/pause.png'
        touch.append(dict(action=action,label=labels[action],**rect(x,y,w,h)))
    manifest=dict(version=1,id=name,name='Nocturne' if night else 'Tape Deck',width=W,height=H,layers=layers,touch=touch)
    (folder/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    package=ROOT/'dist'/f'{name}.mwidget'
    pack(folder,package)
    preview(package,ROOT/'dist'/f'{name}.png')

if __name__=='__main__':
    p=argparse.ArgumentParser(); p.add_argument('name',choices=FONTS); create(p.parse_args().name)

"""Reproducible v2 example; font selected on Noonnu, downloaded from its author."""
import hashlib,json,sys,urllib.request
from pathlib import Path
from PIL import Image,ImageDraw
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'.agents/skills/music-widget-designer/scripts'))
from pack import pack
from preview import preview
source='https://raw.githubusercontent.com/sun-typeface/SUITE/a114fec078bfcbdc93a7b2190510ab83de2a2d40/'
root=ROOT/'themes/orbit'; (root/'assets').mkdir(parents=True,exist_ok=True)
font=root/'assets/SUITE-Medium.ttf'
if not font.exists(): font.write_bytes(urllib.request.urlopen(source+'fonts/static/ttf/SUITE-Medium.ttf',timeout=30).read())
if not (root/'LICENSE.txt').exists(): (root/'LICENSE.txt').write_bytes(urllib.request.urlopen(source+'LICENSE',timeout=30).read())
cover=Image.new('RGBA',(192,192),'#18302f'); d=ImageDraw.Draw(cover)
for r in range(92,10,-4): d.ellipse((96-r,96-r,96+r,96+r),outline='#92c7ab',width=1)
d.polygon([(25,150),(96,25),(160,143)],fill='#dfecc5'); d.ellipse((88,88,104,104),fill='#18302f'); cover.save(root/'assets/cover.png')
def rect(t,x,y,w,h,**kw): return dict(type=t,x=x,y=y,w=w,h=h,**kw)
def label(field,x,y,w,h,size,color,**kw): return rect('text',x,y,w,h,field=field,size=size,color=color,font='assets/SUITE-Medium.ttf',**kw)
m=dict(version=2,id='orbit',name='Orbit / v2',width=720,height=360,fonts=[dict(file='assets/SUITE-Medium.ttf',noonnu='https://noonnu.cc/font_page/1150',source='https://github.com/sun-typeface/SUITE/tree/a114fec078bfcbdc93a7b2190510ab83de2a2d40',license='LICENSE.txt')],layers=[
rect('shape',0,0,720,360,shape='rect',color='#102726',radius=32,gradient=dict(endColor='#243f36',angle=25)),
rect('group',35,58,220,220,id='disc',transform=dict(rotation=-8),children=[
rect('shape',0,0,220,220,shape='ellipse',color='#071515'),
rect('artwork',14,14,192,192,fallback='assets/cover.png',mask='circle',animation=dict(kind='rotate',durationMs=12000,steps=12,when='playing',fallback='static')),
rect('shape',99,99,22,22,shape='ellipse',color='#102726')]),
label('literal',300,38,360,28,18,'#aec8b1',text='ORBIT  /  MUSIC RUNTIME',letterSpacing=.08),
label('title',300,92,370,70,30,'#f0f3d4',overflow='marquee',valign='center'),
label('artist',300,166,360,32,20,'#aec8b1',overflow='ellipsis'),
rect('equalizer',300,222,155,38,mode='simulated',color='#bedb9a',bars=16,gap=.5),
rect('group',566,198,96,96,id='toggle',children=[
rect('shape',0,16,96,64,shape='rect',color='#d8e9b3',radius=22),
label('literal',0,24,96,48,24,'#17362b',text='PLAY',align='center',valign='center',visibleWhen='paused'),
label('literal',0,24,96,48,24,'#17362b',text='II',align='center',valign='center',visibleWhen='playing')]),
rect('progress',300,307,362,4,color='#d8e9b3',trackColor='#465d4e'),
label('elapsed',300,321,96,24,14,'#aec8b1'),label('duration',566,321,96,24,14,'#aec8b1',align='right')
],touch=[dict(target='toggle',action='toggle',label='재생 또는 일시 정지')])
(root/'manifest.json').write_text(json.dumps(m,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
pack(root,ROOT/'dist/orbit.mwidget'); preview(ROOT/'dist/orbit.mwidget',ROOT/'dist/orbit.png',title='달빛이 머무는 밤',artist='새벽의 음악',playing=False)
print('Font SHA256:',hashlib.sha256(font.read_bytes()).hexdigest())

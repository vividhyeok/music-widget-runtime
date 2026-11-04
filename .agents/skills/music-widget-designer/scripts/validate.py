"""Strict package validation. Returns manifest and verified bytes without extraction."""
import io, json, re, stat, sys, zipfile
from pathlib import Path
from PIL import Image, ImageFont
from jsonschema import Draft202012Validator

MAX_ZIP=16*1024*1024
MAX_TOTAL=24*1024*1024
MAX_FILE=8*1024*1024
SCHEMA=Path(__file__).resolve().parents[1]/'references/schema.json'

def safe_name(name):
    return bool(re.fullmatch(r'[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)*',name)) and len(name)<=160 and all(p not in ('.','..') for p in name.split('/'))

def no_duplicates(pairs):
    out={}
    for k,v in pairs:
        if k in out: raise ValueError('Duplicate JSON key: '+k)
        out[k]=v
    return out

def validate_bytes(raw):
    if len(raw)>MAX_ZIP: raise ValueError('ZIP exceeds 16 MiB')
    files={}; total=0
    with zipfile.ZipFile(io.BytesIO(raw)) as z:
        entries=z.infolist()
        if not 1<=len(entries)<=64: raise ValueError('Entry count')
        folded=set()
        for e in entries:
            if not safe_name(e.filename) or e.filename.lower() in folded: raise ValueError('Unsafe or duplicate path')
            folded.add(e.filename.lower())
            if e.flag_bits & 1 or stat.S_ISLNK(e.external_attr>>16): raise ValueError('Encrypted entry or symlink')
            if e.compress_type not in (zipfile.ZIP_STORED,zipfile.ZIP_DEFLATED): raise ValueError('Compression method')
            if e.file_size>MAX_FILE or e.file_size>max(1,e.compress_size)*200: raise ValueError('Expansion limit')
            with z.open(e) as stream: data=stream.read(MAX_FILE+1)
            if len(data)>MAX_FILE or len(data)!=e.file_size: raise ValueError('Entry size')
            total+=len(data)
            if total>MAX_TOTAL: raise ValueError('Total expansion limit')
            files[e.filename]=data
    if 'manifest.json' not in files or len(files['manifest.json'])>128*1024: raise ValueError('Missing/large manifest')
    m=json.loads(files['manifest.json'],object_pairs_hook=no_duplicates,parse_constant=lambda x: (_ for _ in ()).throw(ValueError(x)))
    Draft202012Validator(json.loads(SCHEMA.read_text(encoding='utf-8'))).validate(m)
    used={'manifest.json'}; pixels=0
    for l in m['layers']:
        if l['type']=='progress':
            if ('thumbSrc' in l)!=('thumbWidth' in l): raise ValueError('Thumb image and width required together')
            if l.get('thumbWidth',0)>l['w']: raise ValueError('Thumb exceeds track')
        for key in ('src','playingSrc','fallback','font','trackSrc','fillSrc','thumbSrc'):
            if key in l:
                p=l[key]; used.add(p)
                if not safe_name(p) or p not in files: raise ValueError('Missing asset: '+p)
                if key=='font' and not p.endswith(('.ttf','.otf')): raise ValueError('Font extension')
                if key!='font' and not p.endswith('.png'): raise ValueError('Image extension')
    for l in m['layers']+m['touch']:
        if l['x']+l['w']>m['width'] or l['y']+l['h']>m['height']: raise ValueError('Rectangle outside canvas')
    for i,a in enumerate(m['touch']):
        for b in m['touch'][i+1:]:
            if a['x']<b['x']+b['w'] and b['x']<a['x']+a['w'] and a['y']<b['y']+b['h'] and b['y']<a['y']+a['h']: raise ValueError('Overlapping touch areas')
    for p,data in files.items():
        if p.endswith('.png'):
            with Image.open(io.BytesIO(data)) as im:
                w,h=im.size
                if im.format!='PNG' or w>2048 or h>2048 or w*h>2097152 or getattr(im,'n_frames',1)!=1: raise ValueError('Image dimensions/format')
                pixels+=w*h
                if pixels>8388608: raise ValueError('Aggregate image budget')
                im.verify()
        elif p.endswith(('.ttf','.otf')):
            if len(data)>2*1024*1024 or data[:4] not in (b'\x00\x01\x00\x00',b'OTTO'): raise ValueError('Font format/size')
            ImageFont.truetype(io.BytesIO(data),24)
        elif p not in ('manifest.json','LICENSE.txt'): raise ValueError('Unsupported file: '+p)
    if any(p not in used and p!='LICENSE.txt' for p in files): raise ValueError('Unreferenced asset')
    return m,files

def load(path):
    p=Path(path)
    if p.stat().st_size>MAX_ZIP: raise ValueError('ZIP exceeds limit')
    return validate_bytes(p.read_bytes())

if __name__=='__main__':
    try:
        m,f=load(sys.argv[1]); print(f"VALID {m['id']}: {len(f)} files")
    except Exception as e:
        print('INVALID: '+str(e),file=sys.stderr); sys.exit(1)

import argparse, io, tempfile, zipfile
from pathlib import Path
from validate import validate_bytes, safe_name, MAX_TOTAL

def pack(source,output):
    root=Path(source).resolve(); buf=io.BytesIO(); total=0
    out=Path(output).resolve()
    if not root.is_dir() or root in out.parents:
        raise ValueError('Choose a source directory and an output outside it')
    with zipfile.ZipFile(buf,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for p in sorted(root.rglob('*')):
            if p.is_symlink(): raise ValueError('Symlinks forbidden')
            if not p.is_file(): continue
            name=p.relative_to(root).as_posix()
            if not safe_name(name): raise ValueError('Unsafe name')
            total+=p.stat().st_size
            if total>MAX_TOTAL: raise ValueError('Source exceeds limit')
            entry=zipfile.ZipInfo(name,(2024,1,1,0,0,0)); entry.compress_type=zipfile.ZIP_DEFLATED
            z.writestr(entry,p.read_bytes())
    raw=buf.getvalue(); validate_bytes(raw)
    out.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=out.parent,prefix=out.name+'.',suffix='.tmp',delete=False) as stream:
        temp=Path(stream.name)
        stream.write(raw)
    try:
        temp.replace(out)
    finally:
        temp.unlink(missing_ok=True)

if __name__=='__main__':
    ap=argparse.ArgumentParser(); ap.add_argument('source'); ap.add_argument('output'); a=ap.parse_args()
    pack(a.source,a.output); print(a.output)

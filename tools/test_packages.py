"""Regression checks for actual sample packages and hostile input."""
import io
import sys
import unittest
import zipfile
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'.agents/skills/music-widget-designer/scripts'))
from validate import load, validate_bytes


def zip_data(files):
    buf = io.BytesIO()
    with zipfile.ZipFile(buf,'w',zipfile.ZIP_DEFLATED) as z:
        for name, data in files:
            z.writestr(name,data)
    return buf.getvalue()


class PackageTests(unittest.TestCase):
    def setUp(self):
        self.manifest,self.files=load(ROOT/'dist/nocturne.mwidget')

    def test_demo_packages(self):
        second,_=load(ROOT/'dist/tape-deck.mwidget')
        self.assertNotEqual(self.manifest['id'],second['id'])
        self.assertNotEqual(self.manifest['touch'],second['touch'])

    def test_traversal_and_case_collision(self):
        for name in ('../escape','assets/../../escape','/escape','assets\\escape','MANIFEST.JSON'):
            with self.subTest(name=name),self.assertRaises(ValueError):
                validate_bytes(zip_data([*self.files.items(),(name,b'x')]))

    def test_expansion(self):
        self.files['assets/background.png']=b'\0'*(2*1024*1024)
        with self.assertRaisesRegex(ValueError,'Expansion'):
            validate_bytes(zip_data(self.files.items()))

    def test_oversized_png(self):
        png=io.BytesIO(); Image.new('RGB',(2049,3),'#808080').save(png,format='PNG')
        self.files['assets/background.png']=png.getvalue()
        with self.assertRaisesRegex(ValueError,'dimensions'):
            validate_bytes(zip_data(self.files.items()))

    def test_duplicate_json(self):
        self.files['manifest.json']=self.files['manifest.json'].replace(b'{',b'{"version":1,',1)
        with self.assertRaisesRegex(ValueError,'Duplicate'):
            validate_bytes(zip_data(self.files.items()))


if __name__=='__main__': unittest.main(verbosity=2)

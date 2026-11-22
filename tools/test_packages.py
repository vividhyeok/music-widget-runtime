"""Regression checks for actual sample packages and hostile input."""
import io
import sys
import unittest
import zipfile
import json
import copy
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'.agents/skills/music-widget-designer/scripts'))
from validate import load, validate_bytes
from scene import hits
from preview import preview


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

    def test_v2_sample_provenance_and_portable_states(self):
        m,files=load(ROOT/'dist/orbit.mwidget')
        self.assertEqual(m['version'],2)
        self.assertEqual(m['fonts'][0]['noonnu'],'https://noonnu.cc/font_page/1150')
        self.assertIn(b'SIL OPEN FONT LICENSE',files['LICENSE.txt'])
        import tempfile
        with tempfile.TemporaryDirectory() as output:
            a=Path(output)/'a.png'; b=Path(output)/'b.png'
            preview(ROOT/'dist/orbit.mwidget',a,animate=True,time_ms=0)
            preview(ROOT/'dist/orbit.mwidget',b,animate=True,time_ms=3000)
            self.assertNotEqual(a.read_bytes(),b.read_bytes())
            c=Path(output)/'missing.png'
            preview(ROOT/'dist/orbit.mwidget',c,title='',artist='',duration=0)
            with Image.open(c) as image: self.assertEqual(image.getpixel((340,309))[:3],(70,93,78))

    def test_v2_geometry_and_attached_hits(self):
        m={'version':2,'id':'geometry','name':'Geometry','width':400,'height':240,'layers':[{'type':'group','x':100,'y':50,'w':100,'h':50,'transform':{'rotation':90},'children':[{'id':'button','type':'shape','shape':'rect','x':0,'y':0,'w':100,'h':50,'color':'#ff0000','opacity':.5,'mask':'circle'}]}],'touch':[{'target':'button','action':'toggle','label':'Play'}]}
        def validate(manifest): return validate_bytes(zip_data([('manifest.json',json.dumps(manifest).encode())]))
        validate(m)
        box=hits(m)[0]['bounds']
        for actual,expected in zip(box,(125,25,175,125)): self.assertAlmostEqual(actual,expected)
        m['layers'][0]['animation']={'kind':'rotate','durationMs':6000,'steps':6,'when':'playing','fallback':'static'}
        with self.assertRaisesRegex(ValueError,'cannot move'): validate(m)

    def test_v2_refuses_invalid_effects_and_missing_font_provenance(self):
        m,files=load(ROOT/'dist/orbit.mwidget')
        changes=[lambda x:x.pop('fonts'),lambda x:x['layers'][1]['transform'].update(quad=[0,0,1,1,1,0,0,1]),lambda x:x['layers'][1].update(transform={'skewX':1,'skewY':1}),lambda x:x['layers'][1].update(animation={'kind':'pulse','from':2,'durationMs':6000,'steps':6,'when':'playing','fallback':'static'}),lambda x:x['layers'][1].update(animation={'kind':'javascript','durationMs':6000,'steps':6,'when':'playing','fallback':'static'})]
        for change in changes:
            changed=copy.deepcopy(m); change(changed)
            archive={**files,'manifest.json':json.dumps(changed).encode()}
            with self.assertRaises(Exception): validate_bytes(zip_data(archive.items()))


if __name__=='__main__': unittest.main(verbosity=2)

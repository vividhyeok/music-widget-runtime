"""Generate closed, backwards-compatible v1/v2 schemas; no remote references."""
import json
from pathlib import Path

num = {"type": "number", "minimum": 0, "maximum": 1024}
path = {"type": "string", "pattern": r"^[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)*\.(?:png|ttf|otf)$", "maxLength": 160}
color = {"type": "string", "pattern": "^#[0-9A-Fa-f]{6}$"}
def obj(props, required=None):
    return {"type":"object", "properties":props, "required":required or list(props), "additionalProperties":False}
rect = {"x":num,"y":num,"w":{"type":"number","exclusiveMinimum":0,"maximum":1024},"h":{"type":"number","exclusiveMinimum":0,"maximum":1024}}
variants=[]
def layer(kind, props, optional=()):
    p={"type":{"const":kind},**rect,**props}
    variants.append(obj(p,[k for k in p if k not in optional]))
layer("progress", {"color":color,"trackColor":color,"trackSrc":path,"fillSrc":path,"thumbSrc":path,"thumbWidth":rect["w"],"radius":num,"showFill":{"type":"boolean"}},("trackSrc","fillSrc","thumbSrc","thumbWidth","radius","showFill"))
layer("image", {"src":path})
layer("artwork", {"fallback":path})
layer("text", {"field":{"enum":["title","artist","album","player","status","elapsed","duration"]},"size":{"type":"number","minimum":6,"maximum":160},"color":color,"font":path})
layer("icon", {"action":{"enum":["previous","toggle","next","switch","settings","rewind","forward","stop"]},"src":path,"playingSrc":path},("playingSrc",))
schema={"$schema":"https://json-schema.org/draft/2020-12/schema","title":"Music Widget v1",**obj({
"version":{"const":1},"id":{"type":"string","pattern":"^[a-z][a-z0-9-]{0,47}$"},"name":{"type":"string","minLength":1,"maxLength":80},
"width":{"type":"integer","minimum":240,"maximum":1024},"height":{"type":"integer","minimum":120,"maximum":1024},
"layers":{"type":"array","minItems":1,"maxItems":32,"items":{"oneOf":variants}},
"touch":{"type":"array","minItems":0,"maxItems":8,"items":obj({**rect,"action":{"enum":["previous","toggle","next","switch","settings","rewind","forward","stop"]},"label":{"type":"string","minLength":1,"maxLength":80}})}
})}
# v1 remains closed and unchanged; v2 adds declarative scene nodes, never code.
v1 = {k:v for k,v in schema.items() if k not in ('$schema','title')}
unit={"type":"number","minimum":0,"maximum":1}
signed={"type":"number","minimum":-360,"maximum":360}
transform=obj({"rotation":signed,"scaleX":{"type":"number","minimum":0.01,"maximum":4},"scaleY":{"type":"number","minimum":0.01,"maximum":4},"pivotX":unit,"pivotY":unit,"skewX":{"type":"number","minimum":-1,"maximum":1},"skewY":{"type":"number","minimum":-1,"maximum":1},"quad":{"type":"array","minItems":8,"maxItems":8,"items":unit}},[])
# obj's default requires all properties; explicitly empty requirements here.
transform['required']=[]
animation=obj({"kind":{"enum":["rotate","translate","pulse","frames"]},"durationMs":{"type":"integer","minimum":1000,"maximum":60000},"steps":{"type":"integer","minimum":2,"maximum":12},"when":{"enum":["playing","always"]},"fallback":{"const":"static"},"from":signed,"to":signed,"dx":{"type":"number","minimum":-1024,"maximum":1024},"dy":{"type":"number","minimum":-1024,"maximum":1024},"sources":{"type":"array","minItems":2,"maxItems":12,"items":path}},['kind','durationMs','steps','when','fallback'])
common={"id":{"type":"string","pattern":"^[a-z][a-z0-9-]{0,47}$"},"opacity":unit,"visibleWhen":{"enum":["always","playing","paused","hasArtwork","noArtwork"]},"transform":transform,"mask":{"enum":["none","circle","rounded"]},"radius":num,"maskSrc":path,"animation":animation}
nodes=[]
for variant in variants:
    v=json.loads(json.dumps(variant)); v['properties'].update(common)
    if v['properties']['type']['const']=='text':
        v['properties'].update({"field":{"enum":["title","artist","album","player","status","elapsed","duration","literal"]},"text":{"type":"string","maxLength":2048},"align":{"enum":["left","center","right"]},"valign":{"enum":["top","center","bottom"]},"overflow":{"enum":["ellipsis","clip","wrap","marquee"]},"maxLines":{"type":"integer","minimum":1,"maximum":8},"lineSpacing":{"type":"number","minimum":0.8,"maximum":2},"letterSpacing":{"type":"number","minimum":-0.1,"maximum":1}})
    if v['properties']['type']['const'] in ('image','artwork','icon'):
        v['properties']['fit']={"enum":["crop","contain","stretch"]}
    if v['properties']['type']['const']=='progress':
        v['properties'].update({"direction":{"enum":["horizontal","vertical","circular"]},"strokeWidth":{"type":"number","exclusiveMinimum":0,"maximum":128},"startAngle":signed,"sweepAngle":{"type":"number","minimum":1,"maximum":360}})
    nodes.append(v)
def node(kind,props,required):
    nodes.append(obj({"type":{"const":kind},**rect,**common,**props},['type',*rect,*required]))
node('group',{"children":{"type":"array","minItems":1,"maxItems":64,"items":{"$ref":"#/$defs/node"}}},['children'])
node('shape',{"shape":{"enum":["rect","ellipse","polygon"]},"color":color,"strokeColor":color,"strokeWidth":{"type":"number","minimum":0,"maximum":128},"gradient":obj({"endColor":color,"angle":signed}),"points":{"type":"array","minItems":6,"maxItems":128,"items":unit}},['shape','color'])
node('equalizer',{"mode":{"enum":["static","simulated"]},"color":color,"bars":{"type":"integer","minimum":3,"maximum":48},"gap":unit,"seed":{"type":"integer","minimum":0,"maximum":65535}},['mode','color','bars'])
touch_target=obj({"target":{"type":"string","pattern":"^[a-z][a-z0-9-]{0,47}$"},"action":schema['properties']['touch']['items']['properties']['action'],"label":{"type":"string","minLength":1,"maxLength":80}})
font=obj({"file":path,"noonnu":{"type":"string","pattern":r"^https://noonnu\.cc/font_page/[0-9]+$"},"source":{"type":"string","pattern":"^https://"},"license":{"const":"LICENSE.txt"}})
v2=obj({**schema['properties'],"version":{"const":2},"layers":{"type":"array","minItems":1,"maxItems":64,"items":{"$ref":"#/$defs/node"}},"touch":{"type":"array","maxItems":8,"items":{"oneOf":[schema['properties']['touch']['items'],touch_target]}},"fonts":{"type":"array","maxItems":16,"items":font}},list(schema['required']))
schema={"$schema":schema['$schema'],"title":"Music Widget v1 and v2","oneOf":[v1,v2],"$defs":{"node":{"oneOf":nodes}}}
Path('.agents/skills/music-widget-designer/references/schema.json').write_text(json.dumps(schema,indent=2)+'\n',encoding='utf-8')

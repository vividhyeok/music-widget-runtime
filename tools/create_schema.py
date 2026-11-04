"""Generate the shared, closed v1 schema; no remote schema references."""
import json
from pathlib import Path

num = {"type": "number", "minimum": 0, "maximum": 1024}
path = {"type": "string", "pattern": r"^[A-Za-z0-9_-]+(?:/[A-Za-z0-9_.-]+)*\.(?:png|ttf|otf)$", "maxLength": 160}
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
Path('.agents/skills/music-widget-designer/references/schema.json').write_text(json.dumps(schema,indent=2)+'\n',encoding='utf-8')

"""Pure Python projective geometry matching Scene.kt (no Android or NumPy needed)."""
import math

IDENTITY=(1,0,0,0,1,0,0,0,1)
def mul(a,b): return tuple(sum(a[r*3+k]*b[k*3+c] for k in range(3)) for r in range(3) for c in range(3))
def translate(x,y): return (1,0,x,0,1,y,0,0,1)
def around(m,x,y): return mul(translate(x,y),mul(m,translate(-x,-y)))
def inverse(m):
    a,b,c,d,e,f,g,h,i=m
    adj=(e*i-f*h,c*h-b*i,b*f-c*e,f*g-d*i,a*i-c*g,c*d-a*f,d*h-e*g,b*g-a*h,a*e-b*d)
    determinant=a*adj[0]+b*adj[3]+c*adj[6]
    if abs(determinant)<1e-10: raise ValueError('Singular transform')
    return tuple(x/determinant for x in adj)
def point(m,x,y):
    den=m[6]*x+m[7]*y+m[8]
    if abs(den)<1e-9: raise ValueError('Projective pole')
    return ((m[0]*x+m[1]*y+m[2])/den,(m[3]*x+m[4]*y+m[5])/den)
def local(node):
    w,h=node['w'],node['h']; t=node.get('transform',{}); p=t.get('pivotX',.5)*w; q=t.get('pivotY',.5)*h
    m=IDENTITY
    if 'quad' in t:
        pts=list(zip(t['quad'][::2],t['quad'][1::2])); (x0,y0),(x1,y1),(x2,y2),(x3,y3)=pts
        crosses=[(pts[(n+1)%4][0]-pts[n][0])*(pts[(n+2)%4][1]-pts[(n+1)%4][1])-(pts[(n+1)%4][1]-pts[n][1])*(pts[(n+2)%4][0]-pts[(n+1)%4][0]) for n in range(4)]
        if min(crosses)<=.0001: raise ValueError('Quad must be convex and clockwise')
        dx1=x1-x2; dx2=x3-x2; dx3=x0-x1+x2-x3
        dy1=y1-y2; dy2=y3-y2; dy3=y0-y1+y2-y3
        det=dx1*dy2-dx2*dy1
        g=(dx3*dy2-dx2*dy3)/det; hh=(dx1*dy3-dx3*dy1)/det
        m=((x1-x0+g*x1), (x3-x0+hh*x3)*w/h,x0*w,(y1-y0+g*y1)*h/w,y3-y0+hh*y3,y0*h,g/w,hh/h,1)
    m=mul(around((1,t.get('skewX',0),0,t.get('skewY',0),1,0,0,0,1),p,q),m)
    m=mul(around((t.get('scaleX',1),0,0,0,t.get('scaleY',1),0,0,0,1),p,q),m)
    angle=math.radians(t.get('rotation',0)); cs,sn=math.cos(angle),math.sin(angle)
    m=mul(around((cs,-sn,0,sn,cs,0,0,0,1),p,q),m)
    result=mul(translate(node['x'],node['y']),m)
    inverse(result)
    return result
def walk(nodes,parent=IDENTITY,depth=0,unstable=False):
    if depth>4: raise ValueError('Group depth exceeds 4')
    for node in nodes:
        matrix=mul(parent,local(node)); changing=unstable or 'animation' in node or node.get('visibleWhen','always')!='always'
        yield node,matrix,changing
        yield from walk(node.get('children',[]),matrix,depth+1,changing)
def bounds(node,matrix):
    pts=[point(matrix,x,y) for x,y in ((0,0),(node['w'],0),(node['w'],node['h']),(0,node['h']))]
    return (min(p[0] for p in pts),min(p[1] for p in pts),max(p[0] for p in pts),max(p[1] for p in pts))
def hits(manifest):
    refs={n['id']:(n,m,u) for n,m,u in walk(manifest['layers']) if 'id' in n}
    result=[]
    for hit in manifest['touch']:
        if 'target' in hit:
            if hit['target'] not in refs: raise ValueError('Unknown touch target')
            node,matrix,unstable=refs[hit['target']]
            if unstable: raise ValueError('Touch targets cannot move or change visibility')
            box=bounds(node,matrix)
        else: box=(hit['x'],hit['y'],hit['x']+hit['w'],hit['y']+hit['h'])
        if box[0]<-1e-4 or box[1]<-1e-4 or box[2]>manifest['width']+1e-4 or box[3]>manifest['height']+1e-4 or box[2]-box[0]<=0 or box[3]-box[1]<=0: raise ValueError('Touch outside canvas')
        for other in result:
            b=other['bounds']
            if box[0]<b[2] and b[0]<box[2] and box[1]<b[3] and b[1]<box[3]: raise ValueError('Overlapping touch areas')
        result.append({**hit,'bounds':box})
    return result

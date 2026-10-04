#!/usr/bin/env python3
"""Offline inspection of the authorized saved world; no game/mod code runs.

Preserves region cells and static Java block elements/UVs from installed archives.
This is research rendering, not a Minecraft visual-acceptance gate.
"""
from collections import Counter
from functools import lru_cache
from io import BytesIO
import json
import math
from pathlib import Path
import shutil
import zipfile

import numpy as np
from PIL import Image

from read_ginger_island_reference_world import OUT, INSTANCE
from study_ginger_island_terrain_reference import AIR, category

DIRECTIONS={'up':(1,0,0),'down':(-1,0,0),'south':(0,1,0),'north':(0,-1,0),'east':(0,0,1),'west':(0,0,-1)}
NORMALS={'up':(0,1,0),'down':(0,-1,0),'south':(0,0,1),'north':(0,0,-1),'east':(1,0,0),'west':(-1,0,0)}
LIGHT={'up':1,'down':.54,'north':.8,'south':.8,'east':.68,'west':.68}
ARCHIVES=[];INDEX={};TEX={};MISSING=Counter()


def resource(ref, kind, ext):
    ns,path=ref.split(':',1) if ':' in ref else ('minecraft',ref)
    return f'assets/{ns}/{kind}/{path}.{ext}'


def load(ref, kind='models'):
    path=resource(ref,kind,'json')
    if path not in INDEX:
        MISSING[path]+=1;return {}
    try: return json.loads(ARCHIVES[INDEX[path]].read(path))
    except (ValueError, UnicodeError):
        MISSING['invalid-json:'+path]+=1;return {}


@lru_cache(None)
def model(ref):
    child=load(ref)
    if not child:return {}
    parent=child.get('parent','')
    base=model(parent) if parent and not parent.startswith(('builtin/', 'minecraft:builtin/')) else {}
    return {**base,**child,'textures':{**base.get('textures',{}),**child.get('textures',{})}}


def condition(spec, props):
    if 'OR' in spec:return any(condition(s,props) for s in spec['OR'])
    if 'AND' in spec:return all(condition(s,props) for s in spec['AND'])
    return all(props.get(k,'') in str(v).split('|') for k,v in spec.items())


def applications(state):
    data=load(state['Name'],'blockstates');props=state.get('Properties',{})
    out=[]
    for key,val in data.get('variants',{}).items():
        spec=dict(s.split('=',1) for s in key.split(',') if '=' in s)
        if condition(spec,props):out.append(val[0] if isinstance(val,list) else val);break
    for part in data.get('multipart',[]):
        if 'when' not in part or condition(part['when'],props):
            v=part['apply'];out.append(v[0] if isinstance(v,list) else v)
    return out


def matrix(axis, angle):
    a=math.radians(angle);c,s=math.cos(a),math.sin(a)
    if axis=='x':return np.array([[1,0,0],[0,c,-s],[0,s,c]])
    if axis=='y':return np.array([[c,0,s],[0,1,0],[-s,0,c]])
    return np.array([[c,-s,0],[s,c,0],[0,0,1]])


def corners(a,b,d):
    x,y,z=a;X,Y,Z=b
    return np.array({'north':[(X,Y,z),(x,Y,z),(x,y,z),(X,y,z)],
      'south':[(x,Y,Z),(X,Y,Z),(X,y,Z),(x,y,Z)],
      'east':[(X,Y,Z),(X,Y,z),(X,y,z),(X,y,Z)],
      'west':[(x,Y,z),(x,Y,Z),(x,y,Z),(x,y,z)],
      'up':[(x,Y,z),(X,Y,z),(X,Y,Z),(x,Y,Z)],
      'down':[(x,y,Z),(X,y,Z),(X,y,z),(x,y,z)]}[d],float)


def default_uv(a,b,d):
    x,y,z=a;X,Y,Z=b
    return {'up':[x,z,X,Z],'down':[x,16-Z,X,16-z],
      'north':[16-X,16-Y,16-x,16-y],'south':[x,16-Y,X,16-y],
      'west':[z,16-Y,Z,16-y],'east':[16-Z,16-Y,16-z,16-y]}[d]


def texture(ref):
    if ref in TEX:return TEX[ref]
    p=resource(ref,'textures','png')
    if p not in INDEX:
        MISSING[p]+=1;im=Image.new('RGBA',(16,16),(193,114,158,255))
    else:

        try: im=Image.open(BytesIO(ARCHIVES[INDEX[p]].read(p))).convert('RGBA')
        except Exception:
            MISSING['invalid-image:'+p]+=1;im=Image.new('RGBA',(16,16),(193,114,158,255))
        if im.height>im.width and im.height%im.width==0:im=im.crop((0,0,im.width,im.width))
    TEX[ref]={'image':im,'slot':len(TEX)};return TEX[ref]


def faces(state):
    if state['Name']=='minecraft:lava':
        fs=[]
        for d in NORMALS:
            fs.append({'position':corners([0,0,0],[16,15.9,16],d)/16, 'uv':np.array([(0,0),(1,0),(1,1),(0,1)]), 'color':np.array([1.,1.,1.])*LIGHT[d], 'texture':texture('minecraft:block/lava_still')['slot'], 'direction':d,'cull':None})
        return fs,False
    result=[];full=False
    for app in applications(state):
        m=model(app['model']);textures=m.get('textures',{})
        R=matrix('y',-app.get('y',0)) @ matrix('x',-app.get('x',0))
        for elem in m.get('elements',[]):
            a=elem['from'];b=elem['to'];rot=elem.get('rotation')
            if a==[0,0,0] and b==[16,16,16] and not rot and len(elem.get('faces',{}))==6:full=True
            for d,face in elem.get('faces',{}).items():
                ref=face.get('texture','')
                for _ in range(20):
                    if not ref.startswith('#'):break
                    ref=textures.get(ref[1:],'')
                if not ref or ref.startswith('#'):continue
                points=corners(a,b,d);normal=np.array(NORMALS[d],float)
                if rot:
                    origin=np.array(rot.get('origin',[8,8,8]),float);E=matrix(rot['axis'],rot['angle'])
                    if rot.get('rescale'):
                        scale=np.ones(3)/max(math.cos(math.radians(rot['angle'])),.1);scale['xyz'.index(rot['axis'])]=1
                        points=origin+(points-origin)*scale
                    points=origin+(points-origin) @ E.T;normal=E@normal
                points=(8+(points-8)@R.T)/16;normal=R@normal
                uv=face.get('uv',default_uv(a,b,d));u,v,U,V=uv
                texuv=np.array([(u,v),(U,v),(U,V),(u,V)])/16
                texuv=np.roll(texuv,face.get('rotation',0)//90,axis=0)
                tint=np.ones(3)
                if 'tintindex' in face:
                    name=state['Name']
                    col=(.38,.60,.38) if 'spruce' in name else ((.5,.56,.35) if 'birch' in name else (.38,.62,.28))
                    if 'grass' in name or 'fern' in name:col=(.47,.73,.33)
                    tint=np.array(col)
                shade=.55+.45*max(0,float(normal@np.array([-.4,.85,.33])))
                # Overlay layers receive a small outward displacement only to avoid coplanar z-fighting.
                points+=normal*.0005*len([f for f in result if f['direction']==d])
                result.append({'position':points,'uv':texuv,'color':tint*shade,'texture':texture(ref)['slot'],
                  'direction':max(NORMALS,key=lambda side:float(normal@np.array(NORMALS[side]))),'cull':face.get('cullface')})
    return result,full


def vertices(face, atlas_side):
    ix=[0,1,2,0,2,3];slot=face['texture'];col,row=slot%atlas_side,slot//atlas_side
    # 64-pixel tiles with a one-pixel inset, no atlas gutters sampled.
    uv=face['uv'];uv=np.column_stack(((col*64+1+uv[:,0]*62)/(atlas_side*64),
                                     1-(row*64+1+uv[:,1]*62)/(atlas_side*64)))
    return np.column_stack((face['position'][ix],uv[ix],np.tile(face['color'],(6,1)))).astype('<f4')


def main():
    jars=[INSTANCE/'.fabric/remappedJars/minecraft-1.20.1-0.16.8/client-intermediary.jar']+sorted((INSTANCE/'mods').glob('*.jar'))
    for jar in jars:
        z=zipfile.ZipFile(jar);idx=len(ARCHIVES);ARCHIVES.append(z)
        for name in z.namelist():
            if name.startswith('assets/') and name.endswith(('.json','.png')):INDEX[name]=idx
    data=np.load(OUT/'world-volume.npz');vol=data['blocks'];bounds=data['bounds'];states=json.load(open(OUT/'states.json'))
    render=OUT/'viewer';render.mkdir(exist_ok=True)
    face_sets=[];opaque=[]
    for s in states:
        if s['Name'] in AIR or category(s['Name'])=='water':fs,f=[],False
        else:fs,f=faces(s)
        face_sets.append(fs);opaque.append(f and category(s['Name']) not in ('canopy','understory') and 'glass' not in s['Name'])
    water_slot=texture('minecraft:block/water_still')['slot']
    side=math.ceil(math.sqrt(len(TEX)));atlas=Image.new('RGBA',(side*64,side*64))
    for item in TEX.values():
        slot=item['slot'];im=item['image'].resize((64,64),Image.Resampling.NEAREST);atlas.paste(im,(slot%side*64,slot//side*64))
    atlas.save(render/'atlas.png')
    shapes=[np.concatenate([vertices(f,side) for f in fs]) if fs else np.empty((0,8),np.float32) for fs in face_sets]
    solid=np.array(opaque,bool)[vol];terrain_faces=0;instanced=[];unsupported=Counter()
    origin=np.array([bounds[0],0,bounds[2]],float)
    with (render/'terrain.bin').open('wb') as out:
        for d,(dy,dz,dx) in DIRECTIONS.items():
            neighbor=np.zeros(solid.shape,bool)
            here=(slice(max(0,-dy),min(vol.shape[0],vol.shape[0]-dy)),slice(max(0,-dz),min(vol.shape[1],vol.shape[1]-dz)),slice(max(0,-dx),min(vol.shape[2],vol.shape[2]-dx)))
            there=(slice(max(0,dy),min(vol.shape[0],vol.shape[0]+dy)),slice(max(0,dz),min(vol.shape[1],vol.shape[1]+dz)),slice(max(0,dx),min(vol.shape[2],vol.shape[2]+dx)))
            neighbor[here]=solid[there]
            visible=solid&~neighbor;visible[:32]=False
            # Do not draw artificial observation-window cut faces.
            if dx:visible[:,:,0 if dx<0 else -1]=False
            if dz:visible[:,0 if dz<0 else -1,:]=False
            coords=np.argwhere(visible);ids=vol[visible]
            for sid in np.unique(ids):
                pts=coords[ids==sid][:,[2,0,1]].astype(np.float32)
                for f in face_sets[int(sid)]:
                    if f['direction']!=d:continue
                    base=vertices(f,side);v=np.broadcast_to(base,(len(pts),6,8)).copy();v[:,:,:3]+=pts[:,None,:];v.tofile(out);terrain_faces+=len(pts)
    for sid,s in enumerate(states):
        if opaque[sid] or s['Name'] in AIR or category(s['Name'])=='water':continue
        mask=vol==sid;mask[:55]=False
        pts=np.argwhere(mask)[:,[2,0,1]].astype('<f4')
        if len(pts)==0:continue
        if not len(shapes[sid]):unsupported[s['Name']]+=len(pts);continue
        # Local XYZ; Y remains world height. Geometry is the actual static block model.
        shapes[sid].tofile(render/f'model-{sid}.bin');pts.tofile(render/f'instances-{sid}.bin')
        instanced.append({'id':sid,'name':s['Name'],'category':category(s['Name']),'count':len(pts),'vertices':len(shapes[sid])})
    water=np.array([s['Name'] in ('minecraft:water','minecraft:bubble_column') for s in states])[vol]
    above=np.zeros_like(water);above[:-1]=water[1:]
    surface=water&~above;surface[:55]=False
    pts=np.argwhere(surface)[:,[2,0,1]].astype('<f4')
    f={'position':corners([0,0,0],[16,14.25,16],'up')/16,'uv':np.array([(0,0),(1,0),(1,1),(0,1)]), 'color':np.array([.45,.75,.88]),'texture':water_slot}
    base=vertices(f,side);wv=np.broadcast_to(base,(len(pts),6,8)).copy();wv[:,:,:3]+=pts[:,None,:];wv.tofile(render/'water.bin')
    metadata={'water_surface_cells':len(pts),'bounds':bounds.tolist(),'origin':origin.tolist(),'terrain_faces':terrain_faces,'instances':instanced,
      'unsupported_visible_states':dict(unsupported),'missing_asset_paths':dict(MISSING),
      'limits':['Static Java elements only; dynamic block entities and shader lighting are not rendered.',
        'Biome tint is representative, not the complete Minecraft biome color calculation.',
        'Weighted model alternatives use their first choice; blockstate properties and rotations are retained.',
        'Terrain surfaces include Y32..144; plants and other partial models include Y55..144. Water uses actual saved fluid surface cells above Y55 with simplified level and tint; lava is a simplified static fluid cell.']}
    (render/'scene.json').write_text(json.dumps(metadata,ensure_ascii=False,indent=2)+'\n')
    three=Path('/Users/jiayuhan/游戏制作/科技包专家/工具/产线三维工作台/node_modules/three')
    shutil.copyfile(three/'build/three.core.js',render/'three.core.js');shutil.copyfile(three/'build/three.module.js',render/'three.module.js');shutil.copyfile(three/'examples/jsm/controls/OrbitControls.js',render/'OrbitControls.js')
    print('RENDER',terrain_faces,'terrain faces',sum(s['count'] for s in instanced),'native block model instances',len(TEX),'textures',flush=True)
    print('UNSUPPORTED',dict(unsupported),flush=True)


if __name__=='__main__':main()

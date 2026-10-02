#!/usr/bin/env python3
"""Reconstruct the full observed region volume and tree structure, read-only."""
from collections import Counter
import hashlib
import json
from pathlib import Path

import numpy as np
from scipy.ndimage import label, generate_binary_structure, find_objects

from study_ginger_island_terrain_reference import read_chunk, unpack, category, GROUND, AIR
from import_farm_building_prefabs import value

ROOT=Path(__file__).resolve().parents[1]
INSTANCE=Path('/Users/jiayuhan/Library/Application Support/red.ghs.axolotl/profiles/星露谷复刻1.20.1@宅一夏')
WORLD=INSTANCE/'saves/星露谷复刻'
OUT=ROOT/'docs/ginger-island-research/workshop/reference-terrain-study/full-world'
BOUNDS=(-1090,0,3330,-815,144,3670)


def main():
    x0,y0,z0,x1,y1,z1=BOUNDS
    volume=np.zeros((y1-y0+1,z1-z0+1,x1-x0+1),np.uint16)
    states=[{'Name':'minecraft:air'}];lookup={'minecraft:air':0}
    sources=[];chunks=0;entities=Counter()
    for rz in range(z0//512,z1//512+1):
        for rx in range(x0//512,x1//512+1):
            path=WORLD/'region'/f'r.{rx}.{rz}.mca';raw=path.read_bytes()
            sources.append({'file':path.name,'sha256':hashlib.sha256(raw).hexdigest()})
            for cz in range(max(z0//16,rz*32),min(z1//16,rz*32+31)+1):
                for cx in range(max(x0//16,rx*32),min(x1//16,rx*32+31)+1):
                    root=read_chunk(raw,cx,cz)
                    if not root:continue
                    chunks+=1
                    ax,az=max(x0,cx*16),max(z0,cz*16);bx,bz=min(x1,cx*16+15),min(z1,cz*16+15)
                    for be in value(root,'block_entities',(10,[]))[1]:entities[value(be,'id','unknown')]+=1
                    for sec in value(root,'sections',(10,[]))[1]:
                        base=value(sec,'Y')*16;lo,hi=max(y0,base),min(y1,base+15)
                        bs=value(sec,'block_states')
                        if lo>hi or not bs:continue
                        pal,ids=unpack(bs);keys=[]
                        for item in pal:
                            name=value(item,'Name');props=value(item,'Properties',{})
                            plain={k:v[1] for k,v in props.items()}
                            key=name+json.dumps(plain,sort_keys=True)
                            if name=='minecraft:air' and not props:key=name
                            if key not in lookup:
                                lookup[key]=len(states);states.append({'Name':name,'Properties':plain})
                            keys.append(lookup[key])
                        cropped=ids[lo-base:hi-base+1,az-cz*16:bz-cz*16+1,ax-cx*16:bx-cx*16+1]
                        volume[lo-y0:hi-y0+1,az-z0:bz-z0+1,ax-x0:bx-x0+1]=np.array(keys,np.uint16)[cropped]
            print('VOLUME',path.name,chunks,flush=True)
    OUT.mkdir(parents=True,exist_ok=True)
    np.savez_compressed(OUT/'world-volume.npz',blocks=volume,bounds=BOUNDS)
    (OUT/'states.json').write_text(json.dumps(states,ensure_ascii=False,separators=(',',':')))
    cats=np.array([category(s['Name']) for s in states])
    # Connect each trunk/branch assembly in 3D, not a column or height envelope.
    wood=cats[volume]=='trunk'
    wood[:55]=False
    labels,n=label(wood,generate_binary_structure(3,3));sizes=np.bincount(labels.ravel());trees=[]
    boxes=find_objects(labels)
    for i in np.flatnonzero(sizes>=3):
        if i==0:continue
        box=boxes[int(i)-1];inside=labels[box]==i
        start=np.array([part.start for part in box]);pts=np.argwhere(inside)+start;lo=pts.min(axis=0);hi=pts.max(axis=0)
        if hi[0]-lo[0]<3:continue  # Low timber/props are not called trees.
        neighboring_leaf=False
        for y,z,x in pts:
            if np.any(cats[volume[max(0,y-1):y+2,max(0,z-1):z+2,max(0,x-1):x+2]]=='canopy'):
                neighboring_leaf=True;break
        if not neighboring_leaf:continue
        trees.append({'cells':int(sizes[i]),'bounds':[int(lo[2]+x0),int(lo[0]+y0),int(lo[1]+z0),
            int(hi[2]+x0),int(hi[0]+y0),int(hi[1]+z0)],'wood':dict(Counter(states[int(s)]['Name'] for s in volume[box][inside]))})
    report={'source_world':str(WORLD),'volume_bounds_xyz':BOUNDS,'chunks':chunks,'states':len(states),
      'regions':sources,'tree_or_wood_assemblies_with_adjacent_leaves':trees,
      'block_entity_types':dict(entities),'limits':['Tree assemblies are 26-connected wood groups with adjacent leaf cells, not an authored tree count.',
       'The volume is the whole observation window, including cliffs, cavities, trunks, branches and low plants. No surface envelope was substituted.',
       'Block entity counts cover the read chunks, including cells outside the cropped observation window.']}
    (OUT/'world-structure.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    crown_reports=[]
    for area,b in [('east',(-920,3470,-830,3595)),('river',(-1035,3475,-965,3585)),('mountain',(-985,3338,-905,3465))]:
        x,z,X,Z=b;cut=volume[55-y0:,z-z0:Z-z0+1,x-x0:X-x0+1]
        leaves=np.array(['leaf' in st['Name'] for st in states])[cut]
        crown_ids,num=label(leaves,generate_binary_structure(3,1));counts=np.bincount(crown_ids.ravel());groups=[]
        crown_boxes=find_objects(crown_ids)
        for i in sorted(range(1,num+1),key=lambda i:counts[i],reverse=True)[:5]:
            box=crown_boxes[i-1];lo=np.array([t.start for t in box]);hi=np.array([t.stop-1 for t in box])
            groups.append({'leaf_cells':int(counts[i]),'bounds':[int(lo[2]+x),int(lo[0]+55),int(lo[1]+z),int(hi[2]+x),int(hi[0]+55),int(hi[1]+z)]})
        crown_reports.append({'area':area,'observation_bounds_xz':b,'largest_face_connected_leaf_groups':groups})
    (OUT/'canopy-structure.json').write_text(json.dumps(crown_reports,ensure_ascii=False,indent=2)+'\n')
    print('DONE',len(states),'states,',len(trees),'tree/wood assemblies',flush=True)


if __name__=='__main__':main()

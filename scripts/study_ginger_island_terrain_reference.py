#!/usr/bin/env python3
"""Read-only terrain/forest measurements of a user-authorized reference save.

Output diagrams are derived from stored block positions, not game screenshots.
Nothing is saved to the source world, launcher, or production pregen.
"""
import argparse
from collections import Counter
import gzip
import hashlib
import json
import math
from pathlib import Path
import sys
import zlib

import numpy as np
from PIL import Image, ImageDraw, ImageFont

from import_farm_building_prefabs import NbtReader, value

GROUND = {
    'grass_block', 'dirt', 'coarse_dirt', 'rooted_dirt', 'podzol', 'mycelium',
    'moss_block', 'sand', 'red_sand', 'sandstone', 'smooth_sandstone',
    'red_sandstone', 'stone', 'andesite', 'diorite', 'granite', 'gravel', 'tuff',
    'deepslate', 'clay', 'mud', 'packed_mud', 'dirt_path', 'farmland',
    'calcite', 'blackstone', 'basalt', 'smooth_basalt', 'obsidian', 'magma_block',
    'netherrack', 'soul_sand', 'soul_soil', 'dripstone_block',
}
AIR = {'minecraft:air', 'minecraft:cave_air', 'minecraft:void_air',
       'minecraft:barrier', 'minecraft:light', 'minecraft:structure_void'}


def read_chunk(raw, cx, cz):
    slot = cx % 32 + (cz % 32) * 32
    location = int.from_bytes(raw[slot * 4:slot * 4 + 4], 'big')
    if not location: return None
    start = (location >> 8) * 4096
    length = int.from_bytes(raw[start:start + 4], 'big')
    compression = raw[start + 4]
    if compression & 128: raise ValueError('External chunk data requires explicit support')
    payload = raw[start + 5:start + 4 + length]
    if compression == 1: payload = gzip.decompress(payload)
    elif compression == 2: payload = zlib.decompress(payload)
    elif compression != 3: raise ValueError('Unknown Anvil compression')
    reader = NbtReader(payload)
    assert reader.number('B') == 10
    reader.string()
    root = reader.payload(10)
    assert reader.offset == len(payload)
    assert value(root, 'xPos') == cx and value(root, 'zPos') == cz
    return root


def unpack(block_states):
    palette = value(block_states, 'palette')[1]
    if len(palette) == 1: return palette, np.zeros((16, 16, 16), np.uint16)
    bits = max(4, (len(palette) - 1).bit_length())
    per_word = 64 // bits
    words = np.array(value(block_states, 'data'), dtype=np.int64).view(np.uint64)
    indices = np.arange(4096)
    packed = ((words[indices // per_word] >> ((indices % per_word) * bits).astype(np.uint64))
              & ((1 << bits) - 1)).astype(np.uint16)
    assert int(packed.max()) < len(palette)
    return palette, packed.reshape(16, 16, 16)


def category(name):
    short = name.split(':')[-1]
    if name in AIR: return 'air'
    if short in ('water', 'bubble_column') or 'kelp' in short or 'seagrass' in short: return 'water'
    if 'leaves' in short or 'leaf' in short: return 'canopy'
    if short.endswith(('_log', '_wood')) or 'column_stripped' in short: return 'trunk'
    if short in ('grass_block', 'moss_block', 'podzol', 'mycelium', 'farmland', 'dirt_path'): return 'soil'
    if 'sand' in short and 'soul' not in short: return 'sand'
    if short in GROUND or short.endswith(('_ore', '_coral_block')): return 'rock'
    if short in ('grass', 'fern', 'tall_grass', 'large_fern', 'vine', 'bamboo', 'lily_pad') or 'flower' in short or 'bush' in short: return 'understory'
    return 'built'


def extract(world, bounds):
    x0, z0, x1, z1 = bounds
    shape = (z1 - z0 + 1, x1 - x0 + 1)
    maps = {key: np.full(shape, -32768, np.int16) for key in
            ('surface_y', 'terrain_y', 'canopy_y', 'canopy_min_y', 'trunk_y', 'water_y')}
    surface_id = np.zeros(shape, np.uint16)
    terrain_id = np.zeros(shape, np.uint16)
    names = ['minecraft:air']; name_ids = {names[0]: 0}
    counts = Counter(); sources = []; chunks = 0
    for rz in range(z0 // 512, z1 // 512 + 1):
        for rx in range(x0 // 512, x1 // 512 + 1):
            path = world / 'region' / f'r.{rx}.{rz}.mca'
            if not path.is_file() or path.stat().st_size < 8192: continue
            raw = path.read_bytes()
            sources.append({'name': path.name, 'size': len(raw), 'sha256': hashlib.sha256(raw).hexdigest()})
            for cz in range(max(rz * 32, z0 // 16), min(rz * 32 + 31, z1 // 16) + 1):
                for cx in range(max(rx * 32, x0 // 16), min(rx * 32 + 31, x1 // 16) + 1):
                    root = read_chunk(raw, cx, cz)
                    if root is None: continue
                    chunks += 1
                    bx0, bz0 = max(x0, cx * 16), max(z0, cz * 16)
                    bx1, bz1 = min(x1, cx * 16 + 15), min(z1, cz * 16 + 15)
                    dest = np.s_[bz0-z0:bz1-z0+1, bx0-x0:bx1-x0+1]
                    local = np.s_[bz0-cz*16:bz1-cz*16+1, bx0-cx*16:bx1-cx*16+1]
                    for section in value(root, 'sections', (10, []))[1]:
                        base_y = value(section, 'Y') * 16
                        if base_y + 15 < 0: continue  # Surface study; underground below Y=0 is excluded.
                        bs = value(section, 'block_states')
                        if not bs: continue
                        pal = value(bs, 'palette')[1]
                        pn = [value(s, 'Name') for s in pal]
                        if len(pn) == 1 and pn[0] in AIR: continue
                        pal, ids = unpack(bs)
                        ids = ids[:, local[0], local[1]]
                        global_ids = []
                        for name in pn:
                            if name not in name_ids:
                                name_ids[name] = len(names); names.append(name)
                            global_ids.append(name_ids[name])
                        global_ids = np.array(global_ids, np.uint16)
                        cats = [category(n) for n in pn]
                        terrain = [n.split(':')[-1] in GROUND or n.endswith(('_ore', '_coral_block')) for n in pn]
                        masks = {
                            'surface_y': np.array([n not in AIR for n in pn])[ids],
                            'terrain_y': np.array(terrain)[ids],
                            'canopy_y': np.array([c == 'canopy' for c in cats])[ids],
                            'trunk_y': np.array([c == 'trunk' for c in cats])[ids],
                            'water_y': np.array([c == 'water' for c in cats])[ids],
                        }
                        for key, present in masks.items():
                            top = np.max(np.where(present, np.arange(16)[:,None,None], -32768), axis=0)
                            top = np.where(top >= 0, top + base_y, -32768).astype(np.int16)
                            view = maps[key][dest]; newer = top > view
                            view[newer] = top[newer]
                            if key in ('surface_y', 'terrain_y'):
                                targets = surface_id if key == 'surface_y' else terrain_id
                                layer_ids = global_ids[ids[np.maximum(top - base_y, 0).clip(0,15),
                                                            np.arange(ids.shape[1])[:,None], np.arange(ids.shape[2])[None,:]]]
                                targets[dest][newer] = layer_ids[newer]
                        low = np.min(np.where(masks['canopy_y'], np.arange(16)[:,None,None], 32767), axis=0)
                        low = np.where(low <= 15, low + base_y, -32768)
                        dest_min = maps['canopy_min_y'][dest]
                        newer = (low > -32768) & ((dest_min == -32768) | (low < dest_min))
                        dest_min[newer] = low[newer]
                        if 48 <= base_y <= 144:
                            nums = np.bincount(ids.ravel(), minlength=len(pn))
                            for i, n in enumerate(pn):
                                if n not in AIR: counts[n] += int(nums[i])
            print('read', path.name, 'chunks', chunks, flush=True)
    return maps, surface_id, terrain_id, names, counts, sources, chunks


COLORS = {'air':(17,27,35), 'water':(53,111,143), 'canopy':(53,99,48),
          'trunk':(121,89,52), 'soil':(124,148,65), 'sand':(211,191,136),
          'rock':(130,133,124), 'understory':(105,136,54), 'built':(161,114,85)}


def diagram(maps, ids, names, bounds, output):
    rgb = np.array([COLORS[category(n)] for n in names], np.float32)[ids]
    h = np.where(maps['surface_y'] > -32768, maps['surface_y'], 62).astype(float)
    dz, dx = np.gradient(h)
    shade = np.clip(1 + (dz - dx) * .055, .67, 1.18)
    rgb = np.clip(rgb * shade[:,:,None], 0, 255).astype(np.uint8)
    pic = Image.fromarray(rgb).resize((ids.shape[1]*2,ids.shape[0]*2), Image.Resampling.NEAREST)
    draw = ImageDraw.Draw(pic)
    x0,z0,x1,z1=bounds
    for x in range(math.ceil(x0/64)*64, x1+1,64):
        px=(x-x0)*2
        draw.line((px,0,px,pic.height),fill=(93,124,138),width=1)
        draw.text((px+3,4),str(x),fill=(240,240,223),stroke_width=1,stroke_fill=(24,35,36))
    for z in range(math.ceil(z0/64)*64,z1+1,64):
        py=(z-z0)*2
        draw.line((0,py,pic.width,py),fill=(93,124,138),width=1)
        draw.text((4,py+3),str(z),fill=(240,240,223),stroke_width=1,stroke_fill=(24,35,36))
    ax,az=(-925-x0)*2,(3612-z0)*2
    draw.ellipse((ax-8,az-8,ax+8,az+8),outline=(255,221,101),width=3)
    draw.text((ax+12,az),'-925,63,3612',fill=(255,221,101),stroke_width=1,stroke_fill=(0,0,0))
    pic.save(output)


def slice_cells(world, axis, fixed, start, end, ymin, ymax, cache):
    """One-block-thick stored-cell section; model overhang is not rasterized."""
    names = ['minecraft:air']; lookup = {names[0]: 0}
    grid = np.zeros((ymax-ymin+1, end-start+1), np.uint16)
    for varying_chunk in range(start//16, end//16+1):
        cx, cz = (varying_chunk, fixed//16) if axis == 'x' else (fixed//16, varying_chunk)
        key = cx//32, cz//32
        if key not in cache:
            path = world/'region'/f'r.{key[0]}.{key[1]}.mca'
            cache[key] = path.read_bytes()
        root = read_chunk(cache[key], cx, cz)
        if not root: continue
        a,b=max(start,varying_chunk*16),min(end,varying_chunk*16+15)
        for section in value(root,'sections',(10,[]))[1]:
            base = value(section,'Y')*16
            lo,hi=max(ymin,base),min(ymax,base+15)
            if lo>hi: continue
            bs=value(section,'block_states')
            if not bs: continue
            pal,ids=unpack(bs)
            vals=[]
            for state in pal:
                name=value(state,'Name')
                if name not in lookup: lookup[name]=len(names);names.append(name)
                vals.append(lookup[name])
            selection = (ids[lo-base:hi-base+1,fixed%16,a%16:b%16+1] if axis=='x' else
                         ids[lo-base:hi-base+1,a%16:b%16+1,fixed%16])
            grid[lo-ymin:hi-ymin+1,a-start:b-start+1]=np.array(vals,np.uint16)[selection]
    return grid,names


def profiles(world, output):
    # These are observation samples, never suggested destination coordinates.
    samples=[('东侧森林：冠层与低叶层','z',-862,3468,3594,55,104),
             ('溪岸林缘：空地与坡脚','x',3530,-1073,-929,45,88),
             ('西南海岸：岸坡与海床','x',3600,-1080,-965,32,90),
             ('山坡：山肩、峰顶与林缘','z',-950,3350,3468,55,125)]
    cache={};pic=Image.new('RGB',(1360,1290),(237,238,229));draw=ImageDraw.Draw(pic)
    font_path='/System/Library/Fonts/STHeiti Medium.ttc'
    font=ImageFont.truetype(font_path,23);small=ImageFont.truetype(font_path,16)
    draw.text((36,18),'授权参考世界｜地形与森林方块剖面',font=font,fill=(34,48,44))
    draw.text((36,56),'存档位置示意；不是游戏截图。叶片模型外扩、贴图透明与光照不在剖面中显示。',font=small,fill=(72,80,74))
    for index,(title,axis,fixed,start,end,ymin,ymax) in enumerate(samples):
        grid,names=slice_cells(world,axis,fixed,start,end,ymin,ymax,cache)
        color=np.array([COLORS[category(n)] for n in names],np.uint8)
        color[0]=[201,220,225]
        rgb=color[grid][::-1]
        for i,name in enumerate(names):
            if name in AIR:rgb[(grid==i)[::-1]]=[201,220,225]
        cell=min(8,1200//grid.shape[1],210//grid.shape[0])
        panel=Image.fromarray(rgb).resize((grid.shape[1]*cell,grid.shape[0]*cell),Image.Resampling.NEAREST)
        px,py=72,125+index*282
        draw.text((36,py-32),f'{index+1}. {title}   {axis.upper()} {start}…{end}，固定 {"Z" if axis=="x" else "X"}={fixed}',font=font,fill=(34,48,44))
        pic.paste(panel,(px,py))
        draw.rectangle((px,py,px+panel.width,py+panel.height),outline=(87,98,90),width=1)
        for y in range(math.ceil(ymin/8)*8,ymax+1,8):
            dy=py+(ymax-y)*cell
            draw.text((25,dy-8),str(y),font=small,fill=(63,71,66))
        sea=py+(ymax-62)*cell
        if py<=sea<=py+panel.height:
            draw.line((px,sea,px+panel.width,sea),fill=(105,166,186),width=1)
        for q in range(math.ceil(start/16)*16,end+1,16):
            dx=px+(q-start)*cell
            draw.text((dx,py+panel.height+4),str(q),font=small,fill=(63,71,66))
    draw.text((36,1250),'蓝：水｜灰：岩层｜黄：沙｜绿：草地／叶层｜棕：树干／人工构件。每格代表存档的一格。',font=small,fill=(55,68,59))
    pic.save(output)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--world',type=Path,required=True)
    parser.add_argument('--bounds',type=int,nargs=4,required=True,metavar=('X0','Z0','X1','Z1'))
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    source=args.world.resolve();destination=args.output.resolve()
    if destination==source or source in destination.parents:
        parser.error('Research output must be outside the reference world')
    maps,sid,gid,names,counts,sources,chunks=extract(args.world,args.bounds)
    args.output.mkdir(parents=True,exist_ok=True)
    np.savez_compressed(args.output/'terrain-fields.npz', **maps, surface_id=sid, terrain_id=gid,
                        names=np.array(names), bounds=np.array(args.bounds))
    diagram(maps,sid,names,args.bounds,args.output/'surface-survey.png')
    profiles(args.world,args.output/'terrain-forest-sections.png')
    report={'reference':'星露谷复刻1.20.1@宅一夏','world':str(args.world),
            'authorization':'用户确认作者已授权参考学习','scope':'地形、森林造型；区域关系不沿用参考地图',
            'anchor':[-925,63,3612],'bounds':args.bounds,'loaded_chunks':chunks,
            'regions':sources,'surface_blocks':dict(Counter(names[i] for i in sid.ravel())),
            'blocks_y48_to159':dict(counts.most_common()),
            'limits':['图为存档方块类别与高程示意，不是游戏截图或原贴图渲染。',
                      '地面识别限于显式自然材料；建筑包覆、伪装模型和方块实体需局部复核。',
                      '只读源世界；没有修改玩家位置、保存世界或启动客户端。']}
    (args.output/'terrain-survey.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print('DONE', chunks, 'chunks;',len(names),'block names')


if __name__=='__main__': main()

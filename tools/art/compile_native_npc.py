#!/usr/bin/env python3
"""Compile the supported Generic bbmodel subset; never silently discard features."""
import argparse
import json
import math
from pathlib import Path
from npc_texture_atlas import pack
from npc_joint_skin import skin_joints


def vector(value, size=3):
    if not isinstance(value, list) or len(value) != size:
        raise ValueError(f'Expected {size}-component vector: {value}')
    result = [float(v) for v in value]
    if not all(math.isfinite(v) for v in result):
        raise ValueError('Non-finite vector')
    return result


def rotate(point, degrees):
    x, y, z = point
    for axis, angle in enumerate(degrees):
        c, s = math.cos(math.radians(angle)), math.sin(math.radians(angle))
        if axis == 0:
            y, z = y*c-z*s, y*s+z*c
        elif axis == 1:
            x, z = x*c+z*s, -x*s+z*c
        else:
            x, y = x*c-y*s, x*s+y*c
    return [x, y, z]


def compile_model(model, profile, variant='sam', required_clips=None):
    if model['meta']['model_format'] != 'free':
        raise ValueError('Author as a Generic model (free)')
    if str(model['meta']['format_version']).split('.')[0] != '5':
        raise ValueError('Only bbmodel 5.x is supported; verify a new version before enabling it')
    png,w,h,placements=pack(model.get('textures',[]),model['resolution'])
    if not (0 < profile['intervalMin'] < profile['intervalMax'] < 60
            and 0 < profile['durationMin'] < profile['durationMax'] < 3
            and 0 <= profile['doubleChance'] <= 1
            and math.isfinite(profile['groundOffset'])
            and 0 < profile['walkStride'] < 4):
        raise ValueError('Invalid idle motion profile')
    if 'attentionRig' in profile:
        rig = profile['attentionRig']
        values = [float(rig[k]) for k in ('hipHeight','soleY','toeDepth','halfStance','rightZ','leftZ',
                                        'rightYaw','leftYaw','lift','lean','armSwing')]
        if not all(math.isfinite(v) for v in values) or not (
                rig['hipHeight'] > rig['soleY'] and rig['toeDepth'] > 0 and rig['halfStance'] > 0 and rig['lift'] >= 0):
            raise ValueError('Invalid attention contact geometry')
    if profile.get('blinkMode') not in (None, 'occluded', 'closed'):
        raise ValueError('Unsupported blink visibility mode')
    groups = {g['uuid']: g for g in model['groups']}
    elements = {e['uuid']: e for e in model['elements']}
    bones, cubes, seen, names, indices = [], [], set(), set(), {}

    def visit(nodes, parent):
        for node in nodes:
            uid = node if isinstance(node, str) else node['uuid']
            if uid in seen:
                raise ValueError(f'Duplicate hierarchy reference: {uid}')
            seen.add(uid)
            if isinstance(node, str):
                cubes.append((parent, elements[uid]))
                continue
            group = groups[uid]
            if group['name'] in names:
                raise ValueError('Duplicate bone name')
            if not group.get('export', True) or not group.get('visibility', True):
                raise ValueError('Hidden/non-exported bone must be removed explicitly')
            names.add(group['name'])
            index = len(bones)
            indices[uid] = index
            bones.append(dict(name=group['name'], parent=parent,
                              origin=vector(group['origin']), rotation=vector(group.get('rotation', [0]*3))))
            visit(node.get('children', []), index)
    visit(model['outliner'], -1)
    if seen != set(groups) | set(elements):
        raise ValueError('Orphan bone/cube')
    cloth=profile.get('cloth')
    cloth_bone=-1
    if cloth:
        if cloth.get('kind') not in ('skirt','cape','apron','mantle') or cloth.get('bone') not in names or 'cloth_motion' not in names:
            raise ValueError('Invalid garment binding')
        if not all(math.isfinite(cloth[k]) for k in ('anchorY','hemY','margin')) or not (
                0 < cloth['anchorY']-cloth['hemY'] < 40 and 0 < cloth['margin'] < 2):
            raise ValueError('Invalid garment dimensions')
        cloth_bone=next(i for i,b in enumerate(bones) if b['name']==cloth['bone'])
        if cloth.get('clearancePart') is not None and not any(
                owner==cloth_bone and cube['name']==cloth['clearancePart'] for owner,cube in cubes):
            raise ValueError('Missing garment clearance surface')
        if 'preserveLayerOffset' in cloth and not isinstance(cloth['preserveLayerOffset'], bool):
            raise ValueError('Invalid garment layer preservation')
        if cloth.get('contactBlend') is not None and not (
                math.isfinite(cloth['contactBlend']) and 0 < cloth['contactBlend'] <= 2):
            raise ValueError('Invalid garment contact blend')
        if cloth.get('standingDepthMargin') is not None and not (
                math.isfinite(cloth['standingDepthMargin']) and 0 < cloth['standingDepthMargin'] <= cloth['margin']):
            raise ValueError('Invalid standing garment margin')
    if 'lookLimits' in profile:
        look=profile['lookLimits']
        if not all(math.isfinite(look[k]) and 0 < look[k] <= cap for k,cap in [('yaw',48),('pitch',18)]):
            raise ValueError('Invalid relative neck limits')
    translucent=set(profile.get('translucentParts', []))
    if not translucent <= {c['name'] for _,c in cubes}:
        raise ValueError('Missing translucent part')
    quads = []
    # Blockbench BoxGeometry: top-left, bottom-left, bottom-right, top-right; outward winding.
    corners = {
        'east': [(1,1,1),(1,0,1),(1,0,0),(1,1,0)],
        'west': [(0,1,0),(0,0,0),(0,0,1),(0,1,1)],
        'up': [(0,1,0),(0,1,1),(1,1,1),(1,1,0)],
        'down': [(0,0,1),(0,0,0),(1,0,0),(1,0,1)],
        'south': [(0,1,1),(0,0,1),(1,0,1),(1,1,1)],
        'north': [(1,1,0),(1,0,0),(0,0,0),(0,1,0)]}
    normals = dict(east=[1,0,0], west=[-1,0,0], up=[0,1,0], down=[0,-1,0], south=[0,0,1], north=[0,0,-1])
    for bone, cube in cubes:
        if cube.get('type', 'cube') != 'cube' or cube.get('box_uv', False) or cube.get('rescale', False):
            raise ValueError(f'Unsupported geometry: {cube["name"]}')
        if not cube.get('export', True) or not cube.get('visibility', True):
            raise ValueError('Hidden/non-exported cube')
        lo, hi = vector(cube['from']), vector(cube['to'])
        if any(a>b for a,b in zip(lo,hi)) and not all(a>b for a,b in zip(lo,hi)):
            raise ValueError('Only three-axis inverted hulls are supported')
        inflate = float(cube.get('inflate', 0))
        if not math.isfinite(inflate):
            raise ValueError('Invalid inflate')
        origin, rotation = vector(cube.get('origin', [0]*3)), vector(cube.get('rotation', [0]*3))
        for side, face in cube['faces'].items():
            if face.get('texture') is None:
                continue
            texture_index=face['texture']
            if type(texture_index) is not int or not 0<=texture_index<len(placements):
                raise ValueError('Invalid face texture')
            tx,ty,tw,th=placements[texture_index]
            cull=model['textures'][texture_index].get('render_sides')=='front'
            uv = vector(face['uv'], 4)
            if not (0 <= min(uv[0],uv[2]) <= max(uv[0],uv[2]) <= tw and 0 <= min(uv[1],uv[3]) <= max(uv[1],uv[3]) <= th):
                raise ValueError('Out-of-bounds UV')
            if uv[0] == uv[2] or uv[1] == uv[3]:
                raise ValueError('Enabled face has empty UV')
            # Ignore zero-area side faces, but preserve deliberate zero-thickness planes.
            axes = {'east':(1,2),'west':(1,2),'up':(0,2),'down':(0,2),'north':(0,1),'south':(0,1)}[side]
            if any(hi[i] == lo[i] for i in axes):
                continue
            if face.get('rotation', 0) != 0:
                raise ValueError('MVP requires unrotated face UVs')
            uv=[uv[0]+tx,uv[1]+ty,uv[2]+tx,uv[3]+ty]
            uvs = [(uv[0]/w,uv[1]/h),(uv[0]/w,uv[3]/h),(uv[2]/w,uv[3]/h),(uv[2]/w,uv[1]/h)]
            vertices = []
            for corner, texcoord in zip(corners[side], uvs):
                p = [(hi[i]+inflate if corner[i] else lo[i]-inflate)-origin[i] for i in range(3)]
                p = rotate(p, rotation)
                vertices.append([p[i]+origin[i] for i in range(3)] + list(texcoord))
            normal=rotate(normals[side], rotation)
            if bone==cloth_bone:
                # Horizontal rows let the source cloth bend continuously without splitting its silhouette.
                # Short coat panels need enough rows to clear a thigh immediately
                # below the fixed waist seam, rather than a long straight triangle.
                row_step=.25 if cloth['anchorY']-cloth['hemY']<=4 else .75
                a=max(1,math.ceil(max(abs(vertices[3][1]-vertices[0][1]),abs(vertices[2][1]-vertices[1][1]))/row_step))
                b=max(1,math.ceil(max(abs(vertices[1][1]-vertices[0][1]),abs(vertices[2][1]-vertices[3][1]))/row_step))
                def surface(u,v):
                    return [vertices[0][k]*(1-u)*(1-v)+vertices[1][k]*(1-u)*v+vertices[2][k]*u*v+vertices[3][k]*u*(1-v) for k in range(5)]
                for x in range(a):
                    for y in range(b):
                        row=[surface(x/a,y/b),surface(x/a,(y+1)/b),surface((x+1)/a,(y+1)/b),surface((x+1)/a,y/b)]
                        quads.append(dict(bone=bone,vertices=row,normal=normal,translucent=cube['name'] in translucent,sourcePart=cube['name'],cull=cull))
            else:
                quads.append(dict(bone=bone, vertices=vertices, normal=normal,translucent=cube['name'] in translucent,sourcePart=cube['name'],cull=cull))
    clips = {}
    for animation in model.get('animations', []):
        if animation['name'] in clips:
            raise ValueError('Duplicate animation name')
        duration = float(animation['length'])
        if not math.isfinite(duration) or duration <= 0:
            raise ValueError('Invalid clip duration')
        if animation.get('loop') not in ('once', 'loop'):
            raise ValueError('Unsupported loop mode')
        for field in ('anim_time_update','blend_weight','start_delay','loop_delay'):
            if animation.get(field, '').strip():
                raise ValueError(f'Expressions are unsupported: {field}')
        if animation.get('override', False):
            raise ValueError('Override clips are unsupported; use explicit channel ownership')
        tracks = []
        for uid, animator in animation.get('animators', {}).items():
            keys = animator.get('keyframes', [])
            if not keys:
                continue
            if uid not in indices or animator.get('rotation_global') or animator.get('quaternion_interpolation'):
                raise ValueError('Unsupported animator/bone')
            for channel in ('position', 'rotation', 'scale'):
                selected = sorted((k for k in keys if k['channel'] == channel), key=lambda k:k['time'])
                if not selected:
                    continue
                frames = []
                for key in selected:
                    t = float(key['time'])
                    mode = key.get('interpolation', 'linear')
                    if not math.isfinite(t) or not 0 <= t <= duration or mode not in ('linear','step') or key.get('easing'):
                        raise ValueError('Unsupported key time/interpolation')
                    if frames and t <= frames[-1]['time']:
                        raise ValueError('Duplicate key time')
                    points = key['data_points']
                    if len(points) not in (1, 2):
                        raise ValueError('Invalid pre/post key')
                    values = [vector([p.get(axis, 1 if channel=='scale' else 0) for axis in 'xyz']) for p in points]
                    if channel == 'scale' and any(v <= 0 for row in values for v in row):
                        raise ValueError('Use positive scale; zero scale produces singular normals')
                    frames.append(dict(time=t, before=values[0], after=values[-1], step=mode=='step'))
                if animation['loop'] == 'loop' and (frames[0]['time'] != 0 or frames[-1]['time'] != duration
                        or frames[0]['after'] != frames[-1]['before']):
                    raise ValueError('Loop tracks require matching explicit start/end keys')
                tracks.append(dict(bone=indices[uid], channel=channel, keys=frames))
            if any(k['channel'] not in ('position','rotation','scale') for k in keys):
                raise ValueError('Event tracks are not supported')
        clips[animation['name']] = dict(length=duration, loop=animation['loop']=='loop', tracks=tracks)
    if variant == 'robin_construction':
        required = ('animation.robin.construction', 'animation.robin.construction_low')
    elif variant in ('sam_guitar', 'sam_gameboy', 'sam_skateboard', 'sam_sweep', 'sam_sit', 'sam_sleep', 'sam_pool'):
        activity = variant.removeprefix('sam_')
        required = (f'animation.sam.{activity}_play', f'animation.sam.{activity}_hold', 'animation.sam.idle')
    else:
        required = (f'animation.{variant}.idle', f'animation.{variant}.blink', f'animation.{variant}.walk')
    if profile.get('blinkMode') in ('occluded', 'closed'):
        required = tuple(n for n in required if not n.endswith('.blink'))
        if any(n.endswith('.blink') for n in clips):
            raise ValueError('Fixed closed or occluded eyes must not have a blink clip')
    if required_clips is not None:
        if not required_clips or any(not isinstance(name, str) or not name.strip() for name in required_clips):
            raise ValueError('Activity required_clips must name nonempty clips')
        required = required_clips
    for name in required:
        if name not in clips:
            raise ValueError(f'Missing pilot clip: {name}')
    hip_skin=float(profile.get('hipSkinHeight', 0))
    if not math.isfinite(hip_skin) or not 0 <= hip_skin <= 4:
        raise ValueError('Invalid hip skin height')
    quads = skin_joints(bones, cubes, quads, hip_skin)
    animated = {track['bone'] for clip in clips.values() for track in clip['tracks']}
    for quad in quads:
        skin = quad.get('skin')
        if skin and quad['bone'] not in (skin['upper'], skin['lower']) and quad['bone'] in animated:
            raise ValueError('Independent animated detail requires explicit skin binding: '+quad['sourcePart'])
    result = dict(version=1, texture=f'stardewcraft:textures/entity/npc_native/{variant}.png',
                  textureWidth=w,textureHeight=h,bones=bones, quads=quads, clips=clips, profile=profile)
    return result, png


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('source', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    base = args.output/'assets/stardewcraft'
    manifest = json.loads((args.source/'models.json').read_text())
    for variant, directory in manifest.items():
        source = args.source/directory
        profile_path=source/f'{variant}.motion.json'
        profile = json.loads((profile_path if profile_path.exists() else source/'motion.json').read_text())
        data, png = compile_model(json.loads((source/f'{variant}.bbmodel').read_text()), profile, variant,
                                  required_clips=profile.get('requiredClips'))
        for rel, content in [(f'npc_native/{variant}.json', json.dumps(data, separators=(',', ':'), allow_nan=False).encode()),
                             (f'textures/entity/npc_native/{variant}.png', png)]:
            path = base/rel
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)
        print(f'Native {variant}: {len(data["bones"])} bones, {len(data["quads"])} quads, {len(data["clips"])} clips')
    (base/'npc_native_models.json').write_text(json.dumps(sorted(manifest))+'\n')


if __name__ == '__main__':
    main()

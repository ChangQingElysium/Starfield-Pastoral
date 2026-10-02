#!/usr/bin/env python3
"""Import Java/generic cuboid bbmodels through one model/collision/item pipeline.

Only the import step reads the source; generated runtime assets are self-contained.
"""
import argparse
import base64
import json
import math
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets'


def identity():
    return [[float(i == j) for j in range(4)] for i in range(4)]


def mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def translate(v):
    m = identity()
    for i in range(3):
        m[i][3] = v[i]
    return m


def rotate(pivot, angles):
    m = translate(pivot)
    for axis in (2, 1, 0):
        r = identity()
        a, b = (axis + 1) % 3, (axis + 2) % 3
        angle = math.radians(angles[axis])
        r[a][a] = r[b][b] = math.cos(angle)
        r[a][b], r[b][a] = -math.sin(angle), math.sin(angle)
        m = mul(m, r)
    return mul(m, translate([-v for v in pivot]))


def resource(value):
    if not re.fullmatch(r'[a-z0-9_.-]+:[a-z0-9_./-]+', value) or '..' in value:
        raise ValueError(f'Invalid resource id: {value}')
    return value.split(':', 1)


def output_path(identifier, kind, suffix='.json'):
    namespace, path = resource(identifier)
    return ASSETS / namespace / kind / (path + suffix)


def block_display():
    # Use Minecraft's authored block transforms through a parent, override any slot independently.
    return 'minecraft:block/block'


def import_bbmodel(project, identifier, emissive_elements=(), *, texture_metadata=None,
                   allow_inverted_hulls=False):
    fmt = project.get('meta', {}).get('model_format')
    if fmt not in ('java_block', 'free', 'bedrock', 'geckolib', 'geckolib_model'):
        raise ValueError(f'Unsupported bbmodel format: {fmt}')
    if project.get('animations'):
        raise ValueError('Animated bbmodel: keep/export its GeckoLib world renderer; import its .geo.json rest pose for item display')
    native = fmt == 'java_block'
    groups = {g['uuid']: g for g in project.get('groups', [])}
    elements = {e['uuid']: e for e in project.get('elements', [])}
    resolution = project.get('resolution', {'width': 16, 'height': 16})
    textures, files, aliases = {}, {}, {}
    for index, texture in enumerate(project.get('textures', [])):
        key = str(index)
        name = identifier + ('' if len(project['textures']) == 1 else '_' + key)
        textures[key] = name
        for alias in (index, str(index), texture.get('id'), texture.get('uuid')):
            if alias is not None:
                aliases[alias] = key
        source = texture.get('source', '')
        if not source.startswith('data:image/png;base64,'):
            raise ValueError('Embed all PNG textures in the bbmodel before importing')
        png = base64.b64decode(source.split(',', 1)[1], validate=True)
        if not png.startswith(b'\x89PNG\r\n\x1a\n'):
            raise ValueError('Invalid embedded PNG')
        files[output_path(name, 'textures', '.png')] = png
        # Integer-upscaled item art is one image, not a vertical animation strip.
        if texture.get('height', 0) > texture.get('width', 0) and texture.get('height', 0) * texture.get('uv_width', resolution['width']) > texture.get('width', 0) * texture.get('uv_height', resolution['height']):
            metadata = (texture_metadata or {}).get(index)
            if metadata is None or 'animation' not in metadata:
                raise ValueError('Animated texture needs explicit frame metadata; import a static texture or author its .mcmeta')
            files[output_path(name, 'textures', '.png.mcmeta')] = (json.dumps(metadata) + '\n').encode()
    if not textures:
        raise ValueError('Model has no textures')
    particle = next((str(i) for i, t in enumerate(project['textures']) if t.get('particle')), '0')
    textures['particle'] = textures[particle]
    converted = []
    unmatched_emissive = set(emissive_elements)

    def walk(nodes, parent):
        for node in nodes:
            if isinstance(node, dict):
                group = {**groups.get(node.get('uuid'), {}), **node}
                if not group.get('export', True):
                    continue
                rotation = group.get('rotation', [0, 0, 0])
                if native and any(rotation):
                    raise ValueError('Java block groups cannot rotate; export the group transform explicitly')
                walk(group.get('children', []), mul(parent, rotate(group.get('origin', [0, 0, 0]), rotation)))
                continue
            e = elements[node]
            if not e.get('export', True):
                continue
            if e.get('type', 'cube') != 'cube':
                raise ValueError('Only cuboid generic models are supported; mesh elements must be converted explicitly')
            if e.get('box_uv'):
                raise ValueError('Convert box UV to per-face UV in Blockbench before importing bbmodel')
            inflate = e.get('inflate', 0)
            part = {'from': [x - inflate for x in e['from']], 'to': [x + inflate for x in e['to']], 'faces': {}}
            # Java accepts reversed endpoints for authored inward-facing outline shells.
            if not native and any(part['to'][i] < part['from'][i] for i in range(3)) and not (
                    allow_inverted_hulls and all(part['to'][i] < part['from'][i] for i in range(3))):
                raise ValueError('Inverted cuboid bounds')
            angles = e.get('rotation', [0, 0, 0])
            if native:
                axes = [i for i, a in enumerate(angles) if a]
                if len(axes) > 1 or any(angles[i] not in (-45, -22.5, 22.5, 45) for i in axes):
                    raise ValueError('Rotation cannot be represented by a native Java block model')
                if any(v < -16 or v > 32 for v in part['from'] + part['to']):
                    raise ValueError('Native Java coordinates must be between -16 and 32')
                if axes:
                    part['rotation'] = {'origin': e.get('origin', [8, 8, 8]), 'axis': 'xyz'[axes[0]], 'angle': angles[axes[0]], 'rescale': e.get('rescale', False)}
            else:
                matrix = mul(parent, rotate(e.get('origin', [0, 0, 0]), angles))
                part['transform'] = [round(matrix[i][j], 8) for j in range(4) for i in range(4)]
            for direction, face in e.get('faces', {}).items():
                ref = face.get('texture')
                if ref is None or not face.get('enabled', True):
                    continue
                key = aliases.get(ref)
                if key is None:
                    raise ValueError(f'Unknown texture {ref}')
                tex = project['textures'][int(key)]
                # Java uses the project's UV canvas even when a PNG has a different size.
                width = resolution['width'] if native else tex.get('uv_width') or resolution['width']
                height = resolution['height'] if native else tex.get('uv_height') or resolution['height']
                uv = [v * 16 / (width if i % 2 == 0 else height) for i, v in enumerate(face['uv'])]
                target = {'uv': uv, 'texture': '#' + key}
                if face.get('rotation'):
                    target['rotation'] = face['rotation']
                # Cross-cell models must not cull a face just because the main cell has a neighbour.
                part['faces'][direction] = target
            if e.get('shade') is False:
                part['shade'] = False
            emission = e.get('light_emission', 0)
            if e.get('name') in emissive_elements:
                unmatched_emissive.discard(e['name'])
                emission = 15
            if emission:
                if not 0 <= emission <= 15:
                    raise ValueError('Element emission must be between 0 and 15')
                part['shade'] = False
                part['neoforge_data'] = {'block_light': emission, 'sky_light': emission, 'ambient_occlusion': False}
            converted.append(part)

    walk(project.get('outliner') or list(elements), identity())
    if not converted:
        raise ValueError('No exported cuboids')
    if unmatched_emissive:
        raise ValueError('Emissive elements not exported: ' + ', '.join(sorted(unmatched_emissive)))
    model = {'parent': block_display(), 'render_type': 'minecraft:cutout', 'textures': textures}
    model['elements' if native else 'parts'] = converted
    if not native:
        model['loader'] = 'stardewcraft:geometry'
    if project.get('display'):
        model['display'] = project['display']
    return model, files


def import_geo(project, texture):
    resource(texture)
    geometry = project['minecraft:geometry'][0]
    description = geometry['description']
    width, height = description['texture_width'], description['texture_height']
    bones = {b['name']: b for b in geometry['bones']}
    matrices, visiting = {}, set()

    def bone_matrix(name):
        if name in visiting:
            raise ValueError('Cyclic bone parents')
        if name in matrices:
            return matrices[name]
        visiting.add(name)
        bone = bones[name]
        pivot = bone.get('pivot', [0, 0, 0])
        rotation = bone.get('rotation', [0, 0, 0])
        local = rotate([-pivot[0], pivot[1], pivot[2]], [-rotation[0], -rotation[1], rotation[2]])
        matrices[name] = mul(bone_matrix(bone['parent']), local) if bone.get('parent') else local
        visiting.remove(name)
        return matrices[name]

    parts = []
    for name, bone in bones.items():
        for cube in bone.get('cubes', []):
            x, y, z = cube['origin']
            dx, dy, dz = cube['size']
            inflate = cube.get('inflate', bone.get('inflate', 0))
            part = {'from': [-x - dx - inflate, y - inflate, z - inflate], 'to': [-x + inflate, y + dy + inflate, z + dz + inflate], 'faces': {}}
            pivot = cube.get('pivot', [0, 0, 0])
            rotation = cube.get('rotation', [0, 0, 0])
            matrix = mul(translate([8, 0, 8]), mul(bone_matrix(name), rotate([-pivot[0], pivot[1], pivot[2]], [-rotation[0], -rotation[1], rotation[2]])))
            part['transform'] = [round(matrix[i][j], 8) for j in range(4) for i in range(4)]
            uv = cube.get('uv', [0, 0])
            box_uv = isinstance(uv, list)
            if box_uv:
                u, v = uv
                ux, uy, uz = math.floor(dx), math.floor(dy), math.floor(dz)
                uv = {'east': {'uv': [u, v + uz], 'uv_size': [uz, uy]},
                      'north': {'uv': [u + uz, v + uz], 'uv_size': [ux, uy]},
                      'west': {'uv': [u + uz + ux, v + uz], 'uv_size': [uz, uy]},
                      'south': {'uv': [u + 2 * uz + ux, v + uz], 'uv_size': [ux, uy]},
                      'up': {'uv': [u + uz, v], 'uv_size': [ux, uz]},
                      'down': {'uv': [u + uz + ux, v + uz], 'uv_size': [ux, -uz]}}
            mirror = cube.get('mirror', False)
            for side, face in uv.items():
                u, v = face['uv']
                su, sv = face.get('uv_size', [0, 0])
                target = side
                if mirror:
                    target = {'east': 'west', 'west': 'east'}.get(side, side)
                    if not box_uv:
                        target = {'up': 'down', 'down': 'up'}.get(target, target)
                # Map GeckoLib's quad vertex order to Java FaceInfo, preserving UV rotation/mirror.
                left, right = (u, u + su) if mirror else (u + su, u)
                geo_uvs = [[left, v], [right, v], [right, v + sv], [left, v + sv]]
                turns = int(face.get('uv_rotation', 0)) // 90 % 4
                geo_uvs = geo_uvs[turns:] + geo_uvs[:turns]
                indices = [3, 0, 1, 2] if target in ('up', 'down') else [1, 2, 3, 0]
                desired = [geo_uvs[i] for i in indices]
                found = None
                for flip_u in (False, True):
                    for flip_v in (False, True):
                        u0, u1 = (u + su, u) if flip_u else (u, u + su)
                        v0, v1 = (v + sv, v) if flip_v else (v, v + sv)
                        native = [[u0, v0], [u0, v1], [u1, v1], [u1, v0]]
                        for turn in range(4):
                            if [native[(i + turn) % 4] for i in range(4)] == desired:
                                found = {'texture': '#0', 'uv': [u0 * 16 / width, v0 * 16 / height, u1 * 16 / width, v1 * 16 / height], 'rotation': turn * 90}
                                break
                        if found is not None: break
                    if found is not None: break
                if found is None:
                    raise ValueError('Geo UV cannot be represented by a rectangular face')
                part['faces'][target] = found
            parts.append(part)
    return {'parent': block_display(), 'loader': 'stardewcraft:geometry', 'render_type': 'minecraft:cutout', 'textures': {'0': texture, 'particle': texture}, 'parts': parts}, {}


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False) + '\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    parser.add_argument('--model', required=True, help='Namespaced model id, e.g. stardewcraft:block/decor/chair')
    parser.add_argument('--collision', choices=['voxel', 'aabb', 'custom'], default='aabb')
    parser.add_argument('--boxes', type=Path, help='Custom list of {from: [x,y,z], to: [x,y,z]} in model pixels')
    parser.add_argument('--geo-texture', help='Existing namespaced texture for .geo.json input')
    parser.add_argument('--item', help='Item registry id to generate an item model for')
    parser.add_argument('--item-texture', help='Use an existing texture instead of the block model')
    parser.add_argument('--item-model', help='Use a different existing model for the item')
    parser.add_argument('--display', type=Path, help='JSON display transforms, overrides authored slots')
    parser.add_argument('--blockstate', help='Generate facing/part blockstate for this registry id')
    parser.add_argument('--base-facing', choices=['north', 'south', 'east', 'west'], default='north')
    parser.add_argument('--emissive-element', action='append', default=[], help='bbmodel element name to render at full brightness (repeatable)')
    args = parser.parse_args()
    try:
        source = json.loads(args.source.read_text())
        resource(args.model)
        for identifier in (args.item, args.item_model, args.item_texture, args.blockstate):
            if identifier: resource(identifier)
        if not args.item and (args.display or args.item_model or args.item_texture):
            raise ValueError('Item display options require --item')
        if 'minecraft:geometry' in source:
            if args.emissive_element:
                raise ValueError('--emissive-element requires bbmodel input')
            if not args.geo_texture:
                raise ValueError('--geo-texture is required for .geo.json')
            model, files = import_geo(source, args.geo_texture)
        else:
            model, files = import_bbmodel(source, args.model, args.emissive_element)
        collision = {'mode': args.collision}
        if args.collision == 'custom':
            if not args.boxes:
                raise ValueError('--boxes is required for custom collision')
            collision['boxes'] = json.loads(args.boxes.read_text())
            if not collision['boxes'] or any(len(b['from']) != 3 or len(b['to']) != 3 or any(b['to'][i] <= b['from'][i] for i in range(3)) for b in collision['boxes']):
                raise ValueError('Custom boxes must have positive dimensions')
        model['stardewcraft:collision'] = collision
        outputs = {output_path(args.model, 'models'): model}
        if args.item:
            if args.item_texture and args.item_model:
                raise ValueError('Choose either item texture or item model')
            item = {'parent': args.item_model or args.model}
            if args.item_texture:
                resource(args.item_texture)
                item = {'parent': 'minecraft:item/generated', 'textures': {'layer0': args.item_texture}}
            if args.display:
                item['display'] = json.loads(args.display.read_text())
            outputs[output_path(args.item.split(':')[0] + ':item/' + args.item.split(':')[1], 'models')] = item
        if args.blockstate:
            empty = args.model + '_extension'
            outputs[output_path(empty, 'models')] = {'textures': {'particle': model['textures']['particle']}, 'elements': []}
            facings = ['north', 'east', 'south', 'west']
            variants = {}
            for facing in facings:
                turns = (facings.index(facing) - facings.index(args.base_facing)) % 4
                variants[f'facing={facing},part=main'] = {'model': args.model, 'y': turns * 90}
                variants[f'facing={facing},part=extension'] = {'model': empty}
            outputs[output_path(args.blockstate, 'blockstates')] = {'variants': variants}
        # Finish validation before writing any runtime files.
        for value in outputs.values():
            json.dumps(value, allow_nan=False)
        for path, value in outputs.items():
            write_json(path, value)
        for path, png in files.items():
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(png)
        print(f'Imported {args.source.name}: {len(model.get("elements", model.get("parts", [])))} cuboids, {len(outputs) + len(files)} files')
    except (ValueError, KeyError, TypeError) as error:
        parser.error(str(error))


if __name__ == '__main__':
    main()

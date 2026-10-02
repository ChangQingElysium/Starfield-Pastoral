#!/usr/bin/env python3
"""Export Ginger Island's authored assets, with an exhaustive source disposition ledger.

This authoring command can read assets-src. The game only reads generated classpath files.
It never modifies bbmodels, terrain plans, or Minecraft saves.
"""
import argparse
import copy
import hashlib
import itertools
import json
import math
from pathlib import Path
import sys

from import_block_model import import_bbmodel, output_path, write_json, rotate
from ginger_island_catalog import FAMILIES, MODEL_ONLY, ASSEMBLIES, STATE_VALUES, item_type

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'assets-src/ginger_island'
ASSETS = ROOT / 'src/main/resources/assets/stardewcraft'
LEDGER = ROOT / 'docs/ginger-island-research/integration/source-assets.json'

# Functional objects use the established gameplay service, never the decoration fallback.
KINDS = {
    'tropical_bed': ('bed', 'furniture'),
    'island_red_cooker': ('kitchen', 'utility'),
    'caldera_forge': ('forge', 'utility'),
    'tropical_tv': ('tv', 'furniture'),
    'heavy_tapper': ('heavy_tapper', 'utility'),
    'tropical_chair': ('chair', 'furniture'),
    'pirate_square_stool': ('chair', 'furniture'),
    'resort_beach_chair': ('beach_chair', 'furniture'),
    'ostrich_incubator_empty': ('ostrich_incubator', 'utility'),
    'stove_fireplace': ('fireplace', 'furniture'),
    'island_torch_unlit': ('altar_torch', 'furniture'),
    'volcano_floor_switch': ('volcano_switch', 'building'),
}
STATE_BINDINGS = {
    'ostrich_incubator_loaded': {'block': 'stardewcraft:ginger_ostrich_incubator_empty', 'state': {'loaded': 'true'}},
    'stove_fireplace_lit': {'block': 'stardewcraft:ginger_stove_fireplace', 'state': {'lit': 'true'}},
    'island_torch_lit': {'block': 'stardewcraft:ginger_island_torch_unlit', 'state': {'lit': 'true'}},
    'volcano_floor_switch_pressed': {'block': 'stardewcraft:ginger_volcano_floor_switch', 'state': {'pressed': 'true'}},
}
REUSES = {
    'island_fridge_open': {'block': 'stardewcraft:fridge',
        'models': ['stardewcraft:block/utility/fridge_body', 'stardewcraft:block/utility/fridge_door'],
        'reason': 'Existing FridgeBlock owns the inventory and continuous lower-door animation.'},
}

# These files are explicitly superseded or are material/assembly experiments.
EXCLUDED = {
    'golden_walnut_bush.bbmodel': 'superseded by golden_walnut_bush_rework',
    'golden_walnut_bush_white.bbmodel': 'untextured work stage',
    'material-sample.bbmodel': 'material study',
    'bridge-assembly.bbmodel': 'assembly reference; native bridge modules are authoritative',
    'island_trader_stall.bbmodel': 'superseded by animated whole stall assembly',
}


def ordered_elements(project):
    groups = {g['uuid']: g for g in project.get('groups', [])}
    elements = {e['uuid']: e for e in project['elements']}
    result = []
    def walk(nodes):
        for node in nodes:
            if isinstance(node, dict):
                group = {**groups.get(node.get('uuid'), {}), **node}
                if group.get('export', True): walk(group.get('children', []))
            elif elements[node].get('export', True): result.append(elements[node])
    walk(project.get('outliner') or list(elements))
    return result


def part_bounds(part):
    lo, hi = part['from'], part['to']
    if any(hi[a] < lo[a] for a in range(3)): return None
    hi = [max(hi[a], lo[a] + .01) for a in range(3)]
    points = [list(p) for p in itertools.product(*zip(lo, hi))]
    if 'rotation' in part:
        rotation = part['rotation']; angles = [0, 0, 0]
        axis = 'xyz'.index(rotation['axis']); angles[axis] = rotation['angle']
        if rotation.get('rescale'):
            scale = 1 / math.cos(math.radians(rotation['angle']))
            points = [[rotation['origin'][a] + (p[a] - rotation['origin'][a]) * (1 if a == axis else scale)
                       for a in range(3)] for p in points]
        matrix = rotate(rotation['origin'], angles)
        points = [[sum(matrix[a][j] * p[j] for j in range(3)) + matrix[a][3] for a in range(3)] for p in points]
    if 'transform' in part:
        m = part['transform']
        points = [[sum(m[j * 4 + a] * p[j] for j in range(3)) + m[12 + a] for a in range(3)] for p in points]
    return {'from': [round(min(p[a] for p in points), 4) for a in range(3)],
            'to': [round(max(p[a] for p in points), 4) for a in range(3)]}


def envelope(boxes):
    return {'from': [min(b['from'][a] for b in boxes) for a in range(3)],
            'to': [max(b['to'][a] for b in boxes) for a in range(3)]}


def box(lo, hi):
    return {'from': list(lo), 'to': list(hi)}


def subtract_box(solid, opening):
    """At most six coarse regions, never pixel collisions. Retain a clear portal corridor."""
    lo = [max(solid['from'][a], opening['from'][a]) for a in range(3)]
    hi = [min(solid['to'][a], opening['to'][a]) for a in range(3)]
    if any(lo[a] >= hi[a] for a in range(3)): return [solid]
    result = []; start = solid['from'][:]; end = solid['to'][:]
    for a in range(3):
        if start[a] < lo[a]:
            edge = end[:]; edge[a] = lo[a]; result.append(box(start, edge)); start[a] = lo[a]
        if end[a] > hi[a]:
            edge = start[:]; edge[a] = hi[a]; result.append(box(edge, end)); end[a] = hi[a]
    return result


def simplified_collision(path, project, model):
    """User-approved collision policy: one AABB, or a few functional AABB regions.

    Mesh detail, leaf tips, rope knots and texture planes do not create physics parts.
    Source geometry and the existing cabin blueprint determine the coarse regions.
    """
    name = path.stem
    if name == 'captain_cabin_shell':
        # This is the merged form of the 130 owned shell cells in shell-layout.json.
        # 48 interior cells and the two western entry cells remain available.
        bounds = [box((0, 0, 0), (96, 16, 80)), box((0, 80, 0), (96, 96, 80)),
                  box((0, 16, 0), (96, 80, 16)), box((0, 16, 64), (96, 80, 80)),
                  box((80, 16, 16), (96, 80, 64)), box((0, 16, 16), (16, 80, 32)),
                  box((0, 16, 48), (16, 80, 64)), box((0, 48, 32), (16, 80, 48))]
        return bounds, 'compound_cabin_shell'
    if name == 'professor_field_tent_assembly':
        return [box((0, 0, 0), (31, 40, 12)), box((49, 0, 0), (80, 40, 12)),
                box((31, 32, 0), (49, 41, 12)), box((0, 0, 12), (80, 41, 40))], 'compound_tent_entry'
    if name == 'island_parrot_platform':
        bounds = [box((0, 0, 0), (48, 4, 32)), box((0, 4, 0), (6, 40, 6)),
                  box((42, 4, 0), (48, 40, 6)), box((0, 4, 26), (6, 40, 32)),
                  box((42, 4, 26), (48, 40, 32)), box((0, 5, 6), (3, 8, 26)),
                  box((45, 5, 6), (48, 8, 26)), box((6, 6, 28), (42, 9, 31)),
                  box((6, 6, 1), (16, 9, 4)), box((32, 6, 1), (42, 9, 4)),
                  box((-10, 40, -12), (58, 60, 43))]
        return bounds, 'compound_platform'
    if name == 'pirate_round_table':
        return [box((1, 14, 1), (31, 16, 31)), box((9, 11, 9), (23, 14, 23)),
                box((13, 0, 13), (19, 11, 19)), box((5, 0, 14), (27, 6, 18)),
                box((14, 0, 5), (18, 6, 27))], 'compound_table'
    if name == 'pirate_treasure_table':
        return [box((1, 11, 0), (31, 16, 16)), box((2, 0, 1), (4, 11, 15)),
                box((28, 0, 1), (30, 11, 15)), box((3, 16, 1), (6, 23, 5)),
                box((21, 16, 1), (29, 20, 10))], 'compound_table'
    if name == 'professor_work_desk':
        return [box((-1, 0, 0), (31, 16, 3)), box((28, 0, 3), (31, 16, 32)),
                box((-1, 12, 29), (28, 16, 32)), box((-1, 14, 3), (28, 16, 12)),
                box((-1, 0, 29), (2, 12, 32)), box((21, 14, 24), (32, 23, 32))], 'compound_desk'
    parts = model.get('parts', model.get('elements'))
    if parts is None: parts = model.get('stardewcraft:collision', {}).get('boxes', [])
    bounds = [b for part in parts if (b := part_bounds(part)) is not None]
    if not bounds: raise ValueError('No positive collision bounds')
    if name == 'resort_changing_entry': return bounds, 'compound_door_frame'
    if name == 'island_shipwreck':
        import re
        elements = ordered_elements(project)
        if len(elements) != len(parts): raise ValueError('Ship collision source order mismatch')
        regions = {}; threshold = None
        for element, part in zip(elements, parts):
            item = element['name']; b = part_bounds(part)
            if b is None: continue
            if item == 'captain_entry_threshold': threshold = part
            match = re.match(r'(stern|bow)_(-?1)_', item)
            if match: key = match[1] + '_side_' + match[2]
            elif item.startswith('stern_transom'): key = 'stern_transom'
            elif item.startswith(('deck_plank', 'bilge_floor', 'stern_keel')): key = 'stern_deck_and_floor'
            elif item.startswith(('bow_floor', 'bow_keel')): key = 'bow_floor'
            elif item.startswith(('bow_stem', 'bow_fracture', 'bow_splinter')): key = 'bow_stem'
            elif item.startswith(('fallen_mast', 'mast_binding')): key = 'fallen_mast'
            elif item.startswith(('cross_yard', 'yard_broken_tip')): key = 'cross_yard'
            elif item.startswith('floating_board'): key = 'floating_board_' + str(int(item.rsplit('_', 1)[1]) // 3)
            else: continue  # Rope, algae and tiny broken trim are visual detail.
            regions.setdefault(key, []).append(b)
        if threshold is None: raise ValueError('Ship has no authored entrance')
        # Source doorway: x=-138..-105, z=-50..-24. Its parent transform is
        # inherited from the unrotated threshold, including the hull's tilt.
        entry = part_bounds({**threshold, 'from': [-160, 8, -49.5], 'to': [-90, 47, -24.5]})
        result = [piece for items in regions.values() for piece in subtract_box(envelope(items), entry)]
        return result, 'compound_ship_entry'
    return [envelope(bounds)], 'aabb'


def remove_reused_registration(name, keep_model=False):
    runtime = 'ginger_' + name
    stale = [ASSETS / 'blockstates' / (runtime + '.json'), ASSETS / 'models/item' / (runtime + '.json'),
             ROOT / 'src/main/resources/data/stardewcraft/loot_table/blocks' / (runtime + '.json')]
    if not keep_model:
        stale.extend((ASSETS / 'models/block/ginger_island').glob(name + '*.json'))
        stale.extend((ASSETS / 'textures/block/ginger_island').glob(name + '_*.png*'))
    for path in stale: path.unlink(missing_ok=True)


def disposition(path):
    rel = str(path.relative_to(SOURCE))
    if path.name in EXCLUDED:
        return EXCLUDED[path.name]
    if any(word in rel.lower() for word in ('reject', 'preview', '_check', '-check', 'whitebox',
                                             'white.bbmodel', 'stage-', 'draft', 'proof')):
        return 'authoring/check scene, not a runtime object'
    return None


def metadata_for(path, project):
    result = {}
    for index, texture in enumerate(project.get('textures', [])):
        if texture.get('height', 0) <= texture.get('width', 0):
            continue  # Small static particle sprites do not inherit atlas frame dimensions.
        if texture.get('height', 0) * texture.get('uv_width', project['resolution']['width']) <= texture.get('width', 0) * texture.get('uv_height', project['resolution']['height']):
            continue
        candidates = list(path.parent.rglob(str(texture.get('name', '')) + '.mcmeta'))
        if not candidates:
            candidates = list(path.parent.rglob(Path(str(texture.get('name', ''))).stem + '.png.mcmeta'))
        if candidates:
            result[index] = json.loads(candidates[0].read_text())
        elif texture.get('frame_time') is not None:
            # Blockbench frame timing is an authored texture property, in game ticks.
            result[index] = {'animation': {'frametime': max(1, int(texture['frame_time'])),
                                         'interpolate': bool(texture.get('frame_interpolate', False)),
                                         'width': texture['width'], 'height': texture['width']}}
        else:
            raise ValueError('No authored animation timing for texture ' + str(texture.get('name')))
    return result


def native_composite(project, identifier, metadata):
    """Keep oversize java_block objects native; partition faces, with no scale/mesh conversion.

    Child models are ordinary Java cuboids with the original UVs and allowed rotations.
    NeoForge's existing composite model merely places their local coordinate origins.
    Collision retains the original signed-positive component volumes, including empty rooms.
    """
    probe = copy.deepcopy(project)
    probe['meta']['model_format'] = 'free'
    # Native UVs use the project canvas. Keep that convention in the generic intermediate.
    for texture in probe.get('textures', []):
        texture['uv_width'] = project['resolution']['width']
        texture['uv_height'] = project['resolution']['height']
    model, files = import_bbmodel(probe, identifier, texture_metadata=metadata, allow_inverted_hulls=True)
    groups = {g['uuid']: g for g in project.get('groups', [])}
    elements = {e['uuid']: e for e in project['elements']}
    ordered = []
    def collect(nodes):
        for node in nodes:
            if isinstance(node, dict):
                group = {**groups.get(node.get('uuid'), {}), **node}
                if not group.get('export', True): continue
                if any(group.get('rotation', [0, 0, 0])): raise ValueError('Native groups cannot rotate')
                collect(group.get('children', []))
            elif elements[node].get('export', True):
                ordered.append(elements[node])
    collect(project.get('outliner') or list(elements))
    if len(ordered) != len(model['parts']): raise ValueError('Composite source order mismatch')
    children, boxes = {}, []
    for index, part in enumerate(model['parts']):
        matrix = part['transform']
        # Native groups cannot rotate. Per-element rotations are recovered below.
        element = ordered[index]
        angles = element.get('rotation', [0, 0, 0])
        axes = [a for a in range(3) if angles[a]]
        if len(axes) > 1 or any(angles[a] not in (-45, -22.5, 22.5, 45) for a in axes):
            raise ValueError('Oversize native model has a non-Java rotation')
        lo, hi = part['from'], part['to']
        if all(hi[a] > lo[a] for a in range(3)):
            corners = [[hi[a] if bits[a] else lo[a] for a in range(3)]
                       for bits in itertools.product((0, 1), repeat=3)]
            points = [[sum(matrix[j * 4 + a] * point[j] for j in range(3)) + matrix[12 + a]
                       for a in range(3)] for point in corners]
            boxes.append({'from': [min(p[a] for p in points) for a in range(3)],
                          'to': [max(p[a] for p in points) for a in range(3)]})
        ranges = []
        for a in range(3):
            count = max(1, math.ceil(abs(hi[a] - lo[a]) / 32))
            ranges.append([(lo[a] + (hi[a] - lo[a]) * i / count,
                            lo[a] + (hi[a] - lo[a]) * (i + 1) / count) for i in range(count)])
        for segment in itertools.product(*ranges):
            start, end = [v[0] for v in segment], [v[1] for v in segment]
            offset = [math.floor(min(start[a], end[a]) / 16) * 16 for a in range(3)]
            for a in range(3):
                if max(start[a], end[a]) - offset[a] > 32:
                    offset[a] += 16
            cube = {'from': [start[a] - offset[a] for a in range(3)],
                    'to': [end[a] - offset[a] for a in range(3)], 'faces': {}}
            # A per-face UV crop follows Java FaceInfo's directions. Interior cuts have no face.
            planes = {'east': (0, True, 2, -1, 1, -1), 'west': (0, False, 2, 1, 1, -1),
                      'north': (2, False, 0, -1, 1, -1), 'south': (2, True, 0, 1, 1, -1),
                      'up': (1, True, 0, 1, 2, 1), 'down': (1, False, 0, 1, 2, -1)}
            for side, face in part['faces'].items():
                axis, upper, u_axis, u_sign, v_axis, v_sign = planes[side]
                if abs((end if upper else start)[axis] - (hi if upper else lo)[axis]) > 1e-6:
                    continue
                if face.get('rotation'):
                    raise ValueError('Split native UV requires explicit handling of rotated face: ' + side)
                uv = list(face['uv'])
                for channel, a, sign in ((0, u_axis, u_sign), (1, v_axis, v_sign)):
                    if hi[a] == lo[a]:
                        continue
                    ratios = [(start[a] - lo[a]) / (hi[a] - lo[a]), (end[a] - lo[a]) / (hi[a] - lo[a])]
                    if sign < 0:
                        ratios = [1 - ratios[1], 1 - ratios[0]]
                    uv[channel] = face['uv'][channel] + (face['uv'][channel + 2] - face['uv'][channel]) * ratios[0]
                    uv[channel + 2] = face['uv'][channel] + (face['uv'][channel + 2] - face['uv'][channel]) * ratios[1]
                cube['faces'][side] = {**face, 'uv': uv}
            if not cube['faces']:
                continue
            if axes:
                cube['rotation'] = {'origin': [element.get('origin', [8, 8, 8])[a] - offset[a] for a in range(3)],
                                    'axis': 'xyz'[axes[0]], 'angle': angles[axes[0]],
                                    'rescale': element.get('rescale', False)}
            for field in ('shade', 'neoforge_data'):
                if field in part:
                    cube[field] = part[field]
            children[str(len(children))] = {'parent': 'minecraft:block/block', 'textures': model['textures'],
                                            'render_type': 'minecraft:cutout', 'elements': [cube],
                                            'transform': {'translation': [v / 16 for v in offset]}}
    if not boxes:
        raise ValueError('Oversize native object has no positive collision volumes')
    return {'parent': 'minecraft:block/block', 'loader': 'neoforge:composite',
            'render_type': 'minecraft:cutout', 'textures': model['textures'], 'children': children,
            'stardewcraft:collision': {'mode': 'custom', 'boxes': boxes}}, files


def export_static(path, project, identifier):
    metadata = metadata_for(path, project)
    try:
        model, files = import_bbmodel(project, identifier, texture_metadata=metadata, allow_inverted_hulls=True)
    except ValueError as error:
        if 'coordinates must be between' not in str(error):
            raise
        model, files = native_composite(project, identifier, metadata)
    boxes, policy = simplified_collision(path, project, model)
    if len(boxes) > 48: raise ValueError('Simplified collision exceeds 48 regions')
    model['stardewcraft:collision'] = {'mode': 'custom', 'boxes': boxes, 'policy': policy}
    # Atlas gutters must never be used for break particles. Prefer the author's material sprite.
    particles = sorted(path.parent.rglob('*particle.png'))
    if particles:
        particle_id = identifier + '_particle'
        files[output_path(particle_id, 'textures', '.png')] = particles[0].read_bytes()
        model['textures']['particle'] = particle_id
    else:
        from PIL import Image
        import io
        atlas = Image.open(io.BytesIO(next(v for p, v in files.items() if str(p).endswith('.png')))).convert('RGBA')
        # Take an opaque texel actually used by a visible face, not a transparent atlas gutter.
        colors = [rgba for rgba in atlas.getdata() if rgba[3] == 255]
        if not colors:
            raise ValueError('No opaque material available for block particles')
        from collections import Counter
        material = Counter(colors).most_common(1)[0][0]
        particle = Image.new('RGBA', (16, 16), material)
        data = io.BytesIO(); particle.save(data, format='PNG')
        particle_id = identifier + '_particle'
        files[output_path(particle_id, 'textures', '.png')] = data.getvalue()
        model['textures']['particle'] = particle_id
    for child in model.get('children', {}).values():
        if isinstance(child, dict):
            child.setdefault('textures', {})['particle'] = particle_id
    return model, files


def state_models(name, models):
    if name in ASSEMBLIES:
        return ASSEMBLIES[name]['property'], models
    if name in FAMILIES:
        prop, sources = FAMILIES[name]
        values = STATE_VALUES.get(prop) or ([str(i) for i in range(len(sources))] if prop == 'variant' else ['false', 'true'])
        return prop, dict(zip(values, ['stardewcraft:block/ginger_island/' + s for s in sources]))
    return None, {}


def assemble_models(exported, export):
    result = {}
    for name, family in ASSEMBLIES.items():
        states = {}
        for state, components in family['states'].items():
            children, boxes = {}, []
            for i, (component, offset) in enumerate(components):
                model = exported[component]
                children[str(i)] = {'parent': 'stardewcraft:block/ginger_island/' + component,
                                   'transform': {'translation': [v / 16 for v in offset]}}
                for b in model['stardewcraft:collision']['boxes']:
                    boxes.append({side: [v + offset[a] for a, v in enumerate(b[side])] for side in ('from', 'to')})
            model_id = 'stardewcraft:block/ginger_island/' + name + '_state_' + state
            model = {'parent': 'minecraft:block/block', 'loader': 'neoforge:composite',
                     'textures': {'particle': exported[components[0][0]]['textures']['particle']},
                     'children': children, 'stardewcraft:collision': {'mode': 'custom', 'boxes': boxes,
                                                                 'policy': 'compound_assembly'}}
            if export: write_json(output_path(model_id, 'models'), model)
            states[state] = model_id
        result[name] = states
    return result


def write_registration(definition, exported_model, export):
    if not export: return
    name = definition['id'].removeprefix('ginger_')
    identifier = definition['model']
    runtime = definition['id']
    empty = 'stardewcraft:block/ginger_island/' + name + '_extension'
    particle = exported_model['textures']['particle']
    write_json(output_path(empty, 'models'), {'textures': {'particle': particle}, 'elements': []})
    variants = {}
    prop = definition.get('state_property')
    choices = definition.get('state_models', {})
    if prop == 'variant':
        # The shared property has six values; surplus values resolve to the base.
        choices = {str(i): choices.get(str(i), identifier) for i in range(6)}
    for i, direction in enumerate(('north', 'east', 'south', 'west')):
        for state, model in (choices.items() if prop else [('', identifier)]):
            suffix = ',' + prop + '=' + state if prop else ''
            variants[f'facing={direction},part=main{suffix}'] = {'model': model, 'y': i * 90}
            variants[f'facing={direction},part=extension{suffix}'] = {'model': empty}
    write_json(ASSETS / 'blockstates' / (runtime + '.json'), {'variants': variants})
    write_json(ASSETS / 'models/item' / (runtime + '.json'), {'parent': identifier})
    write_json(ROOT / 'src/main/resources/data/stardewcraft/loot_table/blocks' / (runtime + '.json'),
               {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'conditions': [
                   {'condition': 'minecraft:block_state_property', 'block': 'stardewcraft:' + runtime,
                    'properties': {'part': 'main'}}, {'condition': 'minecraft:survives_explosion'}],
                   'entries': [{'type': 'minecraft:item', 'name': 'stardewcraft:' + runtime}]}]})


def export_terrain(export):
    """Texture-only authored blocks are assets too; check scenes are never registered."""
    import shutil
    definitions, entries = [], []
    base = ASSETS / 'textures/block/ginger_island'
    terrain = SOURCE / '09_volcano/volcano_terrain'
    locations = {
        'volcano': terrain / 'textures',
        'caldera': SOURCE / '09_volcano/caldera/terrain/textures',
        'dwarf_gate': SOURCE / '09_volcano/mechanisms/dwarf_gate/textures',
        'volcano_bridge': SOURCE / '09_volcano/mechanisms/volcano_bridge/textures',
        'qi_room': SOURCE / '10_qi_room/qi_room_tile/textures',
    }
    if export:
        for folder, source in locations.items():
            for path in source.rglob('*.png*'):
                dest = base / folder / path.relative_to(source)
                dest.parent.mkdir(parents=True, exist_ok=True); shutil.copyfile(path, dest)

    def block(name, family, faces, particle, kind='cube', variants=None):
        runtime = 'ginger_' + name
        identifier = 'stardewcraft:block/ginger_island/' + name
        def cube(textures):
            result = {'parent': 'minecraft:block/block', 'textures': {'particle': particle}, 'elements': [
                {'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': {}}]}
            for side, texture in textures.items():
                result['textures'][side] = 'stardewcraft:block/ginger_island/' + family + '/' + texture
                result['elements'][0]['faces'][side] = {'texture': '#' + side, 'uv': [0, 0, 16, 16], 'cullface': side}
            return result
        if export:
            write_json(output_path(identifier, 'models'), cube(faces))
            states = {'': {'model': identifier}}
            if variants:
                states = {}
                for i in range(6):
                    tex = variants[i] if i < len(variants) else variants[0]
                    model_id = identifier + '_' + str(i)
                    write_json(output_path(model_id, 'models'), cube({**faces, 'up': tex}))
                    states['variant=' + str(i)] = {'model': model_id}
            write_json(ASSETS / 'blockstates' / (runtime + '.json'), {'variants': states})
            write_json(ASSETS / 'models/item' / (runtime + '.json'), {'parent': identifier})
            write_json(ROOT / 'src/main/resources/data/stardewcraft/loot_table/blocks' / (runtime + '.json'),
                       {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'conditions': [{'condition': 'minecraft:survives_explosion'}],
                         'entries': [{'type': 'minecraft:item', 'name': 'stardewcraft:' + runtime}]}]})
        definitions.append({'id': runtime, 'model': identifier, 'kind': kind, 'type': 'natural_rock'})
        entries.append({'source': str(locations[family].relative_to(ROOT)), 'id': runtime,
                        'status': 'exported' if export else 'export_validated', 'model': identifier, 'role': 'texture_only_terrain'})

    sides = ('north', 'east', 'south', 'west', 'up', 'down')
    tex_id = lambda family, name: 'stardewcraft:block/ginger_island/' + family + '/' + name
    block('volcano_floor', 'volcano', {**dict.fromkeys(sides, 'volcano_wall'), 'up': 'volcano_floor'},
          tex_id('volcano', 'volcano_floor_particle'), 'volcano_floor',
          ['volcano_floor', 'volcano_floor_pit_a', 'volcano_floor_pit_b', 'volcano_floor_vent', 'volcano_floor_cracks', 'volcano_floor_lava'])
    block('volcano_wall', 'volcano', dict.fromkeys(sides, 'volcano_wall'), tex_id('volcano', 'volcano_wall_particle'))
    block('volcano_mud', 'volcano', dict.fromkeys(sides, 'volcano_mud'), tex_id('volcano', 'volcano_mud_particle'))
    block('caldera_floor', 'caldera', {**dict.fromkeys(sides, 'caldera_floor_side'), 'up': 'caldera_floor'},
          tex_id('caldera', 'caldera_floor_particle'), 'caldera_floor', ['caldera_floor', 'caldera_floor_crack', 'caldera_floor_ember'])
    block('caldera_wall', 'caldera', dict.fromkeys(sides, 'caldera_wall_0'), tex_id('caldera', 'caldera_wall_particle'))
    for name in ('dwarf_brick', 'dwarf_capstone'):
        faces = dict.fromkeys(sides, name)
        if name == 'dwarf_brick':
            faces.update(east='dwarf_brick_side_b', west='dwarf_brick_side_b')
        else:
            faces = {s: name + '_side_' + s for s in ('north', 'east', 'south', 'west')}
            faces.update(up=name + '_top', down=name + '_top')
        block(name, 'dwarf_gate', faces, tex_id('dwarf_gate', name + '_particle'))
    for name in ('volcano_bridge_deck', 'volcano_bridge_rail'):
        material = name.removeprefix('volcano_bridge_')
        faces = {s: name + '_side_' + s for s in ('north', 'east', 'south', 'west')}
        faces.update(up=name + ('_top' if material == 'rail' else ''), down=name + ('_top' if material == 'rail' else ''))
        block(name, 'volcano_bridge', faces, tex_id('volcano_bridge', name + '_particle'))
    block('qi_room_tile', 'qi_room', dict.fromkeys(sides, 'qi_room_tile'), tex_id('qi_room', 'qi_room_tile_particle'))
    runtime = 'ginger_volcano_cooled_lava'
    identifier = 'stardewcraft:block/ginger_island/volcano_cooled_lava'
    definitions.append({'id': runtime, 'model': identifier, 'kind': 'cooled_lava', 'type': 'natural_rock'})
    entries.append({'source': str(terrain.relative_to(ROOT)), 'id': runtime, 'role': 'watering_can_surface',
                    'status': 'exported' if export else 'export_validated', 'model': identifier})
    if export:
        states = {}
        for mask in range(16):
            model = json.loads((terrain / 'models/cooled_lava' / (str(mask) + '.json')).read_text())
            model['textures'] = {k: v.replace('stardewcraft:block/volcano/', 'stardewcraft:block/ginger_island/volcano/')
                                 for k, v in model['textures'].items()}
            model['stardewcraft:collision'] = {'mode': 'custom', 'boxes': [{'from': [0, 14, 0], 'to': [16, 16, 16]}]}
            name = 'stardewcraft:block/ginger_island/cooled_lava/' + str(mask)
            write_json(output_path(name, 'models'), model); states['neighbors=' + str(mask)] = {'model': name}
        write_json(output_path(identifier, 'models'), {'parent': 'stardewcraft:block/ginger_island/cooled_lava/0'})
        write_json(ASSETS / 'blockstates' / (runtime + '.json'), {'variants': states})
        write_json(ASSETS / 'models/item' / (runtime + '.json'), {'parent': identifier})
        write_json(ROOT / 'src/main/resources/data/stardewcraft/tags/worldgen/biome/volcano_cooling.json',
                   {'replace': False, 'values': ['stardewcraft:volcano']})
        for material in (1, 2):
            for mask in range(1, 256):
                name = f'block/mine_ground/volcano/{material}/{mask}'
                source = terrain / 'textures/overlays' / str(material) / (str(mask) + '.png')
                if not source.exists():
                    source = terrain / 'overlays' / str(material) / (str(mask) + '.png')
                dest = ASSETS / 'textures' / (name + '.png'); dest.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(source, dest)
                write_json(ASSETS / 'models' / (name + '.json'), {'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout',
                    'textures': {'0': 'stardewcraft:' + name, 'particle': tex_id('volcano', 'volcano_floor_particle')},
                    'elements': [{'from': [0, 16.01, 0], 'to': [16, 16.01, 16],
                                  'faces': {'up': {'uv': [0, 0, 16, 16], 'texture': '#0', 'cullface': 'up'}}}]})
    return definitions, entries


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--export', action='store_true')
    args = parser.parse_args()
    entries, definitions, seen, exported = [], [], set(), {}
    state_owners = {source: (owner, prop, i) for owner, (prop, sources) in FAMILIES.items()
                    for i, source in enumerate(sources) if source != owner}
    for path in sorted(SOURCE.rglob('*.bbmodel')):
        rel = str(path.relative_to(ROOT))
        entry = {'source': rel, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}
        reason = disposition(path)
        if reason:
            entries.append({**entry, 'status': 'excluded', 'reason': reason}); continue
        project = json.loads(path.read_text())
        name = path.stem.replace('-', '_')
        runtime = 'ginger_' + name
        if runtime in seen:
            raise ValueError('Duplicate runtime ID: ' + runtime)
        seen.add(runtime)
        entry.update(id=runtime, format=project.get('meta', {}).get('model_format'),
                     clips=[a['name'] for a in project.get('animations', [])])
        if name in REUSES:
            if args.export: remove_reused_registration(name)
            entries.append({**entry, 'status': 'existing_runtime_binding', 'binding': REUSES[name]}); continue
        if project.get('animations') or '/cast/' in rel or name == 'fizz':
            entries.append({**entry, 'status': 'actor_pending_export'}); continue
        identifier = 'stardewcraft:block/ginger_island/' + name
        try:
            model, files = export_static(path, project, identifier)
        except (ValueError, KeyError, TypeError) as error:
            entries.append({**entry, 'status': 'export_pending', 'reason': str(error)}); continue
        exported[name] = model
        if args.export:
            write_json(output_path(identifier, 'models'), model)
            for output, data in files.items():
                output.parent.mkdir(parents=True, exist_ok=True); output.write_bytes(data)
                if str(output).endswith('.png') and Path(str(output) + '.mcmeta') not in files:
                    Path(str(output) + '.mcmeta').unlink(missing_ok=True)
        if name in MODEL_ONLY:
            if args.export: remove_reused_registration(name, keep_model=True)
            entries.append({**entry, 'status': 'model_only', 'model': identifier, 'reason': MODEL_ONLY[name]})
            continue
        if name in state_owners:
            owner, prop, index = state_owners[name]
            state = STATE_VALUES[prop][index] if prop in STATE_VALUES else (str(index) if prop == 'variant' else 'true')
            if args.export: remove_reused_registration(name, keep_model=True)
            entries.append({**entry, 'status': 'state_runtime_binding', 'model': identifier,
                            'binding': {'block': 'stardewcraft:ginger_' + owner, 'state': {prop: state}}})
            continue
        if name in STATE_BINDINGS:
            if args.export: remove_reused_registration(name, keep_model=True)
            entries.append({**entry, 'status': 'state_runtime_binding', 'model': identifier,
                            'binding': STATE_BINDINGS[name], 'collision': {
                                'policy': model['stardewcraft:collision']['policy'],
                                'regions': len(model['stardewcraft:collision']['boxes'])}})
            continue
        if args.export:
            write_json(ASSETS / 'models/item' / (runtime + '.json'), {'parent': identifier})
            empty = identifier + '_extension'
            write_json(output_path(empty, 'models'), {'textures': {'particle': model['textures']['particle']}, 'elements': []})
            variants = {}
            for i, direction in enumerate(('north', 'east', 'south', 'west')):
                if name == 'heavy_tapper':
                    variants['facing=' + direction + ',upper=false'] = {'model': identifier, 'y': (i * 90 + 180) % 360}
                    variants['facing=' + direction + ',upper=true'] = {'model': empty}
                elif name == 'ostrich_incubator_empty':
                    for loaded in ('false', 'true'):
                        actual = identifier if loaded == 'false' else identifier.replace('_empty', '_loaded')
                        variants[f'facing={direction},part=main,loaded={loaded}'] = {'model': actual, 'y': i * 90}
                        variants[f'facing={direction},part=extension,loaded={loaded}'] = {'model': empty}
                elif name in ('stove_fireplace', 'island_torch_unlit'):
                    actual_lit = identifier + '_lit' if name == 'stove_fireplace' else identifier.replace('_unlit', '_lit')
                    for lit in ('false', 'true'):
                        variants[f'facing={direction},part=main,lit={lit}'] = {'model': identifier if lit == 'false' else actual_lit, 'y': i * 90}
                        variants[f'facing={direction},part=extension,lit={lit}'] = {'model': empty}
                elif name == 'volcano_floor_switch':
                    for pressed in ('false', 'true'):
                        actual = identifier if pressed == 'false' else identifier + '_pressed'
                        variants[f'facing={direction},pressed={pressed}'] = {'model': actual, 'y': i * 90}
                else:
                    variants['facing=' + direction + ',part=main'] = {'model': identifier, 'y': i * 90}
                    variants['facing=' + direction + ',part=extension'] = {'model': empty}
            write_json(ASSETS / 'blockstates' / (runtime + '.json'), {'variants': variants})
            conditions = [{'condition': 'minecraft:survives_explosion'}]
            if name != 'volcano_floor_switch':
                conditions.insert(0, {'condition': 'minecraft:block_state_property', 'block': 'stardewcraft:' + runtime,
                                      'properties': {'upper': 'false'} if name == 'heavy_tapper' else {'part': 'main'}})
            write_json(ROOT / 'src/main/resources/data/stardewcraft/loot_table/blocks' / (runtime + '.json'),
                       {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'conditions': conditions,
                           'entries': [{'type': 'minecraft:item', 'name': 'stardewcraft:' + runtime}]}]})
        kind = KINDS.get(name, ('decor', 'building'))[0]
        definitions.append({'id': runtime, 'model': identifier, 'kind': kind, 'type': item_type(name, kind)})
        entries.append({**entry, 'status': 'exported' if args.export else 'export_validated', 'model': identifier,
                        'collision': {'policy': model['stardewcraft:collision']['policy'],
                                      'regions': len(model['stardewcraft:collision']['boxes'])}})
    terrain_definitions, terrain_entries = export_terrain(args.export)
    definitions.extend(terrain_definitions); entries.extend(terrain_entries)
    assembled = assemble_models(exported, args.export)
    for name in ASSEMBLIES:
        if not any(d['id'] == 'ginger_' + name for d in definitions):
            definitions.append({'id': 'ginger_' + name, 'model': next(iter(assembled[name].values())),
                                'kind': 'decor', 'type': item_type(name, 'decor')})
    for definition in definitions:
        name = definition['id'].removeprefix('ginger_')
        definition['type'] = item_type(name, definition['kind'])
        prop, choices = state_models(name, assembled.get(name, {}))
        if prop:
            definition.update(kind='state_decor', state_property=prop, state_models=choices)
            definition['model'] = next(iter(choices.values()))
            source_model = exported[name] if name in exported else exported['bridge_0']
            write_registration(definition, source_model, args.export)
    # Fail closed: every visible identity needs an explicitly reviewed localized name.
    from ginger_island_names import localizations
    localized = localizations(definitions)
    write_json(LEDGER, {'version': 1, 'entries': entries})
    if args.export:
        write_json(ROOT / 'src/main/resources/data/stardewcraft/ginger_island/assets.json',
                   {'version': 1, 'blocks': definitions})
        for language, names in localized.items():
            target = ASSETS / 'lang' / (language + '.json')
            data = json.loads(target.read_text())
            for key in list(data):
                if key.startswith('block.stardewcraft.ginger_') and key not in names:
                    del data[key]
            data.update(names); write_json(target, data)
    from collections import Counter
    print(json.dumps(Counter(e['status'] for e in entries), ensure_ascii=False))
    for entry in entries:
        if entry['status'] == 'export_pending':
            print(entry['source'] + ': ' + entry['reason'])


if __name__ == '__main__':
    main()

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
from ginger_island_catalog import FAMILIES, MODEL_ONLY, ASSEMBLIES, STATE_VALUES, STATE_ROLES, LEGACY_ALIASES, PASSABLE_PLANTS, item_type
from ginger_island_item_icons import write_item_model

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'assets-src/ginger_island'
ASSETS = ROOT / 'src/main/resources/assets/stardewcraft'
LEDGER = ROOT / 'docs/ginger-island-research/integration/source-assets.json'
MUSIC_CRYSTAL = SOURCE / '06_west_quests/crystal_cave/music_crystal.bbmodel'

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


def willy_boat_collision(project, damaged):
    """Coarse structural regions, preserving the source's port entry and cabin.

    Occupied block cells are a placement budget, not a player-sized solid. The
    two static states have different gate geometry; continuous gate/plank
    motion and repair progression are deliberately not implemented here.
    """
    for group in project.get('groups', []) + [n for n in project.get('outliner', []) if isinstance(n, dict)]:
        if any(abs(v) > 1e-6 for v in group.get('rotation', [0, 0, 0])):
            raise ValueError('Willy boat collision requires unrotated native groups')
    elements = ordered_elements(project)
    names = [e['name'] for e in elements]
    if len(names) != len(set(names)):
        raise ValueError('Willy boat collision requires unique structural element names')
    bounds = {}
    for e in elements:
        if e.get('type', 'cube') != 'cube':
            raise ValueError('Willy boat must remain native cuboids/planes')
        part = {'from': e['from'], 'to': e['to']}
        axes = [a for a, v in enumerate(e.get('rotation', [0, 0, 0])) if abs(v) > 1e-6]
        if len(axes) > 1:
            raise ValueError('Willy boat collision requires native single-axis rotations')
        if axes:
            a = axes[0]
            part['rotation'] = {'axis': 'xyz'[a], 'angle': e['rotation'][a],
                                'origin': e.get('origin', [8, 8, 8]), 'rescale': e.get('rescale', False)}
        bounds[e['name']] = part_bounds(part)

    def region(*required, prefixes=()):
        missing = [n for n in required if n not in bounds or bounds[n] is None]
        if missing:
            raise ValueError('Willy boat functional region is missing: ' + ', '.join(missing))
        selected = [bounds[n] for n in required]
        for prefix in prefixes:
            matches = [b for n, b in bounds.items() if n.startswith(prefix) and b is not None]
            if not matches:
                raise ValueError('Willy boat structural prefix is missing: ' + prefix)
            selected.extend(matches)
        return envelope(selected)

    sill = region('boarding_sill')
    plank = region('boarding_plank')
    if sill != box((-33, 8, 35), (-29, 16, 51)) or plank != box((-65, 12, 35), (-33, 16, 51)):
        raise ValueError('Willy boat port boarding geometry changed; recheck entry instead of sealing its envelope')
    if ('repair_plank_cross' in bounds) != damaged:
        raise ValueError('Willy boat source does not match its repaired state')
    gate = region('gate_top', 'gate_bottom', prefixes=('gate_bar_',))
    expected_gate = box((-33, 17, 35), (-31, 33, 51)) if damaged else box((-32.8, 17, 50), (-17, 33, 52))
    if gate != expected_gate:
        raise ValueError('Willy boat gate changed; verify closed/open passage before export')

    # Four supported body/deck bands. The keel's narrow projection below the
    # bow is included vertically without widening the whole ship to its length.
    floor = region('bottom_mid', 'keel', 'boarding_sill', prefixes=('deck_mid_', 'port_lower_mid_',
                   'port_middle_mid_', 'starboard_lower_mid_', 'starboard_middle_mid_',))
    floor['from'][2] = bounds['bottom_mid']['from'][2]
    floor['to'][2] = bounds['bottom_mid']['to'][2]
    floor['to'][1] = bounds['deck_mid_0']['to'][1]
    result = [floor]
    for n in (1, 2, 3):
        b = region('bottom_bow' + str(n), 'deck_bow' + str(n))
        if n < 3: b['from'][1] = min(b['from'][1], bounds['keel']['from'][1])
        result.append(b)
    # Preserve the pier's native floor blocks beneath the extended visual
    # plank. A 0.01-unit top support surface owns only air above foot height
    # 16; the authored 12..16 solid wood remains rendered without reserving
    # the six partially covered ground cells. This is static parked physics,
    # not the future continuously retracting bridge/collision transition.
    plank['from'][1] = plank['to'][1]
    plank['to'][1] += .01
    result.append(plank)

    # Port upper strakes are separate on each side of the 14px clear aperture.
    # Damaged sealing boards share the adjacent side regions, not the deck.
    fore = region('port_upper_mid_0', 'port_gunwale_0', 'boarding_post_-36', 'port_rib_-84', 'port_rib_-63')
    aft = region('port_upper_mid_1', 'port_gunwale_1', 'boarding_post_-18', 'port_rib_8',
                 prefixes=('port_stern_round_',))
    if damaged:
        fore = envelope([fore, region('repair_plank_cross')])
        aft = envelope([aft, region('repair_plank_low')])
    result.extend([fore, aft, region('starboard_upper_mid_0', prefixes=('starboard_gunwale_',
                          'starboard_rib_', 'starboard_stern_round_',))])
    for side in ('port', 'starboard'):
        result.append(region(side + '_bow_gunwale', prefixes=(side + '_lower_bow_shoulder',
                             side + '_middle_bow_shoulder', side + '_upper_bow_shoulder')))
        result.append(region(side + '_bow_rail_tip', prefixes=(side + '_lower_bow_tip',
                             side + '_middle_bow_tip', side + '_upper_bow_tip')))
    stern = region('stern_gunwale', 'stern_corner_cap_port', 'stern_corner_cap_starboard',
                   prefixes=('transom_', 'port_stern_round_', 'starboard_stern_round_'))
    # Rounded rear corners overlap the side regions. They do not fill the
    # central standing lane ahead of the mirrored rail/corner caps at z=7.
    stern['to'][2] = max(bounds[n]['to'][2] for n in
                         ('stern_gunwale', 'stern_corner_cap_port', 'stern_corner_cap_starboard'))
    result.append(stern)

    # Five independent walls and a thin cabin/threshold floor leave the rear
    # X2..14 doorway and the complete interior empty above foot height 18.
    for side in ('port', 'starboard'):
        wall = region('cabin_' + side)
        # Corner names follow the actual wall's outer X, including the widened
        # cabin; do not retain the first white model's narrower wall offsets.
        x = format(wall['from'][0], 'g')
        result.append(region('cabin_' + side, prefixes=('cabin_corner_' + x + '_',)))
    result.extend(region(n) for n in ('cabin_bow_wall', 'cabin_aft_left', 'cabin_aft_right'))
    result.append(region('cabin_floor', 'door_threshold'))
    roof = region('roof_ridge', 'port_eave', 'starboard_eave', 'door_header',
                  prefixes=('port_roof', 'starboard_roof', 'roof_', 'aft_gable_', 'bow_gable_',))
    result.extend([roof, region('chimney', 'chimney_lip'),
                   region('mast', 'lantern_bottom', 'lantern_cap', 'lantern_glass',
                          prefixes=('mast_collar_', 'lantern_frame_',)),
                   region('helm_pedestal', 'helm_console', 'wheel_axle', 'helm_wheel'), gate])
    # The port winch and hanging anchor occupy a short shared longitudinal
    # strip; decorative rope coils and hanging bulb wires have no solid volume.
    result.append(region('winch_base', 'winch_axle', prefixes=('anchor_',)) if damaged else
                  region('winch_base', 'winch_axle'))
    if len(result) > 25 or any(any(b['from'][a] >= b['to'][a] for a in range(3)) for b in result):
        raise ValueError('Willy boat needs at most 25 positive functional collision regions')
    return result


def simplified_collision(path, project, model):
    """User-approved collision policy: one AABB, or a few functional AABB regions.

    Mesh detail, leaf tips, rope knots and texture planes do not create physics parts.
    Source geometry and the existing cabin blueprint determine the coarse regions.
    """
    name = path.stem
    if path == MUSIC_CRYSTAL:
        points = [quad['vertices'][start:start + 3] for quad in model['quads'] for start in (0, 5, 10, 15)]
        return [box([min(p[a] for p in points) for a in range(3)],
                    [max(p[a] for p in points) for a in range(3)])], 'aabb'
    if name in ('willy_boat_damaged', 'willy_boat_repaired'):
        return willy_boat_collision(project, name.endswith('_damaged')), 'compound_willy_boat_entry'
    if name in ('south_palm_ground', 'south_palm_cliff', 'coconut_palm', 'resort_young_palm'):
        # The saved/reloaded root and functional regions come from the author's
        # technical record, not the full visual envelope or a foliage voxel map.
        data = json.loads((path.parent / 'acceptance-data.json').read_text())
        records = [p for p in data['technical_checks'] if p['name'] == name]
        if len(records) != 1:
            raise ValueError('Palm needs exactly one saved/reloaded technical source record: ' + name)
        technical = records[0]
        if data['registration_owner'] != 'ginger_south_palm' or technical['root_anchor'] != [8, 0, 8] \
                or technical['bbmodel_sha256'] != hashlib.sha256(path.read_bytes()).hexdigest() \
                or technical['png_sha256'] != hashlib.sha256(path.with_suffix('.png').read_bytes()).hexdigest():
            raise ValueError('Palm source changed since the recorded root/collision check')
        if name in ('coconut_palm', 'resort_young_palm'):
            raw = data['coconut_trunk_boxes' if name == 'coconut_palm' else 'resort_young_trunk_boxes']
            expected_count = len(raw)
            if (name == 'resort_young_palm' and expected_count != 3) or not 1 <= expected_count <= 6:
                raise ValueError('Palm needs its reviewed root/trunk regions, without a leaf/fruit envelope')
        else:
            raw = data['ground_boxes' if name.endswith('_ground') else 'cliff_boxes']
            expected_count = 5 if name.endswith('_ground') else 7
        if len(raw) != expected_count or any(
                len(b) != 6 or not all(math.isfinite(v) for v in b) or b[1] < 0 or
                any(b[a] >= b[a + 3] for a in range(3)) for b in raw):
            raise ValueError('Palm functional collision needs a reviewed positive-region profile')
        # The final region is a coarse crown envelope. Leaves remain fully
        # rendered, but must not reserve its broad, mostly empty air volume.
        # MapDecorStaticBlock derives MAIN/EXTENSION cells from collision:
        # trunk-only profiles own 5/10 cells (13 across the two variants).
        if name not in ('coconut_palm', 'resort_young_palm'):
            if raw[-1][1] < 46.5:
                raise ValueError('Palm crown must remain separate from the root/trunk regions')
            raw = raw[:-1]
        if min(b[1] for b in raw) != 0 or any(b[3] - b[0] > 16 or b[5] - b[2] > 16 for b in raw):
            raise ValueError('Palm collision must start at its root and contain only narrow trunk sections')
        return [box(b[:3], b[3:]) for b in raw], 'compound_palm_trunk'
    if name in ('resort_umbrella_coral', 'resort_umbrella_mint'):
        # Ground is one block below MAIN. Reserve the stand, narrow shaft and
        # canopy separately; the empty air under the canopy stays usable.
        return [box((3, -16, 3), (13, -13, 13)),
                box((7, -13, 7), (9, 30, 9)),
                # Drooping trim is visual. The solid upper canopy clears both
                # standing players and ChairBlock's initial seat teleport.
                box((-14.18, 22, -14.18), (30.18, 32, 30.18))], 'compound_umbrella'
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
    if name in ('island_hint_white_flowers', 'island_hint_yellow_flowers'):
        # A rotated flower tip extends less than one pixel beneath its root.
        # Keep that visual overlap with soil, without making the plant own and
        # replace the solid supporting block. One coarse AABB is sufficient.
        region = envelope(bounds)
        if not -1 < region['from'][1] <= 0:
            raise ValueError('Flower root moved; recheck its ground anchor')
        region['from'][1] = 0
        return [region], 'aabb'
    if name == 'resort_beach_chair':
        # Decorative half-pixel crossbar tips do not own the two adjacent
        # columns. Keep the authored height and real two-cell recliner length.
        region = envelope(bounds)
        region['from'][0] = max(0, region['from'][0])
        region['to'][0] = min(16, region['to'][0])
        return [region], 'aabb'
    if name == 'resort_changing_entry':
        # The +/-1px carved trim visually meets neighbouring native wood walls;
        # it must not claim their block cells. Keep the 14px door passage and
        # thin sill while reserving only the entrance's own two vertical cells.
        return [box((0, 0, 15), (1, 32, 16)), box((15, 0, 15), (16, 32, 16)),
                box((1, 30, 15), (15, 32, 16)), box((1, 0, 0), (15, 1, 16)),
                box((0, 1, 0), (1, 30, 15)), box((15, 1, 0), (16, 30, 15))], 'compound_door_frame'
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
    if rel.startswith('01_willy_boat/work/'):
        return 'Willy boat authoring stages; only formal two-state sources at 01_willy_boat root export'
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
        model, files = import_bbmodel(project, identifier, texture_metadata=metadata, allow_inverted_hulls=True,
                                     allow_mesh=path == MUSIC_CRYSTAL)
    except ValueError as error:
        if 'coordinates must be between' not in str(error):
            raise
        model, files = native_composite(project, identifier, metadata)
    if path == MUSIC_CRYSTAL:
        # The authored mesh is centered on zero; an MC block anchor is at (8,8).
        # Keep the shape/UVs intact and reserve only the one-cell crystal foot,
        # never the original game's independent 52x28 glow sprite.
        for quad in model['quads']:
            for start in (0, 5, 10, 15):
                quad['vertices'][start] += 8
                quad['vertices'][start + 2] += 8
            quad['tintindex'] = 0
    boxes, policy = simplified_collision(path, project, model)
    if len(boxes) > 48: raise ValueError('Simplified collision exceeds 48 regions')
    model['stardewcraft:collision'] = {'mode': 'custom', 'boxes': boxes, 'policy': policy}
    # Atlas gutters must never be used for break particles. Prefer the author's material sprite.
    if path.stem in ('south_palm_ground', 'south_palm_cliff'):
        particles = [path.parent / 'south_palm_particle.png']
    elif path.stem == 'coconut_palm':
        particle = path.parent / 'coconut_palm_particle.png'
        particles = [particle if particle.exists() else path.parent / 'south_palm_particle.png']
    elif path.stem == 'resort_young_palm':
        particles = [path.parent / 'resort_young_palm_particle.png']
    elif path.stem in ('willy_boat_damaged', 'willy_boat_repaired'):
        particle = path.parent / 'willy_boat_particle.png'
        if not particle.is_file():
            raise ValueError('Willy boat requires its dedicated opaque material particle sprite')
        from PIL import Image
        with Image.open(particle) as sprite:
            if sprite.size != (16, 16) or any(a != 255 for a in sprite.convert('RGBA').getchannel('A').getdata()):
                raise ValueError('Willy boat particle must be a fully opaque 16x16 material sprite')
        particles = [particle]
    else:
        particles = sorted(path.parent.rglob('*particle.png'))
    if particles:
        particle_id = ('stardewcraft:block/ginger_island/willy_boat_particle'
                       if path.stem in ('willy_boat_damaged', 'willy_boat_repaired') else identifier + '_particle')
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


def assembly_components(family, state):
    if 'placement_plan' not in family:
        return [(component, offset, 0) for component, offset in family['states'][state]]
    plan = json.loads((SOURCE / family['placement_plan']).read_text())['roof_plans'][family['states'][state]]
    placements = plan['placements']
    slots = {tuple(p['block']) for p in placements}
    if len(slots) != len(placements) or len(slots) != plan['unique_block_slots']:
        raise ValueError('Roof assembly has duplicate or missing source slots')
    return [(p['model'], [v * 16 for v in p['block']], p['y_rotation']) for p in placements]


def assembly_transform(offset, degrees):
    """Exact quarter-turn matrix about the source block center, then translation.

    NeoForge accepts a 3x4 matrix in block units. An explicit matrix avoids its
    legacy default transform origin; collision uses the very same matrix.
    """
    if degrees not in (0, 90, 180, 270):
        raise ValueError('Assembly rotation must be an exact quarter turn')
    c, s = ((1, 0), (0, 1), (-1, 0), (0, -1))[degrees // 90]
    return [[c, 0, -s, offset[0] / 16 + (1 - c + s) / 2],
            [0, 1, 0, offset[1] / 16],
            [s, 0, c, offset[2] / 16 + (1 - c - s) / 2]]


def assemble_models(exported, export, names=None):
    result = {}
    for name in ASSEMBLIES if names is None else names:
        family = ASSEMBLIES[name]
        states = {}
        for state in family['states']:
            components = assembly_components(family, state)
            children, boxes = {}, []
            for i, (component, offset, degrees) in enumerate(components):
                model = exported[component]
                matrix = assembly_transform(offset, degrees)
                children[str(i)] = {'parent': 'stardewcraft:block/ginger_island/' + component,
                                   'transform': {'matrix': matrix} if 'placement_plan' in family else
                                                {'translation': [v / 16 for v in offset]}}
                if 'placement_plan' in family:
                    children[str(i)]['textures'] = {'particle': model['textures']['particle']}
                for b in model['stardewcraft:collision']['boxes']:
                    points = [[sum(matrix[a][j] * p[j] for j in range(3)) + matrix[a][3] * 16
                               for a in range(3)] for p in itertools.product(*zip(b['from'], b['to']))]
                    boxes.append({'from': [min(p[a] for p in points) for a in range(3)],
                                  'to': [max(p[a] for p in points) for a in range(3)]})
            model_id = 'stardewcraft:block/ginger_island/' + name + '_state_' + state
            model = {'parent': 'minecraft:block/block', 'loader': 'neoforge:composite',
                     'textures': {'particle': exported[components[0][0]]['textures']['particle']},
                     'children': children, 'stardewcraft:collision': {'mode': 'custom', 'boxes': boxes,
                                                                 'policy': 'compound_assembly'}}
            if name == 'resort_roof':
                add_resort_roof_timber(model, family, state)
            elif name == 'south_palm':
                model['stardewcraft:collision']['policy'] = 'compound_palm_trunk'
            elif name == 'willy_boat':
                model['stardewcraft:collision']['policy'] = 'compound_willy_boat_entry'
            if not boxes or len(boxes) > 48:
                raise ValueError('Assembly collision exceeds the functional AABB budget: ' + name)
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
    write_item_model(runtime, identifier, definition)
    loot_item = {'type': 'minecraft:item', 'name': 'stardewcraft:' + runtime}
    if definition.get('catalog_owner'):
        loot_item['name'] = 'stardewcraft:' + definition['catalog_owner']
        owner_name = definition['catalog_owner'].removeprefix('ginger_')
        if STATE_ROLES[owner_name] == 'appearance':
            loot_item['functions'] = [{'function': 'minecraft:set_components',
                                      'components': {'minecraft:block_state': definition['canonical_state']}}]
    if prop == 'variant' and name in ('resort_roof', 'resort_umbrella_coral', 'south_palm'):
        # A shared owner must not turn a 7x4 roof into 6x4, or mint into coral,
        # when broken and placed again. 1.21 stores this in BLOCK_STATE data.
        loot_item['functions'] = [{'function': 'minecraft:copy_state', 'block': 'stardewcraft:' + runtime,
                                   'properties': ['variant']}]
    write_json(ROOT / 'src/main/resources/data/stardewcraft/loot_table/blocks' / (runtime + '.json'),
               {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'conditions': [
                   {'condition': 'minecraft:block_state_property', 'block': 'stardewcraft:' + runtime,
                    'properties': {'part': 'main'}}, {'condition': 'minecraft:survives_explosion'}],
                   'entries': [loot_item]}]})


def alias_definition(name, model):
    owner, state = LEGACY_ALIASES[name]
    return {'id': 'ginger_' + name, 'model': model, 'kind': 'decor', 'type': item_type(name, 'decor'),
            'catalog_owner': 'ginger_' + owner, 'canonical_state': state}


def write_alias_visibility(definitions):
    """JEI hides the same metadata-backed aliases as the catalog; old stacks still place."""
    by_id = {d['id']: d for d in definitions}
    aliases = []
    for definition in definitions:
        owner = definition.get('catalog_owner')
        if owner is None:
            assert 'canonical_state' not in definition, definition['id']
            continue
        canonical = by_id[owner]
        assert owner != definition['id'] and 'catalog_owner' not in canonical
        assert canonical['kind'] == 'state_decor' and definition['kind'] == 'decor'
        assert definition['canonical_state'].keys() == {canonical['state_property']}
        value = definition['canonical_state'][canonical['state_property']]
        assert value in canonical['state_models']
        aliases.append('stardewcraft:' + definition['id'])
    write_json(ROOT / 'src/main/resources/data/stardewcraft/tags/item/ginger_island_legacy_aliases.json',
               {'replace': False, 'values': sorted(aliases)})
    hidden = ROOT / 'src/main/resources/data/stardewcraft/tags/item/hidden.json'
    data = json.loads(hidden.read_text())
    reference = '#stardewcraft:ginger_island_legacy_aliases'
    if reference not in data['values']:
        data['values'].append(reference)
        write_json(hidden, data)


def export_catalog_aliases(export):
    """Merge only the three reviewed catalog families; never rewrite source art or saves."""
    target = ROOT / 'src/main/resources/data/stardewcraft/ginger_island/assets.json'
    manifest = json.loads(target.read_text())
    previous = {d['id']: d for d in manifest['blocks']}
    source_names = set(FAMILIES['palm_wall_ornament_left'][1] + FAMILIES['island_starfish_gold'][1]
                       + ['resort_ruined_column', 'resort_leaf_wrapped_column'])
    exported = {name: json.loads(output_path('stardewcraft:block/ginger_island/' + name, 'models').read_text())
                for name in source_names}
    assembled = assemble_models(exported, export, names=('resort_ruined_column',))
    replacements = {}
    for name in ('palm_wall_ornament_left', 'island_starfish_gold', 'resort_ruined_column'):
        definition = dict(previous['ginger_' + name])
        prop, choices = state_models(name, assembled.get(name, {}))
        definition.update(kind='state_decor', state_property=prop, state_models=choices,
                          state_role=STATE_ROLES[name], model=next(iter(choices.values())))
        replacements[definition['id']] = definition
        write_registration(definition, exported[name], export)
    for name in LEGACY_ALIASES:
        definition = alias_definition(name, previous['ginger_' + name]['model'])
        replacements[definition['id']] = definition
        write_registration(definition, exported[name], export)
    definitions = [replacements.get(d['id'], d) for d in manifest['blocks']]
    assert {d['id'] for d in definitions} == set(previous), 'A catalog merge must retain all save IDs'
    if export:
        write_json(target, {**manifest, 'blocks': definitions})
        write_alias_visibility(definitions)
        # Refresh source identities in place, including the two formal boat files
        # omitted by the previous targeted boat export. Preserve all other rows.
        ledger = json.loads(LEDGER.read_text())
        rows = {e['source']: e for e in ledger['entries'] if e['source'].endswith('.bbmodel')}
        for path in SOURCE.rglob('*.bbmodel'):
            name = path.stem
            if name not in source_names and name not in ('willy_boat_damaged', 'willy_boat_repaired'):
                continue
            relative = str(path.relative_to(ROOT))
            project = json.loads(path.read_text())
            row = {**rows.get(relative, {}), 'source': relative,
                   'sha256': hashlib.sha256(path.read_bytes()).hexdigest(), 'id': 'ginger_' + name,
                   'format': project['meta']['model_format'],
                   'clips': [a['name'] for a in project.get('animations', [])],
                   'model': 'stardewcraft:block/ginger_island/' + name}
            if name in LEGACY_ALIASES:
                owner, state = LEGACY_ALIASES[name]
                row.update(status='state_runtime_binding', legacy_registry_id='stardewcraft:ginger_' + name,
                           binding={'block': 'stardewcraft:ginger_' + owner, 'state': state})
            elif name.startswith('willy_boat_'):
                row.update(status='model_only', reason=MODEL_ONLY[name],
                           binding={'block': 'stardewcraft:ginger_willy_boat',
                                    'state': {'repaired': str(name.endswith('_repaired')).lower()}})
            rows[relative] = row
        entries = [rows.pop(e['source']) if e['source'].endswith('.bbmodel') else e for e in ledger['entries']]
        entries.extend(rows.values())
        write_json(LEDGER, {**ledger, 'entries': entries})
        from ginger_island_names import localizations
        for language, names in localizations(list(replacements.values())).items():
            path = ASSETS / 'lang' / (language + '.json')
            data = json.loads(path.read_text())
            for owner in replacements:
                key = 'block.stardewcraft.' + owner
                data[key] = names[key]
            write_json(path, data)
    print(json.dumps({'catalog_alias_merge_exported': export, 'retained_registered_ids': len(definitions),
                      'visible_owners': sum('catalog_owner' not in d for d in definitions),
                      'aliases': LEGACY_ALIASES}, ensure_ascii=False))


def export_music_crystal(export):
    """The user's precise Mesh exception; no other authoring assets are rewritten."""
    path = MUSIC_CRYSTAL
    project = json.loads(path.read_text())
    identifier = 'stardewcraft:block/ginger_island/music_crystal'
    model, files = export_static(path, project, identifier)
    definition = {'id': 'ginger_music_crystal', 'model': identifier,
                  'kind': 'decor', 'type': item_type('music_crystal', 'decor')}
    manifest_path = ROOT / 'src/main/resources/data/stardewcraft/ginger_island/assets.json'
    manifest = json.loads(manifest_path.read_text())
    existing = [d for d in manifest['blocks'] if d['id'] == definition['id']]
    if len(existing) > 1: raise ValueError('Duplicate Music Crystal owner')
    definitions = [definition if d['id'] == definition['id'] else d for d in manifest['blocks']]
    if not existing: definitions.append(definition)
    from ginger_island_names import localizations
    localized = localizations([definition])
    if export:
        write_json(output_path(identifier, 'models'), model)
        for output, data in files.items():
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_bytes(data)
        write_registration(definition, model, True)
        write_json(manifest_path, {**manifest, 'blocks': definitions})
        ledger = json.loads(LEDGER.read_text())
        relative = str(path.relative_to(ROOT))
        previous = next((entry for entry in ledger['entries'] if entry['source'] == relative), {})
        entry = {**previous, 'source': relative, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
                 'id': definition['id'], 'format': project['meta']['model_format'], 'clips': [],
                 'status': 'exported', 'model': identifier, 'geometry_permission': 'user_authorized_music_crystal_mesh'}
        entry.pop('reason', None)
        entries = [entry if e['source'] == relative else e for e in ledger['entries']]
        if not previous: entries.append(entry)
        write_json(LEDGER, {**ledger, 'entries': entries})
        key = 'block.stardewcraft.ginger_music_crystal'
        for language, names in localized.items():
            target = ASSETS / 'lang' / (language + '.json')
            data = json.loads(target.read_text())
            if data.get(key) != names[key]:
                data[key] = names[key]
                write_json(target, data)
    print(json.dumps({'owner': definition['id'], 'exported': export, 'quads': len(model['quads']),
                      'collision': model['stardewcraft:collision'], 'tintindex': 0}, ensure_ascii=False))


def export_willy_boat(export):
    """Target only the saved boat sources and owner; retain every other asset."""
    exported, outputs, sources = {}, {}, {}
    for name in ('willy_boat_damaged', 'willy_boat_repaired'):
        path = SOURCE / '01_willy_boat' / (name + '.bbmodel')
        project = json.loads(path.read_text())
        identifier = 'stardewcraft:block/ginger_island/' + name
        model, files = export_static(path, project, identifier)
        exported[name] = model
        for output, data in files.items():
            if output in outputs and outputs[output] != data:
                raise ValueError('Boat states disagree about their shared particle resource')
            outputs[output] = data
        sources[name] = {'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
                         'elements': len(ordered_elements(project)), 'children': len(model['children']),
                         'collision_regions': len(model['stardewcraft:collision']['boxes'])}
    choices = assemble_models(exported, False, names=('willy_boat',))['willy_boat']
    definition = {'id': 'ginger_willy_boat', 'model': choices['false'], 'kind': 'state_decor',
                  'type': item_type('willy_boat', 'state_decor'),
                  'state_property': 'repaired', 'state_models': choices, 'state_role': STATE_ROLES['willy_boat']}
    manifest_path = ROOT / 'src/main/resources/data/stardewcraft/ginger_island/assets.json'
    manifest = json.loads(manifest_path.read_text())
    previous = [d for d in manifest['blocks'] if d['id'] == definition['id']]
    if len(previous) > 1:
        raise ValueError('Duplicate Willy boat runtime owner')
    blocks = [definition if d['id'] == definition['id'] else d for d in manifest['blocks']]
    if not previous: blocks.append(definition)
    from ginger_island_names import localizations
    localized = localizations([definition])
    key = 'block.stardewcraft.ginger_willy_boat'
    if export:
        for name, model in exported.items():
            write_json(output_path('stardewcraft:block/ginger_island/' + name, 'models'), model)
        for output, data in outputs.items():
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_bytes(data)
            if str(output).endswith('.png') and Path(str(output) + '.mcmeta') not in outputs:
                Path(str(output) + '.mcmeta').unlink(missing_ok=True)
        assemble_models(exported, True, names=('willy_boat',))
        write_registration(definition, exported['willy_boat_damaged'], True)
        write_json(manifest_path, {**manifest, 'blocks': blocks})
        for language, names in localized.items():
            target = ASSETS / 'lang' / (language + '.json')
            data = json.loads(target.read_text())
            if data.get(key) != names[key]:
                data[key] = names[key]
                write_json(target, data)
    print(json.dumps({'owner': definition['id'], 'exported': export, 'sources': sources,
                      'states': choices, 'particle': exported['willy_boat_repaired']['textures']['particle']},
                      ensure_ascii=False))


def export_arrival_totem(export):
    """Export the arrival landmark without rewriting other island identities."""
    path = SOURCE / '02_arrival/arrival_totem/island_arrival_totem.bbmodel'
    project = json.loads(path.read_text())
    identifier = 'stardewcraft:block/ginger_island/island_arrival_totem'
    model, files = export_static(path, project, identifier)
    definition = {'id': 'ginger_island_arrival_totem', 'model': identifier,
                  'kind': 'decor', 'type': item_type('island_arrival_totem', 'decor')}
    target = ROOT / 'src/main/resources/data/stardewcraft/ginger_island/assets.json'
    manifest = json.loads(target.read_text())
    previous = [d for d in manifest['blocks'] if d['id'] == definition['id']]
    if len(previous) > 1:
        raise ValueError('Duplicate Island arrival totem owner')
    blocks = [definition if d['id'] == definition['id'] else d for d in manifest['blocks']]
    if not previous:
        blocks.append(definition)
    from ginger_island_names import localizations
    names = localizations([definition])
    if export:
        write_json(output_path(identifier, 'models'), model)
        for output, data in files.items():
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_bytes(data)
        write_registration(definition, model, True)
        write_json(target, {**manifest, 'blocks': blocks})
        # Preserve existing formatting and all unrelated translation keys.
        key = 'block.stardewcraft.' + definition['id']
        for language, localized in names.items():
            lang = ASSETS / 'lang' / (language + '.json')
            data = json.loads(lang.read_text())
            if data.get(key) != localized[key]:
                data[key] = localized[key]
                write_json(lang, data)
        ledger = json.loads(LEDGER.read_text())
        entry = {'source': str(path.relative_to(ROOT)),
                 'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
                 'id': definition['id'], 'format': 'java_block', 'status': 'exported',
                 'model': identifier, 'kind': 'decor', 'type': 'building',
                 'behavior': 'arrival landmark; destination belongs to the farm instance; no click return action'}
        entries = [e for e in ledger['entries'] if e['source'] != entry['source']]
        write_json(LEDGER, {**ledger, 'entries': entries + [entry]})
    print(json.dumps({'owner': definition['id'], 'exported': export,
                      'collision': model['stardewcraft:collision'],
                      'particle': model['textures']['particle']}, ensure_ascii=False))


def native_plank_children(children, walls, plank, prefix):
    # Tile existing 16px material per world block. Cropped border tiles keep
    # native density; no stretched UVs or newly painted textures are generated.
    sides = {'east': (2, -1, 1, -1), 'west': (2, 1, 1, -1),
             'north': (0, -1, 1, -1), 'south': (0, 1, 1, -1),
             'up': (0, 1, 2, 1), 'down': (0, 1, 2, -1)}
    for wall in walls:
        ranges = []
        for a in range(3):
            lo, hi = wall['from'][a], wall['to'][a]
            cuts = [lo] + list(range((math.floor(lo / 16) + 1) * 16, hi, 16)) + [hi]
            ranges.append(list(zip(cuts, cuts[1:])))
        for segments in itertools.product(*ranges):
            lo, hi = [s[0] for s in segments], [s[1] for s in segments]
            offset = [math.floor(v / 16) * 16 for v in lo]
            local_lo, local_hi = [lo[a] - offset[a] for a in range(3)], [hi[a] - offset[a] for a in range(3)]
            faces = {}
            for side, (u, us, v, vs) in sides.items():
                # Only outside faces of a continuous wall; no interior cut quads.
                axis = {'east': 0, 'west': 0, 'up': 1, 'down': 1, 'north': 2, 'south': 2}[side]
                high = side in ('east', 'up', 'south')
                if (hi if high else lo)[axis] != wall['to' if high else 'from'][axis]:
                    continue
                uv = [local_lo[u] if us > 0 else 16 - local_hi[u],
                      local_lo[v] if vs > 0 else 16 - local_hi[v],
                      local_hi[u] if us > 0 else 16 - local_lo[u],
                      local_hi[v] if vs > 0 else 16 - local_lo[v]]
                faces[side] = {'texture': '#plank', 'uv': uv}
            if faces:
                children[prefix + str(len(children))] = {
                    'parent': 'minecraft:block/block', 'textures': {'plank': plank, 'particle': plank},
                    'elements': [{'from': local_lo, 'to': local_hi, 'faces': faces}],
                    'transform': {'translation': [v / 16 for v in offset]}}


def add_resort_roof_timber(model, family, state):
    """Connect the source roof to y=-16 walls, without another block owner.

    The approved tiles remain unchanged. Square timber strips follow their
    original 22.5-degree underside; the step fits inside the 2px tile thickness.
    Physics uses a few coarse front/back regions, not the individual strips.
    """
    plan_file = SOURCE / family['placement_plan']
    plan = json.loads(plan_file.read_text())
    roof = plan['roof_plans'][family['states'][state]]
    width = (max(p['block'][0] for p in roof['placements']) + 1) * 16
    depth = (max(p['block'][2] for p in roof['placements']) + 1) * 16
    if width not in (96, 112) or depth != 64 or plan['pitch_degrees'] != 22.5:
        raise ValueError('Review timber joints after changing the approved resort roof profile')
    slope = plan['rise_per_block'] / 16
    plank = 'stardewcraft:block/wood/oak_planks'
    if not (ASSETS / 'textures/block/wood/oak_planks.png').exists():
        raise ValueError('Resort roof requires the native oak plank material')
    profile = lambda x: slope * min(x, width - x)
    def top(lo, hi):
        points = [profile(lo), profile(hi)]
        if lo <= width / 2 <= hi:
            points.append(profile(width / 2))
        return math.ceil(max(points))
    beams = [box((0, -16, 0), (width, -3, 3)),
             box((0, -16, depth - 3), (width, -3, depth))]
    visible = beams[:]
    collision = beams[:]
    for z in (0, depth - 3):
        # A 2px square step rises by <2px, inside the unchanged opaque tiles.
        for x in range(0, width, 2):
            visible.append(box((x, -3, z), (x + 2, top(x, x + 2), z + 3)))
        for x in range(0, width, 16):
            collision.append(box((x, -3, z), (x + 16, top(x, x + 16), z + 3)))
    native_plank_children(model['children'], visible, plank, 'timber_')
    model['stardewcraft:collision']['boxes'].extend(collision)
    # Tiny decorative eaves keep their approved visual overhang, but never
    # reserve the next building's cell. Only the nominal roof body owns space.
    for region in model['stardewcraft:collision']['boxes']:
        region['from'][0] = max(0, region['from'][0])
        region['to'][0] = min(width, region['to'][0])
        region['from'][2] = max(0, region['from'][2])
        region['to'][2] = min(depth, region['to'][2])
        if any(a >= b for a, b in zip(region['from'], region['to'])):
            raise ValueError('Roof collision is outside its nominal building footprint')
    model['stardewcraft:collision']['policy'] = 'compound_roof_with_gables'
    model['stardewcraft:timber_profile'] = {'source_plan': str(plan_file.relative_to(ROOT)),
        'width_px': width, 'depth_px': depth, 'underside_slope': slope,
        'beam_min_y': -16, 'beam_max_y': -3, 'gable_step_px': 2, 'material': plank,
        'collision_xz_pixels': [0, 0, width, depth]}


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
        definition = {'id': runtime, 'model': identifier, 'kind': kind, 'type': 'natural_rock'}
        if variants: definition['variant_count'] = len(variants)
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
            write_item_model(runtime, identifier, definition)
            write_json(ROOT / 'src/main/resources/data/stardewcraft/loot_table/blocks' / (runtime + '.json'),
                       {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'conditions': [{'condition': 'minecraft:survives_explosion'}],
                         'entries': [{'type': 'minecraft:item', 'name': 'stardewcraft:' + runtime}]}]})
        definitions.append(definition)
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
        write_item_model(runtime, identifier)
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
    parser.add_argument('--only-willy-boat', action='store_true',
                        help='Export only the two formal boat states and their single owner; no other assets or ledger writes')
    parser.add_argument('--only-arrival-totem', action='store_true',
                        help='Export only the authored island arrival landmark and its owner')
    parser.add_argument('--only-catalog-aliases', action='store_true',
                        help='Merge only the three reviewed families, retain legacy IDs, refresh their source ledger')
    parser.add_argument('--only-music-crystal', action='store_true',
                        help='Export only the user-authorized music crystal Mesh and its one gray building item')
    args = parser.parse_args()
    if sum((args.only_willy_boat, args.only_arrival_totem, args.only_catalog_aliases, args.only_music_crystal)) > 1:
        parser.error('Choose one targeted export')
    if args.only_willy_boat:
        export_willy_boat(args.export)
        return
    if args.only_arrival_totem:
        export_arrival_totem(args.export)
        return
    if args.only_catalog_aliases:
        export_catalog_aliases(args.export)
        return
    if args.only_music_crystal:
        export_music_crystal(args.export)
        return
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
        if path == MUSIC_CRYSTAL:
            entry['geometry_permission'] = 'user_authorized_music_crystal_mesh'
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
        if name in LEGACY_ALIASES:
            definition = alias_definition(name, identifier)
            definitions.append(definition)
            write_registration(definition, model, args.export)
            owner, state = LEGACY_ALIASES[name]
            entries.append({**entry, 'status': 'state_runtime_binding', 'model': identifier,
                            'legacy_registry_id': 'stardewcraft:' + runtime,
                            'binding': {'block': 'stardewcraft:ginger_' + owner, 'state': state}})
            continue
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
            write_item_model(runtime, identifier)
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
    # Changing rooms are built with the approved entry component and native
    # wood blocks. Never restore the withdrawn whole-room assembly owner.
    if args.export: remove_reused_registration('resort_changing_booths')
    for name in ASSEMBLIES:
        if not any(d['id'] == 'ginger_' + name for d in definitions):
            definitions.append({'id': 'ginger_' + name, 'model': next(iter(assembled[name].values())),
                                'kind': 'decor', 'type': item_type(name, 'decor')})
    for definition in definitions:
        name = definition['id'].removeprefix('ginger_')
        definition['type'] = item_type(name, definition['kind'])
        if name in PASSABLE_PLANTS:
            definition['passable'] = True
        prop, choices = state_models(name, assembled.get(name, {}))
        if prop:
            definition.update(kind='state_decor', state_property=prop, state_models=choices,
                              state_role=STATE_ROLES[name])
            definition['model'] = next(iter(choices.values()))
            source_model = exported[name] if name in exported else exported[
                assembly_components(ASSEMBLIES[name], next(iter(ASSEMBLIES[name]['states'])))[0][0]]
            write_registration(definition, source_model, args.export)
    # Fail closed: every visible identity needs an explicitly reviewed localized name.
    from ginger_island_names import localizations
    localized = localizations(definitions)
    write_json(LEDGER, {'version': 1, 'entries': entries})
    if args.export:
        write_json(ROOT / 'src/main/resources/data/stardewcraft/ginger_island/assets.json',
                   {'version': 1, 'blocks': definitions})
        write_alias_visibility(definitions)
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
    if '--export' in sys.argv:
        # Preserve the reviewed floor/wall/water datum on every full or targeted
        # export; source art remains unchanged and transforms are idempotent.
        from ginger_island_placement import apply
        apply()

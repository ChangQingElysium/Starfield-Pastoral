"""Reviewed placement transforms; preserve authoring geometry, UVs and source PNGs.

World render and simplified collision receive the same rigid transform. Roof
modules, suspended fixtures and boats keep their documented construction datum.
"""
import copy
import itertools
import json
import math
from pathlib import Path
from audit_ginger_island_item_bounds import ItemBoundsReader, _xyz

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / 'src/main/resources'
CATALOG = RESOURCES / 'data/stardewcraft/ginger_island/assets.json'
WALL = {
    'dig_ammonite_relief', 'dig_fossil_fragments', 'field_office_fossil_backing',
    'field_office_survey_board', 'resort_opening_notice', 'physics_101',
    'foliage_print', 'palm_wall_ornament_left', 'palm_wall_ornament_right',
    'volcano_photo', 'lifesaver', 'basic_window', 'pirate_skull_flag',
    'pirate_dartboard', 'volcano_spout_dry',
}
# Source modules depend on their common grid datum; a shape's lower overhang
# is not their floor contact. Water and suspended props have separate datums.
KEEP_DATUM = {
    'willy_boat', 'island_shipwreck', 'dig_site_bridge', 'slingshot_walnut_target',
    'volcano_well_pipe', 'caldera_forge', 'farm_thatch_corner', 'farm_thatch_eave',
    'farm_thatch_ridge', 'farm_thatch_slope',
}
POSITIVE_FEET = {'professor_boulder', 'field_office_radio'}
SOUTH_FRONT = {
    'banana_altar_assembly', 'resort_carved_bar_counter',
    'resort_changing_entry', 'pirate_treasure_table',
}


def path_for(model_id):
    ns, name = model_id.split(':', 1)
    return RESOURCES / 'assets' / ns / 'models' / (name + '.json')


def matrix(degrees, offset):
    c, s = ((1, 0), (0, 1), (-1, 0), (0, -1))[degrees // 90]
    return [[c, 0, -s, offset[0] / 16 + (1 - c + s) / 2],
            [0, 1, 0, offset[1] / 16],
            [s, 0, c, offset[2] / 16 + (1 - c - s) / 2]]


def point(p, m):
    return [sum(m[i][j] * p[j] for j in range(3)) + 16 * m[i][3] for i in range(3)]


def snapped(value):
    nearest = round(value / 16) * 16
    return nearest if abs(value - nearest) < .001 else value


def wrap(model, degrees, offset, reason):
    """A native composite keeps every authored face, avoiding UV mirrors."""
    m = matrix(degrees, offset)
    child = copy.deepcopy(model)
    child.pop('stardewcraft:collision', None)
    child.pop('display', None)
    boxes = []
    for b in model['stardewcraft:collision']['boxes']:
        corners = [point(p, m) for p in itertools.product(*zip(b['from'], b['to']))]
        boxes.append({'from': [snapped(min(p[i] for p in corners)) for i in range(3)],
                      'to': [snapped(max(p[i] for p in corners)) for i in range(3)]})
    # Counter the world-only transform in GUI. Generated icons do not use this,
    # but small native items must retain their previous readable inventory pose.
    gui = copy.deepcopy(model.get('display', {}).get('gui',
        {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [.625]*3}))
    rotation = gui.get('rotation', [0, 0, 0])
    if rotation[2] or gui.get('right_rotation') or len(set(gui.get('scale', [1]*3))) != 1:
        if degrees: raise ValueError('Reviewed GUI compensation needs uniform scale and no Z/right rotation')
    rotation = list(rotation); rotation[1] += degrees; gui['rotation'] = rotation
    # Transforms rotate about (8,0,8), while GUI rotates about (8,8,8).
    delta = point([8, 8, 8], m)
    delta = [delta[i] - 8 for i in range(3)]
    compensation = _xyz(tuple(delta[i] * gui.get('scale', [1]*3)[i] for i in range(3)), rotation)
    gui['translation'] = [gui.get('translation', [0]*3)[i] - compensation[i] for i in range(3)]
    return {
        'parent': 'minecraft:block/block', 'loader': 'forge:composite',
        'render_type': model.get('render_type', 'minecraft:cutout'),
        'textures': copy.deepcopy(model['textures']),
        'display': {**copy.deepcopy(model.get('display', {})), 'gui': gui},
        # Let CompositeModel compose this into ModelState. Its explicit corner
        # origin matches both native ElementsModel and imported geometry; putting
        # a rotation directly on the imported child would recenter it a second time.
        'transform': {'matrix': m},
        'children': {'authored': child},
        'stardewcraft:collision': {**model['stardewcraft:collision'], 'boxes': boxes},
        'stardewcraft:placement': {'version': 2, 'reason': reason,
                                 'clockwise_y': degrees, 'offset_pixels': offset},
    }


def apply(write=True):
    catalog = json.loads(CATALOG.read_text())
    reader = ItemBoundsReader(RESOURCES)
    changed, reviewed = [], []
    for asset in catalog['blocks']:
        name = asset['id'].removeprefix('ginger_')
        role = 'wall' if name in WALL else 'cliff_palm' if name == 'south_palm' else 'floor'
        asset['placement_mode'] = role
        models = asset.get('state_models', {'default': asset['model']})
        raw = {key: reader.raw(model_id) for key, model_id in models.items()}
        bounds = {}
        for key, data in raw.items():
            pts = reader.vertices(reader.inherited(models[key]))
            bounds[key] = ([min(p[i] for p in pts) for i in range(3)],
                           [max(p[i] for p in pts) for i in range(3)])
        min_collision_y = min(b['from'][1] for data in raw.values()
                              for b in data.get('stardewcraft:collision', {}).get('boxes', [])) if all(
            'stardewcraft:collision' in data for data in raw.values()) else 0
        datum = math.floor((min_collision_y + 1e-5) / 16) * 16
        for key, model_id in models.items():
            data = raw[key]
            if 'stardewcraft:placement' in data:
                if data['stardewcraft:placement']['version'] == 1:
                    data['transform'] = data['children']['authored'].pop('transform')
                    data['stardewcraft:placement']['version'] = 2
                for b in data['stardewcraft:collision']['boxes']:
                    for edge in ('from', 'to'): b[edge] = [snapped(v) for v in b[edge]]
                if write: path_for(model_id).write_text(json.dumps(data, ensure_ascii=False, indent=2)+'\n')
                changed.append({'owner': asset['id'], 'state': key, 'model': model_id, **data['stardewcraft:placement']})
                continue  # Re-export makes fresh raw geometry.
            if 'stardewcraft:collision' not in data: continue
            lo, hi = bounds[key]; degrees = 0; offset = [0, 0, 0]; reason = None
            if role == 'wall':
                # Most authored wall faces already point NORTH. The dartboard's
                # explicitly named game surface is SOUTH; rotate, never mirror.
                degrees = 180 if name == 'pirate_dartboard' else 0
                projected = [point(p, matrix(degrees, [0, 0, 0])) for p in itertools.product(*zip(lo, hi))]
                collision_corners = [point(p, matrix(degrees, [0, 0, 0]))
                                     for b in data['stardewcraft:collision']['boxes']
                                     for p in itertools.product(*zip(b['from'], b['to']))]
                offset[2] = 16 - max(p[2] for p in projected + collision_corners)
                reason = 'wall_back_flush_and_authored_front_north'
            elif role == 'cliff_palm' and key == '1':
                degrees = 270  # Authored leaning trunk +X becomes canonical NORTH.
                reason = 'cliff_palm_lean_matches_facing'
            elif name not in KEEP_DATUM and (min_collision_y < -1e-5 or name in POSITIVE_FEET):
                # All states share one integer MAIN lift; align each authored
                # foot to that datum. Do not round a tiny anti-flicker offset
                # into nearly a full block of empty air.
                offset[1] = datum - lo[1]
                reason = 'ground_contact_at_reserved_integer_datum'
            if name in SOUTH_FRONT:
                # Keep the actual reserved footprint, rather than counting
                # cosmetic trim extending beyond a doorway's own grid cell.
                degrees = 180
                boxes = data['stardewcraft:collision']['boxes']
                for axis in (0, 2):
                    offset[axis] = min(b['from'][axis] for b in boxes) + max(b['to'][axis] for b in boxes) - 16
                reason = 'authored_south_front_to_canonical_north_keep_footprint'
            if reason and (degrees or any(abs(v) > 1e-5 for v in offset)):
                replacement = wrap(data, degrees, offset, reason)
                if write: path_for(model_id).write_text(json.dumps(replacement, ensure_ascii=False, indent=2)+'\n')
                changed.append({'owner': asset['id'], 'state': key, 'model': model_id,
                                'reason': reason, 'clockwise_y': degrees, 'offset_pixels': offset})
        reviewed.append({'owner': asset['id'], 'mode': role,
                         'datum_exception': name in KEEP_DATUM,
                         'models': list(models.values())})
    if write:
        CATALOG.write_text(json.dumps(catalog, ensure_ascii=False, indent=2)+'\n')
    return {'registered_owners': len(reviewed), 'models_adjusted': changed, 'reviewed': reviewed}


if __name__ == '__main__':
    result = apply()
    output = ROOT / 'docs/ginger-island-research/integration/placement-profile-audit.json'
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n')
    print(json.dumps({'owners': result['registered_owners'], 'models_adjusted': len(result['models_adjusted'])}))

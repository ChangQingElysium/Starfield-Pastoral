"""Root-sized building profiles and an empty state of the reviewed walnut bush.

Leaves keep their exact approved geometry, UVs and texture. The selection profile
describes the planted root, not the full transparent foliage rectangle. Thus a
single plant owns one cell and never creates invisible leaf-envelope extensions.
"""
import copy
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODELS = ROOT / 'src/main/resources/assets/stardewcraft/models/block/ginger_island'

# Actual reviewed source roots: longleaf=root_node center (32,0,9), walnut=
# stem_base center (15.5,0,7.5). Bring their root to the MC anchor near (8,0,8)
# by a rigid translation; never scale the plant or rotate/mirror its UVs.
ROOT_OFFSETS = {
    'island_bright_longleaf_assembly': [-24, 0, 0],
    'golden_walnut_bush_rework': [-8, 0, 0],
    'golden_walnut_bush_empty': [-8, 0, 0],
}
ROOT_BOXES = {
    'island_bright_longleaf_assembly': ([4, 0, 5], [12, 10, 13]),
    'island_broadleaf_rosette': ([4, 0, 4], [12, 6, 12]),
    'island_curled_fern': ([4, 0, 4], [12, 12, 12]),
    'island_counting_purple_flowers': ([4, 0, 4], [12, 10, 12]),
    'island_hint_white_flowers': ([4, 0, 5], [12, 8, 12]),
    'island_hint_yellow_flowers': ([4, 0, 4], [12, 4, 12]),
    'island_beached_seaweed': ([2, 0, 2], [14, 1, 14]),
    # Walnut bushes are woody source obstacles, unlike the passable herbs.
    # Keep only the low stems solid; adjacent foliage can overlap decorations.
    'golden_walnut_bush_rework': ([4, 0, 4], [12, 12, 12]),
    'golden_walnut_bush_empty': ([4, 0, 4], [12, 12, 12]),
    'island_yellowgreen_leaf_clump': ([3, 0, 4], [13, 5, 12]),
    'island_blue_spiky_seedling': ([4, 0, 4], [12, 9, 12]),
}


def normalize_static_model(name, model):
    """Run after export_static material/particle binding and before saving."""
    if name == 'field_office_canvas_floor':
        cubes = model.get('elements', [])
        if len(cubes) != 1 or cubes[0]['from'] != [0, 0, 0] or cubes[0]['to'] != [16, 16, 16]:
            raise ValueError('Canvas floor is no longer an unrotated full cube')
        if cubes[0].get('rotation'):
            raise ValueError('Canvas floor cullfaces require the native cube boundary')
        for direction, face in cubes[0]['faces'].items():
            face['cullface'] = direction
        return model
    if name not in ROOT_BOXES:
        return model
    offset = ROOT_OFFSETS.get(name, [0, 0, 0])
    marker = model.get('stardewcraft:plant_root')
    if marker is None and any(offset):
        # These two whole authored plants use the established cuboid geometry
        # importer. Its per-part translation is measured in model pixels.
        if model.get('loader') != 'stardewcraft:geometry' or not model.get('parts'):
            raise ValueError('Plant root relocation needs reviewed imported cuboids: ' + name)
        for part in model['parts']:
            matrix = part.setdefault('transform', [1, 0, 0, 0, 0, 1, 0, 0,
                                                  0, 0, 1, 0, 0, 0, 0, 1])
            for axis in range(3):
                matrix[12 + axis] += offset[axis]
    elif marker is not None and marker['offset_pixels'] != offset:
        raise ValueError('Plant already has a different root offset: ' + name)
    lo, hi = ROOT_BOXES[name]
    model['stardewcraft:collision'] = {'mode': 'custom', 'policy': 'aabb',
                                      'boxes': [{'from': list(lo), 'to': list(hi)}]}
    model['stardewcraft:plant_root'] = {'version': 1, 'offset_pixels': offset,
                                      'foliage_reserves_cells': False}
    return model


def walnut_empty_model(ripe, project):
    """Remove only the current approved fruit group; never use the rejected old JSON."""
    groups = {g['uuid']: g for g in project.get('groups', [])}
    fruit_groups = {uid for uid, group in groups.items() if group['name'] == 'golden_walnut'}
    if len(fruit_groups) != 1:
        raise ValueError('Walnut source must have one explicitly authored fruit group')
    elements = {element['uuid']: element for element in project['elements']}
    exported, fruit = [], set()

    def walk(nodes, in_fruit=False):
        for node in nodes:
            if isinstance(node, dict):
                group = {**groups.get(node.get('uuid'), {}), **node}
                if group.get('export', True):
                    walk(group.get('children', []), in_fruit or node.get('uuid') in fruit_groups)
            elif elements[node].get('export', True):
                exported.append(node)
                if in_fruit:
                    fruit.add(node)

    walk(project.get('outliner') or list(elements))
    if len(exported) != len(ripe.get('parts', [])) or len(fruit) != 10:
        raise ValueError('Reviewed walnut fruit geometry changed; recheck empty-state extraction')
    empty = copy.deepcopy(ripe)
    empty['parts'] = [part for uid, part in zip(exported, empty['parts']) if uid not in fruit]
    empty['stardewcraft:walnut_state'] = {'ripe': False, 'removed_fruit_parts': len(fruit),
                                        'source_model': 'golden_walnut_bush_rework'}
    # Both states share the same root, material and bounds. This also keeps a
    # union-reserved stateful block at one cell when the fruit is collected.
    return empty


def apply_existing_models():
    """Targeted integration without touching the catalog or other assets."""
    changed = []
    for name in ROOT_BOXES:
        if name == 'golden_walnut_bush_empty':
            continue
        path = MODELS / (name + '.json')
        model = normalize_static_model(name, json.loads(path.read_text()))
        path.write_text(json.dumps(model, ensure_ascii=False, indent=2) + '\n')
        changed.append(name)
    floor = MODELS / 'field_office_canvas_floor.json'
    floor.write_text(json.dumps(normalize_static_model('field_office_canvas_floor',
                     json.loads(floor.read_text())), ensure_ascii=False, indent=2) + '\n')
    source = ROOT / 'assets-src/ginger_island/03_east/walnut_bush/golden_walnut_bush_rework.bbmodel'
    ripe = json.loads((MODELS / 'golden_walnut_bush_rework.json').read_text())
    empty = walnut_empty_model(ripe, json.loads(source.read_text()))
    (MODELS / 'golden_walnut_bush_empty.json').write_text(json.dumps(empty, ensure_ascii=False, indent=2) + '\n')
    return changed + ['golden_walnut_bush_empty', 'field_office_canvas_floor']


if __name__ == '__main__':
    print(json.dumps({'updated_models': apply_existing_models()}, ensure_ascii=False))

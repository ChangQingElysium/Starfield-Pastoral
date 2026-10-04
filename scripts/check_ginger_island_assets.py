#!/usr/bin/env python3
"""Check packaged Ginger Island resources without reading local authoring assets."""
import argparse
import json
import math
import itertools
from pathlib import Path
import re
import struct


def validate_resource_location(identifier):
    assert re.fullmatch(r'[a-z0-9_.-]+:[a-z0-9/._-]+', identifier), \
        f'Invalid Minecraft resource location: {identifier}'
    namespace, name = identifier.split(':', 1)
    assert all(part not in ('', '.', '..') for part in name.split('/')), \
        f'Invalid Minecraft resource path: {identifier}'
    return namespace, name


def resource_path(root, kind, identifier, suffix, directory_names=None):
    """Require the exact directory spelling used by Minecraft's pack scanner.

    Path.exists/read_bytes silently accept different case on common macOS
    volumes. PathPackResources.listPath instead enumerates the physical names
    and ignores names which cannot form a ResourceLocation.
    """
    namespace, name = validate_resource_location(identifier)
    cache = directory_names if directory_names is not None else {}
    path = root
    for part in ['assets', namespace, *kind.split('/'), *(name + suffix).split('/')]:
        if path not in cache:
            cache[path] = {entry.name for entry in path.iterdir()} if path.is_dir() else set()
        if part not in cache[path]:
            other_case = sorted(entry for entry in cache[path] if entry.lower() == part.lower())
            assert not other_case, f'Resource filename case mismatch: {path / part}; actual {other_case}'
            raise AssertionError(f'Missing resource path: {path / part}')
        path /= part
    return path


def check(root, translations=False):
    manifest = json.loads((root / 'data/stardewcraft/ginger_island/assets.json').read_text())
    by_id = {asset['id']: asset for asset in manifest['blocks']}
    ids = set(); models = {}; images = set(); directory_names = {}

    def resource(kind, identifier, suffix):
        return resource_path(root, kind, identifier, suffix, directory_names)

    def image(identifier):
        if identifier in images: return
        validate_resource_location(identifier)
        if identifier.startswith('minecraft:'):
            images.add(identifier)
            return  # Supplied by the vanilla client resource pack.
        path = resource('textures', identifier, '.png')
        data = path.read_bytes()
        assert data[:8] == b'\x89PNG\r\n\x1a\n', f'Invalid PNG: {path}'
        assert all(struct.unpack('>II', data[16:24])), f'Empty texture: {path}'
        width, height = struct.unpack('>II', data[16:24])
        metadata = Path(str(path) + '.mcmeta')
        if metadata.exists():
            animation = json.loads(metadata.read_text()).get('animation')
            if animation is not None:
                fw = animation.get('width', width if 'height' in animation else min(width, height))
                fh = animation.get('height', height if 'width' in animation else min(width, height))
                assert fw > 0 and fh > 0 and width % fw == height % fh == 0, f'Invalid animation frame size: {path}'
                frames = width // fw * (height // fh)
                assert animation.get('frametime', 1) > 0, f'Invalid animation timing: {path}'
                for frame in animation.get('frames', []):
                    index = frame if isinstance(frame, int) else frame['index']
                    assert 0 <= index < frames, f'Invalid animation frame: {path}/{index}'
        images.add(identifier)

    def texture(name, bindings):
        visited = set()
        while name.startswith('#'):
            assert name not in visited, f'Texture cycle: {name}'
            visited.add(name); name = bindings[name[1:]]
        image(name)

    def inspect(data, identifier):
        inherited = model(data['parent']) if data.get('parent') else {}
        bindings = {**inherited, **data.get('textures', {})}
        assert 'particle' in bindings, f'Missing particle: {identifier}'
        texture(bindings['particle'], bindings)
        for name in bindings.values(): texture(name, bindings)
        for part in data.get('parts', []) + data.get('elements', []):
            if 'elements' in data:
                assert all(-16 <= v <= 32 for v in part['from'] + part['to']), f'Java model coordinates out of range: {identifier}'
            for face in part.get('faces', {}).values(): texture(face['texture'], bindings)
        for quad in data.get('quads', []):
            texture(quad['texture'], bindings)
            vertices = quad['vertices']
            assert len(vertices) == 20 and all(math.isfinite(v) for v in vertices), f'Invalid imported quad: {identifier}'
            assert all(0 <= vertices[start + axis] <= 1 for start in (0, 5, 10, 15) for axis in (3, 4)), \
                f'Imported quad UV is outside its texture: {identifier}'
            a, b, c = [vertices[start:start+3] for start in (0, 5, 10)]
            ab, ac = [b[i]-a[i] for i in range(3)], [c[i]-a[i] for i in range(3)]
            normal = [ab[1]*ac[2]-ab[2]*ac[1], ab[2]*ac[0]-ab[0]*ac[2], ab[0]*ac[1]-ab[1]*ac[0]]
            assert sum(v*v for v in normal) > 1e-14, f'Degenerate imported quad: {identifier}'
            assert isinstance(quad.get('tintindex', -1), int) and quad.get('tintindex', -1) >= -1, \
                f'Invalid imported quad tint: {identifier}'
        for key, child in data.get('children', {}).items():
            if isinstance(child, str): model(child)
            else: inspect(child, identifier + '/child/' + key)
        collision = data.get('stardewcraft:collision')
        if collision and collision['mode'] == 'custom':
            boxes = collision['boxes']
            assert 0 < len(boxes) <= 48, f'Collision budget: {identifier}'
            if collision.get('policy') == 'aabb': assert len(boxes) == 1
            for box in boxes:
                assert len(box['from']) == len(box['to']) == 3
                assert all(math.isfinite(x) for x in box['from'] + box['to'])
                assert all(a < b for a, b in zip(box['from'], box['to']))
        models[identifier] = bindings
        return bindings

    def model(identifier):
        if identifier in models: return models[identifier]
        validate_resource_location(identifier)
        if identifier.startswith('minecraft:'): return {}
        return inspect(json.loads(resource('models', identifier, '.json').read_text()), identifier)

    for asset in manifest['blocks']:
        identifier = asset['id']
        assert identifier not in ids, f'Duplicate block: {identifier}'
        ids.add(identifier)
        assert asset.get('placement_mode') in {'floor', 'wall', 'cliff_palm'}, \
            f'Unreviewed manual attachment: {identifier}'
        assert asset['type'] in {'building', 'furniture', 'furniture_painting', 'carpet', 'utility',
                                 'forage', 'natural_ground', 'natural_rock', 'natural_grass',
                                 'natural_flower', 'natural_aquatic'}, f'Invalid catalog category: {identifier}'
        model(asset['model'])
        states = json.loads(resource('blockstates', 'stardewcraft:' + identifier, '.json').read_text())
        def visit(value):
            if isinstance(value, list):
                for child in value: visit(child)
            elif isinstance(value, dict):
                if 'model' in value: model(value['model'])
                for child in value.values(): visit(child)
        visit(states)
        if asset.get('state_property'):
            prop = asset['state_property']
            assert asset['kind'] == 'state_decor' and len(asset['state_models']) >= 2
            assert asset.get('state_role') in {'appearance', 'interaction', 'progress', 'environment'}, \
                f'Unreviewed state role: {identifier}'
            for value, state_model in asset['state_models'].items():
                for direction in ('north', 'east', 'south', 'west'):
                    variant = f'facing={direction},part=main,{prop}={value}'
                    assert states['variants'][variant]['model'] == state_model, f'Unbound visual state: {identifier}/{variant}'
        if asset['kind'] in {'volcano_floor', 'caldera_floor'}:
            assert 1 <= asset.get('variant_count', 0) <= 6, f'Invalid ground choices: {identifier}'
        if asset.get('catalog_owner'):
            owner = by_id[asset['catalog_owner']]
            assert owner['id'] != identifier and 'catalog_owner' not in owner
            assert asset['kind'] == 'decor' and owner['kind'] == 'state_decor'
            assert asset['type'] == owner['type']
            assert asset['canonical_state'].keys() == {owner['state_property']}
            assert asset['canonical_state'][owner['state_property']] in owner['state_models']
        else:
            assert 'canonical_state' not in asset, f'Alias state has no catalog owner: {identifier}'
        model('stardewcraft:item/' + identifier)
        loot = root / f'data/stardewcraft/loot_table/blocks/{identifier}.json'
        if asset['kind'] != 'cooled_lava': assert loot.exists(), f'Missing loot: {identifier}'
        if identifier in ('ginger_resort_roof', 'ginger_resort_umbrella_coral', 'ginger_south_palm'):
            functions = json.loads(loot.read_text())['pools'][0]['entries'][0].get('functions', [])
            assert {'function': 'minecraft:copy_state', 'block': 'stardewcraft:' + identifier,
                    'properties': ['variant']} in functions, f'Dropped visual variant lost: {identifier}'
    aliases = {'stardewcraft:' + a['id'] for a in manifest['blocks'] if a.get('catalog_owner')}
    alias_tag = root / 'data/stardewcraft/tags/item/ginger_island_legacy_aliases.json'
    assert set(json.loads(alias_tag.read_text())['values']) == aliases, 'JEI aliases disagree with catalog metadata'
    hidden = json.loads((root / 'data/stardewcraft/tags/item/hidden.json').read_text())
    assert '#stardewcraft:ginger_island_legacy_aliases' in hidden['values']
    # Whole-column progress keeps its old ruined foot; the legacy leaf ID keeps
    # its original lower pivot. This is a resource binding, not a save rewrite.
    column = by_id['ginger_resort_ruined_column']
    assert column['state_role'] == 'progress' and column['state_property'] == 'repaired'
    assert set(column['state_models']) == {'false', 'true'}
    for state, height in [('false', 32), ('true', 48)]:
        data = json.loads(resource('models', column['state_models'][state], '.json').read_text())
        boxes = data['stardewcraft:collision']['boxes']
        assert min(b['from'][1] for b in boxes) == 0 and max(b['to'][1] for b in boxes) == height
    for canonical, count in [('ginger_palm_wall_ornament_left', 2), ('ginger_island_starfish_gold', 4)]:
        owner = by_id[canonical]
        assert owner['state_role'] == 'appearance' and set(owner['state_models']) == {str(i) for i in range(count)}
    # Functional regression: one owner reserves each whole roof, while an
    # umbrella leaves enough headroom for standing and the initial chair teleport.
    def read_model(identifier):
        return json.loads(resource('models', identifier, '.json').read_text())

    def cells(boxes):
        result = {(0, 0, 0)}
        for box in boxes:
            result.update(itertools.product(*[range(math.floor(box['from'][a] / 16 + 1e-7),
                                                      math.ceil(box['to'][a] / 16 - 1e-7)) for a in range(3)]))
        return result

    def intersects(a, b):
        return all(a['from'][i] < b['to'][i] - 1e-7 and b['from'][i] < a['to'][i] - 1e-7
                   for i in range(3))

    roof = next(a for a in manifest['blocks'] if a['id'] == 'ginger_resort_roof')
    assert roof['state_property'] == 'variant' and set(roof['state_models']) == {'0', '1'}
    roof_cells = set()
    for state, count, footprint, width in (('0', 24, 56, 96), ('1', 28, 64, 112)):
        data = read_model(roof['state_models'][state])
        components = {k: v for k, v in data['children'].items() if k.isdigit()}
        assert data['loader'] == 'neoforge:composite' and len(components) == count
        assert len(data['stardewcraft:collision']['boxes']) == count + 2 + count // 4 * 2
        assert data['stardewcraft:collision']['policy'] == 'compound_roof_with_gables'
        assert data['stardewcraft:timber_profile']['beam_min_y'] == -16
        assert data['stardewcraft:timber_profile']['depth_px'] == 64
        assert data['stardewcraft:timber_profile']['collision_xz_pixels'] == [0, 0, width, 64]
        assert all(0 <= b['from'][0] < b['to'][0] <= width and 0 <= b['from'][2] < b['to'][2] <= 64
                   for b in data['stardewcraft:collision']['boxes']), 'Decorative roof eaves must not claim adjacent buildings'
        assert len(cells(data['stardewcraft:collision']['boxes'])) == footprint
        roof_cells.update(cells(data['stardewcraft:collision']['boxes']))
        for child in components.values():
            matrix = child['transform']['matrix']
            assert len(matrix) == 3 and all(len(row) == 4 for row in matrix)
            # Rigid transforms preserve authored texel density and module size.
            assert matrix[0][0] in (-1, 1) and matrix[1][:3] == [0, 1, 0]
            assert matrix[2][2] == matrix[0][0]
    assert len(roof_cells) == 68, 'Whole four-deep roof reserves nominal bodies and timber, not decorative eaves'
    left_roof = {(x + 26, y, z) for x, y, z in roof_cells}
    right_roof = {(x + 34, y, z) for x, y, z in roof_cells}
    assert not left_roof & right_roof and not any(x == 33 for x, y, z in left_roof | right_roof)
    for suffix in ('high', 'low', 'mid', 'ridge', 'wide_crown'):
        old = 'ginger_resort_tile_roof_' + suffix
        assert old not in ids, f'Roof component registered separately: {old}'
        assert not (root / f'assets/stardewcraft/blockstates/{old}.json').exists()
        assert not (root / f'assets/stardewcraft/models/item/{old}.json').exists()
        assert not (root / f'data/stardewcraft/loot_table/blocks/{old}.json').exists()
    umbrella = next(a for a in manifest['blocks'] if a['id'] == 'ginger_resort_umbrella_coral')
    chair = read_model('stardewcraft:block/ginger_island/resort_beach_chair')
    chair_profile = chair['stardewcraft:collision']
    assert chair_profile['policy'] == 'aabb' and len(chair_profile['boxes']) == 1
    chair_box = chair_profile['boxes'][0]
    assert chair_box['from'] == [0, 0, 0]
    assert all(abs(a - b) < 1e-4 for a, b in zip(chair_box['to'], [16, 27.1277, 22.8946]))
    assert cells([chair_box]) == {(0, y, z) for y in (0, 1) for z in (0, 1)}, \
        'Half-pixel crossbar tips must not claim adjacent chair columns'
    for identifier in umbrella['state_models'].values():
        data = read_model(identifier)
        profile = data['stardewcraft:collision']
        assert profile['policy'] == 'compound_umbrella' and len(profile['boxes']) == 3
        assert len(cells(profile['boxes'])) == 11
        base, shaft, canopy = profile['boxes']
        # Preserve source positions: chair (25,61), umbrella (24,62). The
        # umbrella MAIN is one block above the foot surface / chair MAIN.
        moved_chair = {side: [v + (16, -16, -16)[a] for a, v in enumerate(chair_box[side])]
                       for side in ('from', 'to')}
        chair_cells = {(x + 1, y - 1, z - 1) for x, y, z in cells([chair_box])}
        assert not chair_cells & cells(profile['boxes']), 'Source chair/umbrella owners intersect'
        assert not any(intersects(moved_chair, b) for b in profile['boxes']), 'Source chair and umbrella physically intersect'
        assert shaft['to'][0] - shaft['from'][0] <= 2 and shaft['to'][2] - shaft['from'][2] <= 2
        # MAIN is one block above the foot surface; ChairBlock initially
        # teleports a 1.8-block player to its registered 9/16 seat height.
        assert 1 + canopy['from'][1] / 16 > 1.8 + 9 / 16
        assert base['to'][1] < 0
        for x, z in ((8, -8), (8, 24), (-8, 8), (24, 8)):
            for foot_y in (-16, -7, -16.6):  # standing, initial seat, steady rider
                player = {'from': [x - 4.8, foot_y, z - 4.8],
                          'to': [x + 4.8, foot_y + 28.8, z + 4.8]}
                assert not any(intersects(player, b) for b in profile['boxes']), 'Umbrella blocks a user position'
    withdrawn = 'ginger_resort_changing_booths'
    assert withdrawn not in ids, 'Withdrawn whole changing room must not become a block/item again'
    for kind, name in (('blockstates', withdrawn), ('models/item', withdrawn),
                       ('models/block/ginger_island', 'resort_changing_booths'),
                       ('models/block/ginger_island', 'resort_changing_booths_extension')):
        assert not (root / f'assets/stardewcraft/{kind}/{name}.json').exists()
    assert not (root / f'data/stardewcraft/loot_table/blocks/{withdrawn}.json').exists()
    assert 'ginger_resort_changing_entry' in ids, 'Keep the approved independently buildable entrance'
    entrance = read_model('stardewcraft:block/ginger_island/resort_changing_entry')
    entrance_boxes = entrance['stardewcraft:collision']['boxes']
    entrance_cells = cells(entrance_boxes)
    assert entrance_cells == {(0, 0, 0), (0, 1, 0)}, 'Door trim must not reserve neighbouring native wall cells'
    assert len(entrance_boxes) == 6 and entrance['stardewcraft:collision']['policy'] == 'compound_door_frame'
    for z in range(-6, 23):
        player = {'from': [3.2, 1, z - 4.8], 'to': [12.8, 29.8, z + 4.8]}
        assert not any(intersects(player, b) for b in entrance_boxes), 'Entrance blocks a 0.6x1.8 player'
    # Two separately placed doors can share ordinary block-built outside and
    # dividing walls. Their decorative visual overhang never reserves those walls.
    left = entrance_cells
    right = {(x + 2, y, z) for x, y, z in entrance_cells}
    wood_walls = {(x, y, z) for x in (-1, 1, 3) for y in (0, 1) for z in (0, 1)}
    assert not (left & right or left & wood_walls or right & wood_walls)
    if 'ginger_south_palm' in ids:
        palm = next(a for a in manifest['blocks'] if a['id'] == 'ginger_south_palm')
        assert palm['state_property'] == 'variant' and set(palm['state_models']) == {'0', '1', '2', '3'}
        palm_cells = set()
        for state, count, footprint in (('0', 4, 5), ('1', 6, 10), ('2', 4, 4), ('3', 3, 3)):
            data = read_model(palm['state_models'][state])
            profile = data['stardewcraft:collision']
            assert profile['policy'] == 'compound_palm_trunk'
            boxes = profile['boxes']
            assert len(boxes) == count and min(b['from'][1] for b in boxes) == 0
            assert len(cells(boxes)) == footprint
            turned = data.get('stardewcraft:placement', {}).get('clockwise_y') == 270
            limit_x, limit_z = (12, 14) if turned else (14, 12)
            assert all(b['to'][0] - b['from'][0] <= limit_x and b['to'][2] - b['from'][2] <= limit_z
                       for b in boxes), 'Leaves must not reserve their broad crown envelope'
            assert len(data['children']) == 1, 'Keep the whole authored visual tree'
            palm_cells.update(cells(boxes))
            # A player beside the trunk can stand under the crown.
            player = {'from': [19.2, 0, 19.2], 'to': [28.8, 28.8, 28.8]}
            assert not any(intersects(player, b) for b in boxes)
        assert len(palm_cells) == 13, 'Only reserve the root and small trunk union, not empty crown air'
        assert sum(y == 0 and x == z == 0 for x, y, z in palm_cells) == 1
        assert (-2, 3, -2) not in palm_cells, 'Foliage beside existing terrain must not prevent placement'
        north_lean = read_model(palm['state_models']['1']).get('stardewcraft:placement', {}).get('clockwise_y') == 270
        assert all((x == 0 and -2 <= z <= 0 if north_lean else z == 0 and 0 <= x <= 2)
                   and 0 <= y <= 4 for x, y, z in palm_cells)
        for name in ('south_palm_ground', 'south_palm_cliff', 'coconut_palm', 'resort_young_palm'):
            assert 'ginger_' + name not in ids, 'Palm visual state registered independently'
            for p in (root / f'assets/stardewcraft/blockstates/ginger_{name}.json',
                      root / f'assets/stardewcraft/models/item/ginger_{name}.json',
                      root / f'data/stardewcraft/loot_table/blocks/ginger_{name}.json'):
                assert not p.exists(), 'Palm model component must not have its own item/block/loot'
    if translations:
        languages = list((root / 'assets/stardewcraft/lang').glob('*.json'))
        assert len(languages) == 12
        for path in languages:
            lang = json.loads(path.read_text())
            assert 'block.stardewcraft.ginger_resort_changing_booths' not in lang
            for identifier in ids:
                key = 'block.stardewcraft.' + identifier
                assert key in lang and lang[key].strip(), f'Missing name: {path.name}/{key}'
    print(f'OK: {len(ids)} block registrations, {len(models)} referenced models, {len(images)} textures; simplified collision profiles valid')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--resource-root', type=Path, default=Path(__file__).resolve().parents[1] / 'src/main/resources')
    parser.add_argument('--translations', action='store_true')
    args = parser.parse_args()
    check(args.resource_root, True)

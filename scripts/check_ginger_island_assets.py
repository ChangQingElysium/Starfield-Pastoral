#!/usr/bin/env python3
"""Check packaged Ginger Island resources without reading local authoring assets."""
import argparse
import json
import math
from pathlib import Path
import struct


def check(root, translations=False):
    manifest = json.loads((root / 'data/stardewcraft/ginger_island/assets.json').read_text())
    ids = set(); models = {}; images = set()

    def resource(kind, identifier, suffix):
        namespace, name = identifier.split(':', 1)
        return root / 'assets' / namespace / kind / (name + suffix)

    def image(identifier):
        if identifier in images: return
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
        for quad in data.get('quads', []): texture(quad['texture'], bindings)
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
        if identifier.startswith('minecraft:'): return {}
        return inspect(json.loads(resource('models', identifier, '.json').read_text()), identifier)

    for asset in manifest['blocks']:
        identifier = asset['id']
        assert identifier not in ids, f'Duplicate block: {identifier}'
        ids.add(identifier)
        assert asset['type'] in {'building', 'furniture', 'furniture_painting', 'carpet', 'utility',
                                 'forage', 'natural_ground', 'natural_rock', 'natural_grass',
                                 'natural_flower', 'natural_aquatic'}, f'Invalid catalog category: {identifier}'
        model(asset['model'])
        states = json.loads((root / f'assets/stardewcraft/blockstates/{identifier}.json').read_text())
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
            for value, state_model in asset['state_models'].items():
                for direction in ('north', 'east', 'south', 'west'):
                    variant = f'facing={direction},part=main,{prop}={value}'
                    assert states['variants'][variant]['model'] == state_model, f'Unbound visual state: {identifier}/{variant}'
        model('stardewcraft:item/' + identifier)
        loot = root / f'data/stardewcraft/loot_table/blocks/{identifier}.json'
        if asset['kind'] != 'cooled_lava': assert loot.exists(), f'Missing loot: {identifier}'
    if translations:
        languages = list((root / 'assets/stardewcraft/lang').glob('*.json'))
        assert len(languages) == 12
        for path in languages:
            lang = json.loads(path.read_text())
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

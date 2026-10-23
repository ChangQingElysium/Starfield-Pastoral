"""Export the approved low Joja parcel as native bottom/top/double slab models."""
import argparse
import base64
import copy
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'assets-src/decor/joja_parcel/joja_parcel.bbmodel'
ASSETS = ROOT / 'src/main/resources/assets/stardewcraft'
NAME = 'joja_supermarket_crate'
MODEL = f'stardewcraft:decor/common/{NAME}'


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n')


def main(output_root=None):
    global ASSETS
    resources = output_root or ROOT / 'src/main/resources'
    ASSETS = resources / 'assets/stardewcraft'
    (ASSETS / 'textures/block/decor/common').mkdir(parents=True, exist_ok=True)
    source = json.loads(SOURCE.read_text())
    assert source['meta']['model_format'] == 'java_block'
    assert len(source['elements']) == 1
    cube = source['elements'][0]
    assert cube['from'] == [0, 0, 0] and cube['to'] == [16, 8, 16]
    textures = source['textures']
    for texture in textures:
        suffix = '_particle' if texture.get('particle') else ''
        path = ASSETS / f'textures/block/decor/common/{NAME}{suffix}.png'
        path.write_bytes(base64.b64decode(texture['source'].split(',')[1]))
    resolution = source['resolution']
    faces = {}
    for direction, face in cube['faces'].items():
        assert face['texture'] == 0
        uv = [value * 16 / resolution['width' if index % 2 == 0 else 'height']
              for index, value in enumerate(face['uv'])]
        faces[direction] = {'uv': uv, 'texture': '#0'}
    base = {'from': cube['from'], 'to': cube['to'], 'faces': faces}
    for kind, offsets in [('bottom', [0]), ('top', [8]), ('double', [0, 8])]:
        elements = []
        for offset in offsets:
            element = copy.deepcopy(base)
            element['from'][1] += offset
            element['to'][1] += offset
            if kind == 'double':
                del element['faces']['up' if offset == 0 else 'down']
            for direction, face in element['faces'].items():
                if direction in ('north', 'south', 'east', 'west'):
                    face['cullface'] = direction
                elif direction == 'up' and element['to'][1] == 16:
                    face['cullface'] = direction
                elif direction == 'down' and element['from'][1] == 0:
                    face['cullface'] = direction
            elements.append(element)
        suffix = '' if kind == 'bottom' else '_' + kind
        write(ASSETS / f'models/decor/common/{NAME}{suffix}.json', {
            'parent': 'minecraft:block',
            'textures': {'0': f'stardewcraft:block/decor/common/{NAME}',
                         'particle': f'stardewcraft:block/decor/common/{NAME}_particle'},
            'elements': elements,
        })
    variants = {}
    for facing, angle in [('north', 0), ('east', 90), ('south', 180), ('west', 270)]:
        for kind in ('bottom', 'top', 'double'):
            suffix = '' if kind == 'bottom' else '_' + kind
            variants[f'facing={facing},type={kind}'] = {'model': MODEL + suffix, 'y': angle}
    write(ASSETS / f'blockstates/{NAME}.json', {'variants': variants})
    write(ASSETS / f'models/item/{NAME}.json', {
        'parent': MODEL,
        'display': {'gui': {'rotation': [30, 225, 0], 'translation': [0, 2, 0], 'scale': [0.625] * 3}},
    })
    write(resources / f'data/stardewcraft/loot_table/blocks/{NAME}.json', {
        'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{
            'type': 'minecraft:item', 'name': f'stardewcraft:{NAME}',
            'functions': [
                {'function': 'minecraft:set_count', 'count': 2,
                 'conditions': [{'condition': 'minecraft:block_state_property',
                                 'block': f'stardewcraft:{NAME}', 'properties': {'type': 'double'}}]},
                {'function': 'minecraft:explosion_decay'},
            ],
        }]}],
    })


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output-root', type=Path, help='Resource root; defaults to src/main/resources.')
    main(parser.parse_args().output_root)

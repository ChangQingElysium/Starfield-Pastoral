"""Export the approved basket as an independent native decoration."""
import argparse
import base64
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'assets-src/decor/shopping_basket/shopping_basket.bbmodel'
ASSETS = ROOT / 'src/main/resources/assets/stardewcraft'
NAME = 'shopping_basket'


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n')


def main(output_root=None):
    global ASSETS
    ASSETS = (output_root or ROOT / 'src/main/resources') / 'assets/stardewcraft'
    (ASSETS / 'textures/block/decor/common').mkdir(parents=True, exist_ok=True)
    source = json.loads(SOURCE.read_text())
    assert source['meta']['model_format'] == 'java_block'
    textures = {}
    for index, texture in enumerate(source['textures']):
        suffix = '_particle' if texture.get('particle') else ''
        location = f'block/decor/common/{NAME}{suffix}'
        (ASSETS / f'textures/{location}.png').write_bytes(
            base64.b64decode(texture['source'].split(',')[1]))
        textures[str(index)] = f'stardewcraft:{location}'
        if texture.get('particle'):
            textures['particle'] = f'stardewcraft:{location}'
    elements = []
    resolution = source['resolution']
    for cube in source['elements']:
        assert not any(cube.get('rotation', [0, 0, 0]))
        element = {'from': cube['from'], 'to': cube['to'], 'faces': {}}
        for direction, face in cube['faces'].items():
            index = face.get('texture')
            if index is None:
                continue
            assert type(index) is int and str(index) in textures
            uv = [value * 16 / resolution['width' if i % 2 == 0 else 'height']
                  for i, value in enumerate(face['uv'])]
            # No neighbor culling: the open basket must retain its inner faces.
            element['faces'][direction] = {'uv': uv, 'texture': f'#{index}'}
        elements.append(element)
    write(ASSETS / f'models/decor/common/{NAME}.json', {
        'parent': 'minecraft:block', 'render_type': 'minecraft:cutout',
        'ambientocclusion': False, 'textures': textures, 'elements': elements,
    })
    write(ASSETS / f'models/block/decor/extensions/{NAME}_extension_empty.json', {
        'parent': 'minecraft:block', 'textures': {'particle': textures['particle']}, 'elements': [],
    })
    write(ASSETS / f'models/item/{NAME}.json', {
        'parent': f'stardewcraft:decor/common/{NAME}',
        'display': {'gui': {'rotation': [30, 225, 0], 'translation': [0, 2, 0], 'scale': [0.625] * 3}},
    })
    variants = {}
    for facing, angle in [('north', 0), ('east', 90), ('south', 180), ('west', 270)]:
        variants[f'part=main,facing={facing}'] = {
            'model': f'stardewcraft:decor/common/{NAME}', 'y': angle}
        variants[f'part=extension,facing={facing}'] = {
            'model': f'stardewcraft:block/decor/extensions/{NAME}_extension_empty'}
    write(ASSETS / f'blockstates/{NAME}.json', {'variants': variants})


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output-root', type=Path, help='Resource root; defaults to src/main/resources.')
    main(parser.parse_args().output_root)

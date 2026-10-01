"""Check shipped refrigerator models; no authoring-source or local-path dependency."""
import json
import math
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/stardewcraft'
MODELS = ASSETS / 'models/block/utility'


def read(name):
    return json.loads((MODELS / (name + '.json')).read_text())


def image_for(location):
    namespace, path = location.split(':')
    return Image.open(ROOT / 'src/main/resources/assets' / namespace / 'textures' / (path + '.png')).convert('RGBA')


body, door, collision = (read('fridge' + suffix) for suffix in ['_body', '_door', ''])
body_names = {e['name'] for e in body['elements']}
door_names = {e['name'] for e in door['elements']}
assert not body_names & door_names, 'Moving parts must not also be drawn in the static cabinet'
assert 'lower_door' in door_names and 'lower_handle' in door_names
assert 'upper_door' in body_names and 'upper_handle' in body_names
assert 'upper_door' not in door_names
count = 0
for model in [body, door, read('fridge_extension')]:
    assert 'loader' not in model, 'Use native Java models'
    assert image_for(model['textures']['particle']).getextrema()[3] == (255, 255)
    for e in model['elements']:
        assert all(-16 <= v <= 32 for v in e['from'] + e['to'])
        lengths = [b - a for a, b in zip(e['from'], e['to'])]
        assert min(lengths) > 0
        for side, face in e['faces'].items():
            assert 'cullface' not in face, 'Door and inset faces cannot be culled by neighboring blocks'
            sprite = image_for(model['textures'][face['texture'][1:]])
            u, v, U, V = face['uv']
            assert 0 <= min(u, U) <= max(u, U) <= 16 and 0 <= min(v, V) <= max(v, V) <= 16
            pixels = [abs(U-u)*sprite.width/16, abs(V-v)*sprite.height/16]
            dimensions = ([lengths[0], lengths[2]] if side in ['up', 'down'] else
                          [lengths[0], lengths[1]] if side in ['north', 'south'] else
                          [lengths[2], lengths[1]])
            if face.get('rotation', 0) in [90, 270]:
                dimensions.reverse()
            assert all(math.isclose(p, d) for p, d in zip(pixels, dimensions)), (e['name'], side, pixels, dimensions)
            count += 1
# Inverse hinge rotation of the authored open door must recover the original closed envelope.
for name in ['lower_door', 'lower_handle']:
    e = next(e for e in door['elements'] if e['name'] == name)
    closed = next(e for e in collision['elements'] if e['name'] == name)
    assert [e['from'][2]+13, e['from'][1], 17-e['to'][0]] == closed['from']
    assert [e['to'][2]+13, e['to'][1], 17-e['from'][0]] == closed['to']
bs = json.loads((ASSETS / 'blockstates/fridge.json').read_text())['variants']
assert len(bs) == 8
for facing, angle in [('north',0), ('east',90), ('south',180), ('west',270)]:
    for part, name in [('main','fridge_body'), ('extension','fridge_extension')]:
        entry = bs[f'part={part},facing={facing}']
        assert entry['model'] == 'stardewcraft:block/utility/' + name and entry.get('y',0) == angle
print(f'PASS: {count} native faces, 1px/unit, opaque particles, 4 facings, disjoint door/body, closed hinge envelope')

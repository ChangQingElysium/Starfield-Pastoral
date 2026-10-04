#!/usr/bin/env python3
"""Lossless native export of the approved v4 scene actor, without editing its art."""
import argparse
import hashlib
import json
from pathlib import Path

from compile_native_npc import compile_model

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'assets-src/ginger_island/02_arrival/giant_turtle/v4/giant_turtle.bbmodel'
ASSETS = ROOT / 'src/main/resources/assets/stardewcraft'


def exported(source):
    original = json.loads(source.read_text())
    # The existing compiler's motion-profile contract is NPC-specific. This actor
    # uses only the shared geometry/track output, never humanoid attention or blink.
    profile = dict(intervalMin=5, intervalMax=8, durationMin=.1, durationMax=.2,
                   doubleChance=0, groundOffset=0, walkStride=.5, blinkMode='closed')
    model, png = compile_model(original, profile, 'giant_turtle', required_clips=('idle', 'walk'))
    model.pop('profile')
    model['texture'] = 'stardewcraft:textures/entity/island_actor_native/giant_turtle.png'
    if png != source.with_suffix('.png').read_bytes():
        raise ValueError('Export must preserve the approved external/embedded PNG bytes')
    metadata = dict(version=1, model='stardewcraft:island_actor_native/giant_turtle.json',
                    entity='stardewcraft:ginger_giant_turtle', texture=model['texture'],
                    source_sha256=hashlib.sha256(source.read_bytes()).hexdigest(),
                    texture_sha256=hashlib.sha256(png).hexdigest(),
                    width=3, height=1.6875, depth=3.5,
                    native_bounds_units=[[-24, 0, -27], [24, 27, 29]],
                    clips={'idle': 4.8, 'walk': 1.2},
                    walk_stride_blocks=.5, walk_speed_blocks_per_second=.5 / 1.2,
                    gameplay='Scene actor only; original island unlock and reward state remain separate.')
    return {
        ASSETS / 'island_actor_native/giant_turtle.json': json.dumps(model, separators=(',', ':'), allow_nan=False).encode(),
        ASSETS / 'textures/entity/island_actor_native/giant_turtle.png': png,
        ROOT / 'src/main/resources/data/stardewcraft/island_actor_sources/giant_turtle.json':
            (json.dumps(metadata, ensure_ascii=False, indent=2) + '\n').encode(),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, default=SOURCE)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    for path, data in exported(args.source).items():
        if args.check:
            if not path.is_file() or path.read_bytes() != data:
                raise ValueError(f'Native export differs: {path}')
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
    print('Giant turtle: 13 bones, 126 cuboid faces, approved PNG unchanged, idle/walk loops preserved')


if __name__ == '__main__':
    main()

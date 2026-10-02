#!/usr/bin/env python3
"""Import the authored greenhouse, ruins and interior resources deterministically."""

import argparse
import collections
import importlib.util
import json
from pathlib import Path


SOIL_REPLACEMENTS = {
    'stardewcraft:yellow_dirt': 'stardewcraft:dirt',
    'minecraft:dirt': 'stardewcraft:dirt',
    'minecraft:farmland': 'stardewcraft:farmland',
}


def normalize_soil_state(state):
    name, separator, properties = state.partition('[')
    return SOIL_REPLACEMENTS.get(name, name) + separator + properties


def normalize_soil_palette(intake, root):
    """Only change soil identities; retain palette indices and state properties."""
    replacements = {}
    if 'palette' in root:
        for state in intake.value(root, 'palette')[1]:
            old = intake.value(state, 'Name')
            new = SOIL_REPLACEMENTS.get(old, old)
            if old != new:
                state['Name'] = (8, new)
                replacements[old] = new
    else:
        schematic = intake.value(root, 'Schematic', root)
        blocks = intake.value(schematic, 'Blocks')
        palette = intake.value(blocks, 'Palette')
        normalized = {}
        for old, index in palette.items():
            new = normalize_soil_state(old)
            if new in normalized:
                # Merging indices would rewrite BlockData, not just the palette.
                raise ValueError(f'Soil normalization would merge palette indices: {new}')
            normalized[new] = index
            if old != new:
                replacements[old] = new
        palette.clear()
        palette.update(normalized)
    return replacements


def normalize_packaged_soil(intake, resource_dir):
    """Repair tracked templates in place without WorldEdit sources or re-importing."""
    paths = sorted((resource_dir / 'structures' / 'greenhouse').glob('*.schem'))
    for directory in ('structure', 'structures'):
        paths.extend(sorted((resource_dir / directory / 'farm_buildings').glob('greenhouse*.nbt')))
    if not paths:
        raise ValueError(f'{resource_dir}: no packaged greenhouse templates')
    changed = {}
    for path in paths:
        root = intake.read_nbt(path)
        replacements = normalize_soil_palette(intake, root)
        if normalize_soil_palette(intake, root):
            raise ValueError(f'{path}: soil normalization is not idempotent')
        if replacements:
            intake.write_nbt(path, root)
            if intake.read_nbt(path) != root:
                raise ValueError(f'{path}: soil normalization NBT round-trip failed')
            changed[str(path.relative_to(resource_dir))] = replacements
    return changed


def load_intake(script_dir):
    path = script_dir / 'import_farm_building_prefabs.py'
    spec = importlib.util.spec_from_file_location('farm_building_intake', path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def native_structure(intake, source, destination, manager=None):
    schematic = intake.Schematic(source)
    state_names = [schematic.palette[index] for index in sorted(schematic.palette)]
    manager_state = 'stardewcraft:greenhouse_manager[facing=south]'
    if manager is not None and manager_state not in state_names:
        state_names.append(manager_state)
    palette_index = {state: index for index, state in enumerate(state_names)}
    blocks = []
    removed = collections.Counter()
    for pos, state in schematic.entries():
        if manager is not None and list(pos) == manager:
            state = manager_state
        block = {'pos': intake.int_list(pos), 'state': (3, palette_index[state])}
        if pos in schematic.block_entities:
            entity = schematic.block_entities[pos]
            data = intake.value(entity, 'Data', {})
            removed.update(set(data) - intake.APPEARANCE_FIELDS)
            clean = {key: value for key, value in data.items()
                     if key in intake.APPEARANCE_FIELDS}
            clean['id'] = (8, intake.value(entity, 'Id'))
            block['nbt'] = (10, clean)
        blocks.append(block)
    root = {
        'DataVersion': (3, intake.value(schematic.root, 'DataVersion')),
        'size': intake.int_list(schematic.size),
        'palette': (9, (10, [intake.native_state(normalize_soil_state(state))
                            for state in state_names])),
        'blocks': (9, (10, blocks)),
        'entities': (9, (10, [])),
    }
    intake.write_nbt(destination, root)
    if intake.read_nbt(destination) != root:
        raise ValueError(f'{destination}: native NBT round-trip failed')
    return schematic, dict(sorted(removed.items()))


def replace_schematic_palette(intake, source, destination, replacements):
    root = intake.read_nbt(source)
    schematic = intake.value(root, 'Schematic', root)
    blocks = intake.value(schematic, 'Blocks')
    palette = intake.value(blocks, 'Palette')
    for old, new in replacements.items():
        if old not in palette:
            raise ValueError(f'{source}: missing palette state {old}')
        if new in palette:
            raise ValueError(f'{source}: replacement state already exists: {new}')
        palette[new] = palette.pop(old)
    normalize_soil_palette(intake, root)
    intake.write_nbt(destination, root)
    if intake.read_nbt(destination) != root:
        raise ValueError(f'{destination}: schematic NBT round-trip failed')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-dir', type=Path,
                        default=Path('run/config/worldedit/schematics'))
    parser.add_argument('--resource-dir', type=Path,
                        default=Path('src/main/resources/data/stardewcraft'))
    parser.add_argument('--report', type=Path,
                        default=Path('scripts/data/greenhouse_prefab_report.json'))
    parser.add_argument('--normalize-packaged-soil', action='store_true',
                        help='Normalize only tracked template palettes; do not read source-dir')
    args = parser.parse_args()

    script_dir = Path(__file__).resolve().parent
    intake = load_intake(script_dir)
    if args.normalize_packaged_soil:
        print(json.dumps(normalize_packaged_soil(intake, args.resource_dir),
                         ensure_ascii=False, indent=2))
        return
    manager = [12, 1, 11]
    structure_dir = args.resource_dir / 'structure' / 'farm_buildings'
    built, built_removed = native_structure(
        intake, args.source_dir / 'green_house.schem',
        structure_dir / 'greenhouse.nbt', manager)
    ruins, ruins_removed = native_structure(
        intake, args.source_dir / 'green_house_ruins.schem',
        structure_dir / 'greenhouse_ruins.nbt', manager)
    legacy_repaired, legacy_removed = native_structure(
        intake,
        args.resource_dir / 'structures' / 'greenhouse' / 'green_house_refurbished.schem',
        structure_dir / 'greenhouse_legacy_refurbished.nbt')

    definition = {
        'format_version': 1,
        'family': 'greenhouse',
        'authored_front': 'south',
        'anchor_convention': 'northwest corner vertex, embedded floor layer',
        'reservation_bounds_from_anchor': {
            'min': [0, 0, 0],
            'max_exclusive': list(built.size),
        },
        'tiers': [{
            'tier': 1,
            'structure': 'stardewcraft:farm_buildings/greenhouse',
            'size': list(built.size),
            'anchor': [0, 0, 0],
            'bounds_from_anchor': {
                'min': [0, 0, 0],
                'max_exclusive': list(built.size),
            },
            'manager': {'position': manager, 'facing': 'south'},
            'animal_spawn': [7, 1, 12],
        }],
    }
    definition_path = args.resource_dir / 'farm_building_prefabs' / 'greenhouse.json'
    definition_path.parent.mkdir(parents=True, exist_ok=True)
    definition_path.write_text(
        json.dumps(definition, ensure_ascii=False, indent=2) + '\n')

    packaged_schematics = args.resource_dir / 'structures' / 'greenhouse'
    replace_schematic_palette(
        intake, args.source_dir / 'green_house_ruins.schem',
        packaged_schematics / 'green_house_ruins.schem',
        {'minecraft:purple_wool': 'stardewcraft:greenhouse_manager[facing=south]'})
    replace_schematic_palette(
        intake, args.source_dir / 'green_house_interior.schem',
        packaged_schematics / 'green_house_interior.schem',
        {})
    normalize_packaged_soil(intake, args.resource_dir)

    report = {
        'built': {'size': list(built.size), 'manager': manager,
                  'removed_runtime_fields': built_removed},
        'ruins': {'size': list(ruins.size), 'manager': manager,
                  'removed_runtime_fields': ruins_removed},
        'legacy_repaired': {'size': list(legacy_repaired.size),
                            'removed_runtime_fields': legacy_removed},
        'interior_replacement': {
            'stardewcraft:yellow_dirt': 'stardewcraft:dirt',
            'minecraft:dirt': 'stardewcraft:dirt',
        },
        'soil_replacements': SOIL_REPLACEMENTS,
        'verification': ('native NBT and packaged schematic round-trips; '
                         'idempotent soil palette-only normalization preserves '
                         'indices, properties and all non-palette NBT'),
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print('Imported greenhouse, ruins and interior resources')


if __name__ == '__main__':
    main()

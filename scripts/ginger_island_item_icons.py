"""Shared item-model writer; re-exporting world models must retain authored icons."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / 'src/main/resources'
ICON_LEDGER = RESOURCES / 'data/stardewcraft/ginger_island/item_icons.json'

STATE_ROLES = {'appearance', 'interaction', 'progress', 'environment'}

# These retained models were reviewed individually for this 146-owner catalog.
# A new small object must be reviewed explicitly instead of being silently accepted
# merely because its current projection happens to fit a slot.
NATIVE_ITEM_GROUPS = {
    'building_block': {
        'ginger_field_office_canvas_floor', 'ginger_north_cliff_block',
        'ginger_north_cliff_inner_corner', 'ginger_north_cliff_outer_corner',
        'ginger_north_cliff_slab', 'ginger_north_cliff_stairs', 'ginger_farm_thatch_corner',
        'ginger_sand_duggy_hole', 'ginger_volcano_dwarf_slab', 'ginger_volcano_shortcut_hole',
        'ginger_volcano_vent_brick', 'ginger_volcano_floor', 'ginger_volcano_wall',
        'ginger_volcano_mud', 'ginger_caldera_floor', 'ginger_caldera_wall',
        'ginger_dwarf_brick', 'ginger_dwarf_capstone', 'ginger_volcano_bridge_deck',
        'ginger_volcano_bridge_rail', 'ginger_qi_room_tile', 'ginger_volcano_cooled_lava',
    },
    'ground_detail': {
        'ginger_marker_north', 'ginger_buried_pebble_ring', 'ginger_buried_round_stone_a',
        'ginger_island_sand_arc', 'ginger_island_sand_cross', 'ginger_island_sand_dots',
    },
    'small_plant': {
        'ginger_island_hint_white_flowers', 'ginger_island_counting_purple_flowers',
        'ginger_island_starfish_gold', 'ginger_island_sand_starfish',
    },
    'small_hardware': {
        'ginger_volcano_floor_switch', 'ginger_volcano_well_pipe', 'ginger_volcano_bolt',
        'ginger_volcano_broken_pillar', 'ginger_volcano_medium_gear',
        'ginger_volcano_rubble', 'ginger_volcano_small_gear',
    },
    'small_furniture': {
        'ginger_hut_container_small', 'ginger_slingshot_walnut_target',
        'ginger_field_office_fossil_backing', 'ginger_field_office_radio',
        'ginger_island_beach_broken_timber', 'ginger_gourmand_incense_burner',
        'ginger_resort_bar_drinks', 'ginger_resort_opening_notice',
        'ginger_pirate_square_stool', 'ginger_pirate_palm_planter_teal',
        'ginger_volcano_dwarf_display_table', 'ginger_volcano_dwarf_lantern', 'ginger_qi_dropbox',
    },
}
NATIVE_ITEM_REASONS = {
    'building_block': 'Native building block, slab, corner or surface: its visible block materials and shape fit the standard slot.',
    'ground_detail': 'One-cell ground mark or small stone: retain its authored three-dimensional/flat block appearance, including true appearance choices.',
    'small_plant': 'Small flower or beach-life cluster within one cell: retain the authored cutout model and its actual appearance variations.',
    'small_hardware': 'Small mechanism or stone debris: the actual cuboid geometry is within about one block and fits the standard slot.',
    'small_furniture': 'Small scene furnishing fits its native slot; the incense burner (1.3125 blocks high) and dwarf display table (1.21875) also fit without downscaling the world model.',
}


def item_state_models(asset, resource_root=RESOURCES):
    """Only authored appearance choices belong to selectable inventory models."""
    kind = asset['kind']
    if kind == 'state_decor':
        role = asset.get('state_role')
        assert role in STATE_ROLES, f'Missing or invalid state_role: {asset["id"]}'
        if role != 'appearance':
            return {}
        models = asset['state_models']
    elif kind in {'volcano_floor', 'caldera_floor'}:
        count = asset.get('variant_count')
        assert isinstance(count, int) and count > 0, f'Missing ground variant_count: {asset["id"]}'
        path = Path(resource_root) / 'assets/stardewcraft/blockstates' / (asset['id'] + '.json')
        variants = json.loads(path.read_text())['variants']
        models = {str(index): variants[f'variant={index}']['model'] for index in range(count)}
    else:
        return {}
    prop = asset.get('state_property', 'variant')
    values = list(models)
    if prop == 'variant':
        values.sort(key=int)
    elif prop == 'gem':
        values.sort(key=['empty', 'amethyst', 'aquamarine', 'emerald', 'ruby', 'topaz'].index)
    else:
        values.sort()
    return {value: models[value] for value in values}


def write_item_model(runtime, fallback_model, asset=None):
    if asset is None:
        catalog = RESOURCES / 'data/stardewcraft/ginger_island/assets.json'
        asset = next((entry for entry in json.loads(catalog.read_text())['blocks']
                      if entry['id'] == runtime), None) if catalog.exists() else None
    choices = item_state_models(asset) if asset is not None else {}
    target = RESOURCES / 'assets/stardewcraft/models/item' / (runtime + '.json')
    ledger = json.loads(ICON_LEDGER.read_text()) if ICON_LEDGER.exists() else {}
    icon = ledger.get('items', {}).get(runtime)
    if not icon:
        model = {'parent': fallback_model}
        if choices:
            model['overrides'] = [
                {'predicate': {'stardewcraft:ginger_island_variant': index / max(1, len(choices)-1)},
                 'model': world_model}
                for index, world_model in enumerate(choices.values())
            ]
    else:
        original = json.loads((RESOURCES / 'assets/stardewcraft/models' /
                               (fallback_model.split(':',1)[1] + '.json')).read_text())
        particle = original['textures']['particle']
        while particle.startswith('#'): particle = original['textures'][particle[1:]]
        model = {'parent': 'minecraft:item/generated', 'textures': {'layer0': icon['default'], 'particle': particle}}
        display = {'gui': {'scale': icon['gui_scale']}} if 'gui_scale' in icon else None
        if display: model['display'] = display
        overrides = []
        for index, value in enumerate(choices):
            texture = icon.get('variants', {}).get(value, icon['default'])
            submodel = f'stardewcraft:item/ginger_island/{runtime}_{value}'
            path = RESOURCES / 'assets/stardewcraft/models/item/ginger_island' / f'{runtime}_{value}.json'
            path.parent.mkdir(parents=True, exist_ok=True)
            variant_model = {'parent': 'minecraft:item/generated',
                             'textures': {'layer0': texture, 'particle': particle}}
            if display: variant_model['display'] = display
            path.write_text(json.dumps(variant_model, indent=2)+'\n')
            overrides.append({'predicate': {'stardewcraft:ginger_island_variant':
                                           index / max(1, len(choices)-1)}, 'model': submodel})
        if overrides: model['overrides'] = overrides
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(model, indent=2)+'\n')


def write_manual_icon_audit(assets, entries):
    """Record every owner, including reviewed native items, using shipped geometry."""
    from audit_ginger_island_item_bounds import ItemBoundsReader, audit_assets

    reader = ItemBoundsReader(RESOURCES)
    gui_rows = {row['id']: row for row in audit_assets(RESOURCES)}
    native_groups = {owner: group for group, owners in NATIVE_ITEM_GROUPS.items() for owner in owners}
    rows = {}
    for owner, asset in assets.items():
        entry = entries.get(owner)
        if entry:
            mode = 'original_sprite' if entry.get('artwork') == 'original' else 'authored_16px'
            reason = ('Original source sprite preserved exactly on a square transparent canvas, with its original proportions.'
                      if mode == 'original_sprite' else 'Previously authored native 16x16 inventory drawing; approved source pixels retained.')
        else:
            assert owner in native_groups, f'Inventory artwork/native disposition has not been reviewed: {owner}'
            mode = 'native_block'
            reason = NATIVE_ITEM_REASONS[native_groups[owner]]
        world_models = dict.fromkeys([asset['model'], *asset.get('state_models', {}).values(),
                                     *item_state_models(asset).values()])
        geometry = {model: reader.model(model)['geometry_bounds'] for model in world_models}
        dimensions = [max((bounds[axis][1] - bounds[axis][0]) / 16 for bounds in geometry.values())
                      for axis in range(3)]
        gui = gui_rows[owner]
        assert not gui['outside'], f'Inventory frame overflow: {owner}'
        rows[owner] = {
            'display_mode': mode,
            'reason': reason,
            'catalog_owner': asset.get('catalog_owner', owner),
            'canonical_state': asset.get('canonical_state', {}),
            'visible_catalog_entry': 'catalog_owner' not in asset,
            'state_role': asset.get('state_role'),
            'item_states': list(item_state_models(asset)),
            'world_dimensions_blocks': [round(value, 5) for value in dimensions],
            'world_geometry_bounds_pixels': geometry,
            'gui_models': [{key: variant[key] for key in ('model', 'mode', 'bounds', 'span', 'outside')}
                           for variant in gui['variants']],
        }
        if entry:
            rows[owner]['default_texture'] = entry['default']
            if mode == 'original_sprite':
                source_kind = entry.get('source_kind', 'item_menu')
                source_key = 'source_item' if source_kind == 'item_menu' else 'source_scene'
                rows[owner]['source_kind'] = source_kind
                rows[owner][source_key] = entry[source_key]
        else:
            rows[owner]['native_group'] = native_groups[owner]
    counts = {mode: sum(row['display_mode'] == mode for row in rows.values())
              for mode in ('authored_16px', 'original_sprite', 'native_block')}
    report = {
        'version': 1,
        'evidence': 'Shipped authored world geometry, inherited GUI transforms and owner/state metadata. These geometric checks are not a Minecraft screenshot or final visual acceptance.',
        'owner_count': len(rows), 'counts': counts,
        'original_item_owner_count': sum(row.get('source_kind') == 'item_menu' for row in rows.values()),
        'original_scene_owner_count': sum(row.get('source_kind') == 'scene_sprite' for row in rows.values()),
        'canonical_owner_count': len({row['catalog_owner'] for row in rows.values()}),
        'legacy_alias_count': sum(not row['visible_catalog_entry'] for row in rows.values()),
        'policy': 'Appearance states may select inventory models. Interaction, progression and environmental phases use only the ordinary base item. Ground includes its unbound natural/default display plus actual fixed variants.',
        'items': rows,
    }
    path = RESOURCES / 'data/stardewcraft/ginger_island/manual-icon-audit.json'
    path.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n')

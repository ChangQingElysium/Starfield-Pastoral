"""Reviewed object ownership, independent of the art directory's filenames.

An exported model is not necessarily a block or item. State models and assembly
parts stay available to their owner without becoming independent catalog entries.
"""

FAMILIES = {
    'gem_pedestal': ('gem', ['gem_pedestal', 'gem_pedestal_amethyst', 'gem_pedestal_aquamarine',
                            'gem_pedestal_emerald', 'gem_pedestal_ruby', 'gem_pedestal_topaz']),
    'gem_shrine_assembly': ('solved', ['gem_shrine_assembly', 'gem_shrine_solved']),
    'crystal_cave_statue': ('active', ['crystal_cave_statue', 'crystal_cave_statue_active']),
    'basic_window': ('night', ['basic_window', 'basic_window_night']),
    'volcano_spout_dry': ('flowing', ['volcano_spout_dry', 'volcano_spout']),
    'marker_north': ('variant', ['marker_north', 'marker_east', 'marker_south', 'marker_west']),
    'buried_round_stone_a': ('variant', ['buried_round_stone_a', 'buried_round_stone_b']),
    'island_hint_white_flowers': ('variant', ['island_hint_white_flowers', 'island_hint_yellow_flowers']),
    'island_starfish_gold': ('variant', ['island_starfish_gold', 'island_starfish_lilac', 'island_starfish_mint']),
    'resort_towel_green': ('variant', ['resort_towel_green', 'resort_towel_peach']),
    'resort_umbrella_coral': ('variant', ['resort_umbrella_coral', 'resort_umbrella_mint']),
    'pirate_palm_planter_teal': ('variant', ['pirate_palm_planter_teal', 'pirate_palm_planter_wine']),
    'volcano_canister_174': ('variant', ['volcano_canister_174', 'volcano_canister_175']),
    **{name: ('variant', [name, name + '_1', name + '_2']) for name in
       ['north_cliff_block', 'north_cliff_inner_corner', 'north_cliff_outer_corner',
        'north_cliff_slab', 'north_cliff_stairs']},
}

MODEL_ONLY = {
    **dict.fromkeys(['construction_chip_3d', 'construction_chunk_3d', 'construction_wood_3d'], '施工飞木效果'),
    'golden_walnut_3d': '奖励的三维外观；复用现有金核桃物品',
    **dict.fromkeys(['banana_altar_0', 'banana_altar_1', 'banana_altar_2'], '香蕉供台的编辑分段；整体供台拥有'),
    **dict.fromkeys(['gem_shrine_0', 'gem_shrine_1', 'gem_shrine_2', 'gem_shrine_3', 'gem_shrine_3_solved'], '整座宝石鸟神龛的编辑分段'),
    **dict.fromkeys(['bridge_0', 'bridge_1', 'bridge_2', 'bridge_3', 'bridge_broken_1', 'bridge_broken_2'], '挖掘场桥的装配分段'),
    **dict.fromkeys(['professor_fragment_0', 'professor_fragment_1', 'professor_fragment_2'], '救援巨石破坏碎片'),
    **dict.fromkeys(['captain_cabin_wall', 'captain_cabin_window', 'captain_cabin_window_night'], '旧船舱编辑源；认可的完整舱壳拥有'),
    **dict.fromkeys(['island_bright_longleaf_left', 'island_bright_longleaf_right'], '完整长叶植株的编辑分段'),
    **dict.fromkeys(['dwarvish_sentry_death', 'dwarvish_sentry_thruster'], '怪物死亡／推进器效果'),
    'pirate_dart': '小游戏三维投射物',
    'gourmand_cane': '田间青蛙 cane 组的独立编辑提取；完整角色已包含手杖',
    'qi_cat': '齐先生触发的临时效果',
    'island_caldera_floating_face': '完美度山顶临时角色',
    'volcano_dragon_tooth': '采集物的三维外观；复用现有龙牙物品',
    **dict.fromkeys(['field_office_fossil_large', 'field_office_fossil_snake',
                    'field_office_fossil_bat', 'field_office_fossil_frog'], '完整捐赠标本；由同一展台状态装配'),
}

ASSEMBLIES = {
    'dig_site_bridge': {'property': 'repaired', 'states': {
        'false': [('bridge_0', [0, 0, 0]), ('bridge_broken_1', [16, 0, 0]),
                  ('bridge_broken_2', [32, 0, 0]), ('bridge_3', [48, 0, 0])],
        'true': [('bridge_0', [0, 0, 0]), ('bridge_1', [16, 0, 0]),
                 ('bridge_2', [32, 0, 0]), ('bridge_3', [48, 0, 0])]}},
    'field_office_fossil_display': {'property': 'variant', 'states': {
        '0': [('field_office_fossil_display', [0, 0, 0])],
        '1': [('field_office_fossil_display', [0, 0, 0]), ('field_office_fossil_large', [1.4, 26, 0])],
        '2': [('field_office_fossil_display', [0, 0, 0]), ('field_office_fossil_snake', [1.75, 16, 0])]}},
    'field_office_fossil_backing': {'property': 'variant', 'states': {
        '0': [('field_office_fossil_backing', [0, 0, 0])],
        '1': [('field_office_fossil_backing', [0, 0, 0]), ('field_office_fossil_bat', [0, 0, 0])],
        '2': [('field_office_fossil_backing', [0, 0, 0]), ('field_office_fossil_frog', [0, .35, 0])]}},
}

STATE_VALUES = {'gem': ['empty', 'amethyst', 'aquamarine', 'emerald', 'ruby', 'topaz']}

# Exact identities that really are furniture; scene buildings do not fall back here.
FURNITURE = {
    'hut_container_small', 'hut_container_tall', 'hut_grass_bedding', 'field_office_radio',
    'field_office_survey_board', 'field_office_fossil_display', 'field_office_fossil_backing',
    'professor_work_desk', 'stove_fireplace', 'tropical_bed', 'pirate_square_stool',
    'pirate_round_table', 'pirate_treasure_table', 'tropical_chair', 'tropical_tv',
    'squirrel_figurine', 'lifesaver', 'basic_window', 'jungle_torch', 'resort_beach_chair',
    'island_torch_unlit', 'crystal_cave_brazier', 'gourmand_incense_burner',
    'resort_bar_drinks', 'resort_carved_bar_counter', 'resort_opening_notice',
    'resort_umbrella_coral', 'pirate_bar_assembly', 'pirate_barrel_dark', 'pirate_cannon',
    'pirate_dartboard', 'pirate_palm_planter_teal', 'volcano_dwarf_cabinet',
    'volcano_dwarf_counter', 'volcano_dwarf_display_table', 'volcano_dwarf_lantern',
    'qi_computer_desk',
}
PAINTINGS = {'physics_101', 'foliage_print', 'palm_wall_ornament_left',
             'palm_wall_ornament_right', 'volcano_photo', 'pirate_skull_flag'}
CARPETS = {'bamboo_mat', 'burlap_rug', 'oceanic_rug', 'pirate_purple_rug',
           'gourmand_woven_mat', 'resort_towel_green'}
PLANTS = {'golden_walnut_bush_rework', 'island_bright_longleaf_assembly',
          'island_broadleaf_rosette', 'island_curled_fern'}
FLOWERS = {'island_hint_white_flowers', 'island_counting_purple_flowers'}
AQUATIC = {'island_beached_seaweed', 'island_shell_clam', 'island_shell_cockle',
           'island_starfish_gold', 'island_sand_starfish'}
ROCKS = {'professor_boulder', 'dig_ammonite_relief', 'dig_fossil_fragments',
         'buried_pebble_ring', 'buried_round_stone_a', 'mermaid_performance_rock',
         'volcano_bone_spike', 'volcano_dragon_skull', 'volcano_femur_in_rock', 'volcano_rib_arch',
         'volcano_rubble'}
GROUND = {'volcano_mud', 'sand_duggy_hole', 'island_sand_arc', 'island_sand_cross', 'island_sand_dots'}
UTILITIES = {'hopper', 'qi_challenge_board', 'qi_dropbox', 'island_flute_block'}
BUILDINGS = {
    'parrot_perch', 'banana_altar_assembly', 'gem_pedestal', 'gem_shrine_assembly',
    'marker_north', 'slingshot_walnut_target', 'professor_field_tent_assembly', 'field_office_canvas_floor',
    'golden_parrot_perch', 'farm_return_obelisk', 'island_beach_broken_timber',
    'island_beach_driftwood_pile', 'farm_ruin_boarding', 'farm_thatch_corner',
    'farm_thatch_eave', 'farm_thatch_ridge', 'farm_thatch_slope', 'island_parrot_platform',
    'birdie_hut', 'captain_cabin_shell', 'crystal_cave_statue', 'island_shipwreck',
    'resort_changing_entry', 'resort_leaf_wrapped_column', 'resort_ruined_column',
    'resort_tile_roof_high', 'resort_tile_roof_low', 'resort_tile_roof_mid',
    'resort_tile_roof_ridge', 'resort_tile_roof_wide_crown', 'pirate_cove_rowboat',
    'caldera_monument', 'volcano_canister_174', 'volcano_dwarf_slab', 'volcano_floor_switch',
    'volcano_parrot_perch', 'volcano_shortcut_hole', 'volcano_spout_dry', 'volcano_well_pipe',
    'volcano_big_gear', 'volcano_bolt', 'volcano_broken_pillar', 'volcano_floor_hatch',
    'volcano_medium_gear', 'volcano_small_gear', 'volcano_vent_brick', 'dwarf_brick',
    'dwarf_capstone', 'volcano_bridge_deck', 'volcano_bridge_rail', 'qi_room_tile', 'dig_site_bridge',
}

def item_type(name, kind):
    if kind in ['kitchen', 'forge', 'heavy_tapper', 'ostrich_incubator'] or name in UTILITIES: return 'utility'
    if name == 'volcano_magma_cap': return 'forage'
    if name in GROUND: return 'natural_ground'
    if name in PLANTS: return 'natural_grass'
    if name in FLOWERS: return 'natural_flower'
    if name in AQUATIC: return 'natural_aquatic'
    if name in ROCKS or name.startswith('north_cliff_') or name in ['volcano_floor', 'volcano_wall', 'caldera_floor', 'caldera_wall', 'volcano_cooled_lava']: return 'natural_rock'
    if name in FURNITURE: return 'furniture'
    if name in PAINTINGS: return 'furniture_painting'
    if name in CARPETS: return 'carpet'
    if name in BUILDINGS: return 'building'
    raise ValueError('Review an explicit catalog category before registration: ' + name)

# These translations are taken from the shipped original locale strings.
SOURCE_NAMES = {
    'tropical_bed': ('Furniture', 'TropicalDoubleBed'), 'tropical_tv': ('Furniture', 'TropicalTV'),
    'tropical_chair': ('Furniture', 'TropicalChair'), 'jungle_torch': ('Furniture', 'JungleTorch'),
    'basic_window': ('Furniture', 'BasicWindow'), 'physics_101': ('Furniture', 'Physics101'),
    'squirrel_figurine': ('Furniture', 'SquirrelFigurine'), 'bamboo_mat': ('Furniture', 'BambooMat'),
    'burlap_rug': ('Furniture', 'BurlapRug'), 'oceanic_rug': ('Furniture', 'OceanicRug'),
    'foliage_print': ('Furniture', 'FoliagePrint'), 'lifesaver': ('Furniture', 'Lifesaver'),
    'volcano_photo': ('Furniture', 'VolcanoPhoto'),
    'heavy_tapper': ('BigCraftables', 'HeavyTapper_Name'), 'ostrich_incubator_empty': ('BigCraftables', 'OstrichIncubator_Name'),
    'hopper': ('BigCraftables', 'Hopper_Name'), 'volcano_magma_cap': ('Objects', 'MagmaCap_Name'),
    'island_flute_block': ('Objects', 'FluteBlock_Name'),
}

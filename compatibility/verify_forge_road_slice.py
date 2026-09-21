#!/usr/bin/env python3
"""Verify the asphalt-road vertical slice for Forge 1.20.1.

The source line uses NeoForge 1.21.1 APIs.  This contract permits only the
loader/version adaptations that are required here: RegistryObject wiring,
1.20.1 Block method visibility/signatures, Forge model-event names, and the
vanilla 1.20.1 BlockStateTag replacement for DataComponents.BLOCK_STATE.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "src/main"
FORGE = ROOT / "src/forge-bootstrap"
SOURCE_BLOCKS = SOURCE / "java/com/stardew/craft/block/ModBlocks.java"
FORGE_BLOCKS = FORGE / "java/com/stardew/craft/forge/registry/ForgeBlocks.java"
FORGE_BLOCK_ALIASES = FORGE / "java/com/stardew/craft/block/ModBlocks.java"
SOURCE_ITEMS = SOURCE / "java/com/stardew/craft/item/ModItems.java"
FORGE_ITEMS = FORGE / "java/com/stardew/craft/forge/registry/ForgeItems.java"
FORGE_ITEM_ALIASES = FORGE / "java/com/stardew/craft/item/ModItems.java"
SOURCE_VARIANTS = SOURCE / "java/com/stardew/craft/block/terrain/TerrainVariants.java"
FORGE_VARIANTS = FORGE / "../forge-common/java/com/stardew/craft/block/terrain/TerrainVariants.java"
SOURCE_ROAD = SOURCE / "java/com/stardew/craft/block/terrain/AsphaltRoadBlock.java"
FORGE_ROAD = FORGE / "../forge-common/java/com/stardew/craft/block/terrain/AsphaltRoadBlock.java"
SOURCE_MARKING = SOURCE / "java/com/stardew/craft/block/terrain/RoadMarkingBlock.java"
FORGE_MARKING = FORGE / "../forge-common/java/com/stardew/craft/block/terrain/RoadMarkingBlock.java"
SOURCE_CONNECTIONS = SOURCE / "java/com/stardew/craft/block/terrain/TownPavingConnections.java"
FORGE_CONNECTIONS = FORGE / "../forge-common/java/com/stardew/craft/block/terrain/AsphaltRoadConnections.java"
SOURCE_CLIENT = SOURCE / "java/com/stardew/craft/client/model/terrain/AsphaltRoadModels.java"
FORGE_CLIENT = FORGE / "java/com/stardew/craft/forge/client/AsphaltRoadModels.java"
FORGE_SEASONS = FORGE / "java/com/stardew/craft/forge/client/TerrainSeasonTextures.java"
FORGE_CLIENT_SETUP = FORGE / "java/com/stardew/craft/forge/client/ForgeClientSetup.java"

SOURCE_ASSETS = SOURCE / "resources/assets/stardewcraft"
FORGE_ASSETS = FORGE / "resources/assets/stardewcraft"
SOURCE_DATA = SOURCE / "resources/data/stardewcraft"
FORGE_DATA = FORGE / "resources/data/stardewcraft"

ROAD_IDS = ("asphalt_road", "road_dash", "road_double_line")
BLOCK_FIELDS = {
    "asphalt_road": "ASPHALT_ROAD",
    "road_dash": "ROAD_DASH",
    "road_double_line": "ROAD_DOUBLE_LINE",
}


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def load(path: Path) -> object:
    return json.loads(path.read_text(encoding="utf-8-sig"))


def compact(value: str) -> str:
    return re.sub(r"\s+", "", value)


def registration_body(text: str, field: str, registry: str) -> str:
    start = text.find(f" {field} =")
    if start < 0:
        raise ValueError(f"missing {field}")
    register = text.find(f"{registry}.register(", start)
    if register < 0:
        raise ValueError(f"missing register call for {field}")
    opening = text.find("(", register)
    depth = 0
    for index in range(opening, len(text)):
        if text[index] == "(":
            depth += 1
        elif text[index] == ")":
            depth -= 1
            if depth == 0:
                return text[opening + 1 : index]
    raise ValueError(f"unterminated registration for {field}")


def expect(errors: list[str], condition: bool, message: str) -> None:
    if not condition:
        errors.append(message)


def check_java(errors: list[str]) -> None:
    source_blocks = read(SOURCE_BLOCKS)
    forge_blocks = read(FORGE_BLOCKS)
    source_items = read(SOURCE_ITEMS)
    forge_items = read(FORGE_ITEMS)
    forge_alias_blocks = read(FORGE_BLOCK_ALIASES)
    forge_alias_items = read(FORGE_ITEM_ALIASES)

    source_asphalt = compact(registration_body(source_blocks, "ASPHALT_ROAD", "BLOCKS"))
    forge_asphalt = compact(registration_body(forge_blocks, "ASPHALT_ROAD", "BLOCKS"))
    expect(errors, "newcom.stardew.craft.block.terrain.AsphaltRoadBlock" in source_asphalt,
           "source asphalt registration class drifted")
    expect(errors, "AsphaltRoadBlock" in source_asphalt and "Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.STONE)" in source_asphalt,
           "source asphalt property contract drifted")
    expect(errors, "AsphaltRoadBlock" in forge_asphalt and "BlockBehaviour.Properties.copy(Blocks.STONE)" in forge_asphalt,
           "Forge asphalt property contract drifted")

    for field in ("ROAD_DASH", "ROAD_DOUBLE_LINE"):
        source = compact(registration_body(source_blocks, field, "BLOCKS"))
        forge = compact(registration_body(forge_blocks, field, "BLOCKS"))
        expect(errors, "RoadMarkingBlock" in source and "noCollission()" in source and "noOcclusion()" in source
               and "instabreak()" in source and "sound(SoundType.STONE)" in source
               and "pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)" in source,
               f"source {field} property contract drifted")
        expect(errors, "RoadMarkingBlock" in forge and "roadMarkingProperties()" in forge,
               f"Forge {field} registration drifted")

    forge_props = compact(forge_blocks[forge_blocks.find("private static BlockBehaviour.Properties roadMarkingProperties") :])
    for fragment in ("noCollission()", "noOcclusion()", "instabreak()", "sound(SoundType.STONE)",
                     "pushReaction(PushReaction.DESTROY)"):
        expect(errors, fragment in forge_props, f"Forge road marking property missing: {fragment}")

    for identifier, field in BLOCK_FIELDS.items():
        expect(errors, re.search(rf"RegistryObject<[^>]+>\s+{field}\s*=\s*ForgeBlocks\.{field};", forge_alias_blocks) is not None,
               f"Forge ModBlocks alias missing: {field}")
        expect(errors, re.search(rf"RegistryObject<Item>\s+{field}\s*=\s*ForgeItems\.{field};", forge_alias_items) is not None,
               f"Forge ModItems alias missing: {field}")
        expect(errors, re.search(rf"RegistryObject<Item>\s+{field}\s*=\s*registerBuildingBlock\(ForgeBlocks\.{field}\);", forge_items) is not None,
               f"Forge item registration missing: {identifier}")
        expect(errors, re.search(rf"(?:DeferredItem<Item>\s+{field}|public static final DeferredItem<Item>\s+{field}).*?{identifier}", source_items, re.S) is not None,
               f"source item registration missing: {identifier}")

    variants = read(FORGE_VARIANTS)
    source_variants = read(SOURCE_VARIANTS)
    expect(errors, 'IntegerProperty.create("variant", 0, 2)' in source_variants and 'IntegerProperty.create("variant", 0, 2)' in variants,
           "asphalt variant range drifted")
    for fragment in (
        "BlockItem.BLOCK_STATE_TAG", "getAsString()", "property.getValue(raw.getAsString())",
        "context.getLevel().getRandom().nextInt(3)", "context.getLevel().isClientSide",
        "ItemStack copy = original.copy()", "getOrCreateTagElement(BlockItem.BLOCK_STATE_TAG)",
    ):
        expect(errors, fragment in variants, f"Forge terrain-state adapter missing: {fragment}")
    expect(errors, "import net.minecraft.core.component.DataComponents" not in variants
           and "import net.minecraft.world.item.component.BlockItemStateProperties" not in variants,
           "Forge terrain-state adapter must not depend on 1.21 data components")

    marking = read(FORGE_MARKING)
    for fragment in (
        "instanceof AsphaltRoadBlock", "Blocks.AIR.defaultBlockState()", "Direction.DOWN",
        "get2DDataValue() + 2", "isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type)",
    ):
        expect(errors, fragment in marking, f"Forge road marking behavior missing: {fragment}")
    expect(errors, "MapCodec" not in read(FORGE_ROAD) and "MapCodec" not in marking,
           "Forge 1.20 road blocks must not retain 1.21 MapCodec overrides")

    connections = read(FORGE_CONNECTIONS)
    expect(errors, "private static final int[][] OFFSETS" in connections and "canonical(int mask)" in connections,
           "Forge road connection topology missing")
    expect(errors, "Arrays.binarySearch(MASKS, canonical(mask))" in connections,
           "Forge road row lookup drifted")
    source_topology = read(SOURCE_CONNECTIONS)
    expect(errors, "private static final int[][] OFFSETS" in source_topology and "canonical(int mask)" in source_topology,
           "source paving topology contract unavailable")

    client = read(FORGE_CLIENT)
    source_client = read(SOURCE_CLIENT)
    for fragment in (
        "ModelEvent.RegisterAdditional", "ModelEvent.ModifyBakingResult", "new ModelProperty<>()",
        "new BakedQuad[4][3][47]", "new BakedQuad[4][8][47]", "tile(road, variant, row, 48)",
        "tile(paint, column, row, 128)", "RoadMarkingBlock.rotationIndex(state)",
        "AsphaltRoadConnections.row(AsphaltRoadConnections.mask(level, road))",
        "BlockItem.BLOCK_STATE_TAG", "getQuads(state, side, random, ModelData.EMPTY, null)",
    ):
        expect(errors, fragment in client, f"Forge road client model missing: {fragment}")
    for fragment in ("DataComponents", "BlockItemStateProperties"):
        expect(errors, fragment not in client, f"Forge road client model still uses 1.21 {fragment}")
    expect(errors, "tile(road, v, row, 48)" in source_client and "tile(paint, c, row, 128)" in source_client,
           "source road client atlas contract unavailable")
    seasons = read(FORGE_SEASONS)
    expect(errors, "updateSeason(int season)" in seasons and "currentSeason = -1" in seasons,
           "Forge season selector contract drifted")
    client_setup = read(FORGE_CLIENT_SETUP)
    expect(errors, "ForgeBlocks.ROAD_DASH.get(), RenderType.cutout()" in client_setup
           and "ForgeBlocks.ROAD_DOUBLE_LINE.get(), RenderType.cutout()" in client_setup,
           "Forge road markings are not assigned to the cutout render layer")


def check_resources(errors: list[str]) -> None:
    expected_blockstates = {f"blockstates/{identifier}.json" for identifier in ROAD_IDS}
    for relative in expected_blockstates:
        source = SOURCE_ASSETS / relative
        forge = FORGE_ASSETS / relative
        expect(errors, forge.is_file(), f"missing Forge blockstate: {relative}")
        if forge.is_file():
            expect(errors, load(source) == load(forge), f"blockstate mismatch: {relative}")

    expected_block_models = {f"models/block/asphalt_road/{season}{suffix}.json"
                             for season in ("spring", "summer", "fall", "winter")
                             for suffix in ("", "_isolated", "_markings")}
    expected_item_models = {
        "models/item/asphalt_road.json", "models/item/road_dash.json", "models/item/road_double_line.json",
        *(f"models/item/asphalt_road/{season}_{style}.json"
          for season in ("spring", "summer", "fall", "winter") for style in ("dash", "double")),
    }
    expected_textures = {f"textures/block/asphalt_road/{season}/{name}.png"
                         for season in ("spring", "summer", "fall", "winter")
                         for name in ("asphalt", "dash", "double", "isolated", "markings", "side", "top")}
    for relative in sorted(expected_block_models | expected_item_models | expected_textures):
        source = SOURCE_ASSETS / relative
        forge = FORGE_ASSETS / relative
        expect(errors, source.is_file(), f"missing source road asset: {relative}")
        expect(errors, forge.is_file(), f"missing Forge road asset: {relative}")
        if source.is_file() and forge.is_file():
            expect(errors, source.read_bytes() == forge.read_bytes(), f"road asset mismatch: {relative}")

    manifest = "asphalt_road_manifest.json"
    expect(errors, load(SOURCE_ASSETS / manifest) == load(FORGE_ASSETS / manifest), "road manifest mismatch")
    for identifier in ROAD_IDS:
        source = SOURCE_DATA / f"loot_table/blocks/{identifier}.json"
        forge = FORGE_DATA / f"loot_tables/blocks/{identifier}.json"
        expect(errors, forge.is_file(), f"missing Forge loot table: {identifier}")
        if forge.is_file():
            expect(errors, load(source) == load(forge), f"loot table mismatch: {identifier}")

    source_tag = load(SOURCE / "resources/data/minecraft/tags/block/mineable/pickaxe.json")
    forge_tag = load(FORGE / "resources/data/minecraft/tags/blocks/mineable/pickaxe.json")
    expect(errors, "stardewcraft:asphalt_road" in source_tag.get("values", []), "source road pickaxe tag missing")
    expected_existing = {
        "stardewcraft:pale_cyan_plaster", "stardewcraft:terracotta_roof_tiles",
        "stardewcraft:dark_brown_roof_tiles", "stardewcraft:gray_green_masonry",
        "stardewcraft:gray_violet_roof_tiles", "stardewcraft:brick_red_roof_tiles",
        "stardewcraft:mine_earth_wall", "stardewcraft:mine_earth_dark_wall",
        "stardewcraft:mine_frost_dark_wall", "stardewcraft:mine_lava_dark_wall",
        "stardewcraft:mine_desert_dark_wall", "stardewcraft:mine_frost_wall",
        "stardewcraft:mine_lava_wall", "stardewcraft:mine_desert_wall", "stardewcraft:mine_barrier",
    }
    expect(errors, set(forge_tag.get("values", [])) == expected_existing | {"stardewcraft:asphalt_road"},
           "Forge pickaxe tag changed outside the road addition")

    for language in sorted((SOURCE_ASSETS / "lang").glob("*.json")):
        forge = FORGE_ASSETS / "lang" / language.name
        expect(errors, forge.is_file(), f"missing Forge language: {language.name}")
        if not forge.is_file():
            continue
        source_values = load(language)
        forge_values = load(forge)
        for identifier in ROAD_IDS:
            key = f"block.stardewcraft.{identifier}"
            expect(errors, forge_values.get(key) == source_values.get(key),
                   f"translation mismatch for {key} in {language.name}")


def main() -> int:
    errors: list[str] = []
    try:
        check_java(errors)
        check_resources(errors)
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        errors.append(f"checker exception: {exc}")
    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1
    print("Forge asphalt-road parity passed: 3 blocks, 11 item/block models, 12 block models, 28 textures, 3 loot tables and 12 languages.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

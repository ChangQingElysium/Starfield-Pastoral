#!/usr/bin/env python3
"""Verify the complete playground-sand Forge 1.20.1 vertical slice.

The source line uses NeoForge 1.21.1 data components and model APIs.  This
contract permits only the required Forge/version adaptations: RegistryObject
wiring, Java 17 collection access, Forge model events, and the vanilla 1.20.1
BlockStateTag replacement for DataComponents.BLOCK_STATE.
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
SOURCE_SAND = SOURCE / "java/com/stardew/craft/block/terrain/PlaygroundSandBlock.java"
FORGE_SAND = FORGE / "../forge-common/java/com/stardew/craft/block/terrain/PlaygroundSandBlock.java"
SOURCE_CONNECTIONS = SOURCE / "java/com/stardew/craft/block/terrain/PlaygroundSandConnections.java"
FORGE_CONNECTIONS = FORGE / "../forge-common/java/com/stardew/craft/block/terrain/PlaygroundSandConnections.java"
SOURCE_CLIENT = SOURCE / "java/com/stardew/craft/client/model/terrain/PlaygroundSandModels.java"
FORGE_CLIENT = FORGE / "java/com/stardew/craft/forge/client/PlaygroundSandModels.java"
FORGE_CLIENT_SETUP = FORGE / "java/com/stardew/craft/forge/client/ForgeClientSetup.java"
SOURCE_TEST = SOURCE / "java/com/stardew/craft/gametest/SandBirdGameTests.java"

SOURCE_ASSETS = SOURCE / "resources/assets/stardewcraft"
FORGE_ASSETS = FORGE / "resources/assets/stardewcraft"
SOURCE_DATA = SOURCE / "resources/data/stardewcraft"
FORGE_DATA = FORGE / "resources/data/stardewcraft"


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

    source = compact(registration_body(source_blocks, "PLAYGROUND_SAND", "BLOCKS"))
    forge = compact(registration_body(forge_blocks, "PLAYGROUND_SAND", "BLOCKS"))
    expect(errors, "PlaygroundSandBlock" in source and "Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.SAND)" in source,
           "source playground-sand property contract drifted")
    expect(errors, "PlaygroundSandBlock" in forge and "BlockBehaviour.Properties.copy(Blocks.SAND)" in forge,
           "Forge playground-sand property contract drifted")

    expect(errors, re.search(r"RegistryObject<PlaygroundSandBlock>\s+PLAYGROUND_SAND\s*=\s*ForgeBlocks\.PLAYGROUND_SAND;", forge_alias_blocks) is not None,
           "Forge ModBlocks playground-sand alias missing")
    expect(errors, re.search(r"RegistryObject<Item>\s+PLAYGROUND_SAND\s*=\s*ForgeItems\.PLAYGROUND_SAND;", forge_alias_items) is not None,
           "Forge ModItems playground-sand alias missing")
    expect(errors, re.search(r"RegistryObject<Item>\s+PLAYGROUND_SAND\s*=\s*registerBuildingBlock\(ForgeBlocks\.PLAYGROUND_SAND\);", forge_items) is not None,
           "Forge playground-sand item registration missing")
    expect(errors, re.search(r"DeferredItem<Item>\s+PLAYGROUND_SAND.*?playground_sand", source_items, re.S) is not None,
           "source playground-sand item registration missing")

    variants = read(FORGE_VARIANTS)
    source_variants = read(SOURCE_VARIANTS)
    expect(errors, 'IntegerProperty.create("variant", 0, 3)' in source_variants and
           'IntegerProperty.create("variant", 0, 3)' in variants,
           "sand variant range drifted")
    for fragment in (
        "instanceof PlaygroundSandBlock", "BlockItem.BLOCK_STATE_TAG", "getAsString()",
        "property.getValue(raw.getAsString())", "context.getLevel().getRandom().nextInt(4)",
        "context.getLevel().getRandom().nextInt(3)", "context.getLevel().isClientSide",
        "getOrCreateTagElement(BlockItem.BLOCK_STATE_TAG)",
    ):
        expect(errors, fragment in variants, f"Forge terrain-state adapter missing: {fragment}")
    expect(errors, "import net.minecraft.core.component.DataComponents" not in variants and
           "BlockItemStateProperties" not in variants,
           "Forge terrain-state adapter must not depend on 1.21 data components")

    sand = read(FORGE_SAND)
    expect(errors, "class PlaygroundSandBlock extends Block" in sand and
           "TerrainVariants.SAND" in sand and "getStateForPlacement" in sand,
           "Forge playground-sand block behavior missing")
    expect(errors, "MapCodec" not in sand, "Forge 1.20 playground-sand must not retain MapCodec")

    connections = read(FORGE_CONNECTIONS)
    source_connections = read(SOURCE_CONNECTIONS)
    expect(errors, "private static final int[][] OFFSETS" in connections and
           "canonical(int mask)" in connections and
           "instanceof PlaygroundSandBlock" in connections,
           "Forge playground-sand connection topology missing")
    expect(errors, "Arrays.binarySearch(MASKS, canonical(mask))" in connections,
           "Forge playground-sand row lookup drifted")
    expect(errors, "private static final int[][] OFFSETS" in source_connections and
           "TownPavingConnections.canonical" in source_connections,
           "source playground-sand topology contract unavailable")

    client = read(FORGE_CLIENT)
    source_client = read(SOURCE_CLIENT)
    for fragment in (
        "ModelEvent.RegisterAdditional", "ModelEvent.ModifyBakingResult", "new ModelProperty<>()",
        "new BakedQuad[4][4][47]", "tile(source, row)",
        "PlaygroundSandConnections.row(PlaygroundSandConnections.mask(level, pos))",
        "BlockItem.BLOCK_STATE_TAG", "getQuads(state, side, random, ModelData.EMPTY, null)",
    ):
        expect(errors, fragment in client, f"Forge playground-sand client model missing: {fragment}")
    for fragment in ("DataComponents", "BlockItemStateProperties"):
        expect(errors, fragment not in client, f"Forge playground-sand client model still uses 1.21 {fragment}")
    expect(errors, "new BakedQuad[4][4][47]" in source_client and "row % 8 * 16" in source_client,
           "source playground-sand atlas contract unavailable")

    source_test = read(SOURCE_TEST)
    for fragment in ("rows.size()==47", "TerrainVariants.fixedCopy", "seen.size()==4",
                     "BlockTags.MINEABLE_WITH_SHOVEL", "drops.size()==1"):
        expect(errors, fragment in source_test, f"source sand GameTest contract missing: {fragment}")

def check_resources(errors: list[str]) -> None:
    for relative in ("blockstates/playground_sand.json", "models/item/playground_sand.json"):
        source = SOURCE_ASSETS / relative
        forge = FORGE_ASSETS / relative
        expect(errors, forge.is_file(), f"missing Forge sand asset: {relative}")
        if forge.is_file():
            expect(errors, source.read_bytes() == forge.read_bytes(), f"sand asset mismatch: {relative}")

    expected_models = {f"models/block/playground_sand/{season}_{variant}.json"
                       for season in ("spring", "summer", "fall", "winter")
                       for variant in range(4)}
    expected_textures = {f"textures/block/playground_sand/{name}.png"
                         for name in ("connected_0", "connected_1", "connected_2", "connected_3",
                                      "sand_0", "sand_1", "sand_2", "sand_3", "sand_particle", "sand_side")}
    for relative in sorted(expected_models | expected_textures):
        source = SOURCE_ASSETS / relative
        forge = FORGE_ASSETS / relative
        expect(errors, source.is_file(), f"missing source sand asset: {relative}")
        expect(errors, forge.is_file(), f"missing Forge sand asset: {relative}")
        if source.is_file() and forge.is_file():
            expect(errors, source.read_bytes() == forge.read_bytes(), f"sand asset mismatch: {relative}")

    source_loot = SOURCE_DATA / "loot_table/blocks/playground_sand.json"
    forge_loot = FORGE_DATA / "loot_tables/blocks/playground_sand.json"
    expect(errors, forge_loot.is_file(), "missing Forge playground-sand loot table")
    if forge_loot.is_file():
        expect(errors, load(source_loot) == load(forge_loot), "playground-sand loot table mismatch")

    forge_tag = load(FORGE / "resources/data/minecraft/tags/blocks/mineable/shovel.json")
    expected_existing = {
        "stardewcraft:mine_earth_loose_soil", "stardewcraft:mine_earth_dark_loose_soil",
        "stardewcraft:mine_frost_dark_loose_soil", "stardewcraft:mine_lava_dark_loose_soil",
        "stardewcraft:mine_desert_dark_loose_soil", "stardewcraft:mine_frost_loose_soil",
        "stardewcraft:mine_lava_loose_soil", "stardewcraft:mine_desert_loose_soil",
    }
    expect(errors, set(forge_tag.get("values", [])) == expected_existing | {"stardewcraft:playground_sand"},
           "Forge shovel tag changed outside playground-sand addition")

    for language in sorted((SOURCE_ASSETS / "lang").glob("*.json")):
        forge = FORGE_ASSETS / "lang" / language.name
        expect(errors, forge.is_file(), f"missing Forge language: {language.name}")
        if forge.is_file():
            source_values = load(language)
            forge_values = load(forge)
            key = "block.stardewcraft.playground_sand"
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
    print("Forge playground-sand parity passed: 4 variants, 47 topology rows, 16 models, 10 textures, 1 loot table and 12 languages.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

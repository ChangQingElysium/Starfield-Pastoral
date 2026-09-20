#!/usr/bin/env python3
"""Verify the Forge mine backdrop block slice against the 1.21.1 source."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE_BLOCKS = ROOT / "src/main/java/com/stardew/craft/block/ModBlocks.java"
FORGE_BLOCKS = ROOT / "src/forge-bootstrap/java/com/stardew/craft/forge/registry/ForgeBlocks.java"
FORGE_BLOCK_ALIASES = ROOT / "src/forge-bootstrap/java/com/stardew/craft/block/ModBlocks.java"
SOURCE_ITEMS = ROOT / "src/main/java/com/stardew/craft/item/ModItems.java"
FORGE_ITEMS = ROOT / "src/forge-bootstrap/java/com/stardew/craft/forge/registry/ForgeItems.java"
FORGE_ITEM_ALIASES = ROOT / "src/forge-bootstrap/java/com/stardew/craft/item/ModItems.java"
SOURCE_ASSETS = ROOT / "src/main/resources/assets/stardewcraft"
FORGE_ASSETS = ROOT / "src/forge-bootstrap/resources/assets/stardewcraft"
SOURCE_DATA = ROOT / "src/main/resources/data"
FORGE_DATA = ROOT / "src/forge-bootstrap/resources/data"


MINE_BLOCKS = {
    "mine_earth_loose_soil": ("MINE_EARTH_LOOSE_SOIL", "loose", None, "shovel", "stardewcraft.type.natural_ground"),
    "mine_earth_wall": ("MINE_EARTH_WALL", "wall", "TERRACOTTA_BROWN", "pickaxe", "stardewcraft.type.natural_rock"),
    "mine_earth_dark_loose_soil": ("MINE_EARTH_DARK_LOOSE_SOIL", "loose", "DEEPSLATE", "shovel", "stardewcraft.type.natural_ground"),
    "mine_earth_dark_wall": ("MINE_EARTH_DARK_WALL", "wall", "DEEPSLATE", "pickaxe", "stardewcraft.type.natural_rock"),
    "mine_frost_dark_loose_soil": ("MINE_FROST_DARK_LOOSE_SOIL", "loose", "DEEPSLATE", "shovel", "stardewcraft.type.natural_ground"),
    "mine_frost_dark_wall": ("MINE_FROST_DARK_WALL", "wall", "DEEPSLATE", "pickaxe", "stardewcraft.type.natural_rock"),
    "mine_lava_dark_loose_soil": ("MINE_LAVA_DARK_LOOSE_SOIL", "loose", "DEEPSLATE", "shovel", "stardewcraft.type.natural_ground"),
    "mine_lava_dark_wall": ("MINE_LAVA_DARK_WALL", "wall", "DEEPSLATE", "pickaxe", "stardewcraft.type.natural_rock"),
    "mine_desert_dark_loose_soil": ("MINE_DESERT_DARK_LOOSE_SOIL", "loose", "DEEPSLATE", "shovel", "stardewcraft.type.natural_ground"),
    "mine_desert_dark_wall": ("MINE_DESERT_DARK_WALL", "wall", "DEEPSLATE", "pickaxe", "stardewcraft.type.natural_rock"),
    "mine_frost_loose_soil": ("MINE_FROST_LOOSE_SOIL", "loose", "ICE", "shovel", "stardewcraft.type.natural_ground"),
    "mine_frost_wall": ("MINE_FROST_WALL", "wall", "ICE", "pickaxe", "stardewcraft.type.natural_rock"),
    "mine_lava_loose_soil": ("MINE_LAVA_LOOSE_SOIL", "loose", "COLOR_PURPLE", "shovel", "stardewcraft.type.natural_ground"),
    "mine_lava_wall": ("MINE_LAVA_WALL", "wall", "COLOR_PURPLE", "pickaxe", "stardewcraft.type.natural_rock"),
    "mine_desert_loose_soil": ("MINE_DESERT_LOOSE_SOIL", "loose", "SAND", "shovel", "stardewcraft.type.natural_ground"),
    "mine_desert_wall": ("MINE_DESERT_WALL", "wall", "SAND", "pickaxe", "stardewcraft.type.natural_rock"),
}

def load_json(path: Path) -> object:
    return json.loads(path.read_text(encoding="utf-8-sig"))


def registration_body(text: str, field: str, registry: str) -> str:
    marker = f" {field} ="
    field_start = text.find(marker)
    if field_start < 0:
        raise ValueError(f"missing field {field}")
    register_start = text.find(f"{registry}.register(", field_start)
    if register_start < 0:
        raise ValueError(f"missing {registry}.register for {field}")
    open_index = text.find("(", register_start)
    depth = 0
    for index in range(open_index, len(text)):
        char = text[index]
        if char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
            if depth == 0:
                return text[open_index + 1 : index]
    raise ValueError(f"unterminated registration for {field}")


def validate_stone_helper(text: str, properties_type: str) -> None:
    match = re.search(
        rf"private static {re.escape(properties_type)} stoneProps\(MapColor color, SoundType sound, float hardness\)\s*\{{(.*?)\n\s*\}}",
        text,
        re.S,
    )
    if match is None:
        raise ValueError(f"missing {properties_type}.stoneProps helper")
    body = " ".join(match.group(1).split())
    expected = (
        f"return {properties_type}.of() .mapColor(color) .sound(sound) .strength(hardness, 6.0F);"
    )
    if body != expected:
        raise ValueError(f"stoneProps helper drifted: expected {expected!r}, got {body!r}")


def parse_source_block(body: str, kind: str) -> tuple[str, str, str | None]:
    registration_id = re.search(r'"([a-z0-9_]+)"', body)
    block_class = re.search(r"new\s+(?:[\w.]+\.)?([A-Za-z]+)\s*\(", body)
    map_color = re.search(r"mapColor\(MapColor\.([A-Z0-9_]+)\)", body)
    if map_color is None:
        map_color = re.search(r"stoneProps\(MapColor\.([A-Z0-9_]+)", body)
    if registration_id is None or block_class is None:
        raise ValueError(f"could not parse source block registration: {body.strip()}")
    if block_class.group(1) != "Block":
        raise ValueError(f"mine backdrop block is not a plain Block: {body.strip()}")
    if kind == "loose":
        if "Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.DIRT)" not in body:
            raise ValueError(f"loose soil does not copy vanilla dirt: {body.strip()}")
    elif not re.search(r"stoneProps\(MapColor\.[A-Z0-9_]+, SoundType\.STONE, 5\.0F\)", body):
        raise ValueError(f"wall does not use the source stoneProps contract: {body.strip()}")
    return registration_id.group(1), block_class.group(1), map_color.group(1) if map_color else None


def parse_forge_block(body: str, kind: str) -> tuple[str, str, str | None]:
    registration_id = re.search(r'"([a-z0-9_]+)"', body)
    block_class = re.search(r"new\s+(?:[\w.]+\.)?([A-Za-z]+)\s*\(", body)
    map_color = re.search(r"mapColor\(MapColor\.([A-Z0-9_]+)\)", body)
    if map_color is None:
        map_color = re.search(r"stoneProps\(MapColor\.([A-Z0-9_]+)", body)
    if registration_id is None or block_class is None:
        raise ValueError(f"could not parse Forge block registration: {body.strip()}")
    if block_class.group(1) != "Block":
        raise ValueError(f"Forge mine backdrop block is not a plain Block: {body.strip()}")
    if kind == "loose":
        if "BlockBehaviour.Properties.copy(Blocks.DIRT)" not in body:
            raise ValueError(f"Forge loose soil does not copy vanilla dirt: {body.strip()}")
    elif not re.search(r"stoneProps\(MapColor\.[A-Z0-9_]+, SoundType\.STONE, 5\.0F\)", body):
        raise ValueError(f"Forge wall does not use the ported stoneProps contract: {body.strip()}")
    return registration_id.group(1), block_class.group(1), map_color.group(1) if map_color else None


def parse_source_item(body: str) -> tuple[str, str, str, int, str]:
    registration_id = re.search(r'"([a-z0-9_]+)"', body)
    block_field = re.search(r"ModBlocks\.([A-Z0-9_]+)\.get\(\)", body)
    type_key = re.search(r'"(stardewcraft\.type\.[^"]+)"', body)
    sell_price = re.search(r",\s*(-?\d+)\s*,\s*blockItemProps\(\)", body)
    if not all((registration_id, block_field, type_key, sell_price)) or "new StardewBlockItem" not in body:
        raise ValueError(f"could not parse source mine block item: {body.strip()}")
    return (
        registration_id.group(1),
        block_field.group(1),
        type_key.group(1),
        int(sell_price.group(1)),
        "stacksTo(999)",
    )


def parse_forge_item(text: str, field: str) -> tuple[str, str, str, int, str]:
    match = re.search(
        rf"RegistryObject<Item>\s+{re.escape(field)}\s*=\s*registerNaturalBlock\(\s*"
        rf"ForgeBlocks\.{re.escape(field)}\s*,\s*\"(stardewcraft\.type\.[^\"]+)\"\s*\);",
        text,
        re.S,
    )
    if match is None:
        raise ValueError(f"missing registerNaturalBlock call for {field}")
    helper = re.search(
        r"private static RegistryObject<Item> registerNaturalBlock\(.*?\n\s*\}", text, re.S
    )
    generic_helper = re.search(
        r"private static RegistryObject<Item> registerBlockItem\(.*?\n\s*\}", text, re.S
    )
    if helper is None or generic_helper is None:
        raise ValueError("missing natural block item helper")
    helper_body = " ".join(helper.group(0).split())
    generic_body = " ".join(generic_helper.group(0).split())
    if "return registerBlockItem(block, typeKey);" not in helper_body:
        raise ValueError(f"natural block helper drifted: {helper_body}")
    expected_fragments = (
        "String id = block.getId().getPath();",
        "new StardewSimpleBlockItem(block.get(), typeKey, -1, new Item.Properties().stacksTo(999))",
    )
    if any(fragment not in generic_body for fragment in expected_fragments):
        raise ValueError(f"generic block item helper drifted: {generic_body}")
    return field.lower(), field, match.group(1), -1, "stacksTo(999)"


def resolve_model(name: str) -> Path | None:
    if not name.startswith("stardewcraft:"):
        return None
    return FORGE_ASSETS / "models" / (name.split(":", 1)[1] + ".json")


def resolve_texture(name: str) -> Path | None:
    if not name.startswith("stardewcraft:"):
        return None
    return FORGE_ASSETS / "textures" / (name.split(":", 1)[1] + ".png")


def validate_model(path: Path, seen_models: set[Path], seen_textures: set[Path], errors: list[str]) -> None:
    if path in seen_models:
        return
    seen_models.add(path)
    if not path.is_file():
        errors.append(f"missing model: {path.relative_to(ROOT)}")
        return
    source_model = SOURCE_ASSETS / "models" / path.relative_to(FORGE_ASSETS / "models")
    if not source_model.is_file():
        errors.append(f"missing source model for {path.relative_to(ROOT)}")
    elif load_json(path) != load_json(source_model):
        errors.append(f"model mismatch for {path.relative_to(ROOT)}")
    document = load_json(path)
    if not isinstance(document, dict):
        errors.append(f"model is not an object: {path.relative_to(ROOT)}")
        return
    parent = document.get("parent")
    parent_path = resolve_model(parent) if isinstance(parent, str) else None
    if parent_path is not None:
        validate_model(parent_path, seen_models, seen_textures, errors)
    textures = document.get("textures", {})
    if isinstance(textures, dict):
        for value in textures.values():
            if not isinstance(value, str) or value.startswith("#"):
                continue
            texture_path = resolve_texture(value)
            if texture_path is not None:
                seen_textures.add(texture_path)
                if not texture_path.is_file():
                    errors.append(f"missing texture {value} referenced by {path.relative_to(ROOT)}")


def model_names(value: object):
    if isinstance(value, dict):
        model = value.get("model")
        if isinstance(model, str):
            yield model
        for child in value.values():
            yield from model_names(child)
    elif isinstance(value, list):
        for child in value:
            yield from model_names(child)


def main() -> int:
    errors: list[str] = []
    try:
        source_blocks = SOURCE_BLOCKS.read_text(encoding="utf-8")
        forge_blocks = FORGE_BLOCKS.read_text(encoding="utf-8")
        source_items = SOURCE_ITEMS.read_text(encoding="utf-8")
        forge_items = FORGE_ITEMS.read_text(encoding="utf-8")
    except OSError as exc:
        print(f"Forge mine-block parity parser failed: {exc}", file=sys.stderr)
        return 1

    source_contracts: dict[str, tuple[str, str, str | None]] = {}
    forge_contracts: dict[str, tuple[str, str, str | None]] = {}
    source_item_contracts: dict[str, tuple[str, str, str, int, str]] = {}
    forge_item_contracts: dict[str, tuple[str, str, str, int, str]] = {}
    try:
        validate_stone_helper(source_blocks, "Block.Properties")
        validate_stone_helper(forge_blocks, "BlockBehaviour.Properties")
        for block_id, (field, kind, _, _, _) in MINE_BLOCKS.items():
            source_contracts[block_id] = parse_source_block(
                registration_body(source_blocks, field, "BLOCKS"), kind
            )
            forge_contracts[block_id] = parse_forge_block(
                registration_body(forge_blocks, field, "BLOCKS"), kind
            )
            source_item_contracts[block_id] = parse_source_item(
                registration_body(source_items, field, "ITEMS")
            )
            forge_item_contracts[block_id] = parse_forge_item(forge_items, field)
    except (OSError, ValueError) as exc:
        print(f"Forge mine-block parity parser failed: {exc}", file=sys.stderr)
        return 1

    for block_id, (field, kind, expected_color, _, type_key) in MINE_BLOCKS.items():
        expected_block = (block_id, "Block", expected_color)
        if source_contracts[block_id] != expected_block:
            errors.append(f"source block contract drift for {block_id}: expected {expected_block}, got {source_contracts[block_id]}")
        if forge_contracts[block_id] != expected_block:
            errors.append(f"Forge block mismatch for {block_id}: expected {expected_block}, got {forge_contracts[block_id]}")
        expected_item = (block_id, field, type_key, -1, "stacksTo(999)")
        if source_item_contracts[block_id] != expected_item:
            errors.append(f"source item contract drift for {block_id}: expected {expected_item}, got {source_item_contracts[block_id]}")
        if forge_item_contracts[block_id] != expected_item:
            errors.append(f"Forge block item mismatch for {block_id}: expected {expected_item}, got {forge_item_contracts[block_id]}")

    block_aliases = dict(re.findall(
        r"public static final RegistryObject<[^>]+>\s+([A-Z0-9_]+)\s*=\s*ForgeBlocks\.([A-Z0-9_]+);",
        FORGE_BLOCK_ALIASES.read_text(encoding="utf-8"),
    ))
    item_aliases = dict(re.findall(
        r"public static final RegistryObject<Item>\s+([A-Z0-9_]+)\s*=\s*ForgeItems\.([A-Z0-9_]+);",
        FORGE_ITEM_ALIASES.read_text(encoding="utf-8"),
    ))
    for field, _, _, _, _ in MINE_BLOCKS.values():
        if block_aliases.get(field) != field:
            errors.append(f"missing Forge ModBlocks alias: {field}")
        if item_aliases.get(field) != field:
            errors.append(f"missing Forge ModItems alias: {field}")

    seen_models: set[Path] = set()
    seen_textures: set[Path] = set()
    for block_id, (_, _, _, tag_name, _) in MINE_BLOCKS.items():
        blockstate_path = FORGE_ASSETS / "blockstates" / f"{block_id}.json"
        source_blockstate_path = SOURCE_ASSETS / "blockstates" / f"{block_id}.json"
        if not blockstate_path.is_file():
            errors.append(f"missing blockstate: {blockstate_path.relative_to(ROOT)}")
        elif load_json(blockstate_path) != load_json(source_blockstate_path):
            errors.append(f"blockstate mismatch for {block_id}")
        else:
            for model_name in model_names(load_json(blockstate_path)):
                model_path = resolve_model(model_name)
                if model_path is not None:
                    validate_model(model_path, seen_models, seen_textures, errors)

        for model_kind in ("block", "item"):
            target_model = FORGE_ASSETS / "models" / model_kind / f"{block_id}.json"
            source_model = SOURCE_ASSETS / "models" / model_kind / f"{block_id}.json"
            if not target_model.is_file():
                errors.append(f"missing Forge {model_kind} model: {target_model.relative_to(ROOT)}")
            elif load_json(target_model) != load_json(source_model):
                errors.append(f"{model_kind} model mismatch for {block_id}")
            validate_model(target_model, seen_models, seen_textures, errors)

        source_loot = SOURCE_DATA / "stardewcraft/loot_table/blocks" / f"{block_id}.json"
        forge_loot = FORGE_DATA / "stardewcraft/loot_tables/blocks" / f"{block_id}.json"
        if not forge_loot.is_file():
            errors.append(f"missing Forge loot table: {forge_loot.relative_to(ROOT)}")
        elif load_json(source_loot) != load_json(forge_loot):
            errors.append(f"loot table mismatch for {block_id}")

        tag_path = FORGE_DATA / "minecraft/tags/blocks/mineable" / f"{tag_name}.json"
        if not tag_path.is_file():
            errors.append(f"missing Forge mineable tag: {tag_path.relative_to(ROOT)}")
        elif f"stardewcraft:{block_id}" not in load_json(tag_path).get("values", []):
            errors.append(f"{block_id} missing from mineable/{tag_name}")

    for source_language in sorted((SOURCE_ASSETS / "lang").glob("*.json")):
        target_language = FORGE_ASSETS / "lang" / source_language.name
        if not target_language.is_file():
            errors.append(f"missing Forge language file: {target_language.relative_to(ROOT)}")
            continue
        source_values = load_json(source_language)
        target_values = load_json(target_language)
        for block_id in MINE_BLOCKS:
            key = f"block.stardewcraft.{block_id}"
            if target_values.get(key) != source_values.get(key):
                errors.append(f"translation mismatch for {key} in {source_language.name}")

    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        print(f"Forge mine-block parity failed with {len(errors)} error(s).", file=sys.stderr)
        return 1

    print(
        f"All {len(MINE_BLOCKS)} Forge mine backdrop blocks and block items match the 1.21.1 source; "
        f"{len(seen_models)} models and {len(seen_textures)} textures across 12 languages verified."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Verify the first Forge building-material block slice against the 1.21.1 source."""

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


BLOCKS = {
    "pale_cyan_plaster": ("PALE_CYAN_PLASTER", "Block", "TERRACOTTA", "COLOR_LIGHT_BLUE", "block"),
    "teal_painted_timber": ("TEAL_PAINTED_TIMBER", "RotatedPillarBlock", "OAK_PLANKS", "COLOR_CYAN", "axe"),
    "cream_siding": ("CREAM_SIDING", "Block", "OAK_PLANKS", "SAND", "axe"),
    "terracotta_roof_tiles": ("TERRACOTTA_ROOF_TILES", "Block", "BRICKS", "TERRACOTTA_ORANGE", "pickaxe"),
    "dark_brown_roof_tiles": ("DARK_BROWN_ROOF_TILES", "Block", "BRICKS", "TERRACOTTA_BROWN", "pickaxe"),
    "ivory_siding": ("IVORY_SIDING", "Block", "OAK_PLANKS", "SAND", "axe"),
    "gray_green_masonry": ("GRAY_GREEN_MASONRY", "Block", "STONE_BRICKS", "TERRACOTTA_LIGHT_GREEN", "pickaxe"),
    "gray_violet_roof_tiles": ("GRAY_VIOLET_ROOF_TILES", "Block", "BRICKS", "TERRACOTTA_PURPLE", "pickaxe"),
    "brick_red_roof_tiles": ("BRICK_RED_ROOF_TILES", "Block", "BRICKS", "TERRACOTTA_RED", "pickaxe"),
    "blue_gray_timber": ("BLUE_GRAY_TIMBER", "RotatedPillarBlock", "OAK_PLANKS", "COLOR_CYAN", "axe"),
    "pale_blue_siding": ("PALE_BLUE_SIDING", "Block", "OAK_PLANKS", "COLOR_LIGHT_BLUE", "axe"),
    "blue_painted_planks": ("BLUE_PAINTED_PLANKS", "Block", "OAK_PLANKS", "COLOR_BLUE", "axe"),
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


def parse_block_contract(body: str, expected_api: str) -> tuple[str, str, str, str]:
    registration_id = re.search(r'"([a-z0-9_]+)"', body)
    block_class = re.search(r"new\s+(?:[\w.]+\.)?([A-Za-z]+)\s*\(", body)
    base_block = re.search(r"Blocks\.([A-Z0-9_]+)", body)
    map_color = re.search(r"mapColor\(MapColor\.([A-Z0-9_]+)\)", body)
    if not all((registration_id, block_class, base_block, map_color)):
        raise ValueError(f"could not parse block registration: {body.strip()}")
    if expected_api not in body:
        raise ValueError(f"expected API marker {expected_api!r} missing from {body.strip()}")
    return registration_id.group(1), block_class.group(1), base_block.group(1), map_color.group(1)


def parse_item_contract(body: str, block_prefix: str) -> tuple[str, str, int, str, str]:
    registration_id = re.search(r'"([a-z0-9_]+)"', body)
    block_field = re.search(rf"{re.escape(block_prefix)}\.([A-Z0-9_]+)\.get\(\)", body)
    type_key = re.search(r'"(stardewcraft\.type\.[^"]+)"', body)
    sell_price = re.search(r",\s*(-?\d+)\s*,\s*new Item\.Properties", body)
    stacks = re.search(r"new Item\.Properties\(\)(\.stacksTo\(\d+\))", body)
    if not all((registration_id, block_field, type_key, sell_price, stacks)):
        raise ValueError(f"could not parse block item registration: {body.strip()}")
    return (
        registration_id.group(1),
        block_field.group(1),
        int(sell_price.group(1)),
        type_key.group(1),
        stacks.group(1),
    )


def parse_building_block_helper(text: str, field: str) -> tuple[str, str, int, str, str]:
    match = re.search(
        rf"RegistryObject<Item>\s+{re.escape(field)}\s*=\s*registerBuildingBlock\(ForgeBlocks\.{re.escape(field)}\);",
        text,
    )
    if not match:
        raise ValueError(f"missing registerBuildingBlock call for {field}")
    helper = re.search(
        r"private static RegistryObject<Item> registerBuildingBlock\(.*?\)\s*\{(.*?)\n\s*\}",
        text,
        re.S,
    )
    if not helper:
        raise ValueError("missing registerBuildingBlock helper")
    helper_body = " ".join(helper.group(1).split())
    expected_fragments = (
        "String id = block.getId().getPath();",
        "return ITEMS.register(id, () -> new StardewSimpleBlockItem(block.get(),",
        '"stardewcraft.type.building", -1, new Item.Properties().stacksTo(999)));',
    )
    if any(fragment not in helper_body for fragment in expected_fragments):
        raise ValueError(f"registerBuildingBlock helper drifted: {helper_body!r}")
    return field.lower(), field, -1, "stardewcraft.type.building", ".stacksTo(999)"


def resolve_model(name: str) -> Path | None:
    if not name.startswith("stardewcraft:"):
        return None
    return FORGE_ASSETS / "models" / (name.split(":", 1)[1] + ".json")


def resolve_texture(name: str) -> Path | None:
    if not name.startswith("stardewcraft:"):
        return None
    return FORGE_ASSETS / "textures" / (name.split(":", 1)[1] + ".png")


def validate_model(path: Path, seen: set[Path], errors: list[str]) -> None:
    if path in seen:
        return
    seen.add(path)
    if not path.is_file():
        errors.append(f"missing model: {path.relative_to(ROOT)}")
        return
    document = load_json(path)
    if not isinstance(document, dict):
        errors.append(f"model is not an object: {path.relative_to(ROOT)}")
        return
    parent = document.get("parent")
    parent_path = resolve_model(parent) if isinstance(parent, str) else None
    if parent_path is not None:
        validate_model(parent_path, seen, errors)
    textures = document.get("textures", {})
    if isinstance(textures, dict):
        for value in textures.values():
            if not isinstance(value, str) or value.startswith("#"):
                continue
            texture_path = resolve_texture(value)
            if texture_path is not None and not texture_path.is_file():
                errors.append(f"missing texture {value} referenced by {path.relative_to(ROOT)}")


def main() -> int:
    errors: list[str] = []
    try:
        source_blocks = SOURCE_BLOCKS.read_text(encoding="utf-8")
        forge_blocks = FORGE_BLOCKS.read_text(encoding="utf-8")
        source_items = SOURCE_ITEMS.read_text(encoding="utf-8")
        forge_items = FORGE_ITEMS.read_text(encoding="utf-8")
    except OSError as exc:
        print(f"Forge building-block parity parser failed: {exc}", file=sys.stderr)
        return 1

    source_contracts: dict[str, tuple[str, str, str, str]] = {}
    forge_contracts: dict[str, tuple[str, str, str, str]] = {}
    source_item_contracts: dict[str, tuple[str, str, int, str, str]] = {}
    forge_item_contracts: dict[str, tuple[str, str, int, str, str]] = {}
    try:
        for block_id, (field, _, _, _, _) in BLOCKS.items():
            source_contracts[block_id] = parse_block_contract(
                registration_body(source_blocks, field, "BLOCKS"), "Block.Properties.ofFullCopy"
            )
            forge_contracts[block_id] = parse_block_contract(
                registration_body(forge_blocks, field, "BLOCKS"), "BlockBehaviour.Properties.copy"
            )
            source_item_contracts[block_id] = parse_item_contract(
                registration_body(source_items, field, "ITEMS"), "ModBlocks"
            )
            forge_item_contracts[block_id] = parse_building_block_helper(forge_items, field)
    except (OSError, ValueError) as exc:
        print(f"Forge building-block parity parser failed: {exc}", file=sys.stderr)
        return 1

    for block_id, (_, expected_class, expected_base, expected_color, _) in BLOCKS.items():
        expected = (block_id, expected_class, expected_base, expected_color)
        if source_contracts[block_id] != expected:
            errors.append(f"source contract drift for {block_id}: expected {expected}, got {source_contracts[block_id]}")
        if forge_contracts[block_id] != expected:
            errors.append(f"Forge block mismatch for {block_id}: expected {expected}, got {forge_contracts[block_id]}")

        expected_item = (block_id, BLOCKS[block_id][0], -1, "stardewcraft.type.building", ".stacksTo(999)")
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
    for block_id, (field, _, _, _, _) in BLOCKS.items():
        if block_aliases.get(field) != field:
            errors.append(f"missing Forge ModBlocks alias: {field}")
        if item_aliases.get(field) != field:
            errors.append(f"missing Forge ModItems alias: {field}")

    seen_models: set[Path] = set()
    for block_id in BLOCKS:
        blockstate_path = FORGE_ASSETS / "blockstates" / f"{block_id}.json"
        if not blockstate_path.is_file():
            errors.append(f"missing blockstate: {blockstate_path.relative_to(ROOT)}")
        else:
            blockstate = load_json(blockstate_path)
            variants = blockstate.get("variants", {}) if isinstance(blockstate, dict) else {}
            for variant in variants.values() if isinstance(variants, dict) else []:
                model_name = variant.get("model") if isinstance(variant, dict) else None
                model_path = resolve_model(model_name) if isinstance(model_name, str) else None
                if model_path is not None:
                    validate_model(model_path, seen_models, errors)
        item_model = FORGE_ASSETS / "models/item" / f"{block_id}.json"
        validate_model(item_model, seen_models, errors)

        source_loot = SOURCE_DATA / "stardewcraft/loot_table/blocks" / f"{block_id}.json"
        forge_loot = FORGE_DATA / "stardewcraft/loot_tables/blocks" / f"{block_id}.json"
        if not forge_loot.is_file():
            errors.append(f"missing Forge loot table: {forge_loot.relative_to(ROOT)}")
        elif load_json(source_loot) != load_json(forge_loot):
            errors.append(f"loot table mismatch for {block_id}")

    for tag_name, expected_ids in {
        "axe": [block_id for block_id, contract in BLOCKS.items() if contract[4] == "axe"],
        "pickaxe": [block_id for block_id, contract in BLOCKS.items() if contract[4] == "pickaxe"],
    }.items():
        tag_path = FORGE_DATA / "minecraft/tags/blocks/mineable" / f"{tag_name}.json"
        if not tag_path.is_file():
            errors.append(f"missing Forge mineable tag: {tag_path.relative_to(ROOT)}")
            continue
        values = load_json(tag_path).get("values", [])
        for block_id in expected_ids:
            if f"stardewcraft:{block_id}" not in values:
                errors.append(f"{block_id} missing from mineable/{tag_name}")

    for source_language in sorted((SOURCE_ASSETS / "lang").glob("*.json")):
        target_language = FORGE_ASSETS / "lang" / source_language.name
        if not target_language.is_file():
            errors.append(f"missing Forge language file: {target_language.relative_to(ROOT)}")
            continue
        source_values = load_json(source_language)
        target_values = load_json(target_language)
        for block_id in BLOCKS:
            key = f"block.stardewcraft.{block_id}"
            if target_values.get(key) != source_values.get(key):
                errors.append(f"translation mismatch for {key} in {source_language.name}")

    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        print(f"Forge building-block parity failed with {len(errors)} error(s).", file=sys.stderr)
        return 1

    print(
        f"All {len(BLOCKS)} Forge building blocks and block items match the 1.21.1 source; "
        f"{len(seen_models)} models/resources and 12 languages verified."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

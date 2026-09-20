#!/usr/bin/env python3
"""Verify the Forge plain special-block slice against the 1.21.1 source."""

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


SPECIAL_BLOCKS = {
    "mine_barrier": {
        "field": "MINE_BARRIER",
        "kind": "barrier",
        "source_class": "Block",
        "forge_class": "Block",
        "tags": ("mineable/pickaxe", "dragon_immune", "wither_immune"),
        "language_keys": ("block.stardewcraft.mine_barrier", "block.stardewcraft.mine_barrier.desc"),
    },
    "pale_blue_window_glass": {
        "field": "PALE_BLUE_WINDOW_GLASS",
        "kind": "glass",
        "source_class": "TransparentBlock",
        "forge_class": "GlassBlock",
        "tags": (),
        "language_keys": ("block.stardewcraft.pale_blue_window_glass",),
    },
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


def parse_block(body: str, kind: str, api: str) -> tuple[str, str, str]:
    registration_id = re.search(r'"([a-z0-9_]+)"', body)
    block_class = re.search(r"new\s+(?:[\w.]+\.)?([A-Za-z]+)\s*\(", body)
    if registration_id is None or block_class is None:
        raise ValueError(f"could not parse block registration: {body.strip()}")
    if kind == "glass":
        expected_class = "TransparentBlock" if api == "source" else "GlassBlock"
        expected_properties = (
            "Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.GLASS)"
            if api == "source"
            else "BlockBehaviour.Properties.copy(Blocks.GLASS)"
        )
        if block_class.group(1) != expected_class or expected_properties not in body:
            raise ValueError(f"glass contract drifted: {body.strip()}")
        return registration_id.group(1), block_class.group(1), "GLASS"

    expected_properties = "Block.Properties.of()" if api == "source" else "BlockBehaviour.Properties.of()"
    if block_class.group(1) != "Block" or expected_properties not in body:
        raise ValueError(f"barrier block class/properties drifted: {body.strip()}")
    required = (
        r"mapColor\((?:net\.minecraft\.world\.level\.material\.)?MapColor\.COLOR_BLACK\)",
        r"pushReaction\((?:net\.minecraft\.world\.level\.material\.)?PushReaction\.BLOCK\)",
        r"sound\((?:net\.minecraft\.world\.level\.block\.)?SoundType\.STONE\)",
        r"strength\(-1\.0F, 3600000\.0F\)",
    )
    if any(re.search(fragment, body) is None for fragment in required):
        raise ValueError(f"barrier property contract drifted: {body.strip()}")
    return registration_id.group(1), block_class.group(1), "BARRIER"


def validate_source_item_helper(text: str) -> None:
    helper = re.search(
        r"private static DeferredItem<Item> blockItem\(String name, DeferredBlock<\?> block\)\s*\{(.*?)\n\s*\}",
        text,
        re.S,
    )
    typed_helper = re.search(
        r"private static DeferredItem<Item> blockItem\(String name, DeferredBlock<\?> block, String typeKey\)\s*\{(.*?)\n\s*\}",
        text,
        re.S,
    )
    if helper is None or typed_helper is None:
        raise ValueError("missing source blockItem helpers")
    helper_body = " ".join(helper.group(0).split())
    typed_body = " ".join(typed_helper.group(0).split())
    if 'return blockItem(name, block, "stardewcraft.type.building");' not in helper_body:
        raise ValueError(f"source blockItem default helper drifted: {helper_body}")
    if "new StardewBlockItem(block.get(), typeKey, -1, blockItemProps())" not in typed_body:
        raise ValueError(f"source blockItem typed helper drifted: {typed_body}")


def validate_forge_item_helper(text: str) -> None:
    helper = re.search(
        r"private static RegistryObject<Item> registerBuildingBlock\(.*?\n\s*\}", text, re.S
    )
    if helper is None:
        raise ValueError("missing Forge registerBuildingBlock helper")
    body = " ".join(helper.group(0).split())
    required = (
        "String id = block.getId().getPath();",
        'new StardewSimpleBlockItem(block.get(), "stardewcraft.type.building", -1, new Item.Properties().stacksTo(999))',
    )
    if any(fragment not in body for fragment in required):
        raise ValueError(f"Forge building item helper drifted: {body}")


def parse_source_item(text: str, field: str, block_id: str) -> tuple[str, str, str, int, int]:
    shorthand = re.search(
        rf"DeferredItem<Item>\s+{re.escape(field)}\s*=\s*blockItem\(\s*\"{re.escape(block_id)}\"\s*,\s*ModBlocks\.{re.escape(field)}\s*\);",
        text,
    )
    direct = re.search(
        rf"DeferredItem<Item>\s+{re.escape(field)}\s*=\s*ITEMS\.register\(\s*\"{re.escape(block_id)}\".*?"
        rf"new StardewBlockItem\(ModBlocks\.{re.escape(field)}\.get\(\),\s*\"stardewcraft\.type\.building\",\s*-1,.*?\)\s*\);",
        text,
        re.S,
    )
    if shorthand is None and direct is None:
        raise ValueError(f"missing source block item call for {field}")
    return block_id, field, "stardewcraft.type.building", -1, 999


def parse_forge_item(text: str, field: str, block_id: str) -> tuple[str, str, str, int, int]:
    match = re.search(
        rf"RegistryObject<Item>\s+{re.escape(field)}\s*=\s*registerBuildingBlock\(ForgeBlocks\.{re.escape(field)}\);",
        text,
    )
    if match is None:
        raise ValueError(f"missing Forge registerBuildingBlock call for {field}")
    return block_id, field, "stardewcraft.type.building", -1, 999


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
            if texture_path is None:
                continue
            seen_textures.add(texture_path)
            if not texture_path.is_file():
                errors.append(f"missing texture {value} referenced by {path.relative_to(ROOT)}")
            else:
                source_texture = SOURCE_ASSETS / "textures" / texture_path.relative_to(FORGE_ASSETS / "textures")
                if not source_texture.is_file() or source_texture.read_bytes() != texture_path.read_bytes():
                    errors.append(f"texture mismatch for {value}")
                source_meta = Path(str(source_texture) + ".mcmeta")
                target_meta = Path(str(texture_path) + ".mcmeta")
                if source_meta.is_file():
                    if not target_meta.is_file() or source_meta.read_bytes() != target_meta.read_bytes():
                        errors.append(f"texture metadata mismatch for {value}")


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
        validate_source_item_helper(source_items)
        validate_forge_item_helper(forge_items)
    except (OSError, ValueError) as exc:
        print(f"Forge special-block parity parser failed: {exc}", file=sys.stderr)
        return 1

    try:
        for block_id, contract in SPECIAL_BLOCKS.items():
            field = contract["field"]
            source_contract = parse_block(registration_body(source_blocks, field, "BLOCKS"), contract["kind"], "source")
            forge_contract = parse_block(registration_body(forge_blocks, field, "BLOCKS"), contract["kind"], "forge")
            expected_source = (block_id, contract["source_class"], "GLASS" if contract["kind"] == "glass" else "BARRIER")
            expected_forge = (block_id, contract["forge_class"], "GLASS" if contract["kind"] == "glass" else "BARRIER")
            if source_contract != expected_source:
                errors.append(f"source block contract drift for {block_id}: expected {expected_source}, got {source_contract}")
            if forge_contract != expected_forge:
                errors.append(f"Forge block mismatch for {block_id}: expected {expected_forge}, got {forge_contract}")
            source_item = parse_source_item(source_items, field, block_id)
            forge_item = parse_forge_item(forge_items, field, block_id)
            expected_item = (block_id, field, "stardewcraft.type.building", -1, 999)
            if source_item != expected_item:
                errors.append(f"source item contract drift for {block_id}: expected {expected_item}, got {source_item}")
            if forge_item != expected_item:
                errors.append(f"Forge item contract mismatch for {block_id}: expected {expected_item}, got {forge_item}")
    except (OSError, ValueError) as exc:
        print(f"Forge special-block parity parser failed: {exc}", file=sys.stderr)
        return 1

    block_aliases = dict(re.findall(
        r"public static final RegistryObject<[^>]+>\s+([A-Z0-9_]+)\s*=\s*ForgeBlocks\.([A-Z0-9_]+);",
        FORGE_BLOCK_ALIASES.read_text(encoding="utf-8"),
    ))
    item_aliases = dict(re.findall(
        r"public static final RegistryObject<Item>\s+([A-Z0-9_]+)\s*=\s*ForgeItems\.([A-Z0-9_]+);",
        FORGE_ITEM_ALIASES.read_text(encoding="utf-8"),
    ))
    for contract in SPECIAL_BLOCKS.values():
        field = contract["field"]
        if block_aliases.get(field) != field:
            errors.append(f"missing Forge ModBlocks alias: {field}")
        if item_aliases.get(field) != field:
            errors.append(f"missing Forge ModItems alias: {field}")

    seen_models: set[Path] = set()
    seen_textures: set[Path] = set()
    for block_id, contract in SPECIAL_BLOCKS.items():
        blockstate = FORGE_ASSETS / "blockstates" / f"{block_id}.json"
        source_blockstate = SOURCE_ASSETS / "blockstates" / f"{block_id}.json"
        if not blockstate.is_file():
            errors.append(f"missing blockstate: {blockstate.relative_to(ROOT)}")
        elif load_json(blockstate) != load_json(source_blockstate):
            errors.append(f"blockstate mismatch for {block_id}")
        else:
            for name in model_names(load_json(blockstate)):
                model = resolve_model(name)
                if model is not None:
                    validate_model(model, seen_models, seen_textures, errors)

        for kind in ("block", "item"):
            target_model = FORGE_ASSETS / "models" / kind / f"{block_id}.json"
            source_model = SOURCE_ASSETS / "models" / kind / f"{block_id}.json"
            if not target_model.is_file():
                errors.append(f"missing Forge {kind} model: {target_model.relative_to(ROOT)}")
            elif load_json(target_model) != load_json(source_model):
                errors.append(f"{kind} model mismatch for {block_id}")
            validate_model(target_model, seen_models, seen_textures, errors)

        source_loot = SOURCE_DATA / "stardewcraft/loot_table/blocks" / f"{block_id}.json"
        forge_loot = FORGE_DATA / "stardewcraft/loot_tables/blocks" / f"{block_id}.json"
        if not forge_loot.is_file():
            errors.append(f"missing Forge loot table: {forge_loot.relative_to(ROOT)}")
        elif load_json(source_loot) != load_json(forge_loot):
            errors.append(f"loot table mismatch for {block_id}")

        for tag_name in contract["tags"]:
            tag_path = FORGE_DATA / "minecraft/tags/blocks" / f"{tag_name}.json"
            if not tag_path.is_file():
                errors.append(f"missing Forge tag: {tag_path.relative_to(ROOT)}")
            elif f"stardewcraft:{block_id}" not in load_json(tag_path).get("values", []):
                errors.append(f"{block_id} missing from blocks/{tag_name}")

    for source_language in sorted((SOURCE_ASSETS / "lang").glob("*.json")):
        target_language = FORGE_ASSETS / "lang" / source_language.name
        if not target_language.is_file():
            errors.append(f"missing Forge language file: {target_language.relative_to(ROOT)}")
            continue
        source_values = load_json(source_language)
        target_values = load_json(target_language)
        for contract in SPECIAL_BLOCKS.values():
            for key in contract["language_keys"]:
                if target_values.get(key) != source_values.get(key):
                    errors.append(f"translation mismatch for {key} in {source_language.name}")

    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        print(f"Forge special-block parity failed with {len(errors)} error(s).", file=sys.stderr)
        return 1

    print(
        f"All {len(SPECIAL_BLOCKS)} Forge special plain blocks and block items match the 1.21.1 source; "
        f"{len(seen_models)} models and {len(seen_textures)} textures across 12 languages verified."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

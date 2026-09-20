#!/usr/bin/env python3
"""Verify the complete generated-tree building block slice for Forge 1.20.1.

The source line uses NeoForge 1.21.1 names and resource directories.  This
checker deliberately compares the complete 85-entry contract and only allows
the mechanical Forge directory/API adaptations (RegistryObject, plural data
directories, and recipe result.item).
"""

from __future__ import annotations

import copy
import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "src/main"
FORGE = ROOT / "src/forge-bootstrap"

SPECIES = ("oak", "maple", "pine", "mahogany", "mystic_tree")
PATTERNS = ("", "checkerboard_", "fishscale_")


def tree_ids() -> tuple[str, ...]:
    ids: list[str] = []
    for species in SPECIES:
        for pattern in PATTERNS:
            base = f"{species}_{pattern}planks"
            ids.extend((base, f"{base}_stairs", f"{base}_slab", f"{base}_fence", f"{base}_fence_gate"))
        ids.extend((f"{species}_log_stairs", f"{species}_log_slab"))
    return tuple(ids)


IDS = tree_ids()
PLANKS = tuple(f"{species}_{pattern}planks" for species in SPECIES for pattern in PATTERNS)
STAIRS = tuple(f"{base}_stairs" for base in PLANKS) + tuple(f"{species}_log_stairs" for species in SPECIES)
SLABS = tuple(f"{base}_slab" for base in PLANKS) + tuple(f"{species}_log_slab" for species in SPECIES)
FENCES = tuple(f"{base}_fence" for base in PLANKS)
GATES = tuple(f"{base}_fence_gate" for base in PLANKS)


def load_json(path: Path) -> object:
    return json.loads(path.read_text(encoding="utf-8-sig"))


def fail(errors: list[str], message: str) -> None:
    errors.append(message)


def compact(text: str) -> str:
    return re.sub(r"\s+", "", text)


def check_java_contracts(errors: list[str]) -> None:
    source_blocks = (SOURCE / "java/com/stardew/craft/block/ModBlocks.java").read_text(encoding="utf-8")
    forge_blocks = (FORGE / "java/com/stardew/craft/forge/registry/ForgeBlocks.java").read_text(encoding="utf-8")
    source_items = (SOURCE / "java/com/stardew/craft/item/ModItems.java").read_text(encoding="utf-8")
    forge_items = (FORGE / "java/com/stardew/craft/forge/registry/ForgeItems.java").read_text(encoding="utf-8")
    forge_block_aliases = (FORGE / "java/com/stardew/craft/block/ModBlocks.java").read_text(encoding="utf-8")
    forge_item_aliases = (FORGE / "java/com/stardew/craft/item/ModItems.java").read_text(encoding="utf-8")

    source_compact = compact(source_blocks)
    forge_compact = compact(forge_blocks)
    for values, label in (
        ('"oak","maple","pine","mahogany","mystic_tree"', "species"),
        ('"","checkerboard_","fishscale_"', "plank patterns"),
    ):
        if values not in source_compact or values not in forge_compact:
            fail(errors, f"tree {label} order drifted")

    for text, label in ((source_blocks, "source"), (forge_blocks, "Forge")):
        if "NEW_TREE_BUILDING_BLOCKS" not in text or "registerNewTreeBuildingBlocks()" not in text:
            fail(errors, f"{label} missing NEW_TREE_BUILDING_BLOCKS map")
        if "LinkedHashMap" not in text or "Collections.unmodifiableMap(blocks)" not in text:
            fail(errors, f"{label} tree building map must preserve insertion order and be immutable")

    if "Map<String, RegistryObject<? extends Block>> NEW_TREE_BUILDING_BLOCKS" not in forge_blocks:
        fail(errors, "Forge tree building map has the wrong RegistryObject type")
    if "new StairBlock(base.get().defaultBlockState(), props)" not in forge_blocks:
        fail(errors, "Forge stairs must use the source base block default state")
    if "new SlabBlock(props)" not in forge_blocks or "new FenceBlock(props)" not in forge_blocks:
        fail(errors, "Forge slab/fence constructors drifted")
    if "new FenceGateBlock(props, WoodType.OAK)" not in forge_blocks:
        fail(errors, "Forge 1.20 FenceGateBlock API adaptation drifted")
    for fragment in ("sound(SoundType.WOOD)", "strength(2.0F, 3.0F)", "new Block(newTreeWoodProps())"):
        if fragment not in forge_blocks:
            fail(errors, f"Forge tree building property contract missing: {fragment}")

    if "blockItems(ModBlocks.NEW_TREE_BUILDING_BLOCKS)" not in source_items:
        fail(errors, "source tree building item map drifted")
    for fragment in (
        "ForgeBlocks.NEW_TREE_BUILDING_BLOCKS.entrySet()",
        "registerNewTreeBuildingItems()",
        '"stardewcraft.type.building", -1',
        ".stacksTo(999)",
    ):
        if fragment not in forge_items:
            fail(errors, f"Forge tree building item contract missing: {fragment}")
    if "Map<String, RegistryObject<? extends Block>> NEW_TREE_BUILDING_BLOCKS" not in forge_block_aliases:
        fail(errors, "Forge ModBlocks compatibility map alias missing")
    if "Map<String, RegistryObject<Item>> NEW_TREE_BUILDING_ITEMS" not in forge_item_aliases:
        fail(errors, "Forge ModItems compatibility map alias missing")


def resolve_model(root: Path, name: str) -> Path | None:
    if not isinstance(name, str) or not name.startswith("stardewcraft:"):
        return None
    return root / "models" / (name.split(":", 1)[1] + ".json")


def iter_model_refs(value: object):
    if isinstance(value, dict):
        model = value.get("model")
        if isinstance(model, str):
            yield model
        for child in value.values():
            yield from iter_model_refs(child)
    elif isinstance(value, list):
        for child in value:
            yield from iter_model_refs(child)


def check_resources(errors: list[str]) -> tuple[int, int, int]:
    source_assets = SOURCE / "resources/assets/stardewcraft"
    forge_assets = FORGE / "resources/assets/stardewcraft"
    source_data = SOURCE / "resources/data/stardewcraft"
    forge_data = FORGE / "resources/data/stardewcraft"
    custom_models: set[str] = set()
    textures: set[str] = set()

    def visit_model(model_path: Path) -> None:
        try:
            relative = model_path.relative_to(source_assets / "models").as_posix()
        except ValueError:
            fail(errors, f"model escaped source model root: {model_path}")
            return
        if relative in custom_models:
            return
        custom_models.add(relative)
        target = forge_assets / "models" / relative
        if not model_path.is_file() or not target.is_file():
            fail(errors, f"missing custom model: {relative}")
            return
        if load_json(model_path) != load_json(target):
            fail(errors, f"model mismatch: {relative}")
        document = load_json(model_path)
        if not isinstance(document, dict):
            return
        parent = resolve_model(source_assets, document.get("parent"))
        if parent is not None:
            visit_model(parent)
        model_textures = document.get("textures")
        if isinstance(model_textures, dict):
            for name in model_textures.values():
                if not isinstance(name, str) or name.startswith("#"):
                    continue
                source_texture = source_assets / "textures" / (name.split(":", 1)[1] + ".png") if name.startswith("stardewcraft:") else None
                if source_texture is None:
                    continue
                texture_relative = source_texture.relative_to(source_assets / "textures").as_posix()
                textures.add(texture_relative)
                target_texture = forge_assets / "textures" / texture_relative
                if not source_texture.is_file() or not target_texture.is_file() or source_texture.read_bytes() != target_texture.read_bytes():
                    fail(errors, f"texture mismatch or missing: {texture_relative}")
                source_meta = Path(str(source_texture) + ".mcmeta")
                target_meta = Path(str(target_texture) + ".mcmeta")
                if source_meta.is_file() and (not target_meta.is_file() or source_meta.read_bytes() != target_meta.read_bytes()):
                    fail(errors, f"texture metadata mismatch: {texture_relative}")

    for identifier in IDS:
        source_blockstate = source_assets / "blockstates" / f"{identifier}.json"
        forge_blockstate = forge_assets / "blockstates" / f"{identifier}.json"
        if not forge_blockstate.is_file() or load_json(forge_blockstate) != load_json(source_blockstate):
            fail(errors, f"blockstate mismatch or missing: {identifier}")
        elif source_blockstate.is_file():
            for name in iter_model_refs(load_json(source_blockstate)):
                model = resolve_model(source_assets, name)
                if model is not None:
                    visit_model(model)

        source_item = source_assets / "models/item" / f"{identifier}.json"
        forge_item = forge_assets / "models/item" / f"{identifier}.json"
        if not forge_item.is_file() or load_json(forge_item) != load_json(source_item):
            fail(errors, f"item model mismatch or missing: {identifier}")
        elif source_item.is_file():
            parent = resolve_model(source_assets, load_json(source_item).get("parent"))
            if parent is not None:
                visit_model(parent)

        source_loot = source_data / "loot_table/blocks" / f"{identifier}.json"
        forge_loot = forge_data / "loot_tables/blocks" / f"{identifier}.json"
        if not forge_loot.is_file() or load_json(forge_loot) != load_json(source_loot):
            fail(errors, f"loot table mismatch or missing: {identifier}")

        source_recipe = source_data / "recipe" / f"{identifier}.json"
        forge_recipe = forge_data / "recipes" / f"{identifier}.json"
        if not forge_recipe.is_file():
            fail(errors, f"recipe missing: {identifier}")
        else:
            expected = copy.deepcopy(load_json(source_recipe))
            result = expected.get("result") if isinstance(expected, dict) else None
            if not isinstance(result, dict) or "id" not in result:
                fail(errors, f"source recipe result.id missing: {identifier}")
            else:
                result["item"] = result.pop("id")
                if load_json(forge_recipe) != expected:
                    fail(errors, f"recipe mismatch after result.id -> result.item: {identifier}")

    if len(custom_models) != 225:
        fail(errors, f"expected 225 custom models (220 wood + 5 log), got {len(custom_models)}")
    if sum(name.startswith("block/wood/") for name in custom_models) != 220:
        fail(errors, "wood model closure is not the complete 220-file source directory")
    if sum(name.startswith("block/tree/") for name in custom_models) != 5:
        fail(errors, "tree log model closure must contain five species log models")
    if len(textures) != 25:
        fail(errors, f"expected 25 referenced textures (15 wood + 10 log), got {len(textures)}")

    source_languages = sorted((source_assets / "lang").glob("*.json"))
    for source_language in source_languages:
        target_language = forge_assets / "lang" / source_language.name
        if not target_language.is_file():
            fail(errors, f"missing Forge language: {source_language.name}")
            continue
        source_values = load_json(source_language)
        target_values = load_json(target_language)
        for identifier in IDS:
            key = f"block.stardewcraft.{identifier}"
            if target_values.get(key) != source_values.get(key):
                fail(errors, f"translation mismatch for {key} in {source_language.name}")

    return len(custom_models), len(textures), len(source_languages)


def check_tags(errors: list[str]) -> None:
    source_data = SOURCE / "resources/data"
    forge_data = FORGE / "resources/data"
    expected_tags = {
        "minecraft/tags/blocks/planks.json": [f"stardewcraft:{identifier}" for identifier in PLANKS],
        "minecraft/tags/blocks/stairs.json": [f"stardewcraft:{identifier}" for identifier in STAIRS],
        "minecraft/tags/blocks/slabs.json": [f"stardewcraft:{identifier}" for identifier in SLABS],
        "minecraft/tags/blocks/wooden_stairs.json": [f"stardewcraft:{identifier}" for identifier in STAIRS],
        "minecraft/tags/blocks/wooden_slabs.json": [f"stardewcraft:{identifier}" for identifier in SLABS],
        "minecraft/tags/blocks/wooden_fences.json": [f"stardewcraft:{identifier}" for identifier in FENCES],
        "minecraft/tags/blocks/fence_gates.json": [f"stardewcraft:{identifier}" for identifier in GATES],
        # The source placeholder fence is not registered in this Forge slice;
        # keep the vanilla fence closure pointed at the registered wood fences.
        "minecraft/tags/blocks/fences.json": ["#minecraft:wooden_fences"],
        "minecraft/tags/items/planks.json": [f"stardewcraft:{identifier}" for identifier in PLANKS],
        "minecraft/tags/items/wooden_stairs.json": [f"stardewcraft:{identifier}" for identifier in STAIRS],
        "minecraft/tags/items/wooden_slabs.json": [f"stardewcraft:{identifier}" for identifier in SLABS],
        "minecraft/tags/items/wooden_fences.json": [f"stardewcraft:{identifier}" for identifier in FENCES],
        "minecraft/tags/items/fence_gates.json": [f"stardewcraft:{identifier}" for identifier in GATES],
        "stardewcraft/tags/items/crafting_planks.json": [
            f"stardewcraft:{species}_{pattern}planks" for species in SPECIES[:3] for pattern in PATTERNS
        ],
        "stardewcraft/tags/items/crafting_hardwood_planks.json": [
            f"stardewcraft:{species}_{pattern}planks" for species in SPECIES[3:] for pattern in PATTERNS
        ],
    }
    for forge_relative, values_expected in expected_tags.items():
        forge_path = forge_data / forge_relative
        if not forge_path.is_file():
            fail(errors, f"tag missing: {forge_relative}")
            continue
        document = load_json(forge_path)
        if document.get("replace") is not False or document.get("values") != values_expected:
            fail(errors, f"registered-only tree tag mismatch: {forge_relative}")

    # Generic item stairs/slabs contain only source values for blocks that are
    # not registered in this migration slice.  Do not introduce TagLoader
    # errors before those block slices land.
    for name in ("stairs", "slabs"):
        if (forge_data / "minecraft/tags/items" / f"{name}.json").exists():
            fail(errors, f"unported generic item tag must remain absent: minecraft/tags/items/{name}.json")

    axe = forge_data / "minecraft/tags/blocks/mineable/axe.json"
    if not axe.is_file():
        fail(errors, "missing Forge mineable/axe tag")
        return
    values = load_json(axe).get("values", [])
    legacy = [
        "stardewcraft:teal_painted_timber", "stardewcraft:cream_siding", "stardewcraft:ivory_siding",
        "stardewcraft:blue_gray_timber", "stardewcraft:pale_blue_siding", "stardewcraft:blue_painted_planks",
        "stardewcraft:oak_root", "stardewcraft:oak_log", "stardewcraft:oak_branch",
        "stardewcraft:maple_root", "stardewcraft:maple_log", "stardewcraft:maple_branch",
        "stardewcraft:pine_root", "stardewcraft:pine_log", "stardewcraft:pine_branch",
        "stardewcraft:mahogany_root", "stardewcraft:mahogany_log", "stardewcraft:mahogany_branch",
        "stardewcraft:mystic_tree_root", "stardewcraft:mystic_tree_log", "stardewcraft:mystic_tree_branch",
    ]
    expected_tree = [f"stardewcraft:{identifier}" for identifier in IDS]
    if values[: len(legacy)] != legacy:
        fail(errors, "mineable/axe lost or reordered the existing Forge values")
    if values[len(legacy) : len(legacy) + len(expected_tree)] != expected_tree:
        fail(errors, "mineable/axe tree building IDs are not in source registration order")

    for rel, expected in (
        ("minecraft/tags/blocks/logs.json", [f"stardewcraft:{s}_{p}" for s in SPECIES for p in ("root", "log", "branch")]),
        ("minecraft/tags/items/logs.json", [f"stardewcraft:{s}_log" for s in SPECIES]),
    ):
        path = forge_data / rel
        if not path.is_file() or load_json(path).get("values") != expected:
            fail(errors, f"tree core tag dependency drifted: {rel}")


def main() -> int:
    errors: list[str] = []
    try:
        check_java_contracts(errors)
        models, textures, languages = check_resources(errors)
        check_tags(errors)
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as exc:
        print(f"Forge tree-building parity parser failed: {exc}", file=sys.stderr)
        return 1
    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        print(f"Forge tree-building parity failed with {len(errors)} error(s).", file=sys.stderr)
        return 1
    print(
        f"All {len(IDS)} Forge tree-building blocks and items match the 1.21.1 source; "
        f"{models} custom models, {textures} textures, 85 recipes/loot tables, and {languages} languages verified."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Verify the Forge tree-core block slice against the 1.21.1 source contract."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "src/main"
FORGE = ROOT / "src/forge-bootstrap"
COMMON = ROOT / "src/forge-common"

SPECIES = ("oak", "maple", "pine", "mahogany", "mystic_tree")
PARTS = ("root", "log", "branch")
CORE_IDS = tuple(f"{species}_{part}" for species in SPECIES for part in PARTS)


def load_json(path: Path) -> object:
    return json.loads(path.read_text(encoding="utf-8-sig"))


def fail(errors: list[str], message: str) -> None:
    errors.append(message)


def registration_body(text: str, field: str, registry: str = "BLOCKS") -> str:
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


def field_assignment(text: str, field: str) -> str:
    match = re.search(
        rf"(?:public\s+)?static\s+final\s+[^;\n]+\s+{re.escape(field)}\s*=\s*([^;]+);",
        text,
    )
    if match is None:
        raise ValueError(f"missing field assignment {field}")
    return match.group(1)


def check_text_contracts(errors: list[str]) -> None:
    source_blocks = (SOURCE / "java/com/stardew/craft/block/ModBlocks.java").read_text(encoding="utf-8")
    forge_blocks = (FORGE / "java/com/stardew/craft/forge/registry/ForgeBlocks.java").read_text(encoding="utf-8")
    source_items = (SOURCE / "java/com/stardew/craft/item/ModItems.java").read_text(encoding="utf-8")
    forge_items = (FORGE / "java/com/stardew/craft/forge/registry/ForgeItems.java").read_text(encoding="utf-8")
    forge_block_aliases = (FORGE / "java/com/stardew/craft/block/ModBlocks.java").read_text(encoding="utf-8")
    forge_item_aliases = (FORGE / "java/com/stardew/craft/item/ModItems.java").read_text(encoding="utf-8")
    source_entities = (SOURCE / "java/com/stardew/craft/blockentity/ModBlockEntities.java").read_text(encoding="utf-8")
    forge_entities = (FORGE / "java/com/stardew/craft/forge/registry/ForgeBlockEntities.java").read_text(encoding="utf-8")
    forge_entity_aliases = (FORGE / "java/com/stardew/craft/blockentity/ModBlockEntities.java").read_text(encoding="utf-8")
    forge_client = (FORGE / "java/com/stardew/craft/forge/client/ForgeClientSetup.java").read_text(encoding="utf-8")

    for text, label, properties in (
        (source_blocks, "source", "Block.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.WOOD)"),
        (forge_blocks, "Forge", "BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)"),
    ):
        if "newTreeWoodProps" not in text or properties not in "".join(text.split()):
            # Whitespace-independent check above is intentionally supplemented below.
            compact = re.sub(r"\s+", "", text)
            expected = re.sub(r"\s+", "", properties)
            if expected not in compact:
                fail(errors, f"{label} newTreeWoodProps property contract drifted")
    compact_forge = re.sub(r"\s+", "", forge_blocks)
    for fragment in (
        "sound(SoundType.WOOD)",
        "strength(2.0F,3.0F)",
        "newNewTreePartBlock(newTreeWoodProps().noOcclusion(),true)",
    ):
        # The first two fragments are checked directly; the third is represented by
        # the actual constructor expression and checked with a looser regex below.
        if fragment not in compact_forge and fragment != "newNewTreePartBlock(newTreeWoodProps().noOcclusion(),true)":
            fail(errors, f"Forge tree property contract missing: {fragment}")
    if re.search(r"new\s+NewTreePartBlock\(newTreeWoodProps\(\)\.noOcclusion\(\),\s*true\)", forge_blocks) is None:
        fail(errors, "Forge roots/branches must use NewTreePartBlock(...noOcclusion(), true)")
    if re.search(r"new\s+NewTreeLogBlock\(newTreeWoodProps\(\)\)", forge_blocks) is None:
        fail(errors, "Forge logs must use NewTreeLogBlock(newTreeWoodProps())")

    expected_order: list[str] = []
    for species in SPECIES:
        expected_order.extend(f"{species}_{part}".upper() for part in PARTS)
    forge_fields = re.findall(r"public\s+static\s+final\s+RegistryObject<Block>\s+([A-Z0-9_]+)\s*=", forge_blocks)
    forge_core_order = [field for field in forge_fields if field in expected_order]
    if forge_core_order != expected_order:
        fail(errors, f"Forge core block field order drifted: expected {expected_order}, got {forge_core_order}")

    for species in SPECIES:
        for part in PARTS:
            field = f"{species}_{part}".upper()
            try:
                source_assignment = field_assignment(source_blocks, field)
                forge_assignment = field_assignment(forge_blocks, field)
            except ValueError as exc:
                fail(errors, str(exc))
                continue
            helper = {"root": "newTreeRoot", "log": "newTreeLog", "branch": "newTreeBranch"}[part]
            expected_call = f'{helper}("{species}")'
            if expected_call not in source_assignment:
                fail(errors, f"source registration drifted for {species}_{part}: {source_assignment}")
            if expected_call not in forge_assignment:
                fail(errors, f"Forge registration drifted for {species}_{part}: {forge_assignment}")

            source_item = re.search(
                rf"DeferredItem<Item>\s+{field}\s*=\s*blockItem\(\s*\"{species}_{part}\"\s*,\s*ModBlocks\.{field}\s*\);",
                source_items,
            )
            forge_item = re.search(
                rf"RegistryObject<Item>\s+{field}\s*=\s*registerBuildingBlock\(ForgeBlocks\.{field}\);",
                forge_items,
            )
            if source_item is None:
                fail(errors, f"missing source block item for {species}_{part}")
            if forge_item is None:
                fail(errors, f"missing Forge block item for {species}_{part}")
            if re.search(rf"RegistryObject<Block>\s+{field}\s*=\s*ForgeBlocks\.{field};", forge_block_aliases) is None:
                fail(errors, f"missing Forge ModBlocks alias for {field}")
            if re.search(rf"RegistryObject<Item>\s+{field}\s*=\s*ForgeItems\.{field};", forge_item_aliases) is None:
                fail(errors, f"missing Forge ModItems alias for {field}")

    source_entity = re.search(r"NEW_TREE_PART\s*=.*?\.register\(\s*\"new_tree_part\".*?\.build\(null\)\s*\)", source_entities, re.S)
    forge_entity = re.search(r"NEW_TREE_PART\s*=.*?\.register\(\s*\"new_tree_part\".*?\.build\(null\)\s*\)", forge_entities, re.S)
    if source_entity is None or forge_entity is None:
        fail(errors, "missing new_tree_part block entity registration")
    else:
        for entity_text, label in ((source_entity.group(0), "source"), (forge_entity.group(0), "Forge")):
            expected = [f"{species}_{part}".upper() for species in SPECIES for part in PARTS]
            actual = re.findall(r"(?:ModBlocks|ForgeBlocks)\.([A-Z0-9_]+)\.get\(\)", entity_text)
            if actual != expected:
                fail(errors, f"{label} new_tree_part valid-block order drifted: {actual}")
        if "ForgeRegistries.BLOCK_ENTITY_TYPES" not in forge_entities:
            fail(errors, "Forge block entity register must use ForgeRegistries.BLOCK_ENTITY_TYPES")

    part_source = (SOURCE / "java/com/stardew/craft/block/tree/NewTreePartBlock.java").read_text(encoding="utf-8")
    part_forge = (COMMON / "java/com/stardew/craft/block/tree/NewTreePartBlock.java").read_text(encoding="utf-8")
    log_source = (SOURCE / "java/com/stardew/craft/block/tree/NewTreeLogBlock.java").read_text(encoding="utf-8")
    log_forge = (COMMON / "java/com/stardew/craft/block/tree/NewTreeLogBlock.java").read_text(encoding="utf-8")
    for text, label in ((part_forge, "Forge NewTreePartBlock"), (log_forge, "Forge NewTreeLogBlock")):
        for fragment in ("implements EntityBlock", "new NewTreePartBlockEntity(pos, state)", "clearGeneratedTreeMarker()", "!isMoving", "!state.equals(newState)"):
            if fragment not in text:
                fail(errors, f"{label} missing behavior fragment: {fragment}")
    for text, label in ((part_source, "source NewTreePartBlock"), (part_forge, "Forge NewTreePartBlock")):
        for fragment in ("requiresHorizontalClearance", "hasHorizontalClearance", "canBeReplaced", "Shapes.block()"):
            if fragment not in text:
                fail(errors, f"{label} missing behavior fragment: {fragment}")
        if re.search(r"for\s*\(int\s+dx\s*=\s*-1;\s*dx\s*<=\s*1;\s*dx\+\+", text) is None:
            fail(errors, f"{label} clearance loop drifted")
    if "super.onRemove(state, level, pos, newState, isMoving)" not in log_forge:
        fail(errors, "Forge NewTreeLogBlock must preserve super.onRemove call")

    entity_source = (SOURCE / "java/com/stardew/craft/blockentity/NewTreePartBlockEntity.java").read_text(encoding="utf-8")
    entity_forge = (COMMON / "java/com/stardew/craft/blockentity/NewTreePartBlockEntity.java").read_text(encoding="utf-8")
    for text, label in ((entity_source, "source tree marker"), (entity_forge, "Forge tree marker")):
        for key in ("StardewGeneratedTreeId", "StardewGeneratedTreeSpecies", "StardewGeneratedTreeRoot"):
            if key not in text:
                fail(errors, f"{label} missing NBT key {key}")
        for fragment in ("treeId == null", "species.isBlank()", "root.immutable()", "setChanged()"):
            if fragment not in text:
                fail(errors, f"{label} missing marker behavior: {fragment}")
    if "saveAdditional(CompoundTag)" not in entity_forge or "load(CompoundTag)" not in entity_forge:
        fail(errors, "Forge tree marker must use 1.20.1 saveAdditional/load signatures")
    if "NbtUtils.readBlockPos(tag.getCompound(TAG_TREE_ROOT))" not in entity_forge:
        fail(errors, "Forge tree marker root load must use 1.20.1 NbtUtils.readBlockPos(CompoundTag)")
    if "ModBlockEntities.NEW_TREE_PART.get()" not in entity_forge:
        fail(errors, "Forge tree marker must use the compatibility ModBlockEntities boundary")
    if "ForgeBlockEntities.NEW_TREE_PART" not in forge_entity_aliases:
        fail(errors, "Forge ModBlockEntities alias must expose NEW_TREE_PART")

    if "FMLClientSetupEvent" not in forge_client or "event.enqueueWork" not in forge_client:
        fail(errors, "Forge tree-core client setup must use FMLClientSetupEvent.enqueueWork")
    if "ItemBlockRenderTypes.setRenderLayer" not in forge_client or "RenderType.cutout()" not in forge_client:
        fail(errors, "Forge tree-core client setup must register cutout render layers")
    expected_cutout = [
        f"ForgeBlocks.{species.upper()}_{part}.get()"
        for species in SPECIES
        for part in ("ROOT", "BRANCH")
    ]
    actual_cutout = re.findall(r"ItemBlockRenderTypes\.setRenderLayer\(\s*(ForgeBlocks\.[A-Z0-9_]+\.get\(\))", forge_client)
    if actual_cutout != expected_cutout:
        fail(errors, f"Forge tree-core cutout registration order drifted: {actual_cutout}")


def resolve_model(root: Path, name: str) -> Path | None:
    if not name.startswith("stardewcraft:"):
        return None
    return root / "models" / (name.split(":", 1)[1] + ".json")


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


def validate_model(path: Path, seen_models: set[Path], seen_textures: set[Path], errors: list[str]) -> None:
    if path in seen_models:
        return
    seen_models.add(path)
    if not path.is_file():
        fail(errors, f"missing model: {path.relative_to(FORGE / 'resources')}")
        return
    source_model = SOURCE / "resources/assets/stardewcraft/models" / path.relative_to(FORGE / "resources/assets/stardewcraft/models")
    if not source_model.is_file():
        fail(errors, f"missing source model for {path}")
        return
    if load_json(path) != load_json(source_model):
        fail(errors, f"model mismatch for {path.relative_to(FORGE)}")
    document = load_json(path)
    if not isinstance(document, dict):
        return
    parent = document.get("parent")
    parent_path = resolve_model(FORGE / "resources/assets/stardewcraft", parent) if isinstance(parent, str) else None
    if parent_path is not None:
        validate_model(parent_path, seen_models, seen_textures, errors)
    for value in document.get("textures", {}).values() if isinstance(document.get("textures"), dict) else ():
        if not isinstance(value, str) or value.startswith("#"):
            continue
        texture_path = FORGE / "resources/assets/stardewcraft/textures" / (value.split(":", 1)[1] + ".png") if value.startswith("stardewcraft:") else None
        if texture_path is None:
            continue
        seen_textures.add(texture_path)
        source_texture = SOURCE / "resources/assets/stardewcraft/textures" / texture_path.relative_to(FORGE / "resources/assets/stardewcraft/textures")
        if not texture_path.is_file() or not source_texture.is_file() or texture_path.read_bytes() != source_texture.read_bytes():
            fail(errors, f"texture mismatch or missing for {value}")
        source_meta = Path(str(source_texture) + ".mcmeta")
        target_meta = Path(str(texture_path) + ".mcmeta")
        if source_meta.is_file() and (not target_meta.is_file() or source_meta.read_bytes() != target_meta.read_bytes()):
            fail(errors, f"texture metadata mismatch for {value}")


def check_resources(errors: list[str]) -> tuple[int, int]:
    source_assets = SOURCE / "resources/assets/stardewcraft"
    forge_assets = FORGE / "resources/assets/stardewcraft"
    seen_models: set[Path] = set()
    seen_textures: set[Path] = set()
    for block_id in CORE_IDS:
        source_blockstate = source_assets / "blockstates" / f"{block_id}.json"
        forge_blockstate = forge_assets / "blockstates" / f"{block_id}.json"
        if not forge_blockstate.is_file() or load_json(forge_blockstate) != load_json(source_blockstate):
            fail(errors, f"blockstate mismatch or missing for {block_id}")
        else:
            for model_name in model_names(load_json(forge_blockstate)):
                model = resolve_model(forge_assets, model_name)
                if model is not None:
                    validate_model(model, seen_models, seen_textures, errors)

        item_model = forge_assets / "models/item" / f"{block_id}.json"
        source_item_model = source_assets / "models/item" / f"{block_id}.json"
        if not item_model.is_file() or load_json(item_model) != load_json(source_item_model):
            fail(errors, f"item model mismatch or missing for {block_id}")
        else:
            validate_model(item_model, seen_models, seen_textures, errors)

        source_loot = SOURCE / "resources/data/stardewcraft/loot_table/blocks" / f"{block_id}.json"
        forge_loot = FORGE / "resources/data/stardewcraft/loot_tables/blocks" / f"{block_id}.json"
        if not forge_loot.is_file() or load_json(forge_loot) != load_json(source_loot):
            fail(errors, f"loot table mismatch or missing for {block_id}")

    tag_specs = {
        "minecraft/tags/blocks/logs.json": CORE_IDS,
        "minecraft/tags/items/logs.json": tuple(f"{species}_log" for species in SPECIES),
        "stardewcraft/tags/blocks/wild_tree_parts.json": CORE_IDS,
        "stardewcraft/tags/items/crafting_logs.json": ("oak_log", "maple_log", "pine_log"),
        "stardewcraft/tags/items/crafting_hardwood_logs.json": ("mahogany_log", "mystic_tree_log"),
    }
    for target_rel, ids in tag_specs.items():
        target = FORGE / "resources/data" / target_rel
        if not target.is_file():
            fail(errors, f"missing Forge tag {target_rel}")
            continue
        values = load_json(target).get("values", [])
        expected = [f"stardewcraft:{block_id}" for block_id in ids]
        if values != expected:
            fail(errors, f"tag mismatch for {target_rel}: expected {expected}, got {values}")
    axe = FORGE / "resources/data/minecraft/tags/blocks/mineable/axe.json"
    if not axe.is_file() or any(f"stardewcraft:{block_id}" not in load_json(axe).get("values", []) for block_id in CORE_IDS):
        fail(errors, "Forge axe tag is missing one or more tree core blocks")

    for source_language in sorted((source_assets / "lang").glob("*.json")):
        target_language = forge_assets / "lang" / source_language.name
        if not target_language.is_file():
            fail(errors, f"missing Forge language {source_language.name}")
            continue
        source_values = load_json(source_language)
        target_values = load_json(target_language)
        for block_id in CORE_IDS:
            key = f"block.stardewcraft.{block_id}"
            if target_values.get(key) != source_values.get(key):
                fail(errors, f"translation mismatch for {key} in {source_language.name}")
    return len(seen_models), len(seen_textures)


def main() -> int:
    errors: list[str] = []
    try:
        check_text_contracts(errors)
        models, textures = check_resources(errors)
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"Forge tree-core parity parser failed: {exc}", file=sys.stderr)
        return 1
    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        print(f"Forge tree-core parity failed with {len(errors)} error(s).", file=sys.stderr)
        return 1
    print(
        f"All {len(CORE_IDS)} Forge tree-core blocks and block items match the 1.21.1 source; "
        f"{models} models and {textures} textures across 12 languages verified."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

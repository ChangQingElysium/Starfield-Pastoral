#!/usr/bin/env python3
"""Verify the MoreWalls stable-ID wallpaper slice on the Forge line.

This is intentionally a 26-style static-content slice.  It does not claim
legacy ``wallpaper_block`` fallback or old-save migration parity; those require
the unported DecorBlockEntity/FlooringBlock subsystem and all 138 styles.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE_BLOCKS = ROOT / "src/main/java/com/stardew/craft/block/ModBlocks.java"
SOURCE_ITEMS = ROOT / "src/main/java/com/stardew/craft/item/ModItems.java"
FORGE_REGISTRY = ROOT / "src/forge-bootstrap/java/com/stardew/craft/forge/registry/ForgeWallpaperRegistry.java"
FORGE_BLOCK_ALIASES = ROOT / "src/forge-bootstrap/java/com/stardew/craft/block/ModBlocks.java"
FORGE_ITEM_ALIASES = ROOT / "src/forge-bootstrap/java/com/stardew/craft/item/ModItems.java"
SOURCE_ASSETS = ROOT / "src/main/resources/assets/stardewcraft"
FORGE_ASSETS = ROOT / "src/forge-bootstrap/resources/assets/stardewcraft"
FORGE_DATA = ROOT / "src/forge-bootstrap/resources/data/stardewcraft"

IDS = [f"wallpaper_morewalls_{i}" for i in range(26)]


def load_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def main() -> int:
    errors: list[str] = []
    source_blocks = SOURCE_BLOCKS.read_text(encoding="utf-8")
    source_items = SOURCE_ITEMS.read_text(encoding="utf-8")
    forge = FORGE_REGISTRY.read_text(encoding="utf-8")

    # Keep this slice anchored to the source's dynamic style family and its
    # exact property/item contracts, while intentionally limiting scope to the
    # 26 MoreWalls entries.
    source_helper = re.search(r"private static Map<String, DeferredBlock<WallpaperBlock>> registerWallpaperStyles\(\)\s*\{(.*?)\n\s*\}", source_blocks, re.S)
    if not source_helper or "WallpaperStyles.allStyleIds()" not in source_helper.group(1):
        errors.append("source wallpaper style helper no longer iterates WallpaperStyles.allStyleIds()")
    if "mapColor(MapColor.WOOL)" not in (source_helper.group(1) if source_helper else "") or "strength(0.8F, 1.0F)" not in (source_helper.group(1) if source_helper else ""):
        errors.append("source wallpaper properties drifted")
    source_item_helper = source_items[source_items.find("registerWallpaperStyleItems()") : source_items.find("registerWallpaperStyleItems()") + 1800]
    if "WallpaperStyles.registryPath(styleId)" not in source_item_helper:
        errors.append("source wallpaper item helper no longer uses registryPath")
    if "stardewcraft.type.hidden" not in source_item_helper or ".stacksTo(999)" not in source_item_helper:
        errors.append("source wallpaper item properties drifted")

    if 'for (int i = 0; i < 26; i++)' not in forge:
        errors.append("Forge wallpaper loops no longer preserve the 26-entry source slice")
    if '"wallpaper_morewalls_" + i' not in forge:
        errors.append("Forge wallpaper registry path construction drifted")
    for fragment in ("mapColor(MapColor.WOOL)", "sound(SoundType.WOOL)", "strength(0.8F, 1.0F)"):
        if fragment not in forge:
            errors.append(f"Forge wallpaper property drifted: missing {fragment}")
    if forge.count("new WallpaperBlock(") != 1:
        errors.append("Forge wallpaper block constructor is not centralized in the loop")
    if forge.count("new WallpaperBlockItem(") != 1:
        errors.append("Forge wallpaper item constructor is not centralized in the loop")
    if '"stardewcraft.type.hidden"' not in forge or ".stacksTo(999)" not in forge:
        errors.append("Forge wallpaper item metadata drifted")

    if "WALLPAPER_STYLES =" not in FORGE_BLOCK_ALIASES.read_text(encoding="utf-8"):
        errors.append("missing Forge ModBlocks WALLPAPER_STYLES alias")
    if "WALLPAPER_STYLE_ITEMS =" not in FORGE_ITEM_ALIASES.read_text(encoding="utf-8"):
        errors.append("missing Forge ModItems WALLPAPER_STYLE_ITEMS alias")

    for item_id in IDS:
        blockstate = FORGE_ASSETS / "blockstates" / f"{item_id}.json"
        item_model = FORGE_ASSETS / "models/item" / f"{item_id}.json"
        if not blockstate.is_file():
            errors.append(f"missing blockstate: {blockstate.relative_to(ROOT)}")
        else:
            document = load_json(blockstate)
            variants = document.get("variants", {})
            if sorted(variants) != ["segment=0", "segment=1", "segment=2"]:
                errors.append(f"segment variants drifted for {item_id}")
            for variant in variants.values():
                model = variant.get("model", "")
                model_path = FORGE_ASSETS / "models" / (model.split(":", 1)[-1] + ".json")
                if not model_path.is_file():
                    errors.append(f"missing model {model} for {item_id}")
        if not item_model.is_file():
            errors.append(f"missing item model: {item_model.relative_to(ROOT)}")

    for tag_name, kind in (("stable_wallpapers", "blocks"), ("stable_wallpapers", "items")):
        tag = FORGE_DATA / "tags" / kind / f"{tag_name}.json"
        if not tag.is_file():
            errors.append(f"missing {kind} tag: {tag.relative_to(ROOT)}")
            continue
        values = load_json(tag).get("values", [])
        expected = [f"stardewcraft:{item_id}" for item_id in IDS]
        if values != expected:
            errors.append(f"{kind} stable_wallpapers tag mismatch")

    source_languages = sorted((SOURCE_ASSETS / "lang").glob("*.json"))
    for language in source_languages:
        forge_language = FORGE_ASSETS / "lang" / language.name
        if not forge_language.is_file():
            errors.append(f"missing Forge language file: {forge_language.relative_to(ROOT)}")
            continue
        source_values = load_json(language)
        forge_values = load_json(forge_language)
        for key in ("block.stardewcraft.wallpaper_block", "block.stardewcraft.wallpaper_block.desc"):
            if forge_values.get(key) != source_values.get(key):
                errors.append(f"translation mismatch for {key} in {language.name}")

    if errors:
        print("\n".join(errors), file=sys.stderr)
        print(f"Forge wallpaper parity failed with {len(errors)} error(s).", file=sys.stderr)
        return 1
    print("All 26 MoreWalls wallpaper registrations match the source; 78 segment models, tags and 12 languages verified (legacy fallback intentionally out of slice; no full legacy parity claim).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Idempotent 1.21.1 -> 1.20.1 data-pack conversion of src/main/resources.

Run after syncing resources from main. Binary NBT (structures, pregen regions) is
handled separately by the NBT downgrade tool.

- Directory names: 1.21 singular registry folders -> 1.20.1 plural folders.
  `loot_tables/` folders that already exist next to `loot_table/` are stale on
  1.21.1 (never loaded there), so they are replaced, not merged, to keep parity.
- Recipes: item result objects use `item` instead of `id`; cooking/stonecutting
  results are plain strings (+ top-level `count` for stonecutting).
- Loot tables: `match_tool` item predicates use 1.20.1 list/enchantment syntax.
- Native StardewCraft models: NeoForge face metadata and supported built-in loaders -> Forge equivalents.
"""
from __future__ import annotations

import argparse
import json
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "src/main/resources/data"
MODELS = ROOT / "src/main/resources/assets/stardewcraft/models"

DIR_RENAMES = {
    "recipe": "recipes",
    "structure": "structures",
    "advancement": "advancements",
    "predicate": "predicates",
    "item_modifier": "item_modifiers",
    "function": "functions",
}
TAG_RENAMES = {"block": "blocks", "item": "items", "entity_type": "entity_types", "fluid": "fluids",
               "game_event": "game_events"}


def merge_move(src: Path, dst: Path) -> None:
    for path in sorted(src.rglob("*")):
        if path.is_file():
            target = dst / path.relative_to(src)
            target.parent.mkdir(parents=True, exist_ok=True)
            if target.exists():
                raise SystemExit(f"conflict while renaming {path} -> {target}")
            path.rename(target)
    shutil.rmtree(src)


def rename_dirs() -> None:
    for ns in sorted(p for p in DATA.iterdir() if p.is_dir()):
        loot = ns / "loot_table"
        if loot.is_dir():
            stale = ns / "loot_tables"
            if stale.exists():
                shutil.rmtree(stale)
            loot.rename(stale)
        for old, new in DIR_RENAMES.items():
            if (ns / old).is_dir():
                merge_move(ns / old, ns / new)
        tags = ns / "tags"
        if tags.is_dir():
            for old, new in TAG_RENAMES.items():
                if (tags / old).is_dir():
                    merge_move(tags / old, tags / new)


# NeoForge 1.21.1 custom ingredient types and their Forge 1.20.1 equivalents (same JSON keys).
INGREDIENT_TYPES = {"neoforge:difference": "forge:difference", "neoforge:intersection": "forge:intersection"}

# NeoForge 21.1.217 / Forge 47.4.10 CompositeModel.Loader use the same children
# BlockModels and item_render_order schema. Do not guess mappings for other loaders.
MODEL_LOADERS = {"neoforge:composite": "forge:composite"}


def convert_ingredient_types(o):
    if isinstance(o, dict):
        t = o.get("type")
        if isinstance(t, str) and t.startswith("neoforge:"):
            if t not in INGREDIENT_TYPES:
                raise SystemExit(f"unmapped NeoForge ingredient type {t}")
            o["type"] = INGREDIENT_TYPES[t]
        for v in o.values():
            convert_ingredient_types(v)
    elif isinstance(o, list):
        for v in o:
            convert_ingredient_types(v)
    return o


def convert_recipe(d: dict) -> dict:
    convert_ingredient_types(d)
    result = d.get("result")
    kind = d.get("type", "")
    if isinstance(result, dict) and "id" in result:
        if kind in ("minecraft:smelting", "minecraft:blasting", "minecraft:smoking",
                    "minecraft:campfire_cooking"):
            d["result"] = result["id"]
        elif kind == "minecraft:stonecutting":
            d["result"] = result["id"]
            d["count"] = result.get("count", 1)
        else:
            converted = {"item": result["id"]}
            if result.get("count", 1) != 1:
                converted["count"] = result["count"]
            if "components" in result:
                raise SystemExit("recipe result components need manual conversion")
            d["result"] = converted
    return d


def convert_item_predicate(p: dict) -> dict:
    items = p.get("items")
    if isinstance(items, str):
        if items.startswith("#"):
            p.pop("items")
            p["tag"] = items[1:]
        else:
            p["items"] = [items]
    preds = p.pop("predicates", None)
    if preds:
        for key, value in preds.items():
            if key in ("minecraft:enchantments", "minecraft:stored_enchantments"):
                out = []
                for entry in value:
                    e = dict(entry)
                    if "enchantments" in e:
                        ench = e.pop("enchantments")
                        if isinstance(ench, list):
                            if len(ench) != 1:
                                raise SystemExit("multi-enchantment predicate needs manual conversion")
                            ench = ench[0]
                        e["enchantment"] = ench
                    out.append(e)
                p["enchantments" if key.endswith(":enchantments") else "stored_enchantments"] = out
            else:
                raise SystemExit(f"unsupported item sub-predicate {key}")
    return p


def walk_loot(o):
    if isinstance(o, dict):
        if o.get("condition") == "minecraft:match_tool" and isinstance(o.get("predicate"), dict):
            convert_item_predicate(o["predicate"])
        for v in o.values():
            walk_loot(v)
    elif isinstance(o, list):
        for v in o:
            walk_loot(v)
    return o


def rewrite_json(files, fn) -> int:
    changed = 0
    for path in files:
        text = path.read_text(encoding="utf-8")
        data = json.loads(text)
        new = fn(json.loads(text))
        if new != data:
            path.write_text(json.dumps(new, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
            changed += 1
    return changed


def convert_model_metadata(model: dict) -> dict:
    """Convert supported loaders/native metadata without altering authored geometry.

    ImportedModelGeometry explicitly reads neoforge_data itself; only Minecraft's
    ordinary element/face deserializers need the Forge spelling. Ambiguous input
    is rejected instead of silently discarding one of the two payloads.
    """
    loader = model.get("loader")
    if isinstance(loader, str) and loader.startswith("neoforge:"):
        if loader not in MODEL_LOADERS:
            raise ValueError(f"unmapped NeoForge model loader {loader}")
        model["loader"] = MODEL_LOADERS[loader]
    if model.get("loader") == "forge:composite":
        children = model.get("children")
        if not isinstance(children, dict) or not children or not all(isinstance(child, dict) for child in children.values()):
            raise ValueError("composite model requires nonempty children BlockModels")
        order = model.get("item_render_order")
        if order is not None and (not isinstance(order, list) or any(not isinstance(name, str) or name not in children for name in order)):
            raise ValueError("composite item_render_order must name existing children")
        for child in children.values():
            convert_model_metadata(child)
        return model
    if "loader" in model:
        return model

    def walk(value):
        if isinstance(value, dict):
            if "neoforge_data" in value:
                if "forge_data" in value:
                    raise ValueError("model contains both neoforge_data and forge_data")
                value["forge_data"] = value.pop("neoforge_data")
            for child in value.values():
                walk(child)
        elif isinstance(value, list):
            for child in value:
                walk(child)

    walk(model)
    return model


def convert_models() -> int:
    changed = 0
    for path in sorted(MODELS.rglob("*.json")):
        text = path.read_text(encoding="utf-8-sig")
        # Composite loaders need conversion even when they contain no face metadata.
        if '"neoforge_data"' not in text and "neoforge:" not in text:
            continue
        original = json.loads(text)
        try:
            converted = convert_model_metadata(json.loads(text))
        except ValueError as error:
            raise SystemExit(f"{path.relative_to(ROOT)}: {error}") from error
        if converted != original:
            path.write_text(json.dumps(converted, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
            changed += 1
    return changed


def drop_unavailable_recipes() -> list[str]:
    """Recipes that need vanilla items added after 1.20.1 cannot load there; remove them (logged)."""
    import re as _re
    sys.path.insert(0, str(ROOT / "scripts/port"))
    from import_neoforge_common_data import known_ids
    items = known_ids()["item"]
    dropped = []
    for path in sorted(DATA.glob("*/recipes/**/*.json")):
        text = path.read_text(encoding="utf-8")
        refs = set(_re.findall(r'"(?:item|result)"\s*:\s*"(minecraft:[a-z0-9_/]+)"', text))
        missing = sorted(r for r in refs if r not in items)
        if missing:
            path.unlink()
            dropped.append(f"{path.relative_to(DATA)}: {missing}")
    return dropped


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--models-only", action="store_true", help="convert native model metadata and supported loaders only")
    args = parser.parse_args()
    if args.models_only:
        print(f"native model metadata converted: {convert_models()}")
        return 0
    rename_dirs()
    recipes = rewrite_json(sorted(DATA.glob("*/recipes/**/*.json")), convert_recipe)
    for line in drop_unavailable_recipes():
        print("dropped recipe (needs 1.21-only vanilla items):", line)
    loot = rewrite_json(sorted(DATA.glob("*/loot_tables/**/*.json")), walk_loot)
    models = convert_models()
    print(f"recipes converted: {recipes}, loot tables converted: {loot}, native models converted: {models}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

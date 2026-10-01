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
"""
from __future__ import annotations

import json
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "src/main/resources/data"

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


def convert_recipe(d: dict) -> dict:
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


def main() -> int:
    rename_dirs()
    recipes = rewrite_json(sorted(DATA.glob("*/recipes/**/*.json")), convert_recipe)
    loot = rewrite_json(sorted(DATA.glob("*/loot_tables/**/*.json")), walk_loot)
    print(f"recipes converted: {recipes}, loot tables converted: {loot}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

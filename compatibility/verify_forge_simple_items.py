#!/usr/bin/env python3
"""Verify that the Forge SimpleStardewItem slice preserves the 1.21.1 source contract."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE_ITEMS = ROOT / "src/main/java/com/stardew/craft/item/ModItems.java"
FORGE_ITEMS = ROOT / "src/forge-bootstrap/java/com/stardew/craft/forge/registry/ForgeItems.java"
SOURCE_ASSETS = ROOT / "src/main/resources/assets/stardewcraft"
FORGE_ASSETS = ROOT / "src/forge-bootstrap/resources/assets/stardewcraft"

DIRECT_PATTERN = re.compile(
    r'ITEMS\.register\(\s*"([a-z0-9_]+)"\s*,\s*\(\)\s*->\s*new SimpleStardewItem\('
    r'"([^"]+)",\s*(-?\d+),\s*new Item\.Properties\(\)(.*?)\)\s*\)\s*;',
    re.S,
)
HELPER_PATTERN = re.compile(
    r'public static final RegistryObject<Item>\s+[A-Z0-9_]+\s*=\s*registerSimple\('
    r'"([a-z0-9_]+)",\s*"([^"]+)",\s*(-?\d+)\);'
)


def exact_properties(raw: str) -> str:
    normalized = " ".join(raw.split())
    known = {
        ".stacksTo(999)": ".stacksTo(999)",
        ".stacksTo(1)": ".stacksTo(1)",
        ".stacksTo(1).durability(20)": ".stacksTo(1).durability(20)",
    }
    if normalized not in known:
        raise ValueError(f"unsupported Item.Properties chain: {normalized!r}")
    return known[normalized]


def parse_direct(path: Path) -> dict[str, tuple[str, int, str]]:
    text = path.read_text(encoding="utf-8")
    result: dict[str, tuple[str, int, str]] = {}
    for match in DIRECT_PATTERN.finditer(text):
        item_id, type_key, price, properties = match.groups()
        if item_id in result:
            raise ValueError(f"duplicate direct registration {item_id} in {path}")
        result[item_id] = (type_key, int(price), exact_properties(properties))
    return result


def parse_forge() -> dict[str, tuple[str, int, str]]:
    result = parse_direct(FORGE_ITEMS)
    text = FORGE_ITEMS.read_text(encoding="utf-8")
    for item_id, type_key, price in HELPER_PATTERN.findall(text):
        if item_id in result:
            raise ValueError(f"duplicate Forge registration {item_id}")
        result[item_id] = (type_key, int(price), ".stacksTo(999)")
    return result


def load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8-sig"))


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
    parent = document.get("parent")
    parent_path = resolve_model(parent) if isinstance(parent, str) else None
    if parent_path is not None:
        validate_model(parent_path, seen, errors)
    for value in document.get("textures", {}).values():
        texture = resolve_texture(value) if isinstance(value, str) else None
        if texture is not None and not texture.is_file():
            errors.append(f"missing texture {value} referenced by {path.relative_to(ROOT)}")
    for override in document.get("overrides", []):
        model = override.get("model") if isinstance(override, dict) else None
        override_path = resolve_model(model) if isinstance(model, str) else None
        if override_path is not None:
            validate_model(override_path, seen, errors)


def main() -> int:
    errors: list[str] = []
    try:
        expected = parse_direct(SOURCE_ITEMS)
        actual = parse_forge()
    except (OSError, ValueError) as exc:
        print(f"Forge simple-item parity parser failed: {exc}", file=sys.stderr)
        return 1

    for item_id in sorted(expected.keys() | actual.keys()):
        if item_id not in actual:
            errors.append(f"missing Forge registration: {item_id}")
        elif item_id not in expected:
            errors.append(f"unexpected Forge simple registration: {item_id}")
        elif actual[item_id] != expected[item_id]:
            errors.append(
                f"registration mismatch for {item_id}: expected {expected[item_id]}, got {actual[item_id]}"
            )

    seen_models: set[Path] = set()
    for item_id in expected:
        validate_model(FORGE_ASSETS / "models/item" / f"{item_id}.json", seen_models, errors)

    source_languages = sorted((SOURCE_ASSETS / "lang").glob("*.json"))
    for source_language in source_languages:
        target_language = FORGE_ASSETS / "lang" / source_language.name
        if not target_language.is_file():
            errors.append(f"missing Forge language file: {target_language.relative_to(ROOT)}")
            continue
        source_values = load_json(source_language)
        target_values = load_json(target_language)
        for item_id in expected:
            key = f"item.stardewcraft.{item_id}"
            if target_values.get(key) != source_values.get(key):
                errors.append(f"translation mismatch for {key} in {source_language.name}")

    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        print(f"Forge simple-item parity failed with {len(errors)} error(s).", file=sys.stderr)
        return 1

    print(
        f"All {len(expected)} SimpleStardewItem registrations match the 1.21.1 source; "
        f"{len(seen_models)} model files and {len(source_languages)} languages verified."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

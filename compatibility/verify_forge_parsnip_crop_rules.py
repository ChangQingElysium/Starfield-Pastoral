#!/usr/bin/env python3
"""Verify the closed Forge parsnip agriculture-rule slice.

The complete crop block/item runtime is intentionally deferred until its
Forge dependency closure is migrated.  This checker therefore compares the
loader-independent contract against the NeoForge source and verifies that the
source-side resource closure is present, without pretending those resources
are already shipped by the Forge bootstrap source set.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "src/main/java/com/stardew/craft"
CROP = SOURCE / "block/crop/ParsnipCropBlock.java"
SEED = SOURCE / "item/crop/spring/ParsnipSeedItem.java"
ITEM = SOURCE / "item/crop/spring/ParsnipItem.java"
BASE = SOURCE / "block/crop/StardewCropBlock.java"
SOURCE_RES = ROOT / "src/main/resources"


def fail(errors: list[str], message: str) -> None:
    errors.append(message)


def require(path: Path, errors: list[str]) -> str:
    if not path.is_file():
        fail(errors, f"missing source contract file: {path.relative_to(ROOT)}")
        return ""
    return path.read_text(encoding="utf-8")


def extract_int_array(text: str, field: str) -> tuple[int, ...] | None:
    match = re.search(
        rf"{re.escape(field)}\s*=\s*(?:new\s+int\[\]\s*)?\{{([^}}]+)\}}",
        text,
    )
    if match is None:
        return None
    return tuple(int(value) for value in re.findall(r"-?\d+", match.group(1)))


def extract_return(text: str, method: str) -> str | None:
    match = re.search(
        rf"{re.escape(method)}\s*\([^)]*\)\s*\{{(?P<body>.*?)\}}",
        text,
        re.S,
    )
    if match is None:
        return None
    return match.group("body")


def parse_list_numbers(text: str, expression: str) -> tuple[int, ...] | None:
    match = re.search(expression, text)
    if match is None:
        return None
    return tuple(int(value) for value in re.findall(r"-?\d+", match.group(1)))


def verify_source_contract(errors: list[str]) -> dict[str, object]:
    crop = require(CROP, errors)
    seed = require(SEED, errors)
    item = require(ITEM, errors)
    base = require(BASE, errors)

    phase_days = extract_int_array(crop, "PHASE_DAYS")
    if phase_days != (1, 1, 1, 1):
        fail(errors, f"NeoForge parsnip phase days drifted: {phase_days}")

    if "seasonForGrowth() == 0" not in crop:
        fail(errors, "NeoForge parsnip season gate is not spring (season 0)")

    regrow_body = extract_return(crop, "canRegrow")
    if regrow_body is None or not re.search(r"return\s+false\s*;", regrow_body):
        fail(errors, "NeoForge parsnip must remain non-regrowing")

    regrow_days_body = extract_return(crop, "getRegrowDays")
    if regrow_days_body is None or not re.search(r"return\s+0\s*;", regrow_days_body):
        fail(errors, "NeoForge parsnip regrow-days contract drifted")

    display_body = extract_return(crop, "getCropDisplayNameKey")
    if display_body is None or 'item.stardewcraft.parsnip' not in display_body:
        fail(errors, "NeoForge parsnip display key drifted")

    if "ModItems.PARSNIP_SEEDS" not in crop or "ModBlocks.PARSNIP_CROP" not in seed:
        fail(errors, "NeoForge parsnip seed placement IDs drifted")
    seed_price = re.search(r"getSellPrice\s*\([^)]*\)\s*\{.*?return\s+(\d+)\s*;", seed, re.S)
    if seed_price is None or int(seed_price.group(1)) != 5:
        fail(errors, "NeoForge parsnip seed sell price drifted")

    sell_prices = parse_list_numbers(item, r"SELL_PRICE_BY_QUALITY\s*=\s*new\s+int\[\]\s*\{([^}]+)\}")
    energies = parse_list_numbers(item, r"ENERGY_BY_QUALITY\s*=\s*new\s+int\[\]\s*\{([^}]+)\}")
    health = parse_list_numbers(item, r"HEALTH_BY_QUALITY\s*=\s*new\s+int\[\]\s*\{([^}]+)\}")
    if sell_prices != (35, 43, 52, 70):
        fail(errors, f"NeoForge parsnip quality prices drifted: {sell_prices}")
    if energies != (25, 35, 45, 65):
        fail(errors, f"NeoForge parsnip quality energy drifted: {energies}")
    if health != (11, 15, 20, 29):
        fail(errors, f"NeoForge parsnip quality health drifted: {health}")

    xp_match = re.search(r'CROP_FARMING_XP\.put\("parsnip",\s*(\d+)\)', base)
    if xp_match is None or int(xp_match.group(1)) != 8:
        fail(errors, "NeoForge parsnip farming XP drifted")

    return {
        "phase_days": phase_days,
        "season": "spring",
        "regrow_days": 0,
        "farming_experience": 8,
        "seed_sell_price": 5,
        "quality_prices": sell_prices,
        "quality_energy": energies,
        "quality_health": health,
    }


def verify_forge_boundary(errors: list[str]) -> None:
    """Prove that this audit did not smuggle in a partial Forge crop runtime."""
    forge_java = ROOT / "src/forge-common/java"
    bootstrap_java = ROOT / "src/forge-bootstrap/java"
    forbidden_names = {
        "ParsnipCropBlock.java",
        "ParsnipSeedItem.java",
        "ParsnipItem.java",
        "StardewCropBlock.java",
        "CropGrowthManager.java",
    }
    for root in (forge_java, bootstrap_java):
        for path in root.rglob("*.java"):
            if path.name in forbidden_names:
                fail(errors, f"partial Forge crop runtime unexpectedly present: {path.relative_to(ROOT)}")
            text = path.read_text(encoding="utf-8")
            if re.search(r"(?:PARSNIP_CROP|PARSNIP_SEEDS|parsnip_crop|parsnip_seeds)", text):
                fail(errors, f"partial Forge parsnip registration unexpectedly present: {path.relative_to(ROOT)}")

    bootstrap_resources = ROOT / "src/forge-bootstrap/resources"
    for path in bootstrap_resources.rglob("*"):
        if path.is_file() and re.search(r"parsnip", path.name, re.I):
            fail(errors, f"partial Forge parsnip resource unexpectedly present: {path.relative_to(ROOT)}")

    build = (ROOT / "build.gradle").read_text(encoding="utf-8")
    if "src/main/java" in re.search(r"sourceSets\s*\{(?P<body>.*?)\n\}", build, re.S).group("body"):
        fail(errors, "Forge source set must not compile the unported NeoForge crop runtime")

    # The dependency blocker is part of the contract: if one of these imports
    # disappears from the NeoForge reference, the audit must be revisited before
    # someone claims the crop chain is now closable.
    dependency_text = "\n".join(
        require(SOURCE / relative, errors)
        for relative in (
            "block/crop/StardewCropBlock.java",
            "item/quality/QualityHelper.java",
            "manager/CropGrowthManager.java",
        )
    )
    for marker in ("DataComponents", "net.neoforged", "SavedData"):
        if marker not in dependency_text:
            fail(errors, f"crop dependency blocker marker disappeared: {marker}")


def verify_source_resource_closure(errors: list[str]) -> int:
    required = [
        "assets/stardewcraft/blockstates/parsnip_crop.json",
        "assets/stardewcraft/models/item/parsnip.json",
        "assets/stardewcraft/models/item/parsnip_seeds.json",
        "assets/stardewcraft/models/block/crops/spring/parsnip_stage0.json",
        "assets/stardewcraft/models/block/crops/spring/parsnip_stage1.json",
        "assets/stardewcraft/models/block/crops/spring/parsnip_stage2.json",
        "assets/stardewcraft/models/block/crops/spring/parsnip_stage3.json",
        "data/stardewcraft/loot_tables/blocks/parsnip_crop.json",
        "data/minecraft/tags/block/crops.json",
        "data/minecraft/tags/block/mineable/hoe.json",
        "data/stardewcraft/tags/item/seeds.json",
        "data/stardewcraft/tags/item/spring_seeds.json",
        "data/stardewcraft/tags/item/crops.json",
        "data/stardewcraft/tags/item/vegetable_crops.json",
    ]
    for relative in required:
        if not (SOURCE_RES / relative).is_file():
            fail(errors, f"NeoForge parsnip resource contract missing: {relative}")

    lang_dir = SOURCE_RES / "assets/stardewcraft/lang"
    lang_files = sorted(lang_dir.glob("*.json"))
    if len(lang_files) != 12:
        fail(errors, f"expected 12 shipped languages, found {len(lang_files)}")
    for path in lang_files:
        try:
            values = json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as exc:
            fail(errors, f"invalid language JSON {path.name}: {exc}")
            continue
        for key in (
            "block.stardewcraft.parsnip_crop",
            "item.stardewcraft.parsnip",
            "item.stardewcraft.parsnip_seeds",
        ):
            if key not in values:
                fail(errors, f"{path.name} missing parsnip language key: {key}")
    return len(required)


def main() -> int:
    errors: list[str] = []
    verify_source_contract(errors)
    verify_forge_boundary(errors)
    resource_count = verify_source_resource_closure(errors)
    if errors:
        print("Forge parsnip crop-rules parity failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1
    print(
        "Forge parsnip agriculture audit passed: "
        f"four 1-day phases, spring gate, non-regrow harvest contract, "
        f"XP/seed/produce IDs, {resource_count} source resource anchors, "
        "and the no-partial-runtime boundary verified; no Forge crop runtime claimed."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

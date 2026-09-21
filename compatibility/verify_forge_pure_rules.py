#!/usr/bin/env python3
"""Verify exact parity for the loader-independent Forge rule/model batch."""

from __future__ import annotations

import hashlib
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOT = ROOT / "src/main/java/com/stardew/craft"
FORGE_ROOT = ROOT / "src/forge-common/java/com/stardew/craft"

RELATIVE_PATHS = (
    Path("animal/service/AnimalEntityProjectionPolicy.java"),
    Path("communitycenter/data/BundleDefinition.java"),
    Path("communitycenter/data/BundleIngredient.java"),
    Path("config/StackSizeHolder.java"),
    Path("emote/EmoteType.java"),
    Path("entity/bomb/BombBlastPattern.java"),
    Path("inventory/TrashCanTier.java"),
    Path("item/equipment/BootsType.java"),
    Path("item/trinket/TrinketType.java"),
    Path("joja/JojaConstants.java"),
    Path("mail/MailTextSyntax.java"),
    Path("mastery/MasteryProgress.java"),
    Path("monster/MetalHeadLoot.java"),
    Path("monster/SourceTilePath.java"),
    Path("qi/MrQiQuestAnchor.java"),
    Path("secretnote/SecretNoteStoryFlags.java"),
    Path("sewer/SewerStoryFlags.java"),
    Path("shop/ShopPurchaseRequestTracker.java"),
    Path("festival/FestivalMapOverlayPhase.java"),
    Path("festival/FestivalSessionPhase.java"),
    Path("festival/FestivalType.java"),
    Path("npc/animation/SamActivity.java"),
    Path("templates/TemplateBox.java"),
    Path("templates/GridWindowProfile.java"),
    Path("templates/RoundWindowProfile.java"),
    Path("templates/WindowFrameProfile.java"),
    Path("block/decor/NaturalDecorKind.java"),
    Path("block/decor/ParkFountainSeasons.java"),
    Path("block/decor/ParkFountainMotion.java"),
    Path("block/terrain/TerrainVariantWeights.java"),
    Path("block/utility/SprinklerTier.java"),
    Path("block/utility/WoodenChestColorPalette.java"),
)

IMPORT_RE = re.compile(r"^\s*import\s+(?:static\s+)?([^;]+);\s*$")
ALLOWED_IMPORT_PREFIXES = ("java.", "javax.")
FORBIDDEN_TEXT = (
    "net.minecraft.",
    "net.neoforged.",
    "net.minecraftforge.",
    "com.mojang.",
    "com.google.gson.",
)


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def imports(path: Path) -> list[str]:
    result: list[str] = []
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        match = IMPORT_RE.match(line)
        if match:
            result.append(match.group(1))
    return result


def main() -> int:
    errors: list[str] = []
    for relative in RELATIVE_PATHS:
        source = SOURCE_ROOT / relative
        forge = FORGE_ROOT / relative
        if not source.is_file():
            errors.append(f"missing source contract: {source.relative_to(ROOT)}")
            continue
        if not forge.is_file():
            errors.append(f"missing Forge source: {forge.relative_to(ROOT)}")
            continue
        if digest(source) != digest(forge):
            errors.append(f"Forge pure rule drifted: {relative}")
        for path in (source, forge):
            for imported in imports(path):
                if not imported.startswith(ALLOWED_IMPORT_PREFIXES):
                    errors.append(
                        f"non-pure import in {path.relative_to(ROOT)}: {imported}"
                    )
            text = path.read_text(encoding="utf-8")
            for token in FORBIDDEN_TEXT:
                if token in text:
                    errors.append(
                        f"loader reference in {path.relative_to(ROOT)}: {token}"
                    )

    if errors:
        print("Forge pure-rule parity failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print(f"Forge pure-rule parity passed for {len(RELATIVE_PATHS)} exact classes.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

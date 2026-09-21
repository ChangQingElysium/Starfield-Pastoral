#!/usr/bin/env python3
"""Compare the Forge wild-tree growth rule data with the NeoForge source."""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "src/main/java/com/stardew/craft/tree/WildTrees.java"
FORGE = ROOT / "src/forge-common/java/com/stardew/craft/tree/WildTreeGrowthRules.java"
SPECIES = ("oak", "maple", "pine", "mahogany", "mystic_tree")


def norm(value: str) -> str:
    return value.rstrip("f").lstrip("0") or "0"


def source_rule(text: str, species: str) -> tuple[str, ...] | None:
    match = re.search(rf'new Def\(\s*"{re.escape(species)}"(?P<body>.*?\n\s*\))', text, re.S)
    if match is None:
        return None
    return tuple(norm(value) for value in re.findall(r"(?<![A-Za-z_\d.])(?:\d+\.\d+|\.\d+|\d+)f", match.group("body"))[-6:])


def forge_rule(text: str, species: str) -> tuple[str, ...] | None:
    match = re.search(rf'new Species\(\s*"{re.escape(species)}"(?P<body>[^)]*)\)', text)
    if match is None:
        return None
    return tuple(norm(value) for value in re.findall(r"(?<![\d.])(?:\d+\.\d+|\.\d+|\d+)f", match.group("body")))


def main() -> int:
    source = SOURCE.read_text(encoding="utf-8")
    forge = FORGE.read_text(encoding="utf-8")
    errors: list[str] = []
    for species in SPECIES:
        expected = source_rule(source, species)
        actual = forge_rule(forge, species)
        if expected is None:
            errors.append(f"missing source growth rule: {species}")
        if actual is None:
            errors.append(f"missing Forge growth rule: {species}")
        elif expected != actual:
            errors.append(f"growth rule drift for {species}: source={expected}, Forge={actual}")
    for fragment in (
            "SAPLING1_GROWTH_STAGE = 3", "MATURE_GROWTH_STAGE = 5", "SEASON_WINTER = 3",
            "season != SEASON_WINTER || fertilized || species.growsInWinter()",
            "Math.floorMod((absoluteDay - 1) / 28, 4)",
            "growthStage >= SAPLING1_GROWTH_STAGE ? 1 : 0"):
        if fragment not in forge:
            errors.append(f"Forge growth-rule behavior missing: {fragment}")
    if errors:
        print("Forge tree-growth parity failed:")
        for error in errors:
            print(f"- {error}")
        return 1
    print("Forge tree-growth rules parity passed: five species probabilities, winter gate, day-to-season mapping and stage mapping verified.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

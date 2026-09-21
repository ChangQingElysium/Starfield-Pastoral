#!/usr/bin/env python3
"""Verify the Forge 1.20.1 mining player-state persistence slice."""
from __future__ import annotations

import sys
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "src/main/java/com/stardew/craft/mining"
FORGE = ROOT / "src/forge-common/java/com/stardew/craft/mining"


def fail(errors: list[str]) -> int:
    if errors:
        print("Forge mining player-state parity failed:", file=sys.stderr)
        print("\n".join(f"- {error}" for error in errors), file=sys.stderr)
        return 1
    print("Forge mining player-state parity passed: MiningPlayerData NBT contract verified.")
    return 0


def normalized_java(path: Path) -> str:
    """Compare implementation tokens while allowing loader-side comments."""
    text = path.read_text(encoding="utf-8")
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    text = re.sub(r"//[^\n]*", "", text)
    return "".join(text.split())


def main() -> int:
    errors: list[str] = []
    source_data = SOURCE / "MiningPlayerData.java"
    forge_data = FORGE / "MiningPlayerData.java"
    if not source_data.is_file() or not forge_data.is_file():
        errors.append("MiningPlayerData source/Forge class is missing")
    elif normalized_java(source_data) != normalized_java(forge_data):
        errors.append("MiningPlayerData implementation drifted across loaders")

    if not forge_data.is_file():
        return fail(errors)
    text = forge_data.read_text(encoding="utf-8")
    if "net.neoforged" in text:
        errors.append("Forge mining player-state class retained NeoForge imports")

    required_fragments = (
        'tag.putInt("currentFloor", currentFloor)',
        'tag.putInt("maxFloorReached", maxFloorReached)',
        'tag.putBoolean("receivedMineTotem", receivedMineTotem)',
        'this.currentFloor = tag.getInt("currentFloor")',
        'this.maxFloorReached = tag.getInt("maxFloorReached")',
        'this.receivedMineTotem = tag.getBoolean("receivedMineTotem")',
        'if (floor > maxFloorReached)',
        'public static MiningPlayerData fromNBT(CompoundTag tag)',
    )
    for fragment in required_fragments:
        if fragment not in text:
            errors.append(f"Forge mining player-state class missing contract fragment: {fragment}")

    return fail(errors)


if __name__ == "__main__":
    raise SystemExit(main())

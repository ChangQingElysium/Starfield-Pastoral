#!/usr/bin/env python3
"""Verify the Forge tree-leaves slice against the 1.21.1 source contract."""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "src/main"
FORGE = ROOT / "src/forge-bootstrap"
COMMON = ROOT / "src/forge-common"
SPECIES = ("oak", "maple", "pine", "mahogany", "mystic_tree")


def fail(errors: list[str], message: str) -> None:
    errors.append(message)


def main() -> int:
    errors: list[str] = []
    source_blocks = (SOURCE / "java/com/stardew/craft/block/ModBlocks.java").read_text(encoding="utf-8")
    forge_blocks = (FORGE / "java/com/stardew/craft/forge/registry/ForgeBlocks.java").read_text(encoding="utf-8")
    forge_aliases = (FORGE / "java/com/stardew/craft/block/ModBlocks.java").read_text(encoding="utf-8")
    source_items = (SOURCE / "java/com/stardew/craft/item/ModItems.java").read_text(encoding="utf-8")
    forge_items = (FORGE / "java/com/stardew/craft/forge/registry/ForgeItems.java").read_text(encoding="utf-8")
    item_aliases = (FORGE / "java/com/stardew/craft/item/ModItems.java").read_text(encoding="utf-8")
    client = (FORGE / "java/com/stardew/craft/forge/client/ForgeClientSetup.java").read_text(encoding="utf-8")
    leaves = (COMMON / "java/com/stardew/craft/block/tree/StardewLeavesBlock.java").read_text(encoding="utf-8")
    lighting = (COMMON / "java/com/stardew/craft/tree/SeasonalLeafLighting.java").read_text(encoding="utf-8")
    time_service = (COMMON / "java/com/stardew/craft/time/ForgeStardewTimeService.java").read_text(encoding="utf-8")
    for name in ("ForestCanopySnow.java", "PineCanopyConnections.java"):
        source_path = SOURCE / "java/com/stardew/craft/tree" / name
        forge_path = COMMON / "java/com/stardew/craft/tree" / name
        if not forge_path.is_file():
            fail(errors, f"missing Forge canopy helper: {name}")
        elif forge_path.read_text(encoding="utf-8") != source_path.read_text(encoding="utf-8"):
            fail(errors, f"Forge canopy helper drifted from source: {name}")

    for species in SPECIES:
        field = f"{species.upper()}_LEAVES"
        if not re.search(rf"{field}\s*=\s*newTreeLeaves\(\"{species}\"\)", forge_blocks):
            fail(errors, f"missing Forge leaves registration: {species}")
        if not re.search(rf"{field}\s*=\s*ForgeBlocks\.{field}", forge_aliases):
            fail(errors, f"missing Forge ModBlocks leaves alias: {species}")
        if not re.search(rf"{field}\s*=\s*registerBuildingBlock\(ForgeBlocks\.{field}\)", forge_items):
            fail(errors, f"missing Forge leaves item registration: {species}")
        if not re.search(rf"{field}\s*=\s*ForgeItems\.{field}", item_aliases):
            fail(errors, f"missing Forge ModItems leaves alias: {species}")
        if f"{field}.get(), RenderType.cutoutMipped()" not in client:
            fail(errors, f"missing cutout-mipped render layer: {species}")
        resources = [
            f"blockstates/{species}_leaves.json",
            f"models/item/{species}_leaves.json",
            f"models/block/tree/{species}/leaves.json",
        ]
        resources.extend(
            [f"textures/block/tree/{species}/leaves1.png", f"textures/block/tree/{species}/leaves2.png"]
            if species == "pine" else [f"textures/block/tree/{species}/leaves.png"]
        )
        for relative in resources:
            forge_path = FORGE / "resources/assets/stardewcraft" / relative
            source_path = SOURCE / "resources/assets/stardewcraft" / relative
            if not forge_path.is_file():
                fail(errors, f"missing Forge leaves resource: {relative}")
            elif source_path.is_file() and forge_path.read_bytes() != source_path.read_bytes():
                fail(errors, f"leaves resource drifted from source: {relative}")
        data_relative = f"stardewcraft/loot_table/blocks/{species}_leaves.json"
        forge_data = FORGE / "resources/data" / data_relative
        source_data = SOURCE / "resources/data" / data_relative
        if not forge_data.is_file():
            fail(errors, f"missing Forge leaves loot table: {data_relative}")
        elif source_data.is_file() and forge_data.read_bytes() != source_data.read_bytes():
            fail(errors, f"leaves loot table drifted from source: {data_relative}")

    forge_leaves_tag = FORGE / "resources/data/minecraft/tags/block/leaves.json"
    source_leaves_tag = SOURCE / "resources/data/minecraft/tags/block/leaves.json"
    if not forge_leaves_tag.is_file():
        fail(errors, "missing Forge minecraft:block/leaves tag")
    elif forge_leaves_tag.read_bytes() != source_leaves_tag.read_bytes():
        fail(errors, "Forge leaves tag drifted from source")

    if "OAK_LEAVES_QUESTION" not in forge_blocks or "OAK_LEAVES_QUESTION" not in forge_items:
        fail(errors, "missing oak_leaves_question persistent item/block")
    for fragment in ("extends LeavesBlock", "BooleanProperty DORMANT", "setValue(DORMANT, false)",
                     "seasonalState", "getLightBlock", "propagatesSkylightDown", "Shapes.empty()",
                     "setValue(PERSISTENT, persistent)", "shouldFastDecay", "FAST_DECAY_DELAY"):
        if fragment not in leaves:
            fail(errors, f"Forge StardewLeavesBlock missing behavior: {fragment}")
    for fragment in ("ForgeStardewTimeService.getCurrentSeason", "refreshChunk", "refreshSeason",
                     "Block.UPDATE_CLIENTS", "TickEvent.ServerTickEvent"):
        if fragment not in lighting:
            fail(errors, f"Forge SeasonalLeafLighting missing behavior: {fragment}")
    for fragment in ("computeIfAbsent", "getCurrentSeason(ServerLevel", "setCurrentSeason(ServerLevel",
                     "CURRENT_SEASON", '"currentSeason"', "overworld()", "setDirty()"):
        if fragment not in time_service:
            fail(errors, f"Forge time adapter missing behavior: {fragment}")

    if errors:
        print("Forge tree-leaves parity failed:")
        for error in errors:
            print(f"- {error}")
        return 1
    print(f"Forge tree-leaves parity passed: {len(SPECIES)} species, dormant lighting/time adapter, persistent oak variant, resources and render layers verified (full clock integration remains a separate slice).")
    return 0


if __name__ == "__main__":
    sys.exit(main())

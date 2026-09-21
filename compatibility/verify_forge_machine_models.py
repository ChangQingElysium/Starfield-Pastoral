#!/usr/bin/env python3
"""Verify the loader-independent machine/recipe model slice.

The selected records, interfaces and deterministic machine rules are copied
byte-for-byte from the NeoForge source.  Runtime registries, resource reload
listeners and block-entity implementations intentionally remain outside this
slice until their Forge dependency closures can be migrated together.
"""

from __future__ import annotations

import hashlib
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RELATIVE_PATHS = (
    *(Path("api/v1/machine") / name for name in (
        "StardewMachineCycleContext.java",
        "StardewMachineCycleEvent.java",
        "StardewMachineCycleKind.java",
        "StardewMachineRecipeDisplay.java",
        "StardewMachineType.java",
        "StardewProductionContext.java",
        "StardewProductionEvent.java",
        "StardewProductionListener.java",
        "StardewProductionPhase.java",
        "StardewProductionPlan.java",
        "StardewProductionPlanProvider.java",
        "StardewTimedProduction.java",
    )),
    Path("api/v1/production/StardewCraftingIngredient.java"),
    *(Path("blockentity") / name for name in (
        "AdvanceableUtility.java",
        "AutomationStackHelper.java",
        "InsertResult.java",
        "MissingItemRequirement.java",
        "UtilityMachineInfo.java",
    )),
    Path("block/utility/UtilityMachineRenderState.java"),
    *(Path("workbench") / name for name in (
        "WorkbenchEntry.java",
        "WorkbenchType.java",
    )),
    Path("model/ShippingBinLidMotion.java"),
    Path("model/OilMakerAnimation.java"),
    *(Path("item/artisan") / name for name in (
        "DehydratorIngredientHelper.java",
        "PreserveType.java",
        "PreservesCropTypeHelper.java",
    )),
)

FORBIDDEN_IMPORT_MARKERS = (
    "import net.neoforged.",
    "import net.minecraftforge.",
    "import com.stardew.craft.api.v1.internal.",
    "import com.stardew.craft.production.",
    "import com.stardew.craft.StardewCraft",
    "import net.minecraft.server.packs.resources.",
    "import net.minecraft.core.registries.BuiltInRegistries",
)


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> int:
    errors: list[str] = []
    for relative in RELATIVE_PATHS:
        source = ROOT / "src/main/java/com/stardew/craft" / relative
        forge = ROOT / "src/forge-common/java/com/stardew/craft" / relative
        if not source.is_file():
            errors.append(f"missing source contract: {source.relative_to(ROOT)}")
            continue
        if not forge.is_file():
            errors.append(f"missing Forge source: {forge.relative_to(ROOT)}")
            continue
        if digest(source) != digest(forge):
            errors.append(f"Forge machine model drifted: {relative}")
        text = forge.read_text(encoding="utf-8-sig")
        for marker in FORBIDDEN_IMPORT_MARKERS:
            if marker in text:
                errors.append(f"loader/runtime dependency in {relative}: {marker}")

    if errors:
        print("Forge machine-model parity failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print(
        "Forge machine-model parity passed for "
        f"{len(RELATIVE_PATHS)} exact source classes; no loader/runtime registration imports."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

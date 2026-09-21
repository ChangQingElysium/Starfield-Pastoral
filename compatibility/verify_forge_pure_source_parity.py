#!/usr/bin/env python3
"""Verify exact source parity for Forge classes with no loader-specific code.

These classes are deliberately copied byte-for-byte from the NeoForge source
line.  Keeping the checker small and explicit prevents a later Forge-only
edit from silently changing a gameplay rule or a serialized data shape.
"""

from __future__ import annotations

import hashlib
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]

EXPLICIT_RELATIVE_PATHS = (
    *(Path("server/performance") / name for name in (
        "PerformanceCounter.java",
        "PerformanceReportFormatter.java",
        "PerformanceSnapshot.java",
        "PerformanceTiming.java",
        "RollingTimingWindow.java",
        "ServerPerformanceRecorder.java",
        "TimingSummary.java",
    )),
    Path("route/RouteGuidanceRegistry.java"),
    Path("route/RouteGuidanceRoute.java"),
    *(Path("leaderboard") / name for name in (
        "LeaderboardCategory.java",
        "LeaderboardEntry.java",
        "LeaderboardMetric.java",
        "LeaderboardPeriod.java",
        "LeaderboardSnapshot.java",
    )),
    Path("fishing/BobberStyles.java"),
    Path("fishing/FishMarketCrateLayout.java"),
    Path("fishing/FishingCastPose.java"),
    Path("fishing/FishingCastPower.java"),
    Path("fishing/FishingPresentationPhase.java"),
    Path("fishing/PlacedFishLayout.java"),
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


def relative_paths() -> tuple[Path, ...]:
    """Return explicit utility paths plus the intentionally pure API subset."""
    api_root = ROOT / "src/forge-common/java/com/stardew/craft/api/v1"
    api_paths = tuple(
        path.relative_to(ROOT / "src/forge-common/java/com/stardew/craft")
        for path in sorted(api_root.rglob("*.java"))
    )
    return EXPLICIT_RELATIVE_PATHS + api_paths


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> int:
    errors: list[str] = []
    paths = relative_paths()
    for relative in paths:
        source = ROOT / "src/main/java/com/stardew/craft" / relative
        forge = ROOT / "src/forge-common/java/com/stardew/craft" / relative
        if not source.is_file():
            errors.append(f"missing source contract: {source.relative_to(ROOT)}")
            continue
        if not forge.is_file():
            errors.append(f"missing Forge source: {forge.relative_to(ROOT)}")
            continue
        if digest(source) != digest(forge):
            errors.append(f"Forge pure source drifted: {relative}")

    if errors:
        print("Forge pure-source parity failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print(f"Forge pure-source parity passed for {len(paths)} classes.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

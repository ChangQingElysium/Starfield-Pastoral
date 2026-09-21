#!/usr/bin/env python3
"""Verify that the loader-independent overnight rules are exact copies.

The client screens and packet transport are intentionally not part of this
slice: they require a closed Forge SimpleChannel bridge and their handlers
must be migrated together.  These timing/order rules have no loader API and
must remain byte-for-byte identical while that larger closure is prepared.
"""

from __future__ import annotations

import hashlib
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RELATIVE_PATHS = (
    Path("network/overnight/OvernightCollapseTimeline.java"),
    Path("network/overnight/OvernightSequencePlanner.java"),
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
            errors.append(f"Forge overnight rules drifted: {relative}")

    if errors:
        print("Forge overnight-rule parity failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print(f"Forge overnight-rule parity passed for {len(RELATIVE_PATHS)} classes.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

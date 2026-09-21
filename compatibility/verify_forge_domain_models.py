#!/usr/bin/env python3
"""Verify exact parity for small loader-independent domain models."""

from __future__ import annotations

import hashlib
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOT = ROOT / "src/main/java/com/stardew/craft"
FORGE_ROOT = ROOT / "src/forge-common/java/com/stardew/craft"

RELATIVE_PATHS = (
    Path("economy/sell/SellContext.java"),
    Path("economy/sell/SellQuote.java"),
    Path("economy/sell/SellSource.java"),
    Path("weather/WeatherTypeNormalizer.java"),
    Path("farm/FarmCaveChoice.java"),
    Path("farm/FarmOccupancyTracker.java"),
)

IMPORT_RE = re.compile(r"^\s*import\s+(?:static\s+)?([^;]+);\s*$")
ALLOWED_IMPORT_PREFIXES = ("java.", "javax.")


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
            errors.append(f"Forge domain model drifted: {relative}")
        for imported in imports(source) + imports(forge):
            if not imported.startswith(ALLOWED_IMPORT_PREFIXES):
                errors.append(f"non-pure import in {relative}: {imported}")

    if errors:
        print("Forge domain-model parity failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print(f"Forge domain-model parity passed for {len(RELATIVE_PATHS)} exact classes.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

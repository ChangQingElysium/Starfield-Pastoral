#!/usr/bin/env python3
"""Verify the loader-independent combat-rule source slice.

The Forge port keeps these classes byte-for-byte identical to the NeoForge
source.  They form deliberately small, closed rule/data groups: damage
arithmetic and incoming-damage mapping, weapon timing/knockback rules, ring
and equipment aggregation, and the skill context value object.  Runtime
events, registration, networking, and Minecraft/loader types stay outside
this slice until their complete dependency closures can be ported.
"""

from __future__ import annotations

import hashlib
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOT = ROOT / "src/main/java/com/stardew/craft"
FORGE_ROOT = ROOT / "src/forge-common/java/com/stardew/craft"

RELATIVE_PATHS = (
    *(Path("combat") / name for name in (
        "DamageAdjustment.java",
        "DamageOutcome.java",
        "DamagePipeline.java",
        "DamageRandomSource.java",
        "DamageRequest.java",
        "IncomingDamageResolver.java",
        "StardewWeaponKnockbackRules.java",
        "StardewWeaponSpeedRules.java",
        "WeaponType.java",
    )),
    *(Path("combat/equipment") / name for name in (
        "CombatRingRules.java",
        "EquipmentStats.java",
    )),
    Path("combat/skill/SkillContext.java"),
)

# A pure combat class may use only the JDK and the other combat classes in
# this package tree.  In particular, no Minecraft, NeoForge, Forge loader,
# event, registration, or network import may enter the closure accidentally.
ALLOWED_IMPORT_PREFIXES = (
    "java.",
    "javax.",
)
IMPORT_RE = re.compile(r"^\s*import\s+(?:static\s+)?([^;]+);\s*$")


def pure_class_names() -> set[str]:
    """Return the exact combat classes that are present in this closure."""
    names: set[str] = set()
    for relative in RELATIVE_PATHS:
        if relative.suffix != ".java":
            continue
        package = ".".join(relative.parts[:-1])
        class_name = relative.stem
        names.add(f"com.stardew.craft.{package}.{class_name}")
    return names


def validate_imports(path: Path, relative: Path, pure_names: set[str]) -> list[str]:
    errors: list[str] = []
    for imported in imports(path):
        if imported.startswith(ALLOWED_IMPORT_PREFIXES):
            continue
        # A static import can end in a member name.  Accept it only when its
        # owning class is one of the exact classes above; wildcard combat
        # imports are rejected because they can hide an unported dependency.
        owner = imported
        if owner.endswith(".*"):
            errors.append(f"wildcard/non-pure import in {relative}: import {imported};")
            continue
        while owner and owner not in pure_names and "." in owner:
            owner = owner.rsplit(".", 1)[0]
        if owner in pure_names:
            continue
        errors.append(f"non-pure/loader import in {relative}: import {imported};")
    return errors


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
    pure_names = pure_class_names()
    for relative in RELATIVE_PATHS:
        source = SOURCE_ROOT / relative
        forge = FORGE_ROOT / relative
        if not source.is_file():
            errors.append(f"missing source contract: {source.relative_to(ROOT)}")
            continue
        if not forge.is_file():
            errors.append(f"missing Forge source: {forge.relative_to(ROOT)}")
            continue
        source_digest = digest(source)
        forge_digest = digest(forge)
        if source_digest != forge_digest:
            errors.append(
                f"Forge combat rule drifted: {relative} "
                f"(source={source_digest[:12]}, forge={forge_digest[:12]})"
            )
        errors.extend(validate_imports(source, relative, pure_names))
        errors.extend(validate_imports(forge, relative, pure_names))

    if errors:
        print("Forge combat-rule parity failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print(
        "Forge combat-rule parity passed for "
        f"{len(RELATIVE_PATHS)} exact source classes; no loader/runtime imports."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

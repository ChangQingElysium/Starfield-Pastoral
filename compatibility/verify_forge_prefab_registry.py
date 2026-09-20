#!/usr/bin/env python3
"""Verify the Forge prefab-tree registry slice against the NeoForge source contract.

The two Java classes in this slice are deliberately kept in the same package and
retain the complete registry/index/NBT behavior of the 1.21.1 implementation.
Only the 1.20.1 SavedData signatures are allowed to differ:

* ``SavedData.save(CompoundTag)`` replaces the HolderLookup-aware overload;
* ``DimensionDataStorage.computeIfAbsent(loader, supplier, id)`` replaces the
  1.21.1 ``SavedData.Factory`` wrapper.

This is a source-contract check, not a replacement for a live save/reload test.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIR = ROOT / "src/main/java/com/stardew/craft/tree/prefab"
FORGE_DIR = ROOT / "src/forge-common/java/com/stardew/craft/tree/prefab"

SOURCE_INSTANCE = SOURCE_DIR / "PrefabTreeInstance.java"
SOURCE_REGISTRY = SOURCE_DIR / "PrefabTreeRegistry.java"
FORGE_INSTANCE = FORGE_DIR / "PrefabTreeInstance.java"
FORGE_REGISTRY = FORGE_DIR / "PrefabTreeRegistry.java"

EXPECTED_NBT_KEYS = ("Trees", "Root", "Species", "Variant", "Felled", "BombDamage", "Members")
FORBIDDEN_FORGE_PREFAB_FILES = {
    "PrefabTreeChopHandler.java",
    "PrefabTreeManager.java",
    "PrefabTrees.java",
}


def fail(errors: list[str], message: str) -> None:
    errors.append(message)


def strip_comments(text: str) -> str:
    """Remove Java comments before comparing source contracts.

    The source and Forge copies may use different porting notes in Javadocs,
    while executable statements and string literals remain significant.
    """

    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return re.sub(r"//[^\n]*", "", text)


def compact(text: str) -> str:
    return re.sub(r"\s+", "", text)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def normalized_instance(text: str) -> str:
    return compact(strip_comments(text))


def normalized_registry_source(text: str) -> str:
    """Normalize only the documented 1.20.1 SavedData API differences."""

    text = strip_comments(text)
    text = text.replace("import net.minecraft.core.HolderLookup;", "")
    text = re.sub(r'@SuppressWarnings\("null"\)', "", text)
    text = re.sub(
        r"new\s+SavedData\.Factory<>\(\s*PrefabTreeRegistry::new\s*,\s*"
        r"PrefabTreeRegistry::load\s*,\s*null\s*\)",
        "PrefabTreeRegistry::load, PrefabTreeRegistry::new",
        text,
    )
    text = re.sub(
        r"public\s+CompoundTag\s+save\(\s*CompoundTag\s+tag\s*,\s*"
        r"HolderLookup\.Provider\s+provider\s*\)",
        "public CompoundTag save(CompoundTag tag)",
        text,
    )
    text = re.sub(
        r"public\s+static\s+PrefabTreeRegistry\s+load\(\s*CompoundTag\s+tag\s*,\s*"
        r"HolderLookup\.Provider\s+provider\s*\)",
        "public static PrefabTreeRegistry load(CompoundTag tag)",
        text,
    )
    return compact(text)


def check_instance_contract(errors: list[str]) -> None:
    source = read(SOURCE_INSTANCE)
    forge = read(FORGE_INSTANCE)

    if normalized_instance(source) != normalized_instance(forge):
        fail(errors, "PrefabTreeInstance Forge implementation drifted from the source contract")

    for text, label in ((source, "source"), (forge, "Forge")):
        if "package com.stardew.craft.tree.prefab;" not in text:
            fail(errors, f"{label} PrefabTreeInstance package drifted")
        if "public final class PrefabTreeInstance" not in text:
            fail(errors, f"{label} PrefabTreeInstance must remain final")
        for fragment in (
            "this.root = root.immutable();",
            "this.members = members;",
            "bombDamage = Math.max(0, damage);",
            "bombDamage >= (felled ? 5 : 10)",
            "this.members.clear();",
            "this.members.add(root);",
            "copy.add(pos.immutable());",
            "copy.add(root.immutable());",
        ):
            if fragment not in text:
                fail(errors, f"{label} PrefabTreeInstance missing behavior: {fragment}")


def check_registry_contract(errors: list[str]) -> None:
    source = read(SOURCE_REGISTRY)
    forge = read(FORGE_REGISTRY)

    if normalized_registry_source(source) != normalized_registry_source(forge):
        fail(errors, "PrefabTreeRegistry Forge implementation drifted outside the SavedData API adaptation")

    for text, label in ((source, "source"), (forge, "Forge")):
        if "package com.stardew.craft.tree.prefab;" not in text:
            fail(errors, f"{label} PrefabTreeRegistry package drifted")
        if "public final class PrefabTreeRegistry extends SavedData" not in text:
            fail(errors, f"{label} PrefabTreeRegistry must extend SavedData")
        if 'private static final String DATA_NAME = "stardew_prefab_trees";' not in text:
            fail(errors, f"{label} registry data name drifted")
        for key in EXPECTED_NBT_KEYS:
            if f'"{key}"' not in text:
                fail(errors, f"{label} registry is missing NBT key {key}")
        for fragment in (
            "private final Map<BlockPos, PrefabTreeInstance> byRoot",
            "private final Map<BlockPos, PrefabTreeInstance> byMember",
            "byRoot.put(instance.root(), instance);",
            "byMember.put(pos, instance);",
            "PrefabTreeInstance.ofMembers(root, species, variant, members)",
            "instance.markFelled();",
            "instance.setBombDamage(t.getInt(\"BombDamage\"));",
            "data.index(instance);",
        ):
            if fragment not in text:
                fail(errors, f"{label} PrefabTreeRegistry missing behavior: {fragment}")

    # The source must document the API being adapted, while Forge must expose
    # only the 1.20.1 signatures and call order.
    if "HolderLookup.Provider" not in source or "SavedData.Factory" not in source:
        fail(errors, "source registry no longer contains the expected 1.21.1 SavedData contract")
    if "HolderLookup" in forge:
        fail(errors, "Forge registry must not retain HolderLookup API")
    if "SavedData.Factory" in forge:
        fail(errors, "Forge registry must not retain SavedData.Factory")
    if re.search(
        r"computeIfAbsent\(\s*PrefabTreeRegistry::load\s*,\s*PrefabTreeRegistry::new\s*,\s*DATA_NAME\s*\)",
        forge,
        re.S,
    ) is None:
        fail(errors, "Forge registry must use computeIfAbsent(loader, supplier, id)")
    if re.search(r"CompoundTag\s+save\(\s*CompoundTag\s+tag\s*\)", forge) is None:
        fail(errors, "Forge registry must implement save(CompoundTag)")
    if re.search(
        r"static\s+PrefabTreeRegistry\s+load\(\s*CompoundTag\s+tag\s*\)", forge
    ) is None:
        fail(errors, "Forge registry must implement load(CompoundTag)")
    if re.search(r"save\([^)]*,[^)]*HolderLookup|load\([^)]*,[^)]*HolderLookup", forge):
        fail(errors, "Forge registry retains a HolderLookup save/load parameter")


def check_prefab_boundary(errors: list[str]) -> None:
    if not FORGE_INSTANCE.is_file() or not FORGE_REGISTRY.is_file():
        return
    forge_java = {path.name for path in FORGE_DIR.glob("*.java")}
    for forbidden in sorted(FORBIDDEN_FORGE_PREFAB_FILES & forge_java):
        fail(errors, f"Forge prefab registry slice must not migrate {forbidden}")


def main() -> int:
    errors: list[str] = []
    try:
        for path in (SOURCE_INSTANCE, SOURCE_REGISTRY, FORGE_INSTANCE, FORGE_REGISTRY):
            if not path.is_file():
                fail(errors, f"missing prefab registry source file: {path.relative_to(ROOT)}")
        if not errors:
            check_instance_contract(errors)
            check_registry_contract(errors)
        check_prefab_boundary(errors)
    except (OSError, re.error) as exc:
        print(f"Forge prefab-registry parity parser failed: {exc}", file=sys.stderr)
        return 1

    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        print(f"Forge prefab-registry parity failed with {len(errors)} error(s).", file=sys.stderr)
        return 1

    print(
        "All 2 Forge prefab registry classes match the 1.21.1 source contract; "
        "SavedData API adaptation and prefab-manager boundary verified."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

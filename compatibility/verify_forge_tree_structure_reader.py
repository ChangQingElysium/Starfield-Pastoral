#!/usr/bin/env python3
"""Verify the Forge prefab-tree structure-reader slice.

The Forge port intentionally contains only the vanilla NBT block-reader part
of ``StructureLoader``.  This checker compares that method's source contract.
The only accepted implementation differences are the hosting class/package,
the Forge-common logger binding, and the old 1.20.1 compressed-NBT overload;
it rejects accidental copying of the rest of ``StructureLoader`` or the
not-yet-ported manager.
"""

from __future__ import annotations

import hashlib
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "src/main/java/com/stardew/craft/mining/StructureLoader.java"
FORGE = ROOT / (
    "src/forge-common/java/com/stardew/craft/tree/prefab/"
    "ForgeTreeStructureReader.java"
)
FORGE_COMMON_JAVA = ROOT / "src/forge-common/java"
SOURCE_RESOURCES = ROOT / "src/main/resources/data/stardewcraft/structures/tree"
FORGE_RESOURCES = ROOT / "src/forge-bootstrap/resources/data/stardewcraft/structures/tree"
BUILD = ROOT / "build.gradle"

FORBIDDEN_READER_REFERENCES = (
    "StructureLoader",
    "PrefabTreeManager",
    "PrefabTrees",
    "PrefabTreeChopHandler",
    "loadAndPlace",
    "loadAndPlaceSchematic",
    "loadAndPlaceCW90",
    "parseBlockState",
    "decodeVarIntArray",
)

FORBIDDEN_PLACEMENT_TOKENS = (
    "ServerLevel",
    "BlockPos",
    "StructureTemplate",
    "StructurePlaceSettings",
    "LevelChunk",
    "placeInWorld",
    "setBlock",
    "ensureChunksLoaded",
    "applySchematicBlockEntities",
)


def fail(errors: list[str], message: str) -> None:
    errors.append(message)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def strip_comments(text: str) -> str:
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return re.sub(r"//[^\n]*", "", text)


def compact(text: str) -> str:
    return re.sub(r"\s+", "", text)


def method_body(text: str, method_name: str) -> str:
    """Return one Java method body using a brace-balanced scan."""

    match = re.search(
        rf"\b{re.escape(method_name)}\s*\([^)]*\)\s*\{{", text, re.S
    )
    if match is None:
        raise ValueError(f"missing method {method_name}")
    open_index = text.find("{", match.start(), match.end())
    depth = 0
    for index in range(open_index, len(text)):
        char = text[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return text[open_index + 1 : index]
    raise ValueError(f"unterminated method {method_name}")


def normalize_reader_body(text: str, forge: bool) -> str:
    """Normalize only the declared class/API differences before comparison."""

    text = strip_comments(text)
    if forge:
        text = text.replace(
            "ForgeTreeStructureReader.class.getClassLoader()",
            "StructureLoader.class.getClassLoader()",
        )
        text = text.replace(
            "NbtIo.readCompressed(stream)",
            "NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap())",
        )
        text = re.sub(r"(?<![\w.])LOGGER\b", "StardewCraft.LOGGER", text)
    return compact(text)


def check_method_contract(errors: list[str]) -> None:
    source_text = read(SOURCE)
    forge_text = read(FORGE)

    source_body = normalize_reader_body(
        method_body(source_text, "readStructureNbtBlocks"), forge=False
    )
    forge_body = normalize_reader_body(
        method_body(forge_text, "readStructureNbtBlocks"), forge=True
    )
    if source_body != forge_body:
        fail(errors, "Forge reader method drifted from the source readStructureNbtBlocks contract")

    required_fragments = (
        "if (structurePath == null)",
        "getResourceAsStream(structurePath)",
        "if (stream == null)",
        "NbtIo.readCompressed(stream",
        'root.getList("size", Tag.TAG_INT)',
        'sizeTag.getInt(0)',
        'sizeTag.getInt(1)',
        'sizeTag.getInt(2)',
        'root.getList("palette", Tag.TAG_COMPOUND)',
        'root.contains("palettes", Tag.TAG_LIST)',
        'palettes.getList(0)',
        'Structure {} has empty palette',
        'BuiltInRegistries.BLOCK.asLookup()',
        'NbtUtils.readBlockState(lookup, paletteTag.getCompound(i))',
        'root.getList("blocks", Tag.TAG_COMPOUND)',
        'new java.util.ArrayList<>(blocks.size())',
        'paletteIndex < 0 || paletteIndex >= paletteStates.length',
        'state == null || state.isAir()',
        'pos.size() < 3',
        'maxX = Math.max(maxX, x)',
        'maxY = Math.max(maxY, y)',
        'maxZ = Math.max(maxZ, z)',
        'states.add(new PositionedState(x, y, z, state))',
        'Structure {} has no non-air blocks',
        'if (width <= 0) width = maxX + 1',
        'if (height <= 0) height = maxY + 1',
        'if (length <= 0) length = maxZ + 1',
        'return new StructureBlocks(width, height, length, states)',
        'Failed to read structure blocks {}: {}',
        'return null',
    )
    for text, label in ((source_text, "source"), (forge_text, "Forge")):
        body = method_body(text, "readStructureNbtBlocks")
        body_compact = compact(body)
        for fragment in required_fragments:
            # The source uses a fully qualified ArrayList/List in the original
            # method; both forms are accepted here because the contract is about
            # behavior, not import spelling.
            fragment_compact = compact(fragment)
            short_fragment = fragment_compact.replace(
                "newjava.util.ArrayList", "newArrayList"
            )
            if fragment_compact not in body_compact and short_fragment not in body_compact:
                fail(errors, f"{label} reader is missing behavior: {fragment}")


def check_type_contract(errors: list[str]) -> None:
    source_text = read(SOURCE)
    forge_text = read(FORGE)

    source_records = (
        "public record PositionedState(int dx, int dy, int dz, BlockState state)",
        "public record StructureBlocks(int width, int height, int length, java.util.List<PositionedState> states)",
    )
    forge_records = (
        "public record PositionedState(int dx, int dy, int dz, BlockState state)",
        "public record StructureBlocks(int width, int height, int length, List<PositionedState> states)",
    )
    for fragment in source_records:
        if fragment not in source_text:
            fail(errors, f"source record contract missing: {fragment}")
    for fragment in forge_records:
        if fragment not in forge_text:
            fail(errors, f"Forge record contract missing: {fragment}")

    if "package com.stardew.craft.tree.prefab;" not in forge_text:
        fail(errors, "Forge reader package drifted")
    if "public final class ForgeTreeStructureReader" not in forge_text:
        fail(errors, "Forge reader must remain a final standalone class")
    if re.search(
        r"public\s+static\s+StructureBlocks\s+readStructureNbtBlocks\s*\(\s*String\s+structurePath\s*\)",
        forge_text,
    ) is None:
        fail(errors, "Forge reader must expose readStructureNbtBlocks(String)")

    # The only allowed method-level API adaptations are the old compressed-NBT
    # overload and the source class name used for class-loader resource lookup.
    if "NbtAccounter" in forge_text:
        fail(errors, "Forge 1.20.1 reader must use the old NbtIo.readCompressed(InputStream) signature")
    if "NbtIo.readCompressed(stream);" not in forge_text:
        fail(errors, "Forge reader must call NbtIo.readCompressed(stream)")
    if "ForgeTreeStructureReader.class.getClassLoader()" not in forge_text:
        fail(errors, "Forge reader must resolve resources from its own class loader")
    if "NbtUtils.readBlockState(lookup, paletteTag.getCompound(i))" not in forge_text:
        fail(errors, "Forge reader must use the 1.20.1 NbtUtils/registry lookup path")
    if "import com.mojang.logging.LogUtils;" not in forge_text:
        fail(errors, "Forge reader must use a Forge-common logger")
    if "private static final Logger LOGGER = LogUtils.getLogger();" not in forge_text:
        fail(errors, "Forge reader logger declaration drifted")
    if "import org.slf4j.Logger;" not in forge_text:
        fail(errors, "Forge reader must declare its logger with the Forge runtime API")


def check_boundary(errors: list[str]) -> None:
    forge_text = read(FORGE)
    executable_text = strip_comments(forge_text)
    for forbidden in FORBIDDEN_READER_REFERENCES:
        if forbidden in executable_text:
            fail(errors, f"Forge tree reader crossed the standalone-slice boundary: {forbidden}")
    for forbidden in FORBIDDEN_PLACEMENT_TOKENS:
        if re.search(rf"\b{re.escape(forbidden)}\b", executable_text):
            fail(errors, f"Forge tree reader must not contain placement API: {forbidden}")

    public_methods = re.findall(
        r"\bpublic\s+(?!record\b)(?:static\s+)?(?:[\w<>,.?]+\s+)+([A-Za-z_$][\w$]*)\s*\(",
        executable_text,
    )
    if public_methods != ["readStructureNbtBlocks"]:
        fail(errors, f"Forge tree reader public method boundary drifted: {public_methods}")

    for forbidden_name in (
        "StructureLoader.java",
        "PrefabTreeManager.java",
        "PrefabTrees.java",
        "PrefabTreeChopHandler.java",
    ):
        if any(path.is_file() for path in FORGE_COMMON_JAVA.rglob(forbidden_name)):
            fail(errors, f"Forge tree reader slice must not migrate {forbidden_name}")


def check_resources(errors: list[str]) -> None:
    if not SOURCE_RESOURCES.is_dir():
        fail(errors, f"missing source tree resource directory: {SOURCE_RESOURCES}")
    if not FORGE_RESOURCES.is_dir():
        fail(errors, f"missing Forge tree resource directory: {FORGE_RESOURCES}")
        return

    source_files = {
        path.relative_to(SOURCE_RESOURCES)
        for path in SOURCE_RESOURCES.rglob("*")
        if path.is_file()
    }
    forge_files = {
        path.relative_to(FORGE_RESOURCES)
        for path in FORGE_RESOURCES.rglob("*")
        if path.is_file()
    }
    if source_files != forge_files:
        fail(
            errors,
            "Forge tree resources must have the same file set as the source: "
            f"source-only={sorted(source_files - forge_files)}, "
            f"forge-only={sorted(forge_files - source_files)}",
        )
        return
    expected_files = {
        Path(f"{species}_{variant}.nbt")
        for species in range(1, 6)
        for variant in range(1, 6)
    }
    if source_files != expected_files:
        fail(errors, "source tree resource closure must contain exactly the 25 1..5 variant files")
    if len(source_files) != 25:
        fail(errors, f"expected 25 tree resources, found {len(source_files)}")
    for relative in sorted(source_files):
        source_path = SOURCE_RESOURCES / relative
        forge_path = FORGE_RESOURCES / relative
        if hashlib.sha256(source_path.read_bytes()).digest() != hashlib.sha256(
            forge_path.read_bytes()
        ).digest():
            fail(errors, f"Forge tree resource bytes drifted: {relative}")


def check_gradle_wiring(errors: list[str]) -> None:
    text = read(BUILD)
    required = (
        "def checkForgeTreeStructureReaderParity = tasks.register('checkForgeTreeStructureReaderParity', Exec)",
        "compatibility/verify_forge_tree_structure_reader.py",
        "dependsOn checkForgeTreeStructureReaderParity",
    )
    for fragment in required:
        if fragment not in text:
            fail(errors, f"build.gradle is missing tree-reader check wiring: {fragment}")


def main() -> int:
    errors: list[str] = []
    for path in (SOURCE, FORGE, BUILD):
        if not path.is_file():
            fail(errors, f"missing required tree-reader file: {path.relative_to(ROOT)}")
    if not errors:
        try:
            check_method_contract(errors)
            check_type_contract(errors)
            check_boundary(errors)
            check_resources(errors)
            check_gradle_wiring(errors)
        except (OSError, ValueError, re.error) as exc:
            print(f"Forge tree structure-reader parity parser failed: {exc}", file=sys.stderr)
            return 1

    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        print(
            f"Forge tree structure-reader parity failed with {len(errors)} error(s).",
            file=sys.stderr,
        )
        return 1

    print(
        "Forge tree structure reader matches the source NBT contract; "
        "25 resources, Forge API fallback, and standalone migration boundary verified."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

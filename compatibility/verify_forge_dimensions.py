#!/usr/bin/env python3
"""Verify the Forge dimension-key slice against the 1.21.1 source contract."""

from __future__ import annotations

import re
import sys
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "src/main"
FORGE = ROOT / "src/forge-common"
SOURCE_DIMENSIONS = SOURCE / "java/com/stardew/craft/core/ModDimensions.java"
FORGE_DIMENSIONS = FORGE / "java/com/stardew/craft/core/ModDimensions.java"
SOURCE_MOD = SOURCE / "java/com/stardew/craft/StardewCraft.java"
FORGE_BOOTSTRAP = ROOT / "src/forge-bootstrap/java/com/stardew/craft/forge/ForgeBootstrap.java"

EXPECTED_KEYS = {
    "STARDEW_VALLEY": ("Level", "DIMENSION", "stardewcraft", "stardew_valley"),
    "STARDEW_VALLEY_TYPE": ("DimensionType", "DIMENSION_TYPE", "stardewcraft", "stardew_valley"),
}

KEY_PATTERN = re.compile(
    r"public\s+static\s+final\s+ResourceKey<(?P<type>[^>]+)>\s+"
    r"(?P<field>[A-Z][A-Z0-9_]*)\s*=\s*ResourceKey\.create\(\s*"
    r"Registries\.(?P<registry>[A-Z][A-Z0-9_]*)\s*,\s*"
    r"(?P<factory>ResourceLocation\.fromNamespaceAndPath|new\s+ResourceLocation)\s*"
    r"\(\s*(?P<namespace>[A-Za-z_$][A-Za-z0-9_$.]*)\s*,\s*"
    r'"(?P<path>[a-z0-9_./-]+)"\s*\)\s*\)\s*;',
    re.S,
)


@dataclass(frozen=True)
class KeyContract:
    type_name: str
    registry: str
    namespace_ref: str
    path: str
    factory: str


def parse_constant(path: Path, name: str) -> str:
    text = path.read_text(encoding="utf-8")
    match = re.search(
        rf"\b(?:public\s+)?static\s+final\s+String\s+{re.escape(name)}\s*=\s*\"([^\"]+)\"\s*;",
        text,
    )
    if match is None:
        raise ValueError(f"missing {name} string constant in {path}")
    return match.group(1)


def parse_keys(path: Path) -> dict[str, KeyContract]:
    text = path.read_text(encoding="utf-8")
    matches = list(KEY_PATTERN.finditer(text))
    if len(matches) != len(EXPECTED_KEYS):
        raise ValueError(
            f"expected exactly {len(EXPECTED_KEYS)} ResourceKey definitions in {path}, got {len(matches)}"
        )
    contracts: dict[str, KeyContract] = {}
    for match in matches:
        field = match.group("field")
        if field in contracts:
            raise ValueError(f"duplicate ResourceKey field {field} in {path}")
        contracts[field] = KeyContract(
            type_name=match.group("type").strip(),
            registry=match.group("registry"),
            namespace_ref=match.group("namespace"),
            path=match.group("path"),
            factory=re.sub(r"\s+", " ", match.group("factory")).strip(),
        )
    return contracts


def register_body(text: str) -> str:
    match = re.search(r"public\s+static\s+void\s+register\(\)\s*\{(?P<body>.*?)\}", text, re.S)
    if match is None:
        raise ValueError("missing public static void register()")
    return match.group("body")


def check_register_contract(source_text: str, forge_text: str, errors: list[str]) -> None:
    try:
        source_body = register_body(source_text)
        forge_body = register_body(forge_text)
    except ValueError as exc:
        errors.append(str(exc))
        return

    source_message = re.findall(r"\.info\(\s*\"([^\"]+)\"\s*\)", source_body)
    forge_message = re.findall(r"\.info\(\s*\"([^\"]+)\"\s*\)", forge_body)
    if source_message != ["Registering Stardew Valley dimension"]:
        errors.append(f"source register() log contract drifted: {source_message}")
    if forge_message != source_message:
        errors.append(f"Forge register() log contract drifted: expected {source_message}, got {forge_message}")
    if len(re.findall(r";", forge_body)) != 1:
        errors.append("Forge register() must retain only the source logging responsibility")


def check_api_contract(forge_text: str, errors: list[str]) -> None:
    if "import com.stardew.craft.forge.ForgeBootstrap;" not in forge_text:
        errors.append("Forge ModDimensions must import ForgeBootstrap")
    if "ForgeBootstrap.MOD_ID" not in forge_text:
        errors.append("Forge dimension keys must use ForgeBootstrap.MOD_ID")
    if "new ResourceLocation(" not in forge_text:
        errors.append("Forge dimension keys must use the Forge 1.20.1 ResourceLocation constructor")
    if "ResourceLocation.fromNamespaceAndPath" in forge_text:
        errors.append("Forge ModDimensions must not use the 1.21.1 fromNamespaceAndPath API")
    if "com.stardew.craft.StardewCraft" in forge_text or "StardewCraft.MODID" in forge_text:
        errors.append("Forge ModDimensions must not depend on the NeoForge StardewCraft entrypoint")

    # This slice intentionally defines stable keys only. Dynamic dimension or
    # worldgen registration belongs to a separately verified migration slice.
    forbidden = (
        "DeferredRegister",
        "LevelStem",
        "NoiseGeneratorSettings",
        "ChunkGenerator",
        "DimensionManager",
        "Registry.register",
        "WorldGenSettings",
    )
    for token in forbidden:
        if token in forge_text:
            errors.append(f"Forge ModDimensions contains out-of-scope dynamic/worldgen registration: {token}")


def main() -> int:
    errors: list[str] = []
    try:
        source_text = SOURCE_DIMENSIONS.read_text(encoding="utf-8")
        forge_text = FORGE_DIMENSIONS.read_text(encoding="utf-8")
        source_keys = parse_keys(SOURCE_DIMENSIONS)
        forge_keys = parse_keys(FORGE_DIMENSIONS)
        source_mod_id = parse_constant(SOURCE_MOD, "MODID")
        forge_mod_id = parse_constant(FORGE_BOOTSTRAP, "MOD_ID")
    except (OSError, ValueError) as exc:
        print(f"Forge dimension parity parser failed: {exc}", file=sys.stderr)
        return 1

    if source_keys.keys() != set(EXPECTED_KEYS):
        errors.append(f"source dimension key fields drifted: {sorted(source_keys)}")
    if forge_keys.keys() != set(EXPECTED_KEYS):
        errors.append(f"Forge dimension key fields drifted: {sorted(forge_keys)}")

    for field, expected in EXPECTED_KEYS.items():
        source = source_keys.get(field)
        forge = forge_keys.get(field)
        if source is None or forge is None:
            continue
        source_contract = (source.type_name, source.registry, source_mod_id, source.path)
        forge_contract = (forge.type_name, forge.registry, forge_mod_id, forge.path)
        if source_contract != expected:
            errors.append(f"source key contract drifted for {field}: expected {expected}, got {source_contract}")
        if forge_contract != expected:
            errors.append(f"Forge key contract drifted for {field}: expected {expected}, got {forge_contract}")
        if source_contract != forge_contract:
            errors.append(f"source/Forge key strings differ for {field}: {source_contract} vs {forge_contract}")
        if source.namespace_ref != "StardewCraft.MODID":
            errors.append(f"source namespace reference drifted for {field}: {source.namespace_ref}")
        if forge.namespace_ref != "ForgeBootstrap.MOD_ID":
            errors.append(f"Forge namespace reference drifted for {field}: {forge.namespace_ref}")
        if source.factory != "ResourceLocation.fromNamespaceAndPath":
            errors.append(f"source ResourceLocation API drifted for {field}: {source.factory}")
        if forge.factory != "new ResourceLocation":
            errors.append(f"Forge ResourceLocation API drifted for {field}: {forge.factory}")

    check_api_contract(forge_text, errors)
    check_register_contract(source_text, forge_text, errors)

    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        print(f"Forge dimension parity failed with {len(errors)} error(s).", file=sys.stderr)
        return 1

    print("Forge dimension keys match the 1.21.1 source contract; no dynamic/worldgen registration added.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

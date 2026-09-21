#!/usr/bin/env python3
"""Verify exact source parity for Forge classes with no loader-specific code.

These classes are deliberately copied byte-for-byte from the NeoForge source
line.  Keeping the checker small and explicit prevents a later Forge-only
edit from silently changing a gameplay rule or a serialized data shape.
"""

from __future__ import annotations

import hashlib
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOT = ROOT / "src/main/java/com/stardew/craft"
FORGE_ROOT = ROOT / "src/forge-common/java/com/stardew/craft"
SOURCE_API_ROOT = SOURCE_ROOT / "api/v1"
FORGE_API_ROOT = FORGE_ROOT / "api/v1"

# The Forge branch already contains a few API classes whose contracts still use
# Minecraft types and are being ported in separate slices.  The closure below
# is intentionally narrower: only source-identical classes that can compile
# without a loader, Minecraft, codec, registry, or resource-loader dependency.
_ALLOWED_IMPORT_PREFIXES = ("java.", "javax.", "com.stardew.craft.api.v1.")
_FORBIDDEN_TEXT = (
    "net.minecraft.",
    "net.neoforged.",
    "net.minecraftforge.",
    "com.mojang.",
    "com.google.gson.",
)
_RUNTIME_PATTERNS = (
    ("runtime registration", re.compile(
        r"\b(?:register|registerAll|registerDefaults|registerType|"
        r"registerProvider|registerFactory)\s*\("
    )),
    ("resource loading", re.compile(
        r"\b(?:getResource(?:AsStream)?|openResource|loadResource|"
        r"readResource|ResourceLoader|ClassLoader|ServiceLoader)\b"
    )),
    ("codec/json runtime", re.compile(
        r"\b(?:Codec|JsonOps|JsonObject|JsonElement|RecordCodecBuilder|"
        r"DataResult|Dynamic)\b"
    )),
    ("registry runtime", re.compile(r"\b(?:RegistryObject|BuiltInRegistries)\b")),
)


@dataclass
class _ApiType:
    qualified_name: str
    package: str
    path: Path
    text: str
    imports: tuple[str, ...]
    dependencies: set[str] = field(default_factory=set)
    reasons: list[str] = field(default_factory=list)


def _strip_comments_and_literals(text: str) -> str:
    """Keep identifiers/newlines while hiding comments and string literals."""
    result: list[str] = []
    index = 0
    length = len(text)
    state = "code"
    while index < length:
        char = text[index]
        next_char = text[index + 1] if index + 1 < length else ""
        if state == "code":
            if char == "/" and next_char == "/":
                result.extend((" ", " "))
                index += 2
                state = "line-comment"
                continue
            if char == "/" and next_char == "*":
                result.extend((" ", " "))
                index += 2
                state = "block-comment"
                continue
            if char == '"':
                result.append(" ")
                index += 1
                state = "string"
                continue
            if char == "'":
                result.append(" ")
                index += 1
                state = "char"
                continue
            result.append(char)
            index += 1
            continue
        if state == "line-comment":
            if char == "\n":
                result.append("\n")
                state = "code"
            else:
                result.append(" ")
            index += 1
            continue
        if state == "block-comment":
            if char == "*" and next_char == "/":
                result.extend((" ", " "))
                index += 2
                state = "code"
            else:
                result.append("\n" if char == "\n" else " ")
                index += 1
            continue
        if state in ("string", "char"):
            if char == "\\":
                result.append(" ")
                if next_char:
                    result.append("\n" if next_char == "\n" else " ")
                    index += 2
                else:
                    index += 1
                continue
            if (state == "string" and char == '"') or (state == "char" and char == "'"):
                result.append(" ")
                index += 1
                state = "code"
            else:
                result.append("\n" if char == "\n" else " ")
                index += 1
            continue
    return "".join(result)


def _source_api_types() -> dict[str, _ApiType]:
    """Index one top-level API type per Java source file."""
    types: dict[str, _ApiType] = {}
    for path in sorted(SOURCE_API_ROOT.rglob("*.java")):
        text = path.read_text(encoding="utf-8")
        package_match = re.search(r"^package\s+([\w.]+);", text, re.MULTILINE)
        declaration_match = re.search(
            r"\b(?:public\s+)?(?:sealed\s+|non-sealed\s+|final\s+|abstract\s+)*"
            r"(?:class|interface|enum|record)\s+(\w+)",
            text,
        )
        if not package_match or not declaration_match:
            continue
        package = package_match.group(1)
        name = declaration_match.group(1)
        qualified_name = f"{package}.{name}"
        imports = tuple(
            line.strip()[7:].rstrip(";").replace("static ", "").strip()
            for line in text.splitlines()
            if line.strip().startswith("import ")
        )
        types[qualified_name] = _ApiType(
            qualified_name=qualified_name,
            package=package,
            path=path,
            text=text,
            imports=imports,
        )

    by_package: dict[str, list[str]] = {}
    for qualified_name, api_type in types.items():
        by_package.setdefault(api_type.package, []).append(qualified_name)

    for qualified_name, api_type in types.items():
        sanitized = _strip_comments_and_literals(api_type.text)
        for imported in api_type.imports:
            if imported.startswith("com.stardew.craft.api.v1."):
                if ".internal." in imported:
                    api_type.reasons.append(f"internal API import: {imported}")
                if imported.endswith(".*"):
                    api_type.dependencies.update(
                        candidate
                        for candidate in types
                        if candidate.startswith(imported[:-1])
                    )
                elif imported in types:
                    api_type.dependencies.add(imported)
                continue
            if imported.startswith(("java.", "javax.")):
                continue
            api_type.reasons.append(f"external import: {imported}")

        # A class can reference another type in its package without an import.
        # Restrict this to top-level declarations and strip comments/literals so
        # prose and examples cannot create false closure edges.
        for candidate in by_package.get(api_type.package, ()):
            if candidate == qualified_name:
                continue
            simple_name = candidate.rsplit(".", 1)[1]
            if re.search(rf"\b{re.escape(simple_name)}\b", sanitized):
                api_type.dependencies.add(candidate)

        if any(token in sanitized for token in _FORBIDDEN_TEXT):
            api_type.reasons.append("Minecraft/loader or external codec reference")
        if ".internal." in qualified_name:
            api_type.reasons.append("internal runtime package")
        for reason, pattern in _RUNTIME_PATTERNS:
            if pattern.search(sanitized):
                api_type.reasons.append(reason)

    return types


def _pure_api_closure() -> tuple[tuple[Path, ...], dict[str, list[str]], dict[str, _ApiType]]:
    """Return the fixed-point closure of genuinely loader-independent API types."""
    types = _source_api_types()
    pure = {qualified_name for qualified_name, api_type in types.items() if not api_type.reasons}
    excluded: dict[str, list[str]] = {
        qualified_name: list(api_type.reasons)
        for qualified_name, api_type in types.items()
        if api_type.reasons
    }
    while True:
        newly_excluded = {
            qualified_name
            for qualified_name in pure
            if any(dependency in types and dependency not in pure
                   for dependency in types[qualified_name].dependencies)
        }
        if not newly_excluded:
            break
        for qualified_name in sorted(newly_excluded):
            pure.remove(qualified_name)
            dependencies = sorted(
                dependency
                for dependency in types[qualified_name].dependencies
                if dependency in types and dependency not in pure
            )
            excluded[qualified_name] = [
                f"depends on excluded API type: {dependency}"
                for dependency in dependencies
            ]

    paths = tuple(
        sorted(
            api_type.path.relative_to(SOURCE_ROOT)
            for qualified_name, api_type in types.items()
            if qualified_name in pure
        )
    )
    return paths, excluded, types

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
    Path("animal/model/AnimalAcquisitionSource.java"),
    Path("animal/model/AnimalNameRules.java"),
    Path("animal/rule/AnimalCatchUpRules.java"),
    Path("animal/rule/AnimalNightEventRules.java"),
    Path("animal/rule/DuckSwimmingRules.java"),
)


def relative_paths() -> tuple[Path, ...]:
    """Return explicit utility paths plus every copied API source."""
    api_paths = tuple(
        path.relative_to(FORGE_ROOT)
        for path in sorted(FORGE_API_ROOT.rglob("*.java"))
    )
    # Preserve the historical check for already-porting API slices as well as
    # the new closure.  dict.fromkeys keeps output deterministic if a path is
    # ever listed in both groups.
    return tuple(dict.fromkeys((*EXPLICIT_RELATIVE_PATHS, *api_paths)))


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> int:
    errors: list[str] = []
    api_closure, excluded_api, api_types = _pure_api_closure()

    # Every type in the computed closure must exist in Forge and remain byte
    # identical.  This makes adding a new pure API class fail loudly until its
    # complete source is copied, while leaving intentionally Minecraft-aware
    # legacy API slices on their own migration tracks.
    for relative in api_closure:
        source = SOURCE_ROOT / relative
        forge = FORGE_ROOT / relative
        if not forge.is_file():
            errors.append(f"missing Forge API closure source: {relative}")
            continue
        if digest(source) != digest(forge):
            errors.append(f"Forge API closure drifted: {relative}")
        forge_text = forge.read_text(encoding="utf-8")
        forbidden = [
            token for token in _FORBIDDEN_TEXT
            if token in forge_text
        ]
        if forbidden:
            errors.append(
                f"Forge API closure has forbidden imports/references ({', '.join(forbidden)}): {relative}"
            )

    paths = relative_paths()
    for relative in paths:
        source = SOURCE_ROOT / relative
        forge = FORGE_ROOT / relative
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

    copied = sum((FORGE_ROOT / relative).is_file() for relative in api_closure)
    direct_exclusions = sum(
        bool(api_types[name].reasons)
        for name in api_types
    )
    closure_exclusions = len(excluded_api) - direct_exclusions
    print(
        f"Forge pure-source parity passed for {len(paths)} classes; "
        f"API closure {len(api_closure)} classes ({copied} present, "
        f"{len(api_closure) - copied} missing), "
        f"excluded {len(excluded_api)} ({direct_exclusions} direct, "
        f"{closure_exclusions} dependency)."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

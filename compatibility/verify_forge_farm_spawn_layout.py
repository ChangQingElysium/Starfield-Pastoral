#!/usr/bin/env python3
"""Verify the loader-independent farm spawn-mask rule slice.

The authored forest/beach masks remain in the 1.21.1 source tree until the
Forge resource-loader/runtime closure is migrated.  This checker intentionally
does not copy or reinterpret that Minecraft/Gson loader.  It verifies the
pure run contract extracted from it, validates the authoritative JSON data,
and executes the Forge helper against representative vectors.
"""

from __future__ import annotations

import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOT = ROOT / "src/main/java/com/stardew/craft"
FORGE_ROOT = ROOT / "src/forge-common/java/com/stardew/craft"
SOURCE_MASK_ROOT = ROOT / "src/main/resources/data/stardewcraft/farm_spawn_layouts"

HELPER = Path("farm/FarmSpawnLayoutRules.java")
EXPECTED_SIZES = {
    "forest.json": (288, 81, 272),
    "beach.json": (288, 78, 272),
}


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def java_tool(name: str) -> str | None:
    """Resolve a JDK tool even when macOS exposes only a failing /usr/bin shim."""
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        candidate = Path(java_home) / "bin" / name
        if candidate.is_file() and candidate.stat().st_mode & 0o111:
            return str(candidate)
    # The desktop runtime keeps user JDKs under ~/.jdks; avoid baking that path
    # into the checker while still bypassing Apple's /usr/bin/java shim.
    user_jdks = Path.home() / ".jdks"
    if user_jdks.is_dir():
        for candidate in sorted(user_jdks.glob("*/Contents/Home/bin/" + name)):
            if candidate.is_file() and candidate.stat().st_mode & 0o111:
                return str(candidate)
    candidate = shutil.which(name)
    return candidate


def fail(errors: list[str]) -> int:
    if errors:
        print("Forge farm spawn-layout parity failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1
    print(
        "Forge farm spawn-layout parity passed: pure run rules and "
        "authoritative forest/beach masks verified."
    )
    return 0


def check_source_contract(errors: list[str]) -> None:
    source = SOURCE_ROOT / HELPER
    forge = FORGE_ROOT / HELPER
    if not source.is_file():
        errors.append(f"missing source helper: {source.relative_to(ROOT)}")
        return
    if not forge.is_file():
        errors.append(f"missing Forge helper: {forge.relative_to(ROOT)}")
        return
    if digest(source) != digest(forge):
        errors.append(f"Forge helper drifted: {HELPER}")

    for path in (source, forge):
        text = path.read_text(encoding="utf-8")
        imports = [
            line.strip()[7:].rstrip(";").strip()
            for line in text.splitlines()
            if line.strip().startswith("import ")
        ]
        for imported in imports:
            if not imported.startswith("java."):
                errors.append(
                    f"non-pure import in {path.relative_to(ROOT)}: {imported}"
                )
        for required in (
            "public static final int CURRENT_FORMAT = 1;",
            "public static Run decodeRun(String path, int[] encoded)",
            "public static List<Position> expandRun(",
            "public static List<Position> expandRuns(",
            '"Malformed run in " + path',
            '"Out-of-bounds run in " + path',
        ):
            if required not in text:
                errors.append(
                    f"missing pure farm run contract in {path.relative_to(ROOT)}: {required}"
                )

    parser = SOURCE_ROOT / "farm/FarmSpawnLayoutData.java"
    if not parser.is_file():
        errors.append(f"missing source parser: {parser.relative_to(ROOT)}")
    else:
        parser_text = parser.read_text(encoding="utf-8")
        for required in (
            "FarmSpawnLayoutRules.requireFormat",
            "FarmSpawnLayoutRules.requireSize",
            "FarmSpawnLayoutRules.decodeRun",
            "FarmSpawnLayoutRules.expandRun",
        ):
            if required not in parser_text:
                errors.append(f"source parser no longer delegates {required}")


def check_masks(errors: list[str]) -> None:
    for filename, expected_size in EXPECTED_SIZES.items():
        path = SOURCE_MASK_ROOT / filename
        if not path.is_file():
            errors.append(f"missing authoritative mask: {path.relative_to(ROOT)}")
            continue
        try:
            root = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            errors.append(f"could not read {path.relative_to(ROOT)}: {exc}")
            continue
        if root.get("format") != 1:
            errors.append(f"{filename}: format must remain 1")
        if tuple(root.get("size", ())) != expected_size:
            errors.append(
                f"{filename}: size drifted, expected {expected_size}, got {root.get('size')}"
            )
        runs = root.get("runs")
        counts = root.get("candidate_counts")
        if not isinstance(runs, dict) or not isinstance(counts, dict):
            errors.append(f"{filename}: missing runs/candidate_counts objects")
            continue
        width, height, length = expected_size
        for name, encoded_runs in runs.items():
            if not isinstance(encoded_runs, list):
                errors.append(f"{filename}:{name}: runs must be an array")
                continue
            expanded_count = 0
            for index, run in enumerate(encoded_runs):
                if not isinstance(run, list) or len(run) != 4:
                    errors.append(f"{filename}:{name}[{index}]: malformed run")
                    continue
                y, z, min_x, max_x = run
                if (
                    not all(isinstance(value, int) and not isinstance(value, bool)
                            for value in run)
                    or min_x < 0
                    or max_x < min_x
                    or max_x >= width
                    or y < 0
                    or y >= height
                    or z < 0
                    or z >= length
                ):
                    errors.append(f"{filename}:{name}[{index}]: out-of-bounds run")
                    continue
                expanded_count += max_x - min_x + 1
            if counts.get(name) != expanded_count:
                errors.append(
                    f"{filename}:{name}: candidate count drifted, "
                    f"expected {counts.get(name)}, expanded {expanded_count}"
                )
        for name in counts:
            if name not in runs and name != "ocean_component":
                errors.append(f"{filename}: count has no corresponding run: {name}")


def execute_forge_helper(errors: list[str]) -> None:
    """Compile and execute the Forge helper without requiring the full Forge toolchain."""
    forge = FORGE_ROOT / HELPER
    if not forge.is_file():
        return
    javac = java_tool("javac")
    java = java_tool("java")
    if javac is None or java is None:
        errors.append("Forge helper harness requires a JDK (javac/java not found)")
        return
    harness = """
import com.stardew.craft.farm.FarmSpawnLayoutRules;
import java.util.List;

public final class FarmSpawnLayoutRulesHarness {
    public static void main(String[] args) {
        var size = new FarmSpawnLayoutRules.Size(8, 4, 6);
        var runs = List.of(
                new FarmSpawnLayoutRules.Run(2, 3, 1, 3),
                new FarmSpawnLayoutRules.Run(0, 0, 7, 7));
        var positions = FarmSpawnLayoutRules.expandRuns("test", size, runs);
        if (positions.size() != 4
                || !positions.get(0).equals(
                        new FarmSpawnLayoutRules.Position(1, 2, 3))
                || !positions.get(3).equals(
                        new FarmSpawnLayoutRules.Position(7, 0, 0))) {
            throw new AssertionError("run expansion drifted: " + positions);
        }
        try {
            FarmSpawnLayoutRules.decodeRun("bad", new int[] {1, 2, 3});
            throw new AssertionError("malformed run was accepted");
        } catch (IllegalStateException expected) {
            // Contract preserved.
        }
        try {
            FarmSpawnLayoutRules.expandRun(
                    "bad", size, new FarmSpawnLayoutRules.Run(4, 0, 0, 0));
            throw new AssertionError("out-of-bounds run was accepted");
        } catch (IllegalStateException expected) {
            // Contract preserved.
        }
    }
}
"""
    with tempfile.TemporaryDirectory(prefix="stardewcraft-farm-rules-") as temp:
        temp_path = Path(temp)
        classes = temp_path / "classes"
        classes.mkdir()
        harness_path = temp_path / "FarmSpawnLayoutRulesHarness.java"
        harness_path.write_text(harness, encoding="utf-8")
        compile_result = subprocess.run(
            [
                javac,
                "-d",
                str(classes),
                str(forge),
                str(harness_path),
            ],
            capture_output=True,
            text=True,
        )
        if compile_result.returncode != 0:
            errors.append(
                "Forge helper javac failed: "
                + (compile_result.stderr.strip() or compile_result.stdout.strip())
            )
            return
        run_result = subprocess.run(
            [java, "-cp", str(classes), "FarmSpawnLayoutRulesHarness"],
            capture_output=True,
            text=True,
        )
        if run_result.returncode != 0:
            errors.append(
                "Forge helper harness failed: "
                + (run_result.stderr.strip() or run_result.stdout.strip())
            )


def main() -> int:
    errors: list[str] = []
    check_source_contract(errors)
    check_masks(errors)
    execute_forge_helper(errors)
    return fail(errors)


if __name__ == "__main__":
    raise SystemExit(main())

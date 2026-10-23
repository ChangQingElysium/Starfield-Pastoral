#!/usr/bin/env python3
"""Inventory Fabric port inputs and lexical coupling; this is not runtime validation."""

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[2]
SOURCE_TREES = ("src/main/java", "src/main/resources", "src/generated/resources")
BUILD_INPUTS = (
    "build.gradle", "settings.gradle", "gradle.properties", "requirements-build.txt",
    "gradle/wrapper/gradle-wrapper.properties", ".github/workflows/build.yml",
)
NATIVE_COMPILER_INPUTS = (
    "fabric/native-resources.gradle",
    "assets-src/npc/models.json",
    "assets-src/furniture/sebastian_computer/sebastian_computer.bbmodel",
    "tools/art/compile_native_npc.py", "tools/art/compile_sebastian_computer.py",
    "tools/art/npc_texture_atlas.py", "tools/art/npc_joint_skin.py",
    "requirements-build.txt",
)
CATEGORIES = {
    "events": r"net\.neoforged\.(?:neoforge\.event|bus)\b",
    "attachments": r"net\.neoforged\.neoforge\.attachment\b",
    "capabilities_items_fluids": r"net\.neoforged\.neoforge\.(?:capabilities|items|energy|fluids)\b",
    "network": r"net\.neoforged\.neoforge\.network\b",
    "client_render_models": r"net\.neoforged\.neoforge\.client\b",
    "registries_data_maps": r"net\.neoforged\.neoforge\.registries\b",
    "fml": r"net\.neoforged\.fml\b",
}
# A method spelling is a review candidate, not proof that a particular call is a
# NeoForge extension. Keep ambiguous names so import-free patched APIs are visible.
PATCH_CANDIDATES = {
    "getPersistentData": "Entity persistent NBT must retain save and player-copy semantics.",
    "getData": "Ambiguous spelling; inspect data-map and attachment holder calls.",
    "hasData": "Inspect attachment holder presence checks.",
    "setData": "Inspect attachment holder mutation and persistence.",
    "getCapability": "Inspect capability lookup and Fabric Transfer API transactions.",
    "getModelData": "Inspect dynamic model state and Fabric rendering model data.",
    "requestModelDataUpdate": "Inspect dynamic model invalidation and chunk rebuilds.",
    "getAuxLightManager": "NeoForge auxiliary lighting needs a real lighting adapter.",
    "canPerformAction": "Inspect item ability and tool action behavior.",
    "getFluidType": "Inspect NeoForge fluid type and client rendering behavior.",
    "invalidateCapabilities": "Inspect automation lookup cache invalidation.",
    "getExistingData": "Inspect attachment reads that must not initialize data.",
    "canSustainPlant": "Inspect plant support hooks and soil behavior.",
    "getToolModifiedState": "Inspect hoe/shovel/axe conversion hooks.",
    "onDestroyedByPlayer": "Inspect block destruction, harvest, and drop semantics.",
}
TOKEN_MASK = re.compile(r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/')
IMPORT = re.compile(r"^\s*import\s+(?:static\s+)?(net\.neoforged\.[\w.*]+)\s*;", re.MULTILINE)
EVENT_HANDLER = re.compile(
    r"@(?:[\w.]+\.)?SubscribeEvent(?:\s*\([^)]*\))?\s*"
    r"(?:@\w+(?:\([^)]*\))?\s*)*"
    r"(?:(?:public|private|protected|static|final|synchronized)\s+)*"
    r"void\s+\w+\s*\(\s*([^)]*)\)"
)


def digest(path):
    result = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            result.update(chunk)
    return result.hexdigest()


def portable_files(root, relative):
    """Reject source symlinks which could make an audit depend on external files."""
    directory = root / relative
    if not directory.is_dir():
        return []
    paths = sorted(path for path in directory.rglob("*") if path.is_file())
    for path in paths:
        if not path.resolve().is_relative_to(root):
            raise ValueError(f"Source points outside --root: {path.relative_to(root)}")
    return paths


def native_model_inputs(root):
    paths = {root / relative for relative in NATIVE_COMPILER_INPUTS if (root / relative).is_file()}
    source = root / "assets-src/npc"
    for path in portable_files(root, "assets-src/npc"):
        # This matches native-resources.gradle's declared input allowlist.
        if (path == source / "models.json" or path.suffix == ".bbmodel"
                or path.name == "motion.json" or path.name.endswith(".motion.json")):
            paths.add(path)
    return paths


def inventory_entries(root):
    paths = set()
    for relative in SOURCE_TREES + ("src/main/templates", "gradle"):
        paths.update(portable_files(root, relative))
    paths.update(root / relative for relative in BUILD_INPUTS if (root / relative).is_file())
    paths.update(native_model_inputs(root))
    entries = []
    for path in sorted(paths):
        if not path.resolve().is_relative_to(root):
            raise ValueError(f"Input points outside --root: {path.relative_to(root)}")
        entries.append({"path": path.relative_to(root).as_posix(),
                        "bytes": path.stat().st_size, "sha256": digest(path)})
    return entries


def tree_digest(entries):
    result = hashlib.sha256()
    for entry in sorted(entries, key=lambda value: value["path"]):
        result.update((entry["path"] + "\0" + entry["sha256"] + "\n").encode("utf-8"))
    return result.hexdigest()


def mask_non_code(source):
    return TOKEN_MASK.sub(lambda match: re.sub(r"[^\n]", " ", match.group()), source)


def locations(code, pattern):
    return [code.count("\n", 0, match.start()) + 1 for match in re.finditer(pattern, code)]


def java_inventory(root, paths):
    import_counts, event_types = Counter(), Counter()
    categories = {name: [] for name in CATEGORIES}
    patches = {name: {"explanation": explanation, "occurrences": 0, "files": []}
               for name, explanation in PATCH_CANDIDATES.items()}
    coupled, java_files = [], []
    line_count, payload_count, event_count = 0, 0, 0
    for path in paths:
        source = path.read_text(encoding="utf-8")
        code = mask_non_code(source)
        relative = path.relative_to(root).as_posix()
        java_files.append(relative)
        line_count += len(source.splitlines())
        imports = IMPORT.findall(code)
        import_counts.update(imports)
        qualified = locations(code, r"\bnet\.neoforged\.[\w.]+")
        if qualified:
            coupled.append({"path": relative, "imports": sorted(set(imports)),
                            "qualified_reference_lines": sorted(set(qualified))})
        for name, pattern in CATEGORIES.items():
            if re.search(pattern, code):
                categories[name].append(relative)
        for arguments in EVENT_HANDLER.findall(code):
            normalized = " ".join(arguments.split())
            event_types[normalized.rsplit(" ", 1)[0]] += 1
            event_count += 1
        payload_count += len(re.findall(r"\bimplements\s+(?:[\w.]+\.)?CustomPacketPayload\b", code))
        for name, record in patches.items():
            lines = locations(code, rf"\.\s*{name}\s*\(")
            if lines:
                record["occurrences"] += len(lines)
                record["files"].append({"path": relative, "lines": lines})
    return {
        "files": len(paths), "lines": line_count, "paths": java_files,
        "direct_import_files": sum(bool(entry["imports"]) for entry in coupled),
        "qualified_reference_files": len(coupled),
        "import_occurrences": sum(import_counts.values()),
        "unique_imports": len(import_counts), "imports": dict(sorted(import_counts.items())),
        "coupled_files": coupled,
        "categories": {name: {"count": len(files), "files": files}
                       for name, files in categories.items()},
        "event_handlers": {"count": event_count, "argument_type_count": len(event_types),
                           "argument_types": dict(sorted(event_types.items()))},
        "custom_packet_payload_declarations": payload_count,
        "hidden_patch_candidates": patches,
    }


def resource_inventory(root, entries):
    resources = [entry for entry in entries if entry["path"].startswith(
        ("src/main/resources/", "src/generated/resources/"))]
    extensions, loaders, mixins, invalid_json, utf8_bom = Counter(), {}, [], [], []
    for entry in resources:
        relative = entry["path"]
        extensions[Path(relative).suffix or "(none)"] += 1
        if not relative.endswith(".json"):
            continue
        try:
            raw = (root / relative).read_bytes()
            if raw.startswith(b"\xef\xbb\xbf"):
                utf8_bom.append(relative)
            value = json.loads(raw.decode("utf-8-sig"))
        except (ValueError, UnicodeError) as error:
            invalid_json.append({"path": relative, "error": str(error)})
            continue
        if not isinstance(value, dict):
            continue
        loader = value.get("loader")
        if isinstance(loader, str):
            loaders.setdefault(loader, []).append(relative)
        if "mixins" in value and isinstance(value.get("package"), str):
            mixins.append({"path": relative, "package": value["package"],
                           "plugin": value.get("plugin"),
                           "common": value.get("mixins", []),
                           "client": value.get("client", []),
                           "server": value.get("server", [])})
    return {
        "files": len(resources), "bytes": sum(entry["bytes"] for entry in resources),
        "tree_sha256": tree_digest(resources), "extensions": dict(sorted(extensions.items())),
        "model_loaders": {name: {"count": len(files), "files": sorted(files)}
                          for name, files in sorted(loaders.items())},
        "mixin_configurations": mixins, "invalid_json": invalid_json,
        "json_with_utf8_bom": utf8_bom,
    }


def compare_entries(before, after):
    old = {entry["path"]: entry["sha256"] for entry in before}
    new = {entry["path"]: entry["sha256"] for entry in after}
    return {"added": sorted(new.keys() - old.keys()),
            "removed": sorted(old.keys() - new.keys()),
            "modified": sorted(path for path in old.keys() & new.keys() if old[path] != new[path])}


def conversion_inventory(source, converted_root):
    converted_root = converted_root.resolve()
    java_root = converted_root / "src/main/java"
    if not java_root.is_dir():
        java_root = converted_root
    files = sorted(java_root.rglob("*.java"))
    for path in files:
        if not path.resolve().is_relative_to(converted_root):
            raise ValueError("Converted source points outside --converted-root")
    actual = {path.relative_to(java_root).as_posix(): path for path in files}
    expected = {path.removeprefix("src/main/java/") for path in source["java"]["paths"]}
    residual = []
    for relative, path in actual.items():
        code = mask_non_code(path.read_text(encoding="utf-8"))
        lines = locations(code, r"\bnet\.neoforged\.")
        if lines:
            residual.append({"path": relative, "lines": sorted(set(lines))})
    return {"evidence": "file_presence_and_lexical_scan_only",
            "expected_java_files": len(expected), "present_java_files": len(expected & actual.keys()),
            "missing_java_files": sorted(expected - actual.keys()),
            "additional_java_files": sorted(actual.keys() - expected),
            "residual_neoforge_files": residual,
            "runtime_equivalence": "not_evaluated"}


def build_inventory(root):
    root = root.resolve()
    if not (root / "src/main/java").is_dir():
        raise ValueError("--root must contain src/main/java")
    entries = inventory_entries(root)
    java_paths = [root / entry["path"] for entry in entries
                  if entry["path"].startswith("src/main/java/") and entry["path"].endswith(".java")]
    native_paths = {path.relative_to(root).as_posix() for path in native_model_inputs(root)}
    native_entries = [entry for entry in entries if entry["path"] in native_paths]
    return {
        "schema_version": 1,
        "evidence": "source_inventory_only",
        "runtime_equivalence": "not_evaluated",
        "limitations": [
            "No compilation, startup, gameplay, visual, save-migration or multiplayer validation.",
            "Lexical candidates require attribution; comments and literals are excluded from Java counts.",
            "File presence and zero loader imports do not prove that transformed behavior is implemented.",
            "Hashes describe current disk inputs, including uncommitted files; they are not a clean-checkout gate.",
            "Authoring inputs and external dependencies are not fully audited by this source inventory.",
        ],
        "baseline": {"tree_sha256": tree_digest(entries), "files": len(entries), "entries": entries},
        "native_model_inputs": {
            "files": len(native_entries), "tree_sha256": tree_digest(native_entries),
            "entries": native_entries,
            "missing_required_inputs": [relative for relative in NATIVE_COMPILER_INPUTS
                                        if not (root / relative).is_file()],
        },
        "java": java_inventory(root, java_paths),
        "resources": resource_inventory(root, entries),
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT, help="Repository to inventory (default: this repository).")
    parser.add_argument("--output", type=Path, help="Write JSON here; default: standard output.")
    parser.add_argument("--baseline", type=Path, help="Previous inventory JSON for added/removed/modified hashes.")
    parser.add_argument("--converted-root", type=Path, help="Optional generated Java tree or repository-layout output.")
    options = parser.parse_args(argv)
    try:
        report = build_inventory(options.root)
        if options.baseline:
            previous = json.loads(options.baseline.read_text(encoding="utf-8"))
            if previous.get("schema_version") != 1:
                raise ValueError("Unsupported baseline schema_version")
            report["drift"] = compare_entries(previous["baseline"]["entries"], report["baseline"]["entries"])
        if options.converted_root:
            if not options.converted_root.is_dir():
                raise ValueError("--converted-root must be an existing directory")
            report["conversion"] = conversion_inventory(report, options.converted_root.resolve())
        rendered = json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
        if options.output:
            options.output.parent.mkdir(parents=True, exist_ok=True)
            options.output.write_text(rendered, encoding="utf-8")
        else:
            sys.stdout.write(rendered)
    except (OSError, ValueError, KeyError) as error:
        parser.exit(1, f"Fabric inventory failed: {error}\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

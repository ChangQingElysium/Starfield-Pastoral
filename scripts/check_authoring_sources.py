#!/usr/bin/env python3
"""Check the public authoring catalog without reading local archives or rewriting resources."""
import argparse
import base64
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def relative_file(root: Path, relative: str) -> Path:
    path = Path(relative)
    if path.is_absolute() or ".." in path.parts:
        raise ValueError(f"Not a portable relative path: {relative}")
    result = root / path
    if not result.is_file() or not result.resolve().is_relative_to(root.resolve()):
        raise ValueError(f"Missing file or external dependency: {relative}")
    return result


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def check(root: Path, verify_exports: bool = False) -> tuple[int, int]:
    catalog = json.loads(relative_file(root, "assets-src/authoring.json").read_text(encoding="utf-8"))
    if catalog["schema_version"] != 1:
        raise ValueError("Unsupported authoring catalog version")
    entries = catalog["entries"]
    if not entries:
        raise ValueError("Empty authoring catalog")
    identities, all_outputs = set(), set()
    resources = root / "src/main/resources"
    for entry in entries:
        identity = entry["id"]
        if identity in identities or entry["role"] != "manual_authoring_source":
            raise ValueError(f"Duplicate ID or unsupported role: {identity}")
        identities.add(identity)
        if not entry["source"].startswith("assets-src/"):
            raise ValueError(f"Authoring source outside stable root: {identity}")
        source = relative_file(root, entry["source"])
        model = json.loads(source.read_text(encoding="utf-8"))
        if model["meta"]["model_format"] != "java_block":
            raise ValueError(f"Unexpected model format: {identity}")
        textures = model["textures"]
        if {texture["index"] for texture in entry["textures"]} != set(range(len(textures))):
            raise ValueError(f"Incomplete texture mapping: {identity}")
        for texture in entry["textures"]:
            embedded = textures[texture["index"]]["source"]
            if not embedded.startswith("data:image/png;base64,"):
                raise ValueError(f"Non-embedded texture: {identity}")
            if not texture["path"].startswith("assets-src/"):
                raise ValueError(f"Texture outside stable root: {identity}")
            external = relative_file(root, texture["path"])
            if base64.b64decode(embedded.partition(",")[2], validate=True) != external.read_bytes():
                raise ValueError(f"Embedded/external texture mismatch: {external.relative_to(root)}")
        exporter = relative_file(root, entry["exporter"])
        outputs = entry["outputs"]
        if not outputs or len(set(outputs)) != len(outputs) or all_outputs.intersection(outputs):
            raise ValueError(f"Empty or duplicate outputs: {identity}")
        for relative in outputs:
            if not relative.startswith(("assets/", "data/")):
                raise ValueError(f"Output outside resource namespaces: {relative}")
            relative_file(resources, relative)
        all_outputs.update(outputs)
        if verify_exports:
            with tempfile.TemporaryDirectory(prefix="starfield-authoring-check-") as temp:
                destination = Path(temp) / "resources"
                subprocess.run([sys.executable, str(exporter), "--output-root", str(destination)], cwd=root, check=True)
                actual = {str(path.relative_to(destination)) for path in destination.rglob("*") if path.is_file()}
                if actual != set(outputs):
                    raise ValueError(f"Exporter file set differs from catalog: {identity}")
                for relative in outputs:
                    if digest(destination / relative) != digest(resources / relative):
                        raise ValueError(f"Export differs from runtime: {identity}: {relative}")
    return len(identities), len(all_outputs)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--verify-exports", action="store_true", help="Export to temporary directories and compare every runtime output.")
    options = parser.parse_args()
    try:
        count, outputs = check(ROOT, options.verify_exports)
    except (ValueError, KeyError, OSError, subprocess.CalledProcessError) as error:
        raise SystemExit(str(error)) from error
    print(f"Authoring catalog OK: {count} sources, {outputs} outputs" + ("; isolated exports match runtime bytes" if options.verify_exports else ""))

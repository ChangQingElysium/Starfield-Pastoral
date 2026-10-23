#!/usr/bin/env python3
"""Index every maintained GameTest class for the executable Fabric generator."""

import argparse
import hashlib
import json
from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[2]
NON_CODE = re.compile(r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/')
TEST = re.compile(r"@(?:[\w$]+\.)*GameTest\b")
HOLDER = re.compile(r"@(?:[\w$]+\.)*GameTestHolder\b")


def index_sources(root):
    root = root.resolve()
    source_root = root / "src/main/java"
    paths = sorted(source_root.rglob("*.java"))
    if not paths:
        raise ValueError("The upstream Java source tree is empty")
    snapshots = {}
    classes = []
    for path in paths:
        if not path.resolve().is_relative_to(root):
            raise ValueError("GameTest source points outside --root")
        raw = path.read_bytes()
        snapshots[path] = hashlib.sha256(raw).hexdigest()
        source = raw.decode("utf-8-sig")
        code = NON_CODE.sub(lambda match: re.sub(r"[^\n]", " ", match.group()), source)
        count = len(TEST.findall(code))
        if not count:
            continue
        package = re.search(r"\bpackage\s+([\w$.]+)\s*;", code)
        if package is None or not re.search(r"\bclass\s+" + re.escape(path.stem) + r"\b", code):
            raise ValueError(f"GameTest must have a named top-level class: {path.relative_to(root)}")
        if len(HOLDER.findall(code)) != 1:
            raise ValueError(f"Expected one GameTestHolder for {path.relative_to(root)}")
        classes.append({"class_name": package.group(1) + "." + path.stem, "test_count": count,
                        "source_path": path.relative_to(root).as_posix(), "source_sha256": snapshots[path]})
    if not classes:
        raise ValueError("No GameTests found; refusing a silently empty suite")
    if sorted(source_root.rglob("*.java")) != paths or any(
            hashlib.sha256(path.read_bytes()).hexdigest() != digest for path, digest in snapshots.items()):
        raise ValueError("Upstream Java changed while indexing GameTests")
    return {"schema_version": 1, "total_tests": sum(entry["test_count"] for entry in classes),
            "classes": classes}


def write_index(root, output, report=None):
    root = root.resolve()
    build_root = (root / "fabric/build").resolve()
    for destination in [output] + ([report] if report is not None else []):
        if not destination.resolve().is_relative_to(build_root):
            raise ValueError("Generated GameTest outputs must stay inside fabric/build")
    if report is not None and report.resolve() == output.resolve():
        raise ValueError("--output and --report must be different files")
    result = index_sources(root)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if report is not None:
        report.parent.mkdir(parents=True, exist_ok=True)
        report.write_text(json.dumps({**result, "source_classes": len(result["classes"])},
                                     ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return result


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--output", type=Path, required=True,
                        help="Classpath JSON, normally generated/resources/gametest/META-INF/stardewcraft/fabric-gametest-index.json")
    parser.add_argument("--report", type=Path)
    options = parser.parse_args(argv)
    try:
        result = write_index(options.root, options.output, options.report)
    except (OSError, ValueError) as failure:
        parser.error(str(failure))
    print(f"Indexed all {result['total_tests']} GameTests in {len(result['classes'])} source classes")


if __name__ == "__main__":
    main()

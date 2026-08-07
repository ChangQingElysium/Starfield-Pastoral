#!/usr/bin/env python3
"""Verify the per-type maturity review for non-stable api.v1 types."""

from __future__ import annotations

import argparse
import re
from dataclasses import dataclass
from pathlib import Path


HEADER = (
    "type\tdisposition\tcore_ref\tsample_ref\ttest_ref\tdoc_ref\trationale"
)
DISPOSITION = "keep_experimental"


@dataclass(frozen=True)
class Evidence:
    core: bool
    sample: bool
    test: bool
    doc: bool


def _read_manifest_types(path: Path) -> list[str]:
    return sorted(
        line.strip()
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.startswith("#")
    )


def _read_symbols(paths: list[Path]) -> set[str]:
    symbols: set[str] = set()
    for path in paths:
        if path.is_file():
            symbols.update(
                re.findall(
                    r"\b[A-Za-z_$][A-Za-z0-9_$]*\b",
                    path.read_text(encoding="utf-8", errors="ignore"),
                )
            )
    return symbols


def collect_evidence(project_root: Path, types: list[str]) -> dict[str, Evidence]:
    main_root = project_root / "src/main/java/com/stardew/craft"
    core_paths = [
        path
        for path in main_root.rglob("*.java")
        if "/api/v1/" not in path.as_posix()
    ]
    sample_paths = list(
        (project_root / "examples/stardewcraft-addon").rglob("*.java")
    )
    doc_paths = list((project_root / "docs").rglob("*.md"))
    doc_paths.extend((project_root / "examples").rglob("README.md"))

    areas = {
        "core": _read_symbols(core_paths),
        "sample": _read_symbols(sample_paths),
        # Java tests are intentionally local-only and are not part of the
        # reproducible release tree, so they cannot count as published API
        # maturity evidence.
        "test": set(),
        "doc": _read_symbols(doc_paths),
    }
    evidence: dict[str, Evidence] = {}
    for type_name in types:
        simple_name = type_name.rsplit(".", 1)[-1]
        evidence[type_name] = Evidence(
            core=simple_name in areas["core"],
            sample=simple_name in areas["sample"],
            test=simple_name in areas["test"],
            doc=simple_name in areas["doc"],
        )
    return evidence


def rationale_for(evidence: Evidence) -> str:
    if not evidence.core:
        return "core_runtime_reference_missing"
    if not evidence.sample:
        return "independent_sample_reference_missing"
    if not evidence.test:
        return "direct_test_reference_missing"
    if not evidence.doc:
        return "direct_documentation_reference_missing"
    return "real_network_and_second_consumer_evidence_pending"


def render_review(types: list[str], evidence: dict[str, Evidence]) -> str:
    lines = [
        "# Per-type review of api.v1 types excluded from the stable binary baseline.",
        "# Evidence columns record direct symbol references, not inferred coverage.",
        "# A type remains non-stable until every release-grade runtime gate is proven.",
        HEADER,
    ]
    for type_name in types:
        item = evidence[type_name]
        lines.append(
            "\t".join(
                (
                    type_name,
                    DISPOSITION,
                    "yes" if item.core else "no",
                    "yes" if item.sample else "no",
                    "yes" if item.test else "no",
                    "yes" if item.doc else "no",
                    rationale_for(item),
                )
            )
        )
    return "\n".join(lines) + "\n"


def verify_review(
    experimental_path: Path,
    review_path: Path,
    project_root: Path,
) -> None:
    types = _read_manifest_types(experimental_path)
    expected = render_review(types, collect_evidence(project_root, types))
    actual = review_path.read_text(encoding="utf-8")
    if actual == expected:
        return

    expected_lines = expected.splitlines()
    actual_lines = actual.splitlines()
    mismatch = next(
        (
            index
            for index, (left, right) in enumerate(
                zip(expected_lines, actual_lines), start=1
            )
            if left != right
        ),
        min(len(expected_lines), len(actual_lines)) + 1,
    )
    expected_line = (
        expected_lines[mismatch - 1]
        if mismatch <= len(expected_lines)
        else "<end of file>"
    )
    actual_line = (
        actual_lines[mismatch - 1]
        if mismatch <= len(actual_lines)
        else "<end of file>"
    )
    raise ValueError(
        "api maturity review is stale at line "
        f"{mismatch}:\n  expected: {expected_line}\n  actual:   {actual_line}\n"
        "Review the evidence change, then regenerate deliberately with "
        "--render."
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--project-root",
        type=Path,
        default=Path(__file__).resolve().parents[1],
    )
    parser.add_argument(
        "--experimental",
        type=Path,
        default=Path("compatibility/experimental-api-v1.txt"),
    )
    parser.add_argument(
        "--review",
        type=Path,
        default=Path("compatibility/api-maturity-review-v1.tsv"),
    )
    parser.add_argument(
        "--render",
        action="store_true",
        help="print the exact reviewed manifest expected for the current tree",
    )
    args = parser.parse_args()
    project_root = args.project_root.resolve()
    experimental_path = (
        args.experimental
        if args.experimental.is_absolute()
        else project_root / args.experimental
    )
    review_path = (
        args.review if args.review.is_absolute() else project_root / args.review
    )
    types = _read_manifest_types(experimental_path)
    evidence = collect_evidence(project_root, types)
    if args.render:
        print(render_review(types, evidence), end="")
        return 0
    verify_review(experimental_path, review_path, project_root)
    print(f"verified {len(types)} per-type api maturity decisions")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

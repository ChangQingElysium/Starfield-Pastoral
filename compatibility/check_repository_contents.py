#!/usr/bin/env python3
"""Reject local/generated files in the Git index, including force-added files.

Run from any directory: python3 compatibility/check_repository_contents.py
Only names in the index are inspected; saves and untracked local work are untouched.
"""
from __future__ import annotations

import os
from pathlib import Path, PurePosixPath
import re
import subprocess


ROOT = Path(__file__).resolve().parents[1]
LOCAL_DIRS = {"build", ".gradle", ".local", "__pycache__", ".venv", "node_modules"}
LOCAL_ROOTS = {"tmp", ".tmp", "run", "runs", "logs", "本地资料", "源文件", "bb学习"}
REVIEWED_JARS = {
    "gradle/wrapper/gradle-wrapper.jar",
    "libs/Jade-1.21.1-NeoForge-15.10.4.jar",  # Explicit NeoForge compileOnly input.
}


def violation(path: str) -> str | None:
    parts = PurePosixPath(path).parts
    if parts[0] in LOCAL_ROOTS or LOCAL_DIRS.intersection(parts[:-1]):
        return "local workspace or generated output"
    if any(re.fullmatch(r"run(?:[-_].+)?", p) for p in parts[:-1]):
        return "game runtime directory"
    if re.fullmatch(r"javac\..*\.args", parts[-1]):
        return "compiler diagnostic arguments"
    if path.startswith("scripts/data/") and path.endswith("_report.json"):
        return "generated importer report"
    if parts[-1] == ".DS_Store" or path.endswith((".log", ".jfr", ".hprof", ".pyc")):
        return "local diagnostic or cache"
    if path.endswith((".jar", ".zip")) and path not in REVIEWED_JARS:
        return "unreviewed binary archive; use dependency resolution or release artifacts"
    return None


def check(root: Path) -> list[str]:
    tracked = subprocess.check_output(["git", "ls-files", "--cached", "-z"], cwd=root)
    paths = [p.decode("utf-8") for p in tracked.split(b"\0") if p]
    problems = [f"{p}: {reason}" for p in paths if (reason := violation(p))]
    # --no-index is essential: ordinary check-ignore silently skips tracked files.
    result = subprocess.run(
        ["git", "-c", f"core.excludesFile={os.devnull}", "check-ignore", "--no-index", "--stdin", "-z"],
        cwd=root, input=tracked, capture_output=True,
    )
    if result.returncode not in (0, 1):
        raise RuntimeError(result.stderr.decode("utf-8", errors="replace"))
    problems.extend(f"{p.decode('utf-8')}: excluded by repository ignore policy"
                    for p in result.stdout.split(b"\0") if p)
    return sorted(set(problems))


def main() -> int:
    problems = check(ROOT)
    if problems:
        print("Repository content check failed:")
        for problem in problems:
            print(f"  {problem}")
        return 1
    print("Repository content check passed: no local/generated or ignored files in the Git index.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

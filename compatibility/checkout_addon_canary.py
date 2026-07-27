#!/usr/bin/env python3
"""Checkout one pinned addon canary from the repository manifest."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path


def fail(message: str) -> None:
    raise SystemExit(f"Addon canary checkout failed: {message}")


def main() -> None:
    if len(sys.argv) != 3:
        fail("usage: checkout_addon_canary.py <addon-id> <destination>")

    project_root = Path(__file__).resolve().parents[1]
    addon_id = sys.argv[1]
    destination = Path(sys.argv[2]).resolve()
    manifest = json.loads(
        (project_root / "compatibility/addon-canaries.json").read_text(
            encoding="utf-8"
        )
    )
    addon = next(
        (entry for entry in manifest["addons"] if entry["id"] == addon_id),
        None,
    )
    if addon is None:
        fail(f"{addon_id} is missing from addon-canaries.json")
    if destination.exists():
        fail(f"destination already exists: {destination}")

    destination.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        [
            "git",
            "clone",
            "--filter=blob:none",
            "--no-checkout",
            addon["repository"],
            str(destination),
        ],
        check=True,
    )
    subprocess.run(
        ["git", "-C", str(destination), "checkout", addon["commit"]],
        check=True,
    )


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Bring the 1.20.1 port up to date with the 1.21.1 main workspace (including uncommitted work).

    python3 scripts/port/sync_from_main.py [--main ../StardewCraft] [--dry-run]

1. Snapshot the whole main workspace (tracked + untracked, honouring .gitignore) into a commit
   stored at refs/port/main-snapshots/<n>, using a private index so main's index, HEAD and
   files are never touched.
2. Java (src/main/java): for every file that changed between the previous snapshot and the new
   one, 3-way merge   base = port-scripts(previous) | ours = port tree | theirs = port-scripts(new)
   so hand adaptations in the port survive. Conflicts are left with markers and listed.
3. Resources (src/main/resources): mirrored from main, then the deterministic conversions
   (convert_resources_1201.py, nbt_downgrade_1201.py) are re-run. Port-owned resource files are
   kept; stardewcraft.mixins.json is 3-way merged.
4. The new snapshot is recorded in scripts/port/main-sync-state.txt.
"""
from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
STATE = ROOT / "scripts/port/main-sync-state.txt"
# Order matters: every later script expects the output of the earlier ones.
SCRIPTS = [ROOT / "scripts/port/rewrite_1201.py", ROOT / "scripts/port/adapt_nbt_provider.py",
           ROOT / "scripts/port/adapt_block_use.py", ROOT / "scripts/port/adapt_overrides_1201.py",
           ROOT / "scripts/port/adapt_vertex_api.py", ROOT / "scripts/port/strict_optional_fields_1201.py"]
PORT_OWNED_RESOURCES = {
    "META-INF/mods.toml",
    "META-INF/accesstransformer.cfg",
    "pack.mcmeta",
}
MERGED_RESOURCES = {"stardewcraft.mixins.json"}
# Version-independent build inputs mirrored verbatim (compiled into resources by build.gradle).
MIRRORED_PATHS = ["assets-src/npc", "assets-src/furniture/sebastian_computer", "tools/art", "tools/npc"]


def git(*args, env=None, cwd=ROOT, check=True, binary=False) -> str | bytes:
    res = subprocess.run(["git", *args], cwd=cwd, env=env, capture_output=True, check=False)
    if check and res.returncode != 0:
        raise SystemExit(f"git {' '.join(args)} failed:\n{res.stderr.decode()}")
    return res.stdout if binary else res.stdout.decode().strip()


def snapshot(main: Path, parent: str) -> str:
    with tempfile.TemporaryDirectory() as tmp:
        env = dict(os.environ, GIT_INDEX_FILE=str(Path(tmp) / "index"))
        git("--work-tree", str(main), "read-tree", parent, env=env)
        git("--work-tree", str(main), "add", "-A", env=env)
        tree = git("write-tree", env=env)
    commit = git("commit-tree", tree, "-p", parent, "-m", f"port: snapshot of {main} workspace")
    n = len(git("for-each-ref", "refs/port/main-snapshots/").splitlines())
    git("update-ref", f"refs/port/main-snapshots/{n:04d}", commit)
    return commit


def changed_paths(old: str, new: str, prefix: str) -> list[tuple[str, str]]:
    out = git("diff", "--name-status", "--no-renames", old, new, "--", prefix)
    return [tuple(line.split("\t", 1)) for line in out.splitlines() if line]


def scripted(commit: str, path: str, workdir: Path) -> bytes | None:
    if subprocess.run(["git", "cat-file", "-e", f"{commit}:{path}"], cwd=ROOT,
                      capture_output=True).returncode != 0:
        return None
    data = git("show", f"{commit}:{path}", binary=True)
    target = workdir / Path(path).name
    target.write_bytes(data)
    for script in SCRIPTS:
        subprocess.run([sys.executable, str(script), str(target)], check=True, capture_output=True)
    return target.read_bytes()


def merge_java(old: str, new: str, dry: bool) -> tuple[list[str], list[str]]:
    conflicts, touched = [], []
    with tempfile.TemporaryDirectory() as tmp:
        tmpd = Path(tmp)
        for status, path in changed_paths(old, new, "src/main/java"):
            ours_path = ROOT / path
            (tmpd / "b").mkdir(exist_ok=True); (tmpd / "t").mkdir(exist_ok=True)
            base = scripted(old, path, tmpd / "b")
            theirs = scripted(new, path, tmpd / "t")
            ours = ours_path.read_bytes() if ours_path.exists() else None
            touched.append(f"{status} {path}")
            if dry:
                continue
            if theirs is None:  # deleted in main
                if ours is not None and (base is None or ours == base):
                    ours_path.unlink()
                elif ours is not None:
                    conflicts.append(f"deleted in main but adapted in port: {path}")
                continue
            if ours is None or base is None or ours == base:
                ours_path.parent.mkdir(parents=True, exist_ok=True)
                ours_path.write_bytes(theirs)
                continue
            fb, ft = tmpd / "base.java", tmpd / "theirs.java"
            fb.write_bytes(base); ft.write_bytes(theirs)
            rc = subprocess.run(["git", "merge-file", "-L", "port", "-L", "main-previous", "-L", "main-latest",
                                 str(ours_path), str(fb), str(ft)]).returncode
            if rc != 0:
                conflicts.append(path)
    return touched, conflicts


def sync_resources(main: Path, old: str, new: str, dry: bool) -> list[str]:
    src, dst = main / "src/main/resources", ROOT / "src/main/resources"
    if dry:
        return []
    keep = {p: (dst / p).read_bytes() for p in PORT_OWNED_RESOURCES if (dst / p).exists()}
    conflicts = []
    merged = {}
    with tempfile.TemporaryDirectory() as tmp:
        for p in MERGED_RESOURCES:
            rel = f"src/main/resources/{p}"
            ours = dst / p
            base = git("show", f"{old}:{rel}", binary=True, check=False)
            theirs = git("show", f"{new}:{rel}", binary=True, check=False)
            fb, ft, fo = Path(tmp) / "b", Path(tmp) / "t", Path(tmp) / "o"
            fb.write_bytes(base); ft.write_bytes(theirs); fo.write_bytes(ours.read_bytes())
            if subprocess.run(["git", "merge-file", str(fo), str(fb), str(ft)]).returncode != 0:
                conflicts.append(rel)
            merged[p] = fo.read_bytes()
    # Mirror tracked + untracked (non-ignored) resource files of the snapshot.
    shutil.rmtree(dst)
    files = git("ls-tree", "-r", "--name-only", new, "--", "src/main/resources").splitlines()
    for rel in files:
        target = ROOT / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(main / rel, target)
    for p, data in {**keep, **merged}.items():
        (dst / p).parent.mkdir(parents=True, exist_ok=True)
        (dst / p).write_bytes(data)
    subprocess.run([sys.executable, str(ROOT / "scripts/port/convert_resources_1201.py")], check=True)
    subprocess.run([sys.executable, str(ROOT / "scripts/port/nbt_downgrade_1201.py"), "convert"], check=True)
    return conflicts


def mirror_paths(main: Path, new: str, dry: bool) -> int:
    count = 0
    for prefix in MIRRORED_PATHS:
        wanted = set(git("ls-tree", "-r", "--name-only", new, "--", prefix).splitlines())
        here = ROOT / prefix
        existing = {str(p.relative_to(ROOT)) for p in here.rglob("*") if p.is_file()} if here.exists() else set()
        if dry:
            count += len(wanted ^ existing)
            continue
        for rel in existing - wanted:
            (ROOT / rel).unlink()
            count += 1
        for rel in wanted:
            data = git("show", f"{new}:{rel}", binary=True)
            target = ROOT / rel
            if not target.exists() or target.read_bytes() != data:
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(data)
                count += 1
    return count


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--main", default=str(ROOT.parent / "StardewCraft"))
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--snapshot", help="use an existing snapshot commit instead of taking a new one")
    args = ap.parse_args()
    main_dir = Path(args.main).resolve()
    old = STATE.read_text().split()[0]
    new = git("rev-parse", args.snapshot) if args.snapshot else snapshot(main_dir, old)
    if git("rev-parse", f"{old}^{{tree}}") == git("rev-parse", f"{new}^{{tree}}"):
        print("main unchanged since last sync")
        return 0
    touched, conflicts = merge_java(old, new, args.dry_run)
    conflicts += sync_resources(main_dir, old, new, args.dry_run)
    print(f"mirrored build-input files changed: {mirror_paths(main_dir, new, args.dry_run)}")
    print(f"snapshot {new}: {len(touched)} java paths changed")
    for line in touched:
        print("  ", line)
    if conflicts:
        print("CONFLICTS (resolve, then rerun the javac loop):")
        for c in conflicts:
            print("  ", c)
    if not args.dry_run:
        STATE.write_text(new + "\n")
        # Silent-behaviour idioms that compile on 1.20.1 (sprite UVs, NBT block pos, DFU optionals...).
        print("lint:")
        subprocess.run([sys.executable, str(ROOT / "scripts/port/lint_port.py")])
        print("next: scripts/port/javac_all.sh, then fix_java21_errors.py / fix_errors_1201.py on its log")
    return 1 if conflicts else 0


if __name__ == "__main__":
    sys.exit(main())

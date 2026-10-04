#!/usr/bin/env python3
"""Three-way synchronize the Forge port from a stable main-workspace snapshot.

    python3 scripts/port/sync_from_main.py [--main ../StardewCraft] [--dry-run]
    python3 scripts/port/sync_from_main.py --snapshot <commit>
    python3 scripts/port/sync_from_main.py --complete-snapshot <commit>

Resources and build inputs come from Git blobs, never from live main files after
the snapshot. Both resource snapshots are converted in staging before any port
files change. The converted previous snapshot is the merge base, so manual Forge
adaptations and genuinely port-only files survive. Only the three platform files
below are wholly port-owned; mixins and other text resources use three-way merges.

A dry run uses a private index/tree without creating a commit or ref, changing the
port files, or advancing STATE. Conflicts leave STATE at its previous snapshot;
after resolving text markers and reviewing binary/delete/add collisions, explicitly
complete that exact snapshot. Completion runs lint but is not a build/release gate.
"""
from __future__ import annotations

import argparse
import contextlib
import importlib.util
import io
import json
import os
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
STATE = ROOT / "scripts/port/main-sync-state.txt"
PENDING = ROOT / "build/port-sync-pending.json"
# Order matters: every later script expects the output of the earlier ones.
SCRIPTS = [ROOT / "scripts/port/rewrite_1201.py", ROOT / "scripts/port/adapt_nbt_provider.py",
           ROOT / "scripts/port/adapt_block_use.py", ROOT / "scripts/port/adapt_overrides_1201.py",
           ROOT / "scripts/port/adapt_vertex_api.py", ROOT / "scripts/port/strict_optional_fields_1201.py"]
PORT_OWNED_RESOURCES = {
    "META-INF/mods.toml",
    "META-INF/accesstransformer.cfg",
    "pack.mcmeta",
}
# Version-independent build inputs compiled into resources by build.gradle.
MIRRORED_PATHS = ["assets-src/npc", "assets-src/furniture/sebastian_computer", "tools/art", "tools/npc"]
RESOURCE_PREFIX = "src/main/resources"
CONFLICT_MARKERS = (b"<<<<<<< ", b"=======", b">>>>>>> ")


def git(*args, env=None, cwd=None, check=True, binary=False) -> str | bytes:
    res = subprocess.run(["git", *args], cwd=ROOT if cwd is None else cwd,
                         env=env, capture_output=True, check=False)
    if check and res.returncode != 0:
        raise SystemExit(f"git {' '.join(args)} failed:\n{res.stderr.decode()}")
    return res.stdout if binary else res.stdout.decode().strip()


def snapshot(main: Path, parent: str, dry: bool = False) -> str:
    with tempfile.TemporaryDirectory() as tmp:
        env = dict(os.environ, GIT_INDEX_FILE=str(Path(tmp) / "index"))
        git("--work-tree", str(main), "read-tree", parent, env=env)
        git("--work-tree", str(main), "add", "-A", env=env)
        tree = git("write-tree", env=env)
    if dry:
        return tree
    commit = git("commit-tree", tree, "-p", parent, "-m", f"port: snapshot of {main} workspace")
    refs = git("for-each-ref", "--format=%(refname)", "refs/port/main-snapshots/").splitlines()
    numbers = [int(ref.rsplit("/", 1)[1]) for ref in refs if ref.rsplit("/", 1)[1].isdigit()]
    n = max(numbers, default=-1) + 1
    # The expected zero ref prevents concurrent snapshots from overwriting one another.
    git("update-ref", f"refs/port/main-snapshots/{n:04d}", commit, "0" * 40)
    return commit


def tree_paths(rev: str, prefix: str) -> set[str]:
    return set(git("ls-tree", "-r", "--name-only", rev, "--", prefix).splitlines())


def changed_paths(old: str, new: str, prefix: str) -> list[tuple[str, str]]:
    out = git("diff", "--name-status", "--no-renames", old, new, "--", prefix)
    return [tuple(line.split("\t", 1)) for line in out.splitlines() if line]


def local_path(rel: str) -> Path:
    path = Path(rel)
    if path.is_absolute() or ".." in path.parts:
        raise SystemExit(f"unsafe sync path: {rel}")
    target = ROOT / path
    for part in [target, *target.parents]:
        if part == ROOT:
            break
        if part.is_symlink():
            raise SystemExit(f"refusing to synchronize through symlink: {part}")
    return target


def read_local(rel: str) -> bytes | None:
    target = local_path(rel)
    if target.exists() and not target.is_file():
        raise SystemExit(f"sync file collides with a directory: {rel}")
    return target.read_bytes() if target.exists() else None


def set_file(path: Path, data: bytes | None) -> None:
    if data is None:
        if path.exists():
            path.unlink()
    else:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)


def merge_bytes(base: bytes | None, ours: bytes | None,
                theirs: bytes | None) -> tuple[bytes | None, bool]:
    if ours == theirs or theirs == base:
        return ours, False
    if ours == base:
        return theirs, False
    # Add/add and modify/delete must not overwrite an existing port adaptation.
    if base is None or ours is None or theirs is None:
        return ours, True
    try:
        for data in (base, ours, theirs):
            data.decode("utf-8")
            if b"\0" in data:
                return ours, True
    except UnicodeDecodeError:
        return ours, True
    with tempfile.TemporaryDirectory() as tmp:
        previous, current, incoming = (Path(tmp) / name for name in ("base", "ours", "theirs"))
        previous.write_bytes(base)
        current.write_bytes(ours)
        incoming.write_bytes(theirs)
        res = subprocess.run(["git", "merge-file", "-L", "port", "-L", "main-previous",
                              "-L", "main-latest", str(current), str(previous), str(incoming)],
                             capture_output=True)
        # merge-file returns the number of conflicts (up to 127), not just 1.
        if res.returncode < 0 or res.returncode > 127:
            raise SystemExit(f"git merge-file failed:\n{res.stderr.decode()}")
        return current.read_bytes(), res.returncode != 0


class JavaSource:
    """Read-only source view for adapters' unchanged, live Forge context."""

    def __init__(self, path: Path):
        self.path = path
        self.text = path.read_text(encoding="utf-8")

    def read_text(self, encoding=None, errors=None):
        return self.text

    def __getattr__(self, name):
        return getattr(self.path, name)

    def __fspath__(self):
        return os.fspath(self.path)

    def __str__(self):
        return str(self.path)


class JavaBatchConverters:
    """Reuse adapter modules and their frozen context, keeping per-file semantics.

    Passing every incoming file to adapt_block_use.main at once changes its class
    index: another pending parent/sibling can replace the live Forge definition.
    Instead each file still runs the same six entry points alone, in the original
    order. Only the unchanged ROOT source reads/parses are cached. The cache lives
    for this plan only, and is never used for staging targets or another sync.
    """

    def __init__(self):
        self.adapters = []
        self.sources = None
        for script in SCRIPTS:
            spec = importlib.util.spec_from_file_location(f"port_sync_java_{script.stem}", script)
            adapter = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(adapter)
            self.adapters.append(adapter)
            if script.stem in ("adapt_block_use", "adapt_overrides_1201"):
                self.cache_source_context(adapter, script.stem)
            elif script.stem == "rewrite_1201":
                rules = adapter.build_rules()
                adapter.build_rules = lambda rules=rules: rules

    def cache_source_context(self, adapter, name):
        source_root = adapter.ROOT / "src/main/java"
        if self.sources is None:
            self.sources = tuple(JavaSource(path) for path in sorted(source_root.rglob("*.java")))
        original_collect = adapter.collect

        def collect(paths):
            if len(paths) == 1 and Path(paths[0]) == source_root:
                return self.sources
            return original_collect(paths)

        adapter.collect = collect
        if name == "adapt_block_use":
            original_java_file = adapter.JavaFile
            parsed = {source: original_java_file(source) for source in self.sources}

            class CachedJavaFile(original_java_file):
                def __new__(cls, path=None):
                    # widen() also allocates a parser with JavaFile.__new__(JavaFile).
                    if path in parsed:
                        return parsed[path]
                    return super().__new__(cls)

            adapter.JavaFile = CachedJavaFile
        else:
            # Both queries are functions of the frozen Forge tree, not staging.
            blocks = adapter.block_class_names(self.sources)
            original_block_names = adapter.block_class_names
            adapter.block_class_names = lambda paths: (blocks if paths is self.sources
                                                       else original_block_names(paths))
            codec_pattern = r"(?<![\w])(\w+)\.CODEC\b"
            matches = {source.text: tuple(re.finditer(codec_pattern, source.text))
                       for source in self.sources}

            class ContextRegex:
                def finditer(self, pattern, text, flags=0):
                    if pattern == codec_pattern and flags == 0 and text in matches:
                        return iter(matches[text])
                    return re.finditer(pattern, text, flags)

                def __getattr__(self, attr):
                    return getattr(re, attr)

            adapter.re = ContextRegex()

    def convert(self, targets: list[Path]) -> None:
        for target in targets:
            for adapter in self.adapters:
                # The previous subprocess runner also captured adapter diagnostics.
                with contextlib.redirect_stdout(io.StringIO()):
                    status = adapter.main([str(target)])
                if status not in (None, 0):
                    raise SystemExit(f"Java adapter {adapter.__file__} failed: {status}")


def plan_java(old: str, new: str, workdir: Path) -> tuple[dict, list[str], list[str]]:
    changes, touched, conflicts = {}, [], []
    paths = changed_paths(old, new, "src/main/java")
    if not paths:
        return changes, touched, conflicts
    previous, incoming = workdir / "java-base", workdir / "java-incoming"
    previous.mkdir()
    incoming.mkdir()
    old_paths = [path for status, path in paths if status != "A"]
    new_paths = [path for status, path in paths if status != "D"]
    export_tree(old, old_paths, previous, known_existing=True)
    export_tree(new, new_paths, incoming, known_existing=True)
    converters = JavaBatchConverters()
    converters.convert([previous / path for path in old_paths])
    converters.convert([incoming / path for path in new_paths])
    for status, path in paths:
        base = (previous / path).read_bytes() if status != "A" else None
        theirs = (incoming / path).read_bytes() if status != "D" else None
        ours = read_local(path)
        merged, conflict = merge_bytes(base, ours, theirs)
        touched.append(f"{status} {path}")
        if merged != ours:
            changes[path] = merged
        if conflict:
            conflicts.append(path)
    return changes, touched, conflicts


def export_tree(rev: str, prefixes: list[str], project: Path, known_existing: bool = False) -> None:
    """Export all requested blobs in one Git process, rejecting links/path escapes."""
    existing = prefixes if known_existing else [prefix for prefix in prefixes if tree_paths(rev, prefix)]
    if not existing:
        return
    process = subprocess.Popen(["git", "archive", "--format=tar", rev, "--", *existing],
                               cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    try:
        with tarfile.open(fileobj=process.stdout, mode="r|") as archive:
            for member in archive:
                rel = Path(member.name)
                if rel.is_absolute() or ".." in rel.parts:
                    raise SystemExit(f"unsafe snapshot archive path: {member.name}")
                target = project / rel
                if member.isdir():
                    target.mkdir(parents=True, exist_ok=True)
                elif member.isfile():
                    if not any(member.name == prefix or member.name.startswith(prefix + "/") for prefix in existing):
                        raise SystemExit(f"unexpected snapshot archive file: {member.name}")
                    target.parent.mkdir(parents=True, exist_ok=True)
                    with archive.extractfile(member) as source, target.open("wb") as output:
                        shutil.copyfileobj(source, output)
                    target.chmod(member.mode & 0o777)
                else:
                    raise SystemExit(f"unsupported snapshot archive entry: {member.name}")
        _, stderr = process.communicate()
        if process.returncode != 0:
            raise SystemExit(f"git archive {rev} failed:\n{stderr.decode()}")
    except BaseException:
        if process.poll() is None:
            process.terminate()
            process.communicate()
        else:
            process.stdout.close()
            process.stderr.close()
        raise


def load_conversion(name: str):
    path = ROOT / f"scripts/port/{name}.py"
    spec = importlib.util.spec_from_file_location(f"port_sync_{name}", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def check_conversion_inputs() -> None:
    """The existing converters require prepared 1.20.1 facts, not a Gradle run here."""
    mc = ROOT / "build/port-mc-src/net/minecraft"
    needed = [ROOT / "build/port-classpath.txt", *[mc / path for path in (
        "world/item/Items.java", "world/level/block/Blocks.java", "world/level/biome/Biomes.java",
        "world/item/enchantment/Enchantments.java", "world/entity/EntityType.java",
        "world/level/material/Fluids.java")]]
    missing = [str(path.relative_to(ROOT)) for path in needed if not path.is_file()]
    if missing:
        raise SystemExit("resource conversion inputs missing; prepare the 1.20.1 port tooling first:\n  "
                         + "\n  ".join(missing))
    jars = [Path(p) for p in needed[0].read_text().strip().split(":") if p.endswith("client-extra.jar")]
    if not jars or not all(path.is_file() for path in jars):
        raise SystemExit("resource conversion requires an existing client-extra.jar in build/port-classpath.txt")


def convert_resources(resources: Path) -> None:
    # The converters have no resource-root CLI. Redirect only their resource globals;
    # ROOT must stay real for vanilla source/classpath inputs and diagnostic paths.
    conversion = load_conversion("convert_resources_1201")
    conversion.DATA = resources / "data"
    conversion.MODELS = resources / "assets/stardewcraft/models"
    conversion.DATA.mkdir(parents=True, exist_ok=True)
    argv = sys.argv
    try:
        sys.argv = [str(ROOT / "scripts/port/convert_resources_1201.py")]
        if conversion.main() != 0:
            raise SystemExit("resource conversion failed")
    finally:
        sys.argv = argv
    nbt = load_conversion("nbt_downgrade_1201")
    nbt.RES = resources
    if nbt.main(["convert"]) != 0:
        raise SystemExit("NBT resource conversion failed")


def files_under(root: Path) -> dict[str, Path]:
    files = {}
    for path in root.rglob("*"):
        if path.is_symlink():
            raise SystemExit(f"refusing symlink in resource/build-input tree: {path}")
        if path.is_file():
            files[path.relative_to(root).as_posix()] = path
    return files


def prepare_resources(previous: Path, incoming: Path) -> list[str]:
    dst = local_path(RESOURCE_PREFIX)
    if dst.exists() and not dst.is_dir():
        raise SystemExit("src/main/resources is not a directory")
    base_files, new_files = files_under(previous), files_under(incoming)
    ours_files = files_under(dst)
    conflicts = []
    for rel in sorted(base_files.keys() | new_files.keys() | ours_files.keys()):
        base = base_files[rel].read_bytes() if rel in base_files else None
        theirs = new_files[rel].read_bytes() if rel in new_files else None
        ours = ours_files[rel].read_bytes() if rel in ours_files else None
        if rel in PORT_OWNED_RESOURCES:
            merged, conflict = ours, False
        else:
            merged, conflict = merge_bytes(base, ours, theirs)
        if conflict:
            conflicts.append(f"{RESOURCE_PREFIX}/{rel}")
        set_file(incoming / rel, merged)
    return conflicts


def plan_mirrors(previous: Path, incoming: Path) -> tuple[dict, list[str]]:
    changes, conflicts = {}, []
    for prefix in MIRRORED_PATHS:
        base_files = files_under(previous / prefix)
        new_files = files_under(incoming / prefix)
        # Do not enumerate/delete unrelated local or ignored authoring files.
        for path in sorted(base_files.keys() | new_files.keys()):
            rel = f"{prefix}/{path}"
            base = base_files[path].read_bytes() if path in base_files else None
            theirs = new_files[path].read_bytes() if path in new_files else None
            ours = read_local(rel)
            merged, conflict = merge_bytes(base, ours, theirs)
            if merged != ours:
                changes[rel] = merged
            if conflict:
                conflicts.append(rel)
    return changes, conflicts


def replace_resources(incoming: Path, backup: Path) -> None:
    dst = local_path(RESOURCE_PREFIX)
    existed = dst.exists()
    if existed:
        dst.rename(backup)
    try:
        dst.parent.mkdir(parents=True, exist_ok=True)
        incoming.rename(dst)
    except BaseException:
        if existed:
            backup.rename(dst)
        raise


def run_lint() -> int:
    return subprocess.run([sys.executable, str(ROOT / "scripts/port/lint_port.py")]).returncode


def save_pending(old: str, new: str, conflicts: list[str], applied: bool) -> None:
    PENDING.parent.mkdir(parents=True, exist_ok=True)
    PENDING.write_text(json.dumps({"old": old, "new": new, "conflicts": conflicts,
                                   "applied": applied}, indent=2) + "\n")


def complete_snapshot(rev: str) -> int:
    if not PENDING.is_file():
        raise SystemExit("no pending snapshot to complete")
    pending = json.loads(PENDING.read_text())
    new = git("rev-parse", f"{rev}^{{commit}}")
    old = STATE.read_text().split()[0]
    if new != pending["new"] or old != pending["old"]:
        raise SystemExit("snapshot/state does not match the pending synchronization")
    if not pending.get("applied"):
        raise SystemExit("synchronization was interrupted; retry the exact snapshot with --retry-incomplete")
    unresolved = []
    for rel in pending["conflicts"]:
        data = read_local(rel)
        if data is not None and any(line.startswith(CONFLICT_MARKERS) for line in data.splitlines()):
            unresolved.append(rel)
    if unresolved:
        raise SystemExit("unresolved conflict markers:\n  " + "\n  ".join(unresolved))
    if run_lint() != 0:
        return 1
    STATE.write_text(new + "\n")
    PENDING.unlink()
    print(f"completed snapshot {new}; binary/delete/add collisions explicitly accepted")
    print("next: compile and run the required release checks; completion is not release validation")
    return 0


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--main", default=str(ROOT.parent / "StardewCraft"))
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--snapshot", help="use an existing snapshot commit instead of taking a new one")
    ap.add_argument("--complete-snapshot", help="acknowledge resolved conflicts and advance STATE")
    ap.add_argument("--retry-incomplete", action="store_true",
                    help="retry --snapshot after an interrupted application, not unresolved conflicts")
    args = ap.parse_args(argv)
    if args.complete_snapshot:
        if args.dry_run or args.snapshot or args.retry_incomplete:
            ap.error("--complete-snapshot cannot be combined with sync options")
        return complete_snapshot(args.complete_snapshot)
    old = STATE.read_text().split()[0]
    if PENDING.exists() and not args.dry_run:
        pending = json.loads(PENDING.read_text())
        retry = args.retry_incomplete and args.snapshot and not pending.get("applied")
        if not retry or old != pending["old"] or git("rev-parse", f"{args.snapshot}^{{commit}}") != pending["new"]:
            raise SystemExit("resolve the pending snapshot, then use --complete-snapshot with its exact SHA")
    elif args.retry_incomplete:
        ap.error("--retry-incomplete requires a pending interrupted synchronization")
    main_dir = Path(args.main).resolve()
    new = git("rev-parse", f"{args.snapshot}^{{commit}}") if args.snapshot else snapshot(main_dir, old, args.dry_run)
    if git("rev-parse", f"{old}^{{tree}}") == git("rev-parse", f"{new}^{{tree}}"):
        print("main unchanged since last sync")
        return 0
    check_conversion_inputs()
    (ROOT / "build").mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="port-sync-", dir=ROOT / "build") as tmp:
        workdir = Path(tmp)
        previous, incoming = workdir / "previous", workdir / "incoming"
        prefixes = [RESOURCE_PREFIX, *MIRRORED_PATHS]
        export_tree(old, prefixes, previous)
        export_tree(new, prefixes, incoming)
        previous_resources, incoming_resources = previous / RESOURCE_PREFIX, incoming / RESOURCE_PREFIX
        convert_resources(previous_resources)
        convert_resources(incoming_resources)
        conflicts = prepare_resources(previous_resources, incoming_resources)
        java_changes, touched, java_conflicts = plan_java(old, new, workdir)
        mirror_changes, mirror_conflicts = plan_mirrors(previous, incoming)
        conflicts += java_conflicts + mirror_conflicts
        print(f"snapshot {new}: {len(touched)} java paths changed")
        print(f"mirrored build-input files changed: {len(mirror_changes)}")
        for line in touched:
            print("  ", line)
        if not args.dry_run:
            # Mark incomplete before mutation so an IO failure/crash cannot advance STATE.
            save_pending(old, new, conflicts, applied=False)
            replace_resources(incoming_resources, workdir / "original-resources")
            for rel, data in {**java_changes, **mirror_changes}.items():
                set_file(local_path(rel), data)
            save_pending(old, new, conflicts, applied=True)
    if conflicts:
        print("CONFLICTS (STATE was not advanced; resolve/review, then --complete-snapshot):")
        for path in conflicts:
            print("  ", path)
    lint_status = 0 if args.dry_run else run_lint()
    if conflicts or lint_status:
        return 1
    if not args.dry_run:
        STATE.write_text(new + "\n")
        PENDING.unlink()
        print("next: compile and run the required release checks")
    return 0


if __name__ == "__main__":
    sys.exit(main())

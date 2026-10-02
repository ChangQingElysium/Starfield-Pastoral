"""Snapshot synchronization safety, using only temporary tracked Git fixtures.

No Gradle, local Minecraft facts, production resources, or live saves are needed.
The fixture converter uses the actual pure JSON/directory conversion functions;
NBT/runtime tooling is tested through its staging/input contract, not local caches.
"""
import contextlib
import importlib.util
import io
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
from types import SimpleNamespace
import unittest
from unittest import mock


SCRIPTS = Path(__file__).resolve().parents[1]


def load_script(name):
    spec = importlib.util.spec_from_file_location("test_" + name, SCRIPTS / (name + ".py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class SyncFromMainTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / "port"
        self.root.mkdir()
        self.git("init", "-q")
        self.git("config", "user.name", "Sync fixture")
        self.git("config", "user.email", "sync-fixture@example.invalid")
        self.put(".gitignore", "build/\nscripts/port/main-sync-state.txt\n")
        self.put("src/main/resources/content.txt", "old\n")
        self.put("src/main/resources/pack.mcmeta", "forge-pack\n")
        self.put("src/main/resources/META-INF/mods.toml", "forge-mods\n")
        self.put("src/main/resources/META-INF/accesstransformer.cfg", "forge-at\n")
        self.put("src/main/resources/stardewcraft.mixins.json", self.mixins())
        self.put("src/main/java/Example.java", "class Example {}\n")
        self.put("assets-src/npc/source.txt", "source\n")
        self.old = self.commit()
        self.sync = load_script("sync_from_main")
        self.sync.ROOT = self.root
        self.sync.STATE = self.root / "scripts/port/main-sync-state.txt"
        self.sync.PENDING = self.root / "build/port-sync-pending.json"
        self.sync.SCRIPTS = []
        self.sync.MIRRORED_PATHS = ["assets-src/npc"]
        self.put("scripts/port/main-sync-state.txt", self.old + "\n")
        self.stack = contextlib.ExitStack()
        self.addCleanup(self.stack.close)
        self.inputs = self.stack.enter_context(mock.patch.object(self.sync, "check_conversion_inputs"))
        self.lint = self.stack.enter_context(mock.patch.object(self.sync, "run_lint", return_value=0))
        self.conversion = self.stack.enter_context(mock.patch.object(
            self.sync, "convert_resources", side_effect=self.convert_json_fixture))

    def git(self, *args):
        result = subprocess.run(["git", *args], cwd=self.root, capture_output=True, check=True)
        return result.stdout.decode().strip()

    def put(self, rel, data):
        path = self.root / rel
        if data is None:
            path.unlink()
            return
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data.encode() if isinstance(data, str) else data)

    def commit(self):
        self.git("add", "-A")
        self.git("commit", "-q", "-m", "tracked fixture")
        return self.git("rev-parse", "HEAD")

    def advance(self, changes):
        for rel, data in changes.items():
            self.put(rel, data)
        new = self.commit()
        self.git("checkout", "-q", "--detach", self.old)
        return new

    def call(self, *args):
        with contextlib.redirect_stdout(io.StringIO()):
            return self.sync.main(list(args))

    def content(self, rel):
        return (self.root / rel).read_bytes()

    def convert_json_fixture(self, resources):
        conversion = load_script("convert_resources_1201")
        conversion.ROOT = self.root
        conversion.DATA = resources / "data"
        conversion.DATA.mkdir(parents=True, exist_ok=True)
        conversion.MODELS = resources / "assets/stardewcraft/models"
        conversion.rename_dirs()
        conversion.rewrite_json(sorted(conversion.DATA.glob("*/recipes/**/*.json")),
                                conversion.convert_recipe)
        conversion.convert_models()

    @staticmethod
    def mixins(main="old", port="none"):
        # Separated edits can merge without modifying either loader's additions.
        data = {"main": main, **{f"anchor{i}": "fixed" for i in range(10)}, "port": port}
        return json.dumps(data, indent=2) + "\n"

    def test_resources_are_exported_from_snapshot_not_live_main(self):
        new = self.advance({"src/main/resources/content.txt": "snapshot\n"})
        live = Path(self.temp.name) / "live-main"
        live.mkdir()
        (live / "content.txt").write_text("changed after snapshot\n")
        self.assertEqual(self.call("--snapshot", new, "--main", str(live)), 0)
        self.assertEqual(self.content("src/main/resources/content.txt"), b"snapshot\n")
        self.assertEqual(self.sync.STATE.read_text().strip(), new)
        self.assertFalse(self.sync.PENDING.exists())
        self.assertEqual(self.conversion.call_count, 2)

    def test_platform_files_mixins_and_port_only_resources_survive(self):
        new = self.advance({
            "src/main/resources/content.txt": "incoming\n",
            "src/main/resources/pack.mcmeta": "neo-pack\n",
            "src/main/resources/META-INF/mods.toml": "incoming-mods\n",
            "src/main/resources/META-INF/accesstransformer.cfg": "incoming-at\n",
            "src/main/resources/stardewcraft.mixins.json": self.mixins(main="new"),
        })
        self.put("src/main/resources/stardewcraft.mixins.json", self.mixins(port="forge"))
        self.put("src/main/resources/port-only.txt", "only in Forge\n")
        self.assertEqual(self.call("--snapshot", new), 0)
        self.assertEqual(self.content("src/main/resources/pack.mcmeta"), b"forge-pack\n")
        self.assertEqual(self.content("src/main/resources/META-INF/mods.toml"), b"forge-mods\n")
        self.assertEqual(self.content("src/main/resources/META-INF/accesstransformer.cfg"), b"forge-at\n")
        self.assertEqual(self.content("src/main/resources/port-only.txt"), b"only in Forge\n")
        merged = json.loads(self.content("src/main/resources/stardewcraft.mixins.json"))
        self.assertEqual((merged["main"], merged["port"]), ("new", "forge"))

    def test_conversion_failure_leaves_production_and_state_untouched(self):
        new = self.advance({"src/main/resources/content.txt": "incoming\n",
                            "src/main/java/Example.java": "class Incoming {}\n"})
        self.conversion.side_effect = [None, RuntimeError("conversion failed")]
        with self.assertRaisesRegex(RuntimeError, "conversion failed"):
            self.call("--snapshot", new)
        self.assertEqual(self.content("src/main/resources/content.txt"), b"old\n")
        self.assertEqual(self.content("src/main/java/Example.java"), b"class Example {}\n")
        self.assertEqual(self.sync.STATE.read_text().strip(), self.old)
        self.assertFalse(self.sync.PENDING.exists())

    def test_java_rewrite_failure_also_precedes_production_mutation(self):
        new = self.advance({"src/main/resources/content.txt": "incoming\n",
                            "src/main/java/Example.java": "class Incoming {}\n"})
        with mock.patch.object(self.sync, "scripted", side_effect=RuntimeError("rewrite failed")):
            with self.assertRaisesRegex(RuntimeError, "rewrite failed"):
                self.call("--snapshot", new)
        self.assertEqual(self.content("src/main/resources/content.txt"), b"old\n")
        self.assertEqual(self.sync.STATE.read_text().strip(), self.old)
        self.assertFalse(self.sync.PENDING.exists())

    def test_converted_resource_paths_and_model_metadata_are_merge_base(self):
        model_rel = "src/main/resources/assets/stardewcraft/models/block/native.json"
        recipe_rel = "src/main/resources/data/stardewcraft/recipe/test.json"
        old_model = {"elements": [{"neoforge_data": {"color": "ff123456", "sky_light": 9}}]}
        self.put(model_rel, json.dumps(old_model, indent=2) + "\n")
        self.put(recipe_rel, '{"type":"minecraft:crafting_shapeless","result":{"id":"stardewcraft:test"}}\n')
        self.old = self.commit()
        self.put("scripts/port/main-sync-state.txt", self.old + "\n")
        new_model = {**old_model, "display": {"fixed": {"rotation": [0, 90, 0]}}}
        new = self.advance({model_rel: json.dumps(new_model, indent=2) + "\n"})
        self.convert_json_fixture(self.root / "src/main/resources")
        self.assertEqual(self.call("--snapshot", new), 0)
        model = json.loads(self.content(model_rel))
        self.assertEqual(model["elements"][0]["forge_data"], {"color": "ff123456", "sky_light": 9})
        self.assertNotIn("neoforge_data", model["elements"][0])
        self.assertEqual(model["display"], new_model["display"])
        self.assertFalse((self.root / recipe_rel).exists())
        recipe = json.loads(self.content(recipe_rel.replace("/recipe/", "/recipes/")))
        self.assertEqual(recipe["result"], {"item": "stardewcraft:test"})

    def test_new_resource_and_java_collisions_preserve_port_files(self):
        resource = "src/main/resources/new.txt"
        java = "src/main/java/New.java"
        new = self.advance({resource: "incoming\n", java: "class Incoming {}\n"})
        self.put(resource, "port-only\n")
        self.put(java, "class PortOnly {}\n")
        self.assertEqual(self.call("--snapshot", new), 1)
        self.assertEqual(self.content(resource), b"port-only\n")
        self.assertEqual(self.content(java), b"class PortOnly {}\n")
        self.assertEqual(self.sync.STATE.read_text().strip(), self.old)
        pending = json.loads(self.sync.PENDING.read_text())
        self.assertEqual(set(pending["conflicts"]), {resource, java})
        self.assertTrue(pending["applied"])

    def test_binary_modify_conflict_preserves_ours_and_requires_acknowledgement(self):
        rel = "src/main/resources/pregen/test.mca"
        self.put(rel, b"\0base")
        self.old = self.commit()
        self.put("scripts/port/main-sync-state.txt", self.old + "\n")
        new = self.advance({rel: b"\0theirs"})
        self.put(rel, b"\0ours")
        self.assertEqual(self.call("--snapshot", new), 1)
        self.assertEqual(self.content(rel), b"\0ours")
        self.assertEqual(self.sync.STATE.read_text().strip(), self.old)
        self.assertEqual(self.call("--complete-snapshot", new), 0)
        self.assertEqual(self.sync.STATE.read_text().strip(), new)

    def test_delete_only_removes_unchanged_source_owned_files(self):
        unchanged = "src/main/resources/unchanged.txt"
        adapted = "src/main/resources/adapted.txt"
        locally_deleted = "src/main/resources/locally-deleted.txt"
        for rel in (unchanged, adapted, locally_deleted):
            self.put(rel, "base\n")
        self.old = self.commit()
        self.put("scripts/port/main-sync-state.txt", self.old + "\n")
        new = self.advance({unchanged: None, adapted: None,
                            "src/main/resources/content.txt": "new\n"})
        self.put(adapted, "hand-adapted\n")
        self.put(locally_deleted, None)
        self.assertEqual(self.call("--snapshot", new), 1)
        self.assertFalse((self.root / unchanged).exists())
        self.assertEqual(self.content(adapted), b"hand-adapted\n")
        self.assertFalse((self.root / locally_deleted).exists())
        self.assertEqual(json.loads(self.sync.PENDING.read_text())["conflicts"], [adapted])

    def test_mirror_preserves_unowned_files_and_reports_source_collisions(self):
        edited = "assets-src/npc/edited.txt"
        self.put(edited, "base\n")
        self.old = self.commit()
        self.put("scripts/port/main-sync-state.txt", self.old + "\n")
        new = self.advance({"assets-src/npc/source.txt": None, edited: None,
                            "assets-src/npc/new.txt": "incoming\n"})
        self.put(edited, "port-adapted\n")
        self.put("assets-src/npc/new.txt", "port-only\n")
        self.put("assets-src/npc/local-authoring.txt", "unowned local\n")
        self.assertEqual(self.call("--snapshot", new), 1)
        self.assertFalse((self.root / "assets-src/npc/source.txt").exists())
        self.assertEqual(self.content(edited), b"port-adapted\n")
        self.assertEqual(self.content("assets-src/npc/new.txt"), b"port-only\n")
        self.assertEqual(self.content("assets-src/npc/local-authoring.txt"), b"unowned local\n")
        self.assertEqual(set(json.loads(self.sync.PENDING.read_text())["conflicts"]),
                         {edited, "assets-src/npc/new.txt"})

    def test_dry_run_without_snapshot_creates_no_commit_ref_index_or_state_change(self):
        live = Path(self.temp.name) / "live-main"
        shutil.copytree(self.root, live, ignore=shutil.ignore_patterns(".git", "build"))
        (live / "src/main/resources/content.txt").write_text("snapshot content\n")
        collision = "src/main/resources/collision.txt"
        (live / collision).write_text("incoming\n")
        self.put(collision, "ours\n")
        before = {key: self.git(*command) for key, command in {
            "head": ("rev-parse", "HEAD"), "refs": ("for-each-ref",),
            "index": ("write-tree",), "commits": ("rev-list", "--all", "--count"),
        }.items()}
        self.assertEqual(self.call("--dry-run", "--main", str(live)), 1)
        after = {key: self.git(*command) for key, command in {
            "head": ("rev-parse", "HEAD"), "refs": ("for-each-ref",),
            "index": ("write-tree",), "commits": ("rev-list", "--all", "--count"),
        }.items()}
        self.assertEqual(before, after)
        self.assertEqual(self.content(collision), b"ours\n")
        self.assertEqual(self.content("src/main/resources/content.txt"), b"old\n")
        self.assertEqual(self.sync.STATE.read_text().strip(), self.old)
        self.assertFalse(self.sync.PENDING.exists())
        self.lint.assert_not_called()

    def test_text_conflict_completion_requires_exact_snapshot_markers_and_lint(self):
        rel = "src/main/resources/content.txt"
        new = self.advance({rel: "theirs\n"})
        self.put(rel, "ours\n")
        self.assertEqual(self.call("--snapshot", new), 1)
        self.assertIn(b"<<<<<<< port", self.content(rel))
        with self.assertRaisesRegex(SystemExit, "does not match"):
            self.call("--complete-snapshot", self.old)
        with self.assertRaisesRegex(SystemExit, "unresolved conflict markers"):
            self.call("--complete-snapshot", new)
        with self.assertRaisesRegex(SystemExit, "pending snapshot"):
            self.call("--snapshot", new)
        self.put(rel, "resolved\n")
        self.lint.return_value = 1
        self.assertEqual(self.call("--complete-snapshot", new), 1)
        self.assertEqual(self.sync.STATE.read_text().strip(), self.old)
        self.lint.return_value = 0
        self.assertEqual(self.call("--complete-snapshot", new), 0)
        self.assertEqual(self.sync.STATE.read_text().strip(), new)
        self.assertFalse(self.sync.PENDING.exists())

    def test_lint_failure_does_not_advance_state(self):
        new = self.advance({"src/main/resources/content.txt": "incoming\n"})
        self.lint.return_value = 1
        self.assertEqual(self.call("--snapshot", new), 1)
        self.assertEqual(self.sync.STATE.read_text().strip(), self.old)
        self.assertTrue(self.sync.PENDING.exists())
        self.lint.return_value = 0
        self.assertEqual(self.call("--complete-snapshot", new), 0)

    def test_interrupted_application_can_only_retry_the_same_snapshot(self):
        new = self.advance({"src/main/resources/content.txt": "incoming\n"})
        with mock.patch.object(self.sync, "replace_resources", side_effect=OSError("IO failure")):
            with self.assertRaisesRegex(OSError, "IO failure"):
                self.call("--snapshot", new)
        self.assertFalse(json.loads(self.sync.PENDING.read_text())["applied"])
        self.assertEqual(self.sync.STATE.read_text().strip(), self.old)
        with self.assertRaisesRegex(SystemExit, "interrupted"):
            self.call("--complete-snapshot", new)
        with self.assertRaisesRegex(SystemExit, "pending snapshot"):
            self.call("--snapshot", self.old, "--retry-incomplete")
        self.assertEqual(self.call("--snapshot", new, "--retry-incomplete"), 0)
        self.assertEqual(self.sync.STATE.read_text().strip(), new)

    def test_snapshot_symlink_is_rejected_before_any_production_change(self):
        rel = "src/main/resources/link.txt"
        target = self.root / rel
        target.symlink_to("content.txt")
        new = self.commit()
        self.git("checkout", "-q", "--detach", self.old)
        with self.assertRaisesRegex(SystemExit, "unsupported snapshot archive entry"):
            self.call("--snapshot", new)
        self.assertEqual(self.content("src/main/resources/content.txt"), b"old\n")
        self.assertEqual(self.sync.STATE.read_text().strip(), self.old)
        self.assertFalse(self.sync.PENDING.exists())

    def test_local_symlink_or_directory_collision_is_not_followed(self):
        new = self.advance({"src/main/java/Example.java": "class Incoming {}\n"})
        target = self.root / "src/main/java/Example.java"
        target.unlink()
        target.symlink_to(self.root / "src/main/resources/content.txt")
        with self.assertRaisesRegex(SystemExit, "symlink"):
            self.call("--snapshot", new)
        self.assertEqual(self.content("src/main/resources/content.txt"), b"old\n")
        target.unlink()
        target.mkdir()
        with self.assertRaisesRegex(SystemExit, "collides with a directory"):
            self.call("--snapshot", new)
        self.assertEqual(self.sync.STATE.read_text().strip(), self.old)

    def test_missing_converter_inputs_fail_with_clear_prerequisite(self):
        # Get the unmocked helper from a new module; it only inspects this fixture.
        actual = load_script("sync_from_main")
        actual.ROOT = self.root
        with self.assertRaisesRegex(SystemExit, "resource conversion inputs missing"):
            actual.check_conversion_inputs()

    def test_existing_converter_resource_globals_are_redirected_only_to_staging(self):
        actual = load_script("sync_from_main")
        actual.ROOT = self.root
        resources = self.root / "build/staging/src/main/resources"
        conversion = SimpleNamespace(ROOT=self.root, main=mock.Mock(return_value=0))
        nbt = SimpleNamespace(ROOT=self.root, main=mock.Mock(return_value=0))
        original_argv = list(actual.sys.argv)
        with mock.patch.object(actual, "load_conversion", side_effect=[conversion, nbt]):
            actual.convert_resources(resources)
        self.assertEqual(conversion.DATA, resources / "data")
        self.assertEqual(conversion.MODELS, resources / "assets/stardewcraft/models")
        self.assertEqual(nbt.RES, resources)
        self.assertEqual((conversion.ROOT, nbt.ROOT), (self.root, self.root))
        conversion.main.assert_called_once_with()
        nbt.main.assert_called_once_with(["convert"])
        self.assertEqual(actual.sys.argv, original_argv)

    def test_converter_argument_state_is_restored_on_failure(self):
        actual = load_script("sync_from_main")
        actual.ROOT = self.root
        conversion = SimpleNamespace(main=mock.Mock(side_effect=RuntimeError("converter failure")))
        original_argv = actual.sys.argv
        with mock.patch.object(actual, "load_conversion", return_value=conversion):
            with self.assertRaisesRegex(RuntimeError, "converter failure"):
                actual.convert_resources(self.root / "build/staging/src/main/resources")
        self.assertIs(actual.sys.argv, original_argv)

    def test_failed_resource_directory_switch_restores_original_tree(self):
        workdir = self.root / "build/swap"
        workdir.mkdir(parents=True)
        with self.assertRaises(FileNotFoundError):
            self.sync.replace_resources(workdir / "missing-incoming", workdir / "backup")
        self.assertEqual(self.content("src/main/resources/content.txt"), b"old\n")
        self.assertFalse((workdir / "backup").exists())

    def test_sync_java_keeps_disjoint_manual_adaptations(self):
        rel = "src/main/java/Example.java"
        base = "old-main\n" + "fixed-anchor\n" * 10 + "old-port\n"
        self.put(rel, base)
        self.old = self.commit()
        self.put("scripts/port/main-sync-state.txt", self.old + "\n")
        new = self.advance({rel: base.replace("old-main", "new-main")})
        self.put(rel, base.replace("old-port", "forge-port"))
        self.assertEqual(self.call("--snapshot", new), 0)
        self.assertEqual(self.content(rel), base.replace("old-main", "new-main")
                         .replace("old-port", "forge-port").encode())

    def test_multiple_text_conflict_hunks_are_valid_merge_conflicts(self):
        base = b"base-top\n" + b"anchor\n" * 20 + b"base-bottom\n"
        ours = base.replace(b"base-", b"ours-")
        theirs = base.replace(b"base-", b"theirs-")
        merged, conflict = self.sync.merge_bytes(base, ours, theirs)
        self.assertTrue(conflict)
        self.assertEqual(merged.count(b"<<<<<<< port"), 2)


if __name__ == "__main__":
    unittest.main()

"""Fabric inventory checks use temporary fixtures, never a local game or save."""

import importlib.util
import json
from pathlib import Path
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / "scripts/port/fabric_inventory.py"
SPEC = importlib.util.spec_from_file_location("fabric_inventory", SCRIPT)
inventory = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(inventory)


class FabricInventoryTests(unittest.TestCase):
    def write(self, root, path, content):
        destination = root / path
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(content, encoding="utf-8")
        return destination

    def fixture(self, root):
        self.write(root, "build.gradle", "plugins { id 'java-library' }\n")
        self.write(root, "src/main/java/example/Example.java", """
package example;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
// player.getPersistentData(); net.neoforged.fml.ModList
class Example implements CustomPacketPayload {
  String fake = "net.neoforged.fake.Fake player.getPersistentData()";
  @SubscribeEvent
  public static void tick(PlayerTickEvent.Post event) {
    event.getEntity().getPersistentData();
    net.neoforged.fml.ModList.get();
  }
}
""")
        self.write(root, "src/main/java/example/Plain.java", "package example; class Plain {}\n")
        self.write(root, "src/main/resources/assets/example/models/one.json",
                   '\ufeff{"loader":"neoforge:composite","children":{}}')
        self.write(root, "src/main/resources/example.mixins.json",
                   '{"package":"example.mixin","mixins":["Common"],"client":["Client"]}')
        self.write(root, "src/generated/resources/data/example/generated.json", "{}")

    def test_counts_real_code_and_flags_import_free_patches(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root)
            report = inventory.build_inventory(root)
            java = report["java"]
            self.assertEqual(2, java["files"])
            self.assertEqual(1, java["direct_import_files"])
            self.assertEqual(2, java["import_occurrences"])
            self.assertEqual(1, java["event_handlers"]["count"])
            self.assertEqual({"PlayerTickEvent.Post": 1}, java["event_handlers"]["argument_types"])
            self.assertEqual(1, java["custom_packet_payload_declarations"])
            patches = java["hidden_patch_candidates"]["getPersistentData"]
            self.assertEqual(1, patches["occurrences"])
            self.assertEqual([10], patches["files"][0]["lines"])
            self.assertEqual(1, java["categories"]["fml"]["count"])
            self.assertEqual(3, report["resources"]["files"])
            self.assertEqual(1, report["resources"]["model_loaders"]["neoforge:composite"]["count"])
            self.assertEqual([], report["resources"]["invalid_json"])
            self.assertEqual(["src/main/resources/assets/example/models/one.json"],
                             report["resources"]["json_with_utf8_bom"])
            self.assertEqual(["Client"], report["resources"]["mixin_configurations"][0]["client"])
            self.assertEqual("not_evaluated", report["runtime_equivalence"])

    def test_hashes_are_portable_deterministic_and_detect_each_drift_kind(self):
        with tempfile.TemporaryDirectory() as directory, tempfile.TemporaryDirectory() as second:
            root, other = Path(directory), Path(second)
            self.fixture(root)
            self.fixture(other)
            before = inventory.build_inventory(root)
            self.assertEqual(before, inventory.build_inventory(other))
            self.write(root, "src/main/java/example/Example.java", "package example; class Example {}")
            (root / "src/main/java/example/Plain.java").unlink()
            self.write(root, "src/main/resources/added.txt", "new resource")
            after = inventory.build_inventory(root)
            drift = inventory.compare_entries(before["baseline"]["entries"], after["baseline"]["entries"])
            self.assertEqual(["src/main/resources/added.txt"], drift["added"])
            self.assertEqual(["src/main/java/example/Plain.java"], drift["removed"])
            self.assertEqual(["src/main/java/example/Example.java"], drift["modified"])
            self.assertNotEqual(before["baseline"]["tree_sha256"], after["baseline"]["tree_sha256"])

    def test_conversion_coverage_never_claims_runtime_equivalence(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root)
            converted = root / "fabric/build/generated/sources/port"
            self.write(converted, "example/Example.java", "package example; class Example {}")
            self.write(converted, "adapter/Platform.java", "import net.neoforged.fml.ModList; class Platform {}")
            report = inventory.conversion_inventory(inventory.build_inventory(root), converted)
            self.assertEqual(1, report["present_java_files"])
            self.assertEqual(["example/Plain.java"], report["missing_java_files"])
            self.assertEqual(["adapter/Platform.java"], report["additional_java_files"])
            self.assertEqual("adapter/Platform.java", report["residual_neoforge_files"][0]["path"])
            self.assertEqual("not_evaluated", report["runtime_equivalence"])

    def test_cli_writes_report_and_compares_baseline(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root)
            baseline, output = root / "baseline.json", root / "reports/inventory.json"
            self.assertEqual(0, inventory.main(["--root", str(root), "--output", str(baseline)]))
            self.write(root, "gradle.properties", "minecraft_version=1.21.1\n")
            self.assertEqual(0, inventory.main(["--root", str(root), "--baseline", str(baseline), "--output", str(output)]))
            report = json.loads(output.read_text())
            self.assertEqual(["gradle.properties"], report["drift"]["added"])
            self.assertEqual([], report["drift"]["modified"])

    def test_external_source_symlink_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory, tempfile.TemporaryDirectory() as external:
            root = Path(directory)
            self.fixture(root)
            outside = self.write(Path(external), "Outside.java", "class Outside {}")
            (root / "src/main/java/example/Outside.java").symlink_to(outside)
            with self.assertRaisesRegex(ValueError, "outside --root"):
                inventory.build_inventory(root)

    def test_native_model_sources_and_shared_compilers_participate_in_fingerprints(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root)
            self.write(root, "assets-src/npc/models.json", '{"sam":"sam"}')
            model = self.write(root, "assets-src/npc/sam/sam.bbmodel", "model source")
            self.write(root, "assets-src/npc/sam/motion.json", "motion source")
            self.write(root, "assets-src/npc/sam/sam_sit.motion.json", "activity motion")
            self.write(root, "assets-src/npc/sam/preview.gif", "local preview")
            self.write(root, "assets-src/npc/sam/review.json", "local review")
            self.write(root, "tools/art/compile_native_npc.py", "shared compiler")
            self.write(root, "assets-src/furniture/sebastian_computer/sebastian_computer.bbmodel", "furniture source")
            before = inventory.build_inventory(root)
            paths = {entry["path"] for entry in before["native_model_inputs"]["entries"]}
            self.assertIn("assets-src/npc/sam/sam.bbmodel", paths)
            self.assertIn("assets-src/npc/sam/sam_sit.motion.json", paths)
            self.assertIn("assets-src/npc/sam/motion.json", paths)
            self.assertIn("tools/art/compile_native_npc.py", paths)
            self.assertIn("assets-src/furniture/sebastian_computer/sebastian_computer.bbmodel", paths)
            self.assertNotIn("assets-src/npc/sam/preview.gif", paths)
            self.assertNotIn("assets-src/npc/sam/review.json", paths)

            self.write(root, "assets-src/npc/sam/preview.gif", "changed preview")
            self.assertEqual(before["baseline"]["tree_sha256"],
                             inventory.build_inventory(root)["baseline"]["tree_sha256"])
            model.write_text("updated maintained model", encoding="utf-8")
            after = inventory.build_inventory(root)
            self.assertNotEqual(before["native_model_inputs"]["tree_sha256"],
                                after["native_model_inputs"]["tree_sha256"])
            self.assertEqual(["assets-src/npc/sam/sam.bbmodel"],
                             inventory.compare_entries(before["baseline"]["entries"],
                                                       after["baseline"]["entries"])["modified"])


if __name__ == "__main__":
    unittest.main()

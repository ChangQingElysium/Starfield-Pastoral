"""Optional integrations must be discoverable without becoming base entrypoints."""

import copy
import json
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
METADATA = ROOT / "fabric/src/main/resources/fabric.mod.json"
INTEGRATIONS = ROOT / "src/main/java/com/stardew/craft/integration"
PLUGINS = {"jade": ("WailaPlugin", "IWailaPlugin"),
           "jei_mod_plugin": ("JeiPlugin", "IModPlugin")}


def production_plugins(annotation, interface):
    result = set()
    for path in INTEGRATIONS.rglob("*.java"):
        source = path.read_text(encoding="utf-8")
        if not re.search(r"^\s*@" + annotation + r"\b", source, re.MULTILINE):
            continue
        package = re.search(r"\bpackage\s+([\w.]+);", source)
        declaration = re.search(r"\bpublic\s+(?:final\s+)?class\s+(\w+)\s+implements\s+"
                                + interface + r"\b", source)
        if not package or not declaration:
            raise AssertionError("Unsupported plugin declaration: " + str(path.relative_to(ROOT)))
        result.add(package[1] + "." + declaration[1])
    return result


class OptionalPluginDiscoveryTests(unittest.TestCase):
    def setUp(self):
        self.metadata = json.loads(METADATA.read_text(encoding="utf-8"))

    def assert_plugin_closure(self, metadata):
        for key, (annotation, interface) in PLUGINS.items():
            expected = production_plugins(annotation, interface)
            self.assertTrue(expected, "No production plugins found for " + key)
            actual = metadata["entrypoints"].get(key, [])
            self.assertEqual(len(actual), len(set(actual)), "Duplicate " + key + " entrypoint")
            self.assertEqual(expected, set(actual), "Missing or extra " + key + " entrypoints")

    def test_all_current_production_plugins_are_declared_once_and_only_once(self):
        self.assert_plugin_closure(self.metadata)

    def test_optional_plugins_are_absent_from_all_base_entrypoints_and_hard_dependencies(self):
        plugins = set().union(*(production_plugins(*spec) for spec in PLUGINS.values()))
        for key, entries in self.metadata["entrypoints"].items():
            if key not in PLUGINS:
                self.assertFalse(plugins.intersection(entries), key + " eagerly exposes an optional plugin")
        for mod in ("jade", "jei"):
            self.assertNotIn(mod, self.metadata.get("depends", {}))
            self.assertIn(mod, self.metadata["suggests"])
        for key in ("main", "client", "fabric-gametest"):
            self.assertTrue(self.metadata["entrypoints"].get(key), "Lost original " + key + " entrypoint")

    def test_closure_rejects_a_missing_plugin_an_extra_class_and_a_duplicate(self):
        for mutation in ("missing", "extra", "duplicate"):
            with self.subTest(mutation=mutation):
                metadata = copy.deepcopy(self.metadata)
                entries = metadata["entrypoints"]["jade"]
                if mutation == "missing":
                    entries.pop()
                elif mutation == "extra":
                    entries.append("example.NotAProductionPlugin")
                else:
                    entries.append(entries[0])
                with self.assertRaises(AssertionError):
                    self.assert_plugin_closure(metadata)


if __name__ == "__main__":
    unittest.main()

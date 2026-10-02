"""Content parity gates run with tracked Python sources/data, without Minecraft or decompiler caches."""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "compare_parity.py"
spec = importlib.util.spec_from_file_location("port_compare", SCRIPT)
compare = importlib.util.module_from_spec(spec)
spec.loader.exec_module(compare)


def sealed(facts):
    result = copy.deepcopy(facts)
    result.pop("sha256", None)
    result["sha256"] = hashlib.sha256(compare.canonical_json(result).encode("utf-8")).hexdigest()
    return result


def synthetic_facts():
    """Small synthetic registry for unit cases only; production facts must be runtime-exported."""
    return sealed({
        "schema": 1,
        "minecraft_version": "1.20.1",
        "port_attribute_ids": ["minecraft:generic.scale", "minecraft:generic.step_height"],
        "registries": {
            "minecraft:item": ["minecraft:air", "minecraft:grass"],
            "minecraft:block": ["minecraft:air", "minecraft:grass"],
            "minecraft:entity_type": ["minecraft:pig"],
            "minecraft:fluid": ["minecraft:water"],
            "minecraft:attribute": ["minecraft:generic.movement_speed"],
            "minecraft:enchantment": ["minecraft:unbreaking"],
        },
    })


def dump(section, key, value):
    return {(section, key): compare.canonical_json(value)}


class RegistryFactsTest(unittest.TestCase):
    def read_facts(self, facts):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "facts.json"
            path.write_text(json.dumps(facts), encoding="utf-8")
            return compare.load_registry_facts(path)

    def test_runtime_schema_and_canonical_digest(self):
        facts = synthetic_facts()
        self.assertEqual(facts, self.read_facts(facts))

    def test_changed_payload_without_digest_is_rejected(self):
        facts = synthetic_facts()
        facts["registries"]["minecraft:item"].append("minecraft:stone")
        with self.assertRaisesRegex(ValueError, "sha256"):
            self.read_facts(facts)

    def test_wrong_version_schema_missing_registry_and_port_identity_are_rejected(self):
        cases = []
        facts = synthetic_facts()
        facts["minecraft_version"] = "1.21.1"
        cases.append(facts)
        facts = synthetic_facts()
        facts["schema"] = 2
        cases.append(facts)
        facts = synthetic_facts()
        del facts["registries"]["minecraft:fluid"]
        cases.append(facts)
        facts = synthetic_facts()
        facts["port_attribute_ids"].append("minecraft:generic.luck")
        cases.append(facts)
        for facts in cases:
            with self.subTest(facts=facts), self.assertRaises(ValueError):
                self.read_facts(sealed(facts))

    def test_incomplete_nonvanilla_duplicate_unsorted_and_port_polluted_ids_are_rejected(self):
        for ids in ([], ["minecraft:air", "minecraft:air"], ["minecraft:grass", "minecraft:air"],
                    ["stardewcraft:stone"], ["minecraft:BadName"]):
            facts = synthetic_facts()
            facts["registries"]["minecraft:item"] = ids
            with self.subTest(ids=ids), self.assertRaises(ValueError):
                self.read_facts(sealed(facts))
        facts = synthetic_facts()
        facts["registries"]["minecraft:attribute"].append("minecraft:generic.scale")
        facts["registries"]["minecraft:attribute"].sort()
        with self.assertRaisesRegex(ValueError, "labelled vanilla"):
            self.read_facts(sealed(facts))

    def test_default_tracked_facts_are_valid(self):
        facts = compare.load_registry_facts()
        self.assertEqual("1.20.1", facts["minecraft_version"])
        self.assertGreater(len(facts["registries"]["minecraft:item"]), 1000)


class NarrowParityTest(unittest.TestCase):
    def setUp(self):
        self.apply = compare.normalizer(synthetic_facts())

    def changes(self, base, port, *, content_only=False, rules=()):
        base = compare.normalize_dump(base, self.apply)
        port = compare.normalize_dump(port, self.apply, is_port=True)
        return compare.differences(base, port, rules, content_only=content_only)

    def test_exact_port_only_registry_ids_are_normalized(self):
        for key, allowed in compare.PORT_REGISTRY_IDS.items():
            for section in ("registry", "dynamic_registry"):
                base = dump(section, key, ["stardewcraft:existing"])
                port = dump(section, key, sorted({"stardewcraft:existing"} | allowed))
                with self.subTest(section=section, key=key):
                    self.assertFalse(self.changes(base, port))

    def test_unrelated_registry_addition_or_removal_cannot_use_whole_key_approval(self):
        for key in ("minecraft:attribute", "minecraft:particle_type", "minecraft:block_entity_type",
                    "neoforge:attachment_types", "neoforge:fluid_type"):
            for section in ("registry", "dynamic_registry"):
                base = dump(section, key, ["stardewcraft:existing"])
                for port in (dump(section, key, ["stardewcraft:existing", "stardewcraft:unexpected"]), {}):
                    with self.subTest(section=section, key=key, port=port):
                        self.assertTrue(self.changes(base, port, rules=[(section, key)]))

    def test_only_extra_port_ids_do_not_hide_missing_real_id(self):
        base = dump("registry", "minecraft:particle_type", ["stardewcraft:forest_leaf"])
        port = dump("registry", "minecraft:particle_type", ["stardewcraft:port_entity_effect"])
        self.assertTrue(self.changes(base, port))

    def test_fluid_loader_key_alias_preserves_registry_ids(self):
        base = dump("registry", "neoforge:fluid_type", ["stardewcraft:fish_pond_water"])
        port = dump("registry", "forge:fluid_type", ["stardewcraft:fish_pond_water"])
        self.assertFalse(self.changes(base, port))
        port = dump("registry", "forge:fluid_type", ["stardewcraft:unexpected"])
        self.assertTrue(self.changes(base, port))

    def test_enchantment_views_merge_only_after_strict_id_agreement(self):
        ids = ["stardewcraft:artful", "stardewcraft:master"]
        base = dump("dynamic_registry", "minecraft:enchantment", ids)
        port = dict(base, **{})
        port.update(dump("registry", "minecraft:enchantment", ids))
        self.assertFalse(self.changes(base, port))
        port = dump("dynamic_registry", "minecraft:enchantment", ids[:1])
        port.update(dump("registry", "minecraft:enchantment", ids[:1]))
        self.assertTrue(self.changes(base, port, rules=[("registry", "minecraft:enchantment")]))
        port.update(dump("registry", "minecraft:enchantment", ids))
        with self.assertRaisesRegex(ValueError, "enchantment registry ids disagree"):
            self.changes(base, port)

    def test_registry_duplicate_ids_and_conflicting_loader_views_fail_closed(self):
        bad = dump("registry", "minecraft:item", ["stardewcraft:one", "stardewcraft:one"])
        with self.assertRaisesRegex(ValueError, "invalid registry"):
            self.changes({}, bad)
        bad = dump("registry", "forge:fluid_type", ["stardewcraft:one"])
        bad.update(dump("registry", "neoforge:fluid_type", ["stardewcraft:two"]))
        with self.assertRaisesRegex(ValueError, "conflicting registry"):
            self.changes({}, bad)

    def test_content_only_is_explicit_and_does_not_suppress_content_changes(self):
        base = dump("gametest", "one", True)
        port = dump("gametest", "two", True)
        self.assertTrue(self.changes(base, port))
        self.assertFalse(self.changes(base, port, content_only=True))
        port.update(dump("block", "stardewcraft:keg", {"friction": 123}))
        self.assertTrue(self.changes(base, port, content_only=True))

    def test_tag_facts_are_registry_specific_and_short_grass_rename_is_supported(self):
        base = dump("tag/item", "stardewcraft:one", ["minecraft:pig", "minecraft:short_grass"])
        port = dump("tag/item", "stardewcraft:one", ["minecraft:grass"])
        self.assertFalse(self.changes(base, port))
        self.assertEqual(["minecraft:pig"], json.loads(self.apply("tag/entity_type", "one", '["minecraft:pig"]')))

    def test_unavailable_vanilla_tag_members_are_ignored_only_on_baseline(self):
        base = dump("tag/item", "stardewcraft:one", ["minecraft:grass", "minecraft:future_item"])
        port = dump("tag/item", "stardewcraft:one", ["minecraft:grass"])
        self.assertFalse(self.changes(base, port))
        port = dump("tag/item", "stardewcraft:one", ["minecraft:grass", "minecraft:unexpected"])
        self.assertTrue(self.changes(base, port))
        port = dump("tag/item", "stardewcraft:one", ["minecraft:grass", "minecraft:future_item"])
        self.assertTrue(self.changes(base, port))

    def test_loader_aliases_and_only_exact_version_defaults_are_normalized(self):
        baseline_attributes = dict(compare.VERSION_DEFAULT_ATTRIBUTES)
        baseline_attributes.pop("forge:step_height_addition")
        baseline_attributes["neoforge:swim_speed"] = 1.0
        baseline_attributes["neoforge:nametag_distance"] = 64.0
        port_attributes = {"forge:entity_gravity": 0.08, "forge:step_height_addition": 0.0,
                           "forge:swim_speed": 1.0, "forge:nametag_distance": 64.0}
        base = dump("entity", "stardewcraft:one", {"attributes": baseline_attributes})
        port = dump("entity", "stardewcraft:one", {"attributes": port_attributes})
        self.assertFalse(self.changes(base, port))
        for name, value in (("forge:unexpected", 1), ("minecraft:generic.scale", 2),
                            ("minecraft:generic.burning_time", 2), ("forge:swim_speed", 2)):
            changed = dict(port_attributes)
            changed[name] = value
            with self.subTest(name=name):
                self.assertTrue(self.changes(base, dump("entity", "stardewcraft:one", {"attributes": changed})))

    def test_dump_duplicate_keys_fail_instead_of_overwriting(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "duplicate.jsonl"
            path.write_text("gametest\tone\ttrue\ngametest\tone\tfalse\n", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "duplicate dump key"):
                compare.load(path)

    def test_empty_dump_cannot_report_zero_differences(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "empty.jsonl"
            path.write_text("", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "empty content dump"):
                compare.load(path)

    def test_cli_works_outside_repository_without_ignored_build_sources(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            facts = directory / "facts.json"
            facts.write_text(json.dumps(synthetic_facts()), encoding="utf-8")
            base, port = directory / "baseline.jsonl", directory / "port.jsonl"
            base.write_text("gametest\tone\ttrue\n", encoding="utf-8")
            port.write_text("gametest\ttwo\ttrue\n", encoding="utf-8")
            args = [sys.executable, "-B", str(SCRIPT), str(base), str(port), "--facts", str(facts),
                    "--approved", str(directory / "no-approvals.tsv")]
            strict = subprocess.run(args, cwd=directory, capture_output=True, text=True, check=False)
            self.assertEqual(1, strict.returncode, strict.stdout + strict.stderr)
            content = subprocess.run(args + ["--content-only"], cwd=directory, capture_output=True, text=True, check=False)
            self.assertEqual(0, content.returncode, content.stdout + content.stderr)
            self.assertIn("test coverage must be verified separately", content.stdout)


if __name__ == "__main__":
    unittest.main()

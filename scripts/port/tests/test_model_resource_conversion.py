"""Model conversion preserves geometry and resolves maintained production dependencies."""
import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SCRIPT = Path(__file__).resolve().parents[1] / "convert_resources_1201.py"
SPEC = importlib.util.spec_from_file_location("convert_resources_1201", SCRIPT)
CONVERSION = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CONVERSION)


class ModelResourceConversionTest(unittest.TestCase):
    def test_element_and_face_data_are_lossless_and_idempotent(self):
        element_data = {"color": "ffa1b2c3", "sky_light": 9, "ambient_occlusion": False}
        face_data = {"block_light": 15, "ambient_occlusion": False}
        model = {"elements": [{"neoforge_data": element_data,
                               "faces": {"north": {"tintindex": 3, "neoforge_data": face_data}}}]}
        converted = CONVERSION.convert_model_metadata(copy.deepcopy(model))
        element = converted["elements"][0]
        self.assertEqual(element["forge_data"], element_data)
        self.assertEqual(element["faces"]["north"]["forge_data"], face_data)
        self.assertEqual(element["faces"]["north"]["tintindex"], 3)
        self.assertNotIn("neoforge_data", element)
        self.assertEqual(CONVERSION.convert_model_metadata(copy.deepcopy(converted)), converted)

    def test_imported_loader_keeps_neoforge_spelling(self):
        model = {"loader": "stardewcraft:geometry", "parts": [{"neoforge_data": {"block_light": 15}}]}
        self.assertEqual(CONVERSION.convert_model_metadata(copy.deepcopy(model)), model)

    def test_ambiguous_metadata_fails_instead_of_overwriting(self):
        model = {"elements": [{"neoforge_data": {"color": "ff00ff00"},
                                "forge_data": {"color": "ffff0000"}}]}
        with self.assertRaisesRegex(ValueError, "both neoforge_data and forge_data"):
            CONVERSION.convert_model_metadata(model)

    def test_composite_preserves_geometry_order_and_custom_loader_input(self):
        model = {
            "loader": "neoforge:composite", "parent": "minecraft:block/block",
            "textures": {"particle": "stardewcraft:block/ginger_island/stove_fireplace_particle"},
            "stardewcraft:collision": [{"from": [1, 0, 0], "to": [31, 66, 16]}],
            "item_render_order": ["native", "imported", "nested"],
            "children": {
                "native": {"transform": {"translation": [1.25, 0, -2]},
                           "elements": [{"from": [0, 0, 0], "to": [16, 16, 16],
                                         "faces": {"north": {"texture": "#0", "uv": [0, 0, 16, 16],
                                                               "neoforge_data": {"block_light": 15}}}}]},
                "imported": {"loader": "stardewcraft:geometry",
                             "parts": [{"neoforge_data": {"block_light": 15}}]},
                "nested": {"loader": "neoforge:composite", "children": {
                    "base": {"parent": "minecraft:block/cube_all", "transform": {"scale": [1, 2, 1]}}}},
            },
        }
        expected = copy.deepcopy(model)
        expected["loader"] = "forge:composite"
        expected["children"]["nested"]["loader"] = "forge:composite"
        face = expected["children"]["native"]["elements"][0]["faces"]["north"]
        face["forge_data"] = face.pop("neoforge_data")
        converted = CONVERSION.convert_model_metadata(copy.deepcopy(model))
        self.assertEqual(converted, expected)
        self.assertEqual(list(converted["children"]), list(model["children"]))
        self.assertEqual(CONVERSION.convert_model_metadata(copy.deepcopy(converted)), converted)

    def test_unknown_neo_loader_is_rejected_including_composite_children(self):
        unknown = {"loader": "neoforge:unsupported", "elements": []}
        for model in (unknown, {"loader": "neoforge:composite", "children": {"part": unknown}}):
            with self.subTest(model=model), self.assertRaisesRegex(ValueError, "unmapped NeoForge model loader"):
                CONVERSION.convert_model_metadata(copy.deepcopy(model))

    def test_composite_loader_requires_forge_compatible_children_and_order(self):
        for model in ({"loader": "neoforge:composite", "children": {}},
                      {"loader": "neoforge:composite", "children": {"part": "minecraft:block/stone"}},
                      {"loader": "neoforge:composite", "children": {"part": {}}, "item_render_order": ["missing"]}):
            with self.subTest(model=model), self.assertRaisesRegex(ValueError, "composite"):
                CONVERSION.convert_model_metadata(copy.deepcopy(model))

    def test_composite_without_metadata_is_converted_on_disk(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = root / "model.json"
            model = {"loader": "neoforge:composite", "children": {
                "part": {"parent": "minecraft:block/stone", "transform": {"translation": [0, 2, 0]}}}}
            path.write_text(json.dumps(model), encoding="utf-8")
            with patch.object(CONVERSION, "MODELS", root), patch.object(CONVERSION, "ROOT", root):
                self.assertEqual(CONVERSION.convert_models(), 1)
                self.assertEqual(CONVERSION.convert_models(), 0)
            expected = copy.deepcopy(model)
            expected["loader"] = "forge:composite"
            self.assertEqual(json.loads(path.read_text(encoding="utf-8")), expected)

    def test_registered_ginger_models_have_loadable_geometry_and_dependencies(self):
        resources = SCRIPT.parents[2] / "src/main/resources"
        catalog = json.loads((resources / "data/stardewcraft/ginger_island/assets.json").read_text(encoding="utf-8"))
        seen = set()
        supported = {"forge:composite", "stardewcraft:crop_geometry", "stardewcraft:geometry"}

        def visit_model(model_id):
            namespace, name = model_id.split(":", 1) if ":" in model_id else ("minecraft", model_id)
            if namespace == "minecraft" or model_id in seen:
                return
            seen.add(model_id)
            path = resources / "assets" / namespace / "models" / (name + ".json")
            self.assertTrue(path.is_file(), f"Missing model dependency: {model_id}")
            visit_json(json.loads(path.read_text(encoding="utf-8-sig")))

        def visit_json(value):
            if isinstance(value, dict):
                if "loader" in value:
                    self.assertIn(value["loader"], supported, f"Unregistered Forge geometry loader: {value['loader']}")
                    if value["loader"] == "forge:composite":
                        # Validate the same children/order contract used by Forge's CompositeModel.Loader.
                        self.assertEqual(CONVERSION.convert_model_metadata(copy.deepcopy(value)), value)
                if isinstance(value.get("parent"), str):
                    visit_model(value["parent"])
                if isinstance(value.get("model"), str):
                    visit_model(value["model"])
                for texture in value.get("textures", {}).values():
                    if not isinstance(texture, str) or texture.startswith("#"):
                        continue
                    namespace, name = texture.split(":", 1) if ":" in texture else ("minecraft", texture)
                    if namespace != "minecraft":
                        path = resources / "assets" / namespace / "textures" / (name + ".png")
                        self.assertTrue(path.is_file(), f"Missing texture dependency: {texture}")
                for child in value.values():
                    visit_json(child)
            elif isinstance(value, list):
                for child in value:
                    visit_json(child)

        for asset in catalog["blocks"]:
            visit_model(asset["model"])
            visit_model("stardewcraft:item/" + asset["id"])
            for model_id in asset.get("state_models", {}).values():
                visit_model(model_id)
            blockstate = resources / "assets/stardewcraft/blockstates" / (asset["id"] + ".json")
            self.assertTrue(blockstate.is_file(), f"Missing blockstate: {asset['id']}")
            visit_json(json.loads(blockstate.read_text(encoding="utf-8")))
        self.assertGreater(len(seen), len(catalog["blocks"]))


if __name__ == "__main__":
    unittest.main()

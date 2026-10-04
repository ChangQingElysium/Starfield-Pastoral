"""Importer contracts use generated fixtures, never private artist source files."""
import base64
import copy
import importlib.util
import unittest
from pathlib import Path

_spec = importlib.util.spec_from_file_location('block_model_import', Path(__file__).resolve().parents[1] / 'scripts/import_block_model.py')
importer = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(importer)


def fixture(fmt='java_block'):
    return {'meta': {'model_format': fmt}, 'resolution': {'width': 32, 'height': 32},
            'textures': [{'source': 'data:image/png;base64,' + base64.b64encode(b'\x89PNG\r\n\x1a\n').decode()}],
            'elements': [{'uuid': 'cube', 'from': [0, 0, 0], 'to': [16, 8, 4], 'faces': {'north': {'texture': 0, 'uv': [0, 0, 16, 8]}}}],
            'outliner': ['cube']}


def mesh_fixture():
    project = fixture('free')
    project['elements'] = [{'type': 'mesh', 'uuid': 'mesh', 'origin': [8, 0, 8],
        'vertices': {'a': [-4, 0, -2], 'b': [-4, 8, -2], 'c': [4, 8, -2], 'd': [4, 0, -2]},
        'faces': {'front': {'texture': 0, 'vertices': ['a', 'b', 'c', 'd'],
            'uv': {'a': [0, 8], 'b': [0, 0], 'c': [8, 0], 'd': [8, 8]}}}}]
    project['outliner'] = ['mesh']
    return project


class ModelImportTests(unittest.TestCase):
    def test_only_named_light_is_emissive_in_both_formats(self):
        for fmt in ('java_block', 'free'):
            project = fixture(fmt)
            project['elements'][0]['name'] = 'pole'
            project['elements'].append({**copy.deepcopy(project['elements'][0]), 'uuid': 'lamp', 'name': 'light'})
            project['outliner'].append('lamp')
            model, _ = importer.import_bbmodel(project, 'stardewcraft:block/test', ['light'])
            pole, lamp = model.get('elements', model.get('parts'))
            metadata = 'forge_data' if fmt == 'java_block' else 'neoforge_data'
            other_metadata = 'neoforge_data' if fmt == 'java_block' else 'forge_data'
            self.assertNotIn(metadata, pole)
            self.assertNotIn('shade', pole)
            self.assertEqual({'block_light': 15, 'sky_light': 15, 'ambient_occlusion': False}, lamp[metadata])
            self.assertNotIn(other_metadata, lamp)
            self.assertFalse(lamp['shade'])
            with self.assertRaisesRegex(ValueError, 'not exported'):
                importer.import_bbmodel(project, 'stardewcraft:block/test', ['missing'])

    def test_native_inverted_outline_shell_preserves_signed_inflate(self):
        project = fixture()
        project['elements'][0].update({'from': [10, 12, 10], 'to': [6, 8, 6], 'inflate': -0.25})
        model, _ = importer.import_bbmodel(project, 'stardewcraft:block/test')
        self.assertEqual([10.25, 12.25, 10.25], model['elements'][0]['from'])
        self.assertEqual([5.75, 7.75, 5.75], model['elements'][0]['to'])

    def test_java_uv_uses_project_canvas_for_smaller_texture(self):
        project = fixture()
        project['textures'][0].update({'uv_width': 16, 'uv_height': 16})
        model, _ = importer.import_bbmodel(project, 'stardewcraft:block/test')
        self.assertEqual([0, 0, 8, 4], model['elements'][0]['faces']['north']['uv'])

    def test_java_preserves_geometry_and_scales_uv(self):
        model, files = importer.import_bbmodel(fixture(), 'stardewcraft:block/test')
        self.assertNotIn('loader', model)
        self.assertEqual([0, 0, 8, 4], model['elements'][0]['faces']['north']['uv'])
        self.assertEqual([16, 8, 4], model['elements'][0]['to'])
        self.assertEqual(1, len(files))

    def test_generic_nested_pivot_rotation(self):
        project = fixture('free')
        project['groups'] = [{'uuid': 'group', 'origin': [0, 0, 0], 'rotation': [0, 90, 0]}]
        project['outliner'] = [{'uuid': 'group', 'children': ['cube']}]
        model, _ = importer.import_bbmodel(project, 'stardewcraft:block/test')
        self.assertEqual('stardewcraft:geometry', model['loader'])
        transform = model['parts'][0]['transform']
        self.assertAlmostEqual(-1, transform[2])
        self.assertAlmostEqual(1, transform[8])

    def test_unsupported_mesh_fails_before_output(self):
        project = fixture('free')
        project['elements'][0]['type'] = 'mesh'
        with self.assertRaisesRegex(ValueError, 'mesh'):
            importer.import_bbmodel(project, 'stardewcraft:block/test')

    def test_mesh_requires_explicit_opt_in_and_keeps_vertices_uvs_and_winding(self):
        project = mesh_fixture()
        with self.assertRaisesRegex(ValueError, 'mesh'):
            importer.import_bbmodel(project, 'stardewcraft:block/test')
        model, _ = importer.import_bbmodel(project, 'stardewcraft:block/test', allow_mesh=True)
        self.assertEqual('stardewcraft:geometry', model['loader'])
        self.assertEqual([], model['parts'])
        self.assertEqual([4, 0, 6, 0, .25, 4, 8, 6, 0, 0,
                          12, 8, 6, .25, 0, 12, 0, 6, .25, .25], model['quads'][0]['vertices'])
        self.assertEqual('#0', model['quads'][0]['texture'])
        project['meta']['model_format'] = 'java_block'
        with self.assertRaisesRegex(ValueError, 'mesh'):
            importer.import_bbmodel(project, 'stardewcraft:block/test', allow_mesh=True)

    def test_authorized_mesh_rejects_invalid_faces_without_silent_conversion(self):
        for defect in ('triangle', 'missing_uv', 'out_of_bounds', 'nonfinite', 'degenerate'):
            project = mesh_fixture()
            mesh = project['elements'][0]
            if defect == 'triangle': mesh['faces']['front']['vertices'].pop()
            elif defect == 'missing_uv': del mesh['faces']['front']['uv']['d']
            elif defect == 'out_of_bounds': mesh['faces']['front']['uv']['a'] = [33, 0]
            elif defect == 'nonfinite': mesh['vertices']['a'][0] = float('nan')
            elif defect == 'degenerate': mesh['vertices']['c'] = [-4, 9, -2]
            with self.subTest(defect=defect), self.assertRaises((ValueError, KeyError)):
                importer.import_bbmodel(project, 'stardewcraft:block/test', allow_mesh=True)

    def test_authorized_mesh_retains_parent_rotation(self):
        project = mesh_fixture()
        project['groups'] = [{'uuid': 'group', 'origin': [0, 0, 0], 'rotation': [0, 90, 0]}]
        project['outliner'] = [{'uuid': 'group', 'children': ['mesh']}]
        model, _ = importer.import_bbmodel(project, 'stardewcraft:block/test', allow_mesh=True)
        first = model['quads'][0]['vertices'][:3]
        self.assertAlmostEqual(6, first[0])
        self.assertAlmostEqual(0, first[1])
        self.assertAlmostEqual(-4, first[2])

    def test_group_export_switch_is_resolved_from_blockbench_5_groups(self):
        project = fixture()
        project['elements'].append({**copy.deepcopy(project['elements'][0]), 'uuid': 'hidden'})
        project['groups'] = [{'uuid': 'group', 'export': False}]
        project['outliner'] = ['cube', {'uuid': 'group', 'children': ['hidden']}]
        model, _ = importer.import_bbmodel(project, 'stardewcraft:block/test')
        self.assertEqual(1, len(model['elements']))

    def test_native_multi_axis_rotation_is_never_silently_discarded(self):
        project = fixture()
        project['elements'][0]['rotation'] = [22.5, 45, 0]
        with self.assertRaisesRegex(ValueError, 'Rotation'):
            importer.import_bbmodel(project, 'stardewcraft:block/test')

    def test_display_slots_survive_import(self):
        project = fixture()
        project['display'] = {'gui': {'scale': [0.25, 0.25, 0.25]}}
        model, _ = importer.import_bbmodel(project, 'stardewcraft:block/test')
        self.assertEqual(project['display'], model['display'])

    def test_geo_centers_mirrored_x_and_resolves_parent_rotation(self):
        geometry = {'minecraft:geometry': [{'description': {'texture_width': 16, 'texture_height': 16}, 'bones': [
            {'name': 'parent', 'pivot': [0, 0, 0], 'rotation': [0, -90, 0]},
            {'name': 'child', 'parent': 'parent', 'cubes': [{'origin': [-8, 0, -8], 'size': [16, 16, 16], 'uv': [0, 0]}]}]}]}
        model, _ = importer.import_geo(geometry, 'stardewcraft:block/test')
        part = model['parts'][0]
        self.assertEqual([-8, 0, -8], part['from'])
        self.assertEqual([8, 0, 8], part['transform'][12:15])
        self.assertEqual(6, len(part['faces']))

    def test_resource_ids_cannot_escape_assets_directory(self):
        for name in ['stardewcraft:../../file', 'bad namespace:model', '/absolute/path']:
            with self.assertRaises(ValueError):
                importer.resource(name)


if __name__ == '__main__':
    unittest.main()

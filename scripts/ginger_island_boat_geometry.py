#!/usr/bin/env python3
"""Reviewed Oct 7 boat geometry corrections, with unchanged painted PNGs.

These are positive cuboid/plane edits. No negative renderer scale or UV atlas
repaint is used. The source annotations also let the ordinary export recover
Willy's canonical named collision profile, then reflect the coarse regions.
"""
import copy
import hashlib
import json
import math
import uuid
from pathlib import Path

from import_block_model import rotate

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'assets-src/ginger_island'
TAG = 'stardewcraft:boat_geometry'
REVISION = '2026-10-07'


def mirror_faces(faces, axis):
    """Same face/UV convention as Blockbench Cube.flip, without name changes."""
    opposite = (('west', 'east'), ('up', 'down'), ('south', 'north'))[axis]
    faces[opposite[0]], faces[opposite[1]] = faces[opposite[1]], faces[opposite[0]]
    for side, face in faces.items():
        if 'uv' not in face:
            continue
        horizontal = axis != 1 if side not in ('up', 'down') else axis == 0
        if face.get('rotation', 0) % 180 == 90:
            horizontal = not horizontal
        a = 0 if horizontal else 1
        face['uv'][a], face['uv'][a + 2] = face['uv'][a + 2], face['uv'][a]


def mirror_element(element, axis, pivot):
    a, b = element['from'][axis], element['to'][axis]
    element['from'][axis], element['to'][axis] = round(2 * pivot - b, 10), round(2 * pivot - a, 10)
    if 'origin' in element:
        element['origin'][axis] = round(2 * pivot - element['origin'][axis], 10)
    angles = element.get('rotation')
    if angles:
        for other in range(3):
            if other != axis:
                angles[other] *= -1
    mirror_faces(element['faces'], axis)


def mirror_group(group, axis, pivot):
    group['origin'][axis] = 2 * pivot - group['origin'][axis]
    for other in range(3):
        if other != axis:
            group['rotation'][other] *= -1


def mirror_willy(project):
    result = copy.deepcopy(project)
    for e in result['elements']:
        mirror_element(e, 0, 8)
    for g in result.get('groups', []):
        mirror_group(g, 0, 8)
    return result


def willy_collision_source(project):
    """Return the validated named pre-reflection source and reflection flag."""
    reflected = project.get(TAG, {}).get('willy_reflect_x_about') == 8
    return (mirror_willy(project) if reflected else project), reflected


def willy_collision_boxes(boxes, reflected):
    if not reflected:
        return boxes
    result = copy.deepcopy(boxes)
    for box in result:
        box['from'][0], box['to'][0] = 16 - box['to'][0], 16 - box['from'][0]
    return result


def shipwreck_entry_part(project, threshold):
    """A player corridor across the source aperture, before its group transform.

    Its positive bounds must follow the stern's local Z reflection, just like
    the named threshold. The old local opening would carve the opposite side.
    """
    mirrored = project.get(TAG, {}).get('stern_reflect_z_about') == 0
    return {**threshold, 'from': [-160, 8, 24.5 if mirrored else -49.5],
            'to': [-90, 47, 49.5 if mirrored else -24.5]}


def point(matrix, p):
    return [sum(matrix[i][j] * p[j] for j in range(3)) + matrix[i][3] for i in range(3)]


def group_node(project, name):
    groups = {g['uuid']: g for g in project['groups']}
    def walk(nodes):
        for n in nodes:
            if isinstance(n, dict):
                if groups[n['uuid']]['name'] == name:
                    return n
                found = walk(n.get('children', []))
                if found:
                    return found
    return walk(project['outliner'])


def subtree_ids(node):
    result = []
    for n in node['children']:
        if isinstance(n, dict):
            result.append(n['uuid'])
            result.extend(subtree_ids(n))
        else:
            result.append(n)
    return result


def new_group(name, origin, rotation):
    return {'name': name, 'uuid': str(uuid.uuid5(uuid.NAMESPACE_URL, 'stardewcraft/shipwreck/' + name)),
            'origin': origin, 'rotation': rotation, 'export': True, 'visibility': True,
            'isOpen': False, 'shade': True, 'children': []}


def correct_shipwreck(project):
    result = copy.deepcopy(project)
    if TAG in result:
        return result
    root = group_node(result, 'shipwreck')
    stern_node = group_node(result, 'stern_hull')
    bow_node = group_node(result, 'broken_bow')
    groups = {g['uuid']: g for g in result['groups']}
    elements = {e['uuid']: e for e in result['elements']}
    stern = groups[stern_node['uuid']]
    bow = groups[bow_node['uuid']]
    if stern['origin'] != [-78, 5, 0] or stern['rotation'] != [-14, 0, -18] \
            or bow['origin'] != [85, 0, 16] or bow['rotation'] != [0, -24, -6]:
        raise ValueError('Shipwreck authoring frame changed; review section interfaces again')

    # The broken mast stays inclined across the split, rather than turning with
    # the entire stern. Only move its root to the corresponding new deck point.
    rigging_prefixes = ('fallen_mast', 'mast_binding', 'cross_yard', 'yard_broken_tip',
                        'rigging_rope', 'rigging_rung', 'frayed_rope', 'yard_algae')
    rigging_ids = [n for n in stern_node['children'] if isinstance(n, str)
                   and elements[n]['name'].startswith(rigging_prefixes)]
    if len(rigging_ids) != 55:
        raise ValueError('Shipwreck rigging membership changed; verify mast support')
    old_base = point(rotate(stern['origin'], stern['rotation']), [-68, 45, 6])
    reflected_base = [old_base[0], old_base[1], -old_base[2]]
    new_base = point(rotate(stern['origin'], [0, -90, 0]), reflected_base)
    delta = [new_base[i] - old_base[i] for i in range(3)]
    rigging = new_group('supported_broken_rigging',
                        [stern['origin'][i] + delta[i] for i in range(3)], stern['rotation'][:])
    for element_id in rigging_ids:
        for field in ('from', 'to', 'origin'):
            elements[element_id][field] = [elements[element_id][field][i] + delta[i] for i in range(3)]
    stern_node['children'] = [n for n in stern_node['children'] if n not in rigging_ids]
    result['groups'].append(rigging)
    root['children'].append({'uuid': rigging['uuid'], 'isOpen': False, 'children': rigging_ids})

    # Reflect the stern longitudinally before its quarter turn. This keeps the
    # captain-room aperture on the shore/NORTH side after its cut faces EAST.
    for child_id in subtree_ids(stern_node):
        if child_id in elements:
            mirror_element(elements[child_id], 2, 0)
        else:
            mirror_group(groups[child_id], 2, 0)
    mirror_group(stern, 2, 0)
    for name, node, origin in [('stern_section_interface', stern_node, stern['origin']),
                                ('bow_section_interface', bow_node, bow['origin'])]:
        axis = new_group(name, origin[:], [0, -90, 0])
        result['groups'].append(axis)
        root['children'][root['children'].index(node)] = {
            'uuid': axis['uuid'], 'isOpen': False, 'children': [node]}
    result[TAG] = {'revision': REVISION, 'stern_reflect_z_about': 0,
                   'stern_yaw': -90, 'bow_yaw': -90,
                   'expected_section_outward_normals': {'stern': '+X', 'bow': '-X'},
                   'captain_entry_shore_side': '-Z', 'rigging_base_translation_pixels': delta,
                   'painted_png_unchanged': True}
    return result


def revise_sources():
    paths = [SOURCE / '01_willy_boat' / (name + '.bbmodel')
             for name in ('willy_boat_damaged', 'willy_boat_repaired')]
    paths.append(SOURCE / '06_west_quests/shipwreck/island_shipwreck.bbmodel')
    backup = ROOT / 'tmp/ginger-boats-oct7/reference-before'
    backup.mkdir(parents=True, exist_ok=True)
    for path in paths:
        original = path.read_bytes()
        project = json.loads(original)
        if TAG in project:
            continue
        (backup / path.name).write_bytes(original)
        if path.name.startswith('willy'):
            revised = mirror_willy(project)
            revised[TAG] = {'revision': REVISION, 'willy_reflect_x_about': 8,
                            'bow_direction_retained': '+Z', 'boarding_side': '+X',
                            'painted_png_unchanged': True}
        else:
            revised = correct_shipwreck(project)
        revised[TAG]['reference_before_sha256'] = hashlib.sha256(original).hexdigest()
        path.write_text(json.dumps(revised, ensure_ascii=False, indent=2) + '\n')
        print(path.relative_to(ROOT))


if __name__ == '__main__':
    revise_sources()

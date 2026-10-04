#!/usr/bin/env python3
"""Read shipped item geometry and estimate its actual 16px GUI projection.

Standard library only. No collision profiles, source art, local research reports,
Minecraft cache JARs or installed client are required. Transparent UV gutters
make this a conservative geometric bound, not a rasterized screenshot.

Vanilla parent defaults below were verified against Minecraft 1.21.1 block/block
and item/generated and ItemTransform.apply. Unknown external parents fail loudly.
"""
from __future__ import annotations

import argparse
from functools import lru_cache
import itertools
import json
import math
from pathlib import Path
from typing import Iterable

DEFAULT_RESOURCES = Path(__file__).resolve().parents[1] / "src/main/resources"
EPSILON = 0.01
VANILLA = {
    "minecraft:block/block": {
        "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]}}
    },
    "minecraft:item/generated": {"generated": True},
    "minecraft:builtin/generated": {"generated": True},
}


def _rotation(point, axis, degrees):
    c, s = math.cos(math.radians(degrees)), math.sin(math.radians(degrees))
    x, y, z = point
    if axis == "x":
        return x, c * y - s * z, s * y + c * z
    if axis == "y":
        return c * x + s * z, y, -s * x + c * z
    return c * x - s * y, s * x + c * y, z


def _xyz(point, angles):
    # Quaternion.rotationXYZ is Rx * Ry * Rz for column vectors.
    for axis, angle in reversed(list(zip("xyz", angles))):
        point = _rotation(point, axis, angle)
    return point


def _part_transform(point, part):
    rotation = part.get("rotation")
    if rotation:
        origin = rotation.get("origin", [8, 8, 8])
        point = tuple(point[i] - origin[i] for i in range(3))
        point = _rotation(point, rotation["axis"], rotation["angle"])
        if rotation.get("rescale"):
            factor = 1 / math.cos(math.radians(rotation["angle"]))
            point = tuple(point[i] * (1 if "xyz"[i] == rotation["axis"] else factor) for i in range(3))
        point = tuple(point[i] + origin[i] for i in range(3))
    transform = part.get("transform")
    if isinstance(transform, list):
        if len(transform) != 16:
            raise ValueError("Imported parts require 16 column-major pixel-space values")
        point = tuple(sum(transform[j * 4 + i] * point[j] for j in range(3)) + transform[12 + i] for i in range(3))
    return point


def _root_transform(point, transform):
    if not transform:
        return point
    if "matrix" in transform:
        matrix = transform["matrix"]
        if len(matrix) != 3 or any(len(row) != 4 for row in matrix):
            raise ValueError("Composite matrix requires three rows of four values")
        return tuple(sum(matrix[i][j] * point[j] for j in range(3)) + 16 * matrix[i][3] for i in range(3))
    if set(transform) - {"translation", "scale"}:
        raise ValueError(f"Unsupported root transform: {transform}")
    scale, translation = transform.get("scale", [1, 1, 1]), transform.get("translation", [0, 0, 0])
    return tuple(point[i] * scale[i] + 16 * translation[i] for i in range(3))


def _face_vertices(element):
    low, high = element["from"], element["to"]
    points = []
    for face in element.get("faces", {}):
        axis = {"east": 0, "west": 0, "up": 1, "down": 1, "north": 2, "south": 2}[face]
        other = [i for i in range(3) if i != axis]
        for a, b in itertools.product((0, 1), repeat=2):
            selectors = [0, 0, 0]
            selectors[axis] = int(face in {"east", "up", "south"})
            selectors[other[0]], selectors[other[1]] = a, b
            points.append(_part_transform(tuple((high if selectors[i] else low)[i] for i in range(3)), element))
    return points


def _project(points, gui):
    rotation, right = gui.get("rotation", [0, 0, 0]), gui.get("right_rotation", [0, 0, 0])
    scale, translation = gui.get("scale", [1, 1, 1]), gui.get("translation", [0, 0, 0])
    out = []
    for point in points:
        point = _xyz(tuple(point[i] - 8 for i in range(3)), right)
        point = _xyz(tuple(point[i] * scale[i] for i in range(3)), rotation)
        out.append((8 + point[0] + translation[0], 8 - point[1] - translation[1]))
    return out


class ItemBoundsReader:
    def __init__(self, resource_root: Path | str = DEFAULT_RESOURCES):
        self.resources = Path(resource_root)

    @lru_cache(None)
    def raw(self, model_id):
        if ":" not in model_id:
            model_id = "minecraft:" + model_id
        namespace, path = model_id.split(":", 1)
        file = self.resources / "assets" / namespace / "models" / (path + ".json")
        if file.is_file():
            return json.loads(file.read_text(encoding="utf-8"))
        if model_id in VANILLA:
            return VANILLA[model_id]
        raise ValueError(f"Missing or unsupported model parent: {model_id}")

    @lru_cache(None)
    def inherited(self, model_id):
        model = self.raw(model_id)
        parent = self.inherited(model["parent"]) if model.get("parent") else {}
        out = {**parent, **model}
        out["display"] = {**parent.get("display", {}), **model.get("display", {})}
        return out

    def vertices(self, model):
        points = []
        for part in model.get("parts", model.get("elements", [])):
            points.extend(_face_vertices(part))
        for quad in model.get("quads", []):
            values = quad["vertices"]
            points.extend(tuple(values[i + j] for j in range(3)) for i in range(0, len(values), 5))
        children = model.get("children", {})
        for name in model.get("item_render_order", list(children)):
            child = children[name]
            base = self.inherited(child["parent"]) if child.get("parent") else {}
            points.extend(self.vertices({**base, **child}))
        return [_root_transform(point, model.get("transform")) for point in points]

    def model(self, model_id):
        model = self.inherited(model_id)
        gui = model.get("display", {}).get("gui", {})
        generated = bool(model.get("generated"))
        points = [(x, y, z) for x, y, z in itertools.product((0, 16), (0, 16), (7.5, 8.5))] if generated else self.vertices(model)
        if not points:
            raise ValueError(f"Model has no declared visible faces: {model_id}")
        screen = _project(points, gui)
        bounds = [min(p[0] for p in screen), min(p[1] for p in screen), max(p[0] for p in screen), max(p[1] for p in screen)]
        excess = max(0, -min(bounds[:2]), max(bounds[2:]) - 16)
        return {
            "model": model_id,
            "mode": "generated" if generated else "3d",
            "gui": gui,
            "vertex_count": len(points),
            "geometry_bounds": [[round(min(p[i] for p in points), 4), round(max(p[i] for p in points), 4)] for i in range(3)],
            "bounds": [round(v, 4) for v in bounds],
            "span": [round(bounds[2] - bounds[0], 4), round(bounds[3] - bounds[1], 4)],
            "outside": excess > EPSILON,
            "outside_by": round(excess, 4),
        }

    def item(self, item_id):
        model_id = "stardewcraft:item/" + item_id
        states = [self.model(model_id)]
        for override in self.inherited(model_id).get("overrides", []):
            state = self.model(override["model"])
            state["predicate"] = override["predicate"]
            states.append(state)
        return {"id": item_id, "outside": any(state["outside"] for state in states), "variants": states}


def audit_assets(resource_root: Path | str = DEFAULT_RESOURCES):
    """Return all shipped Ginger Island owner results, including item overrides."""
    reader = ItemBoundsReader(resource_root)
    assets = json.loads((reader.resources / "data/stardewcraft/ginger_island/assets.json").read_text(encoding="utf-8"))["blocks"]
    return [reader.item(asset["id"]) for asset in assets]


def find_overflow_ids(resource_root: Path | str = DEFAULT_RESOURCES, exclude_ids: Iterable[str] = ()):
    """Pending approved icon owners may be excluded by a caller during migration."""
    excluded = set(exclude_ids)
    return [asset["id"] for asset in audit_assets(resource_root) if asset["outside"] and asset["id"] not in excluded]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--resources", type=Path, default=DEFAULT_RESOURCES)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    rows = audit_assets(args.resources)
    report = {"owners": len(rows), "overflow_ids": [row["id"] for row in rows if row["outside"]], "assets": rows}
    payload = json.dumps(report, indent=2, ensure_ascii=False) + "\n"
    if args.output:
        args.output.write_text(payload, encoding="utf-8")
    else:
        print(payload, end="")


if __name__ == "__main__":
    main()

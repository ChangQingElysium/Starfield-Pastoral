#!/usr/bin/env python3
"""Import the authored pasture-grass Blockbench projects as Java block models."""

from __future__ import annotations

import base64
import binascii
import copy
import json
from pathlib import Path
from typing import Iterator


ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIR = ROOT / "tmp/牧草"
MODEL_OUT = ROOT / "src/main/resources/assets/stardewcraft/models/block/grass"
TEXTURE_OUT = ROOT / "src/main/resources/assets/stardewcraft/textures/block/grass"

MODELS = (
    ("牧草.bbmodel", "pasture_grass_0"),
    ("牧草-开花.bbmodel", "pasture_grass_1"),
    ("矮牧草.bbmodel", "pasture_grass_2"),
    ("矮牧草-开花.bbmodel", "pasture_grass_3"),
    ("牧草-蓝色.bbmodel", "blue_pasture_grass_0"),
    ("牧草-蓝色-开花.bbmodel", "blue_pasture_grass_1"),
    ("矮牧草-蓝色.bbmodel", "blue_pasture_grass_2"),
    ("矮牧草-蓝色-开花.bbmodel", "blue_pasture_grass_3"),
)


def embedded_texture(source_model: Path, texture: dict) -> bytes:
    source = texture.get("source", "")
    if isinstance(source, str) and source.startswith("data:image/png;base64,"):
        try:
            return base64.b64decode(source.partition(",")[2], validate=True)
        except binascii.Error as exc:
            raise SystemExit(f"invalid embedded PNG in {source_model}: {exc}") from exc

    texture_path = texture.get("path", "")
    if isinstance(texture_path, str) and texture_path:
        path = Path(texture_path)
        if not path.is_absolute():
            path = source_model.parent / path
        if path.is_file():
            return path.read_bytes()

    raise SystemExit(f"missing embedded PNG in {source_model}")


def exported_element_ids(nodes: list) -> Iterator[str]:
    """Follow Blockbench's Java exporter: traverse exported Outliner groups in order."""
    for node in nodes:
        if isinstance(node, str):
            yield node
        elif isinstance(node, dict) and node.get("export", True):
            yield from exported_element_ids(node.get("children", []))


def rounded(value: float) -> int | float:
    result = round(float(value), 6)
    return int(result) if result.is_integer() else result


def scaled_uv(uv: list, width: int, height: int) -> list[int | float]:
    return [
        rounded(value * 16 / (width if index % 2 == 0 else height))
        for index, value in enumerate(uv)
    ]


def convert_element(
    source_model: Path,
    source: dict,
    texture_aliases: dict[object, dict],
    width: int,
    height: int,
) -> dict:
    inflate = float(source.get("inflate") or 0)
    element: dict = {
        # Blockbench bakes `inflate` into the exported Java-model bounds.  It is
        # especially important for the paired flower cubes, where the authored
        # 0.1 expansion keeps the two textured layers from occupying the same
        # plane and flickering in game.
        "from": [rounded(value - inflate) for value in source["from"]],
        "to": [rounded(value + inflate) for value in source["to"]],
    }
    if source.get("name") not in (None, "cube"):
        element["name"] = source["name"]
    if source.get("shade") is False:
        element["shade"] = False
    if source.get("light_emission"):
        element["light_emission"] = source["light_emission"]

    rotation = source.get("rotation", [0, 0, 0])
    nonzero_axes = [index for index, value in enumerate(rotation) if value]
    if len(nonzero_axes) > 1:
        raise SystemExit(f"unsupported multi-axis rotation in {source_model}: {rotation}")
    if nonzero_axes:
        axis_index = nonzero_axes[0]
        element["rotation"] = {
            "angle": rotation[axis_index],
            "axis": "xyz"[axis_index],
            "origin": copy.deepcopy(source.get("origin", [8, 8, 8])),
        }
        if source.get("rescale"):
            element["rotation"]["rescale"] = True

    faces: dict[str, dict] = {}
    for direction, source_face in source.get("faces", {}).items():
        texture_ref = source_face.get("texture")
        if texture_ref is None or source_face.get("enabled", True) is False:
            continue
        if isinstance(texture_ref, str) and texture_ref.startswith("#"):
            texture_ref = texture_ref[1:]
        texture = texture_aliases.get(texture_ref)
        if texture is None:
            raise SystemExit(
                f"unknown texture reference {texture_ref!r} in {source_model}"
            )

        face: dict = {}
        if "uv" in source_face:
            face["uv"] = scaled_uv(source_face["uv"], width, height)
        if source_face.get("rotation"):
            face["rotation"] = source_face["rotation"]
        face["texture"] = f"#{texture['id']}"
        if source_face.get("cullface"):
            face["cullface"] = source_face["cullface"]
        if source_face.get("tint", -1) >= 0:
            face["tintindex"] = source_face["tint"]
        faces[direction] = face
    element["faces"] = faces
    return element


def convert_bbmodel(source_model: Path, target_name: str) -> tuple[dict, list[tuple[str, bytes]]]:
    project = json.loads(source_model.read_text(encoding="utf-8"))
    if project.get("meta", {}).get("model_format") != "java_block":
        raise SystemExit(f"unsupported Blockbench format in {source_model}; expected java_block")

    source_textures = project.get("textures")
    if not isinstance(source_textures, list) or not source_textures:
        raise SystemExit(f"missing textures in {source_model}")

    texture_aliases: dict[object, dict] = {}
    model_textures: dict[str, str] = {}
    texture_files: list[tuple[str, bytes]] = []
    for index, texture in enumerate(source_textures):
        texture_id = str(texture.get("id", index))
        if texture_id in model_textures:
            raise SystemExit(f"duplicate texture id {texture_id!r} in {source_model}")
        filename = f"{target_name}_{index}.png"
        resource = f"stardewcraft:block/grass/{target_name}_{index}"
        model_textures[texture_id] = resource
        for alias in (index, str(index), texture_id, texture.get("uuid")):
            if alias is not None:
                texture_aliases[alias] = texture
        texture_files.append((filename, embedded_texture(source_model, texture)))
    model_textures["particle"] = model_textures[str(source_textures[0].get("id", 0))]

    resolution = project.get("resolution", {})
    width = int(resolution.get("width") or 16)
    height = int(resolution.get("height") or 16)
    elements_by_id = {
        element["uuid"]: element
        for element in project.get("elements", [])
        if element.get("export", True) and element.get("type", "cube") == "cube"
    }
    ordered_ids = list(exported_element_ids(project.get("outliner", [])))
    if set(ordered_ids) != set(elements_by_id):
        missing = sorted(set(elements_by_id) - set(ordered_ids))
        unknown = sorted(set(ordered_ids) - set(elements_by_id))
        raise SystemExit(
            f"Outliner mismatch in {source_model}: missing={missing}, unknown={unknown}"
        )

    model: dict = {
        "format_version": "1.9.0",
        "credit": "Made with Blockbench",
    }
    if project.get("parent"):
        model["parent"] = project["parent"]
    if project.get("ambientocclusion") is False:
        model["ambientocclusion"] = False
    if width != 16 or height != 16:
        model["texture_size"] = [width, height]
    model["textures"] = model_textures
    model["elements"] = [
        convert_element(
            source_model,
            elements_by_id[element_id],
            texture_aliases,
            width,
            height,
        )
        for element_id in ordered_ids
    ]
    if project.get("front_gui_light"):
        model["gui_light"] = "front"
    if project.get("display"):
        model["display"] = copy.deepcopy(project["display"])
    return model, texture_files


def main() -> None:
    MODEL_OUT.mkdir(parents=True, exist_ok=True)
    TEXTURE_OUT.mkdir(parents=True, exist_ok=True)

    for source_name, target_name in MODELS:
        source_model = SOURCE_DIR / source_name
        if not source_model.is_file():
            raise SystemExit(f"missing source model: {source_model}")
        model, texture_files = convert_bbmodel(source_model, target_name)
        for filename, contents in texture_files:
            (TEXTURE_OUT / filename).write_bytes(contents)
        (TEXTURE_OUT / f"{target_name}.png").unlink(missing_ok=True)
        (MODEL_OUT / f"{target_name}.json").write_text(
            json.dumps(model, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        print(f"Imported {source_name} -> {target_name}.json")


if __name__ == "__main__":
    main()

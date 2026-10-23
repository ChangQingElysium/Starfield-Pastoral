#!/usr/bin/env python3
"""Import maintained mailbox models; local source intake is not a CI build input."""

from __future__ import annotations

import argparse
import base64
import binascii
import copy
import json
from pathlib import Path
from typing import Iterator


ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIR = ROOT / "assets-src/furniture/mailbox"
MODEL_OUT = ROOT / "src/main/resources/assets/stardewcraft/models/block/utility"
TEXTURE_OUT = ROOT / "src/main/resources/assets/stardewcraft/textures/block/utility"
# The four exported flipbook strips are complementary odd/even-frame layers.
# They must advance on the same clock or the owl head/wings briefly become
# transparent together. Five ticks is the requested ~5x slowdown from the
# authored one-tick page flip.
MAIL_PAGE_FRAME_TIME = 5

# The supplied files intentionally omit display transforms. Use the standard
# Minecraft block-item transforms used throughout StardewCraft utility models.
DEFAULT_BLOCK_DISPLAY = {
    "thirdperson_righthand": {
        "rotation": [75, 45, 0],
        "translation": [0, 2.5, 0],
        "scale": [0.375, 0.375, 0.375],
    },
    "thirdperson_lefthand": {
        "rotation": [75, 45, 0],
        "translation": [0, 2.5, 0],
        "scale": [0.375, 0.375, 0.375],
    },
    "firstperson_righthand": {
        "rotation": [0, 45, 0],
        "scale": [0.4, 0.4, 0.4],
    },
    "firstperson_lefthand": {
        "rotation": [0, -135, 0],
        "scale": [0.4, 0.4, 0.4],
    },
    "ground": {
        "translation": [0, 3, 0],
        "scale": [0.25, 0.25, 0.25],
    },
    "gui": {
        "rotation": [30, -135, 0],
        "translation": [0, -1.25, 0],
        "scale": [0.625, 0.625, 0.625],
    },
    "fixed": {"scale": [0.5, 0.5, 0.5]},
}

MAILBOX_MODELS = (
    ("信箱-无信.bbmodel", "mailbox", True),
    ("信箱.bbmodel", "mailbox_has_mail", False),
)


def embedded_texture(source_model: Path, texture: dict) -> bytes:
    source = texture.get("source", "")
    if isinstance(source, str) and source.startswith("data:image/png;base64,"):
        try:
            return base64.b64decode(source.partition(",")[2], validate=True)
        except binascii.Error as exc:
            raise SystemExit(f"invalid embedded PNG in {source_model}: {exc}") from exc
    raise SystemExit(f"missing embedded PNG in {source_model}")


def exported_element_ids(nodes: list, enabled: bool = True) -> Iterator[str]:
    """Follow Blockbench's Java exporter, including group export switches."""
    for node in nodes:
        if isinstance(node, str):
            if enabled:
                yield node
        elif isinstance(node, dict):
            yield from exported_element_ids(
                node.get("children", []),
                enabled and node.get("export", True),
            )


def rounded(value: float) -> int | float:
    result = round(float(value), 6)
    return int(result) if result.is_integer() else result


def scaled_uv(uv: list, width: int, height: int) -> list[int | float]:
    return [
        rounded(value * 16 / (width if index % 2 == 0 else height))
        for index, value in enumerate(uv)
    ]


def resolve_texture(texture_ref: object, aliases: dict[object, dict], source_model: Path) -> dict:
    if isinstance(texture_ref, str) and texture_ref.startswith("#"):
        texture_ref = texture_ref[1:]
    texture = aliases.get(texture_ref)
    if texture is None:
        raise SystemExit(f"unknown texture reference {texture_ref!r} in {source_model}")
    return texture


def convert_element(
    source_model: Path,
    source: dict,
    texture_aliases: dict[object, dict],
    width: int,
    height: int,
) -> dict:
    inflate = float(source.get("inflate") or 0)
    element: dict = {
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
            "angle": rounded(rotation[axis_index]),
            "axis": "xyz"[axis_index],
            "origin": copy.deepcopy(source.get("origin", [8, 8, 8])),
        }
        if source.get("rescale"):
            element["rotation"]["rescale"] = True

    faces: dict[str, dict] = {}
    for direction, source_face in source.get("faces", {}).items():
        texture_ref = source_face.get("texture")
        if texture_ref is None:
            continue
        texture = resolve_texture(texture_ref, texture_aliases, source_model)
        face: dict = {}
        if source_face.get("enabled", True) is not False and "uv" in source_face:
            face["uv"] = scaled_uv(source_face["uv"], width, height)
        if source_face.get("rotation"):
            face["rotation"] = source_face["rotation"]
        face["texture"] = f"#{texture['_export_id']}"
        if source_face.get("cullface"):
            face["cullface"] = source_face["cullface"]
        if source_face.get("tint", -1) >= 0:
            face["tintindex"] = source_face["tint"]
        faces[direction] = face
    element["faces"] = faces
    return element


def animation_metadata(texture: dict) -> dict | None:
    width = int(texture.get("width") or 0)
    height = int(texture.get("height") or 0)
    frame_width = int(texture.get("uv_width") or width)
    frame_height = int(texture.get("uv_height") or frame_width)
    if width <= 0 or height <= 0 or frame_width <= 0 or frame_height <= 0:
        return None
    if width == frame_width and height == frame_height:
        return None
    if width != frame_width or height % frame_height != 0:
        raise SystemExit(
            f"unsupported animated texture layout: {width}x{height}, frame {frame_width}x{frame_height}"
        )
    animation: dict = {"frametime": MAIL_PAGE_FRAME_TIME}
    if texture.get("frame_interpolate"):
        animation["interpolate"] = True
    frame_order = str(texture.get("frame_order") or "").strip()
    if frame_order:
        animation["frames"] = [int(value.strip()) for value in frame_order.split()]
    return {"animation": animation}


def convert_bbmodel(
    source_model: Path,
    target_name: str,
    single_texture_name: bool,
) -> tuple[dict, list[tuple[str, bytes, dict | None]]]:
    project = json.loads(source_model.read_text(encoding="utf-8"))
    if project.get("meta", {}).get("model_format") != "java_block":
        raise SystemExit(f"unsupported Blockbench format in {source_model}; expected java_block")

    source_textures = project.get("textures")
    if not isinstance(source_textures, list) or not source_textures:
        raise SystemExit(f"missing textures in {source_model}")

    texture_aliases: dict[object, dict] = {}
    for index, original in enumerate(source_textures):
        texture = copy.deepcopy(original)
        texture["_source_index"] = index
        texture["_export_id"] = str(texture.get("id", index))
        for alias in (index, str(index), texture["_export_id"], texture.get("uuid")):
            if alias is not None:
                texture_aliases[alias] = texture

    elements_by_id = {
        element["uuid"]: element
        for element in project.get("elements", [])
        if element.get("type", "cube") == "cube"
    }
    ordered_ids = list(exported_element_ids(project.get("outliner", [])))
    ordered_elements = []
    for element_id in ordered_ids:
        source = elements_by_id.get(element_id)
        if source is None:
            raise SystemExit(f"Outliner references unknown element {element_id} in {source_model}")
        if source.get("export", True):
            ordered_elements.append(source)

    resolution = project.get("resolution", {})
    width = int(resolution.get("width") or 16)
    height = int(resolution.get("height") or 16)

    used_ids: set[str] = set()
    for element in ordered_elements:
        for face in element.get("faces", {}).values():
            if face.get("texture") is not None:
                used_ids.add(resolve_texture(face["texture"], texture_aliases, source_model)["_export_id"])

    particle = next((texture for texture in texture_aliases.values() if texture.get("particle")), None)
    if particle is None:
        particle = texture_aliases[0]
    used_ids.add(particle["_export_id"])

    model_textures: dict[str, str] = {}
    texture_files: list[tuple[str, bytes, dict | None]] = []
    emitted: set[str] = set()
    for index in range(len(source_textures)):
        texture = texture_aliases[index]
        texture_id = texture["_export_id"]
        if texture_id not in used_ids or texture_id in emitted:
            continue
        emitted.add(texture_id)
        filename_stem = target_name if single_texture_name else f"{target_name}_{index}"
        model_textures[texture_id] = f"stardewcraft:block/utility/{filename_stem}"
        texture_files.append(
            (f"{filename_stem}.png", embedded_texture(source_model, texture), animation_metadata(texture))
        )
    model_textures["particle"] = model_textures[particle["_export_id"]]

    model: dict = {
        "format_version": project.get("java_block_version", "1.9.0"),
        "credit": "Made with Blockbench",
        "render_type": "minecraft:cutout",
    }
    if project.get("parent"):
        model["parent"] = project["parent"]
    if project.get("ambientocclusion") is False:
        model["ambientocclusion"] = False
    if width != 16 or height != 16:
        model["texture_size"] = [width, height]
    model["textures"] = model_textures
    model["elements"] = [
        convert_element(source_model, element, texture_aliases, width, height)
        for element in ordered_elements
    ]
    if project.get("front_gui_light"):
        model["gui_light"] = "front"
    model["display"] = copy.deepcopy(DEFAULT_BLOCK_DISPLAY)
    return model, texture_files


def write_texture(filename: str, contents: bytes, metadata: dict | None) -> None:
    path = TEXTURE_OUT / filename
    path.write_bytes(contents)
    metadata_path = Path(f"{path}.mcmeta")
    if metadata is None:
        metadata_path.unlink(missing_ok=True)
    else:
        metadata_path.write_text(
            json.dumps(metadata, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )


def import_mailboxes() -> None:
    MODEL_OUT.mkdir(parents=True, exist_ok=True)
    TEXTURE_OUT.mkdir(parents=True, exist_ok=True)
    for source_name, target_name, single_texture_name in MAILBOX_MODELS:
        source_model = SOURCE_DIR / source_name
        if not source_model.is_file():
            raise SystemExit(f"missing source model: {source_model}")
        model, texture_files = convert_bbmodel(source_model, target_name, single_texture_name)
        for filename, contents, metadata in texture_files:
            write_texture(filename, contents, metadata)
        (MODEL_OUT / f"{target_name}.json").write_text(
            json.dumps(model, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        print(f"Imported {source_name} -> {target_name}.json ({len(model['elements'])} elements)")


def main(output_root: Path | None = None, source_dir: Path | None = None) -> None:
    global SOURCE_DIR, MODEL_OUT, TEXTURE_OUT
    if source_dir is not None:
        SOURCE_DIR = source_dir
    if output_root is not None:
        MODEL_OUT = output_root / "assets/stardewcraft/models/block/utility"
        TEXTURE_OUT = output_root / "assets/stardewcraft/textures/block/utility"
    import_mailboxes()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-root", type=Path, help="Resource root; defaults to src/main/resources.")
    parser.add_argument("--source-dir", type=Path, help="Override the local mailbox source directory.")
    options = parser.parse_args()
    main(options.output_root, options.source_dir)

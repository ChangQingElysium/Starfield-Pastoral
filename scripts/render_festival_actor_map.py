#!/usr/bin/env python3
"""
Render Stardew Valley festival actor placement maps.

Examples:
  python3 scripts/render_festival_actor_map.py --preset moonlight_jellies --all
  python3 scripts/render_festival_actor_map.py --preset night_market
  python3 scripts/render_festival_actor_map.py --festival summer28 --profile y1 --phase main
  python3 scripts/render_festival_actor_map.py --secret-note-23
  python3 scripts/render_festival_actor_map.py --wizard-dark-talisman
  python3 scripts/render_festival_actor_map.py --wizard-magic-ink
  python3 scripts/render_festival_actor_map.py --dark-talisman-hunt
  python3 scripts/render_festival_actor_map.py --museum-lost-books
  python3 scripts/render_festival_actor_map.py --linus-heart-events
  python3 scripts/render_festival_actor_map.py --linus-schedule
  python3 scripts/render_festival_actor_map.py --george-heart-events
"""

from __future__ import annotations

import argparse
import json
import math
import re
import shlex
import textwrap
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
CONTENT_DIR = ROOT / "源文件" / "Content"
MAPS_DIR = CONTENT_DIR / "Maps"
DATA_DIR = CONTENT_DIR / "Data"
PORTRAITS_DIR = CONTENT_DIR / "Portraits"
SCHEDULES_DIR = CONTENT_DIR / "Characters" / "schedules"
DEFAULT_OUTPUT_DIR = ROOT / "tools" / "generated" / "festival_actor_maps"

TILE_SIZE = 16
TILED_GID_MASK = 0x1FFFFFFF
BACKGROUND_LAYERS = (
    "Back",
    "Back2",
    "Buildings",
    "Buildings2",
    "Front",
    "Paths",
    "AlwaysFront",
)
FACING_LABELS = {
    0: "N",
    1: "E",
    2: "S",
    3: "W",
}
FACING_COLORS = {
    0: (82, 148, 255, 255),
    1: (255, 174, 66, 255),
    2: (80, 208, 142, 255),
    3: (226, 93, 103, 255),
}
FACING_VECTORS = {
    0: (0, -1),
    1: (1, 0),
    2: (0, 1),
    3: (-1, 0),
}


@dataclass(frozen=True)
class Tileset:
    first_gid: int
    columns: int
    image: Image.Image | None


@dataclass
class ActorMarker:
    name: str
    tile_x: int
    tile_y: int
    facing: int
    anchor: tuple[float, float]
    box: list[float]
    label: str | None = None
    portrait_name: str | None = None


def load_font(size: int) -> ImageFont.ImageFont:
    candidates = (
        "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
        "/System/Library/Fonts/Supplemental/Arial.ttf",
        "/Library/Fonts/Arial.ttf",
    )
    for candidate in candidates:
        path = Path(candidate)
        if path.exists():
            return ImageFont.truetype(str(path), size=size)
    return ImageFont.load_default()


def parse_csv_layer(layer: ET.Element) -> list[list[int]]:
    data = layer.find("data")
    if data is None or data.get("encoding") != "csv" or not data.text:
        return []
    rows: list[list[int]] = []
    for line in data.text.strip().splitlines():
        line = line.strip().rstrip(",")
        if line:
            rows.append([int(value) for value in line.split(",")])
    return rows


def find_tileset_for_gid(gid: int, tilesets: Iterable[Tileset]) -> Tileset | None:
    clean_gid = gid & TILED_GID_MASK
    for tileset in tilesets:
        if clean_gid >= tileset.first_gid:
            return tileset
    return None


def load_tilesets(tmx_root: ET.Element, tmx_path: Path) -> list[Tileset]:
    tilesets: list[Tileset] = []
    for tileset in tmx_root.findall("tileset"):
        first_gid = int(tileset.get("firstgid", "0"))
        columns = int(tileset.get("columns", "0"))
        image_node = tileset.find("image")
        if image_node is None:
            tilesets.append(Tileset(first_gid, columns, None))
            continue

        source = image_node.get("source", "")
        image_path = Path(source)
        if not image_path.suffix:
            image_path = image_path.with_suffix(".png")
        candidates = [tmx_path.parent / image_path, MAPS_DIR / image_path, CONTENT_DIR / "TileSheets" / image_path]
        resolved = next((path for path in candidates if path.exists()), None)
        image = Image.open(resolved).convert("RGBA") if resolved else None
        tilesets.append(Tileset(first_gid, columns, image))
    return sorted(tilesets, key=lambda item: item.first_gid, reverse=True)


def tile_image(gid: int, tilesets: list[Tileset]) -> Image.Image | None:
    clean_gid = gid & TILED_GID_MASK
    if clean_gid == 0:
        return None
    tileset = find_tileset_for_gid(clean_gid, tilesets)
    if tileset is None or tileset.image is None or tileset.columns <= 0:
        return None
    local_id = clean_gid - tileset.first_gid
    sx = (local_id % tileset.columns) * TILE_SIZE
    sy = (local_id // tileset.columns) * TILE_SIZE
    if sx + TILE_SIZE > tileset.image.width or sy + TILE_SIZE > tileset.image.height:
        return None
    return tileset.image.crop((sx, sy, sx + TILE_SIZE, sy + TILE_SIZE))


def render_tmx(tmx_path: Path, scale: int, layers: Iterable[str] = BACKGROUND_LAYERS) -> Image.Image:
    root = ET.parse(tmx_path).getroot()
    map_width = int(root.get("width", "0"))
    map_height = int(root.get("height", "0"))
    canvas = Image.new("RGBA", (map_width * TILE_SIZE, map_height * TILE_SIZE), (0, 0, 0, 255))
    tilesets = load_tilesets(root, tmx_path)
    layer_names = set(layers)

    for layer in root.findall("layer"):
        if layer.get("name") not in layer_names:
            continue
        for y, row in enumerate(parse_csv_layer(layer)):
            for x, gid in enumerate(row):
                img = tile_image(gid, tilesets)
                if img is not None:
                    canvas.alpha_composite(img, (x * TILE_SIZE, y * TILE_SIZE))

    if scale != 1:
        canvas = canvas.resize((canvas.width * scale, canvas.height * scale), Image.Resampling.NEAREST)
    return canvas


def load_actor_index() -> dict[int, str]:
    characters = json.loads((DATA_DIR / "Characters.json").read_text(encoding="utf-8"))
    result: dict[int, str] = {}
    for name, data in characters.items():
        index = data.get("FestivalVanillaActorIndex", -1)
        if isinstance(index, int) and index >= 0 and index not in result:
            result[index] = name
    return result


def extract_actors(tmx_path: Path, layer_name: str, scale: int) -> list[ActorMarker]:
    root = ET.parse(tmx_path).getroot()
    layer = next((item for item in root.findall("layer") if item.get("name") == layer_name), None)
    if layer is None:
        raise ValueError(f"Map {tmx_path.name} has no layer named {layer_name!r}.")

    actor_names = load_actor_index()
    rows = parse_csv_layer(layer)
    markers: list[ActorMarker] = []
    for y, row in enumerate(rows):
        for x, gid in enumerate(row):
            clean_gid = gid & TILED_GID_MASK
            if clean_gid == 0:
                continue
            tile_index = clean_gid - 1
            actor_index = tile_index // 4
            facing = tile_index % 4
            name = actor_names.get(actor_index)
            if name is None:
                continue
            anchor = ((x + 0.5) * TILE_SIZE * scale, (y + 0.5) * TILE_SIZE * scale)
            markers.append(ActorMarker(name, x, y, facing, anchor, [0.0, 0.0, 0.0, 0.0]))
    return markers


def parse_command_value(commands: str, command_name: str) -> str | None:
    pattern = rf"(?:^|/){re.escape(command_name)}\s+([^/]+)"
    match = re.search(pattern, commands)
    if not match:
        return None
    return match.group(1).strip().split()[0]


def festival_profile(festival_id: str, profile: str, phase: str) -> tuple[Path, str, str]:
    festival_path = DATA_DIR / "Festivals" / f"{festival_id}.json"
    if not festival_path.exists():
        raise FileNotFoundError(f"Festival data not found: {festival_path}")
    data = json.loads(festival_path.read_text(encoding="utf-8-sig"))

    setup_key = "set-up" if profile == "y1" else "set-up_y2"
    main_key = "mainEvent" if profile == "y1" else "mainEvent_y2"
    setup_commands = data.get(setup_key) or data.get("set-up")
    if not isinstance(setup_commands, str):
        raise ValueError(f"Festival {festival_id} has no setup commands for profile {profile}.")
    map_id = parse_command_value(setup_commands, "changeToTemporaryMap")
    if not map_id:
        raise ValueError(f"Festival {festival_id} setup has no changeToTemporaryMap command.")

    if phase == "setup":
        commands = setup_commands
    else:
        commands = data.get(main_key) or data.get("mainEvent")
        if not isinstance(commands, str):
            raise ValueError(f"Festival {festival_id} has no mainEvent commands for profile {profile}.")

    layer_name = parse_command_value(commands, "loadActors")
    if not layer_name:
        raise ValueError(f"Festival {festival_id} {phase} commands have no loadActors command.")

    tmx_path = MAPS_DIR / f"{map_id}.tmx"
    if not tmx_path.exists():
        raise FileNotFoundError(f"Map not found: {tmx_path}")
    return tmx_path, layer_name, map_id


def display_name(name: str) -> str:
    return " ".join(part for part in re.split(r"[_\s]+", name) if part)


def portrait_path(name: str) -> Path | None:
    candidates = [
        PORTRAITS_DIR / f"{name}.png",
        PORTRAITS_DIR / f"{name}_Beach.png",
        PORTRAITS_DIR / f"{name}_Winter.png",
    ]
    return next((path for path in candidates if path.exists()), None)


def load_portrait(name: str, size: int) -> Image.Image:
    path = portrait_path(name)
    if path is None:
        image = Image.new("RGBA", (size, size), (45, 52, 66, 255))
        draw = ImageDraw.Draw(image)
        font = load_font(max(12, size // 3))
        initials = "".join(part[0].upper() for part in display_name(name).split()[:2]) or "?"
        bbox = draw.textbbox((0, 0), initials, font=font)
        draw.text(((size - (bbox[2] - bbox[0])) / 2, (size - (bbox[3] - bbox[1])) / 2), initials, fill=(255, 255, 255), font=font)
        return image

    sheet = Image.open(path).convert("RGBA")
    crop = sheet.crop((0, 0, min(64, sheet.width), min(64, sheet.height)))
    return crop.resize((size, size), Image.Resampling.NEAREST)


def initial_boxes(markers: list[ActorMarker], canvas_size: tuple[int, int], portrait_size: int, label_height: int) -> None:
    width, height = canvas_size
    box_w = portrait_size + 18
    box_h = portrait_size + label_height + 16
    for marker in markers:
        x, y = marker.anchor
        left = x - box_w / 2
        top = y - box_h - 20
        if top < 6:
            top = y + 20
        left = min(max(left, 6), width - box_w - 6)
        top = min(max(top, 6), height - box_h - 6)
        marker.box = [left, top, left + box_w, top + box_h]


def repel_boxes(markers: list[ActorMarker], canvas_size: tuple[int, int], padding: int = 6, iterations: int = 90) -> None:
    width, height = canvas_size
    for _ in range(iterations):
        moved = False
        for i in range(len(markers)):
            a = markers[i].box
            for j in range(i + 1, len(markers)):
                b = markers[j].box
                overlap_x = min(a[2], b[2]) - max(a[0], b[0]) + padding
                overlap_y = min(a[3], b[3]) - max(a[1], b[1]) + padding
                if overlap_x <= 0 or overlap_y <= 0:
                    continue
                acx = (a[0] + a[2]) / 2
                acy = (a[1] + a[3]) / 2
                bcx = (b[0] + b[2]) / 2
                bcy = (b[1] + b[3]) / 2
                if overlap_x < overlap_y:
                    shift = overlap_x / 2
                    direction = -1 if acx <= bcx else 1
                    a[0] += direction * shift
                    a[2] += direction * shift
                    b[0] -= direction * shift
                    b[2] -= direction * shift
                else:
                    shift = overlap_y / 2
                    direction = -1 if acy <= bcy else 1
                    a[1] += direction * shift
                    a[3] += direction * shift
                    b[1] -= direction * shift
                    b[3] -= direction * shift
                moved = True
        for marker in markers:
            box = marker.box
            dx = 0.0
            dy = 0.0
            if box[0] < 6:
                dx = 6 - box[0]
            elif box[2] > width - 6:
                dx = width - 6 - box[2]
            if box[1] < 6:
                dy = 6 - box[1]
            elif box[3] > height - 6:
                dy = height - 6 - box[3]
            if dx or dy:
                box[0] += dx
                box[2] += dx
                box[1] += dy
                box[3] += dy
                moved = True
        if not moved:
            break


def rounded_rectangle(draw: ImageDraw.ImageDraw, box: Iterable[float], radius: int, fill, outline, width: int = 1) -> None:
    draw.rounded_rectangle(tuple(round(value) for value in box), radius=radius, fill=fill, outline=outline, width=width)


def draw_arrow(draw: ImageDraw.ImageDraw, x: float, y: float, facing: int, color: tuple[int, int, int, int], size: int) -> None:
    dx, dy = FACING_VECTORS.get(facing, (0, 1))
    tip = (x + dx * size, y + dy * size)
    left_angle = math.atan2(dy, dx) + math.pi * 0.74
    right_angle = math.atan2(dy, dx) - math.pi * 0.74
    left = (x + math.cos(left_angle) * size * 0.65, y + math.sin(left_angle) * size * 0.65)
    right = (x + math.cos(right_angle) * size * 0.65, y + math.sin(right_angle) * size * 0.65)
    draw.polygon([tip, left, right], fill=color)


def draw_title(draw: ImageDraw.ImageDraw, title: str, canvas_width: int) -> None:
    font = load_font(28)
    text = title
    bbox = draw.textbbox((0, 0), text, font=font)
    pad = 12
    box = (12, 12, min(canvas_width - 12, bbox[2] + pad * 2 + 12), bbox[3] + pad * 2 + 12)
    rounded_rectangle(draw, box, 6, (20, 24, 32, 216), (255, 255, 255, 110))
    draw.text((box[0] + pad, box[1] + pad - 1), text, fill=(255, 255, 255, 255), font=font)


def draw_legend(draw: ImageDraw.ImageDraw, canvas_size: tuple[int, int]) -> None:
    font = load_font(16)
    width, height = canvas_size
    labels = [("N", "up"), ("E", "right"), ("S", "down"), ("W", "left")]
    box_w = 190
    box_h = 30 + len(labels) * 24
    left = width - box_w - 12
    top = height - box_h - 12
    rounded_rectangle(draw, (left, top, left + box_w, top + box_h), 6, (20, 24, 32, 216), (255, 255, 255, 110))
    draw.text((left + 12, top + 10), "Facing", fill=(255, 255, 255, 255), font=font)
    y = top + 34
    for idx, (label, meaning) in enumerate(labels):
        color = FACING_COLORS[idx]
        draw.ellipse((left + 14, y + 4, left + 30, y + 20), fill=color, outline=(255, 255, 255, 180))
        draw.text((left + 38, y + 2), f"{label} = {meaning}", fill=(255, 255, 255, 235), font=font)
        y += 24


def annotate_map(base: Image.Image, markers: list[ActorMarker], title: str, portrait_size: int) -> Image.Image:
    image = base.convert("RGBA")
    overlay = Image.new("RGBA", image.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay)
    label_font = load_font(15)
    tiny_font = load_font(13)
    label_height = 32
    initial_boxes(markers, image.size, portrait_size, label_height)
    repel_boxes(markers, image.size)

    for marker in markers:
        ax, ay = marker.anchor
        bx0, by0, bx1, by1 = marker.box
        cx = (bx0 + bx1) / 2
        cy = (by0 + by1) / 2
        color = FACING_COLORS.get(marker.facing, (255, 255, 255, 255))
        draw.line((cx, cy, ax, ay), fill=(255, 255, 255, 120), width=2)
        draw.ellipse((ax - 7, ay - 7, ax + 7, ay + 7), fill=(20, 24, 32, 230), outline=color, width=3)
        draw_arrow(draw, ax, ay, marker.facing, color, 17)

    for marker in markers:
        bx0, by0, bx1, by1 = marker.box
        color = FACING_COLORS.get(marker.facing, (255, 255, 255, 255))
        rounded_rectangle(draw, marker.box, 8, (16, 20, 28, 226), color, width=3)
        portrait = load_portrait(marker.portrait_name or marker.name, portrait_size)
        px = round(bx0 + 9)
        py = round(by0 + 8)
        overlay.alpha_composite(portrait, (px, py))
        draw.rectangle((px, py, px + portrait_size, py + portrait_size), outline=(255, 255, 255, 170), width=1)

        label = marker.label or display_name(marker.name)
        wrapped = textwrap.wrap(label, width=11)[:2]
        ty = py + portrait_size + 3
        for line in wrapped:
            bbox = draw.textbbox((0, 0), line, font=label_font)
            draw.text((bx0 + (bx1 - bx0 - (bbox[2] - bbox[0])) / 2, ty), line, fill=(255, 255, 255, 255), font=label_font)
            ty += 15
        badge = FACING_LABELS.get(marker.facing, "?")
        draw.ellipse((bx1 - 25, by0 + 6, bx1 - 7, by0 + 24), fill=color, outline=(255, 255, 255, 190))
        bbox = draw.textbbox((0, 0), badge, font=tiny_font)
        draw.text((bx1 - 16 - (bbox[2] - bbox[0]) / 2, by0 + 7), badge, fill=(10, 12, 16, 255), font=tiny_font)

    draw_title(draw, title, image.width)
    draw_legend(draw, image.size)
    return Image.alpha_composite(image, overlay)


def output_name(festival_id: str, map_id: str, profile: str, phase: str) -> str:
    safe_map = re.sub(r"[^A-Za-z0-9_.-]+", "_", map_id).lower()
    return f"{festival_id}_{safe_map}_{profile}_{phase}_actors.png"


def render_one(festival_id: str, profile: str, phase: str, output_dir: Path, scale: int, portrait_size: int) -> Path:
    tmx_path, layer_name, map_id = festival_profile(festival_id, profile, phase)
    base = render_tmx(tmx_path, scale=scale)
    markers = extract_actors(tmx_path, layer_name, scale=scale)
    title = f"{festival_id} / {map_id} / {profile} / {phase} / {layer_name} ({len(markers)} NPCs)"
    annotated = annotate_map(base, markers, title, portrait_size=portrait_size)

    output_dir.mkdir(parents=True, exist_ok=True)
    output_path = output_dir / output_name(festival_id, map_id, profile, phase)
    annotated.save(output_path)
    return output_path


def draw_tile_grid(image: Image.Image, scale: int, origin_x: int, origin_y: int) -> Image.Image:
    overlay = Image.new("RGBA", image.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay)
    font = load_font(13)
    step = TILE_SIZE * scale
    for pixel_x in range(0, image.width + 1, step):
        tile_x = origin_x + pixel_x // step
        draw.line((pixel_x, 0, pixel_x, image.height), fill=(255, 255, 255, 105), width=1)
        if tile_x % 2 == 0:
            draw.text((pixel_x + 3, 2), str(tile_x), fill=(255, 255, 255, 235), font=font,
                      stroke_width=2, stroke_fill=(0, 0, 0, 210))
    for pixel_y in range(0, image.height + 1, step):
        tile_y = origin_y + pixel_y // step
        draw.line((0, pixel_y, image.width, pixel_y), fill=(255, 255, 255, 105), width=1)
        if tile_y % 2 == 0:
            draw.text((3, pixel_y + 2), str(tile_y), fill=(255, 255, 255, 235), font=font,
                      stroke_width=2, stroke_fill=(0, 0, 0, 210))
    return Image.alpha_composite(image.convert("RGBA"), overlay)


def render_winter_star_secret_santa_map(output_dir: Path, scale: int, portrait_size: int) -> Path:
    map_id = "Town-Christmas"
    tmx_path = MAPS_DIR / f"{map_id}.tmx"
    full = render_tmx(tmx_path, scale=scale)
    crop_x0, crop_y0, crop_x1, crop_y1 = 14, 54, 42, 80
    step = TILE_SIZE * scale
    base = full.crop((crop_x0 * step, crop_y0 * step, crop_x1 * step, crop_y1 * step))
    base = draw_tile_grid(base, scale, crop_x0, crop_y0)

    def marker(name: str, x: int, y: int, facing: int, label: str, portrait: str | None = None) -> ActorMarker:
        return ActorMarker(name, x, y, facing,
                           ((x - crop_x0 + 0.5) * step, (y - crop_y0 + 0.5) * step),
                           [0.0, 0.0, 0.0, 0.0], label, portrait)

    markers = [
        marker("farmer", 30, 69, 0, "P1 Farmer start/final"),
        marker("gift_giver", 29, 75, 0, "P2 Giver entry"),
        marker("gift_giver", 29, 71, 0, "P3 Approach stop"),
        marker("gift_giver", 30, 71, 1, "P4 Present gift"),
        marker("gift_giver", 29, 69, 1, "P5 Handoff/dialogue"),
        marker("gift_box", 30, 70, 0, "P6 Gift visual anchor"),
        marker("Emily", 37, 59, 2, "P7 Emily temporary", "Emily"),
        marker("Haley", 35, 74, 2, "P8 Haley temporary", "Haley"),
        marker("Emily", 29, 72, 2, "Emily restore", "Emily"),
        marker("Haley", 30, 72, 2, "Haley restore", "Haley"),
        marker("viewport", 30, 67, 2, "Vanilla viewport anchor"),
    ]

    route_layer = Image.new("RGBA", base.size, (0, 0, 0, 0))
    route_draw = ImageDraw.Draw(route_layer)
    route_points = [(29, 75), (29, 71), (30, 71), (29, 71), (29, 69)]
    pixel_points = [((x - crop_x0 + 0.5) * step, (y - crop_y0 + 0.5) * step) for x, y in route_points]
    route_draw.line(pixel_points, fill=(255, 220, 55, 230), width=max(4, scale * 2), joint="curve")
    base = Image.alpha_composite(base, route_layer)

    title = "Winter Star Y1 / Secret Santa / vanilla event points + route"
    annotated = annotate_map(base, markers, title, portrait_size=portrait_size)
    output_dir.mkdir(parents=True, exist_ok=True)
    output_path = output_dir / "winter25_town-christmas_y1_secret_santa_event_points.png"
    annotated.save(output_path)
    return output_path


def draw_numbered_source_points(
    base: Image.Image,
    points: list[tuple[str, float, float]],
    crop_origin: tuple[int, int],
    scale: int,
    title: str,
    route: bool = False,
    start_index: int = 1,
) -> Image.Image:
    map_image = base.convert("RGBA")
    sidebar_width = 520 if len(points) <= 12 else 0
    canvas_height = map_image.height
    if sidebar_width:
        canvas_height = max(canvas_height, 112 + len(points) * 52)
    image = Image.new(
        "RGBA",
        (map_image.width + sidebar_width, canvas_height),
        (232, 240, 247, 255),
    )
    image.alpha_composite(map_image, (0, 0))
    overlay = Image.new("RGBA", image.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay)
    marker_font = load_font(13)
    label_font = load_font(15)
    step = TILE_SIZE * scale
    origin_x, origin_y = crop_origin
    anchors = [((x - origin_x) * step, (y - origin_y) * step) for _, x, y in points]
    if route and len(anchors) > 1:
        draw.line(anchors, fill=(255, 210, 55, 210), width=max(3, scale * 2), joint="curve")
    for index, ((label, _x, _y), (px, py)) in enumerate(zip(points, anchors), start=start_index):
        radius = 11
        draw.ellipse((px - radius, py - radius, px + radius, py + radius),
                     fill=(18, 22, 30, 235), outline=(255, 210, 55, 255), width=3)
        number = str(index)
        number_box = draw.textbbox((0, 0), number, font=marker_font)
        draw.text((px - (number_box[2] - number_box[0]) / 2, py - 8), number,
                  fill=(255, 245, 200, 255), font=marker_font)
    if sidebar_width:
        left = map_image.width
        draw.rectangle((left, 0, image.width, image.height), fill=(237, 243, 248, 255))
        draw.line((left, 0, left, image.height), fill=(89, 111, 132, 255), width=2)
        draw.text((left + 24, 24), "Source point legend", fill=(28, 42, 57, 255), font=load_font(24))
        for row, (label, _x, _y) in enumerate(points):
            index = start_index + row
            y = 72 + row * 52
            draw.ellipse((left + 24, y, left + 54, y + 30), fill=(255, 210, 55, 255),
                         outline=(132, 94, 13, 255), width=2)
            number = str(index)
            number_box = draw.textbbox((0, 0), number, font=marker_font)
            draw.text((left + 39 - (number_box[2] - number_box[0]) / 2, y + 7), number,
                      fill=(43, 37, 20, 255), font=marker_font)
            draw.text((left + 66, y + 5), label, fill=(36, 50, 64, 255), font=label_font)
    draw_title(draw, title, image.width)
    return Image.alpha_composite(image, overlay)


def load_vanilla_event(event_file: str, event_id: str) -> tuple[str, str, dict[str, str]]:
    """Load one exact vanilla event entry and its file-local fork scripts."""
    event_path = DATA_DIR / "Events" / event_file
    data = json.loads(event_path.read_text(encoding="utf-8-sig"))
    matches = [
        (key, value)
        for key, value in data.items()
        if key.split("/", 1)[0] == event_id and isinstance(value, str)
    ]
    if len(matches) != 1:
        raise ValueError(
            f"Expected one event {event_id!r} in {event_path.name}, found {len(matches)}.")
    key, script = matches[0]
    forks = {
        fork_key: fork_script
        for fork_key, fork_script in data.items()
        if "/" not in fork_key and isinstance(fork_script, str)
    }
    return key, script, forks


def draw_source_routes(
    base: Image.Image,
    crop_origin: tuple[int, int],
    scale: int,
    routes: list[tuple[tuple[int, int, int, int], list[tuple[float, float]]]],
) -> Image.Image:
    overlay = Image.new("RGBA", base.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay)
    step = TILE_SIZE * scale
    origin_x, origin_y = crop_origin
    for color, points in routes:
        anchors = [
            ((x - origin_x) * step, (y - origin_y) * step)
            for x, y in points
        ]
        if len(anchors) > 1:
            draw.line(
                anchors,
                fill=color,
                width=max(4, scale * 2),
                joint="curve",
            )
    return Image.alpha_composite(base.convert("RGBA"), overlay)


def render_linus_heart_event_source_maps(output_dir: Path, scale: int) -> list[Path]:
    """Render source-only Linus heart-event choreography; never map it to Minecraft."""
    town_key, town_script, _town_forks = load_vanilla_event("Town.json", "502969")
    campfire_key, campfire_script, _mountain_forks = load_vanilla_event("Mountain.json", "26")
    eight_key, eight_script, mountain_forks = load_vanilla_event("Mountain.json", "371652")
    required_fragments = (
        (town_script, "farmer 72 64 1 Linus 51 58 2"),
        (town_script, "warp Gus 45 71"),
        (campfire_script, "farmer 18 8 1 Linus 29 8 2"),
        (campfire_script, "specificTemporarySprite linusCampfire"),
        (eight_script, "farmer -1000 -1000 2 Robin -1000 -1000 2 Linus 23 33 1"),
        (eight_script, "question fork0"),
        (mountain_forks.get("linusWell", ""), "friendship Linus 250"),
    )
    for script, fragment in required_fragments:
        if fragment not in script:
            raise ValueError(f"Vanilla Linus event source changed; missing {fragment!r}.")

    output_dir.mkdir(parents=True, exist_ok=True)
    outputs: list[Path] = []
    step = TILE_SIZE * scale

    town = render_tmx(MAPS_DIR / "Town.tmx", scale=scale)
    first_crop = (48, 55, 76, 67)
    first = town.crop(tuple(value * step for value in first_crop))
    first = draw_tile_grid(first, scale, first_crop[0], first_crop[1])
    first = draw_source_routes(first, (first_crop[0], first_crop[1]), scale, [
        ((82, 148, 255, 230), [(72.5, 64.5), (58.5, 64.5), (53.5, 64.5)]),
        ((255, 196, 55, 230), [
            (51.5, 58.5), (51.5, 63.5), (53.5, 62.5),
            (52.5, 62.5), (51.5, 64.5),
        ]),
    ])
    first_points = [
        ("P01 Player start (72,64), facing E", 72.5, 64.5),
        ("C01 Initial viewport anchor (55,64)", 55.5, 64.5),
        ("L01 Linus start (51,58), facing S", 51.5, 58.5),
        ("L02 Linus first-can position (51,63)", 51.5, 63.5),
        ("FX01 linusLights first-can anchor (52,63)", 52.5, 63.5),
        ("G01 George doorway warp (57,64)", 57.5, 64.5),
        ("P02 Player stop for George (58,64)", 58.5, 64.5),
        ("L03 Linus startled stop (53,62)", 53.5, 62.5),
        ("L04 Linus confession position (51,64)", 51.5, 64.5),
        ("P03 Player stop for Linus (53,64)", 53.5, 64.5),
    ]
    first_annotated = draw_numbered_source_points(
        first,
        first_points,
        (first_crop[0], first_crop[1]),
        scale,
        "Linus 50-point event / Town 502969 / first garbage can",
    )
    first_path = output_dir / "linus_50point_town_first_can_vanilla_source_points.png"
    first_annotated.save(first_path)
    outputs.append(first_path)

    second_crop = (42, 60, 55, 75)
    second = town.crop(tuple(value * step for value in second_crop))
    second = draw_tile_grid(second, scale, second_crop[0], second_crop[1])
    second = draw_source_routes(second, (second_crop[0], second_crop[1]), scale, [
        ((82, 148, 255, 230), [(53.5, 64.5), (44.5, 64.5)]),
        ((255, 196, 55, 230), [(51.5, 64.5), (48.5, 70.5), (48.5, 68.5)]),
        ((80, 208, 142, 230), [
            (45.5, 71.5), (45.5, 72.5), (48.5, 72.5), (48.5, 71.5),
        ]),
    ])
    second_points = [
        ("L05 Linus second-can position (48,70)", 48.5, 70.5),
        ("FX02 linusLights second-can anchor (47,70)", 47.5, 70.5),
        ("U01 Gus doorway warp (45,71)", 45.5, 71.5),
        ("U02 Gus approach corner (45,72)", 45.5, 72.5),
        ("U03 Gus route corner (48,72)", 48.5, 72.5),
        ("U04 Gus dialogue stop (48,71)", 48.5, 71.5),
        ("L06 Linus reaction stop (48,68)", 48.5, 68.5),
        ("C02 Camera pan source end: viewport -1,+1", 54.5, 65.5),
        ("P04 Player leaves scene toward (44,64)", 44.5, 64.5),
    ]
    second_annotated = draw_numbered_source_points(
        second,
        second_points,
        (second_crop[0], second_crop[1]),
        scale,
        "Linus 50-point event / Town 502969 / Gus encounter",
    )
    second_path = output_dir / "linus_50point_town_gus_vanilla_source_points.png"
    second_annotated.save(second_path)
    outputs.append(second_path)

    mountain = render_tmx(MAPS_DIR / "Mountain.tmx", scale=scale)
    campfire_crop = (15, 4, 34, 13)
    campfire = mountain.crop(tuple(value * step for value in campfire_crop))
    campfire = draw_tile_grid(campfire, scale, campfire_crop[0], campfire_crop[1])
    campfire = draw_source_routes(campfire, (campfire_crop[0], campfire_crop[1]), scale, [
        ((82, 148, 255, 230), [
            (18.5, 8.5), (26.5, 8.5), (28.5, 8.5), (28.5, 9.5),
            (28.5, 8.5), (29.5, 8.5), (29.5, 7.5), (29.5, 8.5),
        ]),
        ((255, 196, 55, 230), [
            (29.5, 8.5), (30.5, 8.5), (30.5, 9.5),
            (30.5, 8.5), (29.5, 8.5),
        ]),
    ])
    campfire_points = [
        ("P01 Player start (18,8), facing E", 18.5, 8.5),
        ("P02 Player invitation stop (26,8)", 26.5, 8.5),
        ("P03 Player fireside stop (28,9)", 28.5, 9.5),
        ("P04 Tent entry route (28,8) -> (29,8) -> (29,7)", 29.5, 8.5),
        ("P05 Reward return warp (29,7)", 29.5, 7.5),
        ("L01 Linus start (29,8), facing S", 29.5, 8.5),
        ("L02 Linus fireside stop (30,9)", 30.5, 9.5),
        ("L03 Linus tent entry (29,8)", 29.5, 8.5),
        ("FX01 Campfire sprite anchor (29,9)", 29.5, 9.5),
        ("C01 Viewport anchor (29,7)", 29.5, 7.5),
    ]
    campfire_annotated = draw_numbered_source_points(
        campfire,
        campfire_points,
        (campfire_crop[0], campfire_crop[1]),
        scale,
        "Linus 4-heart / vanilla Mountain event 26 / campfire and tent",
    )
    campfire_path = output_dir / "linus_4heart_mountain_vanilla_source_points.png"
    campfire_annotated.save(campfire_path)
    outputs.append(campfire_path)

    eight_crop = (3, 16, 27, 36)
    eight = mountain.crop(tuple(value * step for value in eight_crop))
    eight = draw_tile_grid(eight, scale, eight_crop[0], eight_crop[1])
    eight = draw_source_routes(eight, (eight_crop[0], eight_crop[1]), scale, [
        ((255, 196, 55, 230), [
            (23.5, 33.5), (21.5, 33.5), (21.5, 32.5),
            (15.5, 32.5), (15.5, 27.5), (13.5, 27.5),
            (13.5, 28.5), (11.5, 28.5), (9.5, 28.5),
            (5.5, 28.5), (5.5, 18.5),
        ]),
        ((80, 208, 142, 230), [(12.5, 26.5), (12.5, 27.5)]),
        ((82, 148, 255, 230), [(11.5, 26.5), (11.5, 27.5)]),
    ])
    eight_points = [
        ("C01 Initial viewport anchor (18,32)", 18.5, 32.5),
        ("C02 Four-second camera pan end (18,31)", 18.5, 31.5),
        ("L01 Linus start (23,33), facing E", 23.5, 33.5),
        ("FX01 First chopping/action tile (24,33)", 24.5, 33.5),
        ("L02 Linus second work position (21,32)", 21.5, 32.5),
        ("FX02 Second chopping/action tile (22,32)", 22.5, 32.5),
        ("L03 Linus cabin-side dialogue stop (13,27)", 13.5, 27.5),
        ("R01 Robin doorway warp (12,26)", 12.5, 26.5),
        ("R02 Robin dialogue stop (12,27)", 12.5, 27.5),
        ("P01 Player doorway warp (11,26)", 11.5, 26.5),
        ("P02 Player dialogue stop (11,27)", 11.5, 27.5),
        ("L04 Linus berry-exit destination (5,18)", 5.5, 18.5),
    ]
    eight_annotated = draw_numbered_source_points(
        eight,
        eight_points,
        (eight_crop[0], eight_crop[1]),
        scale,
        "Linus 8-heart / vanilla Mountain event 371652 / both answer branches",
    )
    eight_path = output_dir / "linus_8heart_mountain_vanilla_source_points.png"
    eight_annotated.save(eight_path)
    outputs.append(eight_path)
    return outputs


def render_george_heart_event_source_map(output_dir: Path, scale: int) -> Path:
    """Render source-only George 6-heart choreography; never map it to Minecraft."""
    event_key, event_script, _forks = load_vanilla_event("JoshHouse.json", "18")
    required_fragments = (
        "sadpiano/17 17/farmer 10 19 1 George 18 17 0",
        "move farmer 4 0 0/move farmer 0 -2 0/move farmer 3 0 0",
        "move George 0 1 2",
        "move farmer 0 1 1",
    )
    for fragment in required_fragments:
        if fragment not in event_script:
            raise ValueError(
                f"Vanilla George event source changed; missing {fragment!r} in {event_key!r}.")

    output_dir.mkdir(parents=True, exist_ok=True)
    step = TILE_SIZE * scale
    crop = (8, 13, 22, 22)
    josh_house = render_tmx(MAPS_DIR / "JoshHouse.tmx", scale=scale)
    base = josh_house.crop(tuple(value * step for value in crop))
    base = draw_tile_grid(base, scale, crop[0], crop[1])
    base = draw_source_routes(base, (crop[0], crop[1]), scale, [
        ((82, 148, 255, 230), [
            (10.5, 19.5),
            (14.5, 19.5),
            (14.5, 17.5),
            (17.5, 17.5),
            (17.5, 18.5),
        ]),
        ((255, 196, 55, 230), [
            (18.5, 17.5),
            (18.5, 18.5),
        ]),
    ])
    points = [
        ("C01 Vanilla viewport anchor (17,17)", 17.5, 17.5),
        ("P01 Player start (10,19), facing E", 10.5, 19.5),
        ("P02 Player first route corner (14,19)", 14.5, 19.5),
        ("P03 Player second route corner (14,17)", 14.5, 17.5),
        ("P04 Player reaches shelf (17,17), facing E", 17.5, 17.5),
        ("P05 Player final dialogue step (17,18), facing E", 17.5, 18.5),
        ("G01 George start (18,17), facing N", 18.5, 17.5),
        ("G02 George story position (18,18), facing S", 18.5, 18.5),
    ]
    annotated = draw_numbered_source_points(
        base,
        points,
        (crop[0], crop[1]),
        scale,
        "George 6-heart / vanilla JoshHouse event 18",
    )
    output_path = output_dir / "george_6heart_joshhouse_vanilla_source_points.png"
    annotated.save(output_path)
    return output_path


def render_secret_note31_source_maps(output_dir: Path, scale: int) -> list[Path]:
    """Render only vanilla TMX/source points; no Minecraft-coordinate mapping."""
    output_dir.mkdir(parents=True, exist_ok=True)
    outputs: list[Path] = []
    step = TILE_SIZE * scale

    bus = render_tmx(MAPS_DIR / "BusStop.tmx", scale=scale)
    bus_crop = (6, 17, 49, 30)
    bus_image = bus.crop(tuple(value * step for value in bus_crop))
    bus_image = draw_tile_grid(bus_image, scale, bus_crop[0], bus_crop[1])
    bus_points = [
        ("Player start (10,23), facing E", 10.5, 23.5),
        ("Trigger tile A (11,23)", 11.5, 23.5),
        ("Trigger tile B (11,24)", 11.5, 24.5),
        ("Shadow start (26,23), facing W", 26.5, 23.5),
        ("Shadow notice/jump stop (24,23)", 24.5, 23.5),
        ("Player follow end (12,23)", 12.5, 23.5),
        ("Shadow run end (44,23)", 44.5, 23.5),
        ("Vanilla viewport anchor (24,23)", 24.5, 22.5),
    ]
    bus_annotated = draw_numbered_source_points(
        bus_image, bus_points, (bus_crop[0], bus_crop[1]), scale,
        "Secret Note 31 / Vanilla BusStop event / source points", route=False)
    bus_path = output_dir / "secret_note31_bus_stop_vanilla_source_points.png"
    bus_annotated.save(bus_path)
    outputs.append(bus_path)

    town = render_tmx(MAPS_DIR / "Town.tmx", scale=scale)
    footprint_crop = (9, 9, 37, 58)
    footprint_image = town.crop(tuple(value * step for value in footprint_crop))
    footprint_image = draw_tile_grid(footprint_image, scale, footprint_crop[0], footprint_crop[1])
    footprint_coords = [
        (14.5, 52.75), (13.5, 53.0), (15.5, 53.0), (16.0, 52.25), (17.0, 52.0),
        (17.5, 51.0), (18.3125, 50.5625), (18.75, 49.875), (21.75, 39.5), (21.0, 39.0),
        (21.75, 38.25), (22.5, 37.5), (21.75, 36.75), (23.0, 36.0), (22.25, 35.25),
        (23.5, 34.6), (23.5, 33.6), (24.25, 32.6), (26.75, 26.75), (27.5, 26.0),
        (30.0, 23.0), (31.0, 22.0), (30.5, 21.0), (31.0, 20.0), (30.0, 19.0),
        (29.0, 18.0), (29.1, 17.0), (30.0, 17.7), (31.5, 18.2), (30.5, 16.8),
    ]
    footprint_points = [(f"Footprint B{index:02d}", x, y)
                        for index, (x, y) in enumerate(footprint_coords, start=1)]
    footprint_annotated = draw_numbered_source_points(
        footprint_image, footprint_points, (footprint_crop[0], footprint_crop[1]), scale,
        "Secret Note 31 / Vanilla Town footprints / B01-B30", route=True)
    footprint_path = output_dir / "secret_note31_town_vanilla_footprints.png"
    footprint_annotated.save(footprint_path)
    outputs.append(footprint_path)

    finale_crop = (24, 9, 36, 21)
    finale_image = town.crop(tuple(value * step for value in finale_crop))
    finale_image = draw_tile_grid(finale_image, scale, finale_crop[0], finale_crop[1])
    finale_points = [
        ("Target bush (28,14)", 28.5, 14.5),
        ("Shadow spawn (29,13)", 29.5, 13.5),
        ("Landing when player X >= 31: (32,15)", 32.5, 15.5),
        ("Landing when player X < 31: (31,13)", 31.5, 13.5),
        ("Source escape direction: south/down", 31.5, 19.5),
    ]
    finale_annotated = draw_numbered_source_points(
        finale_image, finale_points, (finale_crop[0], finale_crop[1]), scale,
        "SN31 / Vanilla bush finale", route=False)
    finale_path = output_dir / "secret_note31_town_vanilla_bush_finale.png"
    finale_annotated.save(finale_path)
    outputs.append(finale_path)
    return outputs


def render_secret_note_core_source_maps(output_dir: Path, scale: int) -> list[Path]:
    """Render vanilla source anchors needed by active notes; never infer Minecraft coordinates."""
    output_dir.mkdir(parents=True, exist_ok=True)
    town_path = MAPS_DIR / "Town.tmx"
    town = render_tmx(town_path, scale=scale)
    step = TILE_SIZE * scale
    outputs: list[Path] = []

    root = ET.parse(town_path).getroot()
    buildings_objects = next(
        (group for group in root.findall("objectgroup") if group.get("name") == "Buildings"), None)
    if buildings_objects is None:
        raise ValueError("Town.tmx has no Buildings object layer.")
    garbage_points: dict[str, tuple[float, float]] = {}
    for obj in buildings_objects.findall("object"):
        properties = obj.find("properties")
        if properties is None:
            continue
        action = next((prop.get("value", "") for prop in properties.findall("property")
                       if prop.get("name") == "Action"), "")
        if action in {"Garbage Evelyn", "Garbage Saloon", "Garbage Blacksmith", "Garbage Museum"}:
            garbage_points[action.removeprefix("Garbage ")] = (
                float(obj.get("x", "0")) / TILE_SIZE + 0.5,
                float(obj.get("y", "0")) / TILE_SIZE + 0.5,
            )
    expected = {"Evelyn", "Saloon", "Blacksmith", "Museum"}
    if garbage_points.keys() != expected:
        raise ValueError(f"Unexpected Town garbage anchors: {sorted(garbage_points)}")

    garbage_crop = (40, 56, 116, 99)
    garbage_image = town.crop(tuple(value * step for value in garbage_crop))
    garbage_image = draw_tile_grid(garbage_image, scale, garbage_crop[0], garbage_crop[1])
    garbage_order = ("Evelyn", "Saloon", "Blacksmith", "Museum")
    garbage_labels = {
        "Evelyn": "Evelyn can (52,63): cookies",
        "Saloon": "Saloon can (47,70): dish of the day",
        "Blacksmith": "Blacksmith can (97,80): copper/iron/gold ore",
        "Museum": "Museum can (108,91): geode/omni geode",
    }
    garbage_source_points = [
        (garbage_labels[name], *garbage_points[name]) for name in garbage_order
    ]
    garbage_annotated = draw_numbered_source_points(
        garbage_image, garbage_source_points, (garbage_crop[0], garbage_crop[1]), scale,
        "Secret Note 12 / Vanilla Town named garbage cans / source anchors", route=False)
    garbage_path = output_dir / "secret_note12_town_vanilla_garbage_cans.png"
    garbage_annotated.save(garbage_path)
    outputs.append(garbage_path)

    dig_crop = (90, 0, 106, 13)
    dig_image = town.crop(tuple(value * step for value in dig_crop))
    dig_image = draw_tile_grid(dig_image, scale, dig_crop[0], dig_crop[1])
    dig_annotated = draw_numbered_source_points(
        dig_image,
        [("Dig tile (98,5): green strange doll (O)126", 98.5, 5.5)],
        (dig_crop[0], dig_crop[1]), scale,
        "SN17 / Vanilla Town / dig spot", route=False)
    dig_path = output_dir / "secret_note17_town_vanilla_dig_spot.png"
    dig_annotated.save(dig_path)
    outputs.append(dig_path)
    return outputs


def render_secret_note10_source_map(output_dir: Path, scale: int) -> Path:
    """Render the vanilla floor-100 choreography without inventing a Minecraft mapping."""
    output_dir.mkdir(parents=True, exist_ok=True)
    step = TILE_SIZE * scale
    crop_origin = (4, 4)
    grid_width, grid_height = 12, 9
    base = Image.new("RGBA", (grid_width * step, grid_height * step), (38, 42, 50, 255))
    draw = ImageDraw.Draw(base)
    for tile_y in range(grid_height):
        for tile_x in range(grid_width):
            color = (51, 56, 66, 255) if (tile_x + tile_y) % 2 == 0 else (45, 50, 59, 255)
            draw.rectangle(
                (tile_x * step, tile_y * step, (tile_x + 1) * step, (tile_y + 1) * step),
                fill=color,
            )
    base = draw_tile_grid(base, scale, crop_origin[0], crop_origin[1])

    def pixel(tile_x: float, tile_y: float) -> tuple[float, float]:
        return ((tile_x - crop_origin[0]) * step, (tile_y - crop_origin[1]) * step)

    route = [
        pixel(6.5, 6.5),
        pixel(6.5, 10.5),
        pixel(10.5, 10.5),
        pixel(13.5, 10.5),
        pixel(13.5, 9.5),
    ]
    route_layer = Image.new("RGBA", base.size, (0, 0, 0, 0))
    route_draw = ImageDraw.Draw(route_layer)
    route_draw.line(route, fill=(255, 210, 55, 230), width=max(4, scale * 2), joint="curve")
    for route_point in route:
        route_draw.ellipse(
            (route_point[0] - 5, route_point[1] - 5, route_point[0] + 5, route_point[1] + 5),
            fill=(255, 232, 122, 255),
        )
    base = Image.alpha_composite(base, route_layer)

    points = [
        ("Player start (6,6), facing S", 6.5, 6.5),
        ("Mr Qi (10,7), facing N; viewport (10,7)", 10.5, 7.5),
        ("Player route corner (6,10)", 6.5, 10.5),
        ("Player dialogue stop (10,10)", 10.5, 10.5),
        ("Player route corner (13,10)", 13.5, 10.5),
        ("Player drinks milk (13,9), then faces S", 13.5, 9.5),
        ("Milk/table visual source tile (13,7)", 13.5, 7.5),
    ]
    annotated = draw_numbered_source_points(
        base,
        points,
        crop_origin,
        scale,
        "SN10 / Vanilla floor 100 choreography",
        route=False,
    )
    output_path = output_dir / "secret_note10_skull_cavern_vanilla_choreography.png"
    annotated.save(output_path)
    return output_path


def render_secret_note23_source_map(output_dir: Path, scale: int) -> Path:
    """Render the vanilla bear-event anchors without mapping them to Minecraft coordinates."""
    output_dir.mkdir(parents=True, exist_ok=True)
    step = TILE_SIZE * scale
    woods = render_tmx(MAPS_DIR / "Woods.tmx", scale=scale)
    crop = (7, 12, 29, 23)
    base = woods.crop(tuple(value * step for value in crop))
    base = draw_tile_grid(base, scale, crop[0], crop[1])

    route_layer = Image.new("RGBA", base.size, (0, 0, 0, 0))
    route_draw = ImageDraw.Draw(route_layer)

    def pixel(tile_x: float, tile_y: float) -> tuple[float, float]:
        return ((tile_x - crop[0]) * step, (tile_y - crop[1]) * step)

    player_route = [pixel(25.5, 17.5), pixel(19.5, 17.5), pixel(14.5, 17.5)]
    route_draw.line(player_route, fill=(82, 148, 255, 230), width=max(4, scale * 2), joint="curve")
    base = Image.alpha_composite(base, route_layer)

    points = [
        ("Player start (25,17), facing W", 25.5, 17.5),
        ("Player first stop (19,17): notice bear", 19.5, 17.5),
        ("Player dialogue / syrup presentation stop (14,17)", 14.5, 17.5),
        ("Maple syrup visual (13,17)", 13.5, 17.5),
        ("Bear + vanilla viewport anchor (11,17); source turns E/S/E", 11.5, 17.5),
    ]
    annotated = draw_numbered_source_points(
        base,
        points,
        (crop[0], crop[1]),
        scale,
        "SN23 / Vanilla Secret Woods bear event / source anchors",
        route=False,
    )
    output_path = output_dir / "secret_note23_woods_vanilla_bear_event_points.png"
    annotated.save(output_path)
    return output_path


def render_wizard_dark_talisman_source_map(output_dir: Path, scale: int) -> Path:
    """Render vanilla event 529952 without mapping any point to Minecraft coordinates."""
    output_dir.mkdir(parents=True, exist_ok=True)
    step = TILE_SIZE * scale
    railroad = render_tmx(MAPS_DIR / "Railroad.tmx", scale=scale)
    crop = (43, 29, 62, 46)
    base = railroad.crop(tuple(value * step for value in crop))
    base = draw_tile_grid(base, scale, crop[0], crop[1])

    def pixel(tile_x: float, tile_y: float) -> tuple[float, float]:
        return ((tile_x - crop[0]) * step, (tile_y - crop[1]) * step)

    route_layer = Image.new("RGBA", base.size, (0, 0, 0, 0))
    route_draw = ImageDraw.Draw(route_layer)
    player_route = [
        pixel(50.5, 40.5),
        pixel(51.5, 40.5),
        pixel(51.5, 36.5),
        pixel(52.5, 36.5),
    ]
    wizard_step = [pixel(54.5, 36.5), pixel(53.5, 36.5), pixel(54.5, 36.5)]
    route_draw.line(player_route, fill=(82, 148, 255, 230), width=max(4, scale * 2), joint="curve")
    route_draw.line(wizard_step, fill=(190, 92, 255, 230), width=max(4, scale * 2), joint="curve")
    base = Image.alpha_composite(base, route_layer)

    points = [
        ("Viewport anchor (54,36)", 54.5, 36.5),
        ("Wizard main position (54,36), facing N", 54.5, 36.5),
        ("Player start (50,40), facing E", 50.5, 40.5),
        ("Player first route corner (51,40)", 51.5, 40.5),
        ("Player north route stop (51,36)", 51.5, 36.5),
        ("Player dialogue stop (52,36), facing E", 52.5, 36.5),
        ("Wizard ink-emphasis step (53,36), then returns", 53.5, 36.5),
    ]
    annotated = draw_numbered_source_points(
        base,
        points,
        (crop[0], crop[1]),
        scale,
        "Dark Talisman / Vanilla Railroad event 529952 / source choreography",
        route=False,
    )
    output_path = output_dir / "wizard_dark_talisman_railroad_vanilla_source_points.png"
    annotated.save(output_path)
    return output_path


def render_wizard_magic_ink_source_map(output_dir: Path, scale: int) -> Path:
    """Render vanilla WizardHouse event 418172 without inferring Minecraft coordinates."""
    output_dir.mkdir(parents=True, exist_ok=True)
    step = TILE_SIZE * scale
    wizard_house = render_tmx(MAPS_DIR / "WizardHouse.tmx", scale=scale)
    crop = (0, 8, 8, 19)
    base = wizard_house.crop(tuple(value * step for value in crop))
    base = draw_tile_grid(base, scale, crop[0], crop[1])

    def pixel(tile_x: float, tile_y: float) -> tuple[float, float]:
        return ((tile_x - crop[0]) * step, (tile_y - crop[1]) * step)

    route_layer = Image.new("RGBA", base.size, (0, 0, 0, 0))
    route_draw = ImageDraw.Draw(route_layer)
    wizard_route = [pixel(1.5, 14.5), pixel(2.5, 14.5), pixel(1.5, 14.5)]
    route_draw.line(wizard_route, fill=(190, 92, 255, 230), width=max(4, scale * 2), joint="curve")
    base = Image.alpha_composite(base, route_layer)

    points = [
        ("Vanilla viewport anchor (2,14)", 2.5, 14.5),
        ("Player fixed position (3,14), starts/finally faces W", 3.5, 14.5),
        ("Wizard main position (1,14), starts E", 1.5, 14.5),
        ("Wizard question step (2,14), then returns", 2.5, 14.5),
        ("Summoned book visual and smoke anchor (2,12)", 2.5, 12.5),
        ("Persistent WizardBook interaction tile (2,13)", 2.5, 13.5),
    ]
    annotated = draw_numbered_source_points(
        base,
        points,
        (crop[0], crop[1]),
        scale,
        "Magic Ink / Vanilla event 418172 / source points",
        route=False,
    )
    output_path = output_dir / "wizard_magic_ink_wizardhouse_vanilla_source_points.png"
    annotated.save(output_path)
    return output_path


def render_dark_talisman_hunt_source_maps(output_dir: Path, scale: int) -> list[Path]:
    """Render the vanilla Sewer -> BugLand hunt anchors without inferring Minecraft coordinates."""
    output_dir.mkdir(parents=True, exist_ok=True)
    step = TILE_SIZE * scale
    outputs: list[Path] = []

    sewer = render_tmx(MAPS_DIR / "Sewer.tmx", scale=scale)
    sewer_crop = (0, 10, 40, 25)
    sewer_base = sewer.crop(tuple(value * step for value in sewer_crop))
    sewer_base = draw_tile_grid(sewer_base, scale, sewer_crop[0], sewer_crop[1])
    sewer_points = [
        ("BugLand warp + MagicalSeal touch tile (3,18)", 3.5, 18.5),
        ("Krobus unseal spell source used by vanilla code (31,17)", 31.5, 17.5),
    ]
    sewer_annotated = draw_numbered_source_points(
        sewer_base,
        sewer_points,
        (sewer_crop[0], sewer_crop[1]),
        scale,
        "Dark Talisman hunt / Vanilla Sewer anchors",
        route=False,
    )
    sewer_output = output_dir / "dark_talisman_hunt_sewer_vanilla_source_points.png"
    sewer_annotated.save(sewer_output)
    outputs.append(sewer_output)

    bugland = render_tmx(MAPS_DIR / "BugLand.tmx", scale=scale)
    north_crop = (22, 0, 40, 13)
    north_base = bugland.crop(tuple(value * step for value in north_crop))
    north_base = draw_tile_grid(north_base, scale, north_crop[0], north_crop[1])
    north_annotated = draw_numbered_source_points(
        north_base,
        [("Dark Talisman reward chest (31,5)", 31.5, 5.5)],
        (north_crop[0], north_crop[1]),
        scale,
        "Dark Talisman hunt / Vanilla BugLand reward",
        route=False,
    )
    north_output = output_dir / "dark_talisman_hunt_bugland_reward_vanilla_source_points.png"
    north_annotated.save(north_output)
    outputs.append(north_output)

    south_crop = (8, 47, 23, 60)
    south_base = bugland.crop(tuple(value * step for value in south_crop))
    south_base = draw_tile_grid(south_base, scale, south_crop[0], south_crop[1])
    south_points = [
        ("Arrival from Sewer (15,53)", 15.5, 53.5),
        ("Return warp to Sewer (14,55)", 14.5, 55.5),
        ("Return warp to Sewer (15,55)", 15.5, 55.5),
    ]
    south_annotated = draw_numbered_source_points(
        south_base,
        south_points,
        (south_crop[0], south_crop[1]),
        scale,
        "Dark Talisman hunt / Vanilla BugLand entrance",
        route=False,
    )
    south_output = output_dir / "dark_talisman_hunt_bugland_entrance_vanilla_source_points.png"
    south_annotated.save(south_output)
    outputs.append(south_output)
    return outputs


def render_minecraft_capture_workbook(
    output_path: Path,
    title: str,
    subtitle: str,
    entries: list[tuple[str, str, str]],
) -> Path:
    """Render a blank coordinate checklist; values always come from the user's planning tool."""
    output_path.parent.mkdir(parents=True, exist_ok=True)
    width = 1120
    height = 148 + len(entries) * 66
    image = Image.new("RGB", (width, height), (229, 240, 249))
    draw = ImageDraw.Draw(image)
    title_font = load_font(30)
    body_font = load_font(20)
    small_font = load_font(16)
    draw.text((42, 26), title, fill=(28, 42, 57), font=title_font)
    draw.text((42, 70), subtitle, fill=(76, 96, 115), font=small_font)
    for index, (point_id, label, capture) in enumerate(entries):
        y = 112 + index * 66
        draw.rounded_rectangle(
            (42, y, width - 42, y + 52),
            radius=8,
            fill=(248, 250, 252),
            outline=(104, 130, 154),
            width=2,
        )
        draw.ellipse((54, y + 9, 88, y + 43), fill=(255, 196, 55), outline=(157, 112, 19), width=2)
        id_box = draw.textbbox((0, 0), point_id, font=small_font)
        draw.text(
            (71 - (id_box[2] - id_box[0]) / 2, y + 16),
            point_id,
            fill=(45, 40, 24),
            font=small_font,
        )
        draw.text((102, y + 7), label, fill=(35, 49, 63), font=body_font)
        draw.text((102, y + 31), capture, fill=(92, 109, 124), font=small_font)
    image.save(output_path)
    return output_path


def render_museum_lost_books_source_map(output_dir: Path, scale: int) -> Path:
    """Render the 21 vanilla ArchaeologyHouse `Notes N` action tiles."""
    map_path = MAPS_DIR / "ArchaeologyHouse.tmx"
    root = ET.parse(map_path).getroot()
    source_points: dict[int, set[tuple[int, int]]] = {}
    for group in root.findall("objectgroup"):
        for obj in group.findall("object"):
            properties = obj.find("properties")
            if properties is None:
                continue
            action = next((
                prop.get("value", "")
                for prop in properties.findall("property")
                if prop.get("name") == "Action"
            ), "")
            match = re.fullmatch(r"Notes (\d+)", action)
            if match is None:
                continue
            note = int(match.group(1))
            tile = (
                int(float(obj.get("x", "0")) // TILE_SIZE),
                int(float(obj.get("y", "0")) // TILE_SIZE),
            )
            source_points.setdefault(note, set()).add(tile)
    if set(source_points) != set(range(21)):
        raise ValueError(
            f"ArchaeologyHouse expected Notes 0-20, found {sorted(source_points)}")
    duplicates = {
        note: sorted(tiles)
        for note, tiles in source_points.items()
        if len(tiles) != 1
    }
    if duplicates:
        raise ValueError(f"Notes actions have conflicting source tiles: {duplicates}")
    points = [
        (f"Book {note}", *next(iter(source_points[note])))
        for note in range(21)
    ]
    crop_x0, crop_y0, crop_x1, crop_y1 = 6, 2, 24, 16
    full = render_tmx(map_path, scale=scale)
    step = TILE_SIZE * scale
    cropped = full.crop((
        crop_x0 * step,
        crop_y0 * step,
        crop_x1 * step,
        crop_y1 * step,
    ))
    annotated = draw_numbered_source_points(
        cropped,
        points,
        (crop_x0, crop_y0),
        scale,
        "Museum Lost Books / vanilla Notes 0-20 action tiles",
        start_index=0,
    )
    output_dir.mkdir(parents=True, exist_ok=True)
    output_path = output_dir / "museum_lost_books_vanilla_source_points.png"
    annotated.save(output_path)
    return output_path


def render_museum_lost_books_capture_workbook(output_dir: Path) -> Path:
    entries = [
        (str(index), f"Book {index} shelf interaction", "block x/y/z")
        for index in range(21)
    ]
    return render_minecraft_capture_workbook(
        output_dir / "museum_lost_books_minecraft_capture_workbook.png",
        "Museum Lost Books - Minecraft capture workbook",
        "Match each numbered vanilla Notes tile; do not infer coordinates from the source map.",
        entries,
    )


def render_linus_heart_event_capture_workbooks(output_dir: Path) -> list[Path]:
    common_subtitle = (
        "Fill only from the in-game planning tool. Minecraft coordinates/cameras are never "
        "derived from vanilla tile coordinates."
    )
    early_event_entries = [
        ("B01", "Town event trigger region corner 1", "block x/y/z + dimension"),
        ("B02", "Town event trigger region corner 2", "block x/y/z"),
        ("P01", "Player start, facing east", "x/y/z + direction"),
        ("P02", "Player stop for George", "x/y/z + direction"),
        ("P03", "Player stop for Linus conversation", "x/y/z + direction"),
        ("P04", "Player exit/path endpoint", "x/y/z + direction"),
        ("L01", "Linus initial position", "x/y/z + direction"),
        ("L02", "Linus first garbage-can position", "x/y/z + direction"),
        ("L03", "Linus startled/path stop", "x/y/z + direction"),
        ("L04", "Linus confession position", "x/y/z + direction"),
        ("L05", "Linus second garbage-can position", "x/y/z + direction"),
        ("L06", "Linus reaction position near Gus", "x/y/z + direction"),
        ("G01", "George doorway/appearance position", "x/y/z + direction"),
        ("U01", "Gus doorway/appearance position", "x/y/z + direction"),
        ("U02", "Gus route corner", "x/y/z + direction"),
        ("U03", "Gus dialogue stop", "x/y/z + direction"),
        ("FX1", "First garbage-can sound/light effect anchor", "x/y/z"),
        ("FX2", "Second garbage-can sound/light effect anchor", "x/y/z"),
        ("C01", "Initial fixed camera", "camera command"),
        ("C02", "Second-can camera pan endpoint", "camera command"),
    ]
    four_heart_entries = [
        ("B01", "Mountain event trigger region corner 1", "block x/y/z + dimension"),
        ("B02", "Mountain event trigger region corner 2", "block x/y/z"),
        ("P01", "Player start, facing east", "x/y/z + direction"),
        ("P02", "Player invitation stop", "x/y/z + direction"),
        ("P03", "Player fireside stop", "x/y/z + direction"),
        ("P04", "Player tent-entry path endpoint", "x/y/z + direction"),
        ("P05", "Player reward return position", "x/y/z + direction"),
        ("L01", "Linus initial position", "x/y/z + direction"),
        ("L02", "Linus fireside stop", "x/y/z + direction"),
        ("L03", "Linus tent-entry endpoint", "x/y/z + direction"),
        ("FX1", "Campfire center/effect anchor", "x/y/z"),
        ("C01", "Fixed event camera", "camera command"),
    ]
    eight_heart_entries = [
        ("B01", "Mountain event trigger region corner 1", "block x/y/z + dimension"),
        ("B02", "Mountain event trigger region corner 2", "block x/y/z"),
        ("L01", "Linus initial work position", "x/y/z + direction"),
        ("FX1", "First chopping/action anchor", "x/y/z"),
        ("L02", "Linus second work position", "x/y/z + direction"),
        ("FX2", "Second chopping/action anchor", "x/y/z"),
        ("L03", "Linus cabin-side dialogue stop", "x/y/z + direction"),
        ("L04", "Linus first answer-branch stop", "x/y/z + direction"),
        ("L05", "Linus second answer-branch stop", "x/y/z + direction"),
        ("L06", "Linus berry-exit endpoint", "x/y/z + direction"),
        ("R01", "Robin doorway/appearance position", "x/y/z + direction"),
        ("R02", "Robin dialogue stop", "x/y/z + direction"),
        ("P01", "Player doorway/appearance position", "x/y/z + direction"),
        ("P02", "Player dialogue stop", "x/y/z + direction"),
        ("C01", "Initial fixed camera", "camera command"),
        ("C02", "Four-second camera pan endpoint", "camera command"),
    ]
    return [
        render_minecraft_capture_workbook(
            output_dir / "linus_50point_minecraft_capture_workbook.png",
            "Linus 50-point event - Minecraft capture workbook",
            common_subtitle,
            early_event_entries,
        ),
        render_minecraft_capture_workbook(
            output_dir / "linus_4heart_minecraft_capture_workbook.png",
            "Linus 4-heart - Minecraft capture workbook",
            common_subtitle,
            four_heart_entries,
        ),
        render_minecraft_capture_workbook(
            output_dir / "linus_8heart_minecraft_capture_workbook.png",
            "Linus 8-heart - Minecraft capture workbook",
            common_subtitle,
            eight_heart_entries,
        ),
    ]


def render_george_heart_event_capture_workbook(output_dir: Path) -> Path:
    entries = [
        ("B01", "JoshHouse event trigger region corner 1", "block x/y/z + dimension"),
        ("B02", "JoshHouse event trigger region corner 2", "block x/y/z"),
        ("P01", "Player start, facing east", "x/y/z + direction"),
        ("P02", "Player first route corner", "x/y/z + direction"),
        ("P03", "Player second route corner", "x/y/z + direction"),
        ("P04", "Player reaches shelf", "x/y/z + direction"),
        ("P05", "Player final dialogue step", "x/y/z + direction"),
        ("G01", "George initial position", "x/y/z + direction"),
        ("G02", "George story position", "x/y/z + direction"),
        ("C01", "Fixed event camera", "camera command"),
    ]
    return render_minecraft_capture_workbook(
        output_dir / "george_6heart_minecraft_capture_workbook.png",
        "George 6-heart - Minecraft capture workbook",
        "Fill only from the in-game planning tool; never derive Minecraft coordinates from vanilla tiles.",
        entries,
    )


def render_linus_schedule_source_maps(output_dir: Path, scale: int) -> list[Path]:
    """Render every unique vanilla Linus schedule tile without mapping it to Minecraft."""
    schedule_path = SCHEDULES_DIR / "Linus.json"
    schedules = json.loads(schedule_path.read_text(encoding="utf-8-sig"))
    expected_fragments = {
        "rain": "700 Tent 2 2 0/930 Mountain 25 5 1/1010 Tent 3 2 2/1500 Mountain 17 8 2/1900 Tent 2 2 2",
        "GreenRain": "610 Mountain 34 15 2/1200 Mountain 39 5 1/1700 Mountain 30 9 3/2200 bed",
        "DesertFestival_2": "610 Tent 2 2 0/700 Desert 32 8 1",
        "winter_15": "1100 Mountain 28 9 1/1600 Beach 19 4 2",
        "summer": "630 Mountain 39 5 1/940 Mountain 44 27 1",
        "fall": "700 Mountain 25 5 1/740 Mountain 28 9 1/900 Railroad 20 57 2",
        "winter": "1100 Mountain 28 9 1/1400 BathHouse_Entry 8 6 3",
        "spring": "630 Mountain 25 5 1/700 Mountain 28 9 1/930 Mountain 45 19 1",
    }
    for key, fragment in expected_fragments.items():
        if fragment not in schedules.get(key, ""):
            raise ValueError(
                f"Vanilla Linus schedule source changed for {key!r}; "
                f"missing {fragment!r}."
            )

    map_points: dict[str, list[tuple[str, float, float]]] = {
        "Mountain": [
            ("M01 (25,5) rain/spring/summer/fall", 25.5, 5.5),
            ("M02 (17,8) rain afternoon", 17.5, 8.5),
            ("M03 (34,15) Green Rain morning", 34.5, 15.5),
            ("M04 (39,5) Green Rain/summer", 39.5, 5.5),
            ("M05 (30,9) Green Rain evening", 30.5, 9.5),
            ("M06 (28,9) seasonal camp position", 28.5, 9.5),
            ("M07 (44,27) summer wandering", 44.5, 27.5),
            ("M08 (48,35) summer wandering", 48.5, 35.5),
            ("M09 (44,18) fall afternoon", 44.5, 18.5),
            ("M10 (45,19) spring morning", 45.5, 19.5),
        ],
        "Tent": [
            ("T01 (2,2) default tent position", 2.5, 2.5),
            ("T02 (3,2) tent standing position", 3.5, 2.5),
            ("T03 (1,3) spring late-evening position", 1.5, 3.5),
            ("T04 (4,4) sleep position", 4.5, 4.5),
        ],
        "Railroad": [
            ("R01 (20,57) fall forage area", 20.5, 57.5),
        ],
        "BathHouse_Entry": [
            ("B01 (8,6) winter warming position", 8.5, 6.5),
        ],
        "Beach": [
            ("S01 (19,4) Night Market fishing position", 19.5, 4.5),
        ],
        "Desert": [
            ("D01 (32,8) Desert Festival day 2", 32.5, 8.5),
        ],
    }
    crops = {
        "Mountain": (13, 2, 52, 39),
        "Tent": (0, 0, 8, 8),
        "Railroad": (14, 51, 27, 63),
        "BathHouse_Entry": (0, 0, 16, 13),
        "Beach": (13, 0, 27, 12),
        "Desert": (25, 2, 40, 16),
    }

    output_dir.mkdir(parents=True, exist_ok=True)
    outputs: list[Path] = []
    step = TILE_SIZE * scale
    next_point_number = 1
    for map_name, points in map_points.items():
        crop = crops[map_name]
        base = render_tmx(MAPS_DIR / f"{map_name}.tmx", scale=scale)
        base = base.crop(tuple(value * step for value in crop))
        base = draw_tile_grid(base, scale, crop[0], crop[1])
        annotated = draw_numbered_source_points(
            base,
            points,
            (crop[0], crop[1]),
            scale,
            f"Linus vanilla schedule / {map_name} / unique stop tiles",
            start_index=next_point_number,
        )
        next_point_number += len(points)
        output_path = output_dir / (
            f"linus_schedule_{map_name.lower()}_vanilla_source_points.png"
        )
        annotated.save(output_path)
        outputs.append(output_path)
    return outputs


def render_linus_schedule_capture_workbook(output_dir: Path) -> Path:
    entries = [
        ("1", "Mountain (25,5): rain/spring/summer/fall", "x/y/z + east"),
        ("2", "Mountain (17,8): rain afternoon", "x/y/z + south"),
        ("3", "Mountain (34,15): Green Rain morning", "x/y/z + south"),
        ("4", "Mountain (39,5): Green Rain/summer", "x/y/z + east"),
        ("5", "Mountain (30,9): Green Rain evening", "x/y/z + west"),
        ("6", "Mountain (28,9): seasonal camp position", "x/y/z + east"),
        ("7", "Mountain (44,27): summer wandering", "x/y/z + east"),
        ("8", "Mountain (48,35): summer wandering", "x/y/z + south"),
        ("9", "Mountain (44,18): fall afternoon", "x/y/z + east"),
        ("10", "Mountain (45,19): spring morning", "x/y/z + east"),
        ("11", "Tent (2,2): default tent position", "x/y/z + north/south"),
        ("12", "Tent (3,2): tent standing position", "x/y/z + east/south"),
        ("13", "Tent (1,3): spring late-evening position", "x/y/z + south"),
        ("14", "Tent (4,4): sleep position", "x/y/z + south + sleep"),
        ("15", "Railroad (20,57): fall forage area", "x/y/z + south"),
        ("16", "BathHouse Entry (8,6): winter warming position", "x/y/z + west"),
        ("17", "Beach (19,4): Night Market fishing position", "x/y/z + south"),
        ("18", "Desert (32,8): Desert Festival day 2", "x/y/z + east"),
    ]
    return render_minecraft_capture_workbook(
        output_dir / "linus_schedule_minecraft_capture_workbook.png",
        "Linus schedule - Minecraft capture workbook",
        "Numbering is continuous across every map; fill only from the in-game planning tool.",
        entries,
    )


def render_george_schedule_source_maps(output_dir: Path, scale: int) -> list[Path]:
    """Render every unique vanilla George schedule tile without Minecraft mapping."""
    schedule_path = SCHEDULES_DIR / "George.json"
    schedules = json.loads(schedule_path.read_text(encoding="utf-8-sig"))
    expected_fragments = {
        "rain": "630 JoshHouse 16 22 0/1200 JoshHouse 5 21 3/1500 JoshHouse 16 22 0/2000 JoshHouse 3 5 0 george_sleep",
        "GreenRain": "0 JoshHouse 3 18 1",
        "DesertFestival_3": "610 JoshHouse 16 22 0/a1000 Desert 28 31 0 \"Strings\\1_6_Strings:DesertFestival_George\"/2250 bed",
        "23": "630 JoshHouse 5 21 3 \"Strings\\schedules\\George:23.000\"/1030 Hospital 10 15 0 \"Strings\\schedules\\George:23.001\"/1330 Hospital 4 6 1 \"Strings\\schedules\\George:23.002\"/1600 JoshHouse 16 22 0/2000 JoshHouse 3 5 0 george_sleep",
        "winter_17": "630 JoshHouse 16 22 0/1200 JoshHouse 5 21 3/1620 Beach 11 39 2 \"Strings\\schedules\\George:winter_17.000\"/2340 JoshHouse 3 5 0 george_sleep",
        "summer_Fri": "630 JoshHouse 16 22 0/1200 Town 52 61 2/1500 JoshHouse 16 22 0/2000 JoshHouse 3 5 0 george_sleep",
        "Sun": "MAIL saloonSportsRoom/GOTO Sun_normal/630 JoshHouse 16 22 0/1100 Saloon 36 7 0 \"Strings\\schedules\\George:Sun.001\"/1500 JoshHouse 16 22 0/2000 JoshHouse 3 5 0 george_sleep",
        "Sun_normal": "630 JoshHouse 16 22 0/1000 SeedShop 36 22 0 \"Strings\\schedules\\George:Sun.000\"/1400 JoshHouse 16 22 0/2000 JoshHouse 3 5 0 george_sleep",
        "spring": "630 JoshHouse 16 22 0/1200 JoshHouse 5 21 3/1500 JoshHouse 16 22 0/2000 JoshHouse 3 5 0 george_sleep",
    }
    for key, fragment in expected_fragments.items():
        if fragment not in schedules.get(key, ""):
            raise ValueError(
                f"Vanilla George schedule source changed for {key!r}; "
                f"missing {fragment!r}."
            )

    map_points: dict[str, list[tuple[str, float, float]]] = {
        "JoshHouse": [
            ("1 (16,22) TV/default, north", 16.5, 22.5),
            ("2 (5,21) kitchen, west", 5.5, 21.5),
            ("3 (3,5) bed, north", 3.5, 5.5),
            ("4 (3,18) Green Rain, east", 3.5, 18.5),
        ],
        "Hospital": [
            ("5 (10,15) appointment waiting, north", 10.5, 15.5),
            ("6 (4,6) exam room, east", 4.5, 6.5),
        ],
        "Beach": [
            ("7 (11,39) Night Market, south", 11.5, 39.5),
        ],
        "Town": [
            ("8 (52,61) summer Friday, south", 52.5, 61.5),
        ],
        "SeedShop": [
            ("9 (36,22) normal Sunday, north", 36.5, 22.5),
        ],
        "Saloon": [
            ("10 (36,7) sports-room Sunday, north", 36.5, 7.5),
        ],
        "Desert": [
            ("11 (28,31) Desert Festival day 3, north", 28.5, 31.5),
        ],
    }
    crops = {
        "JoshHouse": (0, 2, 22, 25),
        "Hospital": (0, 2, 15, 19),
        "Beach": (4, 32, 19, 47),
        "Town": (45, 55, 60, 69),
        "SeedShop": (29, 16, 44, 29),
        "Saloon": (29, 1, 44, 14),
        "Desert": (21, 24, 36, 38),
    }

    output_dir.mkdir(parents=True, exist_ok=True)
    outputs: list[Path] = []
    step = TILE_SIZE * scale
    next_point_number = 1
    for map_name, points in map_points.items():
        crop = crops[map_name]
        base = render_tmx(MAPS_DIR / f"{map_name}.tmx", scale=scale)
        base = base.crop(tuple(value * step for value in crop))
        base = draw_tile_grid(base, scale, crop[0], crop[1])
        annotated = draw_numbered_source_points(
            base,
            points,
            (crop[0], crop[1]),
            scale,
            f"George vanilla schedule / {map_name} / unique stop tiles",
            start_index=next_point_number,
        )
        next_point_number += len(points)
        output_path = output_dir / (
            f"george_schedule_{map_name.lower()}_vanilla_source_points.png"
        )
        annotated.save(output_path)
        outputs.append(output_path)
    return outputs


def render_george_schedule_capture_workbook(output_dir: Path) -> Path:
    entries = [
        ("1", "JoshHouse (16,22): TV/default", "x/y/z + north"),
        ("2", "JoshHouse (5,21): kitchen", "x/y/z + west"),
        ("3", "JoshHouse (3,5): bed", "x/y/z + north + sleep"),
        ("4", "JoshHouse (3,18): Green Rain", "x/y/z + east"),
        ("5", "Hospital (10,15): appointment waiting", "x/y/z + north"),
        ("6", "Hospital (4,6): exam room", "x/y/z + east"),
        ("7", "Beach (11,39): Night Market", "x/y/z + south"),
        ("8", "Town (52,61): summer Friday", "x/y/z + south"),
        ("9", "SeedShop (36,22): normal Sunday", "x/y/z + north"),
        ("10", "Saloon (36,7): sports-room Sunday", "x/y/z + north"),
        ("11", "Desert (28,31): Desert Festival day 3", "x/y/z + north"),
    ]
    return render_minecraft_capture_workbook(
        output_dir / "george_schedule_minecraft_capture_workbook.png",
        "George schedule - Minecraft capture workbook",
        "Numbering is continuous across every map; fill only from the in-game planning tool.",
        entries,
    )


def render_secret_note10_capture_workbook(output_dir: Path) -> Path:
    entries = [
        ("Q01", "Floor-100 scene area corner 1", "block x/y/z"),
        ("Q02", "Floor-100 scene area corner 2", "block x/y/z"),
        ("Q03", "Player start", "x/y/z + facing"),
        ("Q04", "Player first route corner", "x/y/z"),
        ("Q05", "Player dialogue stop", "x/y/z + facing"),
        ("Q06", "Player second route corner", "x/y/z"),
        ("Q07", "Player milk-drinking position", "x/y/z + facing"),
        ("Q08", "Mr Qi standing position", "x/y/z + facing"),
        ("Q09", "Table and milk visual anchor", "x/y/z + facing"),
        ("Q10", "Camera rig", "exact x/y/z + yaw + pitch"),
    ]
    return render_minecraft_capture_workbook(
        output_dir / "secret_note10_minecraft_capture_workbook.png",
        "Secret Note 10 - Minecraft capture workbook",
        "Every value is intentionally blank. Supply only coordinates captured from the in-game planning tool.",
        entries,
    )


def render_secret_note23_capture_workbook(output_dir: Path) -> Path:
    entries = [
        ("B01", "Secret Woods event trigger area corner 1", "block x/y/z"),
        ("B02", "Secret Woods event trigger area corner 2", "block x/y/z"),
        ("B03", "Player start", "x/y/z + facing"),
        ("B04", "Player notice stop", "x/y/z + facing"),
        ("B05", "Player dialogue and syrup-presentation stop", "x/y/z + facing"),
        ("B06", "Bear fixed position (idle only)", "x/y/z + facing"),
        ("B07", "Maple syrup visual anchor", "x/y/z"),
        ("B08", "Camera rig", "exact x/y/z + yaw + pitch"),
    ]
    return render_minecraft_capture_workbook(
        output_dir / "secret_note23_minecraft_capture_workbook.png",
        "Secret Note 23 - Minecraft capture workbook",
        "Bear stays fixed and uses idle only. Every Minecraft coordinate is intentionally blank.",
        entries,
    )


def render_wizard_dark_talisman_capture_workbook(output_dir: Path) -> Path:
    entries = [
        ("D01", "Event trigger area corner 1", "block x/y/z"),
        ("D02", "Event trigger area corner 2", "block x/y/z"),
        ("D03", "Player cutscene start", "x/y/z + facing"),
        ("D04", "Player route corner", "x/y/z + facing"),
        ("D05", "Player dialogue stop", "x/y/z + facing"),
        ("D06", "Wizard main dialogue position", "x/y/z + facing"),
        ("D07", "Wizard ink-emphasis step", "x/y/z + facing"),
        ("D08", "Dark-talisman seal reference anchor", "block x/y/z + facing; existing point may be reused"),
        ("D09", "Opening camera rig", "exact x/y/z + yaw + pitch"),
        ("D10", "Dialogue two-shot camera rig", "exact x/y/z + yaw + pitch"),
        ("D11", "Seal insert camera rig", "exact x/y/z + yaw + pitch"),
        ("D12", "Wizard warp effect anchor", "x/y/z"),
        ("D13", "Witch flyby start outside frame", "x/y/z + facing"),
        ("D14", "Witch flyby end outside frame", "x/y/z + facing"),
        ("D15", "Witch flyby camera rig", "exact x/y/z + yaw + pitch"),
        ("D16", "Player position after cutscene", "x/y/z + facing"),
    ]
    return render_minecraft_capture_workbook(
        output_dir / "wizard_dark_talisman_minecraft_capture_workbook.png",
        "Dark Talisman opening - Minecraft capture workbook",
        "Every new runtime coordinate is intentionally blank until supplied with the in-game planning tool.",
        entries,
    )


def render_wizard_magic_ink_capture_workbook(output_dir: Path) -> Path:
    entries = [
        ("I01", "Return-event trigger area corner 1", "block x/y/z"),
        ("I02", "Return-event trigger area corner 2", "block x/y/z"),
        ("I03", "Player fixed cutscene position", "x/y/z + facing"),
        ("I04", "Wizard main dialogue position", "x/y/z + facing"),
        ("I05", "Wizard question step", "x/y/z + facing"),
        ("I06", "Summoned book visual/effect anchor", "x/y/z + facing"),
        ("I07", "Permanent summoning-book interaction block", "block x/y/z + facing"),
        ("I08", "Opening two-shot camera rig", "exact x/y/z + yaw + pitch"),
        ("I09", "Book-summoning insert camera rig", "exact x/y/z + yaw + pitch"),
        ("I10", "Player position after cutscene", "x/y/z + facing"),
    ]
    return render_minecraft_capture_workbook(
        output_dir / "wizard_magic_ink_minecraft_capture_workbook.png",
        "Magic Ink return - Minecraft capture workbook",
        "Every Minecraft coordinate is intentionally blank until supplied with the in-game planning tool.",
        entries,
    )


def render_dark_talisman_hunt_capture_workbook(output_dir: Path) -> Path:
    entries = [
        ("T01", "Sewer sealed entrance trigger area corner 1", "block x/y/z"),
        ("T02", "Sewer sealed entrance trigger area corner 2", "block x/y/z"),
        ("T03", "Sewer seal / unseal effect impact anchor", "block x/y/z + facing"),
        ("T04", "Mutant Bug Lair arrival position", "x/y/z + facing"),
        ("T05", "Mutant Bug Lair return trigger area corner 1", "block x/y/z"),
        ("T06", "Mutant Bug Lair return trigger area corner 2", "block x/y/z"),
        ("T07", "Sewer return position", "x/y/z + facing"),
        ("T08", "Dark Talisman reward chest", "block x/y/z + facing"),
    ]
    return render_minecraft_capture_workbook(
        output_dir / "dark_talisman_hunt_minecraft_capture_workbook.png",
        "Dark Talisman hunt - Minecraft capture workbook",
        "Every runtime coordinate is intentionally blank until supplied with the in-game planning tool.",
        entries,
    )


def render_secret_note_core_capture_workbook(output_dir: Path) -> Path:
    entries = [
        ("12A", "Evelyn/Mullner named garbage can", "block x/y/z"),
        ("12B", "Saloon named garbage can", "block x/y/z"),
        ("12C", "Blacksmith named garbage can", "block x/y/z"),
        ("12D", "Museum named garbage can", "block x/y/z"),
        ("17", "Secret Note 17 dig block", "block x/y/z"),
    ]
    return render_minecraft_capture_workbook(
        output_dir / "secret_note12_17_minecraft_capture_workbook.png",
        "Secret Notes 12 and 17 - Minecraft capture workbook",
        "Vanilla source anchors are shown in separate maps; this sheet contains no inferred mapping.",
        entries,
    )


def render_secret_note31_capture_workbooks(output_dir: Path) -> list[Path]:
    """Create blank Minecraft capture checklists. The user supplies every value."""
    output_dir.mkdir(parents=True, exist_ok=True)
    groups = [
        ("Scene A - Bus Stop encounter", [
            ("A01", "Trigger area corner 1", "block x/y/z"),
            ("A02", "Trigger area corner 2", "block x/y/z"),
            ("A03", "Player start", "x/y/z + facing"),
            ("A04", "Player follow endpoint", "x/y/z + facing"),
            ("A05", "Shadow start", "x/y/z + facing"),
            ("A06", "Shadow notice + jump stop", "x/y/z + facing"),
            ("A07", "Shadow escape endpoint outside frame", "x/y/z + facing"),
            ("A08", "Camera rig", "exact x/y/z + yaw + pitch"),
        ]),
        ("Scene B - Snow footprints", [
            (f"B{index:02d}", f"Footprint {index:02d} in route order", "surface x/y/z + facing")
            for index in range(1, 31)
        ]),
        ("Scene C - Bush finale", [
            ("C01", "Interactive bush block", "block x/y/z"),
            ("C02", "Shadow spawn inside bush", "x/y/z + facing"),
            ("C03", "Player reference position, branch 1", "x/y/z + facing"),
            ("C04", "Shadow landing, branch 1", "x/y/z + facing"),
            ("C05", "Shadow escape endpoint, branch 1", "x/y/z + facing"),
            ("C06", "Camera rig, branch 1", "exact x/y/z + yaw + pitch"),
            ("C07", "Player reference position, branch 2", "x/y/z + facing"),
            ("C08", "Shadow landing, branch 2", "x/y/z + facing"),
            ("C09", "Shadow escape endpoint, branch 2", "x/y/z + facing"),
            ("C10", "Camera rig, branch 2", "exact x/y/z + yaw + pitch"),
        ]),
    ]
    outputs: list[Path] = []
    for group_index, (title, entries) in enumerate(groups, start=1):
        rows_per_column = 15
        columns = math.ceil(len(entries) / rows_per_column)
        width = 820 if columns == 1 else 1420
        height = 150 + min(rows_per_column, len(entries)) * 66
        image = Image.new("RGB", (width, height), (229, 240, 249))
        draw = ImageDraw.Draw(image)
        title_font = load_font(30)
        body_font = load_font(20)
        small_font = load_font(16)
        draw.text((42, 28), f"Secret Note 31 - {title}", fill=(28, 42, 57), font=title_font)
        draw.text((42, 72), "All Minecraft coordinates are intentionally blank until supplied with the planning tool.",
                  fill=(76, 96, 115), font=small_font)
        column_width = (width - 84) // columns
        for index, (point_id, label, capture) in enumerate(entries):
            column = index // rows_per_column
            row = index % rows_per_column
            x = 42 + column * column_width
            y = 116 + row * 66
            draw.rounded_rectangle((x, y, x + column_width - 22, y + 52), radius=8,
                                   fill=(248, 250, 252), outline=(104, 130, 154), width=2)
            draw.ellipse((x + 10, y + 9, x + 44, y + 43), fill=(255, 196, 55), outline=(157, 112, 19), width=2)
            id_box = draw.textbbox((0, 0), point_id, font=small_font)
            draw.text((x + 27 - (id_box[2] - id_box[0]) / 2, y + 16), point_id,
                      fill=(45, 40, 24), font=small_font)
            draw.text((x + 56, y + 7), label, fill=(35, 49, 63), font=body_font)
            draw.text((x + 56, y + 31), capture, fill=(92, 109, 124), font=small_font)
        output_path = output_dir / f"secret_note31_minecraft_capture_workbook_{group_index}.png"
        image.save(output_path)
        outputs.append(output_path)
    return outputs


def extract_night_market_actors(day: int, scale: int) -> list[ActorMarker]:
    markers: list[ActorMarker] = []
    schedule_key = f"winter_{day}"
    for schedule_path in sorted(SCHEDULES_DIR.glob("*.json")):
        schedule = json.loads(schedule_path.read_text(encoding="utf-8-sig"))
        route = schedule.get(schedule_key)
        if not isinstance(route, str):
            continue
        for entry in route.split("/"):
            parts = shlex.split(entry)
            if len(parts) < 5 or parts[1].lower() != "beach":
                continue
            arrival_time = parts[0]
            tile_x = int(parts[2])
            tile_y = int(parts[3])
            facing = int(parts[4])
            name = schedule_path.stem
            portrait_name = "Leo" if name == "LeoMainland" else name
            markers.append(ActorMarker(
                name,
                tile_x,
                tile_y,
                facing,
                ((tile_x + 0.5) * TILE_SIZE * scale, (tile_y + 0.5) * TILE_SIZE * scale),
                [0.0, 0.0, 0.0, 0.0],
                f"{display_name(portrait_name)} {arrival_time}",
                portrait_name,
            ))
    return markers


def render_night_market_maps(output_dir: Path, scale: int, portrait_size: int) -> list[Path]:
    map_id = "Beach-NightMarket"
    tmx_path = MAPS_DIR / f"{map_id}.tmx"
    base = render_tmx(tmx_path, scale=scale)
    output_dir.mkdir(parents=True, exist_ok=True)
    outputs: list[Path] = []
    for day in (15, 16, 17):
        markers = extract_night_market_actors(day, scale)
        title = f"Night Market / Winter {day} / vanilla schedule points ({len(markers)} NPCs)"
        annotated = annotate_map(base, markers, title, portrait_size=portrait_size)
        output_path = output_dir / f"night_market_winter_{day}_npc_points.png"
        annotated.save(output_path)
        outputs.append(output_path)
    return outputs


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Render Stardew Valley festival NPC actor maps.")
    parser.add_argument(
        "--preset",
        choices=("moonlight_jellies", "night_market"),
        help="Convenience preset. night_market renders all three vanilla schedule maps.",
    )
    parser.add_argument("--festival", default=None, help="Festival data id, for example summer28.")
    parser.add_argument("--profile", choices=("y1", "y2"), default="y1", help="Festival profile/year variant.")
    parser.add_argument("--phase", choices=("setup", "main"), default="setup", help="Actor phase to render.")
    parser.add_argument("--all", action="store_true", help="Render y1/y2 setup/main images.")
    parser.add_argument("--scale", type=int, default=3, help="Pixel scale for the rendered TMX map.")
    parser.add_argument("--portrait-size", type=int, default=44, help="Portrait chip size in output pixels.")
    parser.add_argument("--out", type=Path, default=DEFAULT_OUTPUT_DIR, help="Output directory.")
    parser.add_argument("--winter-star-secret-santa", action="store_true",
                        help="Render the vanilla Y1 Secret Santa event-point map.")
    parser.add_argument("--secret-note-31", action="store_true",
                        help="Render vanilla source maps and blank Minecraft capture workbooks for Secret Note 31.")
    parser.add_argument("--secret-note-core", action="store_true",
                        help="Render vanilla source anchors for active Secret Notes 12 and 17.")
    parser.add_argument("--secret-note-10", action="store_true",
                        help="Render the vanilla Note 10 choreography and a blank Minecraft capture workbook.")
    parser.add_argument("--secret-note-23", action="store_true",
                        help="Render the vanilla Note 23 bear-event anchors and a blank Minecraft capture workbook.")
    parser.add_argument("--wizard-dark-talisman", action="store_true",
                        help="Render vanilla Dark Talisman opening anchors and a blank Minecraft capture workbook.")
    parser.add_argument("--wizard-magic-ink", action="store_true",
                        help="Render vanilla Magic Ink return anchors and a blank Minecraft capture workbook.")
    parser.add_argument("--dark-talisman-hunt", action="store_true",
                        help="Render vanilla Sewer/BugLand hunt anchors and a blank Minecraft capture workbook.")
    parser.add_argument("--museum-lost-books", action="store_true",
                        help="Render all 21 vanilla museum Notes tiles and a blank Minecraft capture workbook.")
    parser.add_argument("--linus-heart-events", action="store_true",
                        help="Render vanilla Linus 50-point/4/8-heart source maps and blank Minecraft capture workbooks.")
    parser.add_argument("--linus-schedule", action="store_true",
                        help="Render every unique vanilla Linus schedule stop and a blank Minecraft capture workbook.")
    parser.add_argument("--george-heart-events", action="store_true",
                        help="Render the vanilla George 6-heart source map and a blank Minecraft capture workbook.")
    parser.add_argument("--george-schedule", action="store_true",
                        help="Render every unique vanilla George schedule stop and a blank Minecraft capture workbook.")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if args.winter_star_secret_santa:
        output_path = render_winter_star_secret_santa_map(args.out, args.scale, args.portrait_size)
        print(output_path.relative_to(ROOT))
        return
    if args.secret_note_31:
        for output_path in render_secret_note31_source_maps(args.out, args.scale):
            print(output_path.relative_to(ROOT))
        for output_path in render_secret_note31_capture_workbooks(args.out):
            print(output_path.relative_to(ROOT))
        return
    if args.secret_note_core:
        for output_path in render_secret_note_core_source_maps(args.out, args.scale):
            print(output_path.relative_to(ROOT))
        print(render_secret_note_core_capture_workbook(args.out).relative_to(ROOT))
        return
    if args.secret_note_10:
        print(render_secret_note10_source_map(args.out, args.scale).relative_to(ROOT))
        print(render_secret_note10_capture_workbook(args.out).relative_to(ROOT))
        return
    if args.secret_note_23:
        print(render_secret_note23_source_map(args.out, args.scale).relative_to(ROOT))
        print(render_secret_note23_capture_workbook(args.out).relative_to(ROOT))
        return
    if args.wizard_dark_talisman:
        print(render_wizard_dark_talisman_source_map(args.out, args.scale).relative_to(ROOT))
        print(render_wizard_dark_talisman_capture_workbook(args.out).relative_to(ROOT))
        return
    if args.wizard_magic_ink:
        print(render_wizard_magic_ink_source_map(args.out, args.scale).relative_to(ROOT))
        print(render_wizard_magic_ink_capture_workbook(args.out).relative_to(ROOT))
        return
    if args.dark_talisman_hunt:
        for output_path in render_dark_talisman_hunt_source_maps(args.out, args.scale):
            print(output_path.relative_to(ROOT))
        print(render_dark_talisman_hunt_capture_workbook(args.out).relative_to(ROOT))
        return
    if args.museum_lost_books:
        print(render_museum_lost_books_source_map(args.out, args.scale).relative_to(ROOT))
        print(render_museum_lost_books_capture_workbook(args.out).relative_to(ROOT))
        return
    if args.linus_heart_events:
        for output_path in render_linus_heart_event_source_maps(args.out, args.scale):
            print(output_path.relative_to(ROOT))
        for output_path in render_linus_heart_event_capture_workbooks(args.out):
            print(output_path.relative_to(ROOT))
        return
    if args.linus_schedule:
        for output_path in render_linus_schedule_source_maps(args.out, args.scale):
            print(output_path.relative_to(ROOT))
        print(render_linus_schedule_capture_workbook(args.out).relative_to(ROOT))
        return
    if args.george_heart_events:
        print(render_george_heart_event_source_map(args.out, args.scale).relative_to(ROOT))
        print(render_george_heart_event_capture_workbook(args.out).relative_to(ROOT))
        return
    if args.george_schedule:
        for output_path in render_george_schedule_source_maps(args.out, args.scale):
            print(output_path.relative_to(ROOT))
        print(render_george_schedule_capture_workbook(args.out).relative_to(ROOT))
        return
    if args.preset == "night_market":
        for output_path in render_night_market_maps(args.out, args.scale, args.portrait_size):
            print(output_path.relative_to(ROOT))
        return
    festival_id = args.festival
    if args.preset == "moonlight_jellies":
        festival_id = "summer28"
    if not festival_id:
        raise SystemExit("Please pass --festival summer28 or --preset moonlight_jellies.")
    if args.scale <= 0:
        raise SystemExit("--scale must be positive.")
    if args.portrait_size < 24:
        raise SystemExit("--portrait-size must be at least 24.")

    jobs = [(args.profile, args.phase)]
    if args.all:
        jobs = [(profile, phase) for profile in ("y1", "y2") for phase in ("setup", "main")]

    for profile, phase in jobs:
        output_path = render_one(festival_id, profile, phase, args.out, args.scale, args.portrait_size)
        print(output_path.relative_to(ROOT))


if __name__ == "__main__":
    main()

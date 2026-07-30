#!/usr/bin/env python3
"""
Render visual approval references for fixed-map Message, Letter, and
NPCMessage actions.

This script deliberately renders only the original Stardew Valley TMX source
positions. Its output must never be interpreted as Minecraft coordinates.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import textwrap
import xml.etree.ElementTree as ET
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

from PIL import Image, ImageDraw

from render_festival_actor_map import (
    TILE_SIZE,
    draw_numbered_source_points,
    draw_tile_grid,
    load_font,
    render_tmx,
)


ROOT = Path(__file__).resolve().parents[1]
CONTENT_DIR = ROOT / "源文件" / "Content"
MAPS_DIR = CONTENT_DIR / "Maps"
STRINGS_DIR = CONTENT_DIR / "Strings"
DEFAULT_OUTPUT_DIR = (
    ROOT
    / ".workspace"
    / "docs"
    / "root-notes"
    / "assets"
    / "map-interaction-phase2"
)
RENDER_LAYERS = (
    "Back",
    "Back2",
    "Buildings",
    "Buildings2",
    "Front",
    "Front2",
    "Front3",
    "Paths",
    "AlwaysFront",
)
MAP_PREFIXES = {
    "AdventureGuild": "AG",
    "Trailer": "TR",
    "Trailer_big": "TB",
    "Hospital": "HO",
    "LeahHouse": "LE",
    "Saloon": "SA",
    "FishShop": "FI",
    "Blacksmith": "BL",
    "ElliottHouse": "EL",
    "SeedShop": "SE",
    "SamHouse": "SM",
    "JoshHouse": "JO",
    "HaleyHouse": "HA",
    "AnimalShop": "AN",
    "ScienceHouse": "SC",
    "ArchaeologyHouse": "AR",
    "Backwoods_GraveSite": "BG",
    "BathHouse_MensLocker": "BM",
    "BathHouse_WomensLocker": "BW",
    "BusStop": "BS",
    "Club": "CL",
    "Farm": "FA",
    "Farm_Combat": "FC",
    "Farm_Fishing": "FF",
    "Farm_Foraging": "FG",
    "Farm_FourCorners": "F4",
    "Farm_Island": "FIS",
    "Farm_Mining": "FM",
    "Farm_Ranching": "FR",
    "Forest-IceFestival": "IC",
    "Forest-IceFestival2": "IC2",
    "Forest": "FO",
    "HarveyRoom": "HR",
    "Island_Shrine": "IS",
    "JojaMart": "JM",
    "ManorHouse": "MH",
    "MasteryCave": "MC",
    "Mountain": "MO",
    "SebastianRoom": "SR",
    "Town-Christmas": "TC",
    "Town-Christmas2": "TC2",
    "Town-EggFestival": "TE",
    "Town-EggFestival2": "TE2",
    "Town-Fair": "TF",
    "Town-Fair2": "TF2",
    "Town-Halloween": "TH",
    "Town-Halloween2": "TH2",
    "Town": "TO",
    "WitchHut": "WH",
}
MAX_POINTS_PER_SHEET = 20
SIMPLE_TEXT_ACTION_RE = re.compile(
    r'(Message|Letter)\s+(?:"([^"]+)"|(\S+))')
SPECIAL_TEXT_ACTION_REFERENCES = {
    "DwarfGrave": (
        "Strings/Locations:Town_DwarfGrave_Translated",
        "Strings/StringsFromCSFiles:GameLocation.cs.8214",
    ),
    "MonsterGrave": (
        "Strings/Locations:Backwoods_MonsterGrave",
    ),
    "ElliottBook": (
        "Strings/Locations:ElliottHouse_ElliottBook_Blank",
    ),
    "GrandpaMasteryNote": (
        "Strings/1_6_Strings:GrandpaMasteryNote",
    ),
    "Yoba": (
        "Strings/Locations:SeedShop_Yoba",
    ),
}
DEFAULT_MAPS = (
    "Trailer",
    "Hospital",
    "LeahHouse",
    "Saloon",
    "FishShop",
    "Blacksmith",
    "ElliottHouse",
)
LOCALE_SUFFIXES = {
    "en_us": "",
    "zh_cn": ".zh-CN",
    "de_de": ".de-DE",
    "es_es": ".es-ES",
    "fr_fr": ".fr-FR",
    "hu_hu": ".hu-HU",
    "it_it": ".it-IT",
    "ja_jp": ".ja-JP",
    "ko_kr": ".ko-KR",
    "pt_br": ".pt-BR",
    "ru_ru": ".ru-RU",
    "tr_tr": ".tr-TR",
}


@dataclass(frozen=True)
class SourcePoint:
    ledger_id: str
    map_name: str
    key: str
    action_type: str
    layer: str
    tile_x: int
    tile_y: int
    english: str
    chinese: str
    references: tuple[str, ...]
    unresolved_references: tuple[str, ...]
    image_name: str

    @property
    def action(self) -> str:
        return self.key


def safe_name(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", "_", value.lower()).strip("_")


def approval_decision(
    manifest: Optional[dict],
    map_name: str,
    approval_index: int,
) -> str:
    if manifest is None:
        return "**待项目作者决策**"
    batch = next(
        (
            value
            for value in manifest.get("batches", [])
            if value.get("map") == map_name
        ),
        None,
    )
    if batch is None:
        return "**未在批准清单中找到该地图**"
    interaction = next(
        (
            value
            for value in batch.get("interactions", [])
            if str(value.get("approval_index")) == str(approval_index)
        ),
        None,
    )
    if interaction is None:
        omitted = next(
            (
                str(value)
                for value in batch.get("omitted", [])
                if re.match(
                    rf"^{approval_index}(?:\D|$)",
                    str(value),
                )
            ),
            None,
        )
        if omitted is None:
            return "**未在批准清单中找到该审批序号**"
        merged = re.search(r"merged into (\d+)", omitted)
        if merged is not None:
            return f"**不单独实现；并入审批 {merged.group(1)}**"
        return "**不做**"

    singles: list[tuple[int, int, int]] = []
    pairs: dict[str, dict[str, tuple[int, int, int]]] = {}
    for point in interaction.get("points", []):
        position = (point["x"], point["y"], point["z"])
        match = re.fullmatch(r"(.+)_([12])", point["name"])
        if match is None:
            singles.append(position)
            continue
        pairs.setdefault(match.group(1), {})[match.group(2)] = position

    regions = [
        f"单点 `({x}, {y}, {z})`"
        for x, y, z in singles
    ]
    for base_name, endpoints in pairs.items():
        first = endpoints.get("1")
        second = endpoints.get("2")
        if first is None or second is None:
            raise ValueError(
                f"{interaction['id']} has incomplete {base_name}_1/_2")
        minimum = tuple(min(a, b) for a, b in zip(first, second))
        maximum = tuple(max(a, b) for a, b in zip(first, second))
        regions.append(
            "闭区间方盒 "
            f"`({minimum[0]}, {minimum[1]}, {minimum[2]})` → "
            f"`({maximum[0]}, {maximum[1]}, {maximum[2]})`"
        )
    return f"`{interaction['id']}`：" + "；".join(regions)


def parse_simple_text_action(
    action: str,
) -> Optional[tuple[str, str]]:
    match = SIMPLE_TEXT_ACTION_RE.fullmatch(action)
    if match is None:
        return None
    return match.group(1), match.group(2) or match.group(3)


def load_text_sources(suffix: str) -> dict[str, dict[str, str]]:
    return {
        "Strings/StringsFromMaps": json.loads(
            (STRINGS_DIR / f"StringsFromMaps{suffix}.json").read_text(encoding="utf-8")
        ),
        "Strings/Locations": json.loads(
            (STRINGS_DIR / f"Locations{suffix}.json").read_text(encoding="utf-8")
        ),
        "Strings/Notes": json.loads(
            (STRINGS_DIR / f"Notes{suffix}.json").read_text(encoding="utf-8")
        ),
        "Strings/StringsFromCSFiles": json.loads(
            (
                STRINGS_DIR / f"StringsFromCSFiles{suffix}.json"
            ).read_text(encoding="utf-8")
        ),
        "Strings/1_6_Strings": json.loads(
            (
                STRINGS_DIR / f"1_6_Strings{suffix}.json"
            ).read_text(encoding="utf-8")
        ),
        "Data/ExtraDialogue": json.loads(
            (CONTENT_DIR / "Data" / f"ExtraDialogue{suffix}.json").read_text(encoding="utf-8")
        ),
    }


def normalize_content_path(value: str) -> str:
    normalized = value.replace("\\", "/")
    while "//" in normalized:
        normalized = normalized.replace("//", "/")
    return normalized


def resolve_reference(reference: str, sources: dict[str, dict[str, str]]) -> str:
    if ":" not in reference:
        raise ValueError(f"Text reference has no content path: {reference}")
    raw_path, key = reference.split(":", 1)
    content_path = normalize_content_path(raw_path)
    if content_path not in sources:
        raise KeyError(f"Unsupported text content path: {content_path}")
    if key not in sources[content_path]:
        raise KeyError(f"Missing {content_path}:{key}")
    return sources[content_path][key]


def resolve_reference_for_audit(
    reference: str,
    sources: dict[str, dict[str, str]],
    missing_label: str,
) -> tuple[str, bool]:
    try:
        return resolve_reference(reference, sources), False
    except KeyError:
        return f"[{missing_label}: {reference}]", True


def extract_source_points(
    map_name: str,
    ledger_prefix: str,
    english: dict[str, dict[str, str]],
    chinese: dict[str, dict[str, str]],
    action_types: set[str],
) -> list[SourcePoint]:
    root = ET.parse(MAPS_DIR / f"{map_name}.tmx").getroot()
    authored: list[dict[str, object]] = []
    prefix = MAP_PREFIXES[map_name]
    for group in root.findall("objectgroup"):
        layer = group.get("name", "")
        for obj in group.findall("object"):
            properties = {
                item.get("name", ""): item.get("value", "")
                for item in obj.findall("./properties/property")
            }
            parsed_text_action = parse_simple_text_action(
                properties.get("Action", ""))
            if (
                parsed_text_action is not None
                and parsed_text_action[0] in action_types
            ):
                action_type, key = parsed_text_action
                reference = (
                    normalize_content_path(key)
                    if ":" in key
                    else f"Strings/StringsFromMaps:{key}"
                )
                numeric_tail = key.rsplit(".", 1)[-1]
                english_text, english_missing = resolve_reference_for_audit(
                    reference,
                    english,
                    "Bundled original text is missing",
                )
                chinese_text, chinese_missing = resolve_reference_for_audit(
                    reference,
                    chinese,
                    "仓库原版本地化缺失",
                )
                authored.append(
                    {
                        "number": (
                            int(numeric_tail)
                            if numeric_tail.isdigit()
                            else None
                        ),
                        "is_npc_message": False,
                        "key": key,
                        "action_type": action_type,
                        "action": properties["Action"],
                        "layer": layer,
                        "tile_x": int(float(obj.get("x", "0")) / TILE_SIZE),
                        "tile_y": int(float(obj.get("y", "0")) / TILE_SIZE),
                        "english": english_text,
                        "chinese": chinese_text,
                        "references": (reference,),
                        "unresolved_references": (
                            (reference,)
                            if english_missing or chinese_missing
                            else ()
                        ),
                    }
                )
                continue

            notes_match = re.fullmatch(
                r"Notes\s+(\d+)",
                properties.get("Action", ""),
            )
            if notes_match is not None and "Notes" in action_types:
                note_id = int(notes_match.group(1))
                references = (
                    f"Strings/Notes:{note_id}",
                    "Strings/Notes:Missing",
                )
                english_texts = [
                    resolve_reference_for_audit(
                        reference,
                        english,
                        "Bundled original text is missing",
                    )
                    for reference in references
                ]
                chinese_texts = [
                    resolve_reference_for_audit(
                        reference,
                        chinese,
                        "仓库原版本地化缺失",
                    )
                    for reference in references
                ]
                authored.append(
                    {
                        "number": note_id,
                        "is_npc_message": False,
                        "key": f"Notes {note_id}",
                        "action_type": "Notes",
                        "action": properties["Action"],
                        "layer": layer,
                        "tile_x": int(
                            float(obj.get("x", "0")) / TILE_SIZE),
                        "tile_y": int(
                            float(obj.get("y", "0")) / TILE_SIZE),
                        "english": (
                            f"Book found: {english_texts[0][0]} / "
                            f"Book missing: {english_texts[1][0]}"
                        ),
                        "chinese": (
                            f"已找回书籍：{chinese_texts[0][0]} / "
                            f"尚未找回：{chinese_texts[1][0]}"
                        ),
                        "references": references,
                        "unresolved_references": tuple(
                            reference
                            for index, reference in enumerate(references)
                            if (
                                english_texts[index][1]
                                or chinese_texts[index][1]
                            )
                        ),
                    }
                )
                continue

            action = properties.get("Action", "")
            special_references = SPECIAL_TEXT_ACTION_REFERENCES.get(action)
            if (
                special_references is not None
                and action in action_types
            ):
                english_texts = [
                    resolve_reference_for_audit(
                        reference,
                        english,
                        "Bundled original text is missing",
                    )
                    for reference in special_references
                ]
                chinese_texts = [
                    resolve_reference_for_audit(
                        reference,
                        chinese,
                        "仓库原版本地化缺失",
                    )
                    for reference in special_references
                ]
                if action == "DwarfGrave":
                    english_text = (
                        f"Dwarvish understood: {english_texts[0][0]} / "
                        f"Not understood: {english_texts[1][0]}"
                    )
                    chinese_text = (
                        f"已理解矮人语：{chinese_texts[0][0]} / "
                        f"未理解矮人语：{chinese_texts[1][0]}"
                    )
                else:
                    english_text = english_texts[0][0]
                    chinese_text = chinese_texts[0][0]
                authored.append(
                    {
                        "number": None,
                        "is_npc_message": False,
                        "key": action,
                        "action_type": action,
                        "action": action,
                        "layer": layer,
                        "tile_x": int(
                            float(obj.get("x", "0")) / TILE_SIZE),
                        "tile_y": int(
                            float(obj.get("y", "0")) / TILE_SIZE),
                        "english": english_text,
                        "chinese": chinese_text,
                        "references": special_references,
                        "unresolved_references": tuple(
                            reference
                            for index, reference in enumerate(
                                special_references)
                            if (
                                english_texts[index][1]
                                or chinese_texts[index][1]
                            )
                        ),
                    }
                )
                continue

            npc_match = re.fullmatch(
                r'NPCMessage\s+(\S+)\s+"([^"]+)"',
                properties.get("Action", ""),
            )
            if npc_match is None or "NPCMessage" not in action_types:
                continue
            npc_name, raw_references = npc_match.groups()
            references = tuple(raw_references.split("/"))
            if len(references) != 2:
                raise ValueError(
                    f"{map_name} NPCMessage needs nearby/fallback text: {properties['Action']}"
                )
            english_texts = [
                resolve_reference_for_audit(
                    reference,
                    english,
                    "Bundled original text is missing",
                )
                for reference in references
            ]
            chinese_texts = [
                resolve_reference_for_audit(
                    reference,
                    chinese,
                    "仓库原版本地化缺失",
                )
                for reference in references
            ]
            unresolved_references = tuple(
                reference
                for index, reference in enumerate(references)
                if english_texts[index][1] or chinese_texts[index][1]
            )
            authored.append(
                {
                    "number": None,
                    "is_npc_message": True,
                    "key": f"NPCMessage {npc_name}",
                    "action_type": "NPCMessage",
                    "action": properties["Action"],
                    "layer": layer,
                    "tile_x": int(float(obj.get("x", "0")) / TILE_SIZE),
                    "tile_y": int(float(obj.get("y", "0")) / TILE_SIZE),
                    "english": (
                        f"NPC nearby: {english_texts[0][0]} / "
                        f"NPC absent: {english_texts[1][0]}"
                    ),
                    "chinese": (
                        f"NPC 在附近：{chinese_texts[0][0]} / "
                        f"NPC 不在附近：{chinese_texts[1][0]}"
                    ),
                    "references": references,
                    "unresolved_references": unresolved_references,
                }
            )

    authored.sort(
        key=lambda item: (
            2 if item["is_npc_message"] else (
                1 if item["number"] is None else 0),
            item["number"] if item["number"] is not None else 0,
            item["tile_y"],
            item["tile_x"],
        )
    )
    number_counts = Counter(
        item["number"] for item in authored
        if item["number"] is not None
    )
    number_occurrences: Counter[int] = Counter()
    npc_number = 0
    unnumbered_text_number = 0
    points: list[SourcePoint] = []
    for item in authored:
        number = item["number"]
        if item["is_npc_message"]:
            npc_number += 1
            ledger_id = f"{ledger_prefix}-{prefix}-NPC{npc_number:02d}"
        elif number is None:
            unnumbered_text_number += 1
            ledger_id = (
                f"{ledger_prefix}-{prefix}-TXT"
                f"{unnumbered_text_number:02d}")
        else:
            numeric_id = int(number)
            number_occurrences[numeric_id] += 1
            suffix = (
                chr(ord("A") + number_occurrences[numeric_id] - 1)
                if number_counts[numeric_id] > 1
                else ""
            )
            ledger_id = (
                f"{ledger_prefix}-{prefix}-{numeric_id:02d}{suffix}")
        if (
            ledger_prefix == "MI-P2"
            and number is not None
            and number_counts[int(number)] == 1
        ):
            image_name = (
                f"{safe_name(map_name)}_{int(number):02d}_"
                f"{safe_name(str(item['key']))}.png"
            )
        else:
            image_name = (
                f"{safe_name(map_name)}_{safe_name(ledger_id)}.png"
            )
        points.append(
            SourcePoint(
                ledger_id=ledger_id,
                map_name=map_name,
                key=str(item["action"]),
                action_type=str(item["action_type"]),
                layer=str(item["layer"]),
                tile_x=int(item["tile_x"]),
                tile_y=int(item["tile_y"]),
                english=str(item["english"]),
                chinese=str(item["chinese"]),
                references=tuple(item["references"]),
                unresolved_references=tuple(
                    item["unresolved_references"]),
                image_name=image_name,
            )
        )
    ledger_ids = [point.ledger_id for point in points]
    image_names = [point.image_name for point in points]
    if len(set(ledger_ids)) != len(ledger_ids):
        raise ValueError(f"{map_name} produced duplicate ledger IDs")
    if len(set(image_names)) != len(image_names):
        raise ValueError(f"{map_name} produced duplicate detail image names")
    return points


def overview_label(point: SourcePoint) -> str:
    short_text = point.chinese.replace("^", " / ").strip()
    if len(short_text) > 19:
        short_text = short_text[:18] + "…"
    type_badge = {
        "Letter": "信件",
        "NPCMessage": "NPC/备用文本",
        "Notes": "遗失之书/缺失提示",
        "DwarfGrave": "矮人语条件 Textbox",
        "GrandpaMasteryNote": "爷爷信纸",
    }.get(point.action_type, "文本框")
    return f"{point.ledger_id} · {type_badge} · {short_text}"


def render_overview(
    map_name: str,
    points: list[SourcePoint],
    scale: int,
    output_dir: Path,
) -> Path:
    base = render_tmx(MAPS_DIR / f"{map_name}.tmx", scale=scale, layers=RENDER_LAYERS)
    base = draw_tile_grid(base, scale, 0, 0)
    annotations = [
        (
            overview_label(point),
            point.tile_x + 0.5,
            point.tile_y + 0.5,
        )
        for point in points
    ]
    title = f"{map_name} · 原版交互全景（仅供辨认对象，不是 Minecraft 坐标）"
    image = draw_numbered_source_points(base, annotations, (0, 0), scale, title)
    output_path = output_dir / f"{safe_name(map_name)}_overview.png"
    image.save(output_path)
    return output_path


def render_point(
    full_map: Image.Image,
    map_width: int,
    map_height: int,
    point: SourcePoint,
    scale: int,
    crop_radius: int,
    output_dir: Path,
) -> Path:
    def wrapped_lines(text: str, width: int) -> list[str]:
        return textwrap.wrap(
            text.replace("^", " / "),
            width=width,
            break_long_words=True,
            break_on_hyphens=False,
        ) or [""]

    x0 = max(0, point.tile_x - crop_radius)
    y0 = max(0, point.tile_y - crop_radius)
    x1 = min(map_width, point.tile_x + crop_radius + 1)
    y1 = min(map_height, point.tile_y + crop_radius + 1)
    step = TILE_SIZE * scale
    crop = full_map.crop((x0 * step, y0 * step, x1 * step, y1 * step))
    crop = draw_tile_grid(crop, scale, x0, y0)
    estimated_detail_bottom = (
        118
        + 28
        + len(wrapped_lines(point.action, 42)) * 22
        + 12
        + 28
        + len(wrapped_lines(point.chinese, 27)) * 22
        + 12
        + 28
        + len(wrapped_lines(point.english, 48)) * 22
        + 18
    )
    card_height = max(crop.height, 900, estimated_detail_bottom + 96)
    card_base = Image.new("RGBA", (crop.width, card_height), (18, 22, 30, 255))
    card_base.alpha_composite(crop, (0, 0))
    type_badge = {
        "Letter": "Letter / 信件 UI",
        "NPCMessage": "NPCMessage / NPC 对话或备用 Textbox",
        "Notes": "Notes / 遗失之书信纸或缺失 Textbox",
        "DwarfGrave": "DwarfGrave / 语言条件 Textbox",
        "GrandpaMasteryNote": "GrandpaMasteryNote / 信纸 UI",
    }.get(point.action_type, "Message / Textbox")
    annotations = [
        (
            f"{point.ledger_id} · {point.key} · {type_badge}",
            point.tile_x + 0.5,
            point.tile_y + 0.5,
        )
    ]
    title = (
        f"{point.ledger_id} · {point.map_name} · 原版 {point.layer} "
        f"tile ({point.tile_x}, {point.tile_y}) · 非 MC 坐标"
    )
    image = draw_numbered_source_points(card_base, annotations, (x0, y0), scale, title)
    draw = ImageDraw.Draw(image)
    sidebar_left = card_base.width
    text_left = sidebar_left + 24
    text_width = 472
    heading_font = load_font(18)
    body_font = load_font(16)
    warning_font = load_font(17)

    def draw_wrapped(text: str, y: int, width: int, font, fill, line_height: int) -> int:
        for line in wrapped_lines(text, width):
            draw.text((text_left, y), line, fill=fill, font=font)
            y += line_height
        return y

    detail_y = 118
    draw.text((text_left, detail_y), "原版 Action", fill=(48, 67, 86, 255), font=heading_font)
    detail_y = draw_wrapped(point.action, detail_y + 28, 42, body_font, (25, 35, 46, 255), 22) + 12
    draw.text((text_left, detail_y), "原版简中", fill=(48, 67, 86, 255), font=heading_font)
    detail_y = draw_wrapped(point.chinese, detail_y + 28, 27, body_font, (25, 35, 46, 255), 22) + 12
    draw.text((text_left, detail_y), "原版英文", fill=(48, 67, 86, 255), font=heading_font)
    detail_y = draw_wrapped(point.english, detail_y + 28, 48, body_font, (25, 35, 46, 255), 22) + 18
    warning_top = max(detail_y, card_height - 96)
    draw.rounded_rectangle(
        (text_left, warning_top, text_left + text_width, warning_top + 72),
        radius=8,
        fill=(255, 244, 212, 255),
        outline=(183, 127, 18, 255),
        width=2,
    )
    draw.text(
        (text_left + 14, warning_top + 12),
        "Minecraft 3D 点位：待项目作者决策",
        fill=(104, 69, 5, 255),
        font=warning_font,
    )
    draw.text(
        (text_left + 14, warning_top + 39),
        "本图只帮助辨认原版对象，禁止坐标换算。",
        fill=(104, 69, 5, 255),
        font=body_font,
    )
    output_path = output_dir / point.image_name
    image.save(output_path)
    return output_path


def render_contact_sheet_page(
    map_name: str,
    point_paths: list[Path],
    output_path: Path,
) -> Path:
    images = [Image.open(path).convert("RGBA") for path in point_paths]
    columns = 2 if len(images) > 1 else 1
    rows = (len(images) + columns - 1) // columns
    gap = 16
    cell_width = max(image.width for image in images)
    cell_height = max(image.height for image in images)
    sheet = Image.new(
        "RGBA",
        (
            columns * cell_width + (columns - 1) * gap,
            rows * cell_height + (rows - 1) * gap,
        ),
        (36, 42, 50, 255),
    )
    for index, image in enumerate(images):
        column = index % columns
        row = index // columns
        left = column * (cell_width + gap)
        top = row * (cell_height + gap)
        sheet.alpha_composite(image, (left, top))
    sheet.save(output_path)
    return output_path


def point_sheet_names(map_name: str, point_count: int) -> list[str]:
    page_count = (
        point_count + MAX_POINTS_PER_SHEET - 1
    ) // MAX_POINTS_PER_SHEET
    if page_count <= 1:
        return [f"{safe_name(map_name)}_point_sheet.png"]
    return [
        f"{safe_name(map_name)}_point_sheet_{page:02d}.png"
        for page in range(1, page_count + 1)
    ]


def render_contact_sheets(
    map_name: str,
    point_paths: list[Path],
    output_dir: Path,
) -> list[Path]:
    names = point_sheet_names(map_name, len(point_paths))
    return [
        render_contact_sheet_page(
            map_name,
            point_paths[
                page * MAX_POINTS_PER_SHEET:
                (page + 1) * MAX_POINTS_PER_SHEET
            ],
            output_dir / name,
        )
        for page, name in enumerate(names)
    ]


def render_map_set(
    map_name: str,
    points: list[SourcePoint],
    scale: int,
    crop_radius: int,
    output_dir: Path,
) -> list[Path]:
    root = ET.parse(MAPS_DIR / f"{map_name}.tmx").getroot()
    map_width = int(root.get("width", "0"))
    map_height = int(root.get("height", "0"))
    full_map = render_tmx(MAPS_DIR / f"{map_name}.tmx", scale=scale, layers=RENDER_LAYERS)
    overview_path = render_overview(map_name, points, scale, output_dir)
    point_paths = [
        render_point(
            full_map,
            map_width,
            map_height,
            point,
            scale,
            crop_radius,
            output_dir,
        )
        for point in points
    ]
    sheet_paths = render_contact_sheets(
        map_name, point_paths, output_dir)
    return [overview_path, *sheet_paths, *point_paths]


def extract_other_actions(
    map_name: str,
    action_types: set[str],
) -> Counter[str]:
    root = ET.parse(MAPS_DIR / f"{map_name}.tmx").getroot()
    result: Counter[str] = Counter()
    for group in root.findall("objectgroup"):
        for obj in group.findall("object"):
            properties = {
                item.get("name", ""): item.get("value", "")
                for item in obj.findall("./properties/property")
            }
            action = properties.get("Action", "")
            if not action:
                continue
            parsed_text_action = parse_simple_text_action(action)
            if (
                parsed_text_action is not None
                and parsed_text_action[0] in action_types
            ):
                continue
            if (
                "Notes" in action_types
                and re.fullmatch(r"Notes\s+\d+", action)
            ):
                continue
            if (
                "NPCMessage" in action_types
                and re.fullmatch(
                    r'NPCMessage\s+(\S+)\s+"([^"]+)"', action)
            ):
                continue
            if (
                action in SPECIAL_TEXT_ACTION_REFERENCES
                and action in action_types
            ):
                continue
            result[action.split(" ", 1)[0]] += 1
    return result


def validate_source_locales(
    points_by_map: dict[str, list[SourcePoint]],
) -> tuple[int, int]:
    references = sorted(
        {
            reference
            for points in points_by_map.values()
            for point in points
            for reference in point.references
        }
    )
    unresolved_references = {
        reference
        for points in points_by_map.values()
        for point in points
        for reference in point.unresolved_references
    }
    for locale, suffix in LOCALE_SUFFIXES.items():
        sources = load_text_sources(suffix)
        for reference in references:
            if reference in unresolved_references:
                continue
            try:
                resolve_reference(reference, sources)
            except (KeyError, ValueError) as exception:
                raise ValueError(f"{locale}: {exception}") from exception
    return len(references) - len(unresolved_references), len(
        unresolved_references)


def markdown_cell(value: str) -> str:
    return (
        value.replace("\\", "\\\\")
        .replace("|", "\\|")
        .replace("\r", "")
        .replace("\n", "<br>")
    )


def render_ledger(
    ledger_path: Path,
    output_dir: Path,
    maps: list[str],
    points_by_map: dict[str, list[SourcePoint]],
    other_actions_by_map: dict[str, Counter[str]],
    approval_manifest: Optional[dict],
) -> None:
    ledger_path = ledger_path.resolve()
    ledger_path.parent.mkdir(parents=True, exist_ok=True)
    asset_dir = Path(os.path.relpath(output_dir, ledger_path.parent)).as_posix()
    all_points = [
        point for map_name in maps for point in points_by_map[map_name]
    ]
    counts = Counter(point.action_type for point in all_points)
    special_text_count = sum(
        counts[action]
        for action in SPECIAL_TEXT_ACTION_REFERENCES
    )
    unresolved_points = sum(
        bool(point.unresolved_references) for point in all_points)
    lines = [
        "# 地图交互下一批点位审批台账",
        "",
        "> 本文只记录仓库原版 TMX、源码和本地化资源中的事实。",
        "> 所有 Minecraft 3D 坐标、方盒和是否保留均由项目作者决定；",
        "> 图片中的原版 tile 坐标禁止换算成 Minecraft 坐标。",
        "",
        "## 本批范围",
        "",
        f"- 地图：{', '.join(maps)}",
        f"- `Message` 来源点：{counts['Message']}",
        f"- `Letter` 来源点：{counts['Letter']}",
        f"- `NPCMessage` 来源点：{counts['NPCMessage']}",
        f"- `Notes` 来源点：{counts['Notes']}",
        f"- 专用阅读 Action 来源点：{special_text_count}",
        f"- 合计待审批来源点：{len(all_points)}",
        f"- 其中原版文本资源缺失点：{unresolved_points}",
        "",
        "`NPCMessage` 在 NPC 位于当前地点且距离玩家 14 tile 内时显示 NPC 对话；",
        "否则显示备用对象 Textbox。两套原版文本均已列入图片和表格，当前只审批",
        "3D 对象点位，不会先降级成普通 Message。",
        "",
        "`Notes` 在对应遗失之书已经找回时打开原版信纸；否则显示原版",
        "缺失提示 Textbox。两条分支均列入图片，不会降级成固定文案。",
        "",
        "专用阅读 Action 保留原版展示类型和条件分支；例如 `DwarfGrave`",
        "会根据玩家是否理解矮人语选择两条不同原版文本。",
        "",
        "若表格标注“仓库原版本地化缺失”，表示 TMX Action 仍存在，但其",
        "文本键已不在仓库原版资源中；台账保留事实，不会为它自行补写文案。",
        "",
    ]
    for section, map_name in enumerate(maps, start=1):
        points = points_by_map[map_name]
        lines.extend(
            [
                f"## {section}. {map_name}",
                "",
                f"![{map_name} 原版交互全景]"
                f"({asset_dir}/{safe_name(map_name)}_overview.png)",
                "",
                "逐点放大总览："
                + " / ".join(
                    f"[第 {page} 页]({asset_dir}/{name})"
                    for page, name in enumerate(
                        point_sheet_names(map_name, len(points)),
                        start=1,
                    )
                ),
                "",
                "| 台账 ID | 原版 Action | 图层 / 原版 tile | 原版英文 | 原版简中 | Minecraft 3D 点位 | 高清局部图 |",
                "|---|---|---|---|---|---|---|",
            ]
        )
        for approval_index, point in enumerate(points, start=1):
            lines.append(
                "| "
                + " | ".join(
                    (
                        f"`{point.ledger_id}`",
                        f"`{markdown_cell(point.action)}`",
                        f"{point.layer} `({point.tile_x}, {point.tile_y})`",
                        markdown_cell(point.english),
                        markdown_cell(point.chinese),
                        approval_decision(
                            approval_manifest,
                            map_name,
                            approval_index,
                        ),
                        f"[查看]({asset_dir}/{point.image_name})",
                    )
                )
                + " |"
            )
        others = other_actions_by_map[map_name]
        if others:
            summary = "、".join(
                f"`{action}` × {count}"
                for action, count in sorted(others.items())
            )
            lines.extend(
                [
                    "",
                    f"该地图另有 {sum(others.values())} 个非阅读型 Action：{summary}。",
                    "它们不在本轮阅读点位审批中，也没有被静默当成 Textbox。",
                ]
            )
        lines.append("")
    lines.extend(
        [
            "## 审批回复格式",
            "",
            "`_1`、`_2` 是同一交互区域的两个顶点；实现会取每一轴的",
            "最小值和最大值，生成包含两端及内部全部方块的闭区间长方体。",
            "没有 `_1`、`_2` 后缀的点只代表该单个方块。",
            "",
            "```json",
            '"MI-P3-XX-00": [',
            '  { "name": "1", "x": 0, "y": 0, "z": 0 }',
            "]",
            "```",
            "",
            "没有对应 3D 对象的条目可以直接写“XX 不做”。",
            "",
        ]
    )
    ledger_path.write_text("\n".join(lines), encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Render vanilla map-interaction approval references."
    )
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--scale", type=int, default=4)
    parser.add_argument("--crop-radius", type=int, default=3)
    parser.add_argument(
        "--maps",
        nargs="+",
        choices=sorted(MAP_PREFIXES),
        default=list(DEFAULT_MAPS),
    )
    parser.add_argument("--ledger-prefix", default="MI-P2")
    parser.add_argument("--ledger-out", type=Path)
    parser.add_argument("--expected-points", type=int)
    parser.add_argument(
        "--action-types",
        nargs="+",
        choices=(
            "Message",
            "Letter",
            "NPCMessage",
            "Notes",
            *SPECIAL_TEXT_ACTION_REFERENCES.keys(),
        ),
        default=("Message", "Letter", "NPCMessage"),
    )
    parser.add_argument(
        "--approval-manifest",
        type=Path,
        help="Optional author-approved MC coordinate manifest.",
    )
    args = parser.parse_args()
    if args.scale < 1:
        parser.error("--scale must be at least 1")
    if args.crop_radius < 1:
        parser.error("--crop-radius must be at least 1")
    if not re.fullmatch(r"[A-Z0-9-]+", args.ledger_prefix):
        parser.error("--ledger-prefix may contain only A-Z, 0-9 and hyphens")

    output_dir = args.output_dir.resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    approval_manifest = (
        json.loads(
            args.approval_manifest.resolve().read_text(
                encoding="utf-8"))
        if args.approval_manifest is not None
        else None
    )
    english = load_text_sources("")
    chinese = load_text_sources(".zh-CN")

    generated: list[Path] = []
    points_by_map: dict[str, list[SourcePoint]] = {}
    other_actions_by_map: dict[str, Counter[str]] = {}
    action_types = set(args.action_types)
    for map_name in args.maps:
        points = extract_source_points(
            map_name,
            args.ledger_prefix,
            english,
            chinese,
            action_types,
        )
        points_by_map[map_name] = points
        other_actions_by_map[map_name] = extract_other_actions(
            map_name, action_types)
        generated.extend(
            render_map_set(
                map_name,
                points,
                args.scale,
                args.crop_radius,
                output_dir,
            )
        )

    total_points = sum(len(points) for points in points_by_map.values())
    if args.expected_points is not None and total_points != args.expected_points:
        raise ValueError(
            f"Expected {args.expected_points} readable source points, "
            f"found {total_points}."
        )
    reference_count, unresolved_reference_count = validate_source_locales(
        points_by_map)
    if args.ledger_out is not None:
        render_ledger(
            args.ledger_out,
            output_dir,
            args.maps,
            points_by_map,
            other_actions_by_map,
            approval_manifest,
        )
    print(f"Rendered {total_points} source points into {len(generated)} images.")
    print(
        f"Validated {reference_count} unique text references "
        f"across {len(LOCALE_SUFFIXES)} locales."
    )
    if unresolved_reference_count:
        print(
            f"Recorded {unresolved_reference_count} source text references "
            "missing from the bundled original resources."
        )
    print(f"Output: {output_dir}")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Export Stardew's authored font pixels and metrics without re-rasterizing them.

Minecraft's bitmap font JSON format cannot represent XNA SpriteFont bearings,
cropping, floating advances, or SpriteText's pair-dependent spacing.  This
script therefore writes source-resolution texture pages plus metric JSON for
the client-side Stardew font provider/layout engine.
"""

from __future__ import annotations

import argparse
import json
import shutil
import unicodedata
import xml.etree.ElementTree as ET
from pathlib import Path

from PIL import Image


NAMESPACE = "stardewcraft"
# Minecraft's normal font line height is 9 GUI pixels. Stardew's runtime
# SmallFont line spacing is 28 source pixels, so one third preserves Stardew's
# authored title/body ratio while occupying MC's normal 10px tooltip row.
MC_FONT_DIVISOR = 3.0
LANGUAGE_SUFFIXES = {
    "en_us": None,
    "de_de": "de-DE",
    "es_es": "es-ES",
    "fr_fr": "fr-FR",
    "hu_hu": "hu-HU",
    "ja_jp": "ja-JP",
    "ko_kr": "ko-KR",
    "pt_br": "pt-BR",
    "ru_ru": "ru-RU",
    "tr_tr": "tr-TR",
    "zh_cn": "zh-CN",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, default=Path("源文件/Content"))
    parser.add_argument(
        "--output", type=Path,
        default=Path("src/main/resources/assets/stardewcraft"),
    )
    return parser.parse_args()


def drawable(codepoint: int) -> bool:
    return 0 < codepoint <= 0x10FFFF and unicodedata.category(chr(codepoint)) not in {
        "Cc", "Cf", "Cs"
    }


def straight_alpha_white(image: Image.Image) -> Image.Image:
    """XNA SpriteFont PNGs contain premultiplied-white coverage (RGB == A)."""
    rgba = image.convert("RGBA")
    result = Image.new("RGBA", rgba.size, (255, 255, 255, 0))
    result.putalpha(rgba.getchannel("A"))
    return result


def metric_path(output: Path, role: str, variant: str) -> Path:
    return output / "stardew_font_metrics" / role / f"{variant}.json"


def texture_location(role: str, variant: str, page: int) -> str:
    return f"{NAMESPACE}:textures/font/stardew/{role}/{variant}_{page}.png"


def write_metric(output: Path, role: str, variant: str, data: dict) -> None:
    path = metric_path(output, role, variant)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=True, separators=(",", ":")) + "\n", encoding="utf-8")


def write_texture(output: Path, role: str, variant: str, page: int, image: Image.Image) -> None:
    path = output / "textures/font/stardew" / role / f"{variant}_{page}.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, optimize=True)


def sprite_font_paths(content: Path, stem: str, suffix: str | None) -> tuple[Path, Path]:
    localized = f".{suffix}" if suffix else ""
    root = content / "Fonts"
    return root / f"{stem}{localized}.json", root / f"{stem}{localized}.png"


def export_sprite_font(
    output: Path, role: str, variant: str, json_path: Path, png_path: Path,
    runtime_line_height: float | None = None,
) -> None:
    source = json.loads(json_path.read_text(encoding="utf-8"))
    write_texture(output, role, variant, 0, straight_alpha_white(Image.open(png_path)))
    glyphs = []
    for character, raw in source["Glyphs"].items():
        codepoint = ord(character)
        if not drawable(codepoint):
            continue
        bounds = raw["BoundsInTexture"]
        crop = raw["Cropping"]
        glyphs.append({
            "cp": codepoint,
            "page": 0,
            "x": bounds["X"], "y": bounds["Y"],
            "w": bounds["Width"], "h": bounds["Height"],
            "left": float(raw["LeftSideBearing"]),
            "top": float(crop["Y"]),
            "crop_x": float(crop["X"]),
            "width": float(raw["Width"]),
            "right": float(raw["RightSideBearing"]),
        })
    write_metric(output, role, variant, {
        "type": "sprite_font",
        "scale": 1.0 / MC_FONT_DIVISOR,
        "line_height": float(runtime_line_height if runtime_line_height is not None
                             else source["LineSpacing"]),
        "spacing": float(source["Spacing"]),
        "default": ord(source["DefaultCharacter"]) if source.get("DefaultCharacter") else -1,
        "pages": [texture_location(role, variant, 0)],
        "glyphs": glyphs,
    })


def sprite_text_index(codepoint: int) -> int:
    overrides = {
        ord("Œ"): 96, ord("œ"): 97, ord("Ğ"): 102, ord("ğ"): 103,
        ord("İ"): 98, ord("ı"): 99, ord("Ş"): 100, ord("ş"): 101,
        ord("’"): 104, ord("Ő"): 105, ord("ő"): 106,
        ord("Ű"): 107, ord("ű"): 108,
        ord("ё"): 560, ord("ґ"): 561, ord("є"): 562,
        ord("і"): 563, ord("ї"): 564, ord("ў"): 565,
        ord("Ё"): 512, ord("–"): 464, ord("—"): 465,
        ord("№"): 466, ord("Ґ"): 513, ord("Є"): 514,
        ord("І"): 515, ord("Ї"): 516, ord("Ў"): 517,
        ord("Ą"): 576, ord("ą"): 578, ord("Ć"): 579,
        ord("ć"): 580, ord("Ę"): 581, ord("ę"): 582,
        ord("Ł"): 583, ord("ł"): 584, ord("Ń"): 585,
        ord("ń"): 586, ord("Ź"): 587, ord("ź"): 588,
        ord("Ż"): 589, ord("ż"): 590, ord("Ś"): 574,
        ord("ś"): 575,
    }
    if codepoint in overrides:
        return overrides[codepoint]
    index = codepoint - 32
    if 1008 <= index < 1040:
        index -= 528
    elif 1040 <= index < 1072:
        index -= 512
    return index


def sprite_text_width_offset(character: str) -> int:
    if character in ",.":
        return -2
    if character in "!jl¡iìíîïış":
        return -1
    if character == "$":
        return 1
    if character == "^":
        return -8
    return 0


def latin_codepoints(content: Path) -> set[int]:
    result = set(range(32, 127))
    for path in (content / "Fonts").glob("SpriteFont1*.json"):
        source = json.loads(path.read_text(encoding="utf-8"))
        result.update(ord(character) for character in source["Characters"])
    return result


def export_latin_sprite_text(output: Path, content: Path, variant: str, texture: str) -> None:
    atlas = Image.open(content / f"LooseSprites/{texture}.png").convert("RGBA")
    write_texture(output, "sprite_text", variant, 0, atlas)
    glyphs = []
    for codepoint in sorted(latin_codepoints(content)):
        if not drawable(codepoint):
            continue
        index = sprite_text_index(codepoint)
        if index < 0:
            continue
        x = index * 8 % atlas.width
        y = index * 8 // atlas.width * 16
        if y + 16 > atlas.height:
            continue
        character = chr(codepoint)
        glyphs.append({
            "cp": codepoint, "page": 0,
            "x": x, "y": y, "w": 8, "h": 16,
            "left": 0.0,
            "top": float(-1 - (3 if character.isupper() or character == "ß" else 0)
                         + (2 if character == "Ç" else 0)),
            "offset": sprite_text_width_offset(character),
        })
    write_metric(output, "sprite_text", variant, {
        "type": "sprite_text_latin",
        "scale": 3.0 / MC_FONT_DIVISOR,
        "origin_y": 4.0 / MC_FONT_DIVISOR,
        "line_height": 18.0,
        "pages": [texture_location("sprite_text", variant, 0)],
        "glyphs": glyphs,
    })


def export_bm_sprite_text(
    output: Path, content: Path, variant: str, fnt_path: Path, zoom: float,
    origin_y: float,
) -> None:
    root = ET.parse(fnt_path).getroot()
    common = root.find("common")
    pages_node = root.find("pages")
    chars_node = root.find("chars")
    if common is None or pages_node is None or chars_node is None:
        raise ValueError(f"Incomplete BMFont: {fnt_path}")
    pages = []
    for page in pages_node:
        page_id = int(page.attrib["id"])
        source_path = fnt_path.parent / page.attrib["file"]
        if not source_path.suffix:
            source_path = source_path.with_suffix(".png")
        image = Image.open(source_path).convert("RGBA")
        write_texture(output, "sprite_text", variant, page_id, image)
        while len(pages) <= page_id:
            pages.append("")
        pages[page_id] = texture_location("sprite_text", variant, page_id)
    glyphs = []
    for raw in chars_node:
        codepoint = int(raw.attrib["id"])
        if not drawable(codepoint):
            continue
        glyphs.append({
            "cp": codepoint, "page": int(raw.attrib["page"]),
            "x": int(raw.attrib["x"]), "y": int(raw.attrib["y"]),
            "w": int(raw.attrib["width"]), "h": int(raw.attrib["height"]),
            "left": float(raw.attrib["xoffset"]),
            "top": float(raw.attrib["yoffset"]),
            "advance": float(raw.attrib["xadvance"]),
        })
    write_metric(output, "sprite_text", variant, {
        "type": "sprite_text_bm",
        "scale": zoom / MC_FONT_DIVISOR,
        "origin_y": origin_y,
        "line_height": float(int(common.attrib["lineHeight"]) + 2),
        "pages": pages,
        "glyphs": glyphs,
    })


def clean_generated(output: Path) -> None:
    for relative in ("font/stardew", "stardew_font_definitions", "stardew_font_metrics", "textures/font/stardew"):
        path = output / relative
        if path.exists():
            shutil.rmtree(path)


def main() -> None:
    args = parse_args()
    content = args.source.resolve()
    output = args.output.resolve()
    if not (content / "Fonts").is_dir():
        raise SystemExit(f"Stardew font source not found: {content / 'Fonts'}")
    clean_generated(output)

    for language, suffix in LANGUAGE_SUFFIXES.items():
        for role, stem in (("dialogue", "SpriteFont1"), ("small", "SmallFont")):
            json_path, png_path = sprite_font_paths(content, stem, suffix)
            if not json_path.exists():
                json_path, png_path = sprite_font_paths(content, stem, None)
            runtime_line_height = 42.0 if role == "dialogue" else (
                44.0 if language == "ko_kr" else 32.0 if language == "tr_tr" else 28.0
            )
            export_sprite_font(
                output, role, language, json_path, png_path, runtime_line_height,
            )

    chinese_round = content / "Fonts/Chinese_round"
    for role, stem in (("dialogue", "SpriteFont1.zh-CN"), ("small", "SmallFont.zh-CN")):
        export_sprite_font(
            output, role, "zh_cn_round",
            chinese_round / f"{stem}.json", chinese_round / f"{stem}.png",
            42.0 if role == "dialogue" else 28.0,
        )

    tiny_json, tiny_png = sprite_font_paths(content, "tinyFont", None)
    export_sprite_font(output, "tiny", "base", tiny_json, tiny_png)

    export_latin_sprite_text(output, content, "latin_bold", "font_bold")
    export_latin_sprite_text(output, content, "latin_colored", "font_colored")
    export_bm_sprite_text(output, content, "ja_jp", content / "Fonts/Japanese.fnt", 1.75, 0.0)
    export_bm_sprite_text(
        output, content, "ko_kr", content / "Fonts/Korean.fnt", 1.5,
        -8.0 / MC_FONT_DIVISOR,
    )
    export_bm_sprite_text(
        output, content, "zh_cn", content / "Fonts/Chinese.fnt", 1.5,
        -6.0 / MC_FONT_DIVISOR,
    )
    export_bm_sprite_text(
        output, content, "zh_cn_round",
        content / "Fonts/Chinese_round/Chinese.fnt", 1.0,
        -8.0 / MC_FONT_DIVISOR,
    )
    print(f"Exported source-exact Stardew font data to {output}")


if __name__ == "__main__":
    main()

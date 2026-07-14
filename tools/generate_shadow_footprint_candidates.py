#!/usr/bin/env python3
"""Generate deterministic 16x16 footprint candidates and an enlarged review sheet."""

from pathlib import Path
from PIL import Image, ImageDraw


OUT_DIR = Path(__file__).resolve().parent / "generated" / "shadow_footprint_candidates"
SCALE = 8

# Each design is a single right footprint viewed from above, toe pointing north.
# Characters: # = primary print, + = secondary/lighter print, . = transparent.
DESIGNS = {
    "A_solid_boot": (
        "......####......",
        "....#######.....",
        "...########.....",
        "...########.....",
        "....#######.....",
        ".....#####......",
        ".....####.......",
        "......###.......",
        "......###.......",
        ".....####.......",
        ".....#####......",
        ".....#####......",
        "....######......",
        "....######......",
        ".....####.......",
        "................",
    ),
    "B_split_sole": (
        "......####......",
        "....#######.....",
        "...########.....",
        "...########.....",
        "....######......",
        "................",
        ".....#####......",
        "......####......",
        "......###.......",
        "................",
        ".....#####......",
        "....######......",
        "....######......",
        "....######......",
        ".....####.......",
        "................",
    ),
    "C_treaded_boot": (
        ".....##..##.....",
        "...###.##.##....",
        "...##.###.##....",
        "...###.##.##....",
        "....##.####.....",
        ".....####.......",
        ".....##.##......",
        "......###.......",
        "......###.......",
        ".....##.##......",
        "....###.##......",
        "....##.###......",
        "....###.##......",
        "....##.###......",
        ".....####.......",
        "................",
    ),
    "D_soft_shadow": (
        ".......+++......",
        ".....++###++....",
        "....+#######+...",
        "....+#######+...",
        ".....+#####+....",
        "......+###+.....",
        "......+##+......",
        ".......##+......",
        ".......##+......",
        "......+###+.....",
        ".....+####+.....",
        ".....+#####+....",
        ".....+#####+....",
        ".....++###++....",
        ".......+++......",
        "................",
    ),
}

PALETTES = {
    "black": {"#": (20, 16, 22, 230), "+": (42, 34, 44, 165)},
    "brown": {"#": (67, 38, 23, 235), "+": (105, 63, 35, 170)},
}

BACKGROUNDS = {
    "grass": ((92, 112, 53), (105, 123, 61)),
    "path": ((151, 116, 72), (163, 127, 78)),
    "stone": ((137, 137, 128), (149, 149, 139)),
}


def render_icon(rows: tuple[str, ...], palette: dict[str, tuple[int, int, int, int]]) -> Image.Image:
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    pixels = image.load()
    for y, row in enumerate(rows):
        if len(row) != 16:
            raise ValueError(f"row {y} has {len(row)} pixels, expected 16")
        for x, token in enumerate(row):
            if token in palette:
                pixels[x, y] = palette[token]
    return image


def checker_tile(size: tuple[int, int], colors: tuple[tuple[int, int, int], tuple[int, int, int]]) -> Image.Image:
    image = Image.new("RGB", size)
    pixels = image.load()
    for y in range(size[1]):
        for x in range(size[0]):
            pixels[x, y] = colors[(x // 8 + y // 8) & 1]
    return image


def composite_scaled(icon: Image.Image, background: Image.Image, x: int, y: int) -> None:
    scaled = icon.resize((16 * SCALE, 16 * SCALE), Image.Resampling.NEAREST)
    background.paste(scaled, (x, y), scaled)


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    icons: dict[tuple[str, str], Image.Image] = {}
    for design_name, rows in DESIGNS.items():
        for palette_name, palette in PALETTES.items():
            icon = render_icon(rows, palette)
            icons[(design_name, palette_name)] = icon
            icon.save(OUT_DIR / f"{design_name}_{palette_name}.png")
            icon.transpose(Image.Transpose.FLIP_LEFT_RIGHT).save(
                OUT_DIR / f"{design_name}_{palette_name}_left.png"
            )

    panel_width = 16 + 3 * (16 * SCALE + 16)
    panel_height = 28 + 16 * SCALE + 20
    sheet = Image.new("RGB", (panel_width * 2, panel_height * 4), (232, 224, 207))
    draw = ImageDraw.Draw(sheet)
    surface_names = tuple(BACKGROUNDS)

    for row, design_name in enumerate(DESIGNS):
        for column, palette_name in enumerate(PALETTES):
            panel_x = column * panel_width
            panel_y = row * panel_height
            label = f"{design_name[0]}  {design_name[2:].replace('_', ' ')}  /  {palette_name}"
            draw.text((panel_x + 10, panel_y + 8), label, fill=(30, 27, 25))
            icon = icons[(design_name, palette_name)]
            for surface_index, surface_name in enumerate(surface_names):
                tile_x = panel_x + 8 + surface_index * (16 * SCALE + 16)
                tile_y = panel_y + 28
                tile = checker_tile((16 * SCALE, 16 * SCALE), BACKGROUNDS[surface_name])
                sheet.paste(tile, (tile_x, tile_y))
                composite_scaled(icon, sheet, tile_x, tile_y)
                draw.text((tile_x + 2, tile_y + 16 * SCALE + 4), surface_name, fill=(45, 40, 35))

    sheet.save(OUT_DIR / "candidate_sheet.png")
    print(OUT_DIR / "candidate_sheet.png")


if __name__ == "__main__":
    main()

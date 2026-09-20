#!/usr/bin/env python3
"""Generate compact, map-authored candidate masks for special farm spawns.

The masks describe candidate columns only. Runtime still validates the current
world state, so player changes win and existing farms need no schematic paste.
"""

from __future__ import annotations

import argparse
import importlib.util
import json
from collections import deque
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCHEMATIC_DIR = ROOT / "run" / "config" / "worldedit" / "schematics"
OUTPUT_DIR = ROOT / "src" / "main" / "resources" / "data" / "stardewcraft" / "farm_spawn_layouts"
NATURAL_GROUND = {
    "stardewcraft:grass_block",
    "stardewcraft:dark_grass_block",
    "stardewcraft:dirt",
    "stardewcraft:sand",
}


def load_intake():
    path = ROOT / "scripts" / "import_farm_building_prefabs.py"
    spec = importlib.util.spec_from_file_location("farm_spawn_intake", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def block_name(state: str) -> str:
    return state.split("[", 1)[0]


def natural_surface(schematic, x: int, z: int, minimum_y: int = 16, maximum_y: int = 40):
    for y in range(min(maximum_y, schematic.size[1] - 2), minimum_y - 1, -1):
        name = block_name(schematic.state((x, y, z)))
        if name in NATURAL_GROUND:
            return y, name
    return None


def encode_runs(positions):
    """Encode (x, y, z) cells as [y, z, minX, maxX] horizontal runs."""
    rows = {}
    for x, y, z in positions:
        rows.setdefault((y, z), []).append(x)
    runs = []
    for (y, z), values in sorted(rows.items()):
        values.sort()
        start = previous = values[0]
        for x in values[1:]:
            if x == previous + 1:
                previous = x
                continue
            runs.append([y, z, start, previous])
            start = previous = x
        runs.append([y, z, start, previous])
    return runs


def forest_layout(intake):
    schematic = intake.Schematic(SCHEMATIC_DIR / "farm_3.schem")
    if schematic.size != (288, 98, 272):
        raise ValueError(f"farm_3 size changed: {schematic.size}")

    # This is the authored, walkable central farm basin. The former runtime
    # rectangle remains only as an intake boundary; the generated mask follows
    # actual grass/dark-grass/dirt columns instead of treating it as a solid box.
    min_x, min_z, max_x, max_z = 65, 72, 216, 202
    general = []
    forest_strip = []
    for z in range(min_z, max_z + 1):
        for x in range(min_x, max_x + 1):
            surface = natural_surface(schematic, x, z)
            if surface is None:
                continue
            y, name = surface
            if name in {"stardewcraft:grass_block", "stardewcraft:dark_grass_block"}:
                general.append((x, y + 1, z))
            if x <= min_x + 39:
                forest_strip.append((x, y + 1, z))

    return {
        "format": 1,
        "size": [288, 81, 272],
        "candidate_counts": {
            "general": len(general),
            "forest_strip": len(forest_strip),
        },
        "runs": {
            "general": encode_runs(general),
            "forest_strip": encode_runs(forest_strip),
        },
    }


def water_components(cells):
    remaining = set(cells)
    result = []
    while remaining:
        seed = remaining.pop()
        queue = deque([seed])
        component = {seed}
        while queue:
            x, z = queue.popleft()
            for neighbor in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)):
                if neighbor not in remaining:
                    continue
                remaining.remove(neighbor)
                component.add(neighbor)
                queue.append(neighbor)
        result.append(component)
    return result


def beach_layout(intake):
    schematic = intake.Schematic(SCHEMATIC_DIR / "farm_7.schem")
    if schematic.size != (288, 97, 272):
        raise ValueError(f"farm_7 size changed: {schematic.size}")
    width, _, length = schematic.size

    sea_level = 24
    water = {
        (x, z)
        for z in range(length)
        for x in range(width)
        if block_name(schematic.state((x, sea_level, z))) == "minecraft:water"
    }
    ocean = max(water_components(water), key=len)

    shore_water = []
    for x, z in ocean:
        if block_name(schematic.state((x, sea_level + 1, z))) != "minecraft:air":
            continue
        beside_sand = any(
            0 <= x + dx < width
            and 0 <= z + dz < length
            and block_name(schematic.state((x + dx, sea_level, z + dz))) == "stardewcraft:sand"
            for distance in (1, 2)
            for dx in range(-distance, distance + 1)
            for dz in range(-distance, distance + 1)
            if max(abs(dx), abs(dz)) == distance
        )
        if beside_sand:
            shore_water.append((x, sea_level + 1, z))

    beach_spawn = []
    for z in range(length):
        for x in range(width):
            surface = natural_surface(schematic, x, z, minimum_y=sea_level, maximum_y=40)
            if surface is None or surface[1] != "stardewcraft:sand":
                continue
            y, _ = surface
            if block_name(schematic.state((x, y + 1, z))) != "minecraft:air":
                continue
            # The source BeachSpawn property covers a coastal strip rather
            # than every inland sand tile. Radius three yields the same scaled
            # candidate area while following this authored shoreline.
            if any(
                (x + dx, z + dz) in ocean
                for dx in range(-3, 4)
                for dz in range(-3, 4)
            ):
                beach_spawn.append((x, y + 1, z))

    # Projection of the original 33x18 seasonal-forage patch, filtered through
    # the actual farm_7 grass surface (not copied tile-for-tile).
    seasonal_grass = []
    for z in range(153, 176):
        for x in range(84, 137):
            surface = natural_surface(schematic, x, z)
            if surface is not None and surface[1] in {
                "stardewcraft:grass_block", "stardewcraft:dark_grass_block"
            }:
                seasonal_grass.append((x, surface[0] + 1, z))

    return {
        "format": 1,
        "size": [288, 78, 272],
        "candidate_counts": {
            "shore_water": len(shore_water),
            "beach_spawn": len(beach_spawn),
            "seasonal_grass": len(seasonal_grass),
            "ocean_component": len(ocean),
        },
        "runs": {
            "shore_water": encode_runs(shore_water),
            "beach_spawn": encode_runs(beach_spawn),
            "seasonal_grass": encode_runs(seasonal_grass),
        },
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    intake = load_intake()
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    layouts = {"forest": forest_layout(intake), "beach": beach_layout(intake)}
    changed = []
    for name, data in layouts.items():
        encoded = json.dumps(data, ensure_ascii=False, separators=(",", ":")) + "\n"
        output = OUTPUT_DIR / f"{name}.json"
        if not output.exists() or output.read_text(encoding="utf-8") != encoded:
            changed.append(name)
            if not args.check:
                output.write_text(encoded, encoding="utf-8")
        print(f"{name}: {data['candidate_counts']}")
    if args.check and changed:
        raise SystemExit("outdated farm spawn layouts: " + ", ".join(changed))


if __name__ == "__main__":
    main()

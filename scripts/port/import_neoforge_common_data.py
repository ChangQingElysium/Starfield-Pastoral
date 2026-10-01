#!/usr/bin/env python3
"""Import the NeoForge 1.21.1 data the mod relies on into the 1.20.1 port (idempotent).

    python3 scripts/port/import_neoforge_common_data.py

NeoForge ships `c:` common tags and patches vanilla recipes to use them; the mod adds its items to
those tags (e.g. stardewcraft:iron_bar in c:ingots/iron), so on 1.21.1 mod items work in vanilla
recipes. Forge 1.20.1 uses `forge:` tags instead. This script writes, under
the mod's own data pack (the top pack on 1.20.1, so the recipe patches override vanilla and Forge just
like NeoForge's pack does on 1.21.1). Tag files the mod also defines are merged in 1.21.1 pack order
(NeoForge values first, then the mod's) into src/port/resources-merged, which build.gradle gives
precedence over src/main/resources for exactly those paths. Generated files are listed in
src/port/neoforge-import-manifest.txt:

- data/c/tags/<plural registry dir>/**: NeoForge's c: tags in 1.20.1 folders. Entries for ids that do
  not exist in 1.20.1 are dropped; tag references become optional. Each tag also optionally
  includes the same-path `forge:` tag so other Forge mods keep working (no-op in a pure modpack).
- data/minecraft/recipes/**: NeoForge's patched vanilla recipes in 1.20.1 format, unless the mod
  itself ships the same recipe id (the mod's file wins, as on 1.21.1) or it needs an item 1.20.1 lacks.
"""
from __future__ import annotations

import json
import re
import shutil
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts/port"))
import convert_resources_1201 as conv  # noqa: E402

OUT = ROOT / "src/port/resources"
MERGED = ROOT / "src/port/resources-merged"
MAIN_RES = ROOT / "src/main/resources"
LIST = ROOT / "src/port/neoforge-import-manifest.txt"
MC = ROOT / "build/port-mc-src/net/minecraft"
TAG_DIRS = {"item": "items", "block": "blocks", "fluid": "fluids", "entity_type": "entity_types",
            "worldgen/biome": "worldgen/biome", "enchantment": "enchantment"}


def neoforge_jar() -> Path:
    for p in Path.home().glob(".gradle/caches/modules-2/files-2.1/net.neoforged/neoforge/21.1.217/*/neoforge-21.1.217-universal.jar"):
        return p
    raise SystemExit("NeoForge 21.1.217 universal jar not found in the Gradle cache")


def forge_jar() -> Path:
    cp = (ROOT / "build/port-classpath.txt").read_text().split(":")
    return Path(next(p for p in cp if "forge-1.20.1-47.4.10_mapped_official_1.20.1.jar" in p))


def field_ids(path: Path, type_name: str) -> set[str]:
    text = path.read_text(encoding="utf-8")
    return {"minecraft:" + m.group(1).lower()
            for m in re.finditer(r"public static final " + type_name + r"(?:<[^>]*>)? ([A-Z0-9_]+) =", text)}


def known_ids() -> dict[str, set[str]]:
    biomes = {"minecraft:" + m.group(1) for m in
              re.finditer(r'register\("([a-z0-9_]+)"\)', (MC / "world/level/biome/Biomes.java").read_text())}
    items_src = (MC / "world/item/Items.java").read_text()
    items = field_ids(MC / "world/item/Items.java", "Item")
    items |= {"minecraft:" + b.lower() for b in re.findall(r"registerBlock\(\s*Blocks\.([A-Z0-9_]+)", items_src)}
    items |= {"minecraft:" + n for n in re.findall(r'register(?:Item)?\(\s*"([a-z0-9_]+)"', items_src)}
    enchantments = {"minecraft:" + n for n in re.findall(
        r'register\(\s*"([a-z0-9_]+)"', (MC / "world/item/enchantment/Enchantments.java").read_text())}
    return {
        "item": items,
        "block": field_ids(MC / "world/level/block/Blocks.java", "Block"),
        "entity_type": field_ids(MC / "world/entity/EntityType.java", "EntityType"),
        "fluid": field_ids(MC / "world/level/material/Fluids.java", "(?:Fluid|FlowingFluid)"),
        "enchantment": enchantments | {"minecraft:sweeping_edge"},
        "worldgen/biome": biomes,
    }


def valid(entry_id: str, kind: str, ids: dict[str, set[str]], mod_ids: set[str]) -> bool:
    ns = entry_id.split(":", 1)[0]
    if ns == "minecraft":
        return entry_id in ids[kind]
    return ns != "neoforge" or entry_id in mod_ids


def vanilla_jars():
    cp = (ROOT / "build/port-classpath.txt").read_text().split(":")
    v120 = zipfile.ZipFile(next(p for p in cp if p.endswith("client-extra.jar")))
    v121 = zipfile.ZipFile(ROOT.parent / "StardewCraft-1.21.1-baseline/build/moddev/artifacts/"
                           "neoforge-21.1.217-client-extra-aka-minecraft-resources.jar")
    return v120, v121


PLURAL_TO_121 = {v: k for k, v in TAG_DIRS.items()}


def import_missing_vanilla_tags(written, dropped, ids):
    """1.21.1 vanilla tags referenced by imported c: tags but absent in 1.20.1 (e.g. #minecraft:bee_food)."""
    v120, v121 = vanilla_jars()
    n120 = set(v120.namelist())
    pending = []
    for rel in list(written):
        base, _, path = rel.partition(":")
        d = path.split("/")[3]  # data/c/tags/<dir>/...
        d = "worldgen/biome" if d == "worldgen" else d
        text = (ROOT / base / path).read_text(encoding="utf-8")
        pending += [(d, t) for t in re.findall(r'"#minecraft:([a-z0-9_/]+)"', text)]
    done = set()
    while pending:
        d, tag = pending.pop()
        if (d, tag) in done:
            continue
        done.add((d, tag))
        rel = f"data/minecraft/tags/{d}/{tag}.json"
        if rel in n120 or (OUT / rel).exists() or (MAIN_RES / rel).exists():
            continue
        src = f"data/minecraft/tags/{PLURAL_TO_121[d]}/{tag}.json"
        if src not in v121.namelist():
            dropped.append(f"#minecraft:{tag} ({d}) exists in neither version")
            continue
        data = json.loads(v121.read(src))
        values = []
        for v in data.get("values", []):
            vid = v if isinstance(v, str) else v["id"]
            if vid.startswith("#"):
                values.append({"id": vid, "required": False})
                if vid.startswith("#minecraft:"):
                    pending.append((d, vid[len("#minecraft:"):]))
            elif valid(vid, PLURAL_TO_121[d], ids, set()):
                values.append(v)
            else:
                dropped.append(f"#minecraft:{tag}: {vid}")
        target = OUT / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps({"replace": False, "values": values}, indent=2) + "\n")
        written.append(f"src/port/resources:{rel}")


def main() -> int:
    ids = known_ids()
    nf, fg = zipfile.ZipFile(neoforge_jar()), zipfile.ZipFile(forge_jar())
    forge_tags = {n for n in fg.namelist() if n.startswith("data/forge/tags/")}
    # Remove the previous import first so the script is idempotent.
    if LIST.exists():
        for rel in LIST.read_text().split():
            base, _, path = rel.partition(":")
            (ROOT / base / path).unlink(missing_ok=True)
    written, dropped = [], []
    mod_recipes = {p.name for p in (ROOT / "src/main/resources/data/minecraft/recipes").glob("*.json")}
    for name in sorted(nf.namelist()):
        if name.endswith("/"):
            continue
        m = re.match(r"data/c/tags/(worldgen/biome|[a-z_]+)/(.+)\.json$", name)
        if m and m.group(1) in TAG_DIRS:
            kind, path = m.group(1), m.group(2)
            data = json.loads(nf.read(name))
            values = []
            for v in data.get("values", []):
                vid = v if isinstance(v, str) else v["id"]
                req = True if isinstance(v, str) else v.get("required", True)
                if vid.startswith("#"):
                    values.append({"id": vid, "required": False})
                elif valid(vid, kind, ids, set()):
                    values.append(vid if req else {"id": vid, "required": False})
                else:
                    dropped.append(f"c:{kind}/{path}: {vid}")
            forge_name = f"data/forge/tags/{TAG_DIRS[kind]}/{path}.json"
            # 1.21.1 has no forge: tags (NeoForge's optional #forge: refs resolve to nothing there), so
            # keeping them would add Forge-only members: drop them for identical tag contents.
            values = [v for v in values if not (isinstance(v, dict) and v.get("id", "").startswith("#forge:"))]
            seen, unique = set(), []
            for v in values:
                key = json.dumps(v, sort_keys=True)
                if key not in seen:
                    seen.add(key)
                    unique.append(v)
            values = unique
            rel = f"data/c/tags/{TAG_DIRS[kind]}/{path}.json"
            replace = data.get("replace", False)
            mod_file = MAIN_RES / rel
            if mod_file.exists():
                # Same pack on 1.20.1: union in 1.21.1 pack order (NeoForge pack below the mod pack).
                mod = json.loads(mod_file.read_text(encoding="utf-8"))
                if mod.get("replace", False):
                    continue
                for v in mod.get("values", []):
                    if json.dumps(v, sort_keys=True) not in {json.dumps(x, sort_keys=True) for x in values}:
                        values.append(v)
                base_dir, label = MERGED, "src/port/resources-merged"
            else:
                base_dir, label = OUT, "src/port/resources"
            target = base_dir / rel
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(json.dumps({"replace": replace, "values": values}, indent=2) + "\n")
            written.append(f"{label}:{rel}")
            continue
        m = re.match(r"data/minecraft/recipe/(.+\.json)$", name)
        if m:
            if m.group(1) in mod_recipes:
                continue
            data = conv.convert_recipe(json.loads(nf.read(name)))
            text = json.dumps(data)
            missing = [i for i in re.findall(r'"(minecraft:[a-z0-9_/]+)"', text)
                       if i not in ids["item"] and '"item": "' + i in text.replace('"item":"', '"item": "')]
            if missing:
                dropped.append(f"recipe minecraft:{m.group(1)[:-5]}: needs {missing}")
                continue
            rel = f"data/minecraft/recipes/{m.group(1)}"
            target = OUT / rel
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(json.dumps(data, indent=2) + "\n")
            written.append(f"src/port/resources:{rel}")
    import_missing_vanilla_tags(written, dropped, ids)
    LIST.write_text("\n".join(sorted(written)) + "\n")
    print(f"written {len(written)} files; dropped entries: {len(dropped)}")
    for d in dropped:
        print("  dropped", d)
    return 0


if __name__ == "__main__":
    sys.exit(main())

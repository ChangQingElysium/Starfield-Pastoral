#!/usr/bin/env python3
"""Generate the 1.21.1 vanilla enchantment applicability rules for the 1.20.1 Forge port.

1.21.1 decides which items an enchantment applies to with data (enchantment JSON ``supported_items`` /
``primary_items`` / ``exclusive_set`` + the ``minecraft:enchantable/*`` item tags); 1.20.1 decides it with
``EnchantmentCategory`` classes. The port re-applies the 1.21.1 rules to StardewCraft items only
(``PortVanillaEnchantmentRules`` + ``PortVanillaEnchantmentApplicabilityMixin`` & co). This script writes:

  src/port/java/com/stardew/craft/port/PortVanillaEnchantmentTable.java
      every 1.21.1 vanilla enchantment that exists in 1.20.1: 1.20.1 id, supported/primary item sets,
      exclusive set (verbatim HolderSet strings from the 1.21.1 JSON).
  src/port/resources/data/minecraft/tags/items/<tag>.json
      the 1.21.1 vanilla item tags reachable from those item sets that vanilla 1.20.1 lacks
      (enchantable/*, head_armor, ..., skulls), entries for items missing in 1.20.1 dropped, replace:false
      so the mod's own tag files merge. Tags that exist in both versions are checked for equal membership.
  src/port/resources/data/minecraft/tags/enchantment/exclusive_set/<name>.json
      the 1.21.1 vanilla exclusive-set enchantment tags (1.20.1 ids, enchantments missing in 1.20.1 dropped).
  src/port/resources/port-parity/enchantment_rules_expected_1211.json
      the expected 1.21.1 result for every StardewCraft item: per vanilla enchantment the mod items it
      supports (anvil, /enchant, enchant_randomly) and is primary for (enchanting table, enchant_with_levels),
      plus the 1.21.1 incompatible vanilla pairs. Item tag membership is resolved statically from the 1.21.1
      vanilla jar + the mod's 1.21.1 tag files at the main sync snapshot. Read by PortEnchantmentParityGameTests.

    python3 scripts/port/gen_enchantment_rules_1201.py [--mc121-jar JAR] [--mc120-jar JAR] [--snapshot REV]
                                                       [--baseline-dump DUMP.jsonl] [--check]

``--baseline-dump`` cross-checks the static tag resolution against a 1.21.1 PortParityDumpGameTests dump
(defaults to ../StardewCraft-1.21.1-baseline/run-game-test/port-parity/dump.jsonl when it exists).
``--check`` fails instead of writing when a generated file would change.
"""
from __future__ import annotations

import argparse
import glob
import json
import os
import re
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
NS = "stardewcraft"
# 1.21 renamed sweeping -> sweeping_edge; every other surviving vanilla id is unchanged.
RENAMED_1211_TO_1201 = {"minecraft:sweeping_edge": "minecraft:sweeping"}

JAVA_OUT = ROOT / "src/port/java/com/stardew/craft/port/PortVanillaEnchantmentTable.java"
ITEM_TAG_OUT = ROOT / "src/port/resources/data/minecraft/tags/items"
ENCH_TAG_OUT = ROOT / "src/port/resources/data/minecraft/tags/enchantment"
EXPECTED_OUT = ROOT / "src/port/resources/port-parity/enchantment_rules_expected_1211.json"


def die(msg: str) -> None:
    print("error: " + msg, file=sys.stderr)
    sys.exit(1)


def first_existing(patterns: list[str]) -> str | None:
    for pattern in patterns:
        hits = sorted(glob.glob(os.path.expanduser(pattern)))
        if hits:
            return hits[0]
    return None


def rl(value: str) -> str:
    return value if ":" in value else "minecraft:" + value


def entry_id(entry) -> tuple[str, bool]:
    """Tag entry -> (id or #tag, required)."""
    if isinstance(entry, str):
        return entry, True
    return entry["id"], entry.get("required", True)


# ------------------------------------------------------------------ inputs

def jar_tags(jar: zipfile.ZipFile, directory: str) -> dict[str, dict]:
    """{"ns:path": json} for data/<ns>/<directory>/<path>.json."""
    out = {}
    pattern = re.compile(r"^data/([^/]+)/" + re.escape(directory) + r"/(.+)\.json$")
    for name in jar.namelist():
        m = pattern.match(name)
        if m:
            out[f"{m.group(1)}:{m.group(2)}"] = json.loads(jar.read(name))
    return out


def snapshot_item_tags(rev: str) -> dict[str, list[dict]]:
    """Mod 1.21.1 item tag files at the main sync snapshot: {"ns:path": [json, ...]}."""
    names = subprocess.run(["git", "-C", str(ROOT), "ls-tree", "-r", "--name-only", rev, "--", "src/main/resources/data"],
                           check=True, capture_output=True, text=True).stdout.split()
    pattern = re.compile(r"^src/main/resources/data/([^/]+)/tags/item/(.+)\.json$")
    out: dict[str, list[dict]] = {}
    for name in names:
        m = pattern.match(name)
        if not m:
            continue
        text = subprocess.run(["git", "-C", str(ROOT), "show", f"{rev}:{name}"], check=True,
                              capture_output=True, text=True).stdout
        out.setdefault(f"{m.group(1)}:{m.group(2)}", []).append(json.loads(text))
    return out


def ids_1201(java_file: Path, pattern: str) -> set[str]:
    text = java_file.read_text(encoding="utf-8")
    return {"minecraft:" + m.lower() if pattern == "items" else "minecraft:" + m
            for m in re.findall(r"public static final Item ([A-Z0-9_]+) =" if pattern == "items"
                                else r'register\(\s*"([a-z0-9_]+)"', text)}


class TagSet:
    """Merged tag definitions (replace semantics of the loader: later replace:true wipes earlier values)."""

    def __init__(self):
        self.defs: dict[str, list] = {}

    def add(self, tag: str, data: dict) -> None:
        if data.get("replace", False):
            self.defs[tag] = []
        self.defs.setdefault(tag, []).extend(data.get("values", []))

    def resolve(self, tag: str, stack=()) -> set[str]:
        if tag not in self.defs:
            raise KeyError(tag)
        if tag in stack:
            die("tag cycle " + " -> ".join(stack + (tag,)))
        out = set()
        for entry in self.defs[tag]:
            value, required = entry_id(entry)
            if value.startswith("#"):
                try:
                    out |= self.resolve(rl(value[1:]), stack + (tag,))
                except KeyError:
                    if required:
                        die(f"tag {tag} references missing tag {value}")
            else:
                out.add(rl(value))
        return out


def holder_set_members(value, tags: TagSet) -> set[str]:
    if isinstance(value, list):
        return {rl(v) for v in value}
    if value.startswith("#"):
        return tags.resolve(rl(value[1:]))
    return {rl(value)}


def referenced_tags(value, vanilla: dict[str, dict]) -> list[str]:
    """Item tags reachable from a HolderSet value through the vanilla 1.21.1 definitions (DFS order)."""
    seen: list[str] = []

    def walk(tag: str):
        if tag in seen:
            return
        seen.append(tag)
        for entry in vanilla[tag].get("values", []):
            v, _ = entry_id(entry)
            if v.startswith("#"):
                walk(rl(v[1:]))

    if isinstance(value, str) and value.startswith("#"):
        walk(rl(value[1:]))
    return seen


# ------------------------------------------------------------------ outputs

def java_string(value) -> str:
    if value is None:
        return "null"
    if isinstance(value, list):
        die("list-valued HolderSet in a vanilla enchantment is not supported by PortVanillaEnchantmentTable")
    return '"' + value + '"'


def render_java(rules: list[dict], source: str) -> str:
    rows = []
    for r in rules:
        rows.append(f'            new Entry("{r["id_1211"]}", "{r["id"]}", {java_string(r["supported_items"])}, '
                    f'{java_string(r["primary_items"])}, {java_string(r["exclusive_set"])})')
    skipped = ", ".join(r for r in RULES_SKIPPED)
    return f"""package com.stardew.craft.port;

import java.util.List;

/**
 * GENERATED by scripts/port/gen_enchantment_rules_1201.py from {source} - do not edit.
 * <p>
 * PORT(1.20.1): the 1.21.1 vanilla enchantment definitions' applicability fields, verbatim HolderSet strings
 * ({{@code "#tag"}} or {{@code "id"}}): {{@code supported_items}} (anvil, {{@code /enchant}}, {{@code enchant_randomly}}),
 * {{@code primary_items}} (enchanting table / {{@code enchant_with_levels}}; {{@code null}} = supported items) and
 * {{@code exclusive_set}}. Only enchantments that exist in 1.20.1 are listed ({{@code id}} is the 1.20.1 id);
 * not in 1.20.1: {skipped}.
 * Applied to StardewCraft items by {{@link PortVanillaEnchantmentRules}}.
 */
final class PortVanillaEnchantmentTable {{
    record Entry(String id1211, String id, String supportedItems, String primaryItems, String exclusiveSet) {{}}

    static final List<Entry> ENTRIES = List.of(
{(","+chr(10)).join(rows)});

    private PortVanillaEnchantmentTable() {{}}
}}
"""


def dump_json(data) -> str:
    return json.dumps(data, indent=2, ensure_ascii=False) + "\n"


RULES_SKIPPED: list[str] = []


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--mc121-jar", default=first_existing([
        "~/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar",
        "~/.gradle/caches/fabric-loom/1.21.1/minecraft-client.jar"]))
    ap.add_argument("--mc120-jar", default=first_existing([
        "~/.gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client-extra.jar"]))
    ap.add_argument("--mc120-src", default=str(ROOT / "build/port-mc-src"))
    ap.add_argument("--snapshot", default=(ROOT / "scripts/port/main-sync-state.txt").read_text().strip())
    ap.add_argument("--baseline-dump", default=str(ROOT.parent / "StardewCraft-1.21.1-baseline/run-game-test/port-parity/dump.jsonl"))
    ap.add_argument("--check", action="store_true")
    args = ap.parse_args()
    if not args.mc121_jar or not args.mc120_jar:
        die("1.21.1 client jar / 1.20.1 client-extra jar not found; pass --mc121-jar/--mc120-jar")

    jar121 = zipfile.ZipFile(args.mc121_jar)
    jar120 = zipfile.ZipFile(args.mc120_jar)
    enchants121 = jar_tags(jar121, "enchantment")
    vanilla_item_tags121 = jar_tags(jar121, "tags/item")
    vanilla_ench_tags121 = jar_tags(jar121, "tags/enchantment")
    vanilla_item_tags120 = jar_tags(jar120, "tags/items")
    mc_src = Path(args.mc120_src)
    items120 = ids_1201(mc_src / "net/minecraft/world/item/Items.java", "items")
    enchant_ids120 = ids_1201(mc_src / "net/minecraft/world/item/enchantment/Enchantments.java", "enchantments")
    if len(items120) < 1000 or len(enchant_ids120) < 30:
        die("could not parse 1.20.1 Items/Enchantments from " + str(mc_src))

    def to1201(ench: str) -> str | None:
        target = RENAMED_1211_TO_1201.get(ench, ench)
        return target if target in enchant_ids120 else None

    # ---- rule table
    rules = []
    RULES_SKIPPED.clear()
    for ench in sorted(enchants121):
        data = enchants121[ench]
        target = to1201(ench)
        if target is None:
            RULES_SKIPPED.append(ench)
            continue
        rules.append({"id_1211": ench, "id": target, "supported_items": data["supported_items"],
                      "primary_items": data.get("primary_items"), "exclusive_set": data.get("exclusive_set")})

    # ---- item tags missing in 1.20.1
    needed: list[str] = []
    for r in rules:
        for key in ("supported_items", "primary_items"):
            if r[key] is not None:
                for t in referenced_tags(r[key], vanilla_item_tags121):
                    if t not in needed:
                        needed.append(t)
    item_tag_files: dict[Path, str] = {}
    for tag in sorted(needed):
        ns, path = tag.split(":", 1)
        if ns != "minecraft":
            die("unexpected non-vanilla tag " + tag)
        values = vanilla_item_tags121[tag]["values"]
        kept, dropped = [], []
        for entry in values:
            v, _ = entry_id(entry)
            if v.startswith("#") or rl(v) in items120:
                kept.append(entry)
            else:
                dropped.append(v)
        if tag in vanilla_item_tags120:
            old = {entry_id(e)[0] for e in vanilla_item_tags120[tag]["values"]}
            extra = [e for e in kept if entry_id(e)[0] not in old]
            if extra:
                die(f"vanilla 1.20.1 tag {tag} lacks 1.21.1 members {extra}; decide how to port them")
            continue  # same membership in both versions: nothing to add
        if (ROOT / "src/main/resources/data/minecraft/tags/items" / (path + ".json")).exists():
            die(f"src/main/resources already defines {tag}; merge the vanilla members there instead")
        item_tag_files[ITEM_TAG_OUT / (path + ".json")] = dump_json({"replace": False, "values": kept})
        if dropped:
            print(f"  {tag}: dropped 1.21-only {dropped}")

    # ---- exclusive-set enchantment tags
    ench_tag_files: dict[Path, str] = {}
    for tag in sorted(t for t in vanilla_ench_tags121 if t.startswith("minecraft:exclusive_set/")):
        kept = []
        for entry in vanilla_ench_tags121[tag]["values"]:
            v, required = entry_id(entry)
            if v.startswith("#"):
                die("nested enchantment tag in " + tag)
            target = to1201(rl(v))
            if target is not None:
                kept.append(target)
        ench_tag_files[ENCH_TAG_OUT / (tag.split(":", 1)[1] + ".json")] = dump_json({"replace": False, "values": kept})
    for r in rules:
        ex = r["exclusive_set"]
        if ex is not None and (not isinstance(ex, str) or not ex.startswith("#minecraft:exclusive_set/")):
            die(f"{r['id_1211']}: exclusive_set {ex} is not a vanilla exclusive_set tag")

    # ---- expected 1.21.1 results for mod items
    tags121 = TagSet()
    for tag, data in vanilla_item_tags121.items():
        tags121.add(tag, data)
    for tag, files in sorted(snapshot_item_tags(args.snapshot).items()):
        for data in files:
            tags121.add(tag, data)
    ench_tags121 = TagSet()
    for tag, data in vanilla_ench_tags121.items():
        ench_tags121.add(tag, data)
    in_table = {to1201(e) for e in ench_tags121.resolve("minecraft:in_enchanting_table")} - {None}

    def mod_only(ids: set[str]) -> list[str]:
        return sorted(i for i in ids if i.startswith(NS + ":"))

    expected_rules = {}
    for r in rules:
        supported = holder_set_members(r["supported_items"], tags121)
        primary = supported & holder_set_members(r["primary_items"], tags121) if r["primary_items"] else supported
        expected_rules[r["id"]] = {
            "supported": mod_only(supported),
            "primary": mod_only(primary),
            "in_enchanting_table": r["id"] in in_table,
        }
    exclusive = {}
    for r in rules:
        ex = r["exclusive_set"]
        exclusive[r["id"]] = set() if ex is None else {to1201(e) for e in ench_tags121.resolve(rl(ex[1:]))} - {None}
    incompatible = sorted({tuple(sorted((a, b))) for a in exclusive for b in exclusive
                           if a != b and (b in exclusive[a] or a in exclusive[b])})
    expected = {
        "_comment": "GENERATED by scripts/port/gen_enchantment_rules_1201.py - expected 1.21.1 vanilla enchantment "
                    "applicability for StardewCraft items (1.20.1 enchantment ids). supported = supported_items "
                    "(anvil, /enchant, enchant_randomly); primary = supported and primary_items (enchanting table); "
                    "incompatible = pairs excluded by 1.21.1 exclusive_set tags (Enchantment.areCompatible).",
        "snapshot": args.snapshot,
        "skipped_1211_only": RULES_SKIPPED,
        "enchantments": expected_rules,
        "incompatible": [list(p) for p in incompatible],
    }

    # ---- static 1.20.1 check: the port's item tags (vanilla 1.20.1 + converted mod resources + the generated
    # files) must give StardewCraft items the same supported/primary sets on 1.20.1 as in 1.21.1.
    tags120 = TagSet()
    for tag, data in vanilla_item_tags120.items():
        tags120.add(tag, data)
    port_files: dict[str, list[dict]] = {}
    for base in (ROOT / "src/main/resources/data", ROOT / "src/port/resources/data"):
        for f in sorted(base.glob("*/tags/items/**/*.json")):
            if f in item_tag_files:
                continue  # replaced by the freshly generated content below
            rel = f.relative_to(base)
            port_files.setdefault(f"{rel.parts[0]}:{'/'.join(rel.parts[3:])[:-5]}", []).append(json.loads(f.read_text("utf-8")))
    for f, text in item_tag_files.items():
        port_files.setdefault("minecraft:" + str(f.relative_to(ITEM_TAG_OUT))[:-5], []).append(json.loads(text))
    for tag, files in sorted(port_files.items()):
        for data in files:
            tags120.add(tag, data)
    port_mismatch = []
    for r in rules:
        supported = holder_set_members(r["supported_items"], tags120)
        primary = supported & holder_set_members(r["primary_items"], tags120) if r["primary_items"] else supported
        if mod_only(supported) != expected_rules[r["id"]]["supported"] or mod_only(primary) != expected_rules[r["id"]]["primary"]:
            port_mismatch.append(r["id"])
    print(f"static 1.20.1 tag resolution vs 1.21.1 expectation: {len(port_mismatch)} mismatching enchantments {port_mismatch}")
    if port_mismatch:
        die("the port's 1.20.1 item tags do not reproduce the 1.21.1 membership")

    # ---- cross-check against a real 1.21.1 dump
    dump = Path(args.baseline_dump)
    if dump.exists():
        dumped: dict[str, list[str]] = {}
        with dump.open(encoding="utf-8") as f:
            for line in f:
                if line.startswith("tag/item\t"):
                    _, key, value = line.rstrip("\n").split("\t", 2)
                    dumped[key] = [m for m in json.loads(value) if m.startswith(NS + ":")]
        mismatches = 0
        for tag in sorted(vanilla_item_tags121):  # the dump only lists tags with a StardewCraft member
            static = mod_only(tags121.resolve(tag))
            if static != sorted(dumped.get(tag, [])):
                mismatches += 1
                print(f"  dump mismatch {tag}: static {len(static)} vs dump {len(dumped.get(tag, []))}")
        print(f"cross-checked vanilla item tags against {dump}: {mismatches} mismatches")
        if mismatches:
            die("static 1.21.1 tag resolution disagrees with the 1.21.1 runtime dump")

    outputs = {JAVA_OUT: render_java(rules, Path(args.mc121_jar).name), EXPECTED_OUT: dump_json(expected)}
    outputs.update(item_tag_files)
    outputs.update(ench_tag_files)
    stale = [p for p, text in outputs.items() if not p.exists() or p.read_text(encoding="utf-8") != text]
    for directory in (ITEM_TAG_OUT / "enchantable", ENCH_TAG_OUT / "exclusive_set"):
        if directory.exists():
            for f in directory.glob("*.json"):
                if f not in outputs:
                    stale.append(f)
    if args.check:
        if stale:
            die("out of date: " + ", ".join(str(p.relative_to(ROOT)) for p in stale))
        print("up to date")
        return
    for p in stale:
        if p in outputs:
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text(outputs[p], encoding="utf-8")
        else:
            p.unlink()
    print(f"{len(rules)} rules ({len(RULES_SKIPPED)} 1.21-only skipped), {len(item_tag_files)} item tags, "
          f"{len(ench_tag_files)} enchantment tags; wrote {len(stale)} file(s)")
    for ench, e in expected_rules.items():
        if e["supported"]:
            print(f"  {ench}: supported {len(e['supported'])}, primary {len(e['primary'])}")


if __name__ == "__main__":
    main()

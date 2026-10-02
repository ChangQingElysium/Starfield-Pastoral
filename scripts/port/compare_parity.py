#!/usr/bin/env python3
"""Compare the 1.21.1 and 1.20.1 content dumps written by PortParityDumpGameTests.

    python3 scripts/port/compare_parity.py <baseline.jsonl> <port.jsonl> [--approved docs/porting/parity-approved.tsv]

Each dump line is "section<TAB>key<TAB>json". The approved file lists accepted differences as
"section<TAB>key<TAB>reason" (key may end with * as a prefix wildcard). Exit code 1 when an
unapproved difference remains.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_FACTS = ROOT / "scripts/port/fixtures/mc1201-registry-facts.json"
REQUIRED_REGISTRIES = {"minecraft:item", "minecraft:block", "minecraft:entity_type", "minecraft:fluid",
                       "minecraft:attribute", "minecraft:enchantment"}
RESOURCE_ID = re.compile(r"^[a-z0-9_.-]+:[a-z0-9_./-]+$")


def canonical_json(value) -> str:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False)


def load_registry_facts(path: Path = DEFAULT_FACTS) -> dict:
    """Tracked facts captured from a real 1.20.1 registry; never inspect ignored build sources."""
    facts = json.loads(path.read_text(encoding="utf-8"))
    fields = {"schema", "minecraft_version", "registries", "port_attribute_ids", "sha256"}
    if not isinstance(facts, dict) or set(facts) != fields:
        raise ValueError("registry facts schema fields differ")
    if type(facts["schema"]) is not int or facts["schema"] != 1:
        raise ValueError("unsupported registry facts schema")
    if facts["minecraft_version"] != "1.20.1":
        raise ValueError("registry facts must come from Minecraft 1.20.1")
    registries = facts["registries"]
    if not isinstance(registries, dict) or set(registries) != REQUIRED_REGISTRIES:
        raise ValueError("registry facts must contain all six vanilla registries")
    for registry, ids in registries.items():
        if (not isinstance(ids, list) or not ids or
                any(not isinstance(i, str) or not RESOURCE_ID.fullmatch(i) or not i.startswith("minecraft:") for i in ids) or
                ids != sorted(set(ids))):
            raise ValueError(f"registry facts ids must be non-empty, sorted, unique vanilla ids: {registry}")
    if facts["port_attribute_ids"] != ["minecraft:generic.scale", "minecraft:generic.step_height"]:
        raise ValueError("registry facts must identify exactly the two actual port attribute registrations")
    if set(facts["port_attribute_ids"]) & set(registries["minecraft:attribute"]):
        raise ValueError("port attributes must not be labelled vanilla registry facts")
    payload = {k: v for k, v in facts.items() if k != "sha256"}
    expected = hashlib.sha256(canonical_json(payload).encode("utf-8")).hexdigest()
    if facts["sha256"] != expected:
        raise ValueError("registry facts sha256 mismatch")
    return facts


def load(path: Path) -> dict[tuple[str, str], str]:
    out = {}
    with path.open(encoding="utf-8") as f:
        for number, line in enumerate(f, 1):
            section, key, value = line.rstrip("\n").split("\t", 2)
            if (section, key) in out:
                raise ValueError(f"duplicate dump key at {path}:{number}: {section}/{key}")
            json.loads(value)
            out[(section, key)] = value
    if not out:
        raise ValueError(f"empty content dump: {path}")
    return out


RENAMES_121_TO_120 = {"minecraft:short_grass": "minecraft:grass"}
PORT_ATTRIBUTE_NAMES = {"stardewcraft:generic.step_height": "minecraft:generic.step_height",
                        "stardewcraft:generic.scale": "minecraft:generic.scale"}
# These exact defaults were observed in the frozen baseline and corresponding Forge entity dump.
# A non-default or an unknown attribute is NOT discarded: it may represent lost gameplay behavior.
VERSION_DEFAULT_ATTRIBUTES = {
    "minecraft:generic.burning_time": 1.0, "minecraft:generic.explosion_knockback_resistance": 0.0,
    "minecraft:generic.fall_damage_multiplier": 1.0, "minecraft:generic.gravity": 0.08,
    "minecraft:generic.jump_strength": 0.42, "minecraft:generic.max_absorption": 0.0,
    "minecraft:generic.movement_efficiency": 0.0, "minecraft:generic.oxygen_bonus": 0.0,
    "minecraft:generic.safe_fall_distance": 3.0, "minecraft:generic.water_movement_efficiency": 0.0,
    "minecraft:generic.step_height": 0.6, "minecraft:generic.scale": 1.0,
    "forge:step_height_addition": 0.0,
}
LOADER_ATTRIBUTE_NAMES = {"forge:swim_speed": "neoforge:swim_speed",
                          "forge:nametag_distance": "neoforge:nametag_distance",
                          "forge:entity_gravity": "minecraft:generic.gravity"}
PORT_REGISTRY_IDS = {
    "minecraft:attribute": set(PORT_ATTRIBUTE_NAMES) | set(PORT_ATTRIBUTE_NAMES.values()),
    "minecraft:particle_type": {"stardewcraft:port_entity_effect"},
    "minecraft:block_entity_type": {"stardewcraft:port_machine_extension"},
}


def normalizer(facts: dict | None = None):
    """Only documented version facts; registry membership uses the correct registry, not a union."""
    facts = load_registry_facts() if facts is None else facts
    ids = {key: set(value) for key, value in facts["registries"].items()}

    def norm_id(x):
        if isinstance(x, str):
            return RENAMES_121_TO_120.get(x, x)
        return x

    def apply(section, key, value, *, is_port=False):
        v = json.loads(value)
        if section.startswith("tag/") and isinstance(v, list):
            known = ids.get("minecraft:" + section[len("tag/"):])
            if known is None:
                raise ValueError(f"no registry facts for {section}")
            # Only the 1.21 baseline can legitimately name vanilla content unavailable in 1.20.
            # Preserve an unexpected port member so it cannot disappear from the comparison.
            v = sorted({norm_id(m) for m in v if not (not is_port and isinstance(m, str) and
                        m.startswith("minecraft:") and norm_id(m) not in known)})
        elif section == "entity" and isinstance(v, dict) and isinstance(v.get("attributes"), dict):
            out = {}
            for a, val in v["attributes"].items():
                a = LOADER_ATTRIBUTE_NAMES.get(a, PORT_ATTRIBUTE_NAMES.get(a, a))
                if a in VERSION_DEFAULT_ATTRIBUTES and val == VERSION_DEFAULT_ATTRIBUTES[a]:
                    continue
                if a in out and out[a] != val:
                    raise ValueError(f"conflicting attribute aliases for {key}: {a}")
                out[a] = val
            v["attributes"] = out
        return canonical_json(v)
    return apply


def normalize_dump(data, apply, *, is_port=False):
    out = {}
    for (section, key), value in data.items():
        value = apply(section, key, value, is_port=is_port)
        if section in ("registry", "dynamic_registry"):
            if key == "forge:fluid_type":
                key = "neoforge:fluid_type"  # Loader key rename, not exemption of its registered ids.
            ids = json.loads(value)
            if (not isinstance(ids, list) or any(not isinstance(i, str) or not RESOURCE_ID.fullmatch(i) for i in ids) or
                    len(ids) != len(set(ids))):
                raise ValueError(f"invalid registry id list: {section}/{key}")
            if is_port:
                ids = [i for i in ids if i not in PORT_REGISTRY_IDS.get(key, ())]
            if not ids:
                continue
            value = canonical_json(sorted(ids))
        if (section, key) in out and out[(section, key)] != value:
            raise ValueError(f"conflicting registry aliases: {section}/{key}")
        out[(section, key)] = value
    # 1.21 enchantments are dynamic; 1.20 exposes the same ids in both views. Verify agreement first.
    static_key = ("registry", "minecraft:enchantment")
    dynamic_key = ("dynamic_registry", "minecraft:enchantment")
    static, dynamic = out.get(static_key), out.get(dynamic_key)
    if static is not None and dynamic is not None and static != dynamic:
        raise ValueError("static/dynamic enchantment registry ids disagree")
    if static is not None:
        out[dynamic_key] = static
        del out[static_key]
    return out


def load_approved(path: Path | None):
    rules = []
    if path and path.exists():
        for line in path.read_text(encoding="utf-8").splitlines():
            if not line.strip() or line.startswith("#"):
                continue
            section, key, *_ = line.split("\t")
            rules.append((section, key))
    return rules


def approved(rules, section, key) -> bool:
    # Never exempt an entire registry, even when an old approval file still contains a broad rule.
    if section in ("registry", "dynamic_registry"):
        return False
    for s, k in rules:
        if s != section:
            continue
        if k == key or (k.endswith("*") and key.startswith(k[:-1])):
            return True
    return False


def json_diff(a, b, path="") -> list[str]:
    if type(a) != type(b):
        return [f"{path or '.'}: {json.dumps(a)[:120]} -> {json.dumps(b)[:120]}"]
    if isinstance(a, dict):
        out = []
        for k in sorted(set(a) | set(b)):
            if k not in a:
                out.append(f"{path}/{k}: <missing> -> {json.dumps(b[k])[:120]}")
            elif k not in b:
                out.append(f"{path}/{k}: {json.dumps(a[k])[:120]} -> <missing>")
            else:
                out += json_diff(a[k], b[k], f"{path}/{k}")
        return out
    if isinstance(a, list):
        if len(a) != len(b):
            sa, sb = {json.dumps(x, sort_keys=True) for x in a}, {json.dumps(x, sort_keys=True) for x in b}
            return [f"{path}: len {len(a)} -> {len(b)}; only-baseline {sorted(sa - sb)[:3]}; only-port {sorted(sb - sa)[:3]}"]
        out = []
        for i, (x, y) in enumerate(zip(a, b)):
            out += json_diff(x, y, f"{path}[{i}]")
        return out
    return [] if a == b else [f"{path or '.'}: {json.dumps(a)[:120]} -> {json.dumps(b)[:120]}"]


def differences(base, port, rules, *, content_only=False):
    per_section = defaultdict(list)
    for k in sorted(set(base) | set(port)):
        if content_only and k[0] == "gametest":
            continue
        if approved(rules, *k):
            continue
        a, b = base.get(k), port.get(k)
        if a == b:
            continue
        if a is None:
            per_section[k[0]].append((k[1], ["only in port"]))
        elif b is None:
            per_section[k[0]].append((k[1], ["missing in port"]))
        else:
            per_section[k[0]].append((k[1], json_diff(json.loads(a), json.loads(b))))
    return per_section


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("baseline")
    ap.add_argument("port")
    ap.add_argument("--approved", default=str(ROOT / "docs/porting/parity-approved.tsv"))
    ap.add_argument("--facts", type=Path, default=DEFAULT_FACTS, help="tracked runtime-derived 1.20.1 registry facts")
    ap.add_argument("--samples", type=int, default=5)
    ap.add_argument("--raw", action="store_true", help="skip 1.21->1.20.1 version-fact normalisation")
    ap.add_argument("--content-only", action="store_true", help="explicitly exclude selected GameTest lists; does not prove test coverage")
    args = ap.parse_args()
    try:
        base, port = load(Path(args.baseline)), load(Path(args.port))
        rules = load_approved(Path(args.approved))
        if not args.raw:
            apply = normalizer(load_registry_facts(args.facts))
            base = normalize_dump(base, apply)
            port = normalize_dump(port, apply, is_port=True)
        per_section = differences(base, port, rules, content_only=args.content_only)
    except (OSError, ValueError, TypeError) as error:
        ap.error(str(error))
    if args.content_only:
        print("content-only: excluded selected GameTest lists "
              f"(baseline {sum(s == 'gametest' for s, _ in base)}, port {sum(s == 'gametest' for s, _ in port)}); "
              "test coverage must be verified separately")
    total = Counter({s: len(v) for s, v in per_section.items()})
    print(f"baseline entries {len(base)}, port entries {len(port)}, unapproved differing keys {sum(total.values())}")
    for section, n in total.most_common():
        print(f"\n## {section}: {n}")
        for key, diffs in per_section[section][:args.samples]:
            print(f"  {key}")
            for d in diffs[:4]:
                print(f"      {d}")
    return 1 if total else 0


if __name__ == "__main__":
    sys.exit(main())

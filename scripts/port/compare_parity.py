#!/usr/bin/env python3
"""Compare the 1.21.1 and 1.20.1 content dumps written by PortParityDumpGameTests.

    python3 scripts/port/compare_parity.py <baseline.jsonl> <port.jsonl> [--approved docs/porting/parity-approved.tsv]

Each dump line is "section<TAB>key<TAB>json". The approved file lists accepted differences as
"section<TAB>key<TAB>reason" (key may end with * as a prefix wildcard). Exit code 1 when an
unapproved difference remains.
"""
from __future__ import annotations

import argparse
import json
import sys
from collections import Counter, defaultdict
from pathlib import Path


def load(path: Path) -> dict[tuple[str, str], str]:
    out = {}
    with path.open(encoding="utf-8") as f:
        for line in f:
            section, key, value = line.rstrip("\n").split("\t", 2)
            out[(section, key)] = value
    return out


RENAMES_121_TO_120 = {"minecraft:short_grass": "minecraft:grass"}
PORT_ATTRIBUTE_NAMES = {"stardewcraft:generic.step_height": "minecraft:generic.step_height",
                        "stardewcraft:generic.scale": "minecraft:generic.scale"}


def normalizer():
    """Version facts that are not port differences: 1.21-only vanilla ids, loader-specific attributes,
    1.20.3 renames, and the port's namespaced stand-ins for 1.21-only attributes."""
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    from import_neoforge_common_data import known_ids
    ids = known_ids()
    known = ids["item"] | ids["block"] | ids["entity_type"] | ids["fluid"]
    attr_src = (Path(__file__).resolve().parents[2] / "build/port-mc-src/net/minecraft/world/entity/ai/attributes/Attributes.java").read_text()
    import re as _re
    attrs_120 = {"minecraft:" + n for n in _re.findall(r'"([a-z_.]+)"', attr_src) if "." in n}

    def norm_id(x):
        if isinstance(x, str):
            return RENAMES_121_TO_120.get(x, x)
        return x

    def apply(section, key, value):
        v = json.loads(value)
        if section.startswith("tag/") and isinstance(v, list):
            v = sorted({norm_id(m) for m in v if not (isinstance(m, str) and m.startswith("minecraft:") and norm_id(m) not in known)})
        elif section == "entity" and isinstance(v, dict) and isinstance(v.get("attributes"), dict):
            out = {}
            for a, val in v["attributes"].items():
                a = PORT_ATTRIBUTE_NAMES.get(a, a)
                if a.startswith(("forge:", "neoforge:")):
                    continue
                if a.startswith("minecraft:") and a not in attrs_120 and a not in PORT_ATTRIBUTE_NAMES.values():
                    continue
                # 1.21 default-valued attributes equal 1.20.1's built-in defaults: LivingEntity sets
                # maxUpStep 0.6 (attribute default 0.6) and every entity has scale 1.
                if (a, val) in (("minecraft:generic.step_height", 0.6), ("minecraft:generic.scale", 1.0)):
                    continue
                out[a] = val
            v["attributes"] = out
        return json.dumps(v, sort_keys=True)
    return apply


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


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("baseline")
    ap.add_argument("port")
    ap.add_argument("--approved", default="docs/porting/parity-approved.tsv")
    ap.add_argument("--samples", type=int, default=5)
    ap.add_argument("--raw", action="store_true", help="skip 1.21->1.20.1 version-fact normalisation")
    args = ap.parse_args()
    base, port = load(Path(args.baseline)), load(Path(args.port))
    rules = load_approved(Path(args.approved))
    if not args.raw:
        apply = normalizer()
        base = {k: apply(k[0], k[1], v) for k, v in base.items()}
        port = {k: apply(k[0], k[1], v) for k, v in port.items()}
    per_section = defaultdict(list)
    for k in sorted(set(base) | set(port)):
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

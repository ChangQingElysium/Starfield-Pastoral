#!/usr/bin/env python3
"""Downgrade 1.21.1 binary NBT (structure .nbt, region .mca) to the 1.20.1 format.

    python3 scripts/port/nbt_downgrade_1201.py survey   # report unknown ids, component keys
    python3 scripts/port/nbt_downgrade_1201.py convert  # rewrite files in place (idempotent)

1.20.1 cannot downgrade newer data itself (DataFixers only upgrade), so every file
is rewritten to DataVersion 3465 with:
- block palette ids renamed/replaced where 1.20.1 lacks the 1.21 block,
- item stacks converted from {id,count,components} to {id,Count:b,tag},
- the root DataVersion lowered.
Dependency-free: contains its own NBT codec.
"""
from __future__ import annotations

import gzip
import io
import struct
import sys
import zlib
import zipfile
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "src/main/resources"
TARGET_DATA_VERSION = 3465  # 1.20.1

# ---------------------------------------------------------------- NBT codec
END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY, LONG_ARRAY = range(13)


class Tag:
    __slots__ = ("type", "value", "elem")  # elem: element type for LIST

    def __init__(self, type_, value, elem=END):
        self.type, self.value, self.elem = type_, value, elem

    def __eq__(self, other):
        return isinstance(other, Tag) and (self.type, self.value, self.elem) == (other.type, other.value, other.elem)

    def __repr__(self):
        return f"Tag({self.type},{self.value!r})"


def _read_payload(t, buf):
    if t == BYTE: return struct.unpack(">b", buf.read(1))[0]
    if t == SHORT: return struct.unpack(">h", buf.read(2))[0]
    if t == INT: return struct.unpack(">i", buf.read(4))[0]
    if t == LONG: return struct.unpack(">q", buf.read(8))[0]
    if t == FLOAT: return struct.unpack(">f", buf.read(4))[0]
    if t == DOUBLE: return struct.unpack(">d", buf.read(8))[0]
    if t == BYTE_ARRAY:
        n = struct.unpack(">i", buf.read(4))[0]; return buf.read(n)
    if t == STRING:
        n = struct.unpack(">H", buf.read(2))[0]; return buf.read(n)  # keep raw MUTF-8 bytes
    if t == LIST:
        et = buf.read(1)[0]; n = struct.unpack(">i", buf.read(4))[0]
        return (et, [Tag(et, *_read_payload_e(et, buf)) for _ in range(n)])
    if t == COMPOUND:
        out = {}
        while True:
            ct = buf.read(1)[0]
            if ct == END: return out
            name = buf.read(struct.unpack(">H", buf.read(2))[0])
            v, e = _read_payload_e(ct, buf)
            out[name] = Tag(ct, v, e)
    if t == INT_ARRAY:
        n = struct.unpack(">i", buf.read(4))[0]; return list(struct.unpack(f">{n}i", buf.read(4 * n)))
    if t == LONG_ARRAY:
        n = struct.unpack(">i", buf.read(4))[0]; return list(struct.unpack(f">{n}q", buf.read(8 * n)))
    raise ValueError(f"bad tag type {t}")


def _read_payload_e(t, buf):
    v = _read_payload(t, buf)
    if t == LIST:
        et, items = v
        return items, et
    return v, END


def read_root(data: bytes) -> tuple[bytes, Tag]:
    buf = io.BytesIO(data)
    t = buf.read(1)[0]
    assert t == COMPOUND, t
    name = buf.read(struct.unpack(">H", buf.read(2))[0])
    v, _ = _read_payload_e(COMPOUND, buf)
    return name, Tag(COMPOUND, v)


def _write_payload(tag: Tag, out: io.BytesIO):
    t, v = tag.type, tag.value
    if t == BYTE: out.write(struct.pack(">b", v))
    elif t == SHORT: out.write(struct.pack(">h", v))
    elif t == INT: out.write(struct.pack(">i", v))
    elif t == LONG: out.write(struct.pack(">q", v))
    elif t == FLOAT: out.write(struct.pack(">f", v))
    elif t == DOUBLE: out.write(struct.pack(">d", v))
    elif t == BYTE_ARRAY: out.write(struct.pack(">i", len(v))); out.write(v)
    elif t == STRING: out.write(struct.pack(">H", len(v))); out.write(v)
    elif t == LIST:
        et = tag.elem if v else (tag.elem or END)
        out.write(bytes([et])); out.write(struct.pack(">i", len(v)))
        for item in v: _write_payload(item, out)
    elif t == COMPOUND:
        for name, child in v.items():
            out.write(bytes([child.type])); out.write(struct.pack(">H", len(name))); out.write(name)
            _write_payload(child, out)
        out.write(b"\x00")
    elif t == INT_ARRAY: out.write(struct.pack(">i", len(v))); out.write(struct.pack(f">{len(v)}i", *v))
    elif t == LONG_ARRAY: out.write(struct.pack(">i", len(v))); out.write(struct.pack(f">{len(v)}q", *v))
    else: raise ValueError(t)


def write_root(name: bytes, tag: Tag) -> bytes:
    out = io.BytesIO()
    out.write(bytes([COMPOUND])); out.write(struct.pack(">H", len(name))); out.write(name)
    _write_payload(tag, out)
    return out.getvalue()


def s(text: str) -> Tag: return Tag(STRING, text.encode("utf-8"))
def text(tag: Tag) -> str: return tag.value.decode("utf-8", "replace")
def get(compound: Tag, key: str): return compound.value.get(key.encode())


# ---------------------------------------------------------------- 1.20.1 ids
def load_vanilla_ids():
    cp = (ROOT / "build/port-classpath.txt").read_text().split(":")
    jar = next(p for p in cp if p.endswith("client-extra.jar"))
    blocks, items = set(), set()
    with zipfile.ZipFile(jar) as z:
        for n in z.namelist():
            if n.startswith("assets/minecraft/blockstates/") and n.endswith(".json"):
                blocks.add("minecraft:" + n.rsplit("/", 1)[1][:-5])
            elif n.startswith("assets/minecraft/models/item/") and n.endswith(".json"):
                items.add("minecraft:" + n.rsplit("/", 1)[1][:-5])
    blocks.update({"minecraft:air", "minecraft:cave_air", "minecraft:void_air", "minecraft:moving_piston"})
    return blocks, items


# 1.21 block id -> 1.20.1 replacement (renames first, then closest look-alikes).
# Visual substitutes are a reviewable art decision, recorded in docs/porting/bulk-port-gaps.md.
BLOCK_MAP = {
    "minecraft:short_grass": "minecraft:grass",
    # Tuff family (1.20.3) -> andesite/stone brick family with matching shapes.
    "minecraft:polished_tuff": "minecraft:polished_andesite",
    "minecraft:polished_tuff_stairs": "minecraft:polished_andesite_stairs",
    "minecraft:polished_tuff_slab": "minecraft:polished_andesite_slab",
    "minecraft:polished_tuff_wall": "minecraft:andesite_wall",
    "minecraft:tuff_wall": "minecraft:andesite_wall",
    "minecraft:tuff_slab": "minecraft:andesite_slab",
    "minecraft:tuff_stairs": "minecraft:andesite_stairs",
    "minecraft:tuff_bricks": "minecraft:stone_bricks",
    "minecraft:tuff_brick_stairs": "minecraft:stone_brick_stairs",
    "minecraft:tuff_brick_slab": "minecraft:stone_brick_slab",
    "minecraft:tuff_brick_wall": "minecraft:stone_brick_wall",
    "minecraft:chiseled_tuff": "minecraft:chiseled_stone_bricks",
    "minecraft:chiseled_tuff_bricks": "minecraft:chiseled_stone_bricks",
}
_OXIDATION = ["", "exposed_", "weathered_", "oxidized_"]
for _wax in ("", "waxed_"):
    for _ox in _OXIDATION:
        # Copper doors/trapdoors share every blockstate property with the iron ones.
        BLOCK_MAP[f"minecraft:{_wax}{_ox}copper_door"] = "minecraft:iron_door"
        BLOCK_MAP[f"minecraft:{_wax}{_ox}copper_trapdoor"] = "minecraft:iron_trapdoor"
        # Grates and bulbs become the matching cut copper (bulb light handled in map_block_state).
        BLOCK_MAP[f"minecraft:{_wax}{_ox}copper_grate"] = f"minecraft:{_wax}{_ox}cut_copper"
        BLOCK_MAP[f"minecraft:{_wax}{_ox}copper_bulb"] = f"minecraft:{_wax}{_ox}cut_copper"
        BLOCK_MAP[f"minecraft:{_wax}{_ox}chiseled_copper"] = f"minecraft:{_wax}{_ox}cut_copper"
LIT_BULB_SUBSTITUTE = "minecraft:ochre_froglight"  # keeps the light (bulb lit = 15)
ITEM_MAP = {
    "minecraft:short_grass": "minecraft:grass",
}

# ---------------------------------------------------------------- conversion
stats = Counter()
unknown_blocks, unknown_items, component_keys = Counter(), Counter(), Counter()
VANILLA_BLOCKS, VANILLA_ITEMS = set(), set()


def map_block(name: str) -> str:
    new = BLOCK_MAP.get(name, name)
    if new.startswith("minecraft:") and new not in VANILLA_BLOCKS:
        unknown_blocks[new] += 1
    return new


def map_item(name: str) -> str:
    new = ITEM_MAP.get(name, name)
    if new.startswith("minecraft:") and new not in VANILLA_ITEMS:
        unknown_items[new] += 1
    return new


def convert_components(components: Tag, out_tag: dict) -> None:
    """Map 1.21 data components onto the 1.20.1 ItemStack `tag` compound."""
    for key, value in list(components.value.items()):
        k = key.decode()
        component_keys[k] += 1
        if k == "minecraft:custom_data" and value.type == COMPOUND:
            for ck, cv in value.value.items():
                out_tag[ck] = cv
        elif k == "minecraft:custom_model_data":
            out_tag[b"CustomModelData"] = Tag(INT, value.value)
        elif k == "minecraft:block_state" and value.type == COMPOUND:
            out_tag[b"BlockStateTag"] = value
        elif k == "minecraft:block_entity_data" and value.type == COMPOUND:
            out_tag[b"BlockEntityTag"] = value
        elif k == "minecraft:custom_name":
            display = out_tag.setdefault(b"display", Tag(COMPOUND, {}))
            display.value[b"Name"] = value
        elif k == "minecraft:damage":
            out_tag[b"Damage"] = Tag(INT, value.value)
        elif k in ("minecraft:enchantments", "minecraft:stored_enchantments") and value.type == COMPOUND:
            levels = get(value, "levels") or value
            out = [Tag(COMPOUND, {b"id": s(lk.decode()), b"lvl": Tag(SHORT, lv.value)})
                   for lk, lv in levels.value.items() if lv.type == INT]
            key_out = b"Enchantments" if k.endswith(":enchantments") else b"StoredEnchantments"
            out_tag[key_out] = Tag(LIST, out, COMPOUND)
        elif k == "minecraft:writable_book_content" and value.type == COMPOUND:
            pages = []
            for page in (get(value, "pages").value if get(value, "pages") is not None else []):
                raw = get(page, "raw") if page.type == COMPOUND else page
                pages.append(s(text(raw)))
            out_tag[b"pages"] = Tag(LIST, pages, STRING)
        elif k == "minecraft:profile":
            out_tag[b"SkullOwner"] = convert_profile(value)
        else:
            stats["unmapped component " + k] += 1


def convert_profile(profile: Tag) -> Tag:
    """1.21 ResolvableProfile -> 1.20.1 SkullOwner compound."""
    if profile.type == STRING:
        return Tag(COMPOUND, {b"Name": profile})
    out = {}
    if get(profile, "name") is not None:
        out[b"Name"] = get(profile, "name")
    if get(profile, "id") is not None:
        out[b"Id"] = get(profile, "id")
    props = get(profile, "properties")
    if props is not None and props.type == LIST:
        textures = []
        for p in props.value:
            t = {b"Value": get(p, "value")}
            if get(p, "signature") is not None:
                t[b"Signature"] = get(p, "signature")
            textures.append(Tag(COMPOUND, t))
        out[b"Properties"] = Tag(COMPOUND, {b"textures": Tag(LIST, textures, COMPOUND)})
    return Tag(COMPOUND, out)


def convert_item(stack: Tag) -> None:
    v = stack.value
    if b"count" in v and v[b"count"].type == INT:
        count = v.pop(b"count").value
        v[b"Count"] = Tag(BYTE, max(-128, min(127, count)))
    if b"id" in v and v[b"id"].type == STRING:
        v[b"id"] = s(map_item(text(v[b"id"])))
    comps = v.pop(b"components", None)
    if comps is not None and comps.type == COMPOUND:
        tag = v.get(b"tag")
        out = dict(tag.value) if tag is not None else {}
        convert_components(comps, out)
        if out:
            v[b"tag"] = Tag(COMPOUND, out)
    stats["items"] += 1


def looks_like_item(c: Tag) -> bool:
    v = c.value
    if b"id" not in v or v[b"id"].type != STRING:
        return False
    if b"count" in v and v[b"count"].type == INT:
        return True
    return b"components" in v and b"Count" not in v and b"x" not in v


def walk(tag: Tag) -> None:
    if tag.type == COMPOUND:
        if looks_like_item(tag):
            convert_item(tag)
        be_id = get(tag, "id")
        if be_id is not None and be_id.type == STRING and text(be_id) == "minecraft:skull" \
                and get(tag, "profile") is not None:
            tag.value[b"SkullOwner"] = convert_profile(tag.value.pop(b"profile"))
            stats["skull block entities"] += 1
        for child in tag.value.values():
            walk(child)
    elif tag.type == LIST:
        for child in tag.value:
            walk(child)


def convert_palette(palette: Tag) -> None:
    for entry in palette.value:
        name = get(entry, "Name")
        if name is None:
            continue
        old = text(name)
        props = get(entry, "Properties")
        if old.endswith("copper_bulb") and props is not None and get(props, "lit") is not None \
                and text(get(props, "lit")) == "true":
            entry.value[b"Name"] = s(LIT_BULB_SUBSTITUTE)
            entry.value.pop(b"Properties", None)
            continue
        new = map_block(old)
        entry.value[b"Name"] = s(new)
        if new != old and props is not None and (new.endswith("cut_copper") or new.endswith("bricks")
                                                 or new.endswith("polished_andesite")):
            entry.value.pop(b"Properties", None)


def convert_structure(root: Tag) -> None:
    pal = get(root, "palette")
    if pal is not None:
        convert_palette(pal)
    for pals in (get(root, "palettes").value if get(root, "palettes") is not None else []):
        convert_palette(pals)
    for key in ("blocks", "entities"):
        if get(root, key) is not None:
            walk(get(root, key))
    if get(root, "DataVersion") is not None:
        root.value[b"DataVersion"] = Tag(INT, TARGET_DATA_VERSION)


def convert_chunk(root: Tag) -> None:
    for section in (get(root, "sections").value if get(root, "sections") is not None else []):
        states = get(section, "block_states")
        if states is not None and get(states, "palette") is not None:
            convert_palette(get(states, "palette"))
    for key in ("block_entities", "entities"):
        if get(root, key) is not None:
            walk(get(root, key))
    if get(root, "DataVersion") is not None:
        root.value[b"DataVersion"] = Tag(INT, TARGET_DATA_VERSION)


def process_structure(path: Path, write: bool) -> bool:
    raw = path.read_bytes()
    gz = raw[:2] == b"\x1f\x8b"
    name, root = read_root(gzip.decompress(raw) if gz else raw)
    before = write_root(name, root)
    convert_structure(root)
    after = write_root(name, root)
    if after == before:
        return False
    if write:
        path.write_bytes(gzip.compress(after, mtime=0) if gz else after)
    return True


def process_region(path: Path, write: bool) -> bool:
    data = bytearray(path.read_bytes())
    if len(data) < 8192:
        return False
    changed = False
    chunks = []
    for i in range(1024):
        off = struct.unpack(">I", data[i * 4:i * 4 + 4])[0]
        sector, count = off >> 8, off & 0xFF
        ts = data[4096 + i * 4:4096 + i * 4 + 4]
        if sector == 0:
            chunks.append(None); continue
        start = sector * 4096
        length = struct.unpack(">I", data[start:start + 4])[0]
        ctype = data[start + 4]
        payload = bytes(data[start + 5:start + 4 + length])
        if ctype == 2: raw = zlib.decompress(payload)
        elif ctype == 1: raw = gzip.decompress(payload)
        elif ctype == 3: raw = payload
        else: raise SystemExit(f"{path}: unsupported chunk compression {ctype}")
        name, root = read_root(raw)
        before = write_root(name, root)
        convert_chunk(root)
        after = write_root(name, root)
        if after != before:
            changed = True
        chunks.append((ts, after))
    if write and changed:
        out = bytearray(8192)
        sector = 2
        for i, entry in enumerate(chunks):
            if entry is None: continue
            ts, raw = entry
            comp = zlib.compress(raw)
            blob = struct.pack(">I", len(comp) + 1) + b"\x02" + comp
            blob += b"\x00" * ((-len(blob)) % 4096)
            n = len(blob) // 4096
            out[i * 4:i * 4 + 4] = struct.pack(">I", (sector << 8) | n)
            out[4096 + i * 4:4096 + i * 4 + 4] = ts
            out += blob
            sector += n
        path.write_bytes(bytes(out))
    return changed


def refresh_region_manifests() -> int:
    """Region bytes changed, so recompute size/sha256 of every `copy` line."""
    import hashlib
    updated = 0
    for manifest in RES.glob("pregen/*/region_manifest.txt"):
        lines = []
        for line in manifest.read_text(encoding="utf-8").splitlines():
            parts = line.split()
            if len(parts) == 4 and parts[0] == "copy":
                data = (manifest.parent / "region" / parts[1]).read_bytes()
                line = f"copy {parts[1]} {len(data)} {hashlib.sha256(data).hexdigest()}"
                updated += 1
            lines.append(line)
        manifest.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return updated


def main(argv) -> int:
    mode = argv[0] if argv else "survey"
    write = mode == "convert"
    global VANILLA_BLOCKS, VANILLA_ITEMS
    VANILLA_BLOCKS, VANILLA_ITEMS = load_vanilla_ids()
    structures = sorted(RES.glob("data/*/structures/**/*.nbt"))
    regions = sorted(RES.glob("pregen/**/*.mca"))
    changed = sum(process_structure(p, write) for p in structures)
    changed += sum(process_region(p, write) for p in regions)
    print(f"structures: {len(structures)}, regions: {len(regions)}, files needing change: {changed}"
          + (" (written)" if write else ""))
    if write:
        print("manifest entries refreshed:", refresh_region_manifests())
    print("items converted:", stats["items"])
    print("unknown 1.20.1 blocks:", unknown_blocks.most_common())
    print("unknown 1.20.1 items:", unknown_items.most_common())
    print("component keys:", component_keys.most_common())
    print("other:", [(k, v) for k, v in stats.items() if k != "items"])
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

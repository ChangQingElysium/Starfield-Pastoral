#!/usr/bin/env python3
"""Idempotent adaptation of the 1.20.5+ block interaction split to Forge 1.20.1.

Usage:
    python3 scripts/port/adapt_block_use.py [--check] [paths...]   (default: src/main/java)

1.21 blocks override ``useItemOn(ItemStack, BlockState, Level, BlockPos, Player, InteractionHand, BlockHitResult)``
returning ``ItemInteractionResult`` and ``useWithoutItem(BlockState, Level, BlockPos, Player, BlockHitResult)``;
1.20.1 only has ``Block#use``. The shim interface ``com.stardew.craft.port.PortBlockInteraction`` declares both
methods with the 1.21 defaults and a static ``dispatch`` that replays the 1.21.1 block phase of
``ServerPlayerGameMode#useItemOn`` / ``MultiPlayerGameMode#performUseItemOn``. This script:

1. widens every ``useItemOn`` / ``useWithoutItem`` override declaration to ``public`` (interface methods are public);
2. makes every "root" class (one that declares such an override and has no mod ancestor that does) implement
   ``PortBlockInteraction`` and inserts a ``use`` bridge calling ``PortBlockInteraction.dispatch``;
3. in root classes whose vanilla superclass has no 1.20.1 ``use`` override, rewrites ``super.useItemOn(`` /
   ``super.useWithoutItem(`` to ``PortBlockInteraction.super.…(`` (the 1.21 ``BlockBehaviour`` defaults);
4. rewrites 1.21 ``BlockState#useItemOn(stack, level, player, hand, hit)`` /
   ``BlockState#useWithoutItem(level, player, hit)`` calls to ``PortBlockInteraction.stateUseItemOn(state, …)`` /
   ``PortBlockInteraction.stateUseWithoutItem(state, …)``.

Cases it cannot decide (vanilla parents with their own 1.20.1 ``use``, multiple candidate classes in one file,
private overrides) are printed as warnings and must be fixed by hand.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
IFACE = "PortBlockInteraction"
IFACE_FQN = "com.stardew.craft.port." + IFACE
MARKER = "PORT(1.20.1): replay the 1.21 useItemOn/useWithoutItem dispatch"

# Vanilla superclasses whose 1.20.1 version does not override Block#use, i.e. whose 1.21 useItemOn/useWithoutItem are
# the BlockBehaviour defaults. Anything else is reported for a hand fix.
PLAIN_VANILLA_PARENTS = {
    "Block", "BaseEntityBlock", "HorizontalDirectionalBlock", "DirectionalBlock", "FarmBlock", "BushBlock",
    "CropBlock", "RotatedPillarBlock", "SlabBlock", "StairBlock", "FallingBlock", "HalfTransparentBlock",
    "GlassBlock", "LeavesBlock", "SaplingBlock", "FlowerBlock", "TallGrassBlock", "DoublePlantBlock", "WallBlock",
    "FenceBlock", "CarpetBlock", "SnowLayerBlock", "IronBarsBlock", "PipeBlock", "FaceAttachedHorizontalDirectionalBlock",
}
# Root classes with a non-plain vanilla parent that were reviewed and fixed by hand (no warning).
HAND_CHECKED = {
    "com.stardew.craft.block.utility.FriendshipDoorBlock",  # super.useWithoutItem -> DoorBlock#use(MAIN_HAND)
}

BRIDGE_TYPES = {
    "InteractionResult": "net.minecraft.world.InteractionResult",
    "InteractionHand": "net.minecraft.world.InteractionHand",
    "BlockState": "net.minecraft.world.level.block.state.BlockState",
    "Level": "net.minecraft.world.level.Level",
    "BlockPos": "net.minecraft.core.BlockPos",
    "Player": "net.minecraft.world.entity.player.Player",
    "BlockHitResult": "net.minecraft.world.phys.BlockHitResult",
}

DECL_RE = re.compile(r"\b(?:[\w.]+\.)?(ItemInteractionResult|InteractionResult)\s+(useItemOn|useWithoutItem)\s*\(")
CLASS_RE = re.compile(r"\bclass\s+(\w+)\s*(?:<[^{]*?>)?\s+extends\s+([\w.]+)")
IMPORT_RE = re.compile(r"^import\s+([\w.]+)\s*;", re.M)
PACKAGE_RE = re.compile(r"^package\s+([\w.]+)\s*;", re.M)


def find_close(text: str, open_idx: int) -> int:
    """Index of the parenthesis matching text[open_idx] == '('. Ignores strings/chars."""
    depth = 0
    i = open_idx
    while i < len(text):
        c = text[i]
        if c in "\"'":
            q = c
            i += 1
            while i < len(text) and text[i] != q:
                i += 2 if text[i] == "\\" else 1
        elif c in "([{":
            depth += 1
        elif c in ")]}":
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return -1


def split_args(s: str) -> list[str]:
    args, depth, cur, i = [], 0, [], 0
    while i < len(s):
        c = s[i]
        if c in "\"'":
            q = c
            j = i + 1
            while j < len(s) and s[j] != q:
                j += 2 if s[j] == "\\" else 1
            cur.append(s[i:j + 1])
            i = j + 1
            continue
        if c in "([{<":
            depth += 1 if c != "<" else 0
        if c in ")]}":
            depth -= 1
        if c == "," and depth == 0:
            args.append("".join(cur))
            cur = []
        else:
            cur.append(c)
        i += 1
    if "".join(cur).strip():
        args.append("".join(cur))
    return args


class JavaFile:
    def __init__(self, path: Path):
        self.path = path
        self.text = path.read_text(encoding="utf-8")
        m = PACKAGE_RE.search(self.text)
        self.package = m.group(1) if m else ""
        self.imports = IMPORT_RE.findall(self.text)
        self.classes = CLASS_RE.findall(self.text)
        self.decls = [m for m in DECL_RE.finditer(self.text) if self._is_override_decl(m)]

    def _is_override_decl(self, m: re.Match) -> bool:
        open_idx = m.end() - 1
        close = find_close(self.text, open_idx)
        params = self.text[open_idx + 1:close]
        nparams = len(split_args(params))
        head = self._decl_head(m.start())
        if re.search(r"\bstatic\b", head):
            return False
        if m.group(2) == "useItemOn":
            return m.group(1) == "ItemInteractionResult" and nparams == 7 and "ItemStack" in params
        return m.group(1) == "InteractionResult" and nparams == 5 and "BlockHitResult" in params

    def _decl_head_start(self, idx: int) -> int:
        j = idx
        while j > 0 and self.text[j - 1] not in ";{}":
            if self.text[j - 1] == ")":
                # Skip annotation arguments such as @SuppressWarnings({"a", "b"}).
                depth = 0
                while j > 0:
                    j -= 1
                    if self.text[j] == ")":
                        depth += 1
                    elif self.text[j] == "(":
                        depth -= 1
                        if depth == 0:
                            break
                continue
            j -= 1
        # Skip a trailing comment block ending right before the declaration.
        return j

    def _decl_head(self, idx: int) -> str:
        return self.text[self._decl_head_start(idx):idx]

    def resolve(self, simple_or_fqn: str, mod_index: dict[str, list[str]]) -> tuple[str, str]:
        """Return ('mod', fqn) or ('vanilla', simple)."""
        if "." in simple_or_fqn:
            simple = simple_or_fqn.rsplit(".", 1)[1]
            return ("mod", simple_or_fqn) if simple_or_fqn.startswith("com.stardew.") else ("vanilla", simple)
        simple = simple_or_fqn
        for imp in self.imports:
            if imp.endswith("." + simple):
                return ("mod", imp) if imp.startswith("com.stardew.") and IFACE not in imp else ("vanilla", simple)
        same_pkg = self.package + "." + simple
        if same_pkg in mod_index.get(simple, []):
            return "mod", same_pkg
        return "vanilla", simple


def collect(paths: list[Path]) -> list[Path]:
    out = []
    for target in paths:
        out.extend([target] if target.is_file() else sorted(target.rglob("*.java")))
    return out


def main(argv: list[str]) -> int:
    check = "--check" in argv
    argv = [a for a in argv if a != "--check"]
    targets = [Path(a) for a in argv] or [ROOT / "src/main/java"]
    # The class index always spans the whole source tree so partial runs resolve ancestors correctly.
    all_files = {p: JavaFile(p) for p in collect([ROOT / "src/main/java"])}
    for p in collect(targets):
        p = p.resolve()
        if p not in all_files:
            all_files[p] = JavaFile(p)
    selected = {p.resolve() for p in collect(targets)}

    mod_index: dict[str, list[str]] = {}
    parent_of: dict[str, tuple[str, str]] = {}
    declares: set[str] = set()
    implements_iface: set[str] = set()
    fqn_file: dict[str, JavaFile] = {}
    for jf in all_files.values():
        for name, _ in jf.classes:
            mod_index.setdefault(name, []).append(jf.package + "." + name)
    for jf in all_files.values():
        for name, parent in jf.classes:
            fqn = jf.package + "." + name
            parent_of[fqn] = jf.resolve(parent, mod_index)
            fqn_file[fqn] = jf
        if jf.decls and jf.classes:
            fqn = jf.package + "." + jf.classes[0][0]
            declares.add(fqn)
            if re.search(r"\bimplements\b[^{]*\b" + IFACE + r"\b", jf.text):
                implements_iface.add(fqn)

    def mod_ancestors(fqn: str):
        seen = set()
        kind, p = parent_of.get(fqn, ("vanilla", "?"))
        while kind == "mod" and p not in seen:
            seen.add(p)
            yield p
            kind, p = parent_of.get(p, ("vanilla", "?"))

    def vanilla_root_parent(fqn: str) -> str:
        cur = fqn
        for anc in mod_ancestors(fqn):
            cur = anc
        return parent_of.get(cur, ("vanilla", "?"))[1]

    warnings: list[str] = []
    changed = 0
    for path, jf in all_files.items():
        if path not in selected:
            continue
        text = jf.text
        rel = path.relative_to(ROOT) if path.is_relative_to(ROOT) else path

        if jf.decls:
            if len(jf.classes) != 1:
                warnings.append(f"{rel}: {len(jf.classes)} 'extends' classes in file; adapted the first one only")
            if not jf.classes:
                warnings.append(f"{rel}: use overrides outside a named subclass (anonymous?) - fix by hand")
            else:
                fqn = jf.package + "." + jf.classes[0][0]
                is_root = not any(a in declares or a in implements_iface for a in mod_ancestors(fqn))
                text = widen(text, jf, warnings, rel)
                if is_root:
                    vparent = vanilla_root_parent(fqn)
                    safe_parent = vparent in PLAIN_VANILLA_PARENTS
                    if not safe_parent and fqn not in HAND_CHECKED:
                        warnings.append(f"{rel}: vanilla parent {vparent} may have its own 1.20.1 use(); "
                                        f"check super calls / inherited defaults by hand")
                    text = add_implements(text, jf.classes[0][0])
                    text = add_bridge(text)
                    if safe_parent:
                        text = re.sub(r"(?<![\w.])super\.(useItemOn|useWithoutItem)\s*\(", IFACE + r".super.\1(", text)
                    text = ensure_import(text, IFACE_FQN)
        text = rewrite_state_calls(text)
        if IFACE + "." in text:
            text = ensure_import(text, IFACE_FQN)
        if text != jf.text:
            changed += 1
            if not check:
                path.write_text(text, encoding="utf-8")
    for w in warnings:
        print("WARN", w)
    print(f"{'would change' if check else 'adapted'} {changed} files")
    return 0


def widen(text: str, jf: JavaFile, warnings: list[str], rel) -> str:
    # Re-scan on the current text; edit from the end so offsets stay valid.
    matches = [m for m in DECL_RE.finditer(text)]
    tmp = JavaFile.__new__(JavaFile)
    tmp.text = text
    for m in reversed(matches):
        if not JavaFile._is_override_decl(tmp, m):
            continue
        start = JavaFile._decl_head_start(tmp, m.start())
        head = text[start:m.start()]
        if re.search(r"\bpublic\b", head):
            continue
        if re.search(r"\bprivate\b", head):
            warnings.append(f"{rel}: private {m.group(2)} - fix by hand")
            continue
        if re.search(r"\bprotected\b", head):
            new_head = re.sub(r"\bprotected\b", "public", head, count=1)
        else:
            # Package-private: put 'public' right before the return type (after annotations).
            new_head = head + "public "
        text = text[:start] + new_head + text[m.start():]
    return text


def add_implements(text: str, cls: str) -> str:
    m = re.search(r"\bclass\s+" + re.escape(cls) + r"\b", text)
    brace = text.index("{", m.end())
    header = text[m.start():brace]
    if re.search(r"\b" + IFACE + r"\b", header):
        return text
    stripped = header.rstrip()
    tail = header[len(stripped):]
    if re.search(r"\bimplements\b", header):
        new_header = stripped + ", " + IFACE + tail
    else:
        new_header = stripped + " implements " + IFACE + (tail or " ")
    return text[:m.start()] + new_header + text[brace:]


def add_bridge(text: str) -> str:
    if MARKER in text:
        return text
    tmp = JavaFile.__new__(JavaFile)
    tmp.text = text
    first = next(m for m in DECL_RE.finditer(text) if JavaFile._is_override_decl(tmp, m))
    # Walk up from the declaration line over annotations / comments / blank-free javadoc.
    line_start = text.rfind("\n", 0, first.start()) + 1
    lines_before = text[:line_start].split("\n")
    insert_at = line_start
    idx = len(lines_before) - 2  # last complete line before declaration line
    offset = line_start
    while idx >= 0:
        s = lines_before[idx].strip()
        if s.startswith("@") or s.startswith("*") or s.startswith("/**") or s.startswith("//") or s.endswith("*/"):
            offset -= len(lines_before[idx]) + 1
            insert_at = offset
            idx -= 1
            continue
        break
    decl_line = text[line_start:text.find("\n", line_start)]
    indent = re.match(r"\s*", decl_line).group(0)
    names = {k: k for k in BRIDGE_TYPES}
    for simple, fqn in BRIDGE_TYPES.items():
        if not can_use_simple(text, simple, fqn):
            names[simple] = fqn
    i2 = indent + "        "
    bridge = (
        f"{indent}// {MARKER}.\n"
        f"{indent}@Override\n"
        f"{indent}public {names['InteractionResult']} use({names['BlockState']} state, {names['Level']} level, "
        f"{names['BlockPos']} pos, {names['Player']} player,\n"
        f"{i2}{names['InteractionHand']} hand, {names['BlockHitResult']} hit) {{\n"
        f"{indent}    return {IFACE}.dispatch(this, state, level, pos, player, hand, hit);\n"
        f"{indent}}}\n\n"
    )
    text = text[:insert_at] + bridge + text[insert_at:]
    for simple, fqn in BRIDGE_TYPES.items():
        if names[simple] == simple:
            text = ensure_import(text, fqn)
    return text


def can_use_simple(text: str, simple: str, fqn: str) -> bool:
    for imp in IMPORT_RE.findall(text):
        if imp.endswith("." + simple) and imp != fqn:
            return False
    # A nested type or type parameter with the same simple name would shadow the import.
    if re.search(r"\b(?:class|interface|enum|record)\s+" + simple + r"\b", text):
        return False
    return True


def ensure_import(text: str, fqn: str) -> str:
    if re.search(r"^import\s+" + re.escape(fqn) + r"\s*;", text, re.M):
        return text
    pkg = PACKAGE_RE.search(text)
    if pkg and fqn.rsplit(".", 1)[0] == pkg.group(1):
        return text
    imports = list(IMPORT_RE.finditer(text))
    if imports:
        at = text.find("\n", imports[-1].start()) + 1
    else:
        at = text.find("\n", pkg.end()) + 1 if pkg else 0
        fqn_line = "\n"
        return text[:at] + fqn_line + f"import {fqn};\n" + text[at:]
    return text[:at] + f"import {fqn};\n" + text[at:]


STATE_CALL_RE = re.compile(r"\.(useItemOn|useWithoutItem)\s*\(")


KEYWORDS = {"if", "while", "for", "switch", "synchronized", "catch", "return", "throw", "new", "else", "case", "assert"}


def receiver_start(text: str, dot: int) -> int:
    """Start index of the primary expression whose member is accessed at text[dot] == '.'."""
    i = dot
    while i > 0:
        head = text[i]
        c = text[i - 1]
        if head == "." and c in " \t\n":
            k = i - 1
            while k > 0 and text[k - 1] in " \t\n":
                k -= 1
            i = k
            continue
        if c in ")]" and head in ".[":
            open_c = "(" if c == ")" else "["
            depth = 0
            j = i - 1
            while j >= 0:
                if text[j] == c:
                    depth += 1
                elif text[j] == open_c:
                    depth -= 1
                    if depth == 0:
                        break
                j -= 1
            i = j
            continue
        if (c.isalnum() or c == "_") and head in ".([" or (c.isalnum() or c == "_") and (head.isalnum() or head == "_"):
            j = i - 1
            while j > 0 and (text[j - 1].isalnum() or text[j - 1] == "_"):
                j -= 1
            if text[j:i] in KEYWORDS:
                return i
            i = j
            continue
        if c == "." and (head.isalnum() or head == "_"):
            i -= 1
            continue
        break
    return i


def rewrite_state_calls(text: str) -> str:
    out = []
    pos = 0
    for m in STATE_CALL_RE.finditer(text):
        if m.start() < pos:
            continue
        start = receiver_start(text, m.start())
        recv = text[start:m.start()]
        if not recv or recv in ("super", "this") or recv.endswith(IFACE) or recv.endswith("gameMode"):
            continue
        open_idx = m.end() - 1
        close = find_close(text, open_idx)
        args = split_args(text[open_idx + 1:close])
        name = m.group(1)
        if name == "useWithoutItem" and len(args) == 3:
            helper = "stateUseWithoutItem"
        elif name == "useItemOn" and len(args) == 5:
            helper = "stateUseItemOn"
        else:
            continue
        out.append(text[pos:start])
        out.append(f"{IFACE}.{helper}({recv.strip()}, {text[open_idx + 1:close].strip()})")
        pos = close + 1
    out.append(text[pos:])
    return "".join(out)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

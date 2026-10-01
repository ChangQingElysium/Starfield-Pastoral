#!/usr/bin/env python3
"""Error-driven rewrite of 1.21-only call shapes to their exact 1.20.1 equivalents / port helpers.

    python3 scripts/port/fix_errors_1201.py <javac log> [path-prefix | !excluded-prefix ...]

Only the call sites javac reports are touched (error line + caret column), so same-named members
of other types are never rewritten. Optional path prefixes restrict which files may be edited
(parallel agents own different packages). Re-run with a fresh log until it reports 0 fixes.
Receiver wraps work across line breaks: `a\n  .b()\n  .getOrThrow()` -> `PortX.getOrThrow(a\n  .b())`.

Each rule documents why the rewrite is behaviour-identical to 1.21.1.
"""
from __future__ import annotations

import re
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
P = "com.stardew.craft.port."
STOP_WORDS = {"return", "while", "if", "for", "else", "do", "throw", "case", "yield", "assert", "instanceof"}


# ---------------------------------------------------------------- text scanning helpers
def skip_ws_back(text: str, i: int) -> int:
    while i >= 0 and text[i].isspace():
        i -= 1
    return i


def match_back(text: str, i: int, close: str, open_: str) -> int:
    """text[i] == close; return index of matching open (strings skipped)."""
    depth = 0
    while i >= 0:
        c = text[i]
        if c == '"':
            i -= 1
            while i >= 0 and not (text[i] == '"' and text[i - 1] != "\\"):
                i -= 1
        elif c == close:
            depth += 1
        elif c == open_:
            depth -= 1
            if depth == 0:
                return i
        i -= 1
    raise ValueError("unbalanced")


def match_forward(text: str, i: int) -> int:
    """text[i] == '('; return index of matching ')'."""
    depth = 0
    in_str = None
    while i < len(text):
        c = text[i]
        if in_str:
            if c == "\\":
                i += 2
                continue
            if c == in_str:
                in_str = None
        elif c in "\"'":
            in_str = c
        elif c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return i
        i += 1
    raise ValueError("unbalanced")


def word_back(text: str, i: int) -> tuple[int, str]:
    j = i
    while j >= 0 and (text[j].isalnum() or text[j] in "_$"):
        j -= 1
    return j, text[j + 1:i + 1]


def _ident(c: str) -> bool:
    return c.isalnum() or c in "_$"


def receiver_start(text: str, dot: int) -> int:
    """Start index of the receiver expression of `recv.method(` whose '.' is at `dot`."""
    i = skip_ws_back(text, dot - 1)
    start = i + 1
    while i >= 0:
        c = text[i]
        if c == ")":
            j = match_back(text, i, ")", "(")
            start = j
            k = skip_ws_back(text, j - 1)
            if k >= 0 and (_ident(text[k]) or (text[k] == ">" and text[k - 1] != "-")):
                i = k  # method name, constructor type or generic args precede the argument list
                continue
            break  # parenthesised primary
        if c == "]":
            j = match_back(text, i, "]", "[")
            start = j
            i = skip_ws_back(text, j - 1)
            continue
        if c == ">" and i > 0 and text[i - 1] != "-":
            j = match_back(text, i, ">", "<")
            start = j
            i = skip_ws_back(text, j - 1)
            continue
        if _ident(c):
            j, word = word_back(text, i)
            if word in STOP_WORDS:
                break
            start = j + 1
            k = skip_ws_back(text, j)
            if k >= 0 and text[k] == ".":
                i = skip_ws_back(text, k - 1)
                continue
            if k >= 0 and text[k] == ">" and text[k - 1] != "-":  # a.<T>m(
                j2 = match_back(text, k, ">", "<")
                k2 = skip_ws_back(text, j2 - 1)
                if k2 >= 0 and text[k2] == ".":
                    start = j2
                    i = skip_ws_back(text, k2 - 1)
                    continue
                break
            kj, kw = word_back(text, k)
            if kw == "new":
                start = kj + 1
            break
        if c == '"':
            j = i - 1
            while j >= 0 and not (text[j] == '"' and text[j - 1] != "\\"):
                j -= 1
            start = j
            k = skip_ws_back(text, j - 1)
            if k >= 0 and text[k] == ".":
                i = skip_ws_back(text, k - 1)
                continue
            break
        break
    return start


def line_offsets(text: str) -> list[int]:
    offs = [0]
    for m in re.finditer("\n", text):
        offs.append(m.end())
    return offs


# ---------------------------------------------------------------- edit actions
class Edit:
    """An edit at a caret offset; apply(text, pos) -> (new_text, insert_pos, insert_len) or None."""


def wrap(helper: str, method: str, keep_args: bool = True):
    """recv.method(args) -> helper(recv, args)."""
    def apply(text, pos):
        m = re.compile(r"\.\s*" + method + r"\s*\(").match(text, pos)
        if not m:
            return None
        start = receiver_start(text, pos)
        recv = text[start:pos].rstrip()
        if not recv:
            return None
        open_idx = m.end() - 1
        close = match_forward(text, open_idx)
        args = text[open_idx + 1:close].strip()
        prefix = helper + "("
        tail = (", " + args if args and keep_args else "") + ")"
        new = text[:start] + prefix + recv + tail + text[close + 1:]
        return new, start, len(prefix)
    return apply


def rename_member(old: str, new: str):
    """`.old` / `old` at the caret -> new (same receiver)."""
    def apply(text, pos):
        m = re.compile(r"(\.\s*)?" + re.escape(old) + (r"\b" if old[-1].isalnum() or old[-1] == "_" else "")).match(text, pos)
        if not m:
            return None
        dot = m.group(1) or ""
        return text[:pos] + dot + new + text[m.end():], pos, 0
    return apply


def drop_last_arg(method: str):
    """recv.method(a, registries) -> recv.method(a)  (1.21 registry-aware overload of a 1.20.1 method)."""
    def apply(text, pos):
        m = re.compile(r"(?:\.\s*)?" + method + r"\s*\(").match(text, pos)
        if not m:
            return None
        open_idx = m.end() - 1
        close = match_forward(text, open_idx)
        depth, cut = 0, None
        for k in range(open_idx + 1, close):
            ch = text[k]
            if ch in "([{":
                depth += 1
            elif ch in ")]}":
                depth -= 1
            elif ch == "," and depth == 0:
                cut = k
        if cut is None:
            return None
        return text[:cut] + text[close:], pos, 0
    return apply


def drop_call(method: str):
    """`.method()` at the caret is removed (1.21 Holder#value() on what is a plain value in 1.20.1)."""
    def apply(text, pos):
        m = re.compile(r"\.\s*" + method + r"\s*\(\s*\)").match(text, pos)
        if not m:
            return None
        return text[:pos] + text[m.end():], pos, 0
    return apply


def replace_qualified(member: str, new: str):
    """`[pkg.]Owner.member` at the caret (the '.') -> new expression."""
    def apply(text, pos):
        m = re.compile(r"\.\s*" + member + r"\b").match(text, pos)
        if not m:
            return None
        start = receiver_start(text, pos)
        return text[:start] + new + text[m.end():], start, 0
    return apply


def insert_level_before_registry_access():
    """1.21 Entity#registryAccess() is `this.level().registryAccess()`."""
    def apply(text, pos):
        m = re.compile(r"(\.\s*)?registryAccess\s*\(\s*\)").match(text, pos)
        if not m:
            return None
        dot = m.group(1) or ""
        return text[:pos] + dot + "level().registryAccess()" + text[m.end():], pos, 0
    return apply


# (message regex, symbol regex or None, location regex or None, action)
RULES = [
    # DFU 8 DataResult#getOrThrow() / getOrThrow(fn) -> PortDataResults (identical exception behaviour)
    (r"method getOrThrow in class DataResult<R> cannot be applied", None, None,
     wrap(P + "PortDataResults.getOrThrow", "getOrThrow")),
    (r"cannot find symbol", r"method getPartialOrThrow\(", r"DataResult",
     wrap(P + "PortDataResults.getPartialOrThrow", "getPartialOrThrow")),
    # DFU 8 Codec#validate / MapCodec#validate = flatXmap(checker, checker)
    (r"cannot find symbol", r"method validate\(", r"Codec<",
     wrap(P + "PortCodecs.validate", "validate")),
    # Component.Serializer.toJson/fromJson(.., HolderLookup.Provider) -> 1.20.1 registry-less forms (same JSON
    # component model; 1.20.1 components carry no registry-bound data)
    (r"method toJson in class Serializer cannot be applied", None, None, drop_last_arg("toJson")),
    (r"no suitable method found for fromJson\(String,(?:RegistryAccess|Provider|Frozen|ImmutableRegistryAccess)\)", None, None,
     drop_last_arg("fromJson")),
    # FoodProperties.Builder (1.21 saturationModifier(m) -> saturation n*m*2 at build; 1.20.1 saturationMod(m)
    # stores m and FoodData#eat adds n*m*2: same nutrition/saturation reaches the player)
    (r"cannot find symbol", r"method saturationModifier\(float\)", r"Builder", rename_member("saturationModifier", "saturationMod")),
    (r"cannot find symbol", r"method alwaysEdible\(\)", r"Builder", rename_member("alwaysEdible", "alwaysEat")),
    # Entity#registryAccess() (1.20.2+) == level().registryAccess()
    (r"cannot find symbol", r"method registryAccess\(\)", r"(?!.*(?:Level|MinecraftServer|CommandSourceStack))",
     insert_level_before_registry_access()),
    # 1.21 Holder<SoundEvent>/Holder<MobEffect> constants are plain SoundEvent/MobEffect in 1.20.1: value() is the
    # identity there
    (r"cannot find symbol", r"method value\(\)$", r"(?:SoundEvent|MobEffect|Attribute)$", drop_call("value")),
    # 1.20.5+ attributes -> port registrations (see PortAttributes) / Forge reach attribute
    (r"cannot find symbol", r"variable STEP_HEIGHT$", r"class Attributes", replace_qualified("STEP_HEIGHT", P + "PortAttributes.STEP_HEIGHT.get()")),
    (r"cannot find symbol", r"variable SCALE$", r"class Attributes", replace_qualified("SCALE", P + "PortAttributes.SCALE.get()")),
    (r"cannot find symbol", r"variable ENTITY_INTERACTION_RANGE$", r"class Attributes",
     replace_qualified("ENTITY_INTERACTION_RANGE", "net.minecraftforge.common.ForgeMod.ENTITY_REACH.get()")),
    (r"cannot find symbol", r"variable BLOCK_INTERACTION_RANGE$", r"class Attributes",
     replace_qualified("BLOCK_INTERACTION_RANGE", "net.minecraftforge.common.ForgeMod.BLOCK_REACH.get()")),
    # AttributeModifier.Operation 1.21 names
    (r"cannot find symbol", r"variable ADD_VALUE$", r"Operation", rename_member("ADD_VALUE", "ADDITION")),
    (r"cannot find symbol", r"variable ADD_MULTIPLIED_BASE$", r"Operation", rename_member("ADD_MULTIPLIED_BASE", "MULTIPLY_BASE")),
    (r"cannot find symbol", r"variable ADD_MULTIPLIED_TOTAL$", r"Operation", rename_member("ADD_MULTIPLIED_TOTAL", "MULTIPLY_TOTAL")),
    # AttributeModifier record accessors (1.21) -> getters (1.20.1)
    (r"cannot find symbol", r"method amount\(\)", r"AttributeModifier", rename_member("amount", "getAmount")),
    (r"cannot find symbol", r"method operation\(\)", r"AttributeModifier", rename_member("operation", "getOperation")),
    # Item#getDefaultMaxStackSize() (1.20.5 component default) == 1.20.1 Item#getMaxStackSize()
    (r"cannot find symbol", r"method getDefaultMaxStackSize\(\)", None, rename_member("getDefaultMaxStackSize", "getMaxStackSize")),
    # NbtAccounter factories
    (r"cannot find symbol", r"method unlimitedHeap\(\)", r"NbtAccounter", rename_member("unlimitedHeap()", "UNLIMITED")),
]

JDK = {"getFirst", "getLast", "removeFirst", "removeLast", "reversed", "addFirst", "addLast"}


def collect(log_path: Path, prefixes: list[str]):
    log = log_path.read_text(encoding="utf-8", errors="replace").split("\n")
    todo = defaultdict(list)  # file -> [(line, col, action)]
    for i, line in enumerate(log):
        m = re.match(r"(.+\.java):(\d+): error: (.*)", line)
        if not m:
            continue
        rel, lineno, msg = m.group(1), int(m.group(2)), m.group(3)
        inc = [p for p in prefixes if not p.startswith("!")]
        exc = [p[1:] for p in prefixes if p.startswith("!")]
        if (inc and not any(rel.startswith(p) for p in inc)) or any(rel.startswith(p) for p in exc):
            continue
        caret_line = log[i + 2] if i + 2 < len(log) else ""
        col = caret_line.find("^")
        if col < 0:
            continue
        block = log[i + 3:i + 8]
        sym = next((b.split("symbol:", 1)[1].strip() for b in block if "symbol:" in b), "")
        loc = next((b.split("location:", 1)[1].strip() for b in block if "location:" in b), "")
        for msg_re, sym_re, loc_re, action in RULES:
            if not re.search(msg_re, msg):
                continue
            if sym_re and not re.search(sym_re, sym):
                continue
            if loc_re and not re.search(loc_re, loc):
                continue
            todo[rel].append((lineno, col, action))
            break
        else:
            jm = re.match(r"method (\w+)\(", sym)
            if msg.startswith("cannot find symbol") and jm and jm.group(1) in JDK and re.search(r"\bList<|\bList\b|Deque|Collection", loc):
                todo[rel].append((lineno, col, wrap(P + "PortJava." + jm.group(1), jm.group(1))))
    return todo


def main(argv) -> int:
    todo = collect(Path(argv[0]), argv[1:])
    fixed = 0
    for rel, edits in todo.items():
        path = ROOT / rel
        text = path.read_text(encoding="utf-8")
        offs = line_offsets(text)
        seen = set()
        ordered = []
        for lineno, col, action in edits:
            pos = offs[lineno - 1] + col
            if pos in seen:  # javac may report the same site twice
                continue
            seen.add(pos)
            ordered.append((pos, action))
        ordered.sort(key=lambda e: -e[0])
        inserts: list[tuple[int, int]] = []
        for pos, action in ordered:
            for ipos, ilen in inserts:
                if ipos <= pos:
                    pos += ilen
            try:
                res = action(text, pos)
            except ValueError:
                res = None
            if res is None:
                ln = text.count("\n", 0, pos) + 1
                print(f"manual: {rel}:{ln}")
                continue
            text, ipos, ilen = res
            if ilen:
                inserts.append((ipos, ilen))
            fixed += 1
        path.write_text(text, encoding="utf-8")
    print(f"fixed {fixed} call sites")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

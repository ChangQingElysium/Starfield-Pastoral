#!/usr/bin/env python3
"""Idempotent adaptation of 1.21 registry-aware NBT methods to 1.20.1 signatures.

    python3 scripts/port/adapt_nbt_provider.py [paths...]   (default: src/main/java)

Declarations lose their HolderLookup.Provider parameter; a local with the same name
is declared at the top of the body (com.stardew.craft.port.PortRegistries.lookup()) so
method bodies stay byte-identical to main. Call sites drop the provider argument.
SavedData.Factory is redirected to com.stardew.craft.port.PortSavedData.Factory.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PROVIDER = r"(?:@\w+(?:\([^)]*\))?\s+)*(?:net\.minecraft\.core\.)?HolderLookup\.Provider\s+(\w+)"
ANN = r"(?:@\w+(?:\([^)]*\))?\s+)*"
ARG = r"(?:\w+(?:\(\))?)(?:\.\w+(?:\(\))?)*"  # e.g. level.registryAccess()
LOCAL = "net.minecraft.core.HolderLookup.Provider {name} = com.stardew.craft.port.PortRegistries.lookup();"

# (declaration regex, replacement head). Group 'p' = provider param name.
DECLS = [
    # BlockEntity
    (re.compile(r"protected\s+void\s+saveAdditional\(\s*(" + ANN + r"CompoundTag\s+\w+)\s*,\s*" + PROVIDER + r"\s*\)(\s*)\{"),
     "protected void saveAdditional({0}){2}{{"),
    (re.compile(r"(?:protected|public)\s+void\s+loadAdditional\(\s*(" + ANN + r"CompoundTag\s+\w+)\s*,\s*" + PROVIDER + r"\s*\)(\s*)\{"),
     "public void load({0}){2}{{"),
    (re.compile(r"public\s+void\s+handleUpdateTag\(\s*(" + ANN + r"CompoundTag\s+\w+)\s*,\s*" + PROVIDER + r"\s*\)(\s*)\{"),
     "public void handleUpdateTag({0}){2}{{"),
    (re.compile(r"public\s+void\s+onDataPacket\(\s*(" + ANN + r"[\w.]*Connection\s+\w+\s*,\s*" + ANN
                + r"[\w.]*ClientboundBlockEntityDataPacket\s+\w+)\s*,\s*" + PROVIDER + r"\s*\)(\s*)\{"),
     "public void onDataPacket({0}){2}{{"),
    (re.compile(r"public\s+CompoundTag\s+getUpdateTag\(\s*()" + PROVIDER + r"\s*\)(\s*)\{"),
     "public CompoundTag getUpdateTag(){2}{{"),
    # SavedData
    (re.compile(r"(public\s+(?:synchronized\s+)?)CompoundTag\s+save\(\s*(" + ANN + r"CompoundTag\s+\w+)\s*,\s*" + PROVIDER + r"\s*\)(\s*)\{"),
     None),
]

CALLS = [
    (re.compile(r"\bsuper\.saveAdditional\(\s*(\w+)\s*,\s*\w+\s*\)"), r"super.saveAdditional(\1)"),
    (re.compile(r"\bsuper\.loadAdditional\(\s*(\w+)\s*,\s*\w+\s*\)"), r"super.load(\1)"),
    (re.compile(r"\bsuper\.handleUpdateTag\(\s*(\w+)\s*,\s*\w+\s*\)"), r"super.handleUpdateTag(\1)"),
    (re.compile(r"\bsuper\.onDataPacket\(\s*(\w+)\s*,\s*(\w+)\s*,\s*\w+\s*\)"), r"super.onDataPacket(\1, \2)"),
    (re.compile(r"\bsuper\.getUpdateTag\(\s*\w+\s*\)"), "super.getUpdateTag()"),
    # Non-super call sites (receiver or implicit this). Declarations were rewritten above.
    (re.compile(r"(?<![\w.])((?:[\w.()]+\.)?)getUpdateTag\(\s*" + ARG + r"\s*\)(?!\s*\{)"), r"\1getUpdateTag()"),
    (re.compile(r"(?<![\w.])((?:[\w.()]+\.)?)saveAdditional\(\s*(\w+)\s*,\s*" + ARG + r"\s*\)(?!\s*\{)"), r"\1saveAdditional(\2)"),
    (re.compile(r"(?<![\w.])((?:[\w.()]+\.)?)loadAdditional\(\s*(\w+)\s*,\s*" + ARG + r"\s*\)(?!\s*\{)"), r"\1load(\2)"),
    (re.compile(r"\.handleUpdateTag\(\s*([^,;()]+(?:\([^()]*\))?)\s*,\s*" + ARG + r"\s*\)"), r".handleUpdateTag(\1)"),
    (re.compile(r"(?<![\w.])(saveWithFullMetadata|saveWithoutMetadata|saveWithId)\(\s*" + ARG + r"\s*\)(?!\s*\{)"), r"\1()"),
    (re.compile(r"\.(saveWithFullMetadata|saveWithoutMetadata|saveWithId)\(\s*" + ARG + r"\s*\)(?!\s*\{)"), r".\1()"),
    (re.compile(r"\.saveCustomOnly\(\s*" + ARG + r"\s*\)"), ".saveWithoutMetadata()"),
    (re.compile(r"\.(?:loadWithComponents|loadCustomOnly)\(\s*([^,()]+(?:\([^()]*\))?)\s*,\s*" + ARG + r"\s*\)"), r".load(\1)"),
    (re.compile(r"\bBlockEntity\.loadStatic\(([^;]*?),\s*[\w.]+\(\)\s*\)"), r"BlockEntity.loadStatic(\1)"),
    (re.compile(r"\bBlockEntity\.loadStatic\(([^;]*?),\s*(?:registries|provider|lookup|registryAccess)\s*\)"), r"BlockEntity.loadStatic(\1)"),
    # SavedData.Factory
    (re.compile(r"import\s+net\.minecraft\.world\.level\.saveddata\.SavedData\.Factory\s*;"),
     "import com.stardew.craft.port.PortSavedData.Factory;"),
    (re.compile(r"(?<![\w.])(?:net\.minecraft\.world\.level\.saveddata\.)?SavedData\.Factory\b"),
     "com.stardew.craft.port.PortSavedData.Factory"),
]


def split_args(s: str, start: int):
    """s[start] == '('; return (index_of_matching_close_paren, [top-level args])."""
    stack, i, args, cur = [], start, [], start + 1
    in_str = None
    while i < len(s):
        c = s[i]
        if in_str:
            if c == "\\":
                i += 2
                continue
            if c == in_str:
                in_str = None
        elif c in "\"'":
            in_str = c
        elif c in "([{":
            stack.append(c)
        elif c == "<" and i > 0 and (s[i - 1].isalnum() or s[i - 1] in "_.") and re.match(r"<[\w?>]", s[i:i + 2]):
            stack.append("<")
        elif c == ">" and stack and stack[-1] == "<":
            stack.pop()
        elif c in ")]}":
            stack.pop()
            if not stack:
                args.append(s[cur:i])
                return i, args
        elif c == "," and len(stack) == 1:
            args.append(s[cur:i])
            cur = i + 1
        i += 1
    raise ValueError("unbalanced")


def adapt_storage_calls(text: str) -> str:
    """storage.computeIfAbsent(factory, name) / storage.get(factory, name) -> 1.20.1 forms."""
    out, pos = [], 0
    for m in re.finditer(r"\.(computeIfAbsent|get)\(", text):
        if m.start() < pos:
            continue
        open_idx = m.end() - 1
        try:
            close, args = split_args(text, open_idx)
        except ValueError:
            continue
        if len(args) != 2:
            continue
        first = args[0].strip()
        if not re.search(r"(?:\bnew\s+(?:com\.stardew\.craft\.port\.PortSavedData\.)?Factory\b|[Ff]actory\(\)|\bFACTORY\b|_FACTORY\b)", first):
            continue
        if "PortSavedData.loader" in first:
            continue
        lead = args[0][: len(args[0]) - len(args[0].lstrip())]
        if m.group(1) == "computeIfAbsent":
            new_args = (f"{lead}com.stardew.craft.port.PortSavedData.loader({first}), "
                        f"com.stardew.craft.port.PortSavedData.constructor({first}),{args[1]}")
        else:
            new_args = f"{lead}com.stardew.craft.port.PortSavedData.loader({first}),{args[1]}"
        out.append(text[pos:open_idx + 1])
        out.append(new_args)
        pos = close
    out.append(text[pos:])
    return "".join(out)


def drop_third_arg(text: str, method: str) -> str:
    """x.onDataPacket(conn, packet, provider) -> x.onDataPacket(conn, packet); 2-arg calls untouched."""
    out, pos = [], 0
    for m in re.finditer(r"\." + method + r"\(", text):
        if m.start() < pos:
            continue
        try:
            close, args = split_args(text, m.end() - 1)
        except ValueError:
            continue
        if len(args) != 3:
            continue
        out.append(text[pos:m.end()])
        out.append(",".join(args[:2]))
        pos = close
    out.append(text[pos:])
    return "".join(out)


def adapt(text: str) -> str:
    for pattern, head in DECLS:
        def repl(m, head=head):
            if head is None:  # SavedData#save
                prefix, tag_param, name, ws = m.group(1), m.group(2), m.group(3), m.group(4)
                return f"{prefix}CompoundTag save({tag_param}){ws}{{ " + LOCAL.format(name=name)
            first, name, ws = m.group(1), m.group(2), m.group(3)
            return head.format(first, name, ws) + " " + LOCAL.format(name=name)
        text = pattern.sub(repl, text)
    for pattern, rep in CALLS:
        text = pattern.sub(rep, text)
    text = drop_third_arg(text, "onDataPacket")
    return adapt_storage_calls(text)


def main(argv) -> int:
    targets = [Path(a) for a in argv] or [ROOT / "src/main/java"]
    changed = 0
    for target in targets:
        for path in ([target] if target.is_file() else sorted(target.rglob("*.java"))):
            old = path.read_text(encoding="utf-8")
            new = adapt(old)
            if new != old:
                path.write_text(new, encoding="utf-8")
                changed += 1
    print(f"adapted {changed} files")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

#!/usr/bin/env python3
"""Error-driven rewrite of JDK 21-only library calls to com.stardew.craft.port.PortJava.

    python3 scripts/port/fix_java21_errors.py <javac log>   (repeat with a fresh log until 0 fixes)

Only call sites javac reports as `cannot find symbol: method getFirst()` (etc.) on a JDK
collection receiver are touched, so same-named methods of other types (datafixers Pair#getFirst)
are never rewritten. `Math.clamp` is JDK 21-only everywhere and handled by rewrite_1201.py.
PortJava reproduces the JDK 21 semantics exactly, including exceptions.
"""
from __future__ import annotations

import re
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PORT = "com.stardew.craft.port.PortJava"
# method -> number of explicit args it takes after the receiver
METHODS = {
    "getFirst": 0, "getLast": 0, "removeFirst": 0, "removeLast": 0, "reversed": 0,
    "addFirst": 1, "addLast": 1, "firstEntry": 0, "lastEntry": 0, "pollFirstEntry": 0,
    "pollLastEntry": 0, "sequencedKeySet": 0, "sequencedValues": 0, "sequencedEntrySet": 0,
    "putLast": 2,
}
JDK_LOCATION = re.compile(
    r"location: (?:variable \w+ of type |class |interface )"
    r"(?:java\.util\.)?(List|ArrayList|LinkedList|Collection|Set|LinkedHashSet|SortedSet|TreeSet|NavigableSet|"
    r"Map|HashMap|LinkedHashMap|SortedMap|TreeMap|NavigableMap|Deque|ArrayDeque|ImmutableList|"
    r"ImmutableSet|ImmutableMap|CopyOnWriteArrayList|SequencedCollection|SequencedMap|Iterable)\b")


def receiver_start(line: str, dot: int) -> int:
    """Walk back from the '.' of `recv.method(` to the start of the receiver expression."""
    i = dot - 1
    prev = None  # kind of the token just consumed (walking backwards): 'ident' | 'group'
    while i >= 0:
        c = line[i]
        if c in ")]":
            if prev == "ident":  # `while(cond)LIST` / `(Type)x`: the group is not part of the receiver
                break
            depth, open_c = 0, "(" if c == ")" else "["
            while i >= 0:
                if line[i] == c:
                    depth += 1
                elif line[i] == open_c:
                    depth -= 1
                    if depth == 0:
                        break
                i -= 1
            i -= 1
            prev = "group"
            continue
        if c == ">" and i > 0 and line[i - 1] == "<":  # diamond: new Foo<>(...)
            i -= 2
            continue
        if c == ".":
            i -= 1
            prev = "dot"
            continue
        if c.isalnum() or c in "_$":
            j = i
            while j >= 0 and (line[j].isalnum() or line[j] in "_$"):
                j -= 1
            word = line[j + 1:i + 1]
            if word in {"return", "while", "if", "for", "else", "do", "throw", "case", "yield", "assert"}:
                break
            i = j
            prev = "ident"
            continue
        break
    start = i + 1
    # include `new ` for constructor receivers
    if line[max(0, start - 4):start] == "new ":
        start -= 4
    return start


def fix_line(line: str, method: str) -> str | None:
    m = next((x for x in re.finditer(r"\.\s*" + method + r"\(", line)
              if not line[:x.start()].endswith("PortJava")), None)
    if not m:
        return None
    dot = m.start()
    start = receiver_start(line, dot)
    recv = line[start:dot].strip()
    if not recv:
        return None
    # find the closing paren of the call
    open_idx = m.end() - 1
    depth = 0
    for j in range(open_idx, len(line)):
        if line[j] == "(":
            depth += 1
        elif line[j] == ")":
            depth -= 1
            if depth == 0:
                break
    else:
        return None
    args = line[open_idx + 1:j].strip()
    name = "reversed" if method == "reversed" else method
    call = f"{PORT}.{name}({recv}{', ' + args if args else ''})"
    return line[:start] + call + line[j + 1:]


def main(argv) -> int:
    log = Path(argv[0]).read_text(encoding="utf-8", errors="replace").splitlines()
    todo: dict[Path, dict[int, list[str]]] = defaultdict(lambda: defaultdict(list))
    for i, line in enumerate(log):
        m = re.match(r"(.+\.java):(\d+): error: cannot find symbol", line)
        if not m:
            continue
        block = "\n".join(log[i + 1:i + 6])
        sym = re.search(r"symbol:\s+method (\w+)\(", block)
        if not sym or sym.group(1) not in METHODS:
            continue
        if not JDK_LOCATION.search(block):
            continue
        todo[ROOT / m.group(1)][int(m.group(2))].append(sym.group(1))
    fixed = 0
    for path, lines_map in todo.items():
        text = path.read_text(encoding="utf-8").split("\n")
        for lineno, methods in lines_map.items():
            for method in methods:
                new = fix_line(text[lineno - 1], method)
                if new is not None and new != text[lineno - 1]:
                    text[lineno - 1] = new
                    fixed += 1
                else:
                    print(f"manual: {path.relative_to(ROOT)}:{lineno} {method}")
        path.write_text("\n".join(text), encoding="utf-8")
    print(f"fixed {fixed} call sites")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

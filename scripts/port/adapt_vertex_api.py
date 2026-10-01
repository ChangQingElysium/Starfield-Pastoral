#!/usr/bin/env python3
"""Idempotent rewrite of 1.21 vertex chains to the 1.20.1 VertexConsumer API via PortVertex.

1.21:   vc.addVertex(pose, x, y, z).setColor(..).setUv(..).setOverlay(..).setLight(..).setNormal(pose, ..);
1.20.1: PortVertex.of(vc).addVertex(pose, x, y, z).setColor(..)...setNormal(pose, ..).endVertex();

PortVertex records the chain and replays it in an order valid for every vanilla vertex format (1.20.1
BufferBuilder only accepts the format's current element), so every rewritten statement emits exactly one
vertex with the same attribute values as 1.21. Also:
  vc.addVertex(x, y, z, color, u, v, overlay, light, nx, ny, nz);  -> PortVertex.addVertex(vc, ...);
  vc.putBulkData(pose, quad, r, g, b, a, light, overlay);           -> PortVertex.putBulkData(vc, ...);

Statements that cannot be rewritten mechanically (chain used as an expression, unknown chained method, code
inside VertexConsumer implementations) are listed as MANUAL and left untouched.

Usage: adapt_vertex_api.py [--dry-run] [paths...]   (default: src/main/java)
"""
import os
import re
import sys

IMPORT = "com.stardew.craft.port.PortVertex"
CHAIN_METHODS = {"setColor", "setUv", "setUv1", "setUv2", "setOverlay", "setLight", "setNormal", "setWhiteAlpha"}
STATEMENT_PREV = (";", "{", "}", ")", ":", ">")  # '>' covers '->' lambda bodies
IMPL_RE = re.compile(r"\b(?:class|record)\s+\w+[^{;]*?\bimplements\b[^{;]*?\bVertexConsumer\b[^{;]*\{")


def code_mask(s):
    """True for characters that are code (not inside comments/strings/char literals)."""
    mask = [True] * len(s)
    i, n = 0, len(s)
    while i < n:
        c = s[i]
        if s.startswith("//", i):
            j = s.find("\n", i)
            j = n if j < 0 else j
            for k in range(i, j):
                mask[k] = False
            i = j
        elif s.startswith("/*", i):
            j = s.find("*/", i + 2)
            j = n if j < 0 else j + 2
            for k in range(i, j):
                mask[k] = False
            i = j
        elif s.startswith('"""', i):
            j = s.find('"""', i + 3)
            j = n if j < 0 else j + 3
            for k in range(i, j):
                mask[k] = False
            i = j
        elif c in "\"'":
            j = i + 1
            while j < n and s[j] != c:
                j += 2 if s[j] == "\\" else 1
            j += 1
            for k in range(i, min(j, n)):
                mask[k] = False
            i = j
        else:
            i += 1
    return mask


def match_forward(s, mask, i):
    """s[i] is an opener; return index after its matching closer."""
    pairs = {"(": ")", "[": "]", "{": "}"}
    stack = [pairs[s[i]]]
    j = i + 1
    while j < len(s) and stack:
        if mask[j]:
            c = s[j]
            if c in pairs:
                stack.append(pairs[c])
            elif c in ")]}":
                if c != stack[-1]:
                    raise ValueError("unbalanced at %d" % j)
                stack.pop()
        j += 1
    return j


def match_backward(s, mask, i):
    """s[i] is a closer; return index of its matching opener."""
    pairs = {")": "(", "]": "[", "}": "{"}
    stack = [pairs[s[i]]]
    j = i - 1
    while j >= 0 and stack:
        if mask[j]:
            c = s[j]
            if c in pairs:
                stack.append(pairs[c])
            elif c in "([{":
                if c != stack[-1]:
                    raise ValueError("unbalanced at %d" % j)
                stack.pop()
                if not stack:
                    return j
        j -= 1
    return j


def skip_ws(s, mask, i):
    while i < len(s) and (s[i].isspace() or not mask[i]):
        i += 1
    return i


def skip_ws_back(s, mask, i):
    while i >= 0 and (s[i].isspace() or not mask[i]):
        i -= 1
    return i


def split_args(s, mask, start, end):
    """Top-level comma split of s[start:end] (inside parens)."""
    args, depth, last = [], 0, start
    for k in range(start, end):
        if not mask[k]:
            continue
        c = s[k]
        if c in "([{":
            depth += 1
        elif c in ")]}":
            depth -= 1
        elif c == "," and depth == 0:
            args.append(s[last:k])
            last = k + 1
    tail = s[last:end]
    if tail.strip() or args:
        args.append(tail)
    return args


def receiver_start(s, mask, dot):
    """Start index of the primary expression ending right before s[dot] == '.' (e.g. a.b(c)[d].e())."""
    end = skip_ws_back(s, mask, dot - 1)
    j = end
    while j >= 0 and mask[j]:
        if s[j] in ")]":
            j = match_backward(s, mask, j) - 1
            if s[j + 1] == "(" and j >= 0 and (s[j].isalnum() or s[j] in "_$"):
                pass  # method call: consume its name below
            elif s[j + 1] == "[":
                continue
            else:
                break
        if j >= 0 and (s[j].isalnum() or s[j] in "_$"):
            while j >= 0 and (s[j].isalnum() or s[j] in "_$"):
                j -= 1
        else:
            break
        k = skip_ws_back(s, mask, j)
        if k >= 0 and s[k] == ".":
            j = skip_ws_back(s, mask, k - 1)
            continue
        break
    start = j + 1
    while start <= end and (s[start].isspace() or not mask[start]):
        start += 1
    return start, end + 1


def impl_ranges(s, mask):
    ranges = []
    for m in IMPL_RE.finditer(s):
        if not mask[m.start()]:
            continue
        brace = m.end() - 1
        ranges.append((brace, match_forward(s, mask, brace)))
    return ranges


def is_statement_start(s, mask, start):
    k = skip_ws_back(s, mask, start - 1)
    if k < 0:
        return True
    if s[k] in STATEMENT_PREV:
        return True
    return s[max(0, k - 3):k + 1] == "else" and (k < 4 or not (s[k - 4].isalnum() or s[k - 4] == "_"))


def line_of(s, i):
    return s.count("\n", 0, i) + 1


def rewrite(s, path, manual):
    mask = code_mask(s)
    excluded = impl_ranges(s, mask)
    edits = []  # (start, end, replacement)

    def excluded_at(i):
        return any(a <= i < b for a, b in excluded)

    for m in re.finditer(r"\.\s*(addVertex|putBulkData)\s*\(", s):
        dot = m.start()
        if not mask[dot] or excluded_at(dot):
            if mask[dot] and excluded_at(dot):
                manual.append((path, line_of(s, dot), "inside VertexConsumer implementation"))
            continue
        name = m.group(1)
        rstart, rend = receiver_start(s, mask, dot)
        receiver = s[rstart:rend]
        if not receiver or re.match(r"(?:com\.stardew\.craft\.port\.)?PortVertex\.of\s*\(", receiver):
            continue
        if receiver in ("this", "super") or receiver.endswith(".this"):
            manual.append((path, line_of(s, dot), "%s on %s" % (name, receiver)))
            continue
        open_paren = m.end() - 1
        close = match_forward(s, mask, open_paren)
        args = split_args(s, mask, open_paren + 1, close - 1)
        if name == "putBulkData":
            if len(args) != 8:
                continue
            new = "PortVertex.putBulkData(" + receiver + ", " + ",".join(args).lstrip() + ")"
            edits.append((rstart, close, new))
            continue
        if not is_statement_start(s, mask, rstart):
            manual.append((path, line_of(s, dot), "addVertex chain used as expression"))
            continue
        # parse chained calls
        j = close
        chain = []
        ok = True
        while True:
            k = skip_ws(s, mask, j)
            if k < len(s) and s[k] == ";":
                end = k
                break
            if k < len(s) and s[k] == ".":
                mm = re.compile(r"\.\s*(\w+)\s*\(").match(s, k)
                if not mm or mm.group(1) not in CHAIN_METHODS:
                    ok = False
                    manual.append((path, line_of(s, k), "unsupported chained call %s" % (mm.group(1) if mm else "?")))
                    break
                chain.append(mm.group(1))
                j = match_forward(s, mask, mm.end() - 1)
                continue
            ok = False
            manual.append((path, line_of(s, dot), "addVertex chain not terminated by ';'"))
            break
        if not ok:
            continue
        if len(args) == 11 and not chain:
            new = "PortVertex.addVertex(" + receiver + ", " + ",".join(args).lstrip() + ")"
            edits.append((rstart, close, new))
            continue
        # PortVertex.of(receiver)<rest of chain>.endVertex()
        edits.append((rstart, rend, "PortVertex.of(" + receiver + ")"))
        edits.append((end, end, ".endVertex()"))

    if not edits:
        return s, 0
    edits.sort(key=lambda e: (e[0], e[1]), reverse=True)
    out = s
    for a, b, r in edits:
        out = out[:a] + r + out[b:]
    out = ensure_import(out)
    return out, len([e for e in edits if e[2] != ".endVertex()"])


def ensure_import(s):
    if re.search(r"^import\s+" + re.escape(IMPORT) + r"\s*;", s, re.M):
        return s
    m = list(re.finditer(r"^import\s+[\w.*]+\s*;\s*$", s, re.M))
    line = "import " + IMPORT + ";"
    if m:
        pos = m[-1].end()
        return s[:pos] + "\n" + line + s[pos:]
    pm = re.search(r"^package\s+[\w.]+\s*;\s*$", s, re.M)
    pos = pm.end() if pm else 0
    return s[:pos] + "\n\n" + line + s[pos:]


def main(argv):
    dry = "--dry-run" in argv
    argv = [a for a in argv if a != "--dry-run"]
    roots = argv or ["src/main/java"]
    manual, total, files = [], 0, 0
    for root in roots:
        paths = [root] if root.endswith(".java") else [
            os.path.join(d, f) for d, _, fs in os.walk(root) for f in fs if f.endswith(".java")]
        for path in sorted(paths):
            with open(path, encoding="utf-8") as fh:
                s = fh.read()
            if "addVertex" not in s and "putBulkData" not in s:
                continue
            out, n = rewrite(s, path, manual)
            if out != s:
                if not dry:
                    with open(path, "w", encoding="utf-8") as fh:
                        fh.write(out)
                total += n
                files += 1
                print("rewrote %3d  %s" % (n, path))
    for path, line, why in manual:
        print("MANUAL %s:%d: %s" % (path, line, why))
    print("total: %d rewrites in %d files, %d manual" % (total, files, len(manual)))


if __name__ == "__main__":
    main(sys.argv[1:])

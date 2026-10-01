#!/usr/bin/env python3
"""Idempotent rewrite of 1.21.1 vanilla/NeoForge override signatures to their Forge 1.20.1 shapes.

Usage:
    python3 scripts/port/adapt_overrides_1201.py [--check] [paths...]   (default: src/main/java)

Only declarations carrying ``@Override`` are rewritten (plus the matching ``super.`` calls inside them and, where the
old arity is unambiguous, other call sites). Each rule is the exact 1.20.1 counterpart of the 1.21.1 method:

* ``Block#codec()`` (1.20.3+, only feeds the block-type registry that 1.20.1 lacks): the override and a ``CODEC``
  field used only by it are removed.
* ``Block#playerWillDestroy`` returns ``void`` in 1.20.1 (the returned state is unused by every caller in the mod).
* ``Block#getCloneItemStack(LevelReader, …)`` / Forge's 5-arg variant take a ``BlockGetter``.
* ``Block#isPathfindable(state, type)`` -> ``isPathfindable(state, level, pos, type)``.
* ``Item#getUseDuration(stack, entity)`` -> ``getUseDuration(stack)``.
* NeoForge ``IItemExtension#canBeHurtBy(stack, source)`` -> vanilla ``Item#canBeHurtBy(source)``.
* ``GuiEventListener#mouseScrolled(x, y, scrollX, scrollY)`` -> ``mouseScrolled(x, y, delta)`` (delta == scrollY).
* ``Entity#defineSynchedData(SynchedEntityData.Builder)`` -> ``defineSynchedData()`` + ``entityData.define``.
* ``Mob#canBeLeashed()`` -> ``canBeLeashed(Player)``.
* ``Entity#lerpTo(x, y, z, yRot, xRot, steps)`` -> ``lerpTo(…, steps, teleport)``.
* ``Item#appendHoverText(stack, Item.TooltipContext, tooltip, flag)`` -> ``appendHoverText(stack, Level, tooltip, flag)``
  (``context.level()`` -> the level, ``context.registries()`` -> its registry access, ``tickRate()`` -> 20).
* ``BlockBehaviour`` methods made ``protected`` in 1.20.5 are ``public`` in 1.20.1: ``@Override protected`` declarations
  of those names in block classes are widened to ``public``.

Rewrites that need judgement are reported as warnings.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


# ---------------------------------------------------------------- parsing helpers

def skip_string(text: str, i: int) -> int:
    q = text[i]
    if text.startswith('"""', i):
        end = text.find('"""', i + 3)
        return end + 3
    i += 1
    while i < len(text) and text[i] != q:
        i += 2 if text[i] == "\\" else 1
    return i + 1


def skip_comment(text: str, i: int) -> int:
    if text.startswith("//", i):
        end = text.find("\n", i)
        return len(text) if end < 0 else end
    if text.startswith("/*", i):
        end = text.find("*/", i + 2)
        return len(text) if end < 0 else end + 2
    return i


def match_close(text: str, open_idx: int) -> int:
    pairs = {"(": ")", "{": "}", "[": "]"}
    stack = []
    i = open_idx
    while i < len(text):
        c = text[i]
        if c in "\"'":
            i = skip_string(text, i)
            continue
        if text.startswith("//", i) or text.startswith("/*", i):
            i = skip_comment(text, i)
            continue
        if c in pairs:
            stack.append(pairs[c])
        elif c in ")}]":
            if not stack or stack[-1] != c:
                return -1
            stack.pop()
            if not stack:
                return i
        i += 1
    return -1


def split_top(s: str) -> list[str]:
    parts, depth, cur, i = [], 0, [], 0
    while i < len(s):
        c = s[i]
        if c in "\"'":
            j = skip_string(s, i)
            cur.append(s[i:j])
            i = j
            continue
        if c in "([{<":
            depth += 1
        elif c in ")]}>":
            depth -= 1
        if c == "," and depth == 0:
            parts.append("".join(cur))
            cur = []
        else:
            cur.append(c)
        i += 1
    if "".join(cur).strip():
        parts.append("".join(cur))
    return parts


def param_name(param: str) -> str:
    return re.findall(r"\w+", param)[-1]


def param_type(param: str) -> str:
    p = re.sub(r"@\w+(?:\.\w+)*(?:\([^)]*\))?", "", param)
    p = re.sub(r"\bfinal\b", "", p).strip()
    return p.rsplit(None, 1)[0] if len(p.split()) > 1 else p


class Method:
    """A method declaration: head (modifiers/annotations), return type, name, params, body."""

    def __init__(self, text: str, name_start: int, name: str):
        self.name = name
        self.name_start = name_start
        self.p_open = text.index("(", name_start)
        self.p_close = match_close(text, self.p_open)
        self.params = split_top(text[self.p_open + 1:self.p_close])
        k = self.p_close + 1
        while k < len(text) and text[k] in " \t\n":
            k += 1
        if text.startswith("throws", k):
            k = text.index("{", k)
        self.b_open = k if k < len(text) and text[k] == "{" else -1
        self.b_close = match_close(text, self.b_open) if self.b_open >= 0 else -1
        j = name_start
        while j > 0 and text[j - 1] not in ";{}":
            if text[j - 1] == ")":
                # Skip annotation arguments such as @SuppressWarnings({"a", "b"}).
                depth = 0
                while j > 0:
                    j -= 1
                    if text[j] == ")":
                        depth += 1
                    elif text[j] == "(":
                        depth -= 1
                        if depth == 0:
                            break
                continue
            j -= 1
        # Do not swallow a preceding line comment's text into the head.
        self.head_start = j
        self.head = text[j:name_start]

    @property
    def is_override(self) -> bool:
        return "@Override" in self.head

    def body(self, text: str) -> str:
        return text[self.b_open:self.b_close + 1]


def find_methods(text: str, name: str) -> list[Method]:
    out = []
    for m in re.finditer(r"(?<![\w.])" + name + r"\s*\(", text):
        # Declaration: preceded by a type token (identifier, '>' or ']'), not by '.', 'new' or 'return'.
        k = m.start()
        while k > 0 and text[k - 1] in " \t\n":
            k -= 1
        if k == 0 or not (text[k - 1].isalnum() or text[k - 1] in "_>]"):
            continue
        word = re.search(r"(\w+)$", text[:k])
        if word and word.group(1) in ("new", "return", "throw", "else", "case", "yield"):
            continue
        try:
            meth = Method(text, m.start(), name)
        except ValueError:
            continue
        if meth.p_close < 0 or meth.b_open < 0 or meth.b_close < 0:
            continue
        out.append(meth)
    return out


def calls(text: str, name: str, start: int = 0, end: int | None = None, receiver: str | None = None):
    """Yield (call_start, open, close, args) for calls of `name` in text[start:end] (last first)."""
    end = len(text) if end is None else end
    prefix = (re.escape(receiver) + r"\s*\.\s*") if receiver else r"(?<![\w])(?:[\w)\]]\s*\.\s*)?"
    found = []
    for m in re.finditer(prefix + name + r"\s*\(", text[start:end]):
        s = start + m.start()
        o = start + m.end() - 1
        c = match_close(text, o)
        if c < 0:
            continue
        found.append((s, o, c, split_top(text[o + 1:c])))
    return reversed(found)


def ensure_import(text: str, fqn: str) -> str:
    if re.search(r"^import\s+" + re.escape(fqn) + r"\s*;", text, re.M):
        return text
    imports = list(re.finditer(r"^import\s+[\w.]+\s*;", text, re.M))
    pkg = re.search(r"^package\s+[\w.]+\s*;", text, re.M)
    at = text.find("\n", (imports[-1] if imports else pkg).end()) + 1
    return text[:at] + f"import {fqn};\n" + text[at:]


def simple_or_fqn(text: str, simple: str, fqn: str) -> str:
    """Use the simple name when it is (or can safely be) imported, else the FQN."""
    if re.search(r"^import\s+" + re.escape(fqn) + r"\s*;", text, re.M):
        return simple
    if re.search(r"^import\s+[\w.]+\." + simple + r"\s*;", text, re.M):
        return fqn
    pkg = re.search(r"^package\s+([\w.]+)\s*;", text, re.M)
    if pkg and fqn.rsplit(".", 1)[0] == pkg.group(1):
        return simple
    return fqn


def replace_params(text: str, meth: Method, new_params: list[str]) -> str:
    sep = ", "
    old = text[meth.p_open + 1:meth.p_close]
    if "\n" in old and len(new_params) > 1:
        indent = re.search(r"\n([ \t]*)\S", old)
        sep = ",\n" + indent.group(1) if indent else ", "
    return text[:meth.p_open + 1] + sep.join(p.strip() for p in new_params) + text[meth.p_close:]


# ---------------------------------------------------------------- rules

class Ctx:
    def __init__(self, rel: str):
        self.rel = rel
        self.warnings: list[str] = []

    def warn(self, msg: str):
        self.warnings.append(f"{self.rel}: {msg}")


def rule_codec(text: str, ctx: Ctx, codec_refs: set[str]) -> str:
    for meth in reversed(find_methods(text, "codec")):
        if meth.params or not meth.is_override:
            continue
        if not re.search(r"MapCodec\s*<", meth.head):
            continue
        # Only block types: ParticleType#codec and friends keep their override.
        if not re.search(r"MapCodec\s*<[^>]*(Block|\?\s*extends\s+[\w.]*Block)\b", meth.head) and not is_block_file(text):
            continue
        body = meth.body(text)
        start = line_start(text, first_nonblank(text, meth.head_start))
        end = meth.b_close + 1
        if text[end:end + 1] == "\n":
            end += 1
        # Swallow one trailing blank line so no double gap is left behind.
        if text[end:end + 1] == "\n" and text[start - 2:start] == "\n\n":
            end += 1
        text = text[:start] + text[end:]
        if re.fullmatch(r"\{\s*return\s+CODEC\s*;\s*\}", body.strip()):
            text = drop_codec_field(text, ctx, codec_refs)
    return text


def is_block_file(text: str) -> bool:
    return bool(re.search(r"\bclass\s+\w+[^{]*\bextends\s+[\w.]*(Block|BlockBase)\b", text))


def first_nonblank(text: str, i: int) -> int:
    while i < len(text) and text[i] in " \t\n":
        i += 1
    return i


def line_start(text: str, i: int) -> int:
    return text.rfind("\n", 0, i) + 1


def drop_codec_field(text: str, ctx: Ctx, codec_refs: set[str]) -> str:
    cls = re.search(r"\bclass\s+(\w+)", text).group(1)
    if cls in codec_refs:
        ctx.warn(f"{cls}.CODEC referenced elsewhere; field kept")
        return text
    m = re.search(r"^[ \t]*(?:public|protected|private)?\s*static\s+final\s+(?:com\.mojang\.serialization\.)?MapCodec\s*<[^;]*?>\s+CODEC\s*=", text, re.M)
    if not m:
        return text
    end = -1
    # Field initialisers can contain ';' only inside lambdas with braces; find the statement end at depth 0.
    i = m.end()
    depth = 0
    while i < len(text):
        c = text[i]
        if c in "\"'":
            i = skip_string(text, i)
            continue
        if c in "({[":
            depth += 1
        elif c in ")}]":
            depth -= 1
        elif c == ";" and depth == 0:
            end = i
            break
        i += 1
    after = end + 1
    if text[after:after + 1] == "\n":
        after += 1
    new = text[:m.start()] + text[after:]
    if re.search(r"(?<![\w.])CODEC\b", new):
        ctx.warn("CODEC still referenced in file; field kept")
        return text
    return new


def rule_player_will_destroy(text: str, ctx: Ctx) -> str:
    for meth in reversed(find_methods(text, "playerWillDestroy")):
        if not meth.is_override or not re.search(r"(?:[\w.]+\.)?BlockState\s+$", meth.head):
            continue
        body_s, body_e = meth.b_open, meth.b_close
        body = text[body_s:body_e + 1]
        new_body = rewrite_returns_void(body, ctx)
        text = text[:body_s] + new_body + text[body_e + 1:]
        rt = re.search(r"(?:[\w.]+\.)?BlockState(\s+)$", meth.head)
        a = meth.head_start + rt.start()
        b = meth.head_start + rt.end()
        text = text[:a] + "void" + rt.group(1) + text[b:]
    return text


def rewrite_returns_void(body: str, ctx: Ctx) -> str:
    """Turn `return X;` statements of a BlockState-returning method into void returns."""
    out = []
    i = 0
    pattern = re.compile(r"(?<![\w])return\b")
    pos = 0
    for m in pattern.finditer(body):
        if m.start() < pos:
            continue
        # Skip returns inside nested lambdas / anonymous classes: only rewrite at brace depth 1 of the method.
        if depth_at(body, m.start()) is None:
            continue
        semi = statement_end(body, m.end())
        expr = body[m.end():semi].strip()
        out.append(body[pos:m.start()])
        last = body[semi + 1:].strip() == "}"
        prev = body[:m.start()].rstrip()
        braceless = prev.endswith(")") or prev.endswith("else")
        if not expr:
            stmt = "return;"
        elif re.fullmatch(r"\w+", expr):
            stmt = "" if last else "return;"
        elif re.fullmatch(r"super\.playerWillDestroy\s*\(.*\)", expr, re.S) or re.fullmatch(r"[\w.]+\s*\(.*\)", expr, re.S):
            stmt = f"{expr};" if last else (f"{{ {expr}; return; }}" if braceless else f"{expr}; return;")
        else:
            ctx.warn(f"playerWillDestroy returns non-trivial expression '{expr[:60]}' - check by hand")
            stmt = f"return; // PORT(1.20.1): was return {expr};"
        out.append(stmt)
        pos = semi + 1
        if not stmt:
            # Drop the now-empty line.
            out[-1] = out[-1].rstrip(" \t")
            if out[-1].endswith("\n") and body[pos:pos + 1] == "\n":
                pos += 1
    out.append(body[pos:])
    return "".join(out)


def depth_at(body: str, idx: int):
    """Return True when idx is directly inside the method body (not in a lambda/inner class block)."""
    depth = 0
    i = 0
    lambda_depths = []
    while i < idx:
        c = body[i]
        if c in "\"'":
            i = skip_string(body, i)
            continue
        if body.startswith("//", i) or body.startswith("/*", i):
            i = skip_comment(body, i)
            continue
        if c == "{":
            depth += 1
            prev = body[:i].rstrip()
            if prev.endswith("->") or prev.endswith(")") and re.search(r"new\s+[\w.<>]+\s*\([^()]*\)$", prev):
                lambda_depths.append(depth)
        elif c == "}":
            if lambda_depths and lambda_depths[-1] == depth:
                lambda_depths.pop()
            depth -= 1
        i += 1
    return None if lambda_depths else True


def statement_end(body: str, i: int) -> int:
    depth = 0
    while i < len(body):
        c = body[i]
        if c in "\"'":
            i = skip_string(body, i)
            continue
        if c in "({[":
            depth += 1
        elif c in ")}]":
            depth -= 1
        elif c == ";" and depth == 0:
            return i
        i += 1
    return len(body) - 1


def rule_clone_item_stack(text: str, ctx: Ctx) -> str:
    for meth in reversed(find_methods(text, "getCloneItemStack")):
        if not meth.is_override:
            continue
        idx = {3: 0, 5: 2}.get(len(meth.params))
        if idx is None or not re.search(r"\bLevelReader\b", meth.params[idx]):
            continue
        bg = simple_or_fqn(text, "BlockGetter", "net.minecraft.world.level.BlockGetter")
        params = list(meth.params)
        params[idx] = re.sub(r"(?:net\.minecraft\.world\.level\.)?LevelReader\b", bg, params[idx])
        text = replace_params(text, meth, params)
        if bg == "BlockGetter":
            text = ensure_import(text, "net.minecraft.world.level.BlockGetter")
    return text


def rule_pathfindable(text: str, ctx: Ctx) -> str:
    for meth in reversed(find_methods(text, "isPathfindable")):
        if not meth.is_override or len(meth.params) != 2:
            continue
        body = meth.body(text)
        lv, ps = "level", "pos"
        if re.search(r"\blevel\b", body) or re.search(r"\bpos\b", body):
            lv, ps = "pathLevel", "pathPos"
        state_p, type_p = meth.params
        # super.isPathfindable(a, b) -> super.isPathfindable(a, level, pos, b)
        for s, o, c, args in calls(text, "isPathfindable", meth.b_open, meth.b_close, receiver="super"):
            if len(args) == 2:
                text = text[:o + 1] + f"{args[0].strip()}, {lv}, {ps}, {args[1].strip()}" + text[c:]
        bg = simple_or_fqn(text, "BlockGetter", "net.minecraft.world.level.BlockGetter")
        bp = simple_or_fqn(text, "BlockPos", "net.minecraft.core.BlockPos")
        text = replace_params(text, meth, [state_p, f"{bg} {lv}", f"{bp} {ps}", type_p])
        if bg == "BlockGetter":
            text = ensure_import(text, "net.minecraft.world.level.BlockGetter")
        if bp == "BlockPos":
            text = ensure_import(text, "net.minecraft.core.BlockPos")
    return text


def rule_drop_param(name: str, arity: int, drop: int, type_re: str):
    def rule(text: str, ctx: Ctx) -> str:
        for meth in reversed(find_methods(text, name)):
            if not meth.is_override or len(meth.params) != arity:
                continue
            if not re.search(type_re, param_type(meth.params[drop])):
                continue
            pname = param_name(meth.params[drop])
            for s, o, c, args in calls(text, name, meth.b_open, meth.b_close, receiver="super"):
                if len(args) == arity:
                    text = text[:o + 1] + ", ".join(a.strip() for j, a in enumerate(args) if j != drop) + text[c:]
            meth = Method(text, meth.name_start, name)
            if re.search(r"(?<![\w.])" + pname + r"\b", meth.body(text)):
                ctx.warn(f"{name}: dropped parameter '{pname}' is still used in the body")
            text = replace_params(text, meth, [p for j, p in enumerate(meth.params) if j != drop])
        return text
    return rule


def rule_mouse_scrolled(text: str, ctx: Ctx) -> str:
    for meth in reversed(find_methods(text, "mouseScrolled")):
        if not meth.is_override or len(meth.params) != 4:
            continue
        sx = param_name(meth.params[2])
        body = meth.body(text)
        uses_sx = re.search(r"(?<![\w.])" + sx + r"\b", body[1:]) is not None
        params = [meth.params[0], meth.params[1], meth.params[3]]
        if uses_sx:
            indent = re.search(r"\n([ \t]*)\S", body)
            ind = indent.group(1) if indent else "        "
            text = (text[:meth.b_open + 1] + f"\n{ind}double {sx} = 0.0D; // PORT(1.20.1): no horizontal scroll before 1.20.2"
                    + text[meth.b_open + 1:])
        text = replace_params(text, meth, params)
    # Every 4-argument mouseScrolled call becomes the 1.20.1 3-argument form (vertical delta).
    for s, o, c, args in calls(text, "mouseScrolled"):
        if len(args) == 4:
            text = text[:o + 1] + ", ".join(a.strip() for j, a in enumerate(args) if j != 2) + text[c:]
    return text


def rule_define_synched(text: str, ctx: Ctx) -> str:
    for meth in reversed(find_methods(text, "defineSynchedData")):
        if len(meth.params) != 1 or "Builder" not in meth.params[0]:
            continue
        if not meth.is_override:
            continue
        b = param_name(meth.params[0])
        body = meth.body(text)
        new_body = re.sub(r"(?<![\w.])" + b + r"\s*\.\s*define\s*\(", "this.entityData.define(", body)
        new_body = re.sub(r"\bsuper\s*\.\s*defineSynchedData\s*\(\s*" + b + r"\s*\)", "super.defineSynchedData()", new_body)
        if re.search(r"(?<![\w.])" + b + r"\b", new_body[1:]):
            ctx.warn(f"defineSynchedData: builder '{b}' still used in the body")
        text = text[:meth.b_open] + new_body + text[meth.b_close + 1:]
        meth = Method(text, meth.name_start, "defineSynchedData")
        text = replace_params(text, meth, [])
    return text


def rule_can_be_leashed(text: str, ctx: Ctx) -> str:
    for meth in reversed(find_methods(text, "canBeLeashed")):
        if not meth.is_override or meth.params:
            continue
        pl = simple_or_fqn(text, "Player", "net.minecraft.world.entity.player.Player")
        for s, o, c, args in calls(text, "canBeLeashed", meth.b_open, meth.b_close, receiver="super"):
            if not args:
                text = text[:o + 1] + "player" + text[c:]
        meth = Method(text, meth.name_start, "canBeLeashed")
        text = replace_params(text, meth, [f"{pl} player"])
        if pl == "Player":
            text = ensure_import(text, "net.minecraft.world.entity.player.Player")
    return text


def rule_lerp_to(text: str, ctx: Ctx) -> str:
    for meth in reversed(find_methods(text, "lerpTo")):
        if not meth.is_override or len(meth.params) != 6:
            continue
        for s, o, c, args in calls(text, "lerpTo", meth.b_open, meth.b_close, receiver="super"):
            if len(args) == 6:
                text = text[:o + 1] + ", ".join(a.strip() for a in args) + ", teleport" + text[c:]
        meth = Method(text, meth.name_start, "lerpTo")
        text = replace_params(text, meth, list(meth.params) + ["boolean teleport"])
    return text


def rule_append_hover_text(text: str, ctx: Ctx) -> str:
    for meth in reversed(find_methods(text, "appendHoverText")):
        if not meth.is_override or len(meth.params) != 4 or not re.search(r"TooltipContext$", param_type(meth.params[1])):
            continue
        c = param_name(meth.params[1])
        body = meth.body(text)
        lv = "level" if not re.search(r"(?<![\w.])level\b", body) else "tooltipLevel"
        new_body = re.sub(r"(?<![\w.])" + c + r"\s*\.\s*level\s*\(\s*\)", lv, body)
        new_body = re.sub(r"(?<![\w.])" + c + r"\s*\.\s*registries\s*\(\s*\)",
                          f"({lv} == null ? null : {lv}.registryAccess())", new_body)
        new_body = re.sub(r"(?<![\w.])" + c + r"\s*\.\s*tickRate\s*\(\s*\)", "20.0F", new_body)
        new_body = re.sub(r"(\bsuper\s*\.\s*appendHoverText\s*\([^,]*,\s*)" + c + r"(\s*,)", r"\g<1>" + lv + r"\2", new_body)
        if re.search(r"(?<![\w.])" + c + r"\b", new_body):
            ctx.warn(f"appendHoverText: tooltip context '{c}' still used in the body")
        text = text[:meth.b_open] + new_body + text[meth.b_close + 1:]
        meth = Method(text, meth.name_start, "appendHoverText")
        level_type = simple_or_fqn(text, "Level", "net.minecraft.world.level.Level")
        params = list(meth.params)
        params[1] = f"@javax.annotation.Nullable {level_type} {lv}"
        text = replace_params(text, meth, params)
        if level_type == "Level":
            text = ensure_import(text, "net.minecraft.world.level.Level")
    return text


# Public in 1.20.1 BlockBehaviour, protected in 1.21.1 (javap vs. 1.21.1 sources).
BLOCK_PUBLIC_1201 = set("""
attack canBeReplaced canSurvive entityInside getAnalogOutputSignal getBlockSupportShape getCollisionShape
getDestroyProgress getDirectSignal getDrops getFluidState getInteractionShape getLightBlock getMaxHorizontalOffset
getMaxVerticalOffset getMenuProvider getOcclusionShape getRenderShape getSeed getShadeBrightness getShape getSignal
getVisualShape hasAnalogOutputSignal isCollisionShapeFullBlock isOcclusionShapeFullBlock isPathfindable
isSignalSource mirror neighborChanged propagatesSkylightDown onPlace onProjectileHit onRemove randomTick rotate skipRendering
spawnAfterBreak tick triggerEvent updateIndirectNeighbourShapes updateShape useShapeForLightOcclusion
""".split())


def rule_block_access(text: str, ctx: Ctx) -> str:
    for name in BLOCK_PUBLIC_1201:
        if name + "(" not in text.replace(" (", "("):
            continue
        for meth in reversed(find_methods(text, name)):
            if not meth.is_override or not re.search(r"\bprotected\b", meth.head):
                continue
            new_head = re.sub(r"\bprotected\b", "public", meth.head, count=1)
            text = text[:meth.head_start] + new_head + text[meth.name_start:]
    return text


def block_class_names(files) -> set[str]:
    parent = {}
    for p in files:
        for m in re.finditer(r"\bclass\s+(\w+)\s*(?:<[^{]*?>)?\s+extends\s+([\w.]+)", p.read_text(encoding="utf-8")):
            parent[m.group(1)] = m.group(2).rsplit(".", 1)[-1]
    blocks = set()
    changed = True
    while changed:
        changed = False
        for c, par in parent.items():
            if c not in blocks and (par.endswith("Block") or par in blocks):
                blocks.add(c)
                changed = True
    return blocks


RULES = [
    rule_player_will_destroy,
    rule_clone_item_stack,
    rule_pathfindable,
    rule_drop_param("getUseDuration", 2, 1, r"LivingEntity$"),
    rule_drop_param("canBeHurtBy", 2, 0, r"ItemStack$"),
    rule_mouse_scrolled,
    rule_define_synched,
    rule_can_be_leashed,
    rule_lerp_to,
    rule_append_hover_text,
]


def global_call_rules(text: str) -> str:
    # Item#getUseDuration(stack, entity) callers -> getUseDuration(stack).
    for s, o, c, args in calls(text, "getUseDuration"):
        if len(args) == 2:
            text = text[:o + 1] + args[0].strip() + text[c:]
    return text


def collect(paths):
    out = []
    for t in paths:
        out.extend([t] if t.is_file() else sorted(t.rglob("*.java")))
    return out


def main(argv):
    check = "--check" in argv
    argv = [a for a in argv if a != "--check"]
    targets = [Path(a).resolve() for a in argv] or [ROOT / "src/main/java"]
    codec_refs = set()
    blocks = block_class_names(collect([ROOT / "src/main/java"]))
    for p in collect([ROOT / "src/main/java"]):
        for m in re.finditer(r"(?<![\w])(\w+)\.CODEC\b", p.read_text(encoding="utf-8")):
            codec_refs.add(m.group(1))
    warnings = []
    changed = 0
    for path in collect(targets):
        original = path.read_text(encoding="utf-8")
        rel = str(path.relative_to(ROOT)) if path.is_relative_to(ROOT) else str(path)
        ctx = Ctx(rel)
        text = rule_codec(original, ctx, codec_refs)
        for rule in RULES:
            text = rule(text, ctx)
        if any(m.group(1) in blocks for m in re.finditer(r"\bclass\s+(\w+)", text)):
            text = rule_block_access(text, ctx)
        text = global_call_rules(text)
        warnings.extend(ctx.warnings)
        if text != original:
            changed += 1
            if not check:
                path.write_text(text, encoding="utf-8")
    for w in warnings:
        print("WARN", w)
    print(f"{'would change' if check else 'adapted'} {changed} files")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

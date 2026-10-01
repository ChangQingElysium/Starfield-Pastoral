#!/usr/bin/env python3
"""Mechanical, idempotent 1.21.1 NeoForge -> 1.20.1 Forge source rewrite.

Usage:
    python3 scripts/port/rewrite_1201.py [paths...]      (default: src/main/java)

Only purely mechanical substitutions live here. Anything that needs judgement is
fixed by hand in the source or absorbed by the shim layer in src/port/java.
Run it on every file that arrives from main (cherry-pick) before compiling.

Rules, in order:
1. Fixed class substitutions (SPECIAL_CLASSES) and identifier renames.
2. Classes listed in shim-classes.txt are redirected to
   com.stardew.craft.port.<original FQN> (the shim keeps the 1.21.1 API shape).
3. Remaining net.neoforged packages are renamed to their Forge 1.20.1 packages.
4. Small vanilla API differences (ResourceLocation factories, built-in stream codecs).
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SHIM_PREFIX = "com.stardew.craft.port."

SPECIAL_CLASSES = {
    # NeoForge routes @EventBusSubscriber listeners by event type (bus is ignored); the port annotation keeps that.
    "net.neoforged.fml.common.EventBusSubscriber": SHIM_PREFIX + "net.neoforged.fml.common.EventBusSubscriber",
    "net.neoforged.neoforge.common.NeoForge": "net.minecraftforge.common.MinecraftForge",
    "net.neoforged.neoforge.common.ModConfigSpec": "net.minecraftforge.common.ForgeConfigSpec",
    "net.minecraft.world.level.chunk.status.ChunkStatus": "net.minecraft.world.level.chunk.ChunkStatus",
    # GeckoLib 4.x for 1.20.1 still ships its loader-neutral classes under geckolib.core.
    "software.bernie.geckolib.animatable.instance.AnimatableInstanceCache": "software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache",
    "software.bernie.geckolib.animation.AnimatableManager": "software.bernie.geckolib.core.animation.AnimatableManager",
    "software.bernie.geckolib.animation.AnimationController": "software.bernie.geckolib.core.animation.AnimationController",
    "software.bernie.geckolib.animation.AnimationState": "software.bernie.geckolib.core.animation.AnimationState",
    "software.bernie.geckolib.animation.PlayState": "software.bernie.geckolib.core.object.PlayState",
    "software.bernie.geckolib.animation.RawAnimation": "software.bernie.geckolib.core.animation.RawAnimation",
    "software.bernie.geckolib.util.Color": "software.bernie.geckolib.core.object.Color",
}

# NeoForge classes whose Forge 1.20.1 counterpart has the same members the mod uses (registries/utility owner).
# Earlier rewrites redirected some of these to shim FQNs; map those as well so re-running stays idempotent.
FORGE_EQUIVALENTS = {
    "net.neoforged.neoforge.common.ItemAbilities": "net.minecraftforge.common.ToolActions",
    "net.neoforged.neoforge.common.ItemAbility": "net.minecraftforge.common.ToolAction",
    "net.neoforged.neoforge.common.CommonHooks": "net.minecraftforge.common.ForgeHooks",
    "net.neoforged.neoforge.client.ClientHooks": "net.minecraftforge.client.ForgeHooksClient",
    "net.neoforged.neoforge.event.EventHooks": "net.minecraftforge.event.ForgeEventFactory",
    "net.neoforged.neoforge.common.extensions.IBlockExtension": "net.minecraftforge.common.extensions.IForgeBlock",
    "net.neoforged.neoforge.client.model.ExtraFaceData": "net.minecraftforge.client.model.ForgeFaceData",
    "net.neoforged.neoforge.fluids.BaseFlowingFluid": "net.minecraftforge.fluids.ForgeFlowingFluid",
    "net.neoforged.neoforge.gametest.GameTestHooks": "net.minecraftforge.gametest.ForgeGameTestHooks",
}
for _old, _new in FORGE_EQUIVALENTS.items():
    SPECIAL_CLASSES[_old] = _new
    SPECIAL_CLASSES[SHIM_PREFIX + _old] = _new

IDENTIFIER_RENAMES = {
    "NeoForge": "MinecraftForge",
    "ModConfigSpec": "ForgeConfigSpec",
}
IDENTIFIER_RENAMES.update({old.rsplit(".", 1)[1]: new.rsplit(".", 1)[1] for old, new in FORGE_EQUIVALENTS.items()})

# Vanilla 1.20.5+/1.21 classes with a renamed 1.20.1 counterpart that has the members the mod uses
# (vanilla-API owner). Already-shimmed FQNs are mapped too so re-running stays idempotent.
VANILLA_EQUIVALENTS = {
    "net.minecraft.world.level.block.TransparentBlock": "net.minecraft.world.level.block.GlassBlock",
    "net.minecraft.client.gui.screens.options.controls.KeyBindsScreen": "net.minecraft.client.gui.screens.controls.KeyBindsScreen",
    "net.minecraft.client.renderer.chunk.SectionRenderDispatcher": "net.minecraft.client.renderer.chunk.ChunkRenderDispatcher",
    "net.minecraft.client.renderer.SectionBufferBuilderPack": "net.minecraft.client.renderer.ChunkBufferBuilderPack",
    "com.mojang.blaze3d.vertex.MeshData": "com.mojang.blaze3d.vertex.BufferBuilder.RenderedBuffer",
    "net.minecraft.world.level.pathfinder.PathType": "net.minecraft.world.level.pathfinder.BlockPathTypes",
}
for _old, _new in VANILLA_EQUIVALENTS.items():
    SPECIAL_CLASSES[_old] = _new
    SPECIAL_CLASSES[SHIM_PREFIX + _old] = _new
    if _old.rsplit(".", 1)[1] != _new.rsplit(".", 1)[1]:
        IDENTIFIER_RENAMES[_old.rsplit(".", 1)[1]] = _new.rsplit(".", 1)[1]

PACKAGE_RENAMES = [
    ("net.neoforged.neoforge.", "net.minecraftforge."),
    ("net.neoforged.fml.", "net.minecraftforge.fml."),
    ("net.neoforged.bus.api.", "net.minecraftforge.eventbus.api."),
    ("net.neoforged.api.distmarker.", "net.minecraftforge.api.distmarker."),
]

# Vanilla 1.21 static stream codecs -> shim equivalents.
VANILLA_CODECS = {
    "ItemStack.OPTIONAL_STREAM_CODEC": "OPTIONAL_ITEM_STACK",
    "ItemStack.STREAM_CODEC": "ITEM_STACK",
    "ResourceLocation.STREAM_CODEC": "RESOURCE_LOCATION",
    "BlockPos.STREAM_CODEC": "BLOCK_POS",
    "UUIDUtil.STREAM_CODEC": "UUID",
    "ComponentSerialization.STREAM_CODEC": "COMPONENT",
}
CODECS_CLASS = SHIM_PREFIX + "PortCodecs"

NOT_FQN_PREFIX = r"(?<![\w.])"


def load_shims() -> list[str]:
    lines = (ROOT / "scripts/port/shim-classes.txt").read_text(encoding="utf-8").splitlines()
    names = [line.strip() for line in lines if line.strip() and not line.startswith("#")]
    return sorted(names, key=len, reverse=True)


def build_rules():
    rules: list[tuple[re.Pattern, str]] = []
    for old, new in SPECIAL_CLASSES.items():
        rules.append((re.compile(NOT_FQN_PREFIX + re.escape(old) + r"\b"), new))
    for name in load_shims():
        rules.append((re.compile(NOT_FQN_PREFIX + re.escape(name) + r"\b"), SHIM_PREFIX + name))
    for old, new in PACKAGE_RENAMES:
        rules.append((re.compile(NOT_FQN_PREFIX + re.escape(old)), new))
    return rules


IDENT_RULES = [(re.compile(r"(?<![\w.])" + old + r"\b"), new) for old, new in IDENTIFIER_RENAMES.items()]

RL = r"(?:net\.minecraft\.resources\.)?ResourceLocation"
CODE_RULES = [
    # 1.20.1 only has constructors.
    (re.compile(r"(?<![\w.])(" + RL + r")\.(?:fromNamespaceAndPath|withDefaultNamespace|parse)\s*\("), r"new \1("),
    (re.compile(r"(?<![\w.])(" + RL + r")::(?:parse|withDefaultNamespace)\b"), r"\1::new"),
    # NeoForge renamed Forge's default game bus to GAME.
    (re.compile(r"\bEventBusSubscriber\.Bus\.GAME\b"), "EventBusSubscriber.Bus.FORGE"),
    (re.compile(r"\bbus\s*=\s*Bus\.GAME\b"), "bus = Bus.FORGE"),
    (re.compile(r"^import software\.bernie\.geckolib\.animation\.\*;", re.M),
     "import software.bernie.geckolib.core.animation.*;\nimport software.bernie.geckolib.core.object.PlayState;"),
    # The whole codec package is shimmed, so its wildcard import moves with it.
    (re.compile(r"^import net\.minecraft\.network\.codec\.\*;", re.M),
     "import " + SHIM_PREFIX + "net.minecraft.network.codec.*;"),
]
for key, field in VANILLA_CODECS.items():
    cls, member = key.split(".")
    CODE_RULES.append((re.compile(r"(?<![\w])(?:[a-z_][\w]*\.)*" + cls + r"\." + member + r"\b"), CODECS_CLASS + "." + field))

# Vanilla 1.20.5+/1.21 call shapes (vanilla-API owner). Helpers are emitted by simple name and imported.
VANILLA_HELPERS = ("PortBlockProperties", "PortEntities", "PortGameTests", "PortLevels")
CODE_RULES += [
    # SectionRenderDispatcher.RenderSection is 1.20.1 ChunkRenderDispatcher.RenderChunk.
    (re.compile(r"\bChunkRenderDispatcher\.RenderSection\b"), "ChunkRenderDispatcher.RenderChunk"),
    # 1.20.1 ServerPlayer(server, level, profile) has no ClientInformation; drop the default-options argument.
    (re.compile(r",\s*(?:[\w.]+\.)?ClientInformation\.createDefault\(\)"), ""),
    (re.compile(r"^import (?:" + re.escape(SHIM_PREFIX) + r")?net\.minecraft\.server\.level\.ClientInformation;\n", re.M), ""),
    # Properties.ofLegacyCopy is 1.20.1 Properties.copy; ofFullCopy additionally copies predicates/jump/drops.
    (re.compile(r"(?<![\w.])((?:[\w.]*\.)?Properties)\.ofLegacyCopy\("), r"\1.copy("),
    (re.compile(r"(?<![\w.])(?:[\w.]*\.)?Properties\.ofFullCopy\("), "PortBlockProperties.ofFullCopy("),
    # GameTestHelper#makeMockPlayer(GameType) / #assertValueEqual are 1.20.5+.
    (re.compile(r"(?<![\w.])(?!PortGameTests\b)(\w+)\.makeMockPlayer\((?=\s*[^\s)])"), r"PortGameTests.makeMockPlayer(\1, "),
    (re.compile(r"(?<![\w.])(?!PortGameTests\b)(\w+)\.assertValueEqual\("), r"PortGameTests.assertValueEqual(\1, "),
    # CollisionGetter#noBlockCollision (1.20.2+); simple receivers only, complex ones by hand.
    (re.compile(r"(?<![\w.)\]])(?!PortLevels\.)((?:[A-Za-z_]\w*(?:\([^()\"]*\))?)(?:\.[A-Za-z_]\w*(?:\([^()\"]*\))?)*)\.noBlockCollision\("),
     r"PortLevels.noBlockCollision(\1, "),
    # AbstractClientPlayer/PlayerInfo#getSkin() (1.20.2+) -> PlayerSkin.of(x); simple receivers only.
    (re.compile(r"(?<![\w.)\]])((?:[A-Za-z_]\w*(?:\([^()\"]*\))?)(?:\.[A-Za-z_]\w*(?:\([^()\"]*\))?)*)\.getSkin\(\)"),
     SHIM_PREFIX + r"net.minecraft.client.resources.PlayerSkin.of(\1)"),
    # Minecraft#getTimer() returns a DeltaTracker in 1.21; the shim's live client view replaces it.
    (re.compile(r"(?<![\w.])(?:(?:net\.minecraft\.client\.)?Minecraft\.getInstance\(\)|mc|minecraft|this\.minecraft)\.getTimer\(\)"),
     SHIM_PREFIX + "net.minecraft.client.DeltaTracker.client()"),
    # ItemStack#getTooltipLines(Item.TooltipContext, player, flag) is getTooltipLines(player, flag) in 1.20.1.
    (re.compile(r"\bgetTooltipLines\(\s*(?:[\w.]*\.)?TooltipContext\.(?:EMPTY|of\([^()]*(?:\([^()]*\))?[^()]*\))\s*,\s*"), "getTooltipLines("),
    # Registry entries renamed in 1.20.3 (short_grass) / 1.20.5 (turtle_scute).
    (re.compile(r"\b(Blocks|Items)\.SHORT_GRASS\b"), r"\1.GRASS"),
    (re.compile(r"\bItems\.TURTLE_SCUTE\b"), "Items.SCUTE"),
    # Player interaction range API (1.20.5+): Forge 1.20.1 reach attributes.
    (re.compile(r"(?<![\w.)\]])(?!PortEntities\.)((?:[A-Za-z_]\w*(?:\([^()\"]*\))?)(?:\.[A-Za-z_]\w*(?:\([^()\"]*\))?)*)\.canInteractWith(Block|Entity)\("), r"PortEntities.canInteractWith\2(\1, "),
    (re.compile(r"\.blockInteractionRange\(\)"), ".getBlockReach()"),
    (re.compile(r"\.entityInteractionRange\(\)"), ".getEntityReach()"),
    # ItemEntity#setThrower(Entity) (1.20.5+) is setThrower(UUID) in 1.20.1; simple non-null receivers only.
    (re.compile(r"\.setThrower\(\s*(?!null\b)([A-Za-z_]\w*(?:\([^()]*\))?)\s*\)"), r".setThrower(\1.getUUID())"),
    # Player.DEFAULT_VEHICLE_ATTACHMENT (1.20.5+ entity attachments).
    (re.compile(r"(?<![\w.])(?:[\w.]*\.)?Player\.DEFAULT_VEHICLE_ATTACHMENT\b"), "PortEntities.PLAYER_VEHICLE_ATTACHMENT"),
]


# Item data components: 1.20.1 ItemStack has no get/set/has/remove/update/getOrDefault, so calls whose
# receiver is a simple expression (identifier / field / call chain with flat arguments) become static
# PortItemData calls. Complex receivers are converted by hand. Idempotent: the rewritten form no longer
# has a DataComponents argument directly after the opening parenthesis.
ITEM_DATA_CLASS = SHIM_PREFIX + "PortItemData"
ITEM_STACKS_CLASS = SHIM_PREFIX + "PortItemStacks"
_RECEIVER = r"(?<![\w.)\]])((?:[A-Za-z_]\w*(?:\([^()\"]*\))?)(?:\.[A-Za-z_]\w*(?:\([^()\"]*\))?)*)"
_COMPONENTS = r"(?:com\.stardew\.craft\.port\.net\.minecraft\.core\.component\.)?DataComponents\."
ITEM_DATA_RULES = [
    (re.compile(_RECEIVER + r"\.(get|set|has|remove|getOrDefault|update)\(\s*(?=" + _COMPONENTS + ")"),
     r"PortItemData.\2(\1, "),
    (re.compile(r"(?<![\w.])((?:net\.minecraft\.world\.item\.)?ItemStack)(\.|::)isSameItemSameComponents\b"),
     r"\1\2isSameItemSameTags"),
    (re.compile(r"(?<![\w.])(?:net\.minecraft\.world\.item\.)?ItemStack\.(parseOptional|parse)\("),
     r"PortItemStacks.\1("),
    (re.compile(r"(?!(?:com\.stardew\.craft\.port\.)?PortItemStacks\.)" + _RECEIVER + r"\.saveOptional\("),
     r"PortItemStacks.saveOptional(\1, "),
]
_PACKAGE_LINE = re.compile(r"^package [\w.]+;\n", re.M)


def _ensure_import(text: str, simple: str, fqn: str) -> str:
    if not re.search(r"(?<![\w.])" + simple + r"\.", text) or re.search(r"^import " + re.escape(fqn) + ";", text, re.M):
        return text
    if re.search(r"^package " + re.escape(fqn.rsplit(".", 1)[0]) + ";", text, re.M):
        return text
    match = _PACKAGE_LINE.search(text)
    if not match:
        return text
    return text[:match.end()] + "\nimport " + fqn + ";" + text[match.end():]


def rewrite_item_data(text: str) -> str:
    updated = text
    for pattern, repl in ITEM_DATA_RULES:
        updated = pattern.sub(repl, updated)
    if updated == text:
        return text
    updated = _ensure_import(updated, "PortItemData", ITEM_DATA_CLASS)
    return _ensure_import(updated, "PortItemStacks", ITEM_STACKS_CLASS)


def rewrite(text: str, rules) -> str:
    for pattern, repl in rules:
        text = pattern.sub(repl, text)
    # Identifier renames only outside of package paths (imports were already rewritten).
    for pattern, repl in IDENT_RULES:
        text = pattern.sub(repl, text)
    for pattern, repl in CODE_RULES:
        text = pattern.sub(repl, text)
    for simple in VANILLA_HELPERS:
        text = _ensure_import(text, simple, SHIM_PREFIX + simple)
    return rewrite_item_data(text)


def main(argv: list[str]) -> int:
    targets = [Path(a) for a in argv] or [ROOT / "src/main/java"]
    rules = build_rules()
    changed = 0
    for target in targets:
        files = [target] if target.is_file() else sorted(target.rglob("*.java"))
        for path in files:
            original = path.read_text(encoding="utf-8")
            updated = rewrite(original, rules)
            if updated != original:
                path.write_text(updated, encoding="utf-8")
                changed += 1
    print(f"rewrote {changed} files")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

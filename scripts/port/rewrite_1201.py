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
    "net.neoforged.fml.common.EventBusSubscriber": "net.minecraftforge.fml.common.Mod.EventBusSubscriber",
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
]
for key, field in VANILLA_CODECS.items():
    cls, member = key.split(".")
    CODE_RULES.append((re.compile(r"(?<![\w])(?:[a-z_][\w]*\.)*" + cls + r"\." + member + r"\b"), CODECS_CLASS + "." + field))


def rewrite(text: str, rules) -> str:
    for pattern, repl in rules:
        text = pattern.sub(repl, text)
    # Identifier renames only outside of package paths (imports were already rewritten).
    for pattern, repl in IDENT_RULES:
        text = pattern.sub(repl, text)
    for pattern, repl in CODE_RULES:
        text = pattern.sub(repl, text)
    return text


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

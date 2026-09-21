#!/usr/bin/env python3
"""Strict resource/registration gate for the Forge decoration runtime closure."""
from __future__ import annotations
import hashlib
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FORGE = ROOT / "src/forge-bootstrap"
FORGE_COMMON = ROOT / "src/forge-common"
SRC = ROOT / "src/main"

def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def compare_adapted(errors: list[str], relative: str, replacements: tuple[tuple[str, str], ...]) -> None:
    """Compare a Forge-common class with only its documented API substitutions."""
    source = (SRC / "java/com/stardew/craft" / relative).read_text(encoding="utf-8")
    forge = (FORGE_COMMON / "java/com/stardew/craft" / relative).read_text(encoding="utf-8")
    for old, new in replacements:
        source = source.replace(old, new)
    if source != forge:
        errors.append(f"Forge decoration class drifted outside allowed API substitutions: {relative}")
    if "net.neoforged" in forge:
        errors.append(f"Forge decoration class retained NeoForge runtime imports: {relative}")


def compare_resource(errors: list[str], source_relative: str, forge_relative: str | None = None) -> None:
    source = SRC / "resources" / source_relative
    target = FORGE / "resources" / (forge_relative or source_relative)
    if not target.is_file():
        errors.append(f"missing parity resource: {target.relative_to(ROOT)}")
    elif sha(source) != sha(target):
        errors.append(f"resource hash mismatch: {source_relative} -> {target.relative_to(ROOT)}")

def main() -> int:
    errors: list[str] = []
    registry = (FORGE / "java/com/stardew/craft/forge/registry/ForgeWallpaperRegistry.java").read_text()
    required = [
        "WALLPAPER_BLOCK", "FLOORING_BLOCK", "for (int i = 0; i < 112; i++)",
        "for (int i = 0; i < 26; i++)", "wallpaper_" + '" + i',
        "WALLPAPER_ITEM", "FLOORING_ITEM"
    ]
    for text in required:
        if text not in registry:
            errors.append(f"registry missing contract fragment: {text}")
    if 'new WallpaperBlockItem(BLOCK_STYLES.get("0").get()' not in registry:
        errors.append("legacy wallpaper item must place stable wallpaper style 0")

    compare_adapted(errors, "block/utility/WallpaperBlock.java", (
        ("import net.minecraft.world.level.block.Block;", "import net.minecraft.world.level.block.Block;\nimport net.minecraft.world.level.block.state.BlockBehaviour;"),
        ("WallpaperBlock(Properties properties", "WallpaperBlock(BlockBehaviour.Properties properties"),
    ))
    compare_adapted(errors, "block/utility/LegacyWallpaperBlock.java", (
        ("import com.stardew.craft.item.ModItems;", "import com.stardew.craft.forge.registry.ForgeItems;"),
        ("ModItems.WALLPAPER_BLOCK", "ForgeItems.WALLPAPER_BLOCK"),
        ("protected void onPlace", "public void onPlace"),
        ("protected void tick", "public void tick"),
    ))
    compare_adapted(errors, "block/utility/FlooringBlock.java", ())
    compare_adapted(errors, "blockentity/DecorBlockEntity.java", (
        ("import net.minecraft.core.HolderLookup;\n", ""),
        ("protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider)", "protected void saveAdditional(CompoundTag tag)"),
        ("super.saveAdditional(tag, provider)", "super.saveAdditional(tag)"),
        ("protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider)", "public void load(CompoundTag tag)"),
        ("super.loadAdditional(tag, provider)", "super.load(tag)"),
        ("getUpdateTag(HolderLookup.Provider provider)", "getUpdateTag()"),
        ("saveWithoutMetadata(provider)", "saveWithoutMetadata()"),
        ("handleUpdateTag(CompoundTag tag, HolderLookup.Provider provider)", "handleUpdateTag(CompoundTag tag)"),
        ("loadAdditional(tag, provider)", "load(tag)"),
    ))
    compare_adapted(errors, "deco/LegacyWallpaperMigration.java", (
        ("import com.stardew.craft.StardewCraft;", "import com.stardew.craft.forge.ForgeBootstrap;"),
        ("import net.neoforged.bus.api.SubscribeEvent;", "import net.minecraftforge.eventbus.api.SubscribeEvent;"),
        ("import net.neoforged.fml.common.EventBusSubscriber;", "import net.minecraftforge.fml.common.Mod;"),
        ("import net.neoforged.neoforge.event.level.ChunkEvent;", "import net.minecraftforge.event.level.ChunkEvent;"),
        ("@EventBusSubscriber(modid = StardewCraft.MODID)", "@Mod.EventBusSubscriber(modid = ForgeBootstrap.MOD_ID)"),
    ))

    forge_assets = FORGE / "resources/assets/stardewcraft"
    source_assets = SRC / "resources/assets/stardewcraft"
    wallpaper_ids = [*(f"wallpaper_{i}" for i in range(112)), *(f"wallpaper_morewalls_{i}" for i in range(26))]
    for ident in wallpaper_ids:
        visual = int(ident.removeprefix("wallpaper_morewalls_")) + 112 if ident.startswith("wallpaper_morewalls_") else int(ident.removeprefix("wallpaper_"))
        item_rel = f"models/item/{ident}.json"
        # style 0 intentionally uses the legacy wallpaper_block item ID, whose
        # registered block item places the stable style-0 block.
        for rel in (f"blockstates/{ident}.json", item_rel):
            if ident == "wallpaper_0" and rel == item_rel:
                continue
            if not (forge_assets / rel).is_file(): errors.append(f"missing {rel}")
        for seg in range(3):
            for root in ("models", "textures"):
                rel = f"{root}/block/deco/wallpaper/style_{visual}_segment_{seg}.json" if root == "models" else f"{root}/block/deco/wallpaper/style_{visual}_segment_{seg}.png"
                if not (forge_assets / rel).is_file(): errors.append(f"missing {rel}")
    for style in range(65):
        for part in range(4):
            for root, ext in (("models", "json"), ("textures", "png")):
                rel = f"{root}/block/deco/flooring/style_{style}_part_{part}.{ext}"
                if not (forge_assets / rel).is_file(): errors.append(f"missing {rel}")
    for rel in ("blockstates/wallpaper_block.json", "models/block/wallpaper_block.json", "models/item/wallpaper_block.json", "blockstates/flooring_block.json", "models/item/flooring_block.json"):
        if not (forge_assets / rel).is_file(): errors.append(f"missing {rel}")
    # Every decoration asset copied from the source must remain byte-identical.
    for rel_root in (
        "models/block/deco/wallpaper",
        "textures/block/deco/wallpaper",
        "models/block/deco/flooring",
        "textures/block/deco/flooring",
        "textures/item/deco/wallpaper_icons",
        "textures/item/deco/flooring_icons",
        "textures/deco",
    ):
        source_root = source_assets / rel_root
        for source in source_root.rglob("*"):
            if source.is_file():
                compare_resource(errors, f"assets/stardewcraft/{rel_root}/{source.relative_to(source_root).as_posix()}")

    for ident in (*wallpaper_ids, "wallpaper_block", "flooring_block"):
        relatives = [f"blockstates/{ident}.json"]
        if ident != "wallpaper_0":
            relatives.append(f"models/item/{ident}.json")
        for rel in relatives:
            compare_resource(errors, f"assets/stardewcraft/{rel}")
    compare_resource(errors, "assets/stardewcraft/models/block/wallpaper_block.json")

    for source_rel, forge_rel in (
        ("data/stardewcraft/tags/block/stable_wallpapers.json", "data/stardewcraft/tags/blocks/stable_wallpapers.json"),
        ("data/stardewcraft/tags/item/stable_wallpapers.json", "data/stardewcraft/tags/items/stable_wallpapers.json"),
        ("data/stardewcraft/deco/additional_wallpaper_flooring.json", "data/stardewcraft/deco/additional_wallpaper_flooring.json"),
    ):
        compare_resource(errors, source_rel, forge_rel)

    axe = (FORGE / "resources/data/minecraft/tags/blocks/mineable/axe.json").read_text(encoding="utf-8")
    hidden = (FORGE / "resources/data/stardewcraft/tags/items/hidden.json").read_text(encoding="utf-8")
    for fragment, text, label in (
        ('"stardewcraft:wallpaper_block"', axe, "Forge axe tag"),
        ('"#stardewcraft:stable_wallpapers"', axe, "Forge axe tag"),
        ('"stardewcraft:flooring_block"', axe, "Forge axe tag"),
        ('"#stardewcraft:stable_wallpapers"', hidden, "Forge hidden item tag"),
    ):
        if fragment not in text:
            errors.append(f"{label} is missing {fragment}")
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print("Forge decoration runtime parity: 138 wallpaper styles, 65 flooring styles x 4 parts, legacy block assets and source SHA-256 checks passed.")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())

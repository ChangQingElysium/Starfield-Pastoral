#!/usr/bin/env python3
"""Verify the Forge pure-Java decoration data slice against NeoForge source."""

from __future__ import annotations

import re
import sys
import hashlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "src/main/java/com/stardew/craft/deco"
FORGE = ROOT / "src/forge-common/java/com/stardew/craft/deco"
PURE_FILES = ("DecorationType.java", "WallpaperStyles.java")


def normalize_registry(text: str) -> str:
    """Normalize the two explicitly allowed Forge API substitutions."""
    return (
        text.replace("import com.stardew.craft.StardewCraft;", "import com.stardew.craft.forge.ForgeBootstrap;")
        .replace("ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID,", "new ResourceLocation(ForgeBootstrap.MOD_ID,")
    )


def main() -> int:
    errors: list[str] = []
    for name in PURE_FILES:
        source_path = SOURCE / name
        forge_path = FORGE / name
        if not forge_path.is_file():
            errors.append(f"missing Forge pure data class: {forge_path.relative_to(ROOT)}")
            continue
        source_text = source_path.read_text(encoding="utf-8")
        forge_text = forge_path.read_text(encoding="utf-8")
        if source_text != forge_text:
            errors.append(f"Forge pure data class drifted from source: {name}")
        if re.search(r"net\.minecraft|net\.neoforged|net\.minecraftforge", forge_text):
            errors.append(f"Forge pure data class has platform runtime dependency: {name}")

    wallpaper = (FORGE / "WallpaperStyles.java").read_text(encoding="utf-8") if (FORGE / "WallpaperStyles.java").is_file() else ""
    if wallpaper:
        if "new ArrayList<>(138)" not in wallpaper or "i < 112" not in wallpaper or "i < 26" not in wallpaper:
            errors.append("WallpaperStyles cardinality/order contract drifted")
        expected = [str(i) for i in range(112)] + [f"MoreWalls:{i}" for i in range(26)]
        for index, style_id in enumerate(expected):
            if style_id.startswith("MoreWalls:"):
                path = f"wallpaper_morewalls_{style_id.split(':', 1)[1]}"
            else:
                path = f"wallpaper_{style_id}"
            if index >= 112 and "wallpaper_morewalls_" not in wallpaper:
                errors.append("MoreWalls registry path contract missing")
                break
            if index == 0 and 'return "wallpaper_" + styleId;' not in wallpaper:
                errors.append("base wallpaper registry path contract missing")
                break

    style_source = (SOURCE / "DecorationStyle.java").read_text(encoding="utf-8")
    style_forge_path = FORGE / "DecorationStyle.java"
    if not style_forge_path.is_file():
        errors.append("missing Forge DecorationStyle.java")
    elif style_forge_path.read_text(encoding="utf-8") != style_source:
        errors.append("Forge DecorationStyle.java drifted from source")

    registry_source = (SOURCE / "DecorationStyleRegistry.java").read_text(encoding="utf-8")
    registry_forge_path = FORGE / "DecorationStyleRegistry.java"
    if not registry_forge_path.is_file():
        errors.append("missing Forge DecorationStyleRegistry.java")
    else:
        registry_forge = registry_forge_path.read_text(encoding="utf-8")
        if normalize_registry(registry_source) != normalize_registry(registry_forge):
            errors.append("Forge DecorationStyleRegistry.java drifted outside the allowed ResourceLocation/MOD_ID substitutions")
        for bound in ("112", "56", "26", "9"):
            if f"i < {bound}" not in registry_forge:
                errors.append(f"missing DecorationStyleRegistry cardinality bound: {bound}")
        if "ForgeBootstrap.MOD_ID" not in registry_forge or "new ResourceLocation" not in registry_forge:
            errors.append("Forge DecorationStyleRegistry missing Forge ResourceLocation adaptation")
        for texture_name in ("walls_and_floors.png", "wallpapers_2.png", "floors_2.png"):
            source_texture = ROOT / "src/main/resources/assets/stardewcraft/textures/deco" / texture_name
            forge_texture = ROOT / "src/forge-bootstrap/resources/assets/stardewcraft/textures/deco" / texture_name
            if not forge_texture.is_file():
                errors.append(f"missing Forge decoration texture: {forge_texture.relative_to(ROOT)}")
            elif hashlib.sha256(source_texture.read_bytes()).digest() != hashlib.sha256(forge_texture.read_bytes()).digest():
                errors.append(f"Forge decoration texture drifted: {texture_name}")

    if errors:
        print("\n".join(errors), file=sys.stderr)
        print(f"Forge decoration data parity failed with {len(errors)} error(s).", file=sys.stderr)
        return 1
    print("Decoration data parity passed: 2 exact pure classes plus DecorationStyle/DecorationStyleRegistry with only Forge ResourceLocation/MOD_ID substitutions; 112/56/26/9 style groups verified.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

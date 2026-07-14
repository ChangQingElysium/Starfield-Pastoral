#!/usr/bin/env python3
"""Static relationship checks for the StardewCraft 0.5 acceptance examples."""

from __future__ import annotations

import json
from pathlib import Path


PACK = Path(__file__).resolve().parent
ADDON_SOURCE = PACK.parent / "stardewcraft-addon" / "src" / "main" / "java" / "com" / "example" \
    / "stardewaddon" / "ExampleStardewAddon.java"
NAMESPACE = "example_stardew_addon"


def load(relative: str):
    with (PACK / relative).open(encoding="utf-8") as handle:
        return json.load(handle)


def require(value: bool, message: str) -> None:
    if not value:
        raise AssertionError(message)


def main() -> None:
    json_files = sorted(PACK.rglob("*.json"))
    document_files = json_files + sorted(PACK.rglob("pack.mcmeta"))
    for path in document_files:
        with path.open(encoding="utf-8") as handle:
            json.load(handle)

    recipe_id = f"{NAMESPACE}:apple_crate"
    shop = load(f"data/{NAMESPACE}/shops/apple_stand.json")
    shop_items = [entry["item"] for entry in shop["entries"]]
    require(f"recipe:{recipe_id}" in shop_items, "shop must sell the fully namespaced recipe")

    unlock = load(f"data/{NAMESPACE}/player/unlock_sources/apple_club.json")
    require(recipe_id in unlock["recipes"], "mail unlock source must retain the recipe namespace")
    mail = load(f"data/{NAMESPACE}/mail/apple_club.json")
    actions = mail.get("on_read", [])
    require(any(action.get("type") == "stardewcraft:apply_unlock_source"
                and action.get("data", {}).get("source") == f"{NAMESPACE}:apple_club"
                for action in actions), "mail must apply the namespaced unlock source")

    enabled = load(f"data/{NAMESPACE}/festivals/conditional_active_enabled.json")
    disabled = load(f"data/{NAMESPACE}/festivals/conditional_active_disabled.json")
    require(enabled["type"] == disabled["type"] == "active", "festival probes must be active")
    require(enabled["available_when"][0]["data"]["value"] is True,
            "enabled festival must have a true world condition")
    require(disabled["available_when"][0]["data"]["value"] is False,
            "disabled festival must have a false world condition")

    equipment = load("data/stardewcraft/data_maps/item/stardew_equipment_data.json")
    sword = equipment["values"]["minecraft:diamond_sword"]
    require(sword["slot"] == "stardewcraft:weapon", "equipment probe must use the public weapon slot")
    require(sword["weapon"]["primary_skill"] == f"{NAMESPACE}:apple_dash",
            "equipment probe must target the addon skill handler")

    geode = load(f"data/{NAMESPACE}/geode/drops/apple_crystal.json")
    require(bool(geode["inputs"]) and bool(geode["entries"]), "geode JEI probe must have input and output")

    source = ADDON_SOURCE.read_text(encoding="utf-8")
    for marker in (
        "registerCropProvider", "registerTreeProvider", "registerAnimalProvider",
        "registerBuildingProvider", "StardewEquipmentDataApi.registerProvider",
        "StardewWeaponSkillHandlers.register(id(\"apple_dash\")",
    ):
        require(marker in source, f"missing addon acceptance marker: {marker}")

    reload_root = PACK / "acceptance" / "reload-overlay"
    require((reload_root / "pack.mcmeta").is_file(), "reload overlay must be an installable data pack")
    require((reload_root / f"data/{NAMESPACE}/mail/reload_probe.json").is_file(),
            "reload overlay must add a mail-index probe")
    require((reload_root / f"data/{NAMESPACE}/shops/reload_probe.json").is_file(),
            "reload overlay must add a shop JEI probe")
    require((reload_root / f"data/{NAMESPACE}/geode/drops/reload_probe.json").is_file(),
            "reload overlay must add a geode JEI probe")

    print(f"validated {len(document_files)} JSON documents and all cross-file acceptance relationships")


if __name__ == "__main__":
    main()

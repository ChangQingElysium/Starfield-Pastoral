#!/usr/bin/env python3
"""Static relationship checks for the StardewCraft 0.5 acceptance examples."""

from __future__ import annotations

import json
from pathlib import Path


PACK = Path(__file__).resolve().parent
ADDON_SOURCE = PACK.parent / "stardewcraft-addon" / "src" / "main" / "java" / "com" / "example" \
    / "stardewaddon" / "ExampleStardewAddon.java"
ADDON_RESOURCES = (
    PACK.parent / "stardewcraft-addon" / "src" / "main" / "resources"
)
NAMESPACE = "example_stardew_addon"


def load(relative: str):
    with (PACK / relative).open(encoding="utf-8") as handle:
        return json.load(handle)


def require(value: bool, message: str) -> None:
    if not value:
        raise AssertionError(message)


def main() -> None:
    json_files = sorted(PACK.rglob("*.json"))
    addon_json_files = sorted(ADDON_RESOURCES.rglob("*.json"))
    document_files = (
        json_files
        + sorted(PACK.rglob("pack.mcmeta"))
        + addon_json_files
    )
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

    food_effects = load("data/stardewcraft/data_maps/item/stardew_food_effects.json")
    pumpkin_effect = food_effects["values"]["minecraft:pumpkin_pie"]["effects"][
        f"{NAMESPACE}:harvest_focus"
    ]
    carrot_effect = food_effects["values"]["minecraft:golden_carrot"]["effects"][
        f"{NAMESPACE}:golden_luck"
    ]
    require(
        pumpkin_effect["effect"] == "stardewcraft:farmer_blessing"
        and carrot_effect["effect"] == "stardewcraft:spirit_blessing",
        "vanilla food probes must use registered StardewCraft effects",
    )
    require(
        pumpkin_effect["duration_ticks"] > 0
        and carrot_effect["duration_ticks"] > 0,
        "food effects must have positive durations",
    )

    goose = load(
        f"data/{NAMESPACE}/stardewcraft/farm_animals/goose.json"
    )
    require(
        goose["animal_type_id"] == f"{NAMESPACE}:goose",
        "farm-animal example must keep its namespace",
    )
    require(
        goose["entity_type"] == "stardewcraft:duck"
        and goose["shop_texture"].endswith(
            "/animal_purchase/duck.png"
        ),
        "pure-data goose must reuse an available entity and client texture",
    )

    geode = load(f"data/{NAMESPACE}/geode/drops/apple_crystal.json")
    require(bool(geode["inputs"]) and bool(geode["entries"]), "geode JEI probe must have input and output")

    map_interaction = load(
        f"data/{NAMESPACE}/map_interactions/apple_shed_notice.json"
    )
    require(
        map_interaction["trigger"]["location"]
        == f"{NAMESPACE}:apple_shed"
        and map_interaction["branches"][0]["messages"][0]["literal"],
        "standalone datapack must include a self-contained map message",
    )

    source = ADDON_SOURCE.read_text(encoding="utf-8")
    for marker in (
        "registerCropProvider", "registerTreeProvider", "registerAnimalProvider",
        "registerBuildingProvider", "StardewEquipmentDataApi.registerProvider",
        "StardewWeaponSkillHandlers.register(id(\"apple_dash\")",
        "StardewTruffleFoundHandlers.register(",
        "StardewMapInteractionActions.register(",
        "StardewMapInteractions.register(",
    ):
        require(marker in source, f"missing addon acceptance marker: {marker}")

    monster_table = load(
        f"data/{NAMESPACE}/mine_monster_spawns/orchard_floor.json"
    )
    require(
        monster_table["themes"] == [f"{NAMESPACE}:apple_floor"],
        "mine monster table must target the namespaced theme",
    )
    require(
        monster_table["entries"][0]["profile"]
        == "stardewcraft:rock_crab",
        "standalone datapack must reference an available core profile",
    )
    addon_monster_table = load_addon(
        f"data/{NAMESPACE}/mine_monster_spawns/orchard_silverfish.json"
    )
    require(
        addon_monster_table["entries"][0]["profile"]
        == f"{NAMESPACE}:orchard_silverfish",
        "addon-owned table must reference the addon profile",
    )
    require(
        "StardewMineMonsterProfiles.register(" in source
        and "silverfish_floor_13" not in source,
        "the addon must register profile identity while datapack owns placement",
    )
    taste_patch = load(
        f"data/{NAMESPACE}/npc/taste_patches/abigail_apples.json"
    )
    require(
        taste_patch["npc"] == "stardewcraft:abigail"
        and taste_patch["add"]["loved"] == ["minecraft:apple"],
        "NPC taste patch must use exact target and item IDs",
    )
    npc_id = f"{NAMESPACE}:archivist"
    npc_dialogue = load_addon(
        f"data/{NAMESPACE}/npc/dialogue/archivist.json"
    )
    npc_schedule = load_addon(
        f"data/{NAMESPACE}/npc/schedules/archivist.json"
    )
    npc_tastes = load_addon(
        f"data/{NAMESPACE}/npc/tastes/archivist.json"
    )
    npc_locations = load_addon(
        f"data/{NAMESPACE}/npc/location_mappings/orchard.json"
    )
    orchard_anchor = load_addon(
        f"data/{NAMESPACE}/anchors/orchard_stage.json"
    )
    orchard_festival = load_addon(
        f"data/{NAMESPACE}/festivals/orchard_celebration.json"
    )
    orchard_shop_binding = load_addon(
        f"data/{NAMESPACE}/shop_bindings/orchard_archivist.json"
    )
    spring_routes = list(npc_schedule["spring"].values())
    require(
        npc_dialogue["npc_id"]
        == npc_schedule["npc_id"]
        == npc_tastes["npc_id"]
        == "archivist",
        "NPC profile components must use one logical ID",
    )
    require(
        f"{NAMESPACE}:orchard" in npc_locations["locations"]
        and all(route.startswith(f"{NAMESPACE}:orchard ") for route in spring_routes)
        and all(f"@{NAMESPACE}:orchard_stage" in route for route in spring_routes),
        "NPC schedule must consume the shared orchard location and world anchor",
    )
    require(
        orchard_anchor["location"] == f"{NAMESPACE}:orchard"
        and orchard_festival["world"]["location"] == f"{NAMESPACE}:orchard",
        "NPC and festival must consume the same location definition",
    )
    require(
        orchard_shop_binding["npc"] == "archivist",
        "NPC shop binding must use the profile path normalized by its owning namespace",
    )
    require(
        "StardewNpcProfiles.register(" in source
        and "StardewNpcEntities.register(" in source
        and "StardewNpcGifts.register" in source,
        "NPC sample must combine profile, entity lifecycle and gift APIs",
    )

    reload_root = PACK / "acceptance" / "reload-overlay"
    require((reload_root / "pack.mcmeta").is_file(), "reload overlay must be an installable data pack")
    require((reload_root / f"data/{NAMESPACE}/mail/reload_probe.json").is_file(),
            "reload overlay must add a mail-index probe")
    require((reload_root / f"data/{NAMESPACE}/shops/reload_probe.json").is_file(),
            "reload overlay must add a shop JEI probe")
    require((reload_root / f"data/{NAMESPACE}/geode/drops/reload_probe.json").is_file(),
            "reload overlay must add a geode JEI probe")

    print(f"validated {len(document_files)} JSON documents and all cross-file acceptance relationships")


def load_addon(relative: str):
    with (ADDON_RESOURCES / relative).open(encoding="utf-8") as handle:
        return json.load(handle)


if __name__ == "__main__":
    main()

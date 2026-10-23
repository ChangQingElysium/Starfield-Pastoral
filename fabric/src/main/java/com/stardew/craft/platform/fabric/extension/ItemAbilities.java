package com.stardew.craft.platform.fabric.extension;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Stable action names and default memberships used by the upstream 1.21.1 tools. */
public final class ItemAbilities {
    public static final ItemAbility AXE_DIG = ItemAbility.get("axe_dig");
    public static final ItemAbility AXE_STRIP = ItemAbility.get("axe_strip");
    public static final ItemAbility AXE_SCRAPE = ItemAbility.get("axe_scrape");
    public static final ItemAbility AXE_WAX_OFF = ItemAbility.get("axe_wax_off");
    public static final ItemAbility PICKAXE_DIG = ItemAbility.get("pickaxe_dig");
    public static final ItemAbility SHOVEL_DIG = ItemAbility.get("shovel_dig");
    public static final ItemAbility SHOVEL_FLATTEN = ItemAbility.get("shovel_flatten");
    public static final ItemAbility SHOVEL_DOUSE = ItemAbility.get("shovel_douse");
    public static final ItemAbility HOE_DIG = ItemAbility.get("hoe_dig");
    public static final ItemAbility HOE_TILL = ItemAbility.get("till");
    public static final ItemAbility SWORD_DIG = ItemAbility.get("sword_dig");
    public static final ItemAbility SWORD_SWEEP = ItemAbility.get("sword_sweep");
    public static final ItemAbility SHEARS_DIG = ItemAbility.get("shears_dig");
    public static final ItemAbility SHEARS_HARVEST = ItemAbility.get("shears_harvest");
    public static final ItemAbility SHEARS_REMOVE_ARMOR = ItemAbility.get("shears_remove_armor");
    public static final ItemAbility SHEARS_CARVE = ItemAbility.get("shears_carve");
    public static final ItemAbility SHEARS_DISARM = ItemAbility.get("shears_disarm");
    public static final ItemAbility SHEARS_TRIM = ItemAbility.get("shears_trim");
    public static final ItemAbility SHIELD_BLOCK = ItemAbility.get("shield_block");
    public static final ItemAbility FISHING_ROD_CAST = ItemAbility.get("fishing_rod_cast");
    public static final ItemAbility TRIDENT_THROW = ItemAbility.get("trident_throw");
    public static final ItemAbility BRUSH_BRUSH = ItemAbility.get("brush_brush");
    public static final ItemAbility FIRESTARTER_LIGHT = ItemAbility.get("firestarter_light");
    public static final ItemAbility SPYGLASS_SCOPE = ItemAbility.get("spyglass_scope");

    public static final Set<ItemAbility> DEFAULT_AXE_ACTIONS = actions(AXE_DIG, AXE_STRIP, AXE_SCRAPE, AXE_WAX_OFF);
    public static final Set<ItemAbility> DEFAULT_PICKAXE_ACTIONS = actions(PICKAXE_DIG);
    public static final Set<ItemAbility> DEFAULT_SHOVEL_ACTIONS = actions(SHOVEL_DIG, SHOVEL_FLATTEN, SHOVEL_DOUSE);
    public static final Set<ItemAbility> DEFAULT_HOE_ACTIONS = actions(HOE_DIG, HOE_TILL);
    public static final Set<ItemAbility> DEFAULT_SWORD_ACTIONS = actions(SWORD_DIG, SWORD_SWEEP);
    public static final Set<ItemAbility> DEFAULT_SHEARS_ACTIONS = actions(SHEARS_DIG, SHEARS_HARVEST,
            SHEARS_REMOVE_ARMOR, SHEARS_CARVE, SHEARS_DISARM, SHEARS_TRIM);
    public static final Set<ItemAbility> DEFAULT_SHIELD_ACTIONS = actions(SHIELD_BLOCK);
    public static final Set<ItemAbility> DEFAULT_FISHING_ROD_ACTIONS = actions(FISHING_ROD_CAST);
    public static final Set<ItemAbility> DEFAULT_TRIDENT_ACTIONS = actions(TRIDENT_THROW);
    public static final Set<ItemAbility> DEFAULT_BRUSH_ACTIONS = actions(BRUSH_BRUSH);
    public static final Set<ItemAbility> DEFAULT_FLINT_ACTIONS = actions(FIRESTARTER_LIGHT);
    public static final Set<ItemAbility> DEFAULT_FIRECHARGE_ACTIONS = actions(FIRESTARTER_LIGHT);
    public static final Set<ItemAbility> DEFAULT_SPYGLASS_ACTIONS = actions(SPYGLASS_SCOPE);

    private ItemAbilities() {}

    private static Set<ItemAbility> actions(ItemAbility... abilities) {
        Set<ItemAbility> actions = Collections.newSetFromMap(new IdentityHashMap<>());
        Collections.addAll(actions, abilities);
        return actions;
    }
}

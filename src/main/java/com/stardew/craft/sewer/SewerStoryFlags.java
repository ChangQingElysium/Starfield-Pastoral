package com.stardew.craft.sewer;

public final class SewerStoryFlags {
    public static final String HAS_RUSTY_KEY = "HasRustyKey";
    public static final String OPENED_SEWER = "OpenedSewer";
    public static final String KROBUS_UNSEAL = "krobusUnseal";
    public static final String SEWER_STARDROP_PURCHASED = "CF_Sewer";
    /** SDV {@code Wand.actionWhenPurchased}: {@code mailReceived.Add("ReturnScepter")}. */
    public static final String RETURN_SCEPTER_PURCHASED = "ReturnScepter";
    /** Flag written by earlier StardewCraft versions; still counts as purchased. */
    public static final String LEGACY_RETURN_SCEPTER_PURCHASED = "BoughtReturnScepter";

    public static final String RUSTY_KEY_EVENT_READY = "295672";
    public static final String RUSTY_KEY_SPECIAL_ITEM = "stardewcraft:rusty_key";
    public static final String RETURN_SCEPTER_SPECIAL_ITEM = "stardewcraft:warp_wand";

    public static boolean hasPurchasedReturnScepter(com.stardew.craft.player.PlayerStardewData data) {
        return data.hasMailFlag(RETURN_SCEPTER_PURCHASED) || data.hasMailFlag(LEGACY_RETURN_SCEPTER_PURCHASED);
    }

    private SewerStoryFlags() {
    }
}
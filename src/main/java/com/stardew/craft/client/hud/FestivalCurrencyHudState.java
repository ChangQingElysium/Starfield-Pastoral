package com.stardew.craft.client.hud;

/** Client-only state for the single festival-currency slot attached to the main SDV HUD. */
public final class FestivalCurrencyHudState {
    public static final byte NONE = 0;
    public static final byte FAIR_STAR_TOKEN = 1;
    public static final byte CALICO_EGG = 2;

    private static byte currencyType = NONE;

    private FestivalCurrencyHudState() {
    }

    public static byte currencyType() {
        return currencyType;
    }

    public static boolean active() {
        return currencyType != NONE;
    }

    public static void set(byte type) {
        currencyType = type == FAIR_STAR_TOKEN || type == CALICO_EGG ? type : NONE;
    }

    public static void reset() {
        currencyType = NONE;
    }
}

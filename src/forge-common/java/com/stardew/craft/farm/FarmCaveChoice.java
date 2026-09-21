package com.stardew.craft.farm;

import javax.annotation.Nullable;

/**
 * 农场洞穴类型选择（对齐 SDV Farmer.caveChoice）。
 * 0 = 未选择（NONE）
 * 1 = 水果洞（FRUIT_BATS）
 * 2 = 蘑菇洞（MUSHROOMS）
 */
public enum FarmCaveChoice {
    NONE(0, "none"),
    FRUIT_BATS(1, "fruit_bats"),
    MUSHROOMS(2, "mushrooms");

    private final int sdvId;
    private final String name;

    FarmCaveChoice(int sdvId, String name) {
        this.sdvId = sdvId;
        this.name = name;
    }

    public int getSdvId() { return sdvId; }
    public String getName() { return name; }

    public static FarmCaveChoice fromSdvId(int id) {
        for (FarmCaveChoice c : values()) {
            if (c.sdvId == id) return c;
        }
        return NONE;
    }

    @Nullable
    public static FarmCaveChoice fromName(String name) {
        if (name == null) return null;
        for (FarmCaveChoice c : values()) {
            if (c.name.equalsIgnoreCase(name)) return c;
        }
        return null;
    }
}

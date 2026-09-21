package com.stardew.craft.item.trinket;

import java.util.Locale;

public enum TrinketType {
    MAGIC_HAIR_DYE("magic_hair_dye", "MagicHairDye", 1, false, false),
    FROG_EGG("frog_egg", "FrogEgg", 6, true, true),
    MAGIC_QUIVER("magic_quiver", "MagicQuiver", 73, true, true),
    FAIRY_BOX("fairy_box", "FairyBox", 74, true, true),
    PARROT_EGG("parrot_egg", "ParrotEgg", 2, true, true),
    ICE_ROD("ice_rod", "IceRod", 78, true, true),
    IRIDIUM_SPUR("iridium_spur", "IridiumSpur", 76, true, true),
    BASILISK_PAW("basilisk_paw", "BasiliskPaw", 77, true, false);

    private final String registryName;
    private final String sdvId;
    private final int sheetIndex;
    private final boolean dropsNaturally;
    private final boolean canBeReforged;

    TrinketType(String registryName, String sdvId, int sheetIndex, boolean dropsNaturally, boolean canBeReforged) {
        this.registryName = registryName;
        this.sdvId = sdvId;
        this.sheetIndex = sheetIndex;
        this.dropsNaturally = dropsNaturally;
        this.canBeReforged = canBeReforged;
    }

    public String registryName() {
        return registryName;
    }

    public String sdvId() {
        return sdvId;
    }

    public int sheetIndex() {
        return sheetIndex;
    }

    public boolean dropsNaturally() {
        return dropsNaturally;
    }

    public boolean canBeReforged() {
        return canBeReforged;
    }

    public String itemTranslationKey() {
        return "item.stardewcraft." + registryName;
    }

    public String descriptionTranslationKey() {
        return "stardewcraft.trinket." + registryName + ".desc";
    }

    public static TrinketType fromRegistryName(String registryName) {
        if (registryName == null || registryName.isBlank()) {
            return null;
        }
        String normalized = registryName.toLowerCase(Locale.ROOT);
        for (TrinketType type : values()) {
            if (type.registryName.equals(normalized)) {
                return type;
            }
        }
        return null;
    }
}

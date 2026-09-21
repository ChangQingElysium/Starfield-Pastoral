package com.stardew.craft.economy.sell;

public final class SellContext {
    private final SellSource source;
    private final String itemTypeKey;
    private final boolean animalSale;

    private SellContext(SellSource source, String itemTypeKey, boolean animalSale) {
        this.source = source;
        this.itemTypeKey = itemTypeKey == null ? "" : itemTypeKey;
        this.animalSale = animalSale;
    }

    public static SellContext forItem(SellSource source, String itemTypeKey) {
        return new SellContext(source, itemTypeKey, false);
    }

    public static SellContext forAnimal(SellSource source) {
        return new SellContext(source, "", true);
    }

    public SellSource source() {
        return source;
    }

    public String itemTypeKey() {
        return itemTypeKey;
    }

    public boolean hasTypeKey(String typeKey) {
        return itemTypeKey.equals(typeKey);
    }

    public boolean isAnimalSale() {
        return animalSale;
    }
}

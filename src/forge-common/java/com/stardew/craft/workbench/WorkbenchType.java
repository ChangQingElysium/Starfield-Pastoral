package com.stardew.craft.workbench;

public enum WorkbenchType {
    WOOD(0, "wood",  "stardewcraft:wood_normal", "stardewcraft:wood_hard", 5),
    STONE(1, "stone", "stardewcraft:stone",       null,                     0),
    TEMPLATE(2, "template", "stardewcraft:wood_normal", null, 0);

    private final int id;
    private final String key;
    private final String inputItemId;
    private final String bonusItemId;
    private final int bonusMultiplier;

    WorkbenchType(int id, String key, String inputItemId, String bonusItemId, int bonusMultiplier) {
        this.id = id;
        this.key = key;
        this.inputItemId = inputItemId;
        this.bonusItemId = bonusItemId;
        this.bonusMultiplier = bonusMultiplier;
    }

    public int getId() { return id; }
    public String getKey() { return key; }
    public String getInputItemId() { return inputItemId; }
    public String getBonusItemId() { return bonusItemId; }
    public int getBonusMultiplier() { return bonusMultiplier; }
    public boolean hasBonus() { return bonusItemId != null; }

    public static WorkbenchType fromId(int id) {
        for (WorkbenchType t : values()) {
            if (t.id == id) return t;
        }
        return WOOD;
    }
}

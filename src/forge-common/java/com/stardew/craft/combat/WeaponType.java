package com.stardew.craft.combat;

/**
 * 武器类型枚举
 */
public enum WeaponType {
    SWORD(0, "sword", "stardewcraft.type.weapon.sword", 3.0f),
    DAGGER(1, "dagger", "stardewcraft.type.weapon.dagger", 2.0f),
    CLUB(2, "club", "stardewcraft.type.weapon.club", 4.0f),
    SLINGSHOT(3, "slingshot", "stardewcraft.type.weapon.slingshot", 3.0f);
    
    private final int id;
    private final String name;
    private final String translationKey;
    private final float attackRange;
    
    WeaponType(int id, String name, String translationKey, float attackRange) {
        this.id = id;
        this.name = name;
        this.translationKey = translationKey;
        this.attackRange = attackRange;
    }
    
    public int getId() { return id; }
    public String getName() { return name; }
    public String getDisplayName() { return translationKey; }
    public String getTranslationKey() { return translationKey; }
    public float getAttackSpeed() {
        return (float) StardewWeaponSpeedRules.attacksPerSecond(
                this,
                0,
                0.0F
        );
    }
    public float getAttackRange() { return attackRange; }
    
    public static WeaponType fromId(int id) {
        for (WeaponType type : values()) {
            if (type.id == id) return type;
        }
        return SWORD;
    }
    
    public static WeaponType fromName(String name) {
        for (WeaponType type : values()) {
            if (type.name.equals(name)) return type;
        }
        return SWORD;
    }
}

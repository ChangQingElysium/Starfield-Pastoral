package com.stardew.craft.combat;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import com.stardew.craft.api.v1.equipment.StardewEquipmentData;
import com.stardew.craft.api.v1.equipment.StardewEquipmentDataApi;

/**
 * 武器属性
 * 从物品NBT中读取星露谷武器的各项属性
 */
public class WeaponStats {
    
    // NBT标签名
    public static final String TAG_STARDEW_WEAPON = "StardewWeapon";
    public static final String TAG_WEAPON_TYPE = "Type";
    public static final String TAG_MIN_DAMAGE = "MinDamage";
    public static final String TAG_MAX_DAMAGE = "MaxDamage";
    public static final String TAG_CRIT_CHANCE = "CritChance";
    public static final String TAG_CRIT_POWER = "CritPower";
    public static final String TAG_SPEED = "Speed";
    public static final String TAG_DEFENSE = "Defense";
    public static final String TAG_PRECISION = "Precision";
    public static final String TAG_KNOCKBACK = "Knockback";
    
    private final WeaponType weaponType;
    private final float minDamage;
    private final float maxDamage;
    private final float critChance;      // 基础暴击率 (0.02 = 2%)
    private final float bonusCritChance; // 额外暴击率
    private final float bonusCritPower;  // 额外暴击伤害 (以百分比计，10 = +10%)
    private final int speed;             // 速度修正
    private final int defense;           // 防御值
    private final float precision;       // 精确度 (降低敌人闪避)
    private final float knockback;       // 击退力度
    
    private WeaponStats(Builder builder) {
        this.weaponType = builder.weaponType;
        this.minDamage = builder.minDamage;
        this.maxDamage = builder.maxDamage;
        this.critChance = builder.critChance;
        this.bonusCritChance = builder.bonusCritChance;
        this.bonusCritPower = builder.bonusCritPower;
        this.speed = builder.speed;
        this.defense = builder.defense;
        this.precision = builder.precision;
        this.knockback = builder.knockback;
    }
    
    /**
     * 从物品NBT读取武器属性
     */
    public static WeaponStats fromItemStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return empty();
        }
        
        @SuppressWarnings("null")
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return fromApiData(stack);
        }
        
        CompoundTag tag = data.copyTag();
        if (!tag.contains(TAG_STARDEW_WEAPON)) {
            return fromApiData(stack);
        }
        
        CompoundTag weaponTag = tag.getCompound(TAG_STARDEW_WEAPON);
        
        WeaponStats baseStats = builder()
            .weaponType(WeaponType.fromId(weaponTag.getInt(TAG_WEAPON_TYPE)))
            .minDamage(weaponTag.getFloat(TAG_MIN_DAMAGE))
            .maxDamage(weaponTag.getFloat(TAG_MAX_DAMAGE))
            .critChance(weaponTag.contains(TAG_CRIT_CHANCE) ? weaponTag.getFloat(TAG_CRIT_CHANCE) : 0.02f)
            .bonusCritPower(weaponTag.getFloat(TAG_CRIT_POWER))
            .speed(weaponTag.getInt(TAG_SPEED))
            .defense(weaponTag.getInt(TAG_DEFENSE))
            .precision(weaponTag.getFloat(TAG_PRECISION))
            .knockback(weaponTag.getFloat(TAG_KNOCKBACK))
            .build();
        return applyForgeData(baseStats, WeaponForgeData.read(stack));
    }

    private static WeaponStats fromApiData(ItemStack stack) {
        StardewEquipmentData data = StardewEquipmentDataApi.get(stack);
        if (data == null || data.weapon().isEmpty()) return empty();
        StardewEquipmentData.Weapon weapon = data.weapon().get();
        WeaponStats base = builder()
                .weaponType(WeaponType.fromName(weapon.type()))
                .minDamage(weapon.minDamage())
                .maxDamage(weapon.maxDamage())
                .critChance(weapon.baseCritChance())
                .bonusCritChance(data.critChance())
                .bonusCritPower(data.critPower())
                .speed(weapon.speed())
                .defense(weapon.defense() + data.defense())
                .precision(weapon.precision())
                .knockback(weapon.knockback() + data.knockbackBonus())
                .build();
        return applyForgeData(base, WeaponForgeData.read(stack));
    }

    public static WeaponStats applyForgeData(WeaponStats baseStats, WeaponForgeData.State forgeState) {
        float minDamage = baseStats.minDamage;
        float maxDamage = baseStats.maxDamage;
        float critChance = baseStats.critChance;
        float bonusCritPower = baseStats.bonusCritPower;
        int speed = baseStats.speed;
        int defense = baseStats.defense;
        float knockback = baseStats.knockback;

        for (WeaponForgeData.GemForge forge : forgeState.gemForges()) {
            int level = Math.max(0, forge.level());
            if (level <= 0) {
                continue;
            }
            switch (normalizeGemId(forge.itemId())) {
                case "emerald" -> speed += 5 * level;
                case "aquamarine" -> critChance += 0.046f * level;
                case "ruby" -> {
                    minDamage += Math.max(1, (int) (baseStats.minDamage * 0.1f)) * level;
                    maxDamage += Math.max(1, (int) (baseStats.maxDamage * 0.1f)) * level;
                }
                case "amethyst" -> knockback += level;
                case "topaz" -> defense += level;
                case "jade" -> bonusCritPower += 10.0f * level;
                default -> {
                }
            }
        }

        for (WeaponForgeData.DragonToothBonus dragonToothBonus : WeaponForgeData.dragonToothBonuses(forgeState.dragonToothEnchantment())) {
            switch (dragonToothBonus.kind()) {
                case "attack" -> {
                    minDamage += dragonToothBonus.level();
                    maxDamage += dragonToothBonus.level();
                }
                case "defense" -> defense += dragonToothBonus.level();
                case "speed" -> speed += dragonToothBonus.level();
                case "crit" -> critChance += 0.02f * dragonToothBonus.level();
                case "crit_power" -> bonusCritPower += 25.0f * dragonToothBonus.level();
                case "lightweight" -> knockback = Math.max(0.0f, knockback - dragonToothBonus.level());
                default -> {
                }
            }
        }

        return builder()
            .weaponType(baseStats.weaponType)
            .minDamage(minDamage)
            .maxDamage(maxDamage)
            .critChance(critChance)
            .bonusCritChance(baseStats.bonusCritChance)
            .bonusCritPower(bonusCritPower)
            .speed(speed)
            .defense(defense)
            .precision(baseStats.precision)
            .knockback(knockback)
            .build();
    }

    private static String normalizeGemId(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return "";
        }
        String normalized = itemId.toLowerCase(java.util.Locale.ROOT);
        int colon = normalized.indexOf(':');
        if (colon >= 0 && colon + 1 < normalized.length()) {
            normalized = normalized.substring(colon + 1);
        }
        return switch (normalized) {
            case "(o)60", "60" -> "emerald";
            case "(o)62", "62" -> "aquamarine";
            case "(o)64", "64" -> "ruby";
            case "(o)66", "66" -> "amethyst";
            case "(o)68", "68" -> "topaz";
            case "(o)70", "70" -> "jade";
            default -> normalized;
        };
    }

    /**
     * 将武器属性写入物品NBT
     */
    @SuppressWarnings("null")
    public void writeToItemStack(ItemStack stack) {
        @SuppressWarnings("null")
        CustomData existingData = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = existingData != null ? existingData.copyTag() : new CompoundTag();
        
        CompoundTag weaponTag = new CompoundTag();
        weaponTag.putInt(TAG_WEAPON_TYPE, weaponType.getId());
        weaponTag.putFloat(TAG_MIN_DAMAGE, minDamage);
        weaponTag.putFloat(TAG_MAX_DAMAGE, maxDamage);
        weaponTag.putFloat(TAG_CRIT_CHANCE, critChance);
        weaponTag.putFloat(TAG_CRIT_POWER, bonusCritPower);
        weaponTag.putInt(TAG_SPEED, speed);
        weaponTag.putInt(TAG_DEFENSE, defense);
        weaponTag.putFloat(TAG_PRECISION, precision);
        weaponTag.putFloat(TAG_KNOCKBACK, knockback);
        
        tag.put(TAG_STARDEW_WEAPON, weaponTag);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
    
    /**
     * 空武器属性（徒手）
     */
    public static WeaponStats empty() {
        return builder()
            .weaponType(WeaponType.SWORD)
            .minDamage(1)
            .maxDamage(1)
            .critChance(0.02f)
            .build();
    }
    
    // Getters
    public WeaponType getWeaponType() { return weaponType; }
    public float getMinDamage() { return minDamage; }
    public float getMaxDamage() { return maxDamage; }
    public float getCritChance() { return critChance; }
    public float getBonusCritChance() { return bonusCritChance; }
    public float getBonusCritPower() { return bonusCritPower; }
    public int getSpeed() { return speed; }
    public int getDefense() { return defense; }
    public float getPrecision() { return precision; }
    public float getKnockback() { return knockback; }
    
    /**
     * 获取平均伤害
     */
    public float getAverageDamage() {
        return (minDamage + maxDamage) / 2f;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private WeaponType weaponType = WeaponType.SWORD;
        private float minDamage = 1;
        private float maxDamage = 1;
        private float critChance = 0.02f;
        private float bonusCritChance = 0;
        private float bonusCritPower = 0;
        private int speed = 0;
        private int defense = 0;
        private float precision = 0;
        private float knockback = 0;
        
        public Builder weaponType(WeaponType val) { this.weaponType = val; return this; }
        public Builder minDamage(float val) { this.minDamage = val; return this; }
        public Builder maxDamage(float val) { this.maxDamage = val; return this; }
        public Builder critChance(float val) { this.critChance = val; return this; }
        public Builder bonusCritChance(float val) { this.bonusCritChance = val; return this; }
        public Builder bonusCritPower(float val) { this.bonusCritPower = val; return this; }
        public Builder speed(int val) { this.speed = val; return this; }
        public Builder defense(int val) { this.defense = val; return this; }
        public Builder precision(float val) { this.precision = val; return this; }
        public Builder knockback(float val) { this.knockback = val; return this; }
        
        public WeaponStats build() {
            return new WeaponStats(this);
        }
    }
}

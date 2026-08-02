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
    public static final String TAG_DATA_VERSION = "DataVersion";
    public static final String TAG_WEAPON_TYPE = "Type";
    public static final String TAG_MIN_DAMAGE = "MinDamage";
    public static final String TAG_MAX_DAMAGE = "MaxDamage";
    public static final String TAG_CRIT_CHANCE = "CritChance";
    public static final String TAG_CRIT_POWER = "CritPower";
    public static final String TAG_SPEED = "Speed";
    public static final String TAG_RAW_SPEED = "RawSpeed";
    public static final String TAG_DEFENSE = "Defense";
    public static final String TAG_PRECISION = "Precision";
    public static final String TAG_KNOCKBACK = "Knockback";
    public static final int CURRENT_DATA_VERSION = 3;
    
    private final WeaponType weaponType;
    private final float minDamage;
    private final float maxDamage;
    private final float critChance;      // 基础暴击率 (0.02 = 2%)
    private final float bonusCritChance; // 额外暴击率
    private final float bonusCritPower;  // Stardew Crit. Power stat points (+5 => +0.1 base multiplier)
    private final float critPowerMultiplierBonus; // Innate/API relative bonus (0.5 = +50%)
    private final int rawSpeed;          // Stardew MeleeWeapon runtime Speed
    private final int defense;           // 防御值
    private final float precision;       // 精确度 (降低敌人闪避)
    private final float knockback;       // 击退力度
    private final float weaponSpeedMultiplier;
    
    private WeaponStats(Builder builder) {
        this.weaponType = builder.weaponType;
        this.minDamage = builder.minDamage;
        this.maxDamage = builder.maxDamage;
        this.critChance = builder.critChance;
        this.bonusCritChance = builder.bonusCritChance;
        this.bonusCritPower = builder.bonusCritPower;
        this.critPowerMultiplierBonus = builder.critPowerMultiplierBonus;
        this.rawSpeed = builder.rawSpeed != null
                ? builder.rawSpeed
                : StardewWeaponSpeedRules.rawSpeed(
                        builder.weaponType,
                        builder.speed
                );
        this.defense = builder.defense;
        this.precision = builder.precision;
        this.knockback = builder.knockback;
        this.weaponSpeedMultiplier = builder.weaponSpeedMultiplier;
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
            .rawSpeed(weaponTag.contains(TAG_RAW_SPEED)
                    ? weaponTag.getInt(TAG_RAW_SPEED)
                    : StardewWeaponSpeedRules.rawSpeed(
                            WeaponType.fromId(weaponTag.getInt(TAG_WEAPON_TYPE)),
                            weaponTag.getInt(TAG_SPEED)
                    ))
            .defense(weaponTag.getInt(TAG_DEFENSE))
            .precision(weaponTag.getFloat(TAG_PRECISION))
            .knockback(weaponTag.getFloat(TAG_KNOCKBACK))
            .build();
        return applyForgeData(baseStats, WeaponForgeData.read(stack));
    }

    public static boolean hasCurrentDataVersion(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        @SuppressWarnings("null")
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return false;
        }
        CompoundTag root = data.copyTag();
        if (!root.contains(TAG_STARDEW_WEAPON)) {
            return false;
        }
        CompoundTag weaponTag = root.getCompound(TAG_STARDEW_WEAPON);
        return weaponTag.getInt(TAG_DATA_VERSION) == CURRENT_DATA_VERSION;
    }

    private static WeaponStats fromApiData(ItemStack stack) {
        StardewEquipmentData data = StardewEquipmentDataApi.get(stack);
        if (data == null || data.weapon().isEmpty()) return empty();
        StardewEquipmentData.Weapon weapon = data.weapon().get();
        WeaponType weaponType = WeaponType.fromName(weapon.type());
        float baseKnockback = Float.isFinite(weapon.knockback())
                && weapon.knockback() >= 0.0F
                ? weapon.knockback()
                : StardewWeaponKnockbackRules.defaultRawKnockback(
                        weaponType
                );
        WeaponStats base = builder()
                .weaponType(weaponType)
                .minDamage(weapon.minDamage())
                .maxDamage(weapon.maxDamage())
                .critChance(weapon.baseCritChance())
                .bonusCritChance(data.critChance())
                .critPowerMultiplierBonus(data.critPower())
                .speed(weapon.speed())
                .rawSpeed(weapon.rawSpeed().orElseGet(
                        () -> StardewWeaponSpeedRules.rawSpeed(
                                weaponType,
                                weapon.speed()
                        )
                ))
                .weaponSpeedMultiplier(data.weaponSpeedMultiplier())
                .defense(weapon.defense() + data.defense())
                .precision(weapon.precision())
                .knockback(baseKnockback + data.knockbackBonus())
                .build();
        // Public API weapons are data-owned and are not valid Mini-Forge
        // targets. Ignore stray built-in forge state instead of applying only
        // half of the forge contract.
        return base;
    }

    public static WeaponStats applyForgeData(WeaponStats baseStats, WeaponForgeData.State forgeState) {
        float minDamage = baseStats.minDamage;
        float maxDamage = baseStats.maxDamage;
        float critChance = baseStats.critChance;
        float bonusCritPower = baseStats.bonusCritPower;
        float critPowerMultiplierBonus = baseStats.critPowerMultiplierBonus;
        int rawSpeed = baseStats.rawSpeed;
        int defense = baseStats.defense;
        float knockback = baseStats.knockback;
        float weaponSpeedMultiplier = baseStats.weaponSpeedMultiplier;

        for (WeaponForgeData.GemForge forge : forgeState.gemForges()) {
            int level = Math.max(0, forge.level());
            if (level <= 0) {
                continue;
            }
            switch (normalizeGemId(forge.itemId())) {
                case "emerald" -> rawSpeed += 5 * level;
                case "aquamarine" -> critChance += 0.046f * level;
                case "ruby" -> {
                    minDamage += Math.max(1, (int) (baseStats.minDamage * 0.1f)) * level;
                    maxDamage += Math.max(1, (int) (baseStats.maxDamage * 0.1f)) * level;
                }
                case "amethyst" -> knockback += level;
                case "topaz" -> defense += level;
                // One Jade forge adds +5 Crit. Power, which contributes
                // +0.1 to the base critical multiplier (5 / 50).
                case "jade" -> bonusCritPower += 5.0f * level;
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
                // Stardew's innate WeaponSpeedEnchantment contributes to the
                // farmer multiplier; it does not mutate MeleeWeapon.speed.
                case "speed" -> weaponSpeedMultiplier +=
                        0.1F * dragonToothBonus.level();
                case "crit" -> critChance += 0.02f * dragonToothBonus.level();
                // Stardew's innate Crit. Power enchantment is a relative
                // +50% per level, not another +25 Crit. Power stat points.
                case "crit_power" -> critPowerMultiplierBonus += 0.5f * dragonToothBonus.level();
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
            .critPowerMultiplierBonus(critPowerMultiplierBonus)
            .rawSpeed(rawSpeed)
            .defense(defense)
            .precision(baseStats.precision)
            .knockback(knockback)
            .weaponSpeedMultiplier(weaponSpeedMultiplier)
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
        weaponTag.putInt(TAG_DATA_VERSION, CURRENT_DATA_VERSION);
        weaponTag.putInt(TAG_WEAPON_TYPE, weaponType.getId());
        weaponTag.putFloat(TAG_MIN_DAMAGE, minDamage);
        weaponTag.putFloat(TAG_MAX_DAMAGE, maxDamage);
        weaponTag.putFloat(TAG_CRIT_CHANCE, critChance);
        weaponTag.putFloat(TAG_CRIT_POWER, bonusCritPower);
        weaponTag.putInt(TAG_SPEED, getSpeed());
        weaponTag.putInt(TAG_RAW_SPEED, rawSpeed);
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
    public float getCritPowerMultiplierBonus() { return critPowerMultiplierBonus; }
    /** Stardew Tooltip speed after its integer division. */
    public int getSpeed() {
        return StardewWeaponSpeedRules.displayedSpeed(weaponType, rawSpeed);
    }
    public int getRawSpeed() { return rawSpeed; }
    public int getDefense() { return defense; }
    public float getPrecision() { return precision; }
    public float getKnockback() { return knockback; }
    public float getWeaponSpeedMultiplier() { return weaponSpeedMultiplier; }
    
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
        private float critPowerMultiplierBonus = 0;
        private int speed = 0;
        private Integer rawSpeed;
        private int defense = 0;
        private float precision = 0;
        private float knockback = 0;
        private float weaponSpeedMultiplier;
        
        public Builder weaponType(WeaponType val) { this.weaponType = val; return this; }
        public Builder minDamage(float val) { this.minDamage = val; return this; }
        public Builder maxDamage(float val) { this.maxDamage = val; return this; }
        public Builder critChance(float val) { this.critChance = val; return this; }
        public Builder bonusCritChance(float val) { this.bonusCritChance = val; return this; }
        public Builder bonusCritPower(float val) { this.bonusCritPower = val; return this; }
        public Builder critPowerMultiplierBonus(float val) {
            this.critPowerMultiplierBonus = val;
            return this;
        }
        public Builder speed(int val) { this.speed = val; return this; }
        public Builder rawSpeed(int val) { this.rawSpeed = val; return this; }
        public Builder defense(int val) { this.defense = val; return this; }
        public Builder precision(float val) { this.precision = val; return this; }
        public Builder knockback(float val) { this.knockback = val; return this; }
        public Builder weaponSpeedMultiplier(float val) {
            this.weaponSpeedMultiplier = val;
            return this;
        }
        
        public WeaponStats build() {
            return new WeaponStats(this);
        }
    }
}

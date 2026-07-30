package com.stardew.craft.item.equipment;

import com.stardew.craft.combat.equipment.EquipmentStats;

/**
 * SDV 全部战斗相关戒指定义
 * 精确复刻 Ring.cs AddEquipmentEffects + onMonsterSlay 逻辑
 */
public enum RingType {
    // ── 光源型 ──
    SMALL_GLOW_RING(516, 100, false),
    GLOW_RING(517, 200, false),

    // ── 磁力型 ──
    SMALL_MAGNET_RING(518, 200, false),
    MAGNET_RING(519, 200, false),

    // ── 特殊效果型 ──
    SLIME_CHARMER_RING(520, 700, true),
    WARRIOR_RING(521, 1500, true),
    VAMPIRE_RING(522, 1500, true),
    SAVAGE_RING(523, 1500, true),
    RING_OF_YOBA(524, 1500, true),
    STURDY_RING(525, 1500, true),
    BURGLARS_RING(526, 1500, true),

    // ── 组合型 ──
    IRIDIUM_BAND(527, 2000, false),

    // ── 宝石戒指 ──
    AMETHYST_RING(529, 200, false),
    TOPAZ_RING(530, 200, false),
    AQUAMARINE_RING(531, 400, false),
    JADE_RING(532, 400, false),
    EMERALD_RING(533, 600, false),
    RUBY_RING(534, 600, false),

    // ── 高级戒指 ──
    CRABSHELL_RING(810, 2000, false),
    NAPALM_RING(811, 2000, true),
    THORNS_RING(839, 200, true),
    LUCKY_RING(859, 200, false),
    HOT_JAVA_RING(860, 200, true),
    PROTECTION_RING(861, 200, true),
    SOUL_SAPPER_RING(862, 200, true),
    PHOENIX_RING(863, 200, true),
    IMMUNITY_BAND(887, 500, false),
    GLOWSTONE_RING(888, 200, false),

    // ── 结婚戒指 (801) — 纯装饰道具，无实际效果 ──
    WEDDING_RING(801, 50, false);

    private final int sdvId;
    private final int sellPrice;
    private final boolean specialEffect;

    RingType(int sdvId, int sellPrice, boolean specialEffect) {
        this.sdvId = sdvId;
        this.sellPrice = sellPrice;
        this.specialEffect = specialEffect;
    }

    public int getSdvId() { return sdvId; }
    public int getSellPrice() { return sellPrice; }
    public boolean hasSpecialEffect() { return specialEffect; }

    /**
     * 构建该戒指的 Buff 型装备属性（精确复刻 Ring.cs AddEquipmentEffects）
     */
    public EquipmentStats buildStats() {
        EquipmentStats.Builder b = EquipmentStats.builder();
        switch (this) {
            // 光源型 — 提供动态光源 (SDV: Small Glow=5, Glow=10)
            case SMALL_GLOW_RING -> b.lightLevel(5);
            case GLOW_RING -> b.lightLevel(10);
            // 磁力型，数值直接表示 MC 吸附半径（格）。
            case SMALL_MAGNET_RING -> b.magneticRadius(3);
            case MAGNET_RING -> b.magneticRadius(6);
            case IRIDIUM_BAND -> {
                b.magneticRadius(6);
                b.lightLevel(10);
                b.attackMultiplier(0.1f);
            }
            // attackMult handled separately in combat system
            case AMETHYST_RING -> b.knockbackBonus(0.1f);
            case TOPAZ_RING -> b.defense(1);
            case AQUAMARINE_RING -> b.critChance(0.1f);
            case JADE_RING -> b.critPower(0.1f);
            case EMERALD_RING -> b.weaponSpeedMultiplier(0.1f);
            case RUBY_RING -> b.attackMultiplier(0.1f);
            case CRABSHELL_RING -> b.defense(5);
            case LUCKY_RING -> b.luck(1);
            case IMMUNITY_BAND -> b.immunity(4);
            case GLOWSTONE_RING -> { b.magneticRadius(6); b.lightLevel(10); }
            // 特殊效果标记
            case SLIME_CHARMER_RING -> b.slimeCharmer(true);
            case RING_OF_YOBA -> b.yobaProtection(true);
            case THORNS_RING -> b.thorns(true);
            case STURDY_RING -> b.sturdy(true);
            case BURGLARS_RING -> b.burglar(true);
            case PROTECTION_RING -> b.protection(true);
            case PHOENIX_RING -> b.phoenix(true);
            default -> {}
        }
        return b.build();
    }

    /**
     * 是否为击杀触发型戒指
     */
    public boolean isOnKillTrigger() {
        return switch (this) {
            case WARRIOR_RING, VAMPIRE_RING, SAVAGE_RING,
                 NAPALM_RING, HOT_JAVA_RING, SOUL_SAPPER_RING -> true;
            default -> false;
        };
    }

    /**
     * 是否为攻击倍率型（需在伤害计算中检查）
     */
    public boolean isAttackMultiplier() {
        return this == RUBY_RING || this == IRIDIUM_BAND;
    }

    /**
     * 是否为武器速度型（需在挥砍速度中检查）
     */
    public boolean isWeaponSpeedMultiplier() {
        return this == EMERALD_RING;
    }

    /**
     * 获取攻击倍率加成值
     */
    public float getAttackMultiplier() {
        return isAttackMultiplier() ? 0.1f : 0f;
    }

    /**
     * 获取武器速度加成值
     */
    public float getWeaponSpeedMultiplier() {
        return isWeaponSpeedMultiplier() ? 0.1f : 0f;
    }
}

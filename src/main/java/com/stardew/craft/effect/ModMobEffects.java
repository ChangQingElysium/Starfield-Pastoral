package com.stardew.craft.effect;

import com.stardew.craft.StardewCraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import com.stardew.craft.port.net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraftforge.registries.DeferredRegister;

/**
 * 自定义 Buff（状态效果）。
 *
 * 图标放置：assets/stardewcraft/textures/mob_effect/<effect_id>.png
 */
@SuppressWarnings("null")
public final class ModMobEffects {
    private ModMobEffects() {}

    private static final class SimpleBeneficialEffect extends MobEffect {
        private SimpleBeneficialEffect(int color) {
            super(MobEffectCategory.BENEFICIAL, color);
        }
    }

    private static final class SimpleHarmfulEffect extends MobEffect {
        private SimpleHarmfulEffect(int color) {
            super(MobEffectCategory.HARMFUL, color);
        }
    }

    /**
     * 自定义“速度”效果：使用我们自己的 mob_effect/speed.png 图标，但实际加速按 MC 的移动速度属性修饰实现。
     */
    private static final class SpeedEffect extends MobEffect {
        private SpeedEffect(int color) {
            super(MobEffectCategory.BENEFICIAL, color);
            // 与原版 Speed 类似：每级 +20% 移速（乘算，随 amplifier 递增）。
            this.addAttributeModifier(
                Attributes.MOVEMENT_SPEED,
                new ResourceLocation(StardewCraft.MODID, "effect.speed"),
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                amplifier -> 0.2D * (amplifier + 1)
            );
        }
    }

    /**
     * Statue of Blessings _0 — SDV Buffs.json Effects.Speed=0.5。
     * 0.5 SDV tile/sec ≈ 25% MC 移速增益（与原 SpeedEffect 持平的换算）。
     * 单独注册以保留独立图标 (mob_effect/statue_of_blessings_0.png)。
     */
    private static final class StatueBlessingSpeedEffect extends MobEffect {
        private StatueBlessingSpeedEffect(int color) {
            super(MobEffectCategory.BENEFICIAL, color);
            this.addAttributeModifier(
                Attributes.MOVEMENT_SPEED,
                new ResourceLocation(StardewCraft.MODID, "effect.statue_of_blessings_0"),
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                amplifier -> 0.25D
            );
        }
    }

    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(net.minecraft.core.registries.Registries.MOB_EFFECT, StardewCraft.MODID);

    public static final DeferredHolder<MobEffect, MobEffect> JINXED = MOB_EFFECTS.register(
            "jinxed", () -> new SimpleHarmfulEffect(0xFF69B4));
    public static final DeferredHolder<MobEffect, MobEffect> SQUID_INK_RAVIOLI = MOB_EFFECTS.register(
            "squid_ink_ravioli", () -> new SimpleBeneficialEffect(0x504A64));

    public static final DeferredHolder<MobEffect, MobEffect> SLIMED = MOB_EFFECTS.register(
            "slimed", () -> new SimpleHarmfulEffect(0x64BD43).addAttributeModifier(
                    Attributes.MOVEMENT_SPEED,
                    new ResourceLocation(StardewCraft.MODID, "effect.slimed"),
                    -.8D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    /**
     * 活力充沛：用于“最大能量”类增益（每级 +30 Max Energy，持续时间由食物决定）。
     */
    public static final DeferredHolder<MobEffect, MobEffect> VIGOROUS = MOB_EFFECTS.register(
            "vigorous",
            () -> new SimpleBeneficialEffect(0x5CCB6A)
    );

    /**
     * 海王祝福：用于“钓鱼等级”增益（每级 +1 Fishing Level）。
     */
    public static final DeferredHolder<MobEffect, MobEffect> SEA_KING_BLESSING = MOB_EFFECTS.register(
            "sea_king_blessing",
            () -> new SimpleBeneficialEffect(0x4DA6FF)
    );

    /**
     * 精灵赐福：用于“运气等级”增益（每级 +1 Luck Level）。
     */
    public static final DeferredHolder<MobEffect, MobEffect> SPIRIT_BLESSING = MOB_EFFECTS.register(
            "spirit_blessing",
            () -> new SimpleBeneficialEffect(0xE6D36E)
    );

        /**
         * 农耕赐福：用于“农业等级”增益（每级 +1 Farming Level）。
         */
        public static final DeferredHolder<MobEffect, MobEffect> FARMER_BLESSING = MOB_EFFECTS.register(
            "farmer_blessing",
            () -> new SimpleBeneficialEffect(0x8ECF55)
        );

        /**
         * 觅食赐福：用于“觅食等级”增益（每级 +1 Foraging Level）。
         */
        public static final DeferredHolder<MobEffect, MobEffect> FORAGER_BLESSING = MOB_EFFECTS.register(
            "forager_blessing",
            () -> new SimpleBeneficialEffect(0x6CBF6E)
        );

        /**
         * 矿工赐福：用于“采矿等级”增益（每级 +1 Mining Level）。
         */
        public static final DeferredHolder<MobEffect, MobEffect> MINER_BLESSING = MOB_EFFECTS.register(
            "miner_blessing",
            () -> new SimpleBeneficialEffect(0x8FAAC7)
        );

        // ─── Statue of Blessings (Farming Mastery) — 对齐 SDV Buffs.json 7 种祝福 ───
        // SDV Duration: -2 表示"持续到次日"。本 mod 在 PlayerStardewDataAPI.sleep 清除这些 buff。
        // 应用时传 visible=false 关闭粒子（与 SDV 原版一致：祝福类 buff 不带飘散粒子）。

        /** _0 BlessingOfSpeed: SDV Effects.Speed=0.5 → 半格/秒，约 MC +25% MOVEMENT_SPEED。 */
        public static final DeferredHolder<MobEffect, MobEffect> STATUE_OF_BLESSINGS_0 = MOB_EFFECTS.register(
            "statue_of_blessings_0", () -> new StatueBlessingSpeedEffect(0x77E0A0));
        public static final DeferredHolder<MobEffect, MobEffect> STATUE_OF_BLESSINGS_1 = MOB_EFFECTS.register(
            "statue_of_blessings_1", () -> new SimpleBeneficialEffect(0xE6D36E)); // Luck
        public static final DeferredHolder<MobEffect, MobEffect> STATUE_OF_BLESSINGS_2 = MOB_EFFECTS.register(
            "statue_of_blessings_2", () -> new SimpleBeneficialEffect(0xFFA84D)); // Energy
        public static final DeferredHolder<MobEffect, MobEffect> STATUE_OF_BLESSINGS_3 = MOB_EFFECTS.register(
            "statue_of_blessings_3", () -> new SimpleBeneficialEffect(0x4DA6FF)); // Waters
        public static final DeferredHolder<MobEffect, MobEffect> STATUE_OF_BLESSINGS_4 = MOB_EFFECTS.register(
            "statue_of_blessings_4", () -> new SimpleBeneficialEffect(0xFF8FA8)); // Friendship
        public static final DeferredHolder<MobEffect, MobEffect> STATUE_OF_BLESSINGS_5 = MOB_EFFECTS.register(
            "statue_of_blessings_5", () -> new SimpleBeneficialEffect(0xCC3344)); // Fangs
        public static final DeferredHolder<MobEffect, MobEffect> STATUE_OF_BLESSINGS_6 = MOB_EFFECTS.register(
            "statue_of_blessings_6", () -> new SimpleBeneficialEffect(0xC299FF)); // Butterfly

        // ─── Statue of the Dwarf King (Mining Mastery) — 5 种可选 buff，玩家二选一 ───
        public static final DeferredHolder<MobEffect, MobEffect> DWARF_STATUE_0 = MOB_EFFECTS.register(
            "dwarf_statue_0", () -> new SimpleBeneficialEffect(0xB0A080));
        public static final DeferredHolder<MobEffect, MobEffect> DWARF_STATUE_1 = MOB_EFFECTS.register(
            "dwarf_statue_1", () -> new SimpleBeneficialEffect(0x8FAAC7));
        public static final DeferredHolder<MobEffect, MobEffect> DWARF_STATUE_2 = MOB_EFFECTS.register(
            "dwarf_statue_2", () -> new SimpleBeneficialEffect(0xC9A66B));
        public static final DeferredHolder<MobEffect, MobEffect> DWARF_STATUE_3 = MOB_EFFECTS.register(
            "dwarf_statue_3", () -> new SimpleBeneficialEffect(0x9F7FBF));
        public static final DeferredHolder<MobEffect, MobEffect> DWARF_STATUE_4 = MOB_EFFECTS.register(
            "dwarf_statue_4", () -> new SimpleBeneficialEffect(0x6B8E3B));

        /**
         * 战意：用于“攻击”增益（每级 +1 Attack）。
         */
        public static final DeferredHolder<MobEffect, MobEffect> WARRIOR_BLESSING = MOB_EFFECTS.register(
            "warrior_blessing",
            () -> new SimpleBeneficialEffect(0xD6804D)
        );

        /**
         * 守势：用于“防御”增益（每级 +1 Defense）。
         */
        public static final DeferredHolder<MobEffect, MobEffect> GUARDIAN_BLESSING = MOB_EFFECTS.register(
            "guardian_blessing",
            () -> new SimpleBeneficialEffect(0x7AA7CF)
        );

        /**
         * 磁吸：用于“磁力半径”增益（每级 +32）。
         */
        public static final DeferredHolder<MobEffect, MobEffect> MAGNETISM = MOB_EFFECTS.register(
            "magnetism",
            () -> new SimpleBeneficialEffect(0xD8B15A)
        );

        /**
         * 蒜油：怪物不会主动把玩家设为攻击目标。
         */
        public static final DeferredHolder<MobEffect, MobEffect> AVOID_MONSTERS = MOB_EFFECTS.register(
            "avoid_monsters",
            () -> new SimpleBeneficialEffect(0x7D9B45)
        );

        /**
         * 怪兽香水：提高矿井刷怪密度。
         */
        public static final DeferredHolder<MobEffect, MobEffect> MONSTER_MUSK = MOB_EFFECTS.register(
            "monster_musk",
            () -> new SimpleBeneficialEffect(0xA04FAF)
        );

        /**
         * 庇护：每级降低受到伤害 10%。
         */
        public static final DeferredHolder<MobEffect, MobEffect> SHELTER = MOB_EFFECTS.register(
            "shelter",
            () -> new SimpleBeneficialEffect(0x8FBF6A)
        );

        /**
         * 易伤：提高目标受到的伤害。
         */
        public static final DeferredHolder<MobEffect, MobEffect> VULNERABLE = MOB_EFFECTS.register(
            "vulnerable",
            () -> new SimpleHarmfulEffect(0xC43A3A)
        );

        /**
         * 弱点：提高目标被攻击时的暴击率。
         */
        public static final DeferredHolder<MobEffect, MobEffect> WEAK_POINT = MOB_EFFECTS.register(
            "weak_point",
            () -> new SimpleHarmfulEffect(0xB08C4A)
        );

        /**
         * 速度：用于可乐等短时移速增益（图标来自 mob_effect/speed.png）。
         */
        public static final DeferredHolder<MobEffect, MobEffect> SPEED = MOB_EFFECTS.register(
            "speed",
            () -> new SpeedEffect(0x7CAFC6)
        );

        /**
         * 愤怒：攻击伤害+10%（每层）
         */
        public static final DeferredHolder<MobEffect, MobEffect> FURY = MOB_EFFECTS.register(
            "fury",
            () -> {
                MobEffect effect = new SimpleBeneficialEffect(0xD4AF37); // 金色
                effect.addAttributeModifier(
                    Attributes.ATTACK_DAMAGE,
                    new ResourceLocation(StardewCraft.MODID, "effect.fury"),
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                    amplifier -> 0.10D * (amplifier + 1)
                );
                return effect;
            }
        );

    // ====== 数值规则（统一放这里，后续扩展更方便） ======

    public static int vigorousMaxEnergyBonus(int amplifier) {
        return 30 * (amplifier + 1);
    }

    public static int seaKingFishingLevelBonus(int amplifier) {
        return amplifier + 1;
    }

    public static int spiritLuckLevelBonus(int amplifier) {
        return amplifier + 1;
    }

    public static int farmerFarmingLevelBonus(int amplifier) {
        return amplifier + 1;
    }

    public static int foragerForagingLevelBonus(int amplifier) {
        return amplifier + 1;
    }

    public static int minerMiningLevelBonus(int amplifier) {
        return amplifier + 1;
    }

    public static int warriorAttackBonus(int amplifier) {
        return amplifier + 1;
    }

    public static int guardianDefenseBonus(int amplifier) {
        return amplifier + 1;
    }

    public static int magnetismRadiusBonus(int amplifier) {
        return amplifier + 1;
    }

    public static float shelterDamageMultiplier(int amplifier) {
        float reduction = 0.10f * (amplifier + 1);
        return Math.max(0.0f, 1.0f - reduction);
    }
}

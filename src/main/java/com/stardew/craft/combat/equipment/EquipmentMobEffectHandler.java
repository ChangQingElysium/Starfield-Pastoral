package com.stardew.craft.combat.equipment;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.debuff.ImmunitySystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/**
 * Applies Stardew equipment rules at the single boundary where negative
 * status effects enter a player.
 */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class EquipmentMobEffectHandler {
    private static final ThreadLocal<Boolean> REAPPLYING_STURDY_EFFECT =
            ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> APPLYING_PRE_ADJUSTED_STATUS =
            ThreadLocal.withInitial(() -> false);

    private EquipmentMobEffectHandler() {
    }

    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || REAPPLYING_STURDY_EFFECT.get()
                || APPLYING_PRE_ADJUSTED_STATUS.get()) {
            return;
        }

        MobEffectInstance effect = event.getEffectInstance();
        if (!isNegativeCategory(
                effect.getEffect().value().getCategory()
        )) {
            return;
        }

        EquipmentStats equipment = EquipmentResolver.getMergedStats(player);
        if (ImmunitySystem.tryResistEffect(equipment.getImmunity())) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            return;
        }

        int adjustedDuration = ImmunitySystem.adjustDurationTicks(
                effect.getDuration(),
                equipment.hasSturdy()
        );
        if (adjustedDuration == effect.getDuration()) {
            return;
        }

        event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        REAPPLYING_STURDY_EFFECT.set(true);
        try {
            player.addEffect(copyWithDuration(effect, adjustedDuration), event.getEffectSource());
        } finally {
            REAPPLYING_STURDY_EFFECT.remove();
        }
    }

    static boolean isNegativeCategory(MobEffectCategory category) {
        return category == MobEffectCategory.HARMFUL;
    }

    /**
     * Adds a component of a custom status whose one equipment decision has
     * already been made. The normal event boundary is bypassed only for this
     * synchronous addEffect call, preventing a second immunity roll or a
     * second Sturdy duration reduction.
     */
    public static boolean addPreAdjustedEffect(
            LivingEntity target,
            MobEffectInstance effect
    ) {
        boolean previous = APPLYING_PRE_ADJUSTED_STATUS.get();
        APPLYING_PRE_ADJUSTED_STATUS.set(true);
        try {
            return target.addEffect(effect);
        } finally {
            if (previous) {
                APPLYING_PRE_ADJUSTED_STATUS.set(true);
            } else {
                APPLYING_PRE_ADJUSTED_STATUS.remove();
            }
        }
    }

    private static MobEffectInstance copyWithDuration(
            MobEffectInstance effect,
            int durationTicks
    ) {
        return new MobEffectInstance(
                effect.getEffect(),
                durationTicks,
                effect.getAmplifier(),
                effect.isAmbient(),
                effect.isVisible(),
                effect.showIcon()
        );
    }
}

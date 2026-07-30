package com.stardew.craft.combat.equipment;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.debuff.ImmunitySystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
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

    private EquipmentMobEffectHandler() {
    }

    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || REAPPLYING_STURDY_EFFECT.get()) {
            return;
        }

        MobEffectInstance effect = event.getEffectInstance();
        if (effect.getEffect().value().getCategory() != MobEffectCategory.HARMFUL) {
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

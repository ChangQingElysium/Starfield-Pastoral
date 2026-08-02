package com.stardew.craft.combat.skill.runtime;

import com.stardew.craft.StardewCraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;

/** Lets an effective incoming knockback take ownership from skill movement. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class WeaponSkillKnockbackArbitration {
    private static final double EFFECTIVE_STRENGTH_EPSILON = 1.0E-6D;

    private WeaponSkillKnockbackArbitration() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingKnockBack(LivingKnockBackEvent event) {
        if (event.isCanceled()
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        double resistance = player.getAttributeValue(
                Attributes.KNOCKBACK_RESISTANCE
        );
        if (hasEffectiveKnockback(event.getStrength(), resistance)) {
            WeaponSkillMovementArbiter.revokeCurrent(player);
        }
    }

    static boolean hasEffectiveKnockback(
            double strength,
            double knockbackResistance
    ) {
        double clampedResistance = Math.max(
                0.0D,
                Math.min(1.0D, knockbackResistance)
        );
        return strength * (1.0D - clampedResistance)
                > EFFECTIVE_STRENGTH_EPSILON;
    }
}

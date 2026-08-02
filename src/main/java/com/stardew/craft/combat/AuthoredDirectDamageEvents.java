package com.stardew.craft.combat;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.skill.TemplarJudgementHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/** Applied Post bridge for direct authored damage that is not weapon damage. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class AuthoredDirectDamageEvents {
    private AuthoredDirectDamageEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamagePost(LivingDamageEvent.Post event) {
        onAppliedDamage(
                event.getEntity(),
                event.getSource(),
                event.getNewDamage(),
                event.getEntity().level().getGameTime()
        );
    }

    /**
     * Unified authoritative boundary for native Post damage and Stardew's
     * custom-health equivalent.
     */
    public static void onAppliedDamage(
            LivingEntity target,
            DamageSource source,
            float appliedDamage,
            long nowTick
    ) {
        if (target == null
                || source == null
                || target.level().isClientSide) {
            return;
        }
        AuthoredDirectDamageContextStore.Frame frame =
                AuthoredDirectDamageContextStore.consume(
                        target,
                        source,
                        nowTick
                );
        if (frame != null && TemplarJudgementHandler.SHARE_DAMAGE_ID.equals(
                frame.authoredId()
        )) {
            if (Float.isFinite(appliedDamage) && appliedDamage > 0.0F) {
                TemplarJudgementHandler.emitAppliedShare(
                        target,
                        appliedDamage
                );
            }
            return;
        }
        if (target instanceof ServerPlayer player
                && Float.isFinite(appliedDamage)
                && appliedDamage > 0.0F) {
            TemplarJudgementHandler.onAppliedPlayerDamage(
                    player,
                    source,
                    appliedDamage,
                    nowTick
            );
        }
    }
}

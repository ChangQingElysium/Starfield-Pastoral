package com.stardew.craft.combat.skill;

import com.stardew.craft.StardewCraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class TemplarVowHandler {

    private TemplarVowHandler() {}

    @SuppressWarnings("null")
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerHurt(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            return;
        }

        Level level = player.level();
        if (level.isClientSide) {
            return;
        }
        if (event.getAmount() <= 0.0f) {
            return;
        }

        long nowTick = level.getGameTime();
        if (!TemplarVowTracker.isActive(player, nowTick)) {
            return;
        }

        event.setAmount(0.0f);
        level.playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.9f, 1.1f);

        player.swing(InteractionHand.MAIN_HAND, true);

        Entity src = event.getSource().getEntity();
        if (src instanceof LivingEntity attacker && attacker.isAlive()) {
            SkillContext context = TemplarVowTracker.createStrikeContext(
                    TemplarVowTracker.COUNTER_DAMAGE_MULTIPLIER
            );
            WeaponDamageSnapshot weaponSnapshot =
                    TemplarVowTracker.getWeaponSnapshot(player).orElse(null);
            long expireTick = nowTick
                    + TemplarVowTracker.HIT_CONTEXT_LIFETIME_TICKS;
            if (weaponSnapshot == null) {
                WeaponSkillDamage.apply(
                        player,
                        attacker,
                        context,
                        expireTick,
                        WeaponSkillDamage.AttackGatePolicy.RESPECT_AT_IMPACT
                );
            } else {
                WeaponSkillDamage.apply(
                        player,
                        attacker,
                        context,
                        weaponSnapshot,
                        expireTick,
                        WeaponSkillDamage.AttackGatePolicy.RESPECT_AT_IMPACT
                );
            }
        }

        TemplarVowTracker.endNow(player, nowTick);
    }
}

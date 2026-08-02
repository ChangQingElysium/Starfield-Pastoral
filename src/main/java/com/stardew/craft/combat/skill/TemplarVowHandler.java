package com.stardew.craft.combat.skill;

import com.stardew.craft.combat.skill.handler.TemplarVowSkillHandler;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

public final class TemplarVowHandler {

    private TemplarVowHandler() {}

    @SuppressWarnings("null")
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
        TemplarVowSkillHandler.CounterActivation activation =
                TemplarVowSkillHandler.consumeCounter(
                        player,
                        nowTick
                ).orElse(null);
        if (activation == null) {
            return;
        }

        event.setAmount(0.0f);
        level.playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.9f, 1.1f);

        player.swing(InteractionHand.MAIN_HAND, true);

        Entity src = event.getSource().getEntity();
        if (src instanceof LivingEntity attacker && attacker.isAlive()) {
            SkillContext context = TemplarVowSkillHandler.createStrikeContext(
                    TemplarVowSkillHandler.COUNTER_DAMAGE_MULTIPLIER
            );
            WeaponDamageSnapshot weaponSnapshot =
                    activation.weaponSnapshot();
            long expireTick = nowTick
                    + TemplarVowSkillHandler.HIT_CONTEXT_LIFETIME_TICKS;
            if (weaponSnapshot == null) {
                WeaponSkillDamage.apply(
                        player,
                        attacker,
                        context,
                        expireTick,
                        WeaponSkillDamage.AttackGatePolicy.RESPECT_AT_IMPACT,
                        WeaponSkillDamage.HitCooldownPolicy.RESPECT_VANILLA
                );
            } else {
                WeaponSkillDamage.apply(
                        player,
                        attacker,
                        context,
                        weaponSnapshot,
                        expireTick,
                        WeaponSkillDamage.AttackGatePolicy.RESPECT_AT_IMPACT,
                        WeaponSkillDamage.HitCooldownPolicy.RESPECT_VANILLA
                );
            }
        }

        TemplarVowSkillHandler.finishCounter(player, nowTick);
    }
}

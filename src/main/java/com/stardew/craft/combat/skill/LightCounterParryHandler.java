package com.stardew.craft.combat.skill;

import com.stardew.craft.combat.skill.handler.LightCounterSkillHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

public final class LightCounterParryHandler {

    private LightCounterParryHandler() {}

    @SuppressWarnings("null")
    public static void onPlayerHurt(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
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
        LightCounterSkillHandler.CounterActivation activation =
                LightCounterSkillHandler.consumeParry(
                        player,
                        nowTick
                ).orElse(null);
        if (activation == null) {
            return;
        }

        String weaponId = activation.weaponId();
        if (weaponId == null || weaponId.isEmpty()) {
            return;
        }
        WeaponDamageSnapshot weaponSnapshot = activation.weaponSnapshot();

        // Reduce incoming damage to 40%
        event.setAmount(event.getAmount() * 0.4f);

        level.playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0f, 1.0f);

        Entity src = event.getSource().getEntity();
        if (src instanceof LivingEntity attacker && attacker.isAlive()) {
            SkillContext context = SkillContext.builder()
                    .skillId("light_counter")
                    .tier(SkillContext.SkillTier.MINOR)
                    .damageMultiplier(1.2f)
                    .build();
            if (weaponSnapshot == null) {
                WeaponSkillDamage.apply(
                        player,
                        attacker,
                        context,
                        nowTick + 5,
                        WeaponSkillDamage.AttackGatePolicy.RESPECT_AT_IMPACT,
                        WeaponSkillDamage.HitCooldownPolicy.RESPECT_VANILLA
                );
            } else {
                WeaponSkillDamage.apply(
                        player,
                        attacker,
                        context,
                        weaponSnapshot,
                        nowTick + 5,
                        WeaponSkillDamage.AttackGatePolicy.RESPECT_AT_IMPACT,
                        WeaponSkillDamage.HitCooldownPolicy.RESPECT_VANILLA
                );
            }
        }

        WeaponSkillAnimationDispatcher.sendCounterAnim(
                player,
                weaponId,
                "light_counter_counter",
                LightCounterSkillHandler.COUNTER_ANIM_TICKS
        );
    }

}

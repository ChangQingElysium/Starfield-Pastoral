package com.stardew.craft.combat.skill;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.CombatHealing;
import com.stardew.craft.combat.network.DamageNumberPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class TemplarJudgementHandler {

    private TemplarJudgementHandler() {}

    @SuppressWarnings("null")
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPlayerDamaged(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.level().isClientSide) {
            return;
        }

        long nowTick = player.level().getGameTime();
        if (!TemplarJudgementTracker.isActive(player, nowTick)) {
            return;
        }

        float damage = event.getAmount();
        if (damage <= 0.0f) {
            return;
        }

        List<net.minecraft.world.entity.LivingEntity> targets = TemplarJudgementTracker.getMarkedTargets(player);
        if (targets.isEmpty()) {
            return;
        }

        float total = TemplarJudgementTracker.cappedSharedDamage(
                damage,
                CombatHealing.maximumHealth(player)
        );
        if (total <= 0.0f) {
            return;
        }

        float share = total / targets.size();
        if (share <= 0.0f) {
            return;
        }

        for (net.minecraft.world.entity.LivingEntity target : targets) {
            if (!target.isAlive()) {
                continue;
            }
            target.invulnerableTime = 0;
            target.hurtTime = 0;
            target.hurt(player.level().damageSources().magic(), share);

            if (player.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                    6, 0.4, 0.2, 0.4, 0.01);
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                    target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                    4, 0.3, 0.15, 0.3, 0.04);
                serverLevel.playSound(null, target.blockPosition(),
                    net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.4f, 1.6f);
            }

            int dmg = Math.max(1, Math.round(share));
            DamageNumberPayload payload = new DamageNumberPayload(
                (float) target.getX(),
                (float) (target.getY() + target.getBbHeight() * 0.75f),
                (float) target.getZ(),
                dmg,
                false,
                "templar_judgement_share"
            );
            if (player.level() instanceof ServerLevel serverLevel) {
                PacketDistributor.sendToPlayersInDimension(serverLevel, payload);
            } else {
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(target, payload);
            }
        }
    }
}

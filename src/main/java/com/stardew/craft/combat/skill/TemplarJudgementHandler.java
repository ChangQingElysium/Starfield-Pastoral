package com.stardew.craft.combat.skill;

import com.stardew.craft.combat.AuthoredDirectDamageContextStore;
import com.stardew.craft.combat.CombatHealing;
import com.stardew.craft.combat.network.DamageNumberPayload;
import com.stardew.craft.combat.network.TemplarJudgementImpactPayload;
import com.stardew.craft.combat.skill.handler.TemplarJudgementSkillHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public final class TemplarJudgementHandler {
    public static final String SHARE_DAMAGE_ID = "templar_judgement_share";
    private static final int SHARE_CONTEXT_LIFETIME_TICKS = 2;

    private TemplarJudgementHandler() {}

    /**
     * Distributes only authoritative player health loss. Callers must route
     * authored direct damage through {@code AuthoredDirectDamageEvents} first
     * so a share frame is consumed instead of recursively redistributed.
     */
    public static void onAppliedPlayerDamage(
            ServerPlayer player,
            DamageSource incomingSource,
            float appliedDamage,
            long nowTick
    ) {
        if (player == null
                || incomingSource == null
                || player.level().isClientSide
                || !Float.isFinite(appliedDamage)
                || appliedDamage <= 0.0F) {
            return;
        }
        if (AuthoredDirectDamageContextStore.isBound(
                player,
                incomingSource,
                SHARE_DAMAGE_ID,
                nowTick
        )) {
            return;
        }
        if (!TemplarJudgementSkillHandler.isActive(player, nowTick)) {
            return;
        }

        List<LivingEntity> targets =
                TemplarJudgementSkillHandler.getMarkedTargets(player);
        if (targets.isEmpty()) {
            return;
        }

        float total = TemplarJudgementSkillHandler.cappedSharedDamage(
                appliedDamage,
                CombatHealing.maximumHealth(player)
        );
        if (total <= 0.0f) {
            return;
        }

        float share = total / targets.size();
        if (share <= 0.0f) {
            return;
        }

        for (LivingEntity target : targets) {
            if (!target.isAlive()) {
                continue;
            }
            DamageSource source = authoredMagicSource(player);
            AuthoredDirectDamageContextStore.bind(
                    player,
                    target,
                    source,
                    SHARE_DAMAGE_ID,
                    nowTick + SHARE_CONTEXT_LIFETIME_TICKS
            );
            try {
                target.hurt(source, share);
            } finally {
                AuthoredDirectDamageContextStore.discard(target, source);
            }
        }
    }

    /** MAGIC semantics with caster attribution and authored-hit cooldown. */
    private static DamageSource authoredMagicSource(ServerPlayer player) {
        DamageSource magic = player.level().damageSources().magic();
        DamageSource attributedMagic = new DamageSource(
                magic.typeHolder(),
                player,
                player
        );
        return HitCooldownDamageSource.bypassVanillaCooldown(
                attributedMagic
        );
    }

    /** Emits share success feedback from the exact non-weapon Applied Post. */
    public static void emitAppliedShare(
            LivingEntity target,
            float appliedDamage
    ) {
        if (!(target.level() instanceof ServerLevel serverLevel)
                || !Float.isFinite(appliedDamage)
                || appliedDamage <= 0.0F) {
            return;
        }
        serverLevel.sendParticles(
                net.minecraft.core.particles.ParticleTypes.END_ROD,
                target.getX(),
                target.getY() + target.getBbHeight() * 0.6,
                target.getZ(),
                6, 0.4, 0.2, 0.4, 0.01
        );
        serverLevel.sendParticles(
                net.minecraft.core.particles.ParticleTypes.CRIT,
                target.getX(),
                target.getY() + target.getBbHeight() * 0.6,
                target.getZ(),
                4, 0.3, 0.15, 0.3, 0.04
        );
        serverLevel.playSound(
                null,
                target.blockPosition(),
                net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
                net.minecraft.sounds.SoundSource.PLAYERS,
                0.4F,
                1.6F
        );

        int damage = Math.max(1, Math.round(appliedDamage));
        PacketDistributor.sendToPlayersInDimension(
                serverLevel,
                new DamageNumberPayload(
                (float) target.getX(),
                (float) (target.getY() + target.getBbHeight() * 0.75f),
                (float) target.getZ(),
                damage,
                false,
                SHARE_DAMAGE_ID
                )
        );
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                target,
                new TemplarJudgementImpactPayload(target.getId())
        );
    }
}

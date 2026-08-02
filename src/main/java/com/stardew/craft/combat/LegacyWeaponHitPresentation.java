package com.stardew.craft.combat;

import com.stardew.craft.combat.network.DamageNumberPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Frozen legacy feedback emitted by authoritative weapon impacts.
 *
 * <p>This boundary deliberately contains no combat state mutation. It keeps
 * the existing particles, sounds, and damage-number offsets unchanged while
 * gameplay rules move out of the event adapter. The later presentation
 * rebuild replaces this class rather than adding more branches to it.</p>
 */
final class LegacyWeaponHitPresentation {
    private LegacyWeaponHitPresentation() {
    }

    static void emitDamageNumber(ResolvedWeaponHit hit) {
        if (!hit.inStardewDimension()) {
            return;
        }
        LivingEntity target = hit.target();
        String skillId = hit.skillId();
        double headTop = target.getY() + target.getBbHeight();
        double baseYOffset = Math.max(0.20, target.getBbHeight() * 0.25);
        double baseY = headTop + baseYOffset;
        if ("tide_mark_bonus".equals(skillId)) {
            baseY += target.getBbHeight() * 0.12;
        } else if ("tide_anchor".equals(skillId)) {
            baseY += target.getBbHeight() * 0.06;
        } else if ("tide_reel".equals(skillId)) {
            baseY += target.getBbHeight() * 0.08;
        } else if ("templar_judgement_share".equals(skillId)) {
            baseY += target.getBbHeight() * 0.10;
        } else if ("templar_judgement".equals(skillId)) {
            baseY += target.getBbHeight() * 0.08;
        }
        DamageNumberPayload payload = new DamageNumberPayload(
                (float) target.getX(),
                (float) baseY,
                (float) target.getZ(),
                Math.max(0, Math.round(hit.appliedDamage())),
                hit.displayCritical(),
                skillId
        );
        if (target.level() instanceof ServerLevel serverLevel) {
            PacketDistributor.sendToPlayersInDimension(serverLevel, payload);
        } else {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                    target,
                    payload
            );
        }
    }

    static void emitGeneralSkillImpact(ResolvedWeaponHit hit) {
        LivingEntity target = hit.target();
        if (!(target.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        String skillId = hit.skillId();
        double x = target.getX();
        double y = target.getY() + target.getBbHeight() * 0.6;
        double z = target.getZ();

        if ("tide_mark_bonus".equals(skillId)) {
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH,
                    x, y, z, 10, 0.35, 0.2, 0.35, 0.03);
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                    x, y, z, 6, 0.25, 0.15, 0.25, 0.05);
            serverLevel.playSound(null, target.blockPosition(),
                    net.minecraft.sounds.SoundEvents.TRIDENT_HIT,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.55f, 1.1f);
        }
        if ("tide_anchor".equals(skillId)) {
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH,
                    x, y, z, 8, 0.35, 0.2, 0.35, 0.03);
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                    x, y, z, 5, 0.25, 0.15, 0.25, 0.05);
            serverLevel.playSound(null, target.blockPosition(),
                    net.minecraft.sounds.SoundEvents.TRIDENT_HIT,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.35f, 0.95f);
        }
        if ("fishcatch_thrust".equals(skillId)) {
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH,
                    x, y, z, 10, 0.35, 0.2, 0.35, 0.03);
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.BUBBLE,
                    x, y, z, 8, 0.35, 0.2, 0.35, 0.02);
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                    x, y, z, 5, 0.25, 0.15, 0.25, 0.05);
            serverLevel.playSound(null, target.blockPosition(),
                    net.minecraft.sounds.SoundEvents.TRIDENT_HIT,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.45f, 1.1f);
        }
        if ("tide_reel".equals(skillId)) {
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH,
                    x, y, z, 14, 0.45, 0.25, 0.45, 0.04);
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.BUBBLE,
                    x, y, z, 10, 0.45, 0.25, 0.45, 0.02);
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                    x, y, z, 8, 0.35, 0.2, 0.35, 0.06);
            serverLevel.playSound(null, target.blockPosition(),
                    net.minecraft.sounds.SoundEvents.TRIDENT_RIPTIDE_1.value(),
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.55f, 1.1f);
        }
        if ("crystal_dagger_burst".equals(skillId)) {
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    x, y, z, 18, 0.45, 0.35, 0.45, 0.02);
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                    x, y, z, 14, 0.45, 0.35, 0.45, 0.05);
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                    x, y, z, 10, 0.35, 0.25, 0.35, 0.06);
            serverLevel.playSound(null, target.blockPosition(),
                    net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_BREAK,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.15f);
        }
        if ("shadow_dagger_execute".equals(skillId)
                || "shadow_dagger_execute_bonus".equals(skillId)) {
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME,
                    x, y, z, 16, 0.35, 0.25, 0.35, 0.02);
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                    x, y, z, 10, 0.25, 0.2, 0.25, 0.01);
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                    x, y, z, 10, 0.35, 0.25, 0.35, 0.04);
            serverLevel.playSound(null, target.blockPosition(),
                    net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_CRIT,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.7f, 1.4f);
            if (!target.isAlive() || target.getHealth() <= 0.0f) {
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                        x, y + 0.15, z, 20, 0.45, 0.35, 0.45, 0.03);
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                        x, y, z, 16, 0.35, 0.25, 0.35, 0.08);
                serverLevel.playSound(null, target.blockPosition(),
                        net.minecraft.sounds.SoundEvents.WITHER_SPAWN,
                        net.minecraft.sounds.SoundSource.PLAYERS, 0.45f, 1.2f);
                serverLevel.playSound(null, target.blockPosition(),
                        net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(),
                        net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 1.2f);
            }
        }
    }

    static void emitInsectEyeImpact(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        double x = target.getX();
        double y = target.getY() + target.getBbHeight() * 0.55;
        double z = target.getZ();
        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                x, y, z, 10, 0.35, 0.2, 0.35, 0.02);
        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                x, y, z, 6, 0.25, 0.15, 0.25, 0.05);
        serverLevel.playSound(null, target.blockPosition(),
                net.minecraft.sounds.SoundEvents.BEEHIVE_WORK,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 1.4f);
    }

    static void emitObsidianResonance(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        serverLevel.playSound(
                null,
                target.blockPosition(),
                net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
                net.minecraft.sounds.SoundSource.PLAYERS,
                0.5F,
                1.6F
        );
        serverLevel.sendParticles(
                net.minecraft.core.particles.ParticleTypes.CRIT,
                target.getX(),
                target.getY() + target.getBbHeight() * 0.6,
                target.getZ(),
                1,
                0.0,
                0.0,
                0.0,
                0.0
        );
    }

    static void emitInsectDashImpact(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        double x = target.getX();
        double y = target.getY() + target.getBbHeight() * 0.6;
        double z = target.getZ();
        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK,
                x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                x, y, z, 8, 0.3, 0.18, 0.3, 0.06);
        serverLevel.playSound(null, target.blockPosition(),
                net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.15f);
    }

    static void emitOssifiedMarkBonus(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        double x = target.getX();
        double y = target.getY() + target.getBbHeight() * 0.6;
        double z = target.getZ();
        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.ASH,
                x, y, z, 8, 0.25, 0.18, 0.25, 0.02);
        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                x, y, z, 6, 0.2, 0.12, 0.2, 0.05);
        serverLevel.playSound(null, target.blockPosition(),
                net.minecraft.sounds.SoundEvents.BONE_BLOCK_BREAK,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.2f);
    }

    static void emitGalaxyMarkBonus(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        double x = target.getX();
        double y = target.getY() + target.getBbHeight() * 0.6;
        double z = target.getZ();
        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                x, y, z, 14, 0.35, 0.2, 0.35, 0.04);
        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                x, y, z, 12, 0.35, 0.2, 0.35, 0.05);
        serverLevel.playSound(null, target.blockPosition(),
                net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_BREAK,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.35f);
        serverLevel.playSound(null, target.blockPosition(),
                net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_CRIT,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.35f, 1.2f);
    }

    static void emitInfinityMarkBonus(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        double x = target.getX();
        double y = target.getY() + target.getBbHeight() * 0.6;
        double z = target.getZ();
        double radius = 0.32;
        for (int i = 0; i < 12; i++) {
            double angle = (Math.PI * 2.0 * i) / 12.0;
            double px = x + Math.cos(angle) * radius;
            double pz = z + Math.sin(angle) * radius;
            serverLevel.addParticle(net.minecraft.core.particles.ParticleTypes.PORTAL,
                    px, y, pz, (x - px) * 0.08, 0.0, (z - pz) * 0.08);
        }
        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                x, y, z, 10, 0.25, 0.18, 0.25, 0.02);
        serverLevel.playSound(null, target.blockPosition(),
                net.minecraft.sounds.SoundEvents.END_PORTAL_FRAME_FILL,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 0.75f);
        serverLevel.playSound(null, target.blockPosition(),
                net.minecraft.sounds.SoundEvents.END_PORTAL_SPAWN,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.35f, 0.75f);
    }
}

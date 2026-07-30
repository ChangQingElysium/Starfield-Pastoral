package com.stardew.craft.combat.skill;

import com.stardew.craft.combat.CombatHealing;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

@SuppressWarnings("null")
public final class HolyBladeEffects {

    private HolyBladeEffects() {}

    public static void playSmiteHit(ServerLevel level, LivingEntity target) {
        if (level == null || target == null) {
            return;
        }
        double x = target.getX();
        double y = target.getY() + target.getBbHeight() * 0.6;
        double z = target.getZ();

        level.sendParticles(ParticleTypes.FLASH, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, 10, 0.35, 0.3, 0.35, 0.02);
        level.sendParticles(ParticleTypes.INSTANT_EFFECT, x, y, z, 8, 0.25, 0.2, 0.25, 0.02);

        level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.9f, 1.25f);
        level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7f, 1.8f);
    }

    public static void playHeal(ServerPlayer player, int amount) {
        if (player == null) {
            return;
        }
        CombatHealing.heal(player, amount);

        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        Vec3 pos = player.position();
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
            pos.x, pos.y + player.getBbHeight() * 0.45, pos.z,
            10, 0.5, 0.4, 0.5, 0.02);
        level.sendParticles(ParticleTypes.END_ROD,
            pos.x, pos.y + player.getBbHeight() * 0.6, pos.z,
            8, 0.3, 0.5, 0.3, 0.03);

        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.35f, 1.6f);
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7f, 1.9f);
    }

    public static void playDomainActivate(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 pos = player.position();
        level.sendParticles(ParticleTypes.END_ROD,
            pos.x, pos.y + 0.1, pos.z,
            28, 0.8, 0.35, 0.8, 0.04);
        level.sendParticles(ParticleTypes.INSTANT_EFFECT,
            pos.x, pos.y + 0.15, pos.z,
            20, 0.9, 0.2, 0.9, 0.03);

        level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.0f, 0.9f);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.9f, 0.8f);
    }

    public static void playDomainPulse(ServerLevel level, LivingEntity target) {
        if (level == null || target == null) {
            return;
        }
        double x = target.getX();
        double y = target.getY() + target.getBbHeight() * 0.6;
        double z = target.getZ();

        level.sendParticles(ParticleTypes.END_ROD, x, y, z, 8, 0.3, 0.3, 0.3, 0.02);
        level.sendParticles(ParticleTypes.INSTANT_EFFECT, x, y, z, 6, 0.25, 0.2, 0.25, 0.02);
        level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.7f, 1.6f);
    }

    public static void playDodgeSuccess(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 pos = player.position();
        level.sendParticles(ParticleTypes.CLOUD,
            pos.x, pos.y + player.getBbHeight() * 0.4, pos.z,
            12, 0.4, 0.2, 0.4, 0.02);
        level.sendParticles(ParticleTypes.END_ROD,
            pos.x, pos.y + player.getBbHeight() * 0.55, pos.z,
            8, 0.3, 0.35, 0.3, 0.02);

        player.playNotifySound(SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 1.8f);
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7f, 2.0f);
    }
}

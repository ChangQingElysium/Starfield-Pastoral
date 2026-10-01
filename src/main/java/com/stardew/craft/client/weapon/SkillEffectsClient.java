package com.stardew.craft.client.weapon;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 技能视觉和听觉效果（客户端）
 * 每个技能都有独特的粒子和声音组合
 */
public final class SkillEffectsClient {

    private SkillEffectsClient() {}

    /**
     * 根据技能ID播放对应的效果
     */
    public static void playSkillEffects(String skillId, Player player) {
        if (player == null || player.level() == null) {
            return;
        }

        switch (skillId) {
            case "carving_thrust" -> playCarvingThrust(player);
            case "lava_katana_brand" -> playLavaKatanaBrand(player);
            default -> playGenericSkill(player);
        }
    }

    /**
     * 熔岩武士刀 - 熔铸刻印
     * 粒子：熔火与火星喷溅
     * 声音：熔岩爆裂 + 火焰呼啸
     */
    @SuppressWarnings("null")
    private static void playLavaKatanaBrand(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();
        Vec3 look = player.getLookAngle();

        player.playSound(SoundEvents.LAVA_POP, 0.7f, 1.1f);
        player.playSound(SoundEvents.FIRECHARGE_USE, 0.6f, 1.2f);

        double frontX = pos.x + look.x * 1.5;
        double frontY = pos.y + player.getBbHeight() * 0.6;
        double frontZ = pos.z + look.z * 1.5;

        for (int i = 0; i < 10; i++) {
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.5;
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.3;
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.5;
            mc.level.addParticle(ParticleTypes.FLAME,
                frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                look.x * 0.05, 0.02, look.z * 0.05);
        }

        for (int i = 0; i < 6; i++) {
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.4;
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.3;
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.4;
            mc.level.addParticle(ParticleTypes.LAVA,
                frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                0.0, 0.02, 0.0);
        }
    }

    @SuppressWarnings("null")
    private static void playCarvingThrust(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();
        Vec3 look = player.getLookAngle();

        player.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 1.4f);
        player.playSound(SoundEvents.PLAYER_ATTACK_CRIT, 0.6f, 1.6f);

        double frontX = pos.x + look.x * 1.1;
        double frontY = pos.y + player.getBbHeight() * 0.55;
        double frontZ = pos.z + look.z * 1.1;

        mc.level.addParticle(ParticleTypes.SWEEP_ATTACK, frontX, frontY, frontZ, 0, 0, 0);

        for (int i = 0; i < 8; i++) {
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.45;
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.25;
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.45;
            mc.level.addParticle(ParticleTypes.CRIT,
                frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                look.x * 0.06, 0.02, look.z * 0.06);
        }
    }

    /**
     * 残破的三叉戟 - 鱼获状态触发提示
     */
    @SuppressWarnings("null")
    public static void playFishcatchReady(Player player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.35f, 1.65f);
        if (!com.stardew.craft.Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()) return;
        Vec3 point = player.getEyePosition().add(player.getLookAngle().scale(0.8)).add(0, -0.35, 0);
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4;
            mc.level.addParticle(ParticleTypes.SPLASH, point.x, point.y, point.z,
                    Math.cos(angle) * 0.04, 0.035, Math.sin(angle) * 0.04);
        }
    }

    /**
     * 通用技能效果（后备）
     */
    @SuppressWarnings("null")
    private static void playGenericSkill(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();
        Vec3 look = player.getLookAngle();

        player.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 0.8f, 1.0f);

        double frontX = pos.x + look.x * 1.2;
        double frontY = pos.y + player.getBbHeight() * 0.6;
        double frontZ = pos.z + look.z * 1.2;

        for (int i = 0; i < 4; i++) {
            mc.level.addParticle(ParticleTypes.SWEEP_ATTACK,
                frontX, frontY, frontZ,
                0, 0, 0);
        }
    }

}

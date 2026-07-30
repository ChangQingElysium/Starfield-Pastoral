package com.stardew.craft.client.weapon;

import com.stardew.craft.StardewCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
            case "iron_dirk_thrust" -> playIronDirkThrust(player);
            case "burglar_shank" -> playBurglarShankSlash(player);
            case "tetanus_strike" -> playTetanusStrike(player);
            case "light_counter" -> playLightCounter(player);
            case "tree_blessing" -> playTreeBlessing(player);
            case "desperate_plunder" -> playDesperatePlunder(player);
            case "silver_foldback" -> playSilverFoldback(player);
            case "bone_fracture" -> playBoneFracture(player);
            case "claymore_foldback" -> playClaymoreFoldback(player);
            case "elf_blade_leaf" -> playElfBladeLeaf(player);
            case "carving_thrust" -> playCarvingThrust(player);
            case "crystal_dagger_layer" -> playCrystalDaggerLayer(player);
            case "crystal_dagger_burst" -> playCrystalDaggerBurst(player);
            case "shadow_dagger_execute" -> playShadowDaggerExecute(player);
            case "shadow_dagger_execute_bonus" -> playShadowDaggerExecute(player);
            case "fishcatch_thrust" -> playFishcatchThrust(player);
            case "tide_reel" -> playTideReel(player);
            case "tide_mark" -> playTideMark(player);
            case "tide_anchor" -> playTideAnchor(player);
            case "templar_vow" -> playTemplarVow(player);
            case "templar_judgement" -> playTemplarJudgement(player);
            case "meowmere_shot" -> playMeowmereShot(player, false);
            case "meowmere_symphony" -> playMeowmereShot(player, true);
            case "insect_eye_stance" -> playInsectEyeStance(player);
            case "insect_dash" -> playInsectDash(player);
            case "obsidian_resonance" -> playObsidianResonance(player);
            case "obsidian_crack" -> playObsidianCrack(player);
            case "ossified_mark" -> playOssifiedMark(player);
            case "ossified_execution" -> playOssifiedExecution(player);
            case "holy_smite" -> playHolySmite(player);
            case "holy_domain" -> playHolyDomain(player);
            case "tempered_quench" -> playTemperedQuench(player);
            case "tempered_billet" -> playTemperedBillet(player);
            case "yeti_tooth_mark" -> playYetiToothMark(player);
            case "yeti_tooth_spine" -> playYetiToothSpine(player);
            case "steel_falchion_line" -> playSteelFalchionLine(player);
            case "steel_falchion_trace" -> playSteelFalchionTrace(player);
            case "dark_sword_blood_debt" -> playDarkSwordBloodDebt(player);
            case "dark_sword_blood_moon" -> playDarkSwordBloodMoon(player);
            case "lava_katana_brand" -> playLavaKatanaBrand(player);
            case "lava_katana_reverb" -> playLavaKatanaReverb(player);
            case "wicked_kris_venom_ripple" -> playWickedKrisVenomRipple(player);
            case "wicked_kris_nest_burst" -> playWickedKrisNestBurst(player);
            case "dragon_breath_thrust" -> playDragonBreathThrust(player);
            case "dragon_breath_judgement" -> playDragonBreathJudgement(player);
            case "dragontooth_shiv_stab" -> playDragontoothShivStab(player);
            case "dragontooth_shiv_breath" -> playDragontoothShivBreath(player);
            case "iridium_needle_thrust" -> playIridiumNeedleThrust(player);
            case "iridium_needle_frenzy" -> playIridiumNeedleFrenzy(player);
            case "galaxy_dagger_starstab" -> playGalaxyDaggerStarstab(player);
            case "galaxy_dagger_starleap" -> playGalaxyDaggerStarleap(player);
            case "infinity_dagger_singularity_stab" -> playInfinityDaggerSingularityStab(player);
            case "infinity_dagger_singularity_backstab" -> playInfinityDaggerSingularityBackstab(player);
            case "dwarf_rune_guard" -> playDwarfRuneGuard(player);
            case "dwarf_fortress" -> playDwarfFortress(player);
            case "dwarf_dagger_thrust" -> playDwarfDaggerThrust(player);
            case "dwarf_dagger_rush" -> playDwarfDaggerRush(player);
            case "startrail_rift" -> playStartrailRift(player);
            case "galaxy_judgement" -> playGalaxyJudgement(player);
            case "singularity_evolve" -> playSingularityEvolve(player);
            case "eternal_collapse" -> playEternalCollapse(player);
            default -> playGenericSkill(player);
        }
    }

    @SuppressWarnings("null")
    public static void playBurglarShankLoot(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();

        ResourceLocation soundLocation = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "money_dial");
        SoundEvent soundEvent = SoundEvent.createVariableRangeEvent(soundLocation);
        player.playSound(soundEvent, 0.8f, 1.0f);
        player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);

        double baseY = pos.y + player.getBbHeight() * 0.6;
        for (int i = 0; i < 16; i++) {
            double ang = (i / 16.0) * Math.PI * 2.0;
            double r = 0.5 + mc.level.random.nextDouble() * 0.4;
            double px = pos.x + Math.cos(ang) * r;
            double pz = pos.z + Math.sin(ang) * r;
            mc.level.addParticle(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.GOLD_NUGGET)),
                px, baseY + mc.level.random.nextDouble() * 0.4, pz,
                (mc.level.random.nextDouble() - 0.5) * 0.12, 0.08, (mc.level.random.nextDouble() - 0.5) * 0.12);
            if (mc.level.random.nextFloat() < 0.6f) {
                mc.level.addParticle(ParticleTypes.ENCHANT,
                    px, baseY + mc.level.random.nextDouble() * 0.5, pz,
                    0.0, 0.05, 0.0);
            }
        }
    }

    /**
     * 龙牙弯刀 - 龙息突刺
     * 粒子：紫焰+龙息雾
     * 声音：呼啸突刺
     */
    @SuppressWarnings("null")
    private static void playDragonBreathThrust(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();
        Vec3 look = player.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0.0, look.z);
        if (dir.lengthSqr() < 1.0E-4) {
            return;
        }
        dir = dir.normalize();

        Vec3 origin = com.stardew.craft.client.weapon.WeaponSkillAnimationClient.getDragonBreathOrigin();
        Vec3 cachedDir = com.stardew.craft.client.weapon.WeaponSkillAnimationClient.getDragonBreathDir();
        long tick = com.stardew.craft.client.weapon.WeaponSkillAnimationClient.getDragonBreathTick();
        if (origin != null && cachedDir != null && tick > 0) {
            pos = origin;
            dir = cachedDir;
        }

        player.playSound(SoundEvents.DRAGON_FIREBALL_EXPLODE, 0.6f, 1.4f);
        player.playSound(SoundEvents.BLAZE_SHOOT, 0.55f, 1.0f);
        player.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 0.7f, 0.9f);

        double baseY = pos.y + player.getBbHeight() * 0.6;
        for (int i = 0; i < 18; i++) {
            double t = i / 17.0;
            double radius = 0.35 + t * 1.2;
            double spread = (mc.level.random.nextDouble() - 0.5) * 0.8;
            double px = pos.x + dir.x * (1.1 + t * 2.2) + (-dir.z) * spread * radius;
            double pz = pos.z + dir.z * (1.1 + t * 2.2) + dir.x * spread * radius;
            mc.level.addParticle(ParticleTypes.DRAGON_BREATH,
                px, baseY + (mc.level.random.nextDouble() - 0.5) * 0.4, pz,
                dir.x * 0.02, 0.01, dir.z * 0.02);
            if (mc.level.random.nextFloat() < 0.6f) {
                mc.level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                    px, baseY + 0.05, pz,
                    0.0, 0.02, 0.0);
            }
        }
    }

    /**
     * 龙牙弯刀 - 龙息裁决
     * 粒子：紫焰爆裂+扇形火息
     * 声音：低吼+重斩
     */
    @SuppressWarnings("null")
    private static void playDragonBreathJudgement(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();
        Vec3 look = player.getLookAngle();

        player.playSound(SoundEvents.DRAGON_FIREBALL_EXPLODE, 0.9f, 0.85f);
        player.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 0.65f);
        player.playSound(SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 1.0f);
        player.playSound(SoundEvents.WARDEN_SONIC_BOOM, 0.35f, 1.35f);

        double centerY = pos.y + player.getBbHeight() * 0.6;
        double sweep = Math.toRadians(120.0);
        double start = -sweep / 2.0;
        for (int i = 0; i < 48; i++) {
            double t = i / 47.0;
            double angle = start + sweep * t;
            double radius = 2.2 + (mc.level.random.nextDouble() * 1.0);
            double px = pos.x + Math.cos(angle) * radius * look.z + Math.sin(angle) * radius * look.x;
            double pz = pos.z + Math.cos(angle) * radius * -look.x + Math.sin(angle) * radius * look.z;
            mc.level.addParticle(ParticleTypes.DRAGON_BREATH,
                px, centerY + (mc.level.random.nextDouble() - 0.5) * 0.6, pz,
                0.0, 0.03, 0.0);
        }

        double radius = 4.0;
        int areaPoints = 60;
        for (int i = 0; i < areaPoints; i++) {
            double r = radius * Math.sqrt(mc.level.random.nextDouble());
            double ang = (mc.level.random.nextDouble() * Math.PI * 2.0);
            double px = pos.x + Math.cos(ang) * r;
            double pz = pos.z + Math.sin(ang) * r;
            mc.level.addParticle(ParticleTypes.FLAME,
                px, centerY, pz,
                0.0, 0.04, 0.0);
            if (mc.level.random.nextFloat() < 0.6f) {
                mc.level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                    px, centerY + 0.05, pz,
                    0.0, 0.02, 0.0);
            }
            if (mc.level.random.nextFloat() < 0.35f) {
                mc.level.addParticle(ParticleTypes.ENCHANT,
                    px, centerY + (mc.level.random.nextDouble() * 0.5), pz,
                    0.0, 0.05, 0.0);
            }
        }
    }

    /**
     * 龙牙小刀 - 龙息裂刺
     * 粒子：火息+炽焰
     * 声音：短促喷息
     */
    @SuppressWarnings("null")
    private static void playDragontoothShivStab(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();
        Vec3 look = player.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0.0, look.z).normalize();

        player.playSound(SoundEvents.BLAZE_SHOOT, 0.55f, 1.35f);
        player.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 0.6f, 1.05f);

        double baseY = pos.y + player.getBbHeight() * 0.6;
        for (int i = 0; i < 16; i++) {
            double t = i / 15.0;
            double spread = (mc.level.random.nextDouble() - 0.5) * 0.6;
            double px = pos.x + dir.x * (0.9 + t * 1.4) + (-dir.z) * spread;
            double pz = pos.z + dir.z * (0.9 + t * 1.4) + dir.x * spread;
            mc.level.addParticle(ParticleTypes.DRAGON_BREATH,
                px, baseY + (mc.level.random.nextDouble() - 0.5) * 0.25, pz,
                dir.x * 0.02, 0.01, dir.z * 0.02);
            if (mc.level.random.nextFloat() < 0.6f) {
                mc.level.addParticle(ParticleTypes.FLAME,
                    px, baseY + 0.05, pz,
                    0.0, 0.03, 0.0);
            }
        }
    }

    /**
     * 龙牙小刀 - 龙息态
     * 粒子：龙息爆散+炽焰环
     * 声音：龙息爆裂
     */
    @SuppressWarnings("null")
    private static void playDragontoothShivBreath(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();

        player.playSound(SoundEvents.DRAGON_FIREBALL_EXPLODE, 0.7f, 1.2f);
        player.playSound(SoundEvents.FIRECHARGE_USE, 0.6f, 1.1f);

        double baseY = pos.y + player.getBbHeight() * 0.55;
        for (int i = 0; i < 32; i++) {
            double ang = (i / 32.0) * Math.PI * 2.0;
            double r = 0.8 + mc.level.random.nextDouble() * 0.6;
            double px = pos.x + Math.cos(ang) * r;
            double pz = pos.z + Math.sin(ang) * r;
            mc.level.addParticle(ParticleTypes.DRAGON_BREATH,
                px, baseY + mc.level.random.nextDouble() * 0.4, pz,
                0.0, 0.03, 0.0);
            if (mc.level.random.nextFloat() < 0.7f) {
                mc.level.addParticle(ParticleTypes.FLAME,
                    px, baseY + 0.05, pz,
                    0.0, 0.04, 0.0);
            }
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
    private static void playWickedKrisVenomRipple(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();

        player.playSound(SoundEvents.ENCHANTMENT_TABLE_USE, 0.7f, 1.25f);
        player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.6f, 1.55f);

        double centerY = pos.y + player.getBbHeight() * 0.3;
        int points = 28;
        for (int i = 0; i < points; i++) {
            double ang = (Math.PI * 2.0) * (i / (double) points);
            double radius = 2.6 + mc.level.random.nextDouble() * 0.5;
            double x = pos.x + Math.cos(ang) * radius;
            double z = pos.z + Math.sin(ang) * radius;
            mc.level.addParticle(ParticleTypes.WITCH,
                x, centerY + mc.level.random.nextDouble() * 0.2, z,
                0.0, 0.02, 0.0);
            mc.level.addParticle(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0.25f, 0.85f, 0.35f),
                x, centerY + mc.level.random.nextDouble() * 0.2, z,
                0.0, 0.02, 0.0);
        }

        for (int i = 0; i < 14; i++) {
            double offX = (mc.level.random.nextDouble() - 0.5) * 1.4;
            double offZ = (mc.level.random.nextDouble() - 0.5) * 1.4;
            mc.level.addParticle(ParticleTypes.SPORE_BLOSSOM_AIR,
                pos.x + offX, centerY + mc.level.random.nextDouble() * 0.4, pos.z + offZ,
                0.0, 0.02, 0.0);
        }
    }

    @SuppressWarnings("null")
    private static void playWickedKrisNestBurst(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();

        player.playSound(SoundEvents.EVOKER_PREPARE_SUMMON, 0.7f, 1.15f);
        player.playSound(SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.45f, 1.3f);

        double baseY = pos.y + player.getBbHeight() * 0.5;
        for (int i = 0; i < 18; i++) {
            double ang = (Math.PI * 2.0) * (i / 18.0);
            double radius = 1.2 + mc.level.random.nextDouble() * 0.6;
            double x = pos.x + Math.cos(ang) * radius;
            double z = pos.z + Math.sin(ang) * radius;
            mc.level.addParticle(ParticleTypes.WITCH,
                x, baseY + mc.level.random.nextDouble() * 0.3, z,
                0.0, 0.03, 0.0);
            mc.level.addParticle(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0.25f, 0.8f, 0.3f),
                x, baseY + mc.level.random.nextDouble() * 0.3, z,
                0.0, 0.02, 0.0);
        }

        for (int i = 0; i < 10; i++) {
            double offX = (mc.level.random.nextDouble() - 0.5) * 0.8;
            double offZ = (mc.level.random.nextDouble() - 0.5) * 0.8;
            mc.level.addParticle(ParticleTypes.ENCHANT,
                pos.x + offX, baseY + mc.level.random.nextDouble() * 0.4, pos.z + offZ,
                0.0, 0.05, 0.0);
        }
    }

    @SuppressWarnings("null")
    private static void playIronDirkThrust(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();
        Vec3 look = player.getLookAngle();

        player.playSound(SoundEvents.PLAYER_ATTACK_CRIT, 0.8f, 1.35f);
        player.playSound(SoundEvents.ENDERMAN_TELEPORT, 0.5f, 1.6f);
        player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.6f, 1.7f);
        CameraShakeState.kick(0.25f, 3, 1.6f);

        double frontX = pos.x + look.x * 1.2;
        double frontY = pos.y + player.getBbHeight() * 0.55;
        double frontZ = pos.z + look.z * 1.2;

        mc.level.addParticle(ParticleTypes.SWEEP_ATTACK, frontX, frontY, frontZ, 0, 0, 0);

        for (int i = 0; i < 10; i++) {
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.5;
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.25;
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.5;
            mc.level.addParticle(ParticleTypes.CRIT,
                frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                look.x * 0.05, 0.02, look.z * 0.05);
        }

        for (int i = 0; i < 14; i++) {
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.7;
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.35;
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.7;
            mc.level.addParticle(ParticleTypes.END_ROD,
                frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                look.x * 0.03, 0.02, look.z * 0.03);
            if (mc.level.random.nextFloat() < 0.7f) {
                mc.level.addParticle(ParticleTypes.ENCHANT,
                    frontX + offsetX * 0.6, frontY + offsetY, frontZ + offsetZ * 0.6,
                    0.0, 0.03, 0.0);
            }
        }

        for (int i = 0; i < 6; i++) {
            double t = i / 5.0;
            double x = pos.x + look.x * (t * 2.4);
            double y = pos.y + player.getBbHeight() * 0.5 + (mc.level.random.nextDouble() - 0.5) * 0.2;
            double z = pos.z + look.z * (t * 2.4);
            mc.level.addParticle(ParticleTypes.PORTAL,
                x, y, z,
                0.0, 0.01, 0.0);
        }
    }

    @SuppressWarnings("null")
    private static void playShadowDaggerExecute(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();
        Vec3 look = player.getLookAngle();

        player.playSound(SoundEvents.PLAYER_ATTACK_CRIT, 0.8f, 1.45f);
        player.playSound(SoundEvents.WITHER_SHOOT, 0.6f, 1.05f);
        player.playSound(SoundEvents.SOUL_ESCAPE.value(), 0.65f, 1.25f);

        double baseY = pos.y + player.getBbHeight() * 0.6;
        double frontX = pos.x + look.x * 0.8;
        double frontZ = pos.z + look.z * 0.8;
        for (int i = 0; i < 14; i++) {
            double ox = (mc.level.random.nextDouble() - 0.5) * 0.6;
            double oy = (mc.level.random.nextDouble() - 0.5) * 0.4;
            double oz = (mc.level.random.nextDouble() - 0.5) * 0.6;
            mc.level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                frontX + ox, baseY + oy, frontZ + oz,
                0.0, 0.02, 0.0);
            if (mc.level.random.nextFloat() < 0.7f) {
                mc.level.addParticle(ParticleTypes.SMOKE,
                    frontX + ox * 0.8, baseY + oy * 0.8, frontZ + oz * 0.8,
                    0.0, 0.01, 0.0);
            }
            if (mc.level.random.nextFloat() < 0.5f) {
                mc.level.addParticle(ParticleTypes.ENCHANT,
                    frontX + ox, baseY + oy, frontZ + oz,
                    0.0, 0.04, 0.0);
            }
        }
    }

    @SuppressWarnings("null")
    private static void playCrystalDaggerLayer(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();
        Vec3 look = player.getLookAngle();

        player.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 1.25f);
        CameraShakeState.kick(0.15f, 2, 1.4f);

        double frontX = pos.x + look.x * 1.1;
        double frontY = pos.y + player.getBbHeight() * 0.55;
        double frontZ = pos.z + look.z * 1.1;

        mc.level.addParticle(ParticleTypes.SWEEP_ATTACK, frontX, frontY, frontZ, 0, 0, 0);
        for (int i = 0; i < 10; i++) {
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.4;
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.3;
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.4;
            mc.level.addParticle(ParticleTypes.END_ROD,
                frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                look.x * 0.02, 0.01, look.z * 0.02);
            if (mc.level.random.nextFloat() < 0.6f) {
                mc.level.addParticle(ParticleTypes.ENCHANT,
                    frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                    0.0, 0.03, 0.0);
            }
        }
    }

    @SuppressWarnings("null")
    private static void playCrystalDaggerBurst(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();

        player.playSound(SoundEvents.AMETHYST_BLOCK_BREAK, 0.85f, 1.2f);
        player.playSound(SoundEvents.PLAYER_ATTACK_CRIT, 0.8f, 1.1f);
        CameraShakeState.kick(0.35f, 4, 1.8f);

        double baseY = pos.y + player.getBbHeight() * 0.6;
        for (int i = 0; i < 18; i++) {
            double ang = (i / 18.0) * Math.PI * 2.0;
            double r = 0.35 + mc.level.random.nextDouble() * 0.5;
            double px = pos.x + Math.cos(ang) * r;
            double pz = pos.z + Math.sin(ang) * r;
            mc.level.addParticle(ParticleTypes.END_ROD,
                px, baseY + mc.level.random.nextDouble() * 0.4, pz,
                0.0, 0.03, 0.0);
            if (mc.level.random.nextFloat() < 0.7f) {
                mc.level.addParticle(ParticleTypes.CRIT,
                    px, baseY + mc.level.random.nextDouble() * 0.35, pz,
                    0.0, 0.04, 0.0);
            }
        }
    }

    @SuppressWarnings("null")
    private static void playGalaxyDaggerStarstab(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();
        Vec3 look = player.getLookAngle();

        player.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.75f, 1.25f);
        player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.55f, 1.5f);
        player.playSound(SoundEvents.ENCHANTMENT_TABLE_USE, 0.45f, 1.4f);

        double frontX = pos.x + look.x * 1.1;
        double frontY = pos.y + player.getBbHeight() * 0.55;
        double frontZ = pos.z + look.z * 1.1;

        mc.level.addParticle(ParticleTypes.SWEEP_ATTACK, frontX, frontY, frontZ, 0, 0, 0);

        for (int i = 0; i < 12; i++) {
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.5;
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.3;
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.5;
            mc.level.addParticle(ParticleTypes.END_ROD,
                frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                look.x * 0.02, 0.01, look.z * 0.02);
            if (mc.level.random.nextFloat() < 0.6f) {
                mc.level.addParticle(ParticleTypes.ENCHANT,
                    frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                    0.0, 0.03, 0.0);
            }
            if (mc.level.random.nextFloat() < 0.4f) {
                mc.level.addParticle(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0.55f, 0.35f, 1.0f),
                    frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                    0.0, 0.02, 0.0);
            }
        }

        for (int i = 0; i < 14; i++) {
            double ang = (Math.PI * 2.0) * (i / 14.0);
            double radius = 0.5 + mc.level.random.nextDouble() * 0.2;
            double x = frontX + Math.cos(ang) * radius;
            double z = frontZ + Math.sin(ang) * radius;
            mc.level.addParticle(ParticleTypes.ENCHANT,
                x, frontY + (mc.level.random.nextDouble() - 0.5) * 0.2, z,
                0.0, 0.03, 0.0);
        }
    }

    @SuppressWarnings("null")
    private static void playGalaxyDaggerStarleap(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();
        Vec3 look = player.getLookAngle();

        player.playSound(SoundEvents.ENDERMAN_TELEPORT, 0.5f, 1.4f);
        player.playSound(SoundEvents.PLAYER_ATTACK_CRIT, 0.7f, 1.2f);
        player.playSound(SoundEvents.AMETHYST_BLOCK_BREAK, 0.55f, 1.5f);
        CameraShakeState.kick(0.2f, 3, 1.5f);

        double frontX = pos.x + look.x * 0.8;
        double frontY = pos.y + player.getBbHeight() * 0.6;
        double frontZ = pos.z + look.z * 0.8;

        for (int i = 0; i < 10; i++) {
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.5;
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.35;
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.5;
            mc.level.addParticle(ParticleTypes.PORTAL,
                frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                0.0, 0.02, 0.0);
        }

        for (int i = 0; i < 12; i++) {
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.6;
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.3;
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.6;
            mc.level.addParticle(ParticleTypes.ENCHANT,
                frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                0.0, 0.04, 0.0);
            if (mc.level.random.nextFloat() < 0.5f) {
                mc.level.addParticle(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0.7f, 0.5f, 1.0f),
                    frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                    0.0, 0.03, 0.0);
            }
        }

        for (int i = 0; i < 10; i++) {
            double ang = (Math.PI * 2.0) * (i / 10.0);
            double radius = 0.35 + mc.level.random.nextDouble() * 0.25;
            double x = frontX + Math.cos(ang) * radius;
            double z = frontZ + Math.sin(ang) * radius;
            mc.level.addParticle(ParticleTypes.END_ROD,
                x, frontY + (mc.level.random.nextDouble() - 0.5) * 0.2, z,
                0.0, 0.03, 0.0);
        }
    }

    @SuppressWarnings("null")
    private static void playInfinityDaggerSingularityStab(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();
        Vec3 look = player.getLookAngle();

        player.playSound(SoundEvents.END_PORTAL_FRAME_FILL, 0.45f, 0.8f);
        player.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 0.95f);
        player.playSound(SoundEvents.ENCHANTMENT_TABLE_USE, 0.4f, 0.8f);

        double frontX = pos.x + look.x * 1.1;
        double frontY = pos.y + player.getBbHeight() * 0.55;
        double frontZ = pos.z + look.z * 1.1;

        mc.level.addParticle(ParticleTypes.SWEEP_ATTACK, frontX, frontY, frontZ, 0, 0, 0);

        for (int i = 0; i < 12; i++) {
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.5;
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.3;
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.5;
            mc.level.addParticle(ParticleTypes.PORTAL,
                frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                0.0, 0.02, 0.0);
            if (mc.level.random.nextFloat() < 0.5f) {
                mc.level.addParticle(ParticleTypes.END_ROD,
                    frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                    0.0, 0.03, 0.0);
            }
            if (mc.level.random.nextFloat() < 0.4f) {
                mc.level.addParticle(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 1.0f, 0.78f, 0.35f),
                    frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                    0.0, 0.02, 0.0);
            }
        }

        for (int i = 0; i < 12; i++) {
            double ang = (Math.PI * 2.0) * (i / 12.0);
            double radius = 0.45 + mc.level.random.nextDouble() * 0.2;
            double x = frontX + Math.cos(ang) * radius;
            double z = frontZ + Math.sin(ang) * radius;
            mc.level.addParticle(ParticleTypes.PORTAL,
                x, frontY + (mc.level.random.nextDouble() - 0.5) * 0.2, z,
                0.0, 0.02, 0.0);
        }
    }

    @SuppressWarnings("null")
    private static void playInfinityDaggerSingularityBackstab(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();
        Vec3 look = player.getLookAngle();

        player.playSound(SoundEvents.END_PORTAL_SPAWN, 0.35f, 0.75f);
        player.playSound(SoundEvents.PLAYER_ATTACK_CRIT, 0.7f, 0.9f);
        player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.45f, 0.75f);
        CameraShakeState.kick(0.25f, 3, 1.3f);

        double frontX = pos.x + look.x * 0.8;
        double frontY = pos.y + player.getBbHeight() * 0.6;
        double frontZ = pos.z + look.z * 0.8;

        for (int i = 0; i < 14; i++) {
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.6;
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.4;
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.6;
            mc.level.addParticle(ParticleTypes.PORTAL,
                frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                0.0, 0.02, 0.0);
            if (mc.level.random.nextFloat() < 0.6f) {
                mc.level.addParticle(ParticleTypes.CRIT,
                    frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                    0.0, 0.04, 0.0);
            }
            if (mc.level.random.nextFloat() < 0.35f) {
                mc.level.addParticle(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 1.0f, 0.72f, 0.3f),
                    frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                    0.0, 0.02, 0.0);
            }
        }

        for (int i = 0; i < 10; i++) {
            double ang = (Math.PI * 2.0) * (i / 10.0);
            double radius = 0.4 + mc.level.random.nextDouble() * 0.25;
            double x = frontX + Math.cos(ang) * radius;
            double z = frontZ + Math.sin(ang) * radius;
            mc.level.addParticle(ParticleTypes.END_ROD,
                x, frontY + (mc.level.random.nextDouble() - 0.5) * 0.2, z,
                0.0, 0.03, 0.0);
        }
    }

    @SuppressWarnings("null")
    private static void playBurglarShankSlash(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();
        Vec3 look = player.getLookAngle();

        player.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 0.9f, 1.15f);
        player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.6f, 1.6f);
        CameraShakeState.kick(0.22f, 3, 1.4f);

        double frontX = pos.x + look.x * 1.2;
        double frontY = pos.y + player.getBbHeight() * 0.6;
        double frontZ = pos.z + look.z * 1.2;

        mc.level.addParticle(ParticleTypes.SWEEP_ATTACK, frontX, frontY, frontZ, 0, 0, 0);

        for (int i = 0; i < 12; i++) {
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.6;
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.3;
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.6;
            mc.level.addParticle(ParticleTypes.CRIT,
                frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                look.x * 0.05, 0.02, look.z * 0.05);
            if (mc.level.random.nextFloat() < 0.5f) {
                mc.level.addParticle(ParticleTypes.ENCHANT,
                    frontX + offsetX * 0.6, frontY + offsetY, frontZ + offsetZ * 0.6,
                    0.0, 0.03, 0.0);
            }
        }

        for (int i = 0; i < 6; i++) {
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.5;
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.3;
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.5;
            mc.level.addParticle(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.GOLD_NUGGET)),
                frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                (mc.level.random.nextDouble() - 0.5) * 0.06, 0.04, (mc.level.random.nextDouble() - 0.5) * 0.06);
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
     * 熔岩武士刀 - 熔潮回鸣
     * 粒子：热浪脉冲
     * 声音：低沉热能共振
     */
    @SuppressWarnings("null")
    private static void playLavaKatanaReverb(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();

        player.playSound(SoundEvents.FIRE_AMBIENT, 0.9f, 0.8f);
        player.playSound(SoundEvents.BLAZE_AMBIENT, 0.7f, 0.9f);
        player.playSound(SoundEvents.BLAZE_SHOOT, 0.8f, 0.7f);
        player.playSound(SoundEvents.GENERIC_EXPLODE.value(), 0.5f, 1.3f);

        double centerY = pos.y + player.getBbHeight() * 0.6;
        for (int i = 0; i < 20; i++) {
            double angle = (i / 12.0) * Math.PI * 2;
            double radius = 1.1;
            double px = pos.x + Math.cos(angle) * radius;
            double pz = pos.z + Math.sin(angle) * radius;
            mc.level.addParticle(ParticleTypes.FLAME,
                px, centerY + (mc.level.random.nextDouble() - 0.5) * 0.3, pz,
                0.0, 0.02, 0.0);
        }
        for (int i = 0; i < 12; i++) {
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 1.2;
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 1.2;
            mc.level.addParticle(ParticleTypes.LAVA,
                pos.x + offsetX, centerY + 0.1, pos.z + offsetZ,
                0.0, 0.03, 0.0);
        }
        mc.level.addParticle(ParticleTypes.SMOKE,
            pos.x, centerY, pos.z,
            0.0, 0.04, 0.0);
    }

    /**
     * 矮人剑 - 符文回能护斩
     * 粒子：符文火花 + 震击
     * 声音：护斩与金属共鸣
     */
    @SuppressWarnings("null")
    private static void playDwarfRuneGuard(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();
        Vec3 look = player.getLookAngle();

        player.playSound(SoundEvents.SHIELD_BLOCK, 0.7f, 1.2f);
        player.playSound(SoundEvents.ANVIL_LAND, 0.5f, 1.4f);
        player.playSound(SoundEvents.ENCHANTMENT_TABLE_USE, 0.4f, 1.0f);

        double frontX = pos.x + look.x * 1.4;
        double frontY = pos.y + player.getBbHeight() * 0.6;
        double frontZ = pos.z + look.z * 1.4;

        for (int i = 0; i < 16; i++) {
            double ox = (mc.level.random.nextDouble() - 0.5) * 0.6;
            double oy = (mc.level.random.nextDouble() - 0.5) * 0.4;
            double oz = (mc.level.random.nextDouble() - 0.5) * 0.6;
            mc.level.addParticle(ParticleTypes.ENCHANT,
                frontX + ox, frontY + oy, frontZ + oz,
                0.0, 0.05, 0.0);
        }
        for (int i = 0; i < 8; i++) {
            double ox = (mc.level.random.nextDouble() - 0.5) * 0.5;
            double oy = (mc.level.random.nextDouble() - 0.5) * 0.3;
            double oz = (mc.level.random.nextDouble() - 0.5) * 0.5;
            mc.level.addParticle(ParticleTypes.CRIT,
                frontX + ox, frontY + oy, frontZ + oz,
                look.x * 0.1, 0.02, look.z * 0.1);
        }
    }

    /**
     * 矮人剑 - 地脉堡垒（启动）
     * 粒子：地脉震荡与尘土
     * 声音：厚重冲击
     */
    @SuppressWarnings("null")
    private static void playDwarfFortress(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();

        player.playSound(SoundEvents.ANVIL_LAND, 0.9f, 0.7f);
        player.playSound(SoundEvents.GENERIC_EXPLODE.value(), 0.6f, 0.9f);

        double centerY = pos.y + 0.1;
        for (int i = 0; i < 22; i++) {
            double ox = (mc.level.random.nextDouble() - 0.5) * 1.6;
            double oz = (mc.level.random.nextDouble() - 0.5) * 1.6;
            mc.level.addParticle(ParticleTypes.SMOKE,
                pos.x + ox, centerY, pos.z + oz,
                0.0, 0.02, 0.0);
        }
        for (int i = 0; i < 14; i++) {
            double ox = (mc.level.random.nextDouble() - 0.5) * 1.2;
            double oz = (mc.level.random.nextDouble() - 0.5) * 1.2;
            mc.level.addParticle(ParticleTypes.CRIT,
                pos.x + ox, centerY + 0.15, pos.z + oz,
                0.0, 0.04, 0.0);
        }
    }

    /**
     * 矮人匕首 - 符文突刺
     * 粒子：符文火花 + 迅捷尾迹
     * 声音：突刺破风 + 符文共鸣
     */
    @SuppressWarnings("null")
    private static void playDwarfDaggerThrust(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();
        Vec3 look = player.getLookAngle();

        player.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 1.4f);
        player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.5f, 1.6f);

        double centerY = pos.y + player.getBbHeight() * 0.55;
        for (int i = 0; i < 16; i++) {
            double t = i / 15.0;
            double px = pos.x + look.x * (t * 8.0);
            double pz = pos.z + look.z * (t * 8.0);
            double jitterX = (mc.level.random.nextDouble() - 0.5) * 0.4;
            double jitterZ = (mc.level.random.nextDouble() - 0.5) * 0.4;
            mc.level.addParticle(ParticleTypes.ENCHANT,
                px + jitterX, centerY + (mc.level.random.nextDouble() - 0.5) * 0.2, pz + jitterZ,
                0.0, 0.04, 0.0);
            if (mc.level.random.nextFloat() < 0.7f) {
                mc.level.addParticle(ParticleTypes.CRIT,
                    px + jitterX * 0.7, centerY, pz + jitterZ * 0.7,
                    look.x * 0.12, 0.02, look.z * 0.12);
            }
        }
    }

    /**
     * 矮人匕首 - 地脉疾行
     * 粒子：地脉光环 + 符文脉冲
     * 声音：地脉唤醒
     */
    @SuppressWarnings("null")
    private static void playDwarfDaggerRush(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();

        player.playSound(SoundEvents.ENCHANTMENT_TABLE_USE, 0.7f, 1.1f);
        player.playSound(SoundEvents.BEACON_POWER_SELECT, 0.6f, 1.2f);

        double centerY = pos.y + player.getBbHeight() * 0.55;
        for (int i = 0; i < 24; i++) {
            double ang = (i / 24.0) * Math.PI * 2.0;
            double radius = 1.1 + mc.level.random.nextDouble() * 0.4;
            double px = pos.x + Math.cos(ang) * radius;
            double pz = pos.z + Math.sin(ang) * radius;
            mc.level.addParticle(ParticleTypes.ENCHANT,
                px, centerY + (mc.level.random.nextDouble() - 0.5) * 0.2, pz,
                0.0, 0.03, 0.0);
        }

        for (int i = 0; i < 10; i++) {
            double ox = (mc.level.random.nextDouble() - 0.5) * 1.6;
            double oz = (mc.level.random.nextDouble() - 0.5) * 1.6;
            mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                pos.x + ox, centerY, pos.z + oz,
                0.0, 0.02, 0.0);
        }
    }

    /**
     * 铱针 - 三针连斩
     * 粒子：紫晶闪点 + 细长流光
     * 声音：晶体清响 + 迅捷挥斩
     */
    @SuppressWarnings("null")
    private static void playIridiumNeedleThrust(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();
        Vec3 look = player.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0.0, look.z);
        if (dir.lengthSqr() < 1.0E-4) {
            return;
        }
        dir = dir.normalize();

        player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.9f, 1.8f);
        player.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 1.2f);
        player.playSound(SoundEvents.EVOKER_CAST_SPELL, 0.45f, 1.6f);

        double baseY = pos.y + player.getBbHeight() * 0.55;
        for (int i = 0; i < 28; i++) {
            double t = i / 27.0;
            double reach = 0.7 + t * 2.8;
            double px = pos.x + dir.x * reach;
            double pz = pos.z + dir.z * reach;
            double jitterX = (mc.level.random.nextDouble() - 0.5) * 0.5;
            double jitterZ = (mc.level.random.nextDouble() - 0.5) * 0.5;
            mc.level.addParticle(ParticleTypes.END_ROD,
                px + jitterX, baseY + (mc.level.random.nextDouble() - 0.5) * 0.25, pz + jitterZ,
                dir.x * 0.04, 0.02, dir.z * 0.04);
            if (mc.level.random.nextFloat() < 0.85f) {
                mc.level.addParticle(ParticleTypes.ENCHANT,
                    px + jitterX * 0.7, baseY + 0.02, pz + jitterZ * 0.7,
                    0.0, 0.05, 0.0);
            }
            if (mc.level.random.nextFloat() < 0.7f) {
                mc.level.addParticle(ParticleTypes.PORTAL,
                    px + jitterX * 0.9, baseY + 0.08, pz + jitterZ * 0.9,
                    -dir.x * 0.07, 0.03, -dir.z * 0.07);
            }
        }
        for (int i = 0; i < 10; i++) {
            double ox = (mc.level.random.nextDouble() - 0.5) * 0.8;
            double oz = (mc.level.random.nextDouble() - 0.5) * 0.8;
            mc.level.addParticle(ParticleTypes.CRIT,
                pos.x + dir.x * 1.2 + ox, baseY + 0.05, pos.z + dir.z * 1.2 + oz,
                dir.x * 0.2, 0.08, dir.z * 0.2);
        }
    }

    /**
     * 铱针 - 铱辉狂热
     * 粒子：紫辉环 + 细密星屑
     * 声音：奥术共鸣 + 晶体脉冲
     */
    @SuppressWarnings("null")
    private static void playIridiumNeedleFrenzy(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();

        player.playSound(SoundEvents.ENCHANTMENT_TABLE_USE, 1.0f, 1.3f);
        player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.95f, 1.5f);
        player.playSound(SoundEvents.BEACON_POWER_SELECT, 0.6f, 1.4f);

        double centerY = pos.y + player.getBbHeight() * 0.55;
        for (int i = 0; i < 40; i++) {
            double ang = (i / 40.0) * Math.PI * 2.0;
            double radius = 1.2 + mc.level.random.nextDouble() * 0.7;
            double px = pos.x + Math.cos(ang) * radius;
            double pz = pos.z + Math.sin(ang) * radius;
            mc.level.addParticle(ParticleTypes.PORTAL,
                px, centerY + (mc.level.random.nextDouble() - 0.5) * 0.4, pz,
                0.0, 0.06, 0.0);
        }

        for (int i = 0; i < 24; i++) {
            double ox = (mc.level.random.nextDouble() - 0.5) * 1.8;
            double oz = (mc.level.random.nextDouble() - 0.5) * 1.8;
            mc.level.addParticle(ParticleTypes.ENCHANT,
                pos.x + ox, centerY + (mc.level.random.nextDouble() - 0.5) * 0.45, pos.z + oz,
                0.0, 0.08, 0.0);
        }

        for (int i = 0; i < 18; i++) {
            double ox = (mc.level.random.nextDouble() - 0.5) * 1.3;
            double oz = (mc.level.random.nextDouble() - 0.5) * 1.3;
            mc.level.addParticle(ParticleTypes.END_ROD,
                pos.x + ox, centerY + 0.1 + mc.level.random.nextDouble() * 0.45, pos.z + oz,
                0.0, 0.03, 0.0);
        }

        for (int i = 0; i < 12; i++) {
            double ox = (mc.level.random.nextDouble() - 0.5) * 1.1;
            double oz = (mc.level.random.nextDouble() - 0.5) * 1.1;
            mc.level.addParticle(ParticleTypes.CRIT,
                pos.x + ox, centerY + 0.05 + mc.level.random.nextDouble() * 0.3, pos.z + oz,
                0.0, 0.08, 0.0);
        }
    }

    /**
     * 钢脊之怒 - 厚重反震
     * strong=true: 粒子与声音更强；strong=false: 更轻微
     */
    @SuppressWarnings("null")
    public static void playSteelSpineFury(Player player, boolean strong) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();
        Vec3 look = player.getLookAngle();

        if (strong) {
            player.playSound(SoundEvents.ANVIL_HIT, 0.6f, 1.1f);
            player.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 0.8f, 0.9f);
        } else {
            player.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.6f, 1.2f);
            player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.3f, 1.7f);
        }

        double frontX = pos.x + look.x * 1.4;
        double frontY = pos.y + player.getBbHeight() * 0.6;
        double frontZ = pos.z + look.z * 1.4;

        int critCount = strong ? 8 : 4;
        int sparkCount = strong ? 6 : 3;

        for (int i = 0; i < critCount; i++) {
            @SuppressWarnings("null")
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.5;
            @SuppressWarnings("null")
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.3;
            @SuppressWarnings("null")
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.5;
            mc.level.addParticle(ParticleTypes.CRIT,
                frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                look.x * 0.2, 0.08, look.z * 0.2);
        }

        for (int i = 0; i < sparkCount; i++) {
            @SuppressWarnings("null")
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.4;
            @SuppressWarnings("null")
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.4;
            mc.level.addParticle(ParticleTypes.ENCHANT,
                frontX + offsetX, frontY, frontZ + offsetZ,
                0, 0.03, 0);
        }
    }

    /**
     * 钢脊之怒 - 进入姿态
     * 粒子：灰银光点围绕身体
     * 声音：金属低鸣 + 轻微晶体音
     */
    @SuppressWarnings("null")
    public static void playSteelSpineFuryEnter(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();

        player.playSound(SoundEvents.ANVIL_LAND, 0.4f, 1.1f);
        player.playSound(SoundEvents.CHAIN_PLACE, 0.3f, 1.2f);

        double centerY = pos.y + player.getBbHeight() * 0.55;
        for (int i = 0; i < 8; i++) {
            double angle = (i / 8.0) * Math.PI * 2;
            double radius = 0.7;
            double px = pos.x + Math.cos(angle) * radius;
            double pz = pos.z + Math.sin(angle) * radius;
            mc.level.addParticle(ParticleTypes.ENCHANT,
                px, centerY + (mc.level.random.nextDouble() - 0.5) * 0.4, pz,
                0, 0.02, 0);
        }
        mc.level.addParticle(ParticleTypes.SMOKE,
            pos.x, centerY, pos.z,
            0, 0.01, 0);
    }

    /**
     * 钢脊之怒 - 受击“叮”反馈
     */
    @SuppressWarnings("null")
    public static void playSteelSpineFuryHit(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();

        player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.1f, 1.8f);

        double centerY = pos.y + player.getBbHeight() * 0.6;
        for (int i = 0; i < 8; i++) {
            @SuppressWarnings("null")
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.5;
            @SuppressWarnings("null")
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.3;
            @SuppressWarnings("null")
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.5;
            mc.level.addParticle(ParticleTypes.ENCHANT,
                pos.x + offsetX, centerY + offsetY, pos.z + offsetZ,
                0, 0.03, 0);
        }
    }

    /**
     * 破伤风 - 锈蚀、毒素感
     * 粒子：棕绿色烟雾 + 小型爆裂
     * 声音：金属刮擦 + 低沉撞击
     */
    @SuppressWarnings("null")
    private static void playTetanusStrike(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();
        Vec3 look = player.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0.0, look.z);
        if (dir.lengthSqr() < 1.0E-4) {
            dir = look;
        }
        dir = dir.normalize();
        Vec3 right = new Vec3(-dir.z, 0.0, dir.x).normalize();
        
        // 声音：金属刮擦感
        player.playSound(SoundEvents.GRINDSTONE_USE, 0.5f, 0.7f);
        player.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 0.8f, 0.9f);
        player.playSound(SoundEvents.CHAIN_PLACE, 0.35f, 0.8f);
        player.playSound(SoundEvents.SHIELD_BREAK, 0.25f, 0.75f);
        
        // 粒子：在前方生成锈蚀色烟雾
        double frontX = pos.x + dir.x * 1.5;
        double frontY = pos.y + player.getBbHeight() * 0.6;
        double frontZ = pos.z + dir.z * 1.5;
        
        for (int i = 0; i < 6; i++) {
            @SuppressWarnings("null")
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.6;
            @SuppressWarnings("null")
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.4;
            @SuppressWarnings("null")
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.6;
            
            // 烟雾粒子（棕色调）
            mc.level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                look.x * 0.05, 0.02, look.z * 0.05);
        }

        for (int i = 0; i < 4; i++) {
            @SuppressWarnings("null")
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.45;
            @SuppressWarnings("null")
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.2;
            @SuppressWarnings("null")
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.45;
            mc.level.addParticle(ParticleTypes.ASH,
                frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                0.0, 0.01, 0.0);
        }

        // 斩击弧线：脏绿毒雾 + 金属碎屑
        for (int i = 0; i < 10; i++) {
            float t = i / 9.0f;
            double arc = (t - 0.5) * 1.2;
            double forward = 0.65 + t * 0.8;
            double px = pos.x + dir.x * forward + right.x * arc;
            double pz = pos.z + dir.z * forward + right.z * arc;
            double py = frontY + (mc.level.random.nextDouble() - 0.5) * 0.12;

            if (mc.level.random.nextFloat() < 0.7f) {
                mc.level.addParticle(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.RAW_IRON)),
                    px, py, pz,
                    dir.x * 0.06, 0.05, dir.z * 0.06);
            }
        }

        for (int i = 0; i < 6; i++) {
            double jitterX = (mc.level.random.nextDouble() - 0.5) * 0.55;
            double jitterZ = (mc.level.random.nextDouble() - 0.5) * 0.55;
            mc.level.addParticle(ParticleTypes.LARGE_SMOKE,
                frontX + jitterX, pos.y + 0.1, frontZ + jitterZ,
                0.0, 0.02, 0.0);
        }
        
        // 少量金属火花
        for (int i = 0; i < 3; i++) {
            @SuppressWarnings("null")
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.3;
            @SuppressWarnings("null")
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.3;
            mc.level.addParticle(ParticleTypes.CRIT,
                frontX + offsetX, frontY, frontZ + offsetZ,
                look.x * 0.2, 0.1, look.z * 0.2);
        }
    }

    /**
     * 轻剑反击 - 优雅、闪避感
     * 粒子：轻盈的白色闪光
     * 声音：剑刃出鞘 + 轻响
     */
    @SuppressWarnings("null")
    private static void playLightCounter(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();
        
        // 声音：剑刃清脆音
        player.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 1.3f);
        player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.4f, 1.5f);
        
        // 粒子：环绕玩家的防御光芒
        double centerY = pos.y + player.getBbHeight() * 0.5;
        
        for (int i = 0; i < 8; i++) {
            double angle = (i / 8.0) * Math.PI * 2;
            double radius = 0.8;
            double px = pos.x + Math.cos(angle) * radius;
            double pz = pos.z + Math.sin(angle) * radius;
            
            // 白色闪光粒子
            mc.level.addParticle(ParticleTypes.END_ROD,
                px, centerY + (mc.level.random.nextDouble() - 0.5) * 0.5, pz,
                0, 0.03, 0);
        }
        
        // 中心闪光
        mc.level.addParticle(ParticleTypes.FLASH,
            pos.x, centerY, pos.z,
            0, 0, 0);
    }

    @SuppressWarnings("null")
    private static void playMeowmereShot(Player player, boolean major) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();
        float volume = major ? 0.5f : 0.8f;
        float pitch = major ? 1.2f : 1.4f;
        player.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), volume, pitch);
        player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, volume * 0.7f, pitch + 0.2f);

        double centerY = pos.y + player.getBbHeight() * 0.6;
        int sparkle = major ? 8 : 4;
        for (int i = 0; i < sparkle; i++) {
            double angle = (i / (double) sparkle) * Math.PI * 2.0;
            double radius = major ? 0.8 : 0.6;
            double px = pos.x + Math.cos(angle) * radius;
            double pz = pos.z + Math.sin(angle) * radius;
            @SuppressWarnings("null")
            double py = centerY + (mc.level.random.nextDouble() - 0.5) * 0.2;
            mc.level.addParticle(ParticleTypes.END_ROD,
                px, py, pz,
                0, 0.01, 0);
        }
        for (int i = 0; i < sparkle; i++) {
            @SuppressWarnings("null")
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.4;
            @SuppressWarnings("null")
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.2;
            @SuppressWarnings("null")
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.4;
            mc.level.addParticle(ParticleTypes.ENCHANT,
                pos.x + offsetX, centerY + offsetY, pos.z + offsetZ,
                0, 0.01, 0);
        }
    }

        @SuppressWarnings("null")
        private static void playObsidianResonance(Player player) {
            Minecraft mc = Minecraft.getInstance();
            Vec3 pos = player.position();

            player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.5f, 1.3f);

            double centerY = pos.y + player.getBbHeight() * 0.6;
            for (int i = 0; i < 6; i++) {
                double angle = (i / 6.0) * Math.PI * 2;
                double radius = 0.5;
                double px = pos.x + Math.cos(angle) * radius;
                double pz = pos.z + Math.sin(angle) * radius;
                mc.level.addParticle(ParticleTypes.ENCHANT,
                    px, centerY + (mc.level.random.nextDouble() - 0.5) * 0.2, pz,
                    0, 0.02, 0);
            }
        }

        @SuppressWarnings("null")
        private static void playObsidianCrack(Player player) {
            Minecraft mc = Minecraft.getInstance();
            Vec3 pos = player.position();
            Vec3 look = player.getLookAngle();

            player.playSound(SoundEvents.ANVIL_LAND, 0.6f, 0.8f);
            player.playSound(SoundEvents.GLASS_BREAK, 0.5f, 1.1f);

            double frontX = pos.x + look.x * 1.3;
            double frontY = pos.y + player.getBbHeight() * 0.55;
            double frontZ = pos.z + look.z * 1.3;

            for (int i = 0; i < 10; i++) {
                double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.6;
                double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.2;
                double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.6;
                mc.level.addParticle(ParticleTypes.SMOKE,
                    frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                    0, 0.02, 0);
            }

            for (int i = 0; i < 6; i++) {
                double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.4;
                double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.4;
                mc.level.addParticle(ParticleTypes.CRIT,
                    frontX + offsetX, frontY, frontZ + offsetZ,
                    look.x * 0.08, 0.05, look.z * 0.08);
            }

            mc.level.addParticle(ParticleTypes.FLASH, frontX, frontY + 0.1, frontZ, 0, 0, 0);
        }

        @SuppressWarnings("null")
        private static void playOssifiedMark(Player player) {
            Minecraft mc = Minecraft.getInstance();
            Vec3 pos = player.position();
            Vec3 look = player.getLookAngle();

            player.playSound(SoundEvents.BONE_BLOCK_PLACE, 0.95f, 1.02f);
            player.playSound(SoundEvents.SOUL_ESCAPE.value(), 0.85f, 0.9f);

            double frontX = pos.x + look.x * 1.2;
            double frontY = pos.y + player.getBbHeight() * 0.55;
            double frontZ = pos.z + look.z * 1.2;

            for (int i = 0; i < 10; i++) {
                double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.5;
                double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.3;
                double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.5;
                mc.level.addParticle(ParticleTypes.ASH,
                    frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                    0, 0.02, 0);
            }

            for (int i = 0; i < 6; i++) {
                double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.4;
                double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.2;
                double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.4;
                mc.level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                    frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                    0, 0.01, 0);
            }
        }

        @SuppressWarnings("null")
        private static void playOssifiedExecution(Player player) {
            Minecraft mc = Minecraft.getInstance();
            Vec3 pos = player.position();

            player.playSound(SoundEvents.BONE_BLOCK_BREAK, 1.2f, 0.9f);
            player.playSound(SoundEvents.SOUL_ESCAPE.value(), 1.1f, 0.8f);
            player.playSound(SoundEvents.ANVIL_LAND, 0.7f, 0.7f);

            double centerY = pos.y + 0.05;
            for (int i = 0; i < 12; i++) {
                double angle = (i / 12.0) * Math.PI * 2.0;
                double radius = 0.8;
                double px = pos.x + Math.cos(angle) * radius;
                double pz = pos.z + Math.sin(angle) * radius;
                mc.level.addParticle(ParticleTypes.SOUL,
                    px, centerY, pz,
                    0, 0.02, 0);
                mc.level.addParticle(ParticleTypes.ASH,
                    px, centerY, pz,
                    0, 0.02, 0);
            }
        }

    /**
     * 潮汐印记 - 轻微水纹与铃音
     */
    @SuppressWarnings("null")
    private static void playTideMark(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();

        player.playSound(SoundEvents.TRIDENT_THROW.value(), 0.6f, 1.2f);
        player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.35f, 1.7f);
        player.playSound(SoundEvents.FISHING_BOBBER_SPLASH, 0.4f, 1.3f);

        double centerY = pos.y + player.getBbHeight() * 0.6;
        for (int i = 0; i < 8; i++) {
            double angle = (i / 8.0) * Math.PI * 2;
            double radius = 0.6;
            double px = pos.x + Math.cos(angle) * radius;
            double pz = pos.z + Math.sin(angle) * radius;
            mc.level.addParticle(ParticleTypes.BUBBLE,
                px, centerY + (mc.level.random.nextDouble() - 0.5) * 0.3, pz,
                0, 0.01, 0);
        }

        for (int i = 0; i < 6; i++) {
            @SuppressWarnings("null")
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.5;
            @SuppressWarnings("null")
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.25;
            @SuppressWarnings("null")
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.5;
            mc.level.addParticle(ParticleTypes.ENCHANT,
                pos.x + offsetX, centerY + offsetY, pos.z + offsetZ,
                0, 0.02, 0);
        }
        for (int i = 0; i < 4; i++) {
            @SuppressWarnings("null")
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.4;
            @SuppressWarnings("null")
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.2;
            @SuppressWarnings("null")
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.4;
            mc.level.addParticle(ParticleTypes.SPLASH,
                pos.x + offsetX, centerY + offsetY, pos.z + offsetZ,
                0, 0.02, 0);
        }
    }

    /**
     * 残破的三叉戟 - 鱼获试刺
     */
    @SuppressWarnings("null")
    private static void playFishcatchThrust(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();
        Vec3 look = player.getLookAngle();

        player.playSound(SoundEvents.TRIDENT_THROW.value(), 0.55f, 1.35f);
        player.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.5f, 1.15f);
        player.playSound(SoundEvents.FISHING_BOBBER_SPLASH, 0.5f, 1.5f);
        CameraShakeState.kick(0.16f, 3, 1.1f);

        double frontX = pos.x + look.x * 0.9;
        double frontY = pos.y + player.getBbHeight() * 0.6;
        double frontZ = pos.z + look.z * 0.9;

        for (int i = 0; i < 12; i++) {
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.6;
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.3;
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.6;
            mc.level.addParticle(ParticleTypes.SPLASH,
                frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                look.x * 0.02, 0.02, look.z * 0.02);
        }
        for (int i = 0; i < 8; i++) {
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.5;
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.25;
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.5;
            mc.level.addParticle(ParticleTypes.BUBBLE,
                frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                0.0, 0.02, 0.0);
        }
        for (int i = 0; i < 6; i++) {
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.4;
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.2;
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.4;
            mc.level.addParticle(ParticleTypes.ENCHANT,
                frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                0.0, 0.03, 0.0);
        }
    }

    /**
     * 残破的三叉戟 - 鱼获状态触发提示
     */
    @SuppressWarnings("null")
    public static void playFishcatchReady(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();

        player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.55f, 1.65f);
        player.playSound(SoundEvents.FISHING_BOBBER_SPLASH, 0.6f, 1.35f);
        player.playSound(SoundEvents.TRIDENT_THROW.value(), 0.4f, 1.55f);
        CameraShakeState.kick(0.2f, 4, 1.2f);

        double centerY = pos.y + player.getBbHeight() * 0.6;
        for (int i = 0; i < 14; i++) {
            double angle = (i / 14.0) * Math.PI * 2.0;
            double radius = 0.6 + (mc.level.random.nextDouble() * 0.35);
            double px = pos.x + Math.cos(angle) * radius;
            double pz = pos.z + Math.sin(angle) * radius;
            mc.level.addParticle(ParticleTypes.BUBBLE,
                px, centerY + (mc.level.random.nextDouble() - 0.5) * 0.25, pz,
                0.0, 0.02, 0.0);
            if (mc.level.random.nextFloat() < 0.7f) {
                mc.level.addParticle(ParticleTypes.SPLASH,
                    px, centerY + 0.05, pz,
                    0.0, 0.03, 0.0);
            }
        }

        for (int i = 0; i < 8; i++) {
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.5;
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.25;
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.5;
            mc.level.addParticle(ParticleTypes.ENCHANT,
                pos.x + offsetX, centerY + offsetY, pos.z + offsetZ,
                0.0, 0.04, 0.0);
        }

        mc.level.addParticle(ParticleTypes.FLASH, pos.x, centerY + 0.05, pos.z, 0, 0, 0);
    }

    /**
     * 残破的三叉戟 - 渔潮回钩
     */
    @SuppressWarnings("null")
    private static void playTideReel(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();
        Vec3 look = player.getLookAngle();

        player.playSound(SoundEvents.TRIDENT_RIPTIDE_2.value(), 0.7f, 0.95f);
        player.playSound(SoundEvents.TRIDENT_HIT, 0.7f, 1.1f);
        player.playSound(SoundEvents.FISHING_BOBBER_SPLASH, 0.7f, 1.2f);
        CameraShakeState.kick(0.28f, 4, 1.6f);

        double frontX = pos.x + look.x * 1.0;
        double frontY = pos.y + player.getBbHeight() * 0.6;
        double frontZ = pos.z + look.z * 1.0;

        for (int i = 0; i < 18; i++) {
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.9;
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.35;
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.9;
            mc.level.addParticle(ParticleTypes.SPLASH,
                frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                0.0, 0.03, 0.0);
        }
        for (int i = 0; i < 10; i++) {
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.7;
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.3;
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.7;
            mc.level.addParticle(ParticleTypes.BUBBLE,
                frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                0.0, 0.02, 0.0);
        }
        for (int i = 0; i < 8; i++) {
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.6;
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.25;
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.6;
            mc.level.addParticle(ParticleTypes.CLOUD,
                frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                0.0, 0.01, 0.0);
        }
    }

    /**
     * 潮汐锚 - 重击水波
     */
    @SuppressWarnings("null")
    private static void playTideAnchor(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();
        Vec3 look = player.getLookAngle();

        player.playSound(SoundEvents.TRIDENT_RIPTIDE_1.value(), 0.7f, 0.85f);
        player.playSound(SoundEvents.TRIDENT_THROW.value(), 0.6f, 0.9f);
        player.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 0.7f, 0.8f);

        double frontX = pos.x + look.x * 1.2;
        double frontY = pos.y + player.getBbHeight() * 0.6;
        double frontZ = pos.z + look.z * 1.2;

        for (int i = 0; i < 16; i++) {
            @SuppressWarnings("null")
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.8;
            @SuppressWarnings("null")
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.3;
            @SuppressWarnings("null")
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.8;
            mc.level.addParticle(ParticleTypes.SPLASH,
                frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                0, 0.02, 0);
        }
        for (int i = 0; i < 6; i++) {
            @SuppressWarnings("null")
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.6;
            @SuppressWarnings("null")
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.25;
            @SuppressWarnings("null")
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.6;
            mc.level.addParticle(ParticleTypes.CLOUD,
                frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                0, 0.01, 0);
        }
        for (int i = 0; i < 6; i++) {
            @SuppressWarnings("null")
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.5;
            @SuppressWarnings("null")
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.25;
            @SuppressWarnings("null")
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.5;
            mc.level.addParticle(ParticleTypes.CRIT,
                frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                look.x * 0.15, 0.06, look.z * 0.15);
        }
    }

    /**
     * 圣堂之刃 - 誓约反斩（架势）
     */
    @SuppressWarnings("null")
    private static void playTemplarVow(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();

        player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f, 1.6f);
        player.playSound(SoundEvents.SHIELD_BLOCK, 0.5f, 1.2f);

        double centerY = pos.y + player.getBbHeight() * 0.6;
        for (int i = 0; i < 10; i++) {
            double angle = (i / 10.0) * Math.PI * 2.0;
            double radius = 0.7;
            double px = pos.x + Math.cos(angle) * radius;
            double pz = pos.z + Math.sin(angle) * radius;
            mc.level.addParticle(ParticleTypes.END_ROD,
                px, centerY + (mc.level.random.nextDouble() - 0.5) * 0.3, pz,
                0, 0.02, 0);
        }
    }

    /**
     * 圣堂之刃 - 圣堂裁决（施放）
     */
    @SuppressWarnings("null")
    private static void playTemplarJudgement(Player player) {
        player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.5f, 1.7f);
    }

    /**
     * 昆虫头部 - 复眼架势
     * 粒子：淡绿光点环绕 + 轻微“锁定”闪光
     * 声音：微弱蜂鸣 + 清脆晶体音
     */
    @SuppressWarnings("null")
    private static void playInsectEyeStance(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();

        player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.6f, 1.6f);
        player.playSound(SoundEvents.BEEHIVE_WORK, 0.4f, 1.3f);

        double centerY = pos.y + player.getBbHeight() * 0.6;
        for (int i = 0; i < 10; i++) {
            double angle = (i / 10.0) * Math.PI * 2.0;
            double radius = 0.65;
            double px = pos.x + Math.cos(angle) * radius;
            double pz = pos.z + Math.sin(angle) * radius;
            mc.level.addParticle(ParticleTypes.ENCHANT,
                px, centerY + (mc.level.random.nextDouble() - 0.5) * 0.25, pz,
                0, 0.02, 0);
        }
    }

    /**
     * 精灵之刃 - 月露萤刃
     */
    @SuppressWarnings("null")
    private static void playElfBladeLeaf(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();

        player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.6f, 1.6f);
        player.playSound(SoundEvents.BEEHIVE_WORK, 0.35f, 1.4f);

        double centerY = pos.y + player.getBbHeight() * 0.6;
        for (int i = 0; i < 10; i++) {
            double angle = (i / 10.0) * Math.PI * 2.0;
            double radius = 0.7;
            double px = pos.x + Math.cos(angle) * radius;
            double pz = pos.z + Math.sin(angle) * radius;
            mc.level.addParticle(ParticleTypes.END_ROD,
                px, centerY + (mc.level.random.nextDouble() - 0.5) * 0.2, pz,
                0, 0.02, 0);
        }

        for (int i = 0; i < 8; i++) {
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.6;
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.3;
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.6;
            mc.level.addParticle(ParticleTypes.ENCHANT,
                pos.x + offsetX, centerY + offsetY, pos.z + offsetZ,
                0, 0.02, 0);
        }

        mc.level.addParticle(ParticleTypes.FLASH,
            pos.x, centerY, pos.z,
            0, 0, 0);
    }

    /**
     * 昆虫头部 - 甲翼疾掠
     * 粒子：前方斩击弧 + 速度残影
     * 声音：翼振 + 扫击
     */
    @SuppressWarnings("null")
    private static void playInsectDash(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();
        Vec3 look = player.getLookAngle();

        player.playSound(SoundEvents.PHANTOM_FLAP, 0.7f, 1.2f);
        player.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 1.1f);

        double frontX = pos.x + look.x * 1.2;
        double frontY = pos.y + player.getBbHeight() * 0.6;
        double frontZ = pos.z + look.z * 1.2;

        mc.level.addParticle(ParticleTypes.SWEEP_ATTACK, frontX, frontY, frontZ, 0, 0, 0);

        for (int i = 0; i < 8; i++) {
            @SuppressWarnings("null")
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.6;
            @SuppressWarnings("null")
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.25;
            @SuppressWarnings("null")
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.6;
            mc.level.addParticle(ParticleTypes.CRIT,
                frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                look.x * 0.15, 0.06, look.z * 0.15);
        }

        for (int i = 0; i < 6; i++) {
            @SuppressWarnings("null")
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.4;
            @SuppressWarnings("null")
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.4;
            mc.level.addParticle(ParticleTypes.CLOUD,
                pos.x + offsetX, frontY, pos.z + offsetZ,
                0, 0.02, 0);
        }
    }

    /**
     * 双刃大剑 - 回刃折返
     * 粒子：前方小范围扫击弧 + 冲击火花
     * 声音：重型挥击 + 金属钝击
     */
    @SuppressWarnings("null")
    private static void playClaymoreFoldback(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();
        Vec3 look = player.getLookAngle();

        player.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 0.8f);
        player.playSound(SoundEvents.ANVIL_HIT, 0.4f, 1.1f);

        double frontX = pos.x + look.x * 1.4;
        double frontY = pos.y + player.getBbHeight() * 0.6;
        double frontZ = pos.z + look.z * 1.4;

        mc.level.addParticle(ParticleTypes.SWEEP_ATTACK, frontX, frontY, frontZ, 0, 0, 0);

        for (int i = 0; i < 6; i++) {
            @SuppressWarnings("null")
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.5;
            @SuppressWarnings("null")
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.3;
            @SuppressWarnings("null")
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.5;
            mc.level.addParticle(ParticleTypes.CRIT,
                frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                look.x * 0.15, 0.06, look.z * 0.15);
        }
    }

    /**
     * 树木庇佑 - 自然、治愈感
     * 粒子：绿色叶片 + 柔和光芒
     * 声音：树叶沙沙 + 治愈音
     */
    @SuppressWarnings("null")
    private static void playTreeBlessing(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();
        
        // 声音：自然治愈感
        player.playSound(SoundEvents.AZALEA_LEAVES_PLACE, 0.8f, 1.0f);
        player.playSound(SoundEvents.PLAYER_LEVELUP, 0.3f, 1.5f);
        
        // 粒子：从脚下升起的绿色粒子
        for (int i = 0; i < 10; i++) {
            @SuppressWarnings("null")
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.8;
            @SuppressWarnings("null")
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.8;
            @SuppressWarnings("null")
            double startY = pos.y + mc.level.random.nextDouble() * 0.3;
            
            // 绿色叶片效果
            mc.level.addParticle(ParticleTypes.COMPOSTER,
                pos.x + offsetX, startY, pos.z + offsetZ,
                0, 0.08 + mc.level.random.nextDouble() * 0.04, 0);
        }
        
        // 少量金色光点（庇护感）
        for (int i = 0; i < 4; i++) {
            @SuppressWarnings("null")
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.5;
            @SuppressWarnings("null")
            double offsetY = mc.level.random.nextDouble() * player.getBbHeight();
            @SuppressWarnings("null")
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.5;
            
            mc.level.addParticle(ParticleTypes.HAPPY_VILLAGER,
                pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ,
                0, 0.02, 0);
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

    /**
     * 亡命掠夺 - 海盗的鲁莽与贪婪
     * 粒子：血红色飞溅 + 金币闪光
     * 声音：狂野挥砍 + 金币叮当
     */
    @SuppressWarnings("null")
    private static void playDesperatePlunder(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();
        Vec3 look = player.getLookAngle();
        
        // 声音：狂野的海盗挥砍
        player.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 0.8f);
        player.playSound(SoundEvents.PLAYER_HURT, 0.4f, 1.2f); // 自伤的代价
        player.playSound(SoundEvents.AMETHYST_BLOCK_HIT, 0.5f, 0.6f); // 金属/宝藏音
        
        // 粒子位置：前方
        double frontX = pos.x + look.x * 1.5;
        double frontY = pos.y + player.getBbHeight() * 0.6;
        double frontZ = pos.z + look.z * 1.5;
        
        // 血红色伤害粒子（代表自伤代价）
        for (int i = 0; i < 5; i++) {
            @SuppressWarnings("null")
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.4;
            @SuppressWarnings("null")
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.3;
            @SuppressWarnings("null")
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.4;
            
            mc.level.addParticle(ParticleTypes.DAMAGE_INDICATOR,
                pos.x + offsetX, pos.y + player.getBbHeight() * 0.5 + offsetY, pos.z + offsetZ,
                0, 0.1, 0);
        }
        
        // 金色闪光粒子（海盗掠夺的宝藏感）
        for (int i = 0; i < 4; i++) {
            @SuppressWarnings("null")
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.6;
            @SuppressWarnings("null")
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.6;
            
            mc.level.addParticle(ParticleTypes.WAX_ON, // 金色闪光
                frontX + offsetX, frontY + mc.level.random.nextDouble() * 0.3, frontZ + offsetZ,
                look.x * 0.1, 0.05, look.z * 0.1);
        }
        
        // 挥砍特效
        mc.level.addParticle(ParticleTypes.SWEEP_ATTACK,
            frontX, frontY, frontZ,
            0, 0, 0);
    }


    /**
     * 银纹折返 - 冷冽银光与回锋
     * 粒子：银蓝弧光 + 轻微闪光
     * 声音：利落挥砍 + 轻微金属清响
     */
    @SuppressWarnings("null")
    private static void playSilverFoldback(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();
        Vec3 look = player.getLookAngle();

        player.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 1.15f);
        player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.3f, 1.6f);

        double frontX = pos.x + look.x * 1.3;
        double frontY = pos.y + player.getBbHeight() * 0.6;
        double frontZ = pos.z + look.z * 1.3;

        for (int i = 0; i < 5; i++) {
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.5;
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.3;
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.5;
            mc.level.addParticle(ParticleTypes.END_ROD,
                frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                0, 0.02, 0);
        }

        mc.level.addParticle(ParticleTypes.SWEEP_ATTACK,
            frontX, frontY, frontZ,
            0, 0, 0);
    }

    /**
     * 圣辉惩戒 - 圣光斩击
     * 粒子：明亮扫击 + 金色光点
     * 声音：清脆挥砍 + 圣光回响
     */
    @SuppressWarnings("null")
    private static void playHolySmite(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();
        Vec3 look = player.getLookAngle();

        player.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 1.25f);
        player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.5f, 1.9f);

        double centerX = pos.x + look.x * 1.4;
        double centerY = pos.y + player.getBbHeight() * 0.6;
        double centerZ = pos.z + look.z * 1.4;

        mc.level.addParticle(ParticleTypes.SWEEP_ATTACK,
            centerX, centerY, centerZ,
            0, 0, 0);

        for (int i = 0; i < 8; i++) {
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.6;
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.3;
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.6;
            mc.level.addParticle(ParticleTypes.END_ROD,
                centerX + offsetX, centerY + offsetY, centerZ + offsetZ,
                0, 0.02, 0);
        }
    }

    /**
     * 晨曦圣域 - 圣域展开
     * 粒子：环形圣光 + 轻微光尘
     * 声音：钟鸣 + 圣能低吟
     */
    @SuppressWarnings("null")
    private static void playHolyDomain(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();

        player.playSound(SoundEvents.BELL_RESONATE, 0.8f, 1.2f);
        player.playSound(SoundEvents.ENCHANTMENT_TABLE_USE, 0.7f, 1.4f);

        double centerY = pos.y + 0.2;
        for (int i = 0; i < 14; i++) {
            double angle = (i / 14.0) * Math.PI * 2;
            double radius = 0.8;
            double px = pos.x + Math.cos(angle) * radius;
            double pz = pos.z + Math.sin(angle) * radius;
            mc.level.addParticle(ParticleTypes.END_ROD,
                px, centerY, pz,
                0, 0.02, 0);
        }

        for (int i = 0; i < 10; i++) {
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.9;
            double offsetY = mc.level.random.nextDouble() * 0.6;
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.9;
            mc.level.addParticle(ParticleTypes.INSTANT_EFFECT,
                pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ,
                0, 0.01, 0);
        }
    }

    /**
     * 回炉淬火 - 热锻斩击
     * 粒子：火花 + 小火焰
     * 声音：铁砧敲击 + 火焰噼啪
     */
    @SuppressWarnings("null")
    private static void playTemperedQuench(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();
        Vec3 look = player.getLookAngle();

        player.playSound(SoundEvents.ANVIL_LAND, 0.7f, 1.1f);
        player.playSound(SoundEvents.FIRECHARGE_USE, 0.6f, 1.2f);

        double frontX = pos.x + look.x * 1.2;
        double frontY = pos.y + player.getBbHeight() * 0.6;
        double frontZ = pos.z + look.z * 1.2;

        mc.level.addParticle(ParticleTypes.SWEEP_ATTACK,
            frontX, frontY, frontZ,
            0, 0, 0);

        for (int i = 0; i < 8; i++) {
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.45;
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.25;
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.45;
            mc.level.addParticle(ParticleTypes.CRIT,
                frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                look.x * 0.08, 0.02, look.z * 0.08);
        }

        for (int i = 0; i < 6; i++) {
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.4;
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.25;
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.4;
            mc.level.addParticle(ParticleTypes.FLAME,
                frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                look.x * 0.02, 0.01, look.z * 0.02);
        }
    }

    /**
     * 熔锻飞坯 - 三枚铁坯喷射
     * 粒子：火星环绕 + 熔热光点
     * 声音：沉重锻击 + 强力挥斩
     */
    @SuppressWarnings("null")
    private static void playTemperedBillet(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();

        player.playSound(SoundEvents.ANVIL_LAND, 0.9f, 0.9f);
        player.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 0.8f, 0.85f);

        double centerY = pos.y + player.getBbHeight() * 0.6;
        for (int i = 0; i < 12; i++) {
            double angle = (i / 12.0) * Math.PI * 2;
            double radius = 0.9;
            double px = pos.x + Math.cos(angle) * radius;
            double pz = pos.z + Math.sin(angle) * radius;
            mc.level.addParticle(ParticleTypes.FLAME,
                px, centerY + (mc.level.random.nextDouble() - 0.5) * 0.3, pz,
                0, 0.02, 0);
        }

        for (int i = 0; i < 10; i++) {
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.8;
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.4;
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.8;
            mc.level.addParticle(ParticleTypes.LAVA,
                pos.x + offsetX, centerY + offsetY, pos.z + offsetZ,
                0, 0.01, 0);
        }
    }

    @SuppressWarnings("null")
    private static void playBoneFracture(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();
        Vec3 look = player.getLookAngle();

        player.playSound(SoundEvents.SKELETON_HURT, 0.7f, 0.9f);
        player.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 1.1f);

        double frontX = pos.x + look.x * 1.1;
        double frontY = pos.y + player.getBbHeight() * 0.6;
        double frontZ = pos.z + look.z * 1.1;

        for (int i = 0; i < 6; i++) {
            @SuppressWarnings("null")
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.4;
            @SuppressWarnings("null")
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.3;
            @SuppressWarnings("null")
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.4;
            mc.level.addParticle(ParticleTypes.CRIT,
                frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                look.x * 0.08, 0.02, look.z * 0.08);
        }

        @SuppressWarnings("null")
        ItemParticleOption bone = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.BONE));
        for (int i = 0; i < 4; i++) {
            @SuppressWarnings("null")
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.3;
            @SuppressWarnings("null")
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.3;
            @SuppressWarnings("null")
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.3;
            mc.level.addParticle(bone,
                frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                0.0, 0.02, 0.0);
        }
    }

    @SuppressWarnings("null")
    private static void playYetiToothMark(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();
        Vec3 look = player.getLookAngle();

        player.playSound(SoundEvents.GLASS_PLACE, 0.5f, 1.4f);
        player.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.6f, 1.1f);

        double frontX = pos.x + look.x * 1.1;
        double frontY = pos.y + player.getBbHeight() * 0.6;
        double frontZ = pos.z + look.z * 1.1;

        for (int i = 0; i < 10; i++) {
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.5;
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.3;
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.5;
            mc.level.addParticle(ParticleTypes.SNOWFLAKE,
                frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                0.0, 0.01, 0.0);
        }
    }

    @SuppressWarnings("null")
    private static void playYetiToothSpine(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();
        Vec3 look = player.getLookAngle();

        player.playSound(SoundEvents.GLASS_BREAK, 0.5f, 1.2f);
        player.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 0.95f);

        double frontX = pos.x + look.x * 1.2;
        double frontY = pos.y + 0.2;
        double frontZ = pos.z + look.z * 1.2;

        for (int i = 0; i < 12; i++) {
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.8;
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.2;
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.8;
            mc.level.addParticle(ParticleTypes.SNOWFLAKE,
                frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                0.0, 0.01, 0.0);
        }
    }

    @SuppressWarnings("null")
    private static void playStartrailRift(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();
        Vec3 look = player.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0.0, look.z).normalize();

        player.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 1.25f);
        player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f, 1.4f);
        player.playSound(SoundEvents.END_PORTAL_FRAME_FILL, 0.35f, 1.6f);
        CameraShakeState.kick(0.35f, 4, 2.0f);

        double frontX = pos.x + look.x * 1.6;
        double frontY = pos.y + player.getBbHeight() * 0.6;
        double frontZ = pos.z + look.z * 1.6;

        mc.level.addParticle(ParticleTypes.SWEEP_ATTACK, frontX, frontY, frontZ, 0, 0, 0);
        for (int i = 0; i < 10; i++) {
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.7;
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.35;
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.7;
            mc.level.addParticle(ParticleTypes.CRIT,
                frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                0.0, 0.02, 0.0);
        }
        for (int i = 0; i < 16; i++) {
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.8;
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.4;
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.8;
            mc.level.addParticle(ParticleTypes.END_ROD,
                frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                look.x * 0.03, 0.02, look.z * 0.03);
            if (mc.level.random.nextFloat() < 0.6f) {
                mc.level.addParticle(ParticleTypes.ENCHANT,
                    frontX + offsetX * 0.6, frontY + offsetY, frontZ + offsetZ * 0.6,
                    0.0, 0.03, 0.0);
            }
        }

        // 星轨拖尾：沿突进方向生成星尘段
        for (double d = 0.4; d <= 3.2; d += 0.4) {
            double px = pos.x + dir.x * d;
            double pz = pos.z + dir.z * d;
            double py = pos.y + player.getBbHeight() * 0.45 + (mc.level.random.nextDouble() - 0.5) * 0.2;
            mc.level.addParticle(ParticleTypes.END_ROD, px, py, pz,
                dir.x * 0.02, 0.02, dir.z * 0.02);
            mc.level.addParticle(ParticleTypes.CRIT, px, py, pz,
                0.0, 0.01, 0.0);
            if (mc.level.random.nextFloat() < 0.5f) {
                mc.level.addParticle(ParticleTypes.ENCHANT, px, py, pz,
                    0.0, 0.02, 0.0);
            }
        }
    }

    @SuppressWarnings("null")
    private static void playGalaxyJudgement(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();

        player.playSound(SoundEvents.END_PORTAL_SPAWN, 0.9f, 1.05f);
        player.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 0.7f);
        player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.6f, 1.3f);
        player.playSound(SoundEvents.WARDEN_SONIC_BOOM, 0.35f, 1.1f);
        CameraShakeState.kick(0.6f, 6, 3.5f);

        double centerY = pos.y + player.getBbHeight() * 0.6;
        mc.level.addParticle(ParticleTypes.FLASH, pos.x, centerY + 0.1, pos.z, 0.0, 0.0, 0.0);
        for (int i = 0; i < 36; i++) {
            double ang = (i / 36.0) * Math.PI * 2.0;
            double radius = 3.6 + (mc.level.random.nextDouble() * 0.6);
            double px = pos.x + Math.cos(ang) * radius;
            double pz = pos.z + Math.sin(ang) * radius;
            mc.level.addParticle(ParticleTypes.END_ROD,
                px, centerY + (mc.level.random.nextDouble() - 0.5) * 0.6, pz,
                0.0, 0.02, 0.0);
            if (mc.level.random.nextFloat() < 0.7f) {
                mc.level.addParticle(ParticleTypes.ENCHANT,
                    px, centerY + (mc.level.random.nextDouble() * 0.6), pz,
                    0.0, 0.03, 0.0);
            }
        }

        // 星爆冲击：向外散射火花
        for (int i = 0; i < 48; i++) {
            double ang = mc.level.random.nextDouble() * Math.PI * 2.0;
            double spd = 0.06 + mc.level.random.nextDouble() * 0.08;
            double vx = Math.cos(ang) * spd;
            double vz = Math.sin(ang) * spd;
            mc.level.addParticle(ParticleTypes.CRIT,
                pos.x, centerY + 0.2, pos.z,
                vx, 0.03, vz);
            if (mc.level.random.nextFloat() < 0.5f) {
                mc.level.addParticle(ParticleTypes.END_ROD,
                    pos.x, centerY + 0.15, pos.z,
                    vx * 0.6, 0.02, vz * 0.6);
            }
        }
    }

    @SuppressWarnings("null")
    private static void playSingularityEvolve(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();

        player.playSound(SoundEvents.ENDERMAN_TELEPORT, 0.8f, 0.9f);
        player.playSound(SoundEvents.PORTAL_TRAVEL, 0.7f, 1.1f);
        player.playSound(SoundEvents.SOUL_ESCAPE.value(), 0.35f, 1.3f);
        CameraShakeState.kick(0.45f, 6, 2.5f);

        double centerY = pos.y + player.getBbHeight() * 0.5;
        for (int i = 0; i < 28; i++) {
            double ang = (i / 28.0) * Math.PI * 2.0;
            double radius = 2.6 + (mc.level.random.nextDouble() * 0.4);
            double px = pos.x + Math.cos(ang) * radius;
            double pz = pos.z + Math.sin(ang) * radius;
            mc.level.addParticle(ParticleTypes.PORTAL,
                px, centerY + (mc.level.random.nextDouble() - 0.5) * 0.6, pz,
                0.0, 0.02, 0.0);
            if (mc.level.random.nextFloat() < 0.5f) {
                mc.level.addParticle(ParticleTypes.ENCHANT,
                    px, centerY + (mc.level.random.nextDouble() * 0.6), pz,
                    0.0, 0.03, 0.0);
            }
        }

        // 旋涡拉扯：带切向速度的环形粒子
        for (int i = 0; i < 40; i++) {
            double ang = (i / 40.0) * Math.PI * 2.0;
            double radius = 1.8 + (mc.level.random.nextDouble() * 0.6);
            double px = pos.x + Math.cos(ang) * radius;
            double pz = pos.z + Math.sin(ang) * radius;
            double tx = -Math.sin(ang) * 0.05;
            double tz = Math.cos(ang) * 0.05;
            mc.level.addParticle(ParticleTypes.END_ROD,
                px, centerY + (mc.level.random.nextDouble() - 0.5) * 0.3, pz,
                tx, 0.02, tz);
        }
    }

    @SuppressWarnings("null")
    private static void playEternalCollapse(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();

        player.playSound(SoundEvents.WITHER_SPAWN, 0.6f, 1.2f);
        player.playSound(SoundEvents.END_PORTAL_SPAWN, 0.7f, 0.9f);
        player.playSound(SoundEvents.GENERIC_EXPLODE.value(), 0.5f, 0.8f);
        CameraShakeState.kick(0.55f, 7, 3.0f);

        double centerY = pos.y + player.getBbHeight() * 0.55;
        for (int i = 0; i < 24; i++) {
            double ang = (i / 24.0) * Math.PI * 2.0;
            double radius = 3.2 + (mc.level.random.nextDouble() * 0.4);
            double px = pos.x + Math.cos(ang) * radius;
            double pz = pos.z + Math.sin(ang) * radius;
            mc.level.addParticle(ParticleTypes.PORTAL,
                px, centerY + (mc.level.random.nextDouble() - 0.5) * 0.6, pz,
                0.0, 0.03, 0.0);
            if (mc.level.random.nextFloat() < 0.4f) {
                mc.level.addParticle(ParticleTypes.SMOKE,
                    px, centerY + (mc.level.random.nextDouble() * 0.4), pz,
                    0.0, 0.02, 0.0);
            }
        }

        // 内吸粒子：向中心收束
        for (int i = 0; i < 36; i++) {
            double ang = mc.level.random.nextDouble() * Math.PI * 2.0;
            double radius = 2.2 + mc.level.random.nextDouble() * 1.0;
            double px = pos.x + Math.cos(ang) * radius;
            double pz = pos.z + Math.sin(ang) * radius;
            double vx = (pos.x - px) * 0.06;
            double vz = (pos.z - pz) * 0.06;
            mc.level.addParticle(ParticleTypes.END_ROD,
                px, centerY + (mc.level.random.nextDouble() - 0.5) * 0.4, pz,
                vx, 0.02, vz);
            if (mc.level.random.nextFloat() < 0.5f) {
                mc.level.addParticle(ParticleTypes.ENCHANT,
                    px, centerY + (mc.level.random.nextDouble() - 0.5) * 0.4, pz,
                    vx * 0.6, 0.01, vz * 0.6);
            }
        }
    }

    @SuppressWarnings("null")
    private static void playSteelFalchionLine(Player player) {
        player.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 1.4f);
        player.playSound(SoundEvents.IRON_TRAPDOOR_CLOSE, 0.35f, 1.2f);
    }

    @SuppressWarnings("null")
    private static void playSteelFalchionTrace(Player player) {
        player.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 1.1f);
        player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.25f, 0.9f);
    }

    @SuppressWarnings("null")
    private static void playDarkSwordBloodDebt(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();
        Vec3 look = player.getLookAngle();

        player.playSound(SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.6f, 0.9f);
        player.playSound(SoundEvents.WITHER_AMBIENT, 0.5f, 1.2f);

        double frontX = pos.x + look.x * 1.1;
        double frontY = pos.y + player.getBbHeight() * 0.6;
        double frontZ = pos.z + look.z * 1.1;

        for (int i = 0; i < 10; i++) {
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.6;
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.4;
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.6;
            mc.level.addParticle(ParticleTypes.SMOKE,
                frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                0.0, 0.02, 0.0);
        }

        for (int i = 0; i < 6; i++) {
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.4;
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.3;
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.4;
            mc.level.addParticle(ParticleTypes.DRIPPING_OBSIDIAN_TEAR,
                frontX + offsetX, frontY + offsetY, frontZ + offsetZ,
                0.0, 0.01, 0.0);
        }
    }

    @SuppressWarnings("null")
    private static void playDarkSwordBloodMoon(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = player.position();

        player.playSound(SoundEvents.WITHER_AMBIENT, 0.8f, 0.8f);
        player.playSound(SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.4f, 1.4f);

        double centerY = pos.y + player.getBbHeight() * 0.5;
        for (int i = 0; i < 16; i++) {
            double angle = (i / 16.0) * Math.PI * 2;
            double radius = 0.9;
            double px = pos.x + Math.cos(angle) * radius;
            double pz = pos.z + Math.sin(angle) * radius;
            mc.level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                px, centerY + (mc.level.random.nextDouble() - 0.5) * 0.3, pz,
                0.0, 0.02, 0.0);
        }

        for (int i = 0; i < 12; i++) {
            double offsetX = (mc.level.random.nextDouble() - 0.5) * 0.8;
            double offsetY = (mc.level.random.nextDouble() - 0.5) * 0.4;
            double offsetZ = (mc.level.random.nextDouble() - 0.5) * 0.8;
            mc.level.addParticle(ParticleTypes.SMOKE,
                pos.x + offsetX, centerY + offsetY, pos.z + offsetZ,
                0.0, 0.01, 0.0);
        }
    }
}

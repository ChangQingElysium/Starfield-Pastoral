package com.stardew.craft.client.weapon;

import com.stardew.craft.Config;
import com.stardew.craft.StardewCraft;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/** Confirmed target feedback: a readable dark edge, a brief solid core and outward fragments. */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class WeaponTargetImpactClient {
    public enum Style {
        CUTLASS_CUT(.64f,6,false,false),CRESCENT_SLASH(1f,8,false,false),FALCHION_CUT(.67f,6,false,false),FALCHION_DOT(.4f,5,false,false),FALCHION_BURST(.87f,7,false,false),
        RUST_CUT(.57f,6,false,false),RUST_STRIKE(.82f,8,false,false),WOOD_CUT(.57f,6,false,false),WOOD_BLESS(.84f,7,false,false),
        LIGHT_CUT(.58f,6,false,false),LIGHT_COUNTER(.8f,6,false,false),SPINE_CUT(.7f,6,false,false),SPINE_STRIKE(1.05f,8,false,false),SPINE_WEAK(.77f,7,false,false),
        PIRATE_CUT(.62f,6,false,false), PIRATE_PLUNDER(.95f,8,false,false),
        SILVER_CUT(.6f,6,false,false), SILVER_OUT(.8f,6,false,false), SILVER_RETURN(.82f,6,false,false),
        IRON_CUT(.43f,6,false,false), IRON_THRUST(.75f,7,false,false),
        WIND_CUT(.53f,6,false,false), WIND_THRUST(.7f,6,false,false),
        BONE_SWORD_CUT(.6f, 6, false, false), BONE_FRACTURE(.95f, 8, false, false),
        CLAYMORE_CUT(.72f, 6, false, false), CLAYMORE_OUT(.95f, 7, false, false), CLAYMORE_RETURN(.85f, 7, false, false),
        DWARF_SWORD_CUT(.66f, 6, false, false), DWARF_DAGGER_CUT(.46f, 6, false, false),
        DWARF_GUARD(.92f, 6, false, false), DWARF_THRUST(.68f, 6, false, false), DWARF_SHOCK(.55f, 7, false, false),
        NEEDLE_CUT(.45f, 6, false, false), NEEDLE_STRIKE(.56f, 5, false, false), NEEDLE_FINAL(.8f, 6, false, false),
        NEEDLE_FRENZY(.55f, 6, false, false), BURGLAR_CUT(.46f, 6, false, false), BURGLAR_STRIKE(.8f, 7, false, false),
        SHADOW_CUT(.46f, 6, false, false), SHADOW_EXECUTE(.78f, 6, false, false), SHADOW_FINISH(.8f, 8, false, false),
        INSECT_CUT(.6f, 6, false, false), INSECT_EYE(.68f, 6, false, false), INSECT_DASH(.83f, 7, false, false),
        CRYSTAL_CUT(.46f,6,false,false), CRYSTAL_LAYER(.72f,6,false,false), CRYSTAL_BURST(.8f,8,false,false),
        VENOM_CUT(.48f,6,false,false), VENOM_RIPPLE(.6f,7,false,false), VENOM_NEST(.8f,6,false,false),
        VENOM_DOT(.22f,5,false,false), VENOM_BURST(.9f,9,false,false),
        DARK_CUT(0.64f, 6, false, false), DARK_DEBT(1.05f, 6, false, false), DARK_BURST(1.05f, 8, false, false),
        FORGE_CUT(0.68f, 6, false, false), FORGE_QUENCH(1.1f, 6, false, false), FORGE_BLAST(0.85f, 8, false, false),
        FORGE_BILLET(0.85f, 6, false, false), FORGE_RING(0.38f, 5, false, false),
        OBSIDIAN_CUT(0.62f, 6, false, false), OBSIDIAN_RESONANCE(0.95f, 6, false, false), OBSIDIAN_CRACK(1.15f, 11, false, false),
        OSSIFIED_CUT(0.6f, 6, false, false), OSSIFIED_BONUS(1.0f, 6, false, false), OSSIFIED_PULSE(0.8f, 9, false, false),
        MEOW_CUT(0.6f, 7, false, false), MEOW_PROJECTILE(0.85f, 9, false, false),
        FOREST_CUT(0.62f, 7, false, false), FROST_CUT(0.65f, 7, false, false),
        GALAXY_CUT(0.6f, 7, false, false), INFINITY_CUT(0.62f, 7, false, false),
        TIDE_CUT(0.62f, 7, false, false), DRAGON_CUT(0.65f, 7, false, true),
        HOLY_CUT(0.65f, 6, false, false), TEMPLAR_CUT(0.7f, 6, false, false),
        HOLY_SMITE(1.15f, 6, false, false), HOLY_PULSE(0.68f, 7, false, false),
        TEMPLAR_STRIKE(1.2f, 6, false, false), TEMPLAR_JUDGEMENT(1.45f, 12, false, false), TEMPLAR_SHARE(0.45f, 6, false, false),
        MOLTEN_PULSE(0.48f, 6, false, false), MOLTEN_STRIKE(0.95f, 10, false, false), MOLTEN_FINISHER(1.55f, 14, true, false),
        DRAGON_PIERCE(0.95f, 10, false, true), DRAGON_JUDGEMENT(1.7f, 14, true, true),
        FOREST_RELEASE(1.05f, 10, false, false), ELF_STAB(0.52f, 6, false, false), ELF_LEAF(0.72f, 8, false, false),
        TIDE_ANCHOR(1.25f, 10, false, false), TIDE_BONUS(0.42f, 5, false, false),
        TIDE_STAB(0.65f, 6, false, false), TIDE_REEL(1.1f, 9, false, false),
        INFINITY_EVOLVE(1.1f, 9, false, false), INFINITY_COLLAPSE(0.85f, 7, false, false),
        INFINITY_STAB(0.55f, 6, false, false), INFINITY_BACK(1.05f, 9, false, false), INFINITY_RIFT(0.4f, 5, false, false),
        GALAXY_RIFT(0.95f, 9, false, false), GALAXY_JUDGEMENT(1.25f, 10, true, false),
        GALAXY_STAB(0.58f, 6, false, false), GALAXY_LEAP(0.95f, 9, false, false),
        SHIV_HIT(0.52f, 6, false, true), SHIV_STAB(0.80f, 9, false, true), SHIV_BREATH(0.68f, 8, false, true),
        FROST_MARK(0.85f, 10, false, false), FROST_SPINE(1.25f, 12, true, false);
        // Reuse the readable contact flash selectively; damage-over-time keeps its quieter family shapes.
        boolean crossContact() {
            return switch (this) {
                case CUTLASS_CUT, RUST_CUT, WOOD_CUT, LIGHT_CUT, SPINE_CUT, SPINE_WEAK, PIRATE_CUT, SILVER_CUT, SILVER_OUT, IRON_CUT, BONE_SWORD_CUT, CLAYMORE_CUT, DWARF_SWORD_CUT, DWARF_DAGGER_CUT, DWARF_GUARD, BURGLAR_CUT, SHADOW_CUT, INSECT_EYE, CRYSTAL_CUT, DARK_CUT, DARK_DEBT, FORGE_CUT, FORGE_QUENCH, FORGE_BILLET, OBSIDIAN_CUT, OBSIDIAN_RESONANCE, OSSIFIED_CUT, OSSIFIED_BONUS,
                        HOLY_CUT, HOLY_SMITE, TEMPLAR_CUT, TEMPLAR_STRIKE -> true;
                default -> false;
            };
        }
        boolean slenderCross() {return this==FALCHION_BURST||this==LIGHT_COUNTER||this==SILVER_RETURN||this==WIND_THRUST||this==CLAYMORE_RETURN||this==DWARF_THRUST||this==NEEDLE_FINAL||this==SHADOW_EXECUTE||this==OBSIDIAN_RESONANCE||this==OSSIFIED_BONUS||this==CRYSTAL_LAYER||this==VENOM_NEST;}
        boolean crescent(){return this==CUTLASS_CUT||this==CRESCENT_SLASH;}
        boolean falchion(){return this==FALCHION_CUT||this==FALCHION_DOT||this==FALCHION_BURST;}
        boolean rusty(){return this==RUST_CUT||this==RUST_STRIKE;}
        boolean wooden(){return this==WOOD_CUT||this==WOOD_BLESS;}
        boolean lightSteel(){return this==LIGHT_CUT||this==LIGHT_COUNTER;}
        boolean spine(){return this==SPINE_CUT||this==SPINE_STRIKE||this==SPINE_WEAK;}
        boolean pirate() { return this == PIRATE_CUT || this == PIRATE_PLUNDER; }
        boolean silver() { return this == SILVER_CUT || this == SILVER_OUT || this == SILVER_RETURN; }
        boolean iron() { return this == IRON_CUT || this == IRON_THRUST; }
        boolean wind() { return this == WIND_CUT || this == WIND_THRUST; }
        boolean boneSword() { return this == BONE_SWORD_CUT || this == BONE_FRACTURE; }
        boolean claymore() { return this == CLAYMORE_CUT || this == CLAYMORE_OUT || this == CLAYMORE_RETURN; }
        boolean dwarf() { return this == DWARF_SWORD_CUT || this == DWARF_DAGGER_CUT || this == DWARF_GUARD || this == DWARF_THRUST || this == DWARF_SHOCK; }
        boolean dwarfDagger() { return this == DWARF_DAGGER_CUT || this == DWARF_THRUST; }
        boolean needle() { return this == NEEDLE_CUT || this == NEEDLE_STRIKE || this == NEEDLE_FINAL || this == NEEDLE_FRENZY; }
        boolean burglar() { return this == BURGLAR_CUT || this == BURGLAR_STRIKE; }
        boolean shadow() { return this == SHADOW_CUT || this == SHADOW_EXECUTE || this == SHADOW_FINISH; }
        boolean insect() { return this == INSECT_CUT || this == INSECT_EYE || this == INSECT_DASH; }
        boolean crystal() {return this==CRYSTAL_CUT||this==CRYSTAL_LAYER||this==CRYSTAL_BURST;}
        boolean venom() {return this==VENOM_CUT||this==VENOM_RIPPLE||this==VENOM_NEST||this==VENOM_DOT||this==VENOM_BURST;}
        boolean dark() { return this == DARK_CUT || this == DARK_DEBT || this == DARK_BURST; }
        boolean forge() { return this == FORGE_CUT || this == FORGE_QUENCH || this == FORGE_BLAST || this == FORGE_BILLET || this == FORGE_RING; }
        boolean obsidian() { return this == OBSIDIAN_CUT || this == OBSIDIAN_RESONANCE || this == OBSIDIAN_CRACK; }
        boolean ossified() { return this == OSSIFIED_CUT || this == OSSIFIED_BONUS || this == OSSIFIED_PULSE; }
        boolean rainbow() { return this == MEOW_CUT || this == MEOW_PROJECTILE; }
        boolean sacred() { return this == HOLY_CUT || this == TEMPLAR_CUT || this == HOLY_SMITE || this == HOLY_PULSE || this == TEMPLAR_STRIKE || this == TEMPLAR_JUDGEMENT || this == TEMPLAR_SHARE; }
        boolean grove() { return this == FOREST_CUT || this == FOREST_RELEASE || this == ELF_STAB || this == ELF_LEAF; }
        boolean tide() { return this == TIDE_CUT || this == TIDE_ANCHOR || this == TIDE_BONUS || this == TIDE_STAB || this == TIDE_REEL; }
        boolean infinity() { return this == INFINITY_CUT || this == INFINITY_EVOLVE || this == INFINITY_COLLAPSE || this == INFINITY_STAB || this == INFINITY_BACK || this == INFINITY_RIFT; }
        boolean galaxy() { return this == GALAXY_CUT || this == GALAXY_RIFT || this == GALAXY_JUDGEMENT || this == GALAXY_STAB || this == GALAXY_LEAP; }
        boolean shiv() { return this == SHIV_HIT || this == SHIV_STAB || this == SHIV_BREATH; }
        boolean frost() { return this == FROST_CUT || this == FROST_MARK || this == FROST_SPINE; }
        final float size;
        final int lifetime;
        final boolean heavy, dragon;
        Style(float size, int lifetime, boolean heavy, boolean dragon) {
            this.size = size; this.lifetime = lifetime; this.heavy = heavy; this.dragon = dragon;
        }
    }
    private record Impact(int casterId, int targetId, long serverTick, Vec3 point, long tick, Style style) {}
    private record SoundKey(int casterId, Style style) {}
    private static final List<Impact> IMPACTS = new ArrayList<>();
    private static final Map<SoundKey, Long> SOUNDS = new HashMap<>();
    private static ClientLevel activeLevel;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private WeaponTargetImpactClient() {}

    public static void show(int casterId, int targetId, long serverTick, Vec3 point, Style style) {
        Minecraft mc = Minecraft.getInstance();
        ensureLevel(mc.level);
        if (mc.level == null || mc.player == null || mc.player.distanceToSqr(point) > 48 * 48) return;
        // Stack-derived extra hits remain real damage, but share one visual contact per target and server tick.
        if ((style.galaxy() || style.infinity()) && IMPACTS.stream().anyMatch(hit -> hit.casterId == casterId && hit.targetId == targetId
                && hit.serverTick == serverTick && hit.style == style)) return;
        boolean first = style != Style.MOLTEN_STRIKE && !Long.valueOf(serverTick).equals(SOUNDS.put(new SoundKey(casterId, style), serverTick));
        if (first) {
            var sound = style.crescent() ? SoundEvents.PLAYER_ATTACK_CRIT : style.falchion() ? SoundEvents.TRIDENT_HIT : style.rusty() ? SoundEvents.CHAIN_HIT : style.wooden() ? SoundEvents.WOOD_HIT : style.lightSteel() ? SoundEvents.TRIDENT_HIT : style.spine() ? SoundEvents.ANVIL_HIT : style.pirate() ? SoundEvents.PLAYER_ATTACK_CRIT : style.silver() ? SoundEvents.TRIDENT_HIT : style.iron() ? SoundEvents.TRIDENT_HIT : style.wind() ? SoundEvents.PLAYER_ATTACK_CRIT : style.boneSword() ? SoundEvents.BONE_BLOCK_BREAK : style.claymore() ? SoundEvents.PLAYER_ATTACK_CRIT : style.dwarf() ? (style == Style.DWARF_SHOCK ? SoundEvents.STONE_HIT : style.dwarfDagger() ? SoundEvents.TRIDENT_HIT : SoundEvents.ANVIL_HIT) : style.needle() ? (style == Style.NEEDLE_FINAL ? SoundEvents.AMETHYST_BLOCK_BREAK : SoundEvents.AMETHYST_BLOCK_HIT) : style.burglar() ? SoundEvents.PLAYER_ATTACK_CRIT : style.shadow() ? (style == Style.SHADOW_FINISH ? SoundEvents.AMETHYST_BLOCK_BREAK : SoundEvents.PLAYER_ATTACK_CRIT) : style.insect() ? SoundEvents.TRIDENT_HIT : style.crystal() ? (style==Style.CRYSTAL_BURST?SoundEvents.AMETHYST_BLOCK_BREAK:SoundEvents.AMETHYST_BLOCK_HIT) : style.venom() ? (style==Style.VENOM_BURST?SoundEvents.FIRE_EXTINGUISH:SoundEvents.PLAYER_ATTACK_CRIT) : style.dark() ? SoundEvents.PLAYER_ATTACK_CRIT : style.forge() ? (style == Style.FORGE_RING ? SoundEvents.FIRE_EXTINGUISH : SoundEvents.ANVIL_HIT) : style.obsidian() ? SoundEvents.GLASS_BREAK : style.ossified() ? SoundEvents.BONE_BLOCK_BREAK : style.rainbow() ? (style == Style.MEOW_PROJECTILE ? com.stardew.craft.sound.ModSounds.MEOW.get() : SoundEvents.AMETHYST_BLOCK_HIT) : style.sacred() ? (style == Style.HOLY_PULSE || style == Style.TEMPLAR_SHARE ? SoundEvents.AMETHYST_BLOCK_HIT : SoundEvents.PLAYER_ATTACK_CRIT) : style.grove() ? (style == Style.ELF_LEAF ? SoundEvents.AMETHYST_BLOCK_HIT : SoundEvents.TRIDENT_HIT) : style.tide() ? SoundEvents.TRIDENT_HIT : (style.galaxy() || style.infinity()) ? SoundEvents.AMETHYST_BLOCK_BREAK : style.shiv() ? SoundEvents.BONE_BLOCK_BREAK : style.frost() ? SoundEvents.GLASS_BREAK : style.heavy ? SoundEvents.GENERIC_EXPLODE
                    : style.dragon ? SoundEvents.TRIDENT_HIT : SoundEvents.LAVA_POP;
            mc.level.playLocalSound(point.x, point.y, point.z, sound, SoundSource.PLAYERS,
                    style.crescent() ? .38f : style.falchion() ? (style==Style.FALCHION_DOT?.12f:.35f) : style.rusty() ? .38f : style.wooden() ? .48f : style.lightSteel() ? .3f : style.spine() ? .25f : style.pirate() ? .42f : style.silver() ? .36f : style.iron() ? .34f : style.wind() ? .28f : style.boneSword() ? .42f : style.claymore() ? .48f : style.dwarf() ? (style == Style.DWARF_SHOCK ? .22f : .38f) : style.needle() ? (style == Style.NEEDLE_FINAL ? .32f : .2f) : style.burglar() ? .32f : style.shadow() ? (style == Style.SHADOW_FINISH ? .22f : .34f) : style.insect() ? .32f : style.crystal() ? (style==Style.CRYSTAL_BURST?.5f:.25f) : style.venom() ? (style==Style.VENOM_DOT?.08f:.3f) : style.dark() ? 0.45f : style.forge() ? (style == Style.FORGE_RING ? 0.12f : 0.45f) : style.obsidian() || style.ossified() ? 0.5f : style.rainbow() ? 0.42f : style.sacred() ? (style == Style.HOLY_PULSE || style == Style.TEMPLAR_SHARE ? 0.24f : 0.65f) : style.heavy ? 0.85f : style.grove() ? 0.4f : style.tide() ? (style == Style.TIDE_BONUS ? 0.2f : 0.55f) : style.dragon ? 0.65f : 0.2f, style.crescent() ? 1.05f : style.falchion() ? 1.4f : style.rusty() ? .75f : style.wooden() ? .8f : style.lightSteel() ? 1.6f : style.spine() ? .7f : style.pirate() ? .85f : style.silver() ? 1.3f : style.iron() ? 1.45f : style.wind() ? 1.65f : style.boneSword() ? .88f : style.claymore() ? .72f : style.shiv() ? 1.5f : style.heavy ? 0.72f : 1.1f, false);
            if (style.heavy && !style.galaxy()) mc.level.playLocalSound(point.x, point.y, point.z,
                    style.frost() ? SoundEvents.AMETHYST_BLOCK_BREAK : style.dragon ? SoundEvents.BONE_BLOCK_BREAK : SoundEvents.FIRECHARGE_USE,
                    SoundSource.PLAYERS, 0.65f, 0.7f, false);
            if (style == Style.MOLTEN_FINISHER && casterId == mc.player.getId()) CameraShakeState.kick(0.23f, 3, 0.7f);
        }
        if (!Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()) return;
        if (IMPACTS.size() >= 48) com.stardew.craft.port.PortJava.removeFirst(IMPACTS);
        IMPACTS.add(new Impact(casterId, targetId, serverTick, point, mc.level.getGameTime(), style));
        RandomSource random = RandomSource.create(serverTick * 31 + targetId);
        var bone = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.BONE));
        for (int i = 0; i < (style.crescent() || style.falchion() ? 0 : style.rusty() || style.wooden() ? 0 : style.lightSteel() || style.spine() ? 0 : style.pirate() || style.silver() ? 0 : style.iron() || style.wind() ? 0 : style.boneSword() || style.claymore() ? 0 : style.dwarf() ? 0 : style.needle() || style.burglar() ? 0 : style.shadow() ? 0 : style.insect() ? 2 : style==Style.VENOM_DOT?1:style.venom()?3:style.crystal()?3:style.rainbow() ? 3 : style.grove() ? 2 : style.shiv() ? 6 : style.heavy ? 24 : style.dragon ? 12 : 5); i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double speed = 0.06 + random.nextDouble() * (style.heavy ? 0.3 : 0.1);
            mc.level.addParticle(style.insect() ? ParticleTypes.CRIT : style.crystal()?ParticleTypes.END_ROD:style.venom()?ParticleTypes.CRIT:style.dark() ? ParticleTypes.CRIT : style.forge() ? (i % 2 == 0 ? ParticleTypes.FLAME : ParticleTypes.CRIT) : style.obsidian() ? ParticleTypes.CRIT : style.ossified() ? ParticleTypes.ASH : style.rainbow() ? ParticleTypes.END_ROD : style.sacred() ? (i % 2 == 0 ? ParticleTypes.END_ROD : ParticleTypes.CRIT) : style.grove() ? ParticleTypes.END_ROD : style.tide() ? (i % 3 == 0 ? ParticleTypes.CRIT : ParticleTypes.SPLASH) : (style.galaxy() || style.infinity()) ? (i % 3 == 0 ? ParticleTypes.CRIT : ParticleTypes.END_ROD) : style.frost() ? (i % 3 == 0 ? ParticleTypes.CRIT : ParticleTypes.SNOWFLAKE) : style.dragon && i % 3 == 0 ? bone : i % 3 == 0 ? ParticleTypes.CRIT
                            : style.dragon ? ParticleTypes.DRAGON_BREATH : ParticleTypes.FLAME,
                    point.x, point.y, point.z, Math.cos(a) * speed,
                    0.04 + random.nextDouble() * 0.16, Math.sin(a) * speed);
        }
    }

    static float opacity(float age, int lifetime) {
        if (age < 0 || age >= lifetime) return 0;
        float fade = 1 - Math.max(0, age - 2) / (lifetime - 2f);
        return fade * fade;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ensureLevel(mc.level);
        if (mc.level == null || mc.isPaused()) return;
        IMPACTS.removeIf(hit -> mc.level.getGameTime() - hit.tick >= hit.style.lifetime);
        SOUNDS.entrySet().removeIf(entry -> mc.level.getGameTime() - entry.getValue() > 40);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        ensureLevel(mc.level);
        if (mc.level == null || IMPACTS.isEmpty() || !Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()) return;
        float partial = event.getPartialTick();
        double now = mc.level.getGameTime() + partial;
        Vec3 camera = event.getCamera().getPosition();
        var stack = event.getPoseStack();
        stack.pushPose();
        stack.translate(-camera.x, -camera.y, -camera.z);
        var pose = stack.last().pose();
        var buffers = mc.renderBuffers().bufferSource();
        // Flush the contrasting silhouette before drawing its luminous center.
        for (int pass = 0; pass < 2; pass++) {
            boolean edge = pass == 0;
            var type = edge ? WeaponEffectRenderTypes.IMPACT_EDGE : WeaponEffectRenderTypes.MOLTEN_GLOW;
            var out = buffers.getBuffer(type);
            for (Impact hit : IMPACTS) {
                float age = (float) (now - hit.tick);
                float fade = opacity(age, hit.style.lifetime);
                if (fade <= 0 || hit.point.distanceToSqr(camera) > 48 * 48) continue;
                Vec3 point = hit.point;
                if (mc.level.getEntity(hit.targetId) instanceof LivingEntity target) {
                    Vec3 movement = target.getPosition(partial).subtract(target.position());
                    var box = target.getBoundingBox().move(movement);
                    Vec3 center = box.getCenter();
                    point = box.clip(camera, center).orElse(center);
                }
                Vec3 normal = camera.subtract(point).normalize();
                point = point.add(normal.scale(edge ? 0.055 : 0.065));
                Vec3 right = normal.cross(UP).normalize();
                if (right.lengthSqr() < 1.0E-6) right = new Vec3(1, 0, 0);
                Vec3 up = right.cross(normal).normalize();
                int r = edge ? (hit.style.galaxy() ? 24 : 40) : hit.style.crescent()?214:hit.style.falchion()?167:hit.style.rusty()?215:hit.style.wooden()?201:hit.style.lightSteel()||hit.style.spine()?205:hit.style.pirate()?222:hit.style.silver()?199:hit.style.iron()?193:hit.style.wind()?143:hit.style.boneSword()?232:hit.style.claymore()?172:hit.style.dwarf()?(hit.style.dwarfDagger()?159:232):hit.style.needle()?204:hit.style.burglar()?210:hit.style.shadow()?172:hit.style.insect()?224:hit.style.crystal()?142:hit.style.venom()?130:hit.style.dark() ? 219 : hit.style.forge() ? 255 : hit.style.obsidian() ? 163 : hit.style.ossified() ? 232 : hit.style.sacred() ? 255 : hit.style.grove() ? 154 : hit.style.tide() ? 63 : hit.style.infinity() ? 255 : hit.style.galaxy() ? 136 : hit.style.frost() ? 105 : hit.style.dragon ? 210 : 255;
                int g = edge ? 12 : hit.style.crescent()?217:hit.style.falchion()?216:hit.style.rusty()?132:hit.style.wooden()?212:hit.style.lightSteel()||hit.style.spine()?219:hit.style.pirate()?165:hit.style.silver()?219:hit.style.iron()?212:hit.style.wind()?226:hit.style.boneSword()?221:hit.style.claymore()?201:hit.style.dwarf()?(hit.style.dwarfDagger()?218:184):hit.style.needle()?172:hit.style.burglar()?221:hit.style.shadow()?145:hit.style.insect()?207:hit.style.crystal()?215:hit.style.venom()?197:hit.style.dark() ? 47 : hit.style.forge() ? 154 : hit.style.obsidian() ? 101 : hit.style.ossified() ? 222 : hit.style.sacred() ? 223 : hit.style.grove() ? 235 : hit.style.tide() ? 217 : hit.style.infinity() ? 209 : hit.style.galaxy() ? 189 : hit.style.frost() ? 218 : hit.style.dragon ? 145 : 103;
                int b = edge ? (hit.style.galaxy() ? 38 : 18) : hit.style.crescent()?244:hit.style.falchion()?237:hit.style.rusty()?81:hit.style.wooden()?132:hit.style.lightSteel()||hit.style.spine()?238:hit.style.pirate()?101:hit.style.silver()?243:hit.style.iron()?238:hit.style.wind()?211:hit.style.boneSword()?188:hit.style.claymore()?228:hit.style.dwarf()?(hit.style.dwarfDagger()?217:103):hit.style.needle()?250:hit.style.burglar()?226:hit.style.shadow()?244:hit.style.insect()?145:hit.style.crystal()?255:hit.style.venom()?75:hit.style.dark() ? 76 : hit.style.forge() ? 58 : hit.style.obsidian() ? 245 : hit.style.ossified() ? 193 : hit.style.sacred() ? 139 : hit.style.grove() ? 160 : hit.style.tide() ? 245 : hit.style.infinity() ? 136 : hit.style.galaxy() ? 255 : hit.style.frost() ? 255 : hit.style.dragon ? 255 : 15;
                WeaponImpactGeometry.draw(out, pose, point, right, up, normal, hit.style, age, fade, edge, r, g, b);
            }
            buffers.endBatch(type);
        }
        stack.popPose();
    }

    private static void ensureLevel(ClientLevel level) {
        if (activeLevel == level) return;
        activeLevel = level; IMPACTS.clear(); SOUNDS.clear();
    }
}

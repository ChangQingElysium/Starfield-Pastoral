package com.stardew.craft.client.weapon;

import com.stardew.craft.Config;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.network.ForestBlessingPayload;
import com.stardew.craft.combat.network.ForestBlessingPayload.Phase;
import com.stardew.craft.combat.network.WeaponSkillAnimPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import static com.stardew.craft.client.weapon.MeleeWeaponVisuals.*;

/** A quiet blessing silhouette; bright healing pulses come only from actual server healing. */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class GroveWeaponVisuals {
    private static final Map<Integer, ForestBlessingPayload> FIELDS = new HashMap<>();
    private static final Map<Integer, Long> SEEN = new HashMap<>();
    private static final List<ForestBlessingPayload> HEALS = new ArrayList<>();
    private static ClientLevel activeLevel;
    private GroveWeaponVisuals() {}
    public static boolean isAction(String id) {
        return FOREST_READY.equals(id) || FOREST_RELEASE.equals(id) || ELF_CAST.equals(id) || ELF_SWING.equals(id);
    }
    public static void start(WeaponSkillAnimPayload p) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || FOREST_READY.equals(p.skillId())) return;
        Vec3 point = new Vec3(p.originX(), p.originY() + 0.8, p.originZ());
        if (mc.player.distanceToSqr(point) > 48 * 48) return;
        mc.level.playLocalSound(point.x, point.y, point.z,
                FOREST_RELEASE.equals(p.skillId()) ? SoundEvents.PLAYER_ATTACK_SWEEP : SoundEvents.AZALEA_LEAVES_PLACE,
                SoundSource.PLAYERS, 0.4f, FOREST_RELEASE.equals(p.skillId()) ? 0.9f : 1.35f, false);
    }
    static boolean newer(long incoming, Long seen) { return seen == null || incoming > seen; }
    static boolean matches(long incoming, ForestBlessingPayload field) { return field != null && field.castTick() == incoming; }
    static float fade(float age, int duration) {
        if (duration <= 0 || age < 0 || age >= duration) return 0;
        float f = 1 - age / duration; return f * f;
    }
    public static void phase(ForestBlessingPayload p) {
        var mc = Minecraft.getInstance(); ensureLevel(mc.level);
        if (mc.level == null) return;
        var field = FIELDS.get(p.casterId());
        boolean local = mc.player != null && mc.player.getId() == p.casterId();
        if (p.phase() == Phase.END) {
            if (matches(p.castTick(), field)) {
                FIELDS.remove(p.casterId());
                if (local) ForestBlessingClientState.clear();
            }
            SEEN.merge(p.casterId(), p.castTick(), Math::max);
            return;
        }
        if (p.phase() == Phase.START) {
            if (!newer(p.castTick(), SEEN.get(p.casterId())) || mc.level.getGameTime() >= p.tick() + p.duration()) return;
            if (FIELDS.size() >= 64) FIELDS.remove(FIELDS.keySet().iterator().next());
            FIELDS.put(p.casterId(), p); SEEN.put(p.casterId(), p.castTick());
            if (local) ForestBlessingClientState.start(p.tick(), p.duration());
            return;
        }
        if (!matches(p.castTick(), field) || fade(mc.level.getGameTime() - p.tick(), p.duration()) <= 0
                || HEALS.stream().anyMatch(h -> h.casterId() == p.casterId() && h.tick() == p.tick())) return;
        if (HEALS.size() >= 48) HEALS.removeFirst();
        HEALS.add(p);
        if (mc.level.getEntity(p.casterId()) instanceof LivingEntity caster && mc.player != null && caster.distanceToSqr(mc.player) <= 32 * 32) {
            mc.level.playLocalSound(caster.getX(), caster.getY() + 0.8, caster.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.PLAYERS, 0.09f, p.empowered() ? 1.4f : 1.15f, false);
            if (Config.ENABLE_WEAPON_SPECIAL_EFFECTS.getAsBoolean()) for (int i = 0; i < 2; i++)
                mc.level.addParticle(ParticleTypes.END_ROD, caster.getX() + (i - 1) * 0.25,
                        caster.getY() + 0.5 + i * 0.14, caster.getZ(), 0, 0.025, 0);
        }
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance(); ensureLevel(mc.level);
        if (mc.level == null || mc.isPaused()) return;
        long now = mc.level.getGameTime();
        FIELDS.entrySet().removeIf(e -> now >= e.getValue().tick() + e.getValue().duration()
                || !(mc.level.getEntity(e.getKey()) instanceof LivingEntity caster) || !caster.isAlive());
        SEEN.entrySet().removeIf(e -> now - e.getValue() > 200);
        HEALS.removeIf(p -> now >= p.tick() + p.duration());
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var mc = Minecraft.getInstance(); ensureLevel(mc.level);
        if (mc.level == null || !Config.ENABLE_WEAPON_SPECIAL_EFFECTS.getAsBoolean()) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double now = mc.level.getGameTime() + partial;
        var stack = event.getPoseStack(); var camera = event.getCamera().getPosition();
        var buffers = mc.renderBuffers().bufferSource();
        var out = buffers.getBuffer(WeaponEffectRenderTypes.MOLTEN_GLOW);
        for (var field : FIELDS.values()) {
            if (!(mc.level.getEntity(field.casterId()) instanceof LivingEntity caster) || caster.distanceToSqr(camera) > 32 * 32) continue;
            Vec3 base = caster.getPosition(partial).subtract(camera);
            float endFade = (float) Math.clamp((field.tick() + field.duration() - now) / 10, 0, 1);
            float startFade = (float) Math.clamp((now - field.tick()) / 8, 0, 1);
            float opacity = endFade * startFade * (field.empowered() ? 1 : 0.7f);
            GroveEffectGeometry.wisp(out, stack.last().pose(), base, now * 0.028, 0.6, 0.14, opacity);
            GroveEffectGeometry.wisp(out, stack.last().pose(), base, now * 0.028 + 3.1, 0.52, 0.32, opacity * 0.65f);
        }
        for (var heal : HEALS) {
            float f = fade((float) (now - heal.tick()), heal.duration());
            if (f <= 0 || !(mc.level.getEntity(heal.casterId()) instanceof LivingEntity caster) || caster.distanceToSqr(camera) > 32 * 32) continue;
            Vec3 point = caster.getPosition(partial).subtract(camera);
            double rise = (1 - f) * 0.6;
            GroveEffectGeometry.wisp(out, stack.last().pose(), point, heal.tick() * 0.17 + (1 - f),
                    0.48, 0.22 + rise, f);
        }
        buffers.endBatch(WeaponEffectRenderTypes.MOLTEN_GLOW);
    }
    private static void ensureLevel(ClientLevel level) {
        if (level == activeLevel) return;
        activeLevel = level; FIELDS.clear(); SEEN.clear(); HEALS.clear(); ForestBlessingClientState.clear();
    }
}

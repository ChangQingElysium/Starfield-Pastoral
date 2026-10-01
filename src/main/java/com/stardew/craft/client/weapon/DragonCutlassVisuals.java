package com.stardew.craft.client.weapon;

import com.stardew.craft.Config;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.network.WeaponSkillAnimPayload;
import com.stardew.craft.item.weapon.IStardewWeapon;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import static com.stardew.craft.client.weapon.WeaponGlowGeometry.*;

/** Release shapes use the authoritative cast origin; dash trails follow actual movement. */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class DragonCutlassVisuals {
    private record Cast(WeaponSkillAnimPayload payload, long playbackTick) {}
    private static final Map<Integer, Cast> CASTS = new HashMap<>();
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static ClientLevel activeLevel;
    private DragonCutlassVisuals() {}

    public static void start(WeaponSkillAnimPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        ensureLevel(mc.level);
        if (mc.level == null) return;
        Cast prior = CASTS.get(payload.casterEntityId());
        if (prior != null && prior.payload.startGameTick() >= payload.startGameTick()) return;
        CASTS.put(payload.casterEntityId(), new Cast(payload, mc.level.getGameTime()));
        if (mc.player == null || mc.player.distanceToSqr(origin(payload)) > 48 * 48) return;
        boolean major = MeleeWeaponVisuals.DRAGON_JUDGEMENT.equals(payload.skillId());
        Vec3 p = origin(payload);
        mc.level.playLocalSound(p.x, p.y, p.z, major ? SoundEvents.PLAYER_ATTACK_SWEEP : SoundEvents.TRIDENT_THROW,
                SoundSource.PLAYERS, major ? 0.7f : 0.45f, major ? 0.65f : 0.85f, false);
        mc.level.playLocalSound(p.x, p.y, p.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS,
                major ? 0.4f : 0.25f, major ? 0.6f : 1.0f, false);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ensureLevel(mc.level);
        if (mc.level == null || mc.isPaused()) return;
        long now = mc.level.getGameTime();
        CASTS.entrySet().removeIf(entry -> now - entry.getValue().playbackTick > 10
                || !(mc.level.getEntity(entry.getKey()) instanceof Player player) || !player.isAlive()
                || !(player.getMainHandItem().getItem() instanceof IStardewWeapon weapon)
                || !"dragontooth_cutlass".equals(weapon.getWeaponId()));
        if (!Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get() || mc.player == null) return;
        for (var entry : CASTS.entrySet()) {
            var player = mc.level.getEntity(entry.getKey());
            if (player == null || player.distanceToSqr(mc.player) > 24 * 24
                    || now - entry.getValue().playbackTick > 5) continue;
            Vec3 p = player.position().add(0, 0.8, 0);
            mc.level.addParticle(ParticleTypes.DRAGON_BREATH, p.x, p.y, p.z, 0, 0.018, 0);
        }
    }

    static Vec3 forward(float yaw) {
        double angle = Math.toRadians(yaw);
        return new Vec3(-Math.sin(angle), 0, Math.cos(angle));
    }
    private static Vec3 origin(WeaponSkillAnimPayload payload) {
        return new Vec3(payload.originX(), payload.originY() + 0.9, payload.originZ());
    }
    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        ensureLevel(mc.level);
        if (mc.level == null || CASTS.isEmpty() || !Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()) return;
        float partial = event.getPartialTick();
        Vec3 camera = event.getCamera().getPosition();
        var stack = event.getPoseStack();
        stack.pushPose(); stack.translate(-camera.x, -camera.y, -camera.z);
        var pose = stack.last().pose();
        var buffers = mc.renderBuffers().bufferSource();
        var out = buffers.getBuffer(WeaponEffectRenderTypes.MOLTEN_GLOW);
        for (var entry : CASTS.entrySet()) {
            Cast cast = entry.getValue();
            var payload = cast.payload;
            float age = mc.level.getGameTime() + partial - cast.playbackTick;
            if (age < 0 || age >= 10 || origin(payload).distanceToSqr(camera) > 48 * 48) continue;
            float fade = 1 - age / 10;
            Vec3 center = origin(payload), f = forward(payload.yaw()), right = f.cross(UP);
            if (MeleeWeaponVisuals.DRAGON_JUDGEMENT.equals(payload.skillId())) {
                double radius = 0.6 + Math.min(1, age / 2) * 2.8;
                for (int i = 0; i < 20; i++) {
                    double a = Math.toRadians(-60 + i * 6), b = Math.toRadians(-60 + (i + 1) * 6);
                    Vec3 ray = f.scale(Math.cos(a)).add(right.scale(Math.sin(a)));
                    Vec3 next = f.scale(Math.cos(b)).add(right.scale(Math.sin(b)));
                    Vec3 from = center.add(ray.scale(radius)), to = center.add(next.scale(radius));
                    strip(out, pose, from, to, ray.scale(0.16 * fade), 138, 55, 230, Math.round(175 * fade));
                    strip(out, pose, from, to, ray.scale(0.035 * fade), 255, 242, 213, Math.round(250 * fade));
                    if (i % 3 == 0) strip(out, pose, from, from.add(ray.scale(0.45 * fade)).add(0, 0.18, 0),
                            right.scale(0.025 * fade), 244, 224, 255, Math.round(215 * fade));
                }
            } else if (mc.level.getEntity(entry.getKey()) instanceof Player player) {
                Vec3 end = player.getPosition(partial).add(0, 0.9, 0);
                // Do not draw through walls or fabricate the full five-meter dash when movement was cut short.
                if (end.distanceToSqr(center) > 36) continue;
                for (int side : new int[]{-1, 1}) {
                    Vec3 offset = right.scale(side * 0.32);
                    strip(out, pose, center.add(offset), end.add(offset), UP.scale(0.065 * fade),
                            160, 71, 238, Math.round(170 * fade));
                    strip(out, pose, center.add(offset), end.add(offset), UP.scale(0.018 * fade),
                            255, 246, 219, Math.round(240 * fade));
                }
            }
        }
        stack.popPose(); buffers.endBatch(WeaponEffectRenderTypes.MOLTEN_GLOW);
    }
    private static void ensureLevel(ClientLevel level) {
        if (activeLevel == level) return;
        activeLevel = level; CASTS.clear();
    }
}

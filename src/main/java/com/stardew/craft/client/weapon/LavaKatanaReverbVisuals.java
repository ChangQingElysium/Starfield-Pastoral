package com.stardew.craft.client.weapon;

import com.stardew.craft.Config;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.network.LavaKatanaImpactPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import static com.stardew.craft.client.weapon.WeaponGlowGeometry.*;

/** Sparse orbiting molten ribbons followed by server-confirmed, target-centered fractures. */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class LavaKatanaReverbVisuals {
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private LavaKatanaReverbVisuals() {}

    public static void ignite(int casterId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !(mc.level.getEntity(casterId) instanceof Player caster)
                || caster.distanceToSqr(mc.player) > 48 * 48) return;
        Vec3 p = caster.position().add(0, 0.8, 0);
        sound(p, SoundEvents.FIRECHARGE_USE, 0.55f, 0.65f);
        sound(p, SoundEvents.BLAZE_SHOOT, 0.35f, 0.8f);
    }

    public static void burst(LavaKatanaImpactPayload payload) {
        WeaponTargetImpactClient.show(payload.casterId(), payload.targetId(), payload.gameTick(),
                new Vec3(payload.x(), payload.y(), payload.z()), payload.burn()
                        ? WeaponTargetImpactClient.Style.MOLTEN_PULSE : WeaponTargetImpactClient.Style.MOLTEN_FINISHER);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        long now = mc.level.getGameTime();
        if (!Config.ENABLE_WEAPON_SPECIAL_EFFECTS.getAsBoolean() || now % 4 != 0) return;
        for (int id : LavaKatanaReverbClientState.activeCasterIds()) {
            if (!(mc.level.getEntity(id) instanceof Player caster) || !caster.isAlive()
                    || caster.distanceToSqr(mc.player) > 24 * 24) continue;
            float progress = LavaKatanaReverbClientState.progress(id, 0);
            for (int i = 0; i < 2; i++) {
                Vec3 p = orbit(caster.position(), now * 0.17 + i * Math.PI, progress);
                mc.level.addParticle(ParticleTypes.SMALL_FLAME, p.x, p.y, p.z, 0, 0.018, 0);
            }
        }
    }

    static Vec3 orbit(Vec3 base, double angle, float progress) {
        double radius = 0.65 + Math.sin(angle * 0.5) * 0.08;
        return base.add(Math.cos(angle) * radius,
                0.65 + Math.sin(angle * 1.5) * 0.35 + progress * 0.15,
                Math.sin(angle) * radius);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !Config.ENABLE_WEAPON_SPECIAL_EFFECTS.getAsBoolean()) return;
        var ids = LavaKatanaReverbClientState.activeCasterIds();
        if (ids.isEmpty()) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        var stack = event.getPoseStack();
        stack.pushPose();
        stack.translate(-camera.x, -camera.y, -camera.z);
        var pose = stack.last().pose();
        var buffers = mc.renderBuffers().bufferSource();
        var out = buffers.getBuffer(WeaponEffectRenderTypes.MOLTEN_GLOW);
        for (int id : ids) {
            if (!(mc.level.getEntity(id) instanceof Player caster) || !caster.isAlive()
                    || caster.distanceToSqr(camera) > 32 * 32) continue;
            float p = LavaKatanaReverbClientState.progress(id, partial);
            float fade = Math.min(1, p * 16) * Math.min(1, (1 - p) * 12);
            // Keep the local first-person view open: lower the thin orbit below the reticle.
            Vec3 base = caster.getPosition(partial);
            if (caster == mc.player && mc.options.getCameraType().isFirstPerson()) base = base.add(0, -0.35, 0);
            for (int strand = 0; strand < 2; strand++) {
                double angle = p * (18 + p * 20) + strand * Math.PI;
                for (int i = 0; i < 18; i++) {
                    Vec3 from = orbit(base, angle - i * 0.075, p);
                    Vec3 to = orbit(base, angle - (i + 1) * 0.075, p);
                    float alpha = fade * (1 - i / 18f);
                    strip(out, pose, from, to, UP.scale(0.045), 255, 65, 4, Math.round(90 * alpha));
                    strip(out, pose, from, to, UP.scale(0.009), 255, 217, 112, Math.round(190 * alpha));
                }
            }
        }
        stack.popPose();
        buffers.endBatch(WeaponEffectRenderTypes.MOLTEN_GLOW);
    }

    private static void sound(Vec3 point, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) mc.level.playLocalSound(point.x, point.y, point.z, sound,
                SoundSource.PLAYERS, volume, pitch, false);
    }

}

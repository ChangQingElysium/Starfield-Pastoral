package com.stardew.craft.client.weapon.trail;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stardew.craft.Config;
import com.stardew.craft.StardewCraft;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Shared, bounded blade trail renderer fed by the final item render transform.
 */
public final class WeaponTrailClient {
    private static final int MAX_SAMPLES = 18;
    private static final int MAX_RESAMPLE_STEPS = 4;
    private static final double SAMPLE_INTERVAL_TICKS = 0.20;
    private static final double MIN_SAMPLE_DISTANCE_SQR = 0.0004;
    private static final double MAX_RENDER_DISTANCE_SQR = 48.0 * 48.0;
    private static final Map<String, TrailProfile> PROFILES = Map.of(
            "crescent_slash",
            new TrailProfile(
                    ResourceLocation.fromNamespaceAndPath(
                            StardewCraft.MODID,
                            "textures/particle/crescent_blade_trail.png"
                    ),
                    0.25f,
                    0.625f,
                    3.0f
            ),
            "forest_blessing",
            new TrailProfile(
                    ResourceLocation.fromNamespaceAndPath(
                            StardewCraft.MODID,
                            "textures/particle/forest_blade_trail.png"
                    ),
                    0.25f,
                    0.60f,
                    2.5f
            )
    );
    private static final Map<Integer, TrailState> STATES = new HashMap<>();
    private static ClientLevel activeLevel;

    private WeaponTrailClient() {}

    public static boolean supports(String skillId) {
        return PROFILES.containsKey(skillId);
    }

    public static void capture(
            int entityId,
            String skillId,
            long actionStartTick,
            float actionProgress,
            Vec3 bladeBase,
            Vec3 bladeTip,
            double sampleTime
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !Config.ENABLE_WEAPON_SPECIAL_EFFECTS.getAsBoolean()) {
            return;
        }
        if (activeLevel != minecraft.level) {
            STATES.clear();
            activeLevel = minecraft.level;
        }

        TrailProfile profile = PROFILES.get(skillId);
        if (profile == null
                || actionProgress < profile.startProgress
                || actionProgress > profile.endProgress) {
            return;
        }

        TrailState state = STATES.computeIfAbsent(
                entityId,
                ignored -> new TrailState(profile, actionStartTick)
        );
        if (state.actionStartTick != actionStartTick || state.profile != profile) {
            state.samples.clear();
            state.actionStartTick = actionStartTick;
            state.profile = profile;
        }

        TrailSample previous = state.samples.peekLast();
        if (previous == null) {
            state.samples.addLast(new TrailSample(bladeBase, bladeTip, sampleTime));
            return;
        }

        double elapsed = sampleTime - previous.time;
        double travelSqr = Math.max(
                previous.base.distanceToSqr(bladeBase),
                previous.tip.distanceToSqr(bladeTip)
        );
        if (elapsed < SAMPLE_INTERVAL_TICKS
                || travelSqr < MIN_SAMPLE_DISTANCE_SQR) {
            return;
        }

        int resampleCount = calculateResampleCount(elapsed);
        for (int index = 1; index <= resampleCount; index++) {
            double time = Math.min(
                    previous.time + SAMPLE_INTERVAL_TICKS * index,
                    sampleTime
            );
            double amount = Mth.clamp(
                    (time - previous.time) / elapsed,
                    0.0,
                    1.0
            );
            Vec3 sampleBase = previous.base.lerp(bladeBase, amount);
            Vec3 sampleTip = previous.tip.lerp(bladeTip, amount);
            state.samples.addLast(new TrailSample(sampleBase, sampleTip, time));
        }
        while (state.samples.size() > MAX_SAMPLES) {
            state.samples.removeFirst();
        }
    }

    public static void render(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || STATES.isEmpty()
                || !Config.ENABLE_WEAPON_SPECIAL_EFFECTS.getAsBoolean()) {
            return;
        }
        if (activeLevel != minecraft.level) {
            STATES.clear();
            activeLevel = minecraft.level;
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double now = minecraft.level.getGameTime() + partialTick;
        Vec3 camera = event.getCamera().getPosition();
        Map<TrailProfile, RenderType> renderTypes = new LinkedHashMap<>();

        Iterator<TrailState> states = STATES.values().iterator();
        while (states.hasNext()) {
            TrailState state = states.next();
            while (!state.samples.isEmpty()
                    && now - state.samples.peekFirst().time > state.profile.lifetimeTicks) {
                state.samples.removeFirst();
            }
            if (state.samples.size() < 2) {
                if (state.samples.isEmpty()) {
                    states.remove();
                }
                continue;
            }
            TrailSample newest = state.samples.peekLast();
            if (newest.tip.distanceToSqr(camera) > MAX_RENDER_DISTANCE_SQR) {
                continue;
            }
            renderTypes.computeIfAbsent(
                    state.profile,
                    profile -> RenderType.entityTranslucent(profile.texture)
            );
        }
        if (renderTypes.isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f pose = poseStack.last().pose();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();

        for (TrailState state : STATES.values()) {
            if (state.samples.size() < 2) {
                continue;
            }
            RenderType renderType = renderTypes.get(state.profile);
            if (renderType == null) {
                continue;
            }
            renderTrail(
                    minecraft.level,
                    buffers.getBuffer(renderType),
                    pose,
                    state,
                    now
            );
        }
        poseStack.popPose();
        for (RenderType renderType : renderTypes.values()) {
            buffers.endBatch(renderType);
        }
    }

    private static void renderTrail(
            ClientLevel level,
            VertexConsumer consumer,
            Matrix4f pose,
            TrailState state,
            double now
    ) {
        TrailSample[] samples = state.samples.toArray(TrailSample[]::new);
        for (int index = 1; index < samples.length; index++) {
            TrailSample previous = samples[index - 1];
            TrailSample current = samples[index];
            float u0 = trailCoordinate(previous, state.profile, now);
            float u1 = trailCoordinate(current, state.profile, now);
            float ageAlpha = 1.0f - Mth.clamp(
                    (float) ((now - current.time) / state.profile.lifetimeTicks),
                    0.0f,
                    1.0f
            );
            int alpha = Math.round(255.0f * ageAlpha);
            Vec3 center = previous.base.add(previous.tip)
                    .add(current.base)
                    .add(current.tip)
                    .scale(0.25);
            int light = LevelRenderer.getLightColor(level, BlockPos.containing(center));
            Vec3 blade = current.tip.subtract(current.base);
            Vec3 travel = current.tip.subtract(previous.tip);
            Vec3 normal = blade.cross(travel);
            if (normal.lengthSqr() < 1.0E-6) {
                normal = new Vec3(0.0, 1.0, 0.0);
            } else {
                normal = normal.normalize();
            }

            vertex(consumer, pose, previous.base, u0, 1.0f, alpha, light, normal);
            vertex(consumer, pose, previous.tip, u0, 0.0f, alpha, light, normal);
            vertex(consumer, pose, current.tip, u1, 0.0f, alpha, light, normal);
            vertex(consumer, pose, current.base, u1, 1.0f, alpha, light, normal);
        }
    }

    private static float trailCoordinate(
            TrailSample sample,
            TrailProfile profile,
            double now
    ) {
        return calculateTrailCoordinate(
                sample.time,
                profile.lifetimeTicks,
                now
        );
    }

    static int calculateResampleCount(double elapsedTicks) {
        return Mth.clamp(
                (int) Math.floor(elapsedTicks / SAMPLE_INTERVAL_TICKS),
                1,
                MAX_RESAMPLE_STEPS
        );
    }

    static float calculateTrailCoordinate(
            double sampleTime,
            float lifetimeTicks,
            double now
    ) {
        return 1.0f - Mth.clamp(
                (float) ((now - sampleTime) / lifetimeTicks),
                0.0f,
                1.0f
        );
    }

    static int sampleCapacity() {
        return MAX_SAMPLES;
    }

    static double sampleIntervalTicks() {
        return SAMPLE_INTERVAL_TICKS;
    }

    private static void vertex(
            VertexConsumer consumer,
            Matrix4f pose,
            Vec3 point,
            float u,
            float v,
            int alpha,
            int light,
            Vec3 normal
    ) {
        consumer.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
                .setColor(255, 255, 255, alpha)
                .setUv(u, v)
                .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(
                        (float) normal.x,
                        (float) normal.y,
                        (float) normal.z
                );
    }

    private record TrailProfile(
            ResourceLocation texture,
            float startProgress,
            float endProgress,
            float lifetimeTicks
    ) {}

    private record TrailSample(Vec3 base, Vec3 tip, double time) {}

    private static final class TrailState {
        private TrailProfile profile;
        private long actionStartTick;
        private final ArrayDeque<TrailSample> samples = new ArrayDeque<>();

        private TrailState(TrailProfile profile, long actionStartTick) {
            this.profile = profile;
            this.actionStartTick = actionStartTick;
        }
    }
}

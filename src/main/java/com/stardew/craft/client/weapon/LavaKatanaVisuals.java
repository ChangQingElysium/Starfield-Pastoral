package com.stardew.craft.client.weapon;

import static com.stardew.craft.client.weapon.WeaponGlowGeometry.*;

import com.stardew.craft.Config;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.network.LavaKatanaImpactPayload;
import com.stardew.craft.combat.network.WeaponSkillAnimPayload;
import com.stardew.craft.item.weapon.IStardewWeapon;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.jetbrains.annotations.Nullable;

/** Lava Katana's short-lived, client-only performance: slash, confirmed impact and heat. */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class LavaKatanaVisuals {
    public static final String SWING = "lava_katana_swing";
    public static final String REVERB = "lava_katana_reverb";
    public static final String BRAND = "lava_katana_brand";
    private static final int MAX_IMPACTS = 48;
    private static final double RANGE_SQR = 48 * 48;
    private static final List<Impact> IMPACTS = new ArrayList<>();
    private static final Map<Integer, Long> SWING_SOUNDS = new HashMap<>();
    private static final Map<Integer, Long> IMPACT_SOUNDS = new HashMap<>();
    private static ClientLevel activeLevel;
    private static long freezeUntil;
    private static long frozenStart;
    private static String frozenSkill;
    private static float frozenProgress;

    private LavaKatanaVisuals() {}

    public record Action(String skillId, long startTick, float progress) {}

    @Nullable
    public static Action action(LivingEntity entity, float partialTick) {
        if (!entity.isAlive() || !(entity.getMainHandItem().getItem() instanceof IStardewWeapon weapon)
                || !"lava_katana".equals(weapon.getWeaponId())) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || entity.level() != mc.level) {
            return null;
        }
        float skillProgress = WeaponSkillAnimationClient.getWorldActionProgress(entity.getId(), partialTick);
        WeaponSkillAnimPayload skill = WeaponSkillAnimationClient.getWorldAction(entity.getId());
        Action action;
        if (skillProgress >= 0 && skill != null) {
            if (!(BRAND.equals(skill.skillId()) || REVERB.equals(skill.skillId())) || !"lava_katana".equals(skill.weaponId())) {
                return null;
            }
            action = new Action(skill.skillId(), skill.startGameTick(), skillProgress);
        } else {
            float swing = entity.getAttackAnim(partialTick);
            if (swing <= 0 || entity.swingingArm != InteractionHand.MAIN_HAND) {
                return null;
            }
            action = new Action(SWING, mc.level.getGameTime() - Math.max(0, entity.swingTime), swing);
        }
        if (entity == mc.player && activeLevel == mc.level && Util.getMillis() < freezeUntil
                && action.startTick() == frozenStart && action.skillId().equals(frozenSkill)) {
            return new Action(action.skillId(), action.startTick(), frozenProgress);
        }
        return action;
    }

    public static void startBrand(WeaponSkillAnimPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        ensureLevel(mc.level);
        playRelease(new Vec3(payload.originX(), payload.originY() + 1, payload.originZ()), true);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ensureLevel(mc.level);
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        long now = mc.level.getGameTime();
        IMPACTS.removeIf(impact -> now - impact.startTick > 8);
        SWING_SOUNDS.entrySet().removeIf(entry -> now - entry.getValue() > 20);
        IMPACT_SOUNDS.entrySet().removeIf(entry -> mc.level.getEntity(entry.getKey()) == null);
        for (var player : mc.level.players()) {
            if (player.distanceToSqr(mc.player) > RANGE_SQR) continue;
            Action action = action(player, 1);
            if (action == null || !SWING.equals(action.skillId())) continue;
            if (!Long.valueOf(action.startTick()).equals(SWING_SOUNDS.get(player.getId()))) {
                SWING_SOUNDS.put(player.getId(), action.startTick());
                playRelease(player.position().add(0, 1, 0), false);
            }
        }
    }

    public static void impact(LavaKatanaImpactPayload payload) {
        if (payload.finisher() || payload.burn()) {
            LavaKatanaReverbVisuals.burst(payload);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        ensureLevel(mc.level);
        Vec3 point = new Vec3(payload.x(), payload.y(), payload.z());
        if (mc.player.distanceToSqr(point) > RANGE_SQR) return;
        Vec3 direction = new Vec3(payload.directionX(), payload.directionY(), payload.directionZ());
        if (direction.lengthSqr() < 1.0E-6) direction = new Vec3(0, 0, 1);
        boolean firstSound = !Long.valueOf(payload.gameTick()).equals(IMPACT_SOUNDS.get(payload.casterId()));
        IMPACT_SOUNDS.put(payload.casterId(), payload.gameTick());
        if (firstSound) {
            mc.level.playLocalSound(point.x, point.y, point.z,
                    payload.brand() ? SoundEvents.ANVIL_LAND
                            : payload.critical() ? SoundEvents.PLAYER_ATTACK_CRIT : SoundEvents.TRIDENT_HIT,
                    SoundSource.PLAYERS, payload.brand() ? 0.45f : 0.48f,
                    payload.critical() ? 0.82f : 1.3f, false);
            mc.level.playLocalSound(point.x, point.y, point.z, SoundEvents.LAVA_POP,
                    SoundSource.PLAYERS, payload.brand() ? 0.65f : 0.32f, 0.85f, false);
        }
        if (firstSound && payload.casterId() == mc.player.getId()) {
            Action current = action(mc.player, mc.getTimer().getGameTimeDeltaPartialTick(false));
            if (current != null && (payload.brand() == BRAND.equals(current.skillId()))
                    && Math.abs(payload.gameTick() - current.startTick()) <= 2) {
                frozenStart = current.startTick();
                frozenSkill = current.skillId();
                frozenProgress = current.progress();
                freezeUntil = Util.getMillis() + (payload.critical() || payload.brand() ? 45 : 22);
                CameraShakeState.kick(payload.brand() ? 0.16f : payload.critical() ? 0.12f : 0.055f,
                        2, payload.brand() ? 0.8f : 0.25f);
            }
        }
        if (mc.level.getEntity(payload.casterId()) instanceof net.minecraft.world.entity.player.Player caster
                && LavaKatanaReverbClientState.isActive(caster)) {
            WeaponTargetImpactClient.show(payload.casterId(), payload.targetId(), payload.gameTick(), point,
                    WeaponTargetImpactClient.Style.MOLTEN_STRIKE);
            return;
        }
        if (!Config.ENABLE_WEAPON_SPECIAL_EFFECTS.getAsBoolean()) return;
        if (IMPACTS.size() >= MAX_IMPACTS) IMPACTS.removeFirst();
        IMPACTS.add(new Impact(point, mc.level.getGameTime(),
                payload.brand() ? 1.25f : payload.critical() ? 1.0f : 0.68f));
        RandomSource random = RandomSource.create(payload.gameTick() * 31 + payload.targetId());
        int count = payload.brand() ? 18 : payload.critical() ? 12 : 7;
        for (int i = 0; i < count; i++) {
            Vec3 velocity = direction.scale(0.08 + random.nextDouble() * 0.16).add(
                    (random.nextDouble() - 0.5) * 0.22, random.nextDouble() * 0.19,
                    (random.nextDouble() - 0.5) * 0.22);
            mc.level.addParticle(i % 3 == 0 ? ParticleTypes.FLAME : ParticleTypes.CRIT,
                    point.x, point.y, point.z, velocity.x, velocity.y, velocity.z);
        }
    }

    private static void playRelease(Vec3 position, boolean brand) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.player.distanceToSqr(position) > RANGE_SQR) return;
        mc.level.playLocalSound(position.x, position.y, position.z,
                SoundEvents.FIRECHARGE_USE,
                SoundSource.PLAYERS, brand ? 0.6f : 0.16f, brand ? 1.25f : 1.75f, false);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        ensureLevel(mc.level);
        if (mc.level == null || !Config.ENABLE_WEAPON_SPECIAL_EFFECTS.getAsBoolean()) return;
        LavaKatanaMarkRenderer.onRenderLevel(event);
        if (IMPACTS.isEmpty()) return;
        var stack = event.getPoseStack();
        var camera = event.getCamera().getPosition();
        stack.pushPose();
        stack.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f pose = stack.last().pose();
        var buffers = mc.renderBuffers().bufferSource();
        var consumer = buffers.getBuffer(WeaponEffectRenderTypes.MOLTEN_GLOW);
        double now = mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for (Impact impact : IMPACTS) {
            float age = (float) (now - impact.startTick);
            if (age < 0 || age > 8 || impact.point.distanceToSqr(camera) > RANGE_SQR) continue;
            // Camera-facing accent stays readable, while emitted debris follows the hit direction.
            Vec3 normal = camera.subtract(impact.point).normalize();
            Vec3 right = normal.cross(new Vec3(0, 1, 0));
            if (right.lengthSqr() < 1.0E-6) right = new Vec3(1, 0, 0);
            right = right.normalize();
            Vec3 up = right.cross(normal).normalize();
            float fade = (float) Math.pow(1 - age / 8, 2);
            Vec3 slash = right.scale(0.9).add(up.scale(0.45)).scale(impact.scale * (0.75 + age * 0.07));
            WeaponContactGeometry.blade(consumer, pose, impact.point.subtract(slash), impact.point.add(slash),
                    normal, 0.21, fade, false, 255, 75, 10);
            if (age < 3) {
                Vec3 cross = up.scale(impact.scale * 0.45);
                WeaponContactGeometry.blade(consumer, pose, impact.point.subtract(cross), impact.point.add(cross),
                        normal, 0.10, 1 - age / 3, false, 255, 181, 75);
            }
            ring(consumer, pose, impact.point, right, up,
                    impact.scale * (0.12 + age * 0.12), 0.06 * fade, 255, 88, 8, Math.round(145 * fade));
        }
        stack.popPose();
        buffers.endBatch(WeaponEffectRenderTypes.MOLTEN_GLOW);
    }

    private static void ensureLevel(@Nullable ClientLevel level) {
        if (activeLevel == level) return;
        IMPACTS.clear();
        SWING_SOUNDS.clear();
        IMPACT_SOUNDS.clear();
        freezeUntil = 0;
        frozenSkill = null;
        activeLevel = level;
    }

    private record Impact(Vec3 point, long startTick, float scale) {}
}

package com.stardew.craft.client.weapon;

import com.stardew.craft.client.weapon.presentation.SkillPresentationClient;
import com.stardew.craft.combat.network.WeaponSkillAnimPayload;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class WeaponSkillAnimationClient {

    private static long startTick = -1;
    private static int durationTicks = 0;
    private static String weaponId;
    private static String skillId;
    private static Vec3 dragonBreathOrigin;
    private static Vec3 dragonBreathDir;
    private static long dragonBreathTick = -9999;
    private static Vec3 windSpireOrigin;
    private static long windSpireTick = -9999;
    private static final Map<Integer, WorldAction> WORLD_ACTIONS = new HashMap<>();
    private static ClientLevel actionLevel;

    private WeaponSkillAnimationClient() {}

    public static void start(int durationTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        start(null, null, durationTicks);
    }

    @SuppressWarnings("null")
    public static void start(String weaponId, String skillId, int durationTicks) {
        startLocalAnimation(weaponId, skillId, durationTicks);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || skillId == null) {
            return;
        }
        SkillEffectsClient.playSkillEffects(skillId, mc.player);
    }

    public static void start(WeaponSkillAnimPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        if (actionLevel != mc.level) {
            WORLD_ACTIONS.clear();
            actionLevel = mc.level;
        }
        long playbackStartTick = mc.level.getGameTime();
        WORLD_ACTIONS.put(
                payload.casterEntityId(),
                new WorldAction(payload, playbackStartTick)
        );

        Entity entity = mc.level.getEntity(payload.casterEntityId());
        Player caster = entity instanceof Player player ? player : null;
        boolean localCaster = mc.player != null && mc.player.getId() == payload.casterEntityId();
        if (localCaster) {
            startLocalAnimation(
                    payload.weaponId(),
                    payload.skillId(),
                    payload.actionDurationTicks(),
                    playbackStartTick
            );
        }

        boolean migrated = SkillPresentationClient.start(payload, playbackStartTick);
        if (!migrated && caster != null) {
            SkillEffectsClient.playSkillEffects(payload.skillId(), caster);
        }
    }

    @SuppressWarnings("null")
    private static void startLocalAnimation(String weaponId, String skillId, int durationTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        startLocalAnimation(weaponId, skillId, durationTicks, mc.level.getGameTime());
    }

    private static void startLocalAnimation(
            String weaponId,
            String skillId,
            int durationTicks,
            long actionStartTick
    ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        WeaponSkillAnimationClient.startTick = actionStartTick;
        WeaponSkillAnimationClient.durationTicks = Math.max(1, durationTicks);
        WeaponSkillAnimationClient.weaponId = weaponId;
        WeaponSkillAnimationClient.skillId = skillId;

        if ("dragon_breath_thrust".equals(skillId) && mc.level != null) {
            var player = mc.player;
            if (player == null) {
                return;
            }
            long now = mc.level.getGameTime();
            if (now - dragonBreathTick > 2) {
                dragonBreathOrigin = player.position();
                Vec3 look = player.getLookAngle();
                dragonBreathDir = new Vec3(look.x, 0.0, look.z);
                if (dragonBreathDir.lengthSqr() < 1.0E-4) {
                    dragonBreathDir = look;
                }
                dragonBreathDir = dragonBreathDir.normalize();
                dragonBreathTick = now;
            }
        }

        if ("wind_spire_thrust".equals(skillId) && mc.level != null) {
            var player = mc.player;
            if (player == null) {
                return;
            }
            long now = mc.level.getGameTime();
            if (now - windSpireTick > 2) {
                windSpireOrigin = player.position();
                windSpireTick = now;
            }
        }
    }

    public static Vec3 getDragonBreathOrigin() {
        return dragonBreathOrigin;
    }

    public static Vec3 getDragonBreathDir() {
        return dragonBreathDir;
    }

    public static long getDragonBreathTick() {
        return dragonBreathTick;
    }

    public static Vec3 getWindSpireOrigin() {
        return windSpireOrigin;
    }

    public static long getWindSpireTick() {
        return windSpireTick;
    }

    @SuppressWarnings("null")
    public static float getProgress(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || startTick < 0 || durationTicks <= 0) {
            return -1.0f;
        }
        float age = (mc.level.getGameTime() - startTick) + partialTick;
        float t = age / durationTicks;
        if (t >= 1.0f) {
            startTick = -1;
            durationTicks = 0;
            weaponId = null;
            skillId = null;
            return -1.0f;
        }
        return t;
    }

    public static boolean isActive() {
        return startTick >= 0 && durationTicks > 0;
    }

    public static void stop() {
        startTick = -1;
        durationTicks = 0;
        weaponId = null;
        skillId = null;
    }

    public static String getWeaponId() {
        return weaponId;
    }

    public static String getSkillId() {
        return skillId;
    }

    public static float getWorldActionProgress(int entityId, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || actionLevel != minecraft.level) {
            WORLD_ACTIONS.clear();
            actionLevel = minecraft.level;
            return -1.0f;
        }
        WorldAction worldAction = WORLD_ACTIONS.get(entityId);
        if (worldAction == null) {
            return -1.0f;
        }
        float progress = calculatePlaybackProgress(
                minecraft.level.getGameTime(),
                worldAction.playbackStartTick,
                partialTick,
                worldAction.payload.actionDurationTicks()
        );
        if (progress >= 1.0f) {
            WORLD_ACTIONS.remove(entityId);
            return -1.0f;
        }
        return Math.max(0.0f, progress);
    }

    public static WeaponSkillAnimPayload getWorldAction(int entityId) {
        WorldAction action = WORLD_ACTIONS.get(entityId);
        return action == null ? null : action.payload;
    }

    public static long getWorldActionPlaybackStartTick(int entityId) {
        WorldAction action = WORLD_ACTIONS.get(entityId);
        return action == null ? -1L : action.playbackStartTick;
    }

    static float calculatePlaybackProgress(
            long currentTick,
            long playbackStartTick,
            float partialTick,
            int durationTicks
    ) {
        float age = (currentTick - playbackStartTick) + partialTick;
        return age / Math.max(1, durationTicks);
    }

    private record WorldAction(
            WeaponSkillAnimPayload payload,
            long playbackStartTick
    ) {}
}

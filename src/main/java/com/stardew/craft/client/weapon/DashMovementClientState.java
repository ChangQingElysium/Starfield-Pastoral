package com.stardew.craft.client.weapon;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;

public final class DashMovementClientState {

    private static boolean active = false;
    private static long endTick = 0L;
    private static int totalTicks = 0;
    private static Vec3 startPos = null;
    private static Vec3 endPos = null;
    private static boolean startFxPlayed = false;

    private DashMovementClientState() {}

    public static void start(long nowTick, int durationTicks, Vec3 end) {
        if (durationTicks <= 0 || end == null) {
            clear();
            return;
        }
        active = true;
        totalTicks = Math.max(1, durationTicks);
        endTick = nowTick + totalTicks;
        startPos = null;
        endPos = end;
        startFxPlayed = false;
    }

    public static void clear() {
        active = false;
        endTick = 0L;
        totalTicks = 0;
        startPos = null;
        endPos = null;
        startFxPlayed = false;
    }

    @SuppressWarnings("null")
    public static boolean isActive(Player player) {
        if (!active || player == null || player.level() == null) {
            return false;
        }
        long nowTick = player.level().getGameTime();
        if (nowTick > endTick) {
            clear();
            return false;
        }
        return true;
    }

    @SuppressWarnings("null")
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            clear();
            return;
        }
        Player player = mc.player;
        if (!isActive(player)) {
            return;
        }
        if (player.isPassenger()) {
            return;
        }
        if (startPos == null) {
            startPos = player.position();
        }
        if (endPos == null) {
            clear();
            return;
        }

        int remaining = (int) Math.max(0L, endTick - player.level().getGameTime());
        if (remaining <= 0) {
            Vec3 vel = player.getDeltaMovement();
            player.setDeltaMovement(0.0, vel.y, 0.0);
            player.hasImpulse = true;
            clear();
            return;
        }

        int elapsed = Math.max(0, totalTicks - remaining);
        int windupTicks = Math.max(1, Math.min(2, totalTicks - 1));
        if (!startFxPlayed) {
            startFxPlayed = true;
            if (!IronWindVisuals.ownsWindDash(player) && !PirateSilverVisuals.ownsDash(player)) player.playSound(SoundEvents.ENCHANTMENT_TABLE_USE, 0.35f, 1.25f);
        }
        if (elapsed == windupTicks && !IronWindVisuals.ownsWindDash(player) && !PirateSilverVisuals.ownsDash(player)) {
            player.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 1.35f);
            player.playSound(SoundEvents.TRIDENT_THROW.value(), 0.5f, 1.2f);
        }

        Vec3 current = player.position();
        double speed = endPos.subtract(startPos).horizontalDistance() / totalTicks;
        Vec3 velocity = com.stardew.craft.combat.skill.DashMotionRules.horizontalVelocity(current,endPos,speed);
        player.setDeltaMovement(velocity.x,player.getDeltaMovement().y,velocity.z);
        player.hasImpulse = true;
        // Set next-tick velocity only. Calling move here would integrate a second time per tick.

        if (elapsed >= windupTicks && !IronWindVisuals.ownsWindDash(player) && !PirateSilverVisuals.ownsDash(player)) {
            spawnDashTrail(player, current, endPos);
        }
    }

    @SuppressWarnings("null")
    private static void spawnDashTrail(Player player, Vec3 current, Vec3 end) {
        if (player.level() == null || end == null) return;
        Vec3 dir = end.subtract(current);
        if (dir.lengthSqr() < 1.0e-4) return;
        Vec3 back = dir.normalize().scale(-0.4);
        double x = current.x + back.x;
        double y = current.y + 0.6;
        double z = current.z + back.z;
        player.level().addParticle(ParticleTypes.CLOUD, x, y, z, 0.0, 0.0, 0.0);
        if (player.level().getRandom().nextFloat() < 0.35f) {
            player.level().addParticle(ParticleTypes.CRIT, x, y + 0.1, z, 0.0, 0.0, 0.0);
        }
    }

    public static void clearIfNoPlayer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            clear();
        }
    }
}

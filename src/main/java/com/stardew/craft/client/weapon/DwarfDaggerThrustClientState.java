package com.stardew.craft.client.weapon;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;

public final class DwarfDaggerThrustClientState {

    private static boolean active = false;
    private static long endTick = 0L;
    private static int totalTicks = 0;
    private static Vec3 startPos = null;
    private static Vec3 endPos = null;

    private DwarfDaggerThrustClientState() {}

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
    }

    public static void clear() {
        active = false;
        endTick = 0L;
        totalTicks = 0;
        startPos = null;
        endPos = null;
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

        Vec3 velocity = com.stardew.craft.combat.skill.DashMotionRules.horizontalVelocity(
                player.position(),endPos,endPos.subtract(startPos).horizontalDistance()/totalTicks);
        player.setDeltaMovement(velocity.x,player.getDeltaMovement().y,velocity.z);
        player.hasImpulse = true;
    }

    public static void clearIfNoPlayer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            clear();
        }
    }
}

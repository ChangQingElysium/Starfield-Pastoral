package com.stardew.craft.client.weapon;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;

public final class IridiumNeedleFrenzyClientState {

    private static boolean active = false;
    private static long endTick = 0L;
    private static int totalTicks = 0;

    private IridiumNeedleFrenzyClientState() {}

    public static void start(long nowTick, int durationTicks) {
        if (durationTicks <= 0) {
            clear();
            return;
        }
        active = true;
        totalTicks = Math.max(1, durationTicks);
        endTick = nowTick + totalTicks;
    }

    public static void clear() {
        active = false;
        endTick = 0L;
        totalTicks = 0;
    }

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

    public static int getRemainingTicks(Player player) {
        if (!isActive(player)) {
            return 0;
        }
        long nowTick = player.level().getGameTime();
        return (int) Math.max(0, endTick - nowTick);
    }

    public static int getTotalTicks() {
        return Math.max(1, totalTicks);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            clear();
        }
    }

    public static void clearIfNoPlayer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            clear();
        }
    }
}

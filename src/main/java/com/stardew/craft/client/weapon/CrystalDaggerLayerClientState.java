package com.stardew.craft.client.weapon;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.Objects;

public final class CrystalDaggerLayerClientState {

    private static int stacks = 0;
    private static long endTick = 0L;
    private static int totalTicks = 0;

    private CrystalDaggerLayerClientState() {}

    public static void start(long nowTick, int durationTicks, int stackCount) {
        stacks = Math.max(0, stackCount);
        totalTicks = Math.max(1, durationTicks);
        endTick = nowTick + totalTicks;
    }

    public static void clear() {
        stacks = 0;
        endTick = 0L;
        totalTicks = 0;
    }

    public static boolean isActive(Player player) {
        if (stacks <= 0 || player == null || player.level() == null) {
            return false;
        }
        long nowTick = player.level().getGameTime();
        if (nowTick > endTick) {
            clear();
            return false;
        }
        return true;
    }

    public static int getStacks(Player player) {
        if (!isActive(player)) {
            return 0;
        }
        return stacks;
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
            return;
        }
        var level = Objects.requireNonNull(mc.level, "level");
        if (stacks <= 0) {
            return;
        }
        long nowTick = level.getGameTime();
        if (nowTick > endTick) {
            clear();
        }
    }
}

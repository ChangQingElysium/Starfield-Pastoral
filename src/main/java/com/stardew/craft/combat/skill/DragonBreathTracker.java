package com.stardew.craft.combat.skill;

import com.stardew.craft.combat.network.DragonBreathPayload;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 龙牙弯刀 - 龙息积攒
 * 普攻命中：+1层；暴击命中：+3层；上限20层
 */
public final class DragonBreathTracker {

    public static final int MAX_STACKS = 20;
    public static final int MAJOR_THRESHOLD = 15;

    // Stacks are a player combat resource and intentionally survive dimension
    // travel. Spatial thrust execution belongs to WeaponSkillRuntime.
    private static final Map<UUID, Integer> STACKS = new HashMap<>();

    private DragonBreathTracker() {}

    public static int getStacks(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        return STACKS.getOrDefault(player.getUUID(), 0);
    }

    public static void setStacks(ServerPlayer player, int stacks) {
        if (player == null) {
            return;
        }
        int clamped = clampStacks(stacks);
        if (clamped == 0) {
            STACKS.remove(player.getUUID());
        } else {
            STACKS.put(player.getUUID(), clamped);
        }
        PacketDistributor.sendToPlayer(player, new DragonBreathPayload(clamped));
    }

    /** Reconciles the client with this non-persistent combat resource. */
    public static void sync(ServerPlayer player) {
        if (player != null) {
            PacketDistributor.sendToPlayer(
                    player,
                    new DragonBreathPayload(getStacks(player))
            );
        }
    }

    /** Clears the resource while the concrete player can still receive sync. */
    public static void clear(ServerPlayer player) {
        if (player != null) {
            setStacks(player, 0);
        }
    }

    public static void addStacks(ServerPlayer player, int delta) {
        if (player == null || delta == 0) {
            return;
        }
        int current = getStacks(player);
        setStacks(player, stacksAfterDelta(current, delta));
    }

    public static int consumeAll(ServerPlayer player) {
        int current = getStacks(player);
        setStacks(player, 0);
        return current;
    }

    public static int consumeForMajor(ServerPlayer player) {
        int current = getStacks(player);
        int consumed = consumableMajorStacks(current);
        if (consumed == 0) {
            return 0;
        }
        setStacks(player, 0);
        return consumed;
    }

    public static boolean canCastMajor(ServerPlayer player) {
        return canCastMajor(getStacks(player));
    }

    static int clampStacks(int stacks) {
        return Mth.clamp(stacks, 0, MAX_STACKS);
    }

    static int stacksAfterDelta(int current, int delta) {
        return clampStacks(current + delta);
    }

    static boolean canCastMajor(int stacks) {
        return clampStacks(stacks) >= MAJOR_THRESHOLD;
    }

    static int consumableMajorStacks(int stacks) {
        int clamped = clampStacks(stacks);
        return clamped >= MAJOR_THRESHOLD ? clamped : 0;
    }

}

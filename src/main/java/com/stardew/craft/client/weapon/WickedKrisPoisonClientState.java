package com.stardew.craft.client.weapon;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Client projection of all target-scoped Wicked Kris states owned by us. */
public final class WickedKrisPoisonClientState {
    private static final Map<UUID, Status> STATUSES = new HashMap<>();

    private WickedKrisPoisonClientState() {
    }

    public static void upsert(
            UUID targetId,
            long nowTick,
            int stacks,
            int poisonRemainingTicks,
            int poisonTotalTicks,
            int detonateRemainingTicks,
            int detonateTotalTicks
    ) {
        if (targetId == null) {
            return;
        }
        Status previous = STATUSES.get(targetId);
        long poisonEndTick = stacks > 0 && poisonRemainingTicks > 0
                ? nowTick + poisonRemainingTicks
                : 0L;
        long detonateEndTick;
        int resolvedDetonateTotal;
        if (detonateRemainingTicks < 0 && previous != null) {
            detonateEndTick = previous.detonateEndTick();
            resolvedDetonateTotal = previous.detonateTotalTicks();
        } else if (detonateRemainingTicks > 0) {
            detonateEndTick = nowTick + detonateRemainingTicks;
            resolvedDetonateTotal = Math.max(
                    1,
                    detonateTotalTicks > 0
                            ? detonateTotalTicks
                            : detonateRemainingTicks
            );
        } else {
            detonateEndTick = 0L;
            resolvedDetonateTotal = 0;
        }
        Status status = new Status(
                Math.max(0, stacks),
                poisonEndTick,
                Math.max(0, poisonTotalTicks),
                detonateEndTick,
                resolvedDetonateTotal
        );
        if (!status.hasPoison(nowTick) && !status.hasDetonation(nowTick)) {
            STATUSES.remove(targetId);
        } else {
            STATUSES.put(targetId, status);
        }
    }

    public static void remove(UUID targetId) {
        if (targetId != null) {
            STATUSES.remove(targetId);
        }
    }

    public static void clearAll() {
        STATUSES.clear();
    }

    public static boolean hasPoison(Player player) {
        return hasPoisonAt(nowTick(player));
    }

    public static int getStacks(Player player) {
        return getStacksAt(nowTick(player));
    }

    static boolean hasPoisonAt(long nowTick) {
        return strongestPoison(nowTick) != null;
    }

    static int getStacksAt(long nowTick) {
        Status status = strongestPoison(nowTick);
        return status == null ? 0 : status.stacks();
    }

    public static int getRemainingTicks(Player player) {
        return getRemainingTicksAt(nowTick(player));
    }

    static int getRemainingTicksAt(long nowTick) {
        Status status = strongestPoison(nowTick);
        return status == null
                ? 0
                : remainingTicks(status.poisonEndTick(), nowTick);
    }

    public static int getTotalTicks() {
        return getTotalTicksAt(clientNowTick());
    }

    static int getTotalTicksAt(long nowTick) {
        Status status = strongestPoison(nowTick);
        return status == null ? 1 : Math.max(1, status.poisonTotalTicks());
    }

    public static boolean hasDetonation(Player player) {
        return hasDetonationAt(nowTick(player));
    }

    public static int getDetonationRemainingTicks(Player player) {
        return getDetonationRemainingTicksAt(nowTick(player));
    }

    static boolean hasDetonationAt(long nowTick) {
        return earliestDetonation(nowTick) != null;
    }

    static int getDetonationRemainingTicksAt(long nowTick) {
        Status status = earliestDetonation(nowTick);
        return status == null
                ? 0
                : remainingTicks(status.detonateEndTick(), nowTick);
    }

    public static int getDetonationTotalTicks() {
        return getDetonationTotalTicksAt(clientNowTick());
    }

    static int getDetonationTotalTicksAt(long nowTick) {
        Status status = earliestDetonation(nowTick);
        return status == null ? 1 : Math.max(1, status.detonateTotalTicks());
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            clearAll();
            return;
        }
        pruneExpired(minecraft.level.getGameTime());
    }

    static int trackedTargetCount() {
        return STATUSES.size();
    }

    private static Status strongestPoison(long nowTick) {
        pruneExpired(nowTick);
        Status best = null;
        for (Status status : STATUSES.values()) {
            if (!status.hasPoison(nowTick)) {
                continue;
            }
            if (best == null
                    || status.stacks() > best.stacks()
                    || status.stacks() == best.stacks()
                    && status.poisonEndTick() > best.poisonEndTick()) {
                best = status;
            }
        }
        return best;
    }

    private static Status earliestDetonation(long nowTick) {
        pruneExpired(nowTick);
        Status best = null;
        for (Status status : STATUSES.values()) {
            if (!status.hasDetonation(nowTick)) {
                continue;
            }
            if (best == null
                    || status.detonateEndTick() < best.detonateEndTick()) {
                best = status;
            }
        }
        return best;
    }

    private static void pruneExpired(long nowTick) {
        STATUSES.replaceAll((ignored, status) -> status.expire(nowTick));
        STATUSES.values().removeIf(status ->
                !status.hasPoison(nowTick)
                        && !status.hasDetonation(nowTick)
        );
    }

    private static int remainingTicks(long endTick, long nowTick) {
        return (int) Math.max(0L, endTick - nowTick);
    }

    private static long nowTick(Player player) {
        return player == null || player.level() == null
                ? Long.MAX_VALUE
                : player.level().getGameTime();
    }

    private static long clientNowTick() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null
                ? Long.MAX_VALUE
                : minecraft.level.getGameTime();
    }

    private record Status(
            int stacks,
            long poisonEndTick,
            int poisonTotalTicks,
            long detonateEndTick,
            int detonateTotalTicks
    ) {
        private boolean hasPoison(long nowTick) {
            return stacks > 0 && nowTick <= poisonEndTick;
        }

        private boolean hasDetonation(long nowTick) {
            return detonateEndTick > 0L && nowTick <= detonateEndTick;
        }

        private Status expire(long nowTick) {
            return new Status(
                    hasPoison(nowTick) ? stacks : 0,
                    hasPoison(nowTick) ? poisonEndTick : 0L,
                    hasPoison(nowTick) ? poisonTotalTicks : 0,
                    hasDetonation(nowTick) ? detonateEndTick : 0L,
                    hasDetonation(nowTick) ? detonateTotalTicks : 0
            );
        }
    }
}

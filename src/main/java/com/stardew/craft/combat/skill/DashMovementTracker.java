package com.stardew.craft.combat.skill;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.network.DashMovementPayload;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.WeaponSkillMovementArbiter;
import com.stardew.craft.combat.skill.runtime.WeaponSkillMovementControl;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings("null")
public final class DashMovementTracker {

    /** Identifies one exact shared dash without exposing its mutable state. */
    public record Handle(UUID playerId, UUID movementId) {}

    private static final class State
            implements WeaponSkillMovementArbiter.Owner {
        private final UUID playerId;
        private final int playerEntityId;
        private final UUID movementId;
        private final ResourceKey<Level> dimension;
        private final Vec3 end;
        private final Vec3 step;
        private final long endTick;
        private WeaponSkillMovementArbiter.Lease lease;
        private State(UUID playerId, int playerEntityId, UUID movementId,
                      ResourceKey<Level> dimension, Vec3 end, Vec3 step,
                      long endTick) {
            this.playerId = playerId;
            this.playerEntityId = playerEntityId;
            this.movementId = movementId;
            this.dimension = dimension;
            this.end = end;
            this.step = step;
            this.endTick = endTick;
        }

        @Override
        public void onMovementRevoked(ServerPlayer player) {
            if (ACTIVE.get(playerId) == this) {
                ACTIVE.remove(playerId);
            }
            sendClientState(player, false, 0, null);
        }
    }

    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    private DashMovementTracker() {}

    @SuppressWarnings("null")
    public static void start(
            ServerPlayer player,
            long nowTick,
            Vec3 end,
            int durationTicks
    ) {
        startExact(player, nowTick, end, durationTicks);
    }

    @SuppressWarnings("null")
    public static Handle startExact(
            ServerPlayer player,
            long nowTick,
            Vec3 end,
            int durationTicks
    ) {
        if (player == null || end == null || durationTicks <= 0) {
            return null;
        }
        if (WeaponSkillMovementControl.isLocked(player, nowTick)) {
            return null;
        }
        Vec3 start = player.position();
        Vec3 diff = end.subtract(start);
        Vec3 step = new Vec3(diff.x / durationTicks, 0.0, diff.z / durationTicks);
        UUID movementId = UUID.randomUUID();
        State state = new State(player.getUUID(), player.getId(), movementId,
            player.level().dimension(), end, step, nowTick + durationTicks);
        state.lease = WeaponSkillMovementArbiter.claim(player, state);
        ACTIVE.put(player.getUUID(), state);
        try {
            sendClientState(player, true, durationTicks, end);
        } catch (RuntimeException exception) {
            if (ACTIVE.get(player.getUUID()) == state) {
                ACTIVE.remove(player.getUUID());
            }
            WeaponSkillMovementArbiter.release(state.lease);
            try {
                sendClientState(player, false, 0, null);
            } catch (RuntimeException stopFailure) {
                exception.addSuppressed(stopFailure);
            }
            throw exception;
        }
        return new Handle(player.getUUID(), movementId);
    }

    /**
     * Starts a dash during a runtime handler's begin phase and attaches exact
     * compensation until that begin succeeds. A later begin failure cancels
     * only this movement handle, never a replacement dash.
     */
    public static Handle startDuringBegin(
            SkillInstance instance,
            ServerPlayer player,
            long nowTick,
            Vec3 end,
            int durationTicks
    ) {
        java.util.Objects.requireNonNull(instance, "instance");
        Handle handle = startExact(player, nowTick, end, durationTicks);
        if (handle == null) {
            return null;
        }
        try {
            instance.registerBeginFailureCleanup(
                    () -> cancel(player, handle)
            );
        } catch (RuntimeException registrationFailure) {
            try {
                cancel(player, handle);
            } catch (RuntimeException cleanupFailure) {
                if (cleanupFailure != registrationFailure) {
                    registrationFailure.addSuppressed(cleanupFailure);
                }
            }
            throw registrationFailure;
        }
        return handle;
    }

    @SuppressWarnings("null")
    public static void tickServer(MinecraftServer server) {
        if (server == null || ACTIVE.isEmpty()) {
            return;
        }
        State[] states = ACTIVE.values().toArray(State[]::new);
        for (State state : states) {
            if (ACTIVE.get(state.playerId) != state) {
                continue;
            }
            try {
                if (!tickState(server, state)) {
                    ACTIVE.remove(state.playerId, state);
                }
            } catch (RuntimeException exception) {
                ACTIVE.remove(state.playerId, state);
                WeaponSkillMovementArbiter.release(state.lease);
                stopClientAfterFailure(server, state, exception);
                StardewCraft.LOGGER.error(
                        "Ticking shared dash movement failed for player {}",
                        state.playerId,
                        exception
                );
            }
        }
    }

    /**
     * Binary-compatible bridge for the former event callback. This method is
     * deliberately not subscribed; WeaponSkillPostServerRuntime owns the sole
     * server-post tick entry.
     */
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event != null) {
            tickServer(event.getServer());
        }
    }

    private static boolean tickState(
            MinecraftServer server,
            State state
    ) {
        if (!WeaponSkillMovementArbiter.owns(state.lease)) {
            return false;
        }
        ServerLevel level = server.getLevel(state.dimension);
        if (level == null) {
            sendClientState(server, state, false, 0, null);
            WeaponSkillMovementArbiter.release(state.lease);
            return false;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(state.playerId);
        if (player == null) {
            WeaponSkillMovementArbiter.release(state.lease);
            return false;
        }
        if (player.getId() != state.playerEntityId
                || !player.isAlive() || player.isRemoved()
                || player.level() != level) {
            sendClientState(player, false, 0, null);
            WeaponSkillMovementArbiter.release(state.lease);
            return false;
        }
        long nowTick = level.getGameTime();
        if (WeaponSkillMovementControl.isLocked(player, nowTick)) {
            sendClientState(player, false, 0, null);
            WeaponSkillMovementArbiter.release(state.lease);
            return false;
        }
        if (com.stardew.craft.time.StardewTimePauseService
                .shouldPauseLevel(level)) {
            return true;
        }
        if (nowTick > state.endTick) {
            sendClientState(server, state, false, 0, null);
            WeaponSkillMovementArbiter.release(state.lease);
            return false;
        }

        Vec3 current = player.position();
        Vec3 desired = current.add(state.step);
        if (nowTick + 1 >= state.endTick) {
            desired = state.end;
        }
        desired = new Vec3(desired.x, player.getY(), desired.z);

        Vec3 safe = findSafePosition(
                player,
                adjustForCollision(player, desired)
        );
        if (safe == null) {
            sendClientState(player, false, 0, null);
            WeaponSkillMovementArbiter.release(state.lease);
            return false;
        }

        Vec3 desiredVel = safe.subtract(current);
        Vec3 currentVel = player.getDeltaMovement();
        Vec3 nextVel = currentVel.add(
                desiredVel.subtract(currentVel).scale(0.6)
        );
        player.setDeltaMovement(nextVel.x, currentVel.y, nextVel.z);
        player.hasImpulse = true;
        player.move(
                net.minecraft.world.entity.MoverType.SELF,
                player.getDeltaMovement()
        );
        player.fallDistance = 0.0F;

        Vec3 afterMove = player.position();
        if (afterMove.subtract(current).horizontalDistanceSqr() < 1.0e-4) {
            player.teleportTo(safe.x, safe.y, safe.z);
            player.fallDistance = 0.0F;
        }
        return true;
    }

    private static void stopClientAfterFailure(
            MinecraftServer server,
            State state,
            RuntimeException failure
    ) {
        ServerPlayer player = server.getPlayerList().getPlayer(state.playerId);
        if (player == null) {
            return;
        }
        try {
            sendClientState(player, false, 0, null);
        } catch (RuntimeException stopFailure) {
            failure.addSuppressed(stopFailure);
        }
    }

    /** Returns whether the exact movement represented by the handle still owns the player. */
    public static boolean isActive(Handle handle) {
        if (handle == null) {
            return false;
        }
        State state = ACTIVE.get(handle.playerId());
        return state != null
                && state.movementId.equals(handle.movementId())
                && WeaponSkillMovementArbiter.owns(state.lease);
    }

    /** Cancels only the represented movement, never a newer replacement dash. */
    public static boolean cancel(ServerPlayer player, Handle handle) {
        if (player == null || handle == null
                || !player.getUUID().equals(handle.playerId())) {
            return false;
        }
        State state = ACTIVE.get(handle.playerId());
        if (state == null || !state.movementId.equals(handle.movementId())) {
            return false;
        }
        ACTIVE.remove(handle.playerId(), state);
        WeaponSkillMovementArbiter.release(state.lease);
        sendClientState(player, false, 0, null);
        return true;
    }

    @SuppressWarnings("null")
    private static void sendClientState(MinecraftServer server, State state, boolean active,
                                        int durationTicks, Vec3 end) {
        if (server == null || state == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(state.playerId);
        if (player == null) return;
        sendClientState(player, active, durationTicks, end);
    }

    @SuppressWarnings("null")
    private static void sendClientState(ServerPlayer player, boolean active,
                                        int durationTicks, Vec3 end) {
        if (player == null) return;
        double endX = end != null ? end.x : 0.0;
        double endY = end != null ? end.y : 0.0;
        double endZ = end != null ? end.z : 0.0;
        PacketDistributor.sendToPlayer(player,
            new DashMovementPayload(active, durationTicks, endX, endY, endZ));
    }

    @SuppressWarnings("null")
    private static Vec3 adjustForCollision(ServerPlayer player, Vec3 desired) {
        Vec3 start = player.position();
        Vec3 look = desired.subtract(start);
        if (look.lengthSqr() < 1.0E-6) {
            return desired;
        }
        Vec3 dir = new Vec3(look.x, 0.0, look.z).normalize();
        HitResult hit = player.level().clip(new ClipContext(
            start.add(0, player.getBbHeight() * 0.5, 0),
            desired.add(0, player.getBbHeight() * 0.5, 0),
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            player
        ));

        if (hit.getType() != HitResult.Type.MISS) {
            Vec3 hitPos = hit.getLocation();
            return hitPos.subtract(dir.scale(0.4));
        }
        return desired;
    }

    @SuppressWarnings("null")
    private static Vec3 findSafePosition(Player player, Vec3 desired) {
        if (desired == null) return null;
        AABB box = player.getBoundingBox().move(desired.x - player.getX(), desired.y - player.getY(), desired.z - player.getZ());
        if (player.level().noCollision(player, box)) {
            return desired;
        }
        Vec3 raised = desired.add(0, 0.25, 0);
        AABB boxUp = player.getBoundingBox().move(raised.x - player.getX(), raised.y - player.getY(), raised.z - player.getZ());
        if (player.level().noCollision(player, boxUp)) {
            return raised;
        }
        return null;
    }

    /** Stops shared dash state while the concrete player can still be synced. */
    public static void clear(ServerPlayer player) {
        if (player == null) {
            return;
        }
        State state = ACTIVE.remove(player.getUUID());
        if (state != null) {
            WeaponSkillMovementArbiter.release(state.lease);
            sendClientState(player, false, 0, null);
        }
    }

    /**
     * Binary-compatible offline cleanup bridge. Online lifecycle paths should
     * prefer {@link #clear(ServerPlayer)} so the client also receives stop.
     */
    public static void removePlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        State state = ACTIVE.remove(playerId);
        if (state != null) {
            WeaponSkillMovementArbiter.release(state.lease);
        }
    }
}

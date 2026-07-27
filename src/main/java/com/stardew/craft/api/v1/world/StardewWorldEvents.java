package com.stardew.craft.api.v1.world;

import com.stardew.craft.api.v1.internal.world.StardewWorldEventRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Experimental bounded lifecycle for temporary world modifications.
 *
 * <p>Handlers only prepare an immutable block plan. StardewCraft preflights the complete plan,
 * commits it on the server thread, rolls back partial failures, persists the exact inverse, and
 * can clean it up even while the defining addon is absent. Block entities are intentionally
 * rejected because restoring only their block state would lose inventory or custom data.
 */
public final class StardewWorldEvents {
    public static final int MAX_BLOCK_CHANGES = 1_024;
    public static final int MAX_PERSISTENT_DATA_BYTES = 65_536;

    private StardewWorldEvents() {
    }

    /**
     * Registers one event type. Event type IDs are unique; duplicate and late registrations fail.
     */
    public static void register(ResourceLocation eventType, Handler handler) {
        StardewWorldEventRegistry.register(eventType, handler);
    }

    /**
     * Resolves and commits one event instance. Reusing an active instance ID is idempotent.
     */
    public static Result start(Context context) {
        return StardewWorldEventRegistry.start(context);
    }

    /**
     * Atomically restores the blocks captured by an active event.
     *
     * <p>The saved inverse is sufficient even if the event handler is no longer installed.
     */
    public static Result cleanup(MinecraftServer server, UUID instanceId) {
        return StardewWorldEventRegistry.cleanup(server, instanceId);
    }

    /** Read-only active event receipts in deterministic instance-ID order. */
    public static List<Receipt> active(MinecraftServer server) {
        return StardewWorldEventRegistry.active(server);
    }

    /**
     * Returns an event's owner payload only when the caller supplies its exact event type.
     */
    public static Optional<CompoundTag> persistentData(
            MinecraftServer server,
            UUID instanceId,
            ResourceLocation eventType
    ) {
        return StardewWorldEventRegistry.persistentData(
                server, instanceId, eventType);
    }

    @FunctionalInterface
    public interface Handler {
        Plan prepare(Context context);
    }

    public record Context(
            ResourceLocation eventType,
            UUID instanceId,
            ServerLevel level,
            BlockPos origin,
            long seed,
            CompoundTag requestData
    ) {
        public Context {
            Objects.requireNonNull(eventType, "eventType");
            Objects.requireNonNull(instanceId, "instanceId");
            Objects.requireNonNull(level, "level");
            origin = Objects.requireNonNull(origin, "origin").immutable();
            requestData = requestData == null
                    ? new CompoundTag()
                    : requestData.copy();
        }

        @Override
        public CompoundTag requestData() {
            return requestData.copy();
        }
    }

    public record Plan(
            List<BlockChange> changes,
            CompoundTag persistentData
    ) {
        public Plan {
            changes = List.copyOf(Objects.requireNonNull(
                    changes, "changes"));
            persistentData = persistentData == null
                    ? new CompoundTag()
                    : persistentData.copy();
        }

        public static Plan of(List<BlockChange> changes) {
            return new Plan(changes, new CompoundTag());
        }

        @Override
        public CompoundTag persistentData() {
            return persistentData.copy();
        }
    }

    public record BlockChange(
            BlockPos position,
            BlockState expected,
            BlockState replacement
    ) {
        public BlockChange {
            position = Objects.requireNonNull(
                    position, "position").immutable();
            Objects.requireNonNull(expected, "expected");
            Objects.requireNonNull(replacement, "replacement");
        }
    }

    public record Receipt(
            UUID instanceId,
            ResourceLocation eventType,
            ResourceKey<Level> dimension,
            BlockPos origin,
            int changedBlocks,
            long committedGameTime,
            boolean recoveryRequired
    ) {
        public Receipt {
            Objects.requireNonNull(instanceId, "instanceId");
            Objects.requireNonNull(eventType, "eventType");
            Objects.requireNonNull(dimension, "dimension");
            origin = Objects.requireNonNull(origin, "origin").immutable();
            if (changedBlocks < 0 || changedBlocks > MAX_BLOCK_CHANGES) {
                throw new IllegalArgumentException(
                        "changedBlocks is outside the world-event budget");
            }
        }
    }

    public record Result(Status status, Optional<Receipt> receipt) {
        public Result {
            Objects.requireNonNull(status, "status");
            receipt = receipt == null ? Optional.empty() : receipt;
        }

        public boolean success() {
            return status == Status.COMMITTED
                    || status == Status.CLEANED
                    || status == Status.ALREADY_ACTIVE;
        }
    }

    public enum Status {
        COMMITTED,
        CLEANED,
        ALREADY_ACTIVE,
        NOT_ACTIVE,
        HANDLER_MISSING,
        LEVEL_UNAVAILABLE,
        PREPARE_FAILED,
        INVALID_PLAN,
        CONFLICT,
        COMMIT_FAILED,
        ROLLBACK_FAILED
    }
}

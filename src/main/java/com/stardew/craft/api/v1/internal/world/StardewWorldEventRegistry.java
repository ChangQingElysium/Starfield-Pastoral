package com.stardew.craft.api.v1.internal.world;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.internal.extension.OrderedExtensionRegistry;
import com.stardew.craft.api.v1.world.StardewWorldEvents;
import com.stardew.craft.world.event.WorldEventSavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Internal authoritative runtime behind {@link StardewWorldEvents}. */
public final class StardewWorldEventRegistry {
    private static final ResourceLocation EXTENSION_POINT =
            ResourceLocation.fromNamespaceAndPath(
                    StardewCraft.MODID, "world_events");
    private static final OrderedExtensionRegistry<
            StardewWorldEvents.Handler> HANDLERS =
            new OrderedExtensionRegistry<>(EXTENSION_POINT);

    private StardewWorldEventRegistry() {
    }

    public static void register(
            ResourceLocation eventType,
            StardewWorldEvents.Handler handler
    ) {
        HANDLERS.register(eventType, 0, handler);
    }

    public static StardewWorldEvents.Result start(
            StardewWorldEvents.Context context
    ) {
        Objects.requireNonNull(context, "context");
        MinecraftServer server = context.level().getServer();
        requireServerThread(server);
        WorldEventSavedData data = WorldEventSavedData.get(server);
        Optional<WorldEventSavedData.ActiveEvent> existing =
                data.find(context.instanceId());
        if (existing.isPresent()) {
            return result(
                    StardewWorldEvents.Status.ALREADY_ACTIVE,
                    existing.orElseThrow());
        }
        Optional<OrderedExtensionRegistry.Entry<
                StardewWorldEvents.Handler>> registered =
                HANDLERS.entries().stream()
                        .filter(entry -> entry.id().equals(
                                context.eventType()))
                        .findFirst();
        if (registered.isEmpty()) {
            return result(
                    StardewWorldEvents.Status.HANDLER_MISSING, null);
        }

        StardewWorldEvents.Plan plan;
        try {
            OrderedExtensionRegistry.Entry<
                    StardewWorldEvents.Handler> entry =
                    registered.orElseThrow();
            plan = HANDLERS.invoke(
                    entry,
                    handler -> handler.prepare(context));
        } catch (RuntimeException | Error exception) {
            StardewCraft.LOGGER.error(
                    "[World event] Handler {} failed while preparing {}",
                    context.eventType(), context.instanceId(),
                    exception);
            return result(
                    StardewWorldEvents.Status.PREPARE_FAILED, null);
        }
        if (plan == null
                || !withinPersistentDataBudget(
                        plan.persistentData())) {
            return result(
                    StardewWorldEvents.Status.INVALID_PLAN, null);
        }

        WorldEventTransaction.Outcome outcome =
                WorldEventTransaction.commit(
                        new ServerLevelAccess(context.level()),
                        plan.changes());
        if (outcome.status()
                != StardewWorldEvents.Status.COMMITTED) {
            if (outcome.status()
                    == StardewWorldEvents.Status.ROLLBACK_FAILED) {
                WorldEventSavedData.ActiveEvent recovery =
                        activeEvent(context, plan, true);
                data.put(recovery);
                return result(outcome.status(), recovery);
            }
            return result(outcome.status(), null);
        }

        WorldEventSavedData.ActiveEvent committed =
                activeEvent(context, plan, false);
        data.put(committed);
        return result(
                StardewWorldEvents.Status.COMMITTED, committed);
    }

    public static StardewWorldEvents.Result cleanup(
            MinecraftServer server,
            UUID instanceId
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(instanceId, "instanceId");
        requireServerThread(server);
        WorldEventSavedData data = WorldEventSavedData.get(server);
        Optional<WorldEventSavedData.ActiveEvent> found =
                data.find(instanceId);
        if (found.isEmpty()) {
            return result(
                    StardewWorldEvents.Status.NOT_ACTIVE, null);
        }
        WorldEventSavedData.ActiveEvent event =
                found.orElseThrow();
        ServerLevel level = server.getLevel(event.dimension());
        if (level == null) {
            return result(
                    StardewWorldEvents.Status.LEVEL_UNAVAILABLE,
                    event);
        }

        WorldEventTransaction.Outcome outcome =
                WorldEventTransaction.cleanup(
                        new ServerLevelAccess(level),
                        event.changes(),
                        event.recoveryRequired());
        if (outcome.status()
                == StardewWorldEvents.Status.CLEANED) {
            data.remove(instanceId);
            return result(
                    StardewWorldEvents.Status.CLEANED, event);
        }
        if (outcome.status()
                == StardewWorldEvents.Status.ROLLBACK_FAILED) {
            data.markRecoveryRequired(instanceId);
            event = data.find(instanceId).orElse(event);
        }
        return result(outcome.status(), event);
    }

    public static List<StardewWorldEvents.Receipt> active(
            MinecraftServer server
    ) {
        Objects.requireNonNull(server, "server");
        requireServerThread(server);
        return WorldEventSavedData.get(server).snapshot().stream()
                .map(WorldEventSavedData.ActiveEvent::receipt)
                .toList();
    }

    public static Optional<CompoundTag> persistentData(
            MinecraftServer server,
            UUID instanceId,
            ResourceLocation eventType
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(eventType, "eventType");
        requireServerThread(server);
        return WorldEventSavedData.get(server)
                .find(instanceId)
                .filter(event -> event.eventType().equals(eventType))
                .map(WorldEventSavedData.ActiveEvent::persistentData);
    }

    private static WorldEventSavedData.ActiveEvent activeEvent(
            StardewWorldEvents.Context context,
            StardewWorldEvents.Plan plan,
            boolean recoveryRequired
    ) {
        return new WorldEventSavedData.ActiveEvent(
                context.instanceId(),
                context.eventType(),
                context.level().dimension(),
                context.origin(),
                plan.changes(),
                plan.persistentData(),
                context.level().getGameTime(),
                recoveryRequired);
    }

    private static StardewWorldEvents.Result result(
            StardewWorldEvents.Status status,
            WorldEventSavedData.ActiveEvent event
    ) {
        return new StardewWorldEvents.Result(
                status,
                event == null
                        ? Optional.empty()
                        : Optional.of(event.receipt()));
    }

    static boolean withinPersistentDataBudget(
            CompoundTag data
    ) {
        int estimatedSize = data.sizeInBytes();
        if (estimatedSize < 0
                || estimatedSize
                > StardewWorldEvents
                        .MAX_PERSISTENT_DATA_BYTES) {
            return false;
        }
        try (ByteArrayOutputStream output =
                     new ByteArrayOutputStream();
             DataOutputStream encoded =
                     new DataOutputStream(output)) {
            data.write(encoded);
            encoded.flush();
            return output.size()
                    <= StardewWorldEvents
                            .MAX_PERSISTENT_DATA_BYTES;
        } catch (IOException exception) {
            return false;
        }
    }

    private static void requireServerThread(
            MinecraftServer server
    ) {
        if (!server.isSameThread()) {
            throw new IllegalStateException(
                    "World event operations must run on the server thread");
        }
    }

    private record ServerLevelAccess(
            ServerLevel level
    ) implements WorldEventTransaction.Access {
        @Override
        public BlockState get(net.minecraft.core.BlockPos position) {
            return level.getBlockState(position);
        }

        @Override
        public boolean canWrite(
                net.minecraft.core.BlockPos position
        ) {
            return level.isInWorldBounds(position)
                    && level.hasChunkAt(position);
        }

        @Override
        public boolean hasBlockEntity(
                net.minecraft.core.BlockPos position
        ) {
            return level.getBlockEntity(position) != null;
        }

        @Override
        public boolean write(
                net.minecraft.core.BlockPos position,
                BlockState state
        ) {
            return level.setBlock(
                    position, state, Block.UPDATE_ALL);
        }
    }
}

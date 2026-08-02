package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.weapon.WickedKrisPoisonClientState;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/** Target-scoped poison status update for one Wicked Kris owner. */
public record WickedKrisPoisonStatusPayload(
        UUID targetId,
        Operation operation,
        int stacks,
        int poisonRemainingTicks,
        int poisonTotalTicks,
        int detonateRemainingTicks,
        int detonateTotalTicks
) implements CustomPacketPayload {
    private static final UUID CLEAR_ALL_TARGET = new UUID(0L, 0L);

    @SuppressWarnings("null")
    public static final Type<WickedKrisPoisonStatusPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    StardewCraft.MODID,
                    "wicked_kris_poison_status"
            )
    );

    public static final StreamCodec<ByteBuf, WickedKrisPoisonStatusPayload>
            STREAM_CODEC = new StreamCodec<>() {
        @Override
        public WickedKrisPoisonStatusPayload decode(ByteBuf buffer) {
            UUID targetId = new UUID(buffer.readLong(), buffer.readLong());
            int operationId = ByteBufCodecs.VAR_INT.decode(buffer);
            Operation operation = Operation.fromId(operationId);
            int stacks = ByteBufCodecs.VAR_INT.decode(buffer);
            int poisonRemaining = ByteBufCodecs.VAR_INT.decode(buffer);
            int poisonTotal = ByteBufCodecs.VAR_INT.decode(buffer);
            int detonateRemaining = ByteBufCodecs.INT.decode(buffer);
            int detonateTotal = ByteBufCodecs.VAR_INT.decode(buffer);
            return new WickedKrisPoisonStatusPayload(
                    targetId,
                    operation,
                    stacks,
                    poisonRemaining,
                    poisonTotal,
                    detonateRemaining,
                    detonateTotal
            );
        }

        @Override
        public void encode(
                ByteBuf buffer,
                WickedKrisPoisonStatusPayload value
        ) {
            buffer.writeLong(value.targetId().getMostSignificantBits());
            buffer.writeLong(value.targetId().getLeastSignificantBits());
            ByteBufCodecs.VAR_INT.encode(buffer, value.operation().id());
            ByteBufCodecs.VAR_INT.encode(buffer, value.stacks());
            ByteBufCodecs.VAR_INT.encode(buffer, value.poisonRemainingTicks());
            ByteBufCodecs.VAR_INT.encode(buffer, value.poisonTotalTicks());
            ByteBufCodecs.INT.encode(buffer, value.detonateRemainingTicks());
            ByteBufCodecs.VAR_INT.encode(buffer, value.detonateTotalTicks());
        }
    };

    public WickedKrisPoisonStatusPayload {
        java.util.Objects.requireNonNull(targetId, "targetId");
        java.util.Objects.requireNonNull(operation, "operation");
    }

    public static WickedKrisPoisonStatusPayload upsert(
            UUID targetId,
            int stacks,
            int poisonRemainingTicks,
            int poisonTotalTicks,
            int detonateRemainingTicks,
            int detonateTotalTicks
    ) {
        return new WickedKrisPoisonStatusPayload(
                targetId,
                Operation.UPSERT,
                stacks,
                poisonRemainingTicks,
                poisonTotalTicks,
                detonateRemainingTicks,
                detonateTotalTicks
        );
    }

    public static WickedKrisPoisonStatusPayload remove(UUID targetId) {
        return new WickedKrisPoisonStatusPayload(
                targetId,
                Operation.REMOVE,
                0,
                0,
                0,
                0,
                0
        );
    }

    public static WickedKrisPoisonStatusPayload clearAll() {
        return new WickedKrisPoisonStatusPayload(
                CLEAR_ALL_TARGET,
                Operation.CLEAR_ALL,
                0,
                0,
                0,
                0,
                0
        );
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(
            WickedKrisPoisonStatusPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.neoforged.api.distmarker.OnlyIn(
            net.neoforged.api.distmarker.Dist.CLIENT
    )
    private static void handleClient(WickedKrisPoisonStatusPayload payload) {
        net.minecraft.client.Minecraft minecraft =
                net.minecraft.client.Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        long nowTick = minecraft.level.getGameTime();
        switch (payload.operation()) {
            case UPSERT -> WickedKrisPoisonClientState.upsert(
                    payload.targetId(),
                    nowTick,
                    payload.stacks(),
                    payload.poisonRemainingTicks(),
                    payload.poisonTotalTicks(),
                    payload.detonateRemainingTicks(),
                    payload.detonateTotalTicks()
            );
            case REMOVE -> WickedKrisPoisonClientState.remove(
                    payload.targetId()
            );
            case CLEAR_ALL -> WickedKrisPoisonClientState.clearAll();
        }
    }

    public enum Operation {
        UPSERT(0),
        REMOVE(1),
        CLEAR_ALL(2);

        private final int id;

        Operation(int id) {
            this.id = id;
        }

        int id() {
            return id;
        }

        static Operation fromId(int id) {
            return switch (id) {
                case 0 -> UPSERT;
                case 1 -> REMOVE;
                case 2 -> CLEAR_ALL;
                default -> throw new IllegalArgumentException(
                        "Unknown Wicked Kris poison operation: " + id
                );
            };
        }
    }
}

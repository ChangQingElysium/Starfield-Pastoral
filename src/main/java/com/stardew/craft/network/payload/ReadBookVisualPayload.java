package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record ReadBookVisualPayload(
        int entityId,
        boolean complete,
        int durationTicks,
        double x,
        double y,
        double z,
        float yRot
) implements CustomPacketPayload {
    @SuppressWarnings("null")
    public static final Type<ReadBookVisualPayload> TYPE = new Type<>(
            new ResourceLocation(StardewCraft.MODID, "read_book_visual")
    );

    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, ReadBookVisualPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ReadBookVisualPayload decode(ByteBuf buffer) {
            return new ReadBookVisualPayload(
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer),
                    ByteBufCodecs.FLOAT.decode(buffer)
            );
        }

        @Override
        public void encode(ByteBuf buffer, ReadBookVisualPayload payload) {
            ByteBufCodecs.VAR_INT.encode(buffer, payload.entityId());
            ByteBufCodecs.BOOL.encode(buffer, payload.complete());
            ByteBufCodecs.VAR_INT.encode(buffer, payload.durationTicks());
            ByteBufCodecs.DOUBLE.encode(buffer, payload.x());
            ByteBufCodecs.DOUBLE.encode(buffer, payload.y());
            ByteBufCodecs.DOUBLE.encode(buffer, payload.z());
            ByteBufCodecs.FLOAT.encode(buffer, payload.yRot());
        }
    };

    public static ReadBookVisualPayload fromPlayer(ServerPlayer player, boolean complete, int durationTicks) {
        return new ReadBookVisualPayload(
                player.getId(),
                complete,
                durationTicks,
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getYRot()
        );
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ReadBookVisualPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.stardew.craft.client.book.ReadingBookClientEffect.apply(payload));
    }
}
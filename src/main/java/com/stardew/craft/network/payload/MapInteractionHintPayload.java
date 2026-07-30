package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.interaction.StardewInteractionHint;
import com.stardew.craft.api.v1.interaction.StardewInteractionHintType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server response describing the target's resolved interaction semantics. */
public record MapInteractionHintPayload(
        BlockPos pos,
        int entityId,
        ResourceLocation identity,
        StardewInteractionHintType hintType,
        boolean visible,
        boolean done
) implements CustomPacketPayload {
    private static final ResourceLocation EMPTY_ID =
            ResourceLocation.fromNamespaceAndPath(
                    StardewCraft.MODID, "none");

    public static final Type<MapInteractionHintPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    StardewCraft.MODID, "map_interaction_hint"));

    public static final StreamCodec<FriendlyByteBuf,
            MapInteractionHintPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.pos());
                buf.writeVarInt(payload.entityId());
                buf.writeResourceLocation(payload.identity());
                buf.writeVarInt(payload.hintType().networkId());
                buf.writeBoolean(payload.visible());
                buf.writeBoolean(payload.done());
            },
            buf -> new MapInteractionHintPayload(
                    buf.readBlockPos(),
                    buf.readVarInt(),
                    buf.readResourceLocation(),
                    StardewInteractionHintType.byNetworkId(
                            buf.readVarInt()),
                    buf.readBoolean(),
                    buf.readBoolean()));

    public MapInteractionHintPayload {
        pos = pos == null ? BlockPos.ZERO : pos.immutable();
        identity = identity == null ? EMPTY_ID : identity;
        hintType = hintType == null
                ? StardewInteractionHintType.GRAB
                : hintType;
    }

    public static MapInteractionHintPayload visibleBlock(
            BlockPos pos,
            StardewInteractionHint hint
    ) {
        return new MapInteractionHintPayload(
                pos, -1, hint.identity(), hint.type(),
                true, hint.done());
    }

    public static MapInteractionHintPayload visibleEntity(
            int entityId,
            StardewInteractionHint hint
    ) {
        return new MapInteractionHintPayload(
                BlockPos.ZERO, entityId,
                hint.identity(), hint.type(),
                true, hint.done());
    }

    public static MapInteractionHintPayload hiddenBlock(BlockPos pos) {
        return new MapInteractionHintPayload(
                pos, -1, EMPTY_ID,
                StardewInteractionHintType.GRAB,
                false, false);
    }

    public static MapInteractionHintPayload hiddenEntity(int entityId) {
        return new MapInteractionHintPayload(
                BlockPos.ZERO, entityId, EMPTY_ID,
                StardewInteractionHintType.GRAB,
                false, false);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(
            MapInteractionHintPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(MapInteractionHintPayload payload) {
        com.stardew.craft.client.render.MapInteractionHintRenderer
                .accept(payload);
    }
}

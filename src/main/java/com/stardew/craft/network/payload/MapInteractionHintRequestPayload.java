package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.world.interaction.InteractionHintService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client query for the server-authoritative target under the crosshair. */
public record MapInteractionHintRequestPayload(
        BlockPos pos,
        int entityId
) implements CustomPacketPayload {
    private static final double MAX_QUERY_DISTANCE_SQ = 64.0D;

    public static final Type<MapInteractionHintRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    StardewCraft.MODID, "map_interaction_hint_request"));

    public static final StreamCodec<FriendlyByteBuf,
            MapInteractionHintRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.pos());
                buf.writeVarInt(payload.entityId());
            },
            buf -> new MapInteractionHintRequestPayload(
                    buf.readBlockPos(),
                    buf.readVarInt()));

    public MapInteractionHintRequestPayload {
        pos = pos == null ? BlockPos.ZERO : pos.immutable();
    }

    public static MapInteractionHintRequestPayload block(BlockPos pos) {
        return new MapInteractionHintRequestPayload(pos, -1);
    }

    public static MapInteractionHintRequestPayload entity(Entity entity) {
        return new MapInteractionHintRequestPayload(
                entity.blockPosition(), entity.getId());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(
            MapInteractionHintRequestPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (payload.entityId() >= 0) {
                Entity entity = player.serverLevel()
                        .getEntity(payload.entityId());
                if (entity == null
                        || player.distanceToSqr(entity)
                                > MAX_QUERY_DISTANCE_SQ) {
                    PacketDistributor.sendToPlayer(
                            player,
                            MapInteractionHintPayload.hiddenEntity(
                                    payload.entityId()));
                    return;
                }
                MapInteractionHintPayload response =
                        InteractionHintService.resolveEntity(player, entity)
                                .map(hint ->
                                        MapInteractionHintPayload
                                                .visibleEntity(
                                                        entity.getId(),
                                                        hint))
                                .orElseGet(() ->
                                        MapInteractionHintPayload
                                                .hiddenEntity(
                                                        entity.getId()));
                PacketDistributor.sendToPlayer(player, response);
                return;
            }
            if (player.distanceToSqr(Vec3.atCenterOf(payload.pos()))
                    > MAX_QUERY_DISTANCE_SQ) {
                PacketDistributor.sendToPlayer(
                        player,
                        MapInteractionHintPayload.hiddenBlock(payload.pos()));
                return;
            }
            MapInteractionHintPayload response =
                    InteractionHintService.resolveBlock(player, payload.pos())
                            .map(hint ->
                                    MapInteractionHintPayload.visibleBlock(
                                            payload.pos(), hint))
                            .orElseGet(() ->
                                    MapInteractionHintPayload.hiddenBlock(
                                            payload.pos()));
            PacketDistributor.sendToPlayer(player, response);
        });
    }
}

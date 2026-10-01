package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.farm.FarmJoinManager;
import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * C→S: 玩家请求加入某个农场。
 */
@SuppressWarnings("null")
public record FarmJoinRequestPayload(UUID targetOwner) implements CustomPacketPayload {

    public static final Type<FarmJoinRequestPayload> TYPE =
            new Type<>(new ResourceLocation(StardewCraft.MODID, "farm_join_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FarmJoinRequestPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public FarmJoinRequestPayload decode(RegistryFriendlyByteBuf buf) {
                    return new FarmJoinRequestPayload(buf.readUUID());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, FarmJoinRequestPayload payload) {
                    buf.writeUUID(payload.targetOwner);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FarmJoinRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            boolean created = FarmJoinManager.createRequest(player, payload.targetOwner, player.server);
            if (!created) {
                FarmJoinManager.syncPendingState(player, FarmJoinManager.hasPending(player.getUUID()));
            }
        });
    }
}

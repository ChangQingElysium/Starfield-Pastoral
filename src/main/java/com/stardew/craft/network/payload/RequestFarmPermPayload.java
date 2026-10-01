package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C→S: 玩家打开农场管理 Tab，请求在线玩家列表和权限数据。
 * 服务端收到后用 FarmPermSyncPayload 回复。
 */
@SuppressWarnings("null")
public record RequestFarmPermPayload() implements CustomPacketPayload {

    public static final Type<RequestFarmPermPayload> TYPE =
            new Type<>(new ResourceLocation(StardewCraft.MODID, "request_farm_perm"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestFarmPermPayload> STREAM_CODEC =
            StreamCodec.unit(new RequestFarmPermPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestFarmPermPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            FarmPermSyncPayload.sendToPlayer(player);
        });
    }
}

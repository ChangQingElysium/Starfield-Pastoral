package com.stardew.craft.network;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.player.PlayerStardewData;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 玩家数据同步包（服务端 -> 客户端）
 * 使用NBT传输完整数据
 */
public record PlayerDataSyncPacket(CompoundTag data) implements CustomPacketPayload {
    
    @SuppressWarnings("null")
    public static final Type<PlayerDataSyncPacket> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "player_data_sync")
    );
    
    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, PlayerDataSyncPacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.COMPOUND_TAG,
        PlayerDataSyncPacket::data,
        PlayerDataSyncPacket::new
    );
    
    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    
    /**
     * 从玩家数据创建数据包
     */
    public static PlayerDataSyncPacket fromPlayerData(PlayerStardewData playerData) {
        CompoundTag nbt = playerData.toNBT();
        return new PlayerDataSyncPacket(nbt);
    }
    
    /**
     * 客户端接收处理
     */
    public static void handle(PlayerDataSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            // 更新客户端缓存
            com.stardew.craft.client.ClientPlayerDataCache.updateFromNBT(packet.data());
        });
    }
}

package com.stardew.craft.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.*;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/** Only the receiving player's persistent lid state; never sends other players' inventories. */
public record MineRewardStatePacket(String floors) implements CustomPacketPayload {
    public static final Type<MineRewardStatePacket> TYPE=new Type<>(new ResourceLocation("stardewcraft","mine_reward_state"));
    public static final StreamCodec<ByteBuf,MineRewardStatePacket> STREAM_CODEC=StreamCodec.composite(ByteBufCodecs.STRING_UTF8,MineRewardStatePacket::floors,MineRewardStatePacket::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(MineRewardStatePacket packet,IPayloadContext context) {
        context.enqueueWork(()->com.stardew.craft.client.mining.ClientMineRewardState.receive(packet.floors()));
    }
}

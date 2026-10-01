package com.stardew.craft.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.*;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

public record MineFogPacket(int floor,int remainingTicks) implements CustomPacketPayload {
    public static final Type<MineFogPacket> TYPE=new Type<>(new ResourceLocation("stardewcraft","mine_swarm_fog"));
    public static final StreamCodec<ByteBuf,MineFogPacket> STREAM_CODEC=StreamCodec.composite(
            ByteBufCodecs.VAR_INT,MineFogPacket::floor,ByteBufCodecs.VAR_INT,MineFogPacket::remainingTicks,MineFogPacket::new);
    @Override public Type<? extends CustomPacketPayload> type() {return TYPE;}
    public static void handle(MineFogPacket packet,IPayloadContext context) {
        context.enqueueWork(()->com.stardew.craft.client.mining.ClientMineFog.receive(packet.floor(),packet.remainingTicks()));
    }
}

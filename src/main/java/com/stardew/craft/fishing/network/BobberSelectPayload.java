package com.stardew.craft.fishing.network;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.fishing.server.BobberStyleService;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.UUID;

public record BobberSelectPayload(UUID token,int selected) implements CustomPacketPayload {
    public static final Type<BobberSelectPayload> TYPE=new Type<>(new ResourceLocation(StardewCraft.MODID,"bobber_select"));
    public static final StreamCodec<FriendlyByteBuf,BobberSelectPayload> CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.token);b.writeVarInt(p.selected);},b->new BobberSelectPayload(b.readUUID(),b.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void handle(BobberSelectPayload p,IPayloadContext c){c.enqueueWork(()->{if(c.player() instanceof ServerPlayer player)BobberStyleService.select(player,p);});}
}

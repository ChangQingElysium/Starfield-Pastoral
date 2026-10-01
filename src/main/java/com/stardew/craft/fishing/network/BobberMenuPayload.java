package com.stardew.craft.fishing.network;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.UUID;

public record BobberMenuPayload(UUID token,int selected,int fishSpecies,boolean open) implements CustomPacketPayload {
    public static final Type<BobberMenuPayload> TYPE=new Type<>(new ResourceLocation(StardewCraft.MODID,"bobber_menu"));
    public static final StreamCodec<FriendlyByteBuf,BobberMenuPayload> CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.token);b.writeVarInt(p.selected);b.writeVarInt(p.fishSpecies);b.writeBoolean(p.open);},b->new BobberMenuPayload(b.readUUID(),b.readVarInt(),b.readVarInt(),b.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void handle(BobberMenuPayload p,IPayloadContext c){c.enqueueWork(()->client(p));}
    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void client(BobberMenuPayload p){
        var mc=net.minecraft.client.Minecraft.getInstance();if(mc.player==null)return;
        if(p.open)mc.setScreen(new com.stardew.craft.client.gui.BobberStyleScreen(p));
        else if(mc.screen instanceof com.stardew.craft.client.gui.BobberStyleScreen screen)screen.accept(p);
    }
}

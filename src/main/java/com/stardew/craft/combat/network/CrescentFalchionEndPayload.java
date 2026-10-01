package com.stardew.craft.combat.network;
import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.*;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
public record CrescentFalchionEndPayload(int lineId,int actor,String action) implements CustomPacketPayload{
    public static final Type<CrescentFalchionEndPayload> TYPE=new Type<>(new ResourceLocation(StardewCraft.MODID,"crescent_falchion_end"));
    public static final StreamCodec<ByteBuf,CrescentFalchionEndPayload> STREAM_CODEC=StreamCodec.composite(ByteBufCodecs.VAR_INT,CrescentFalchionEndPayload::lineId,ByteBufCodecs.VAR_INT,CrescentFalchionEndPayload::actor,ByteBufCodecs.STRING_UTF8,CrescentFalchionEndPayload::action,CrescentFalchionEndPayload::new);
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void handle(CrescentFalchionEndPayload p,IPayloadContext c){c.enqueueWork(()->{
        com.stardew.craft.client.weapon.SteelFalchionLineEffectClient.remove(p.lineId());
        com.stardew.craft.client.weapon.WeaponSkillAnimationClient.stopMatching(p.actor(),p.action());
    });}
}

package com.stardew.craft.combat.network;

import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

public record WoodWeaponFxPayload(int caster,long tick,String skill,int phase,Vec3 center,int target,float yaw) implements CustomPacketPayload {
    public static final Type<WoodWeaponFxPayload> TYPE=new Type<>(new ResourceLocation("stardewcraft","wood_weapon_fx"));
    public static final StreamCodec<ByteBuf,WoodWeaponFxPayload> STREAM_CODEC=new StreamCodec<>() {
        public WoodWeaponFxPayload decode(ByteBuf b){return new WoodWeaponFxPayload(ByteBufCodecs.VAR_INT.decode(b),b.readLong(),ByteBufCodecs.STRING_UTF8.decode(b),b.readUnsignedByte(),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),ByteBufCodecs.VAR_INT.decode(b),b.readFloat());}
        public void encode(ByteBuf b,WoodWeaponFxPayload p){ByteBufCodecs.VAR_INT.encode(b,p.caster);b.writeLong(p.tick);ByteBufCodecs.STRING_UTF8.encode(b,p.skill);b.writeByte(p.phase);b.writeDouble(p.center.x).writeDouble(p.center.y).writeDouble(p.center.z);ByteBufCodecs.VAR_INT.encode(b,p.target);b.writeFloat(p.yaw);}
    };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void handle(WoodWeaponFxPayload p,IPayloadContext c){c.enqueueWork(()->com.stardew.craft.client.weapon.WoodWeaponVisuals.receive(p));}
    public static void send(ServerPlayer p,String skill,Vec3 center,int phase,int target){PacketDistributor.sendToPlayersTrackingEntityAndSelf(p,new WoodWeaponFxPayload(p.getId(),p.level().getGameTime(),skill,phase,center,target,p.getYRot()));}
}

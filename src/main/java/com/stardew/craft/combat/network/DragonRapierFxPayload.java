package com.stardew.craft.combat.network;

import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

public record DragonRapierFxPayload(int caster,long tick,String skill,int phase,Vec3 center,int target,float yaw) implements CustomPacketPayload {
    public static final Type<DragonRapierFxPayload> TYPE=new Type<>(new ResourceLocation("stardewcraft","dragon_rapier_fx"));
    public static final StreamCodec<ByteBuf,DragonRapierFxPayload> STREAM_CODEC=new StreamCodec<>() {
        public DragonRapierFxPayload decode(ByteBuf b){return new DragonRapierFxPayload(ByteBufCodecs.VAR_INT.decode(b),b.readLong(),ByteBufCodecs.STRING_UTF8.decode(b),b.readUnsignedByte(),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),ByteBufCodecs.VAR_INT.decode(b),b.readFloat());}
        public void encode(ByteBuf b,DragonRapierFxPayload p){ByteBufCodecs.VAR_INT.encode(b,p.caster);b.writeLong(p.tick);ByteBufCodecs.STRING_UTF8.encode(b,p.skill);b.writeByte(p.phase);b.writeDouble(p.center.x).writeDouble(p.center.y).writeDouble(p.center.z);ByteBufCodecs.VAR_INT.encode(b,p.target);b.writeFloat(p.yaw);}
    };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void handle(DragonRapierFxPayload p,IPayloadContext c){c.enqueueWork(()->com.stardew.craft.client.weapon.DragonRapierVisuals.receive(p));}
    public static void send(ServerPlayer p,String skill,Vec3 center,int phase,int target,float yaw){PacketDistributor.sendToPlayersTrackingEntityAndSelf(p,new DragonRapierFxPayload(p.getId(),p.level().getGameTime(),skill,phase,center,target,yaw));}
}

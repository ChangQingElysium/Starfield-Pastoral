package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/** Resolved healing, foldback state, or observed movement; never an inferred damage event. */
public record PirateSilverEffectPayload(int caster,long tick,Phase phase,Vec3 from,Vec3 to,int duration) implements CustomPacketPayload{
    public enum Phase{HEAL,ANCHOR,END,BLINK,DASH}
    public static final Type<PirateSilverEffectPayload> TYPE=new Type<>(new ResourceLocation(StardewCraft.MODID,"pirate_silver_effect"));
    public static final StreamCodec<ByteBuf,PirateSilverEffectPayload> STREAM_CODEC=new StreamCodec<>(){
        @Override public PirateSilverEffectPayload decode(ByteBuf b){return new PirateSilverEffectPayload(ByteBufCodecs.VAR_INT.decode(b),ByteBufCodecs.VAR_LONG.decode(b),Phase.values()[b.readUnsignedByte()],new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),ByteBufCodecs.VAR_INT.decode(b));}
        @Override public void encode(ByteBuf b,PirateSilverEffectPayload p){ByteBufCodecs.VAR_INT.encode(b,p.caster);ByteBufCodecs.VAR_LONG.encode(b,p.tick);b.writeByte(p.phase.ordinal());b.writeDouble(p.from.x).writeDouble(p.from.y).writeDouble(p.from.z);b.writeDouble(p.to.x).writeDouble(p.to.y).writeDouble(p.to.z);ByteBufCodecs.VAR_INT.encode(b,p.duration);}
    };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void send(Player player,Phase phase,Vec3 from,Vec3 to,int duration){
        if(player instanceof ServerPlayer server)PacketDistributor.sendToPlayersTrackingEntityAndSelf(server,new PirateSilverEffectPayload(player.getId(),player.level().getGameTime(),phase,from,to,duration));
    }
    public static void handle(PirateSilverEffectPayload p,IPayloadContext context){context.enqueueWork(()->com.stardew.craft.client.weapon.PirateSilverVisuals.effect(p));}
}

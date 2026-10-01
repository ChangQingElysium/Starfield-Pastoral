package com.stardew.craft.combat.network;
import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.*;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
public record GuardSpineStatePayload(int actor,long tick,int phase,int duration) implements CustomPacketPayload {
    public static final int WAIT=0,CHARGED=1,WEAK=2,CLEAR=3,GUARD_END=4;
    public static final Type<GuardSpineStatePayload> TYPE=new Type<>(new ResourceLocation(StardewCraft.MODID,"guard_spine_state"));
    public static final StreamCodec<ByteBuf,GuardSpineStatePayload> STREAM_CODEC=StreamCodec.composite(ByteBufCodecs.VAR_INT,GuardSpineStatePayload::actor,ByteBufCodecs.VAR_LONG,GuardSpineStatePayload::tick,ByteBufCodecs.VAR_INT,GuardSpineStatePayload::phase,ByteBufCodecs.VAR_INT,GuardSpineStatePayload::duration,GuardSpineStatePayload::new);
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void send(ServerPlayer p,int phase,int duration){PacketDistributor.sendToPlayersTrackingEntityAndSelf(p,new GuardSpineStatePayload(p.getId(),p.level().getGameTime(),phase,duration));}
    public static void handle(GuardSpineStatePayload p,IPayloadContext c){c.enqueueWork(()->com.stardew.craft.client.weapon.GuardSpineVisuals.state(p));}
}

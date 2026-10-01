package com.stardew.craft.fishing.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.UUID;

/** Minigame feedback only. The server accepts it solely while that exact session is playing. */
public record FishingMotionInputPayload(UUID session,float position,float progress,float velocity,boolean held,boolean controlled) implements CustomPacketPayload {
    public static final Type<FishingMotionInputPayload> TYPE=new Type<>(new ResourceLocation("stardewcraft","fishing_motion_input"));
    public static final StreamCodec<ByteBuf,FishingMotionInputPayload> STREAM_CODEC=StreamCodec.composite(com.stardew.craft.port.PortCodecs.UUID,FishingMotionInputPayload::session,ByteBufCodecs.FLOAT,FishingMotionInputPayload::position,ByteBufCodecs.FLOAT,FishingMotionInputPayload::progress,ByteBufCodecs.FLOAT,FishingMotionInputPayload::velocity,ByteBufCodecs.BOOL,FishingMotionInputPayload::held,ByteBufCodecs.BOOL,FishingMotionInputPayload::controlled,FishingMotionInputPayload::new);
    public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void handle(FishingMotionInputPayload p,IPayloadContext c){c.enqueueWork(()->{if(c.player() instanceof ServerPlayer player)com.stardew.craft.fishing.server.FishingPresentationEvents.input(player,p);});}
}

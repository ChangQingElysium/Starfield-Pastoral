package com.stardew.craft.fishing.network;

import com.stardew.craft.fishing.FishingPresentationPhase;
import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.UUID;

/** Complete cosmetic snapshot, also sent when an observer starts tracking the player. */
public record FishingPresentationPayload(UUID actor,UUID session,FishingPresentationPhase phase,long started,
        int hook,Vec3 bobber,ItemStack stack,boolean fish,float fishPosition,float progress,float velocity,boolean held,boolean controlled) implements CustomPacketPayload {
    public static final Type<FishingPresentationPayload> TYPE=new Type<>(new ResourceLocation("stardewcraft","fishing_presentation"));
    public static final StreamCodec<RegistryFriendlyByteBuf,FishingPresentationPayload> STREAM_CODEC=new StreamCodec<>() {
        public FishingPresentationPayload decode(RegistryFriendlyByteBuf b){return new FishingPresentationPayload(b.readUUID(),b.readUUID(),b.readEnum(FishingPresentationPhase.class),b.readLong(),b.readVarInt(),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),com.stardew.craft.port.PortCodecs.OPTIONAL_ITEM_STACK.decode(b),b.readBoolean(),b.readFloat(),b.readFloat(),b.readFloat(),b.readBoolean(),b.readBoolean());}
        public void encode(RegistryFriendlyByteBuf b,FishingPresentationPayload p){b.writeUUID(p.actor);b.writeUUID(p.session);b.writeEnum(p.phase);b.writeLong(p.started);b.writeVarInt(p.hook);b.writeDouble(p.bobber.x);b.writeDouble(p.bobber.y);b.writeDouble(p.bobber.z);com.stardew.craft.port.PortCodecs.OPTIONAL_ITEM_STACK.encode(b,p.stack);b.writeBoolean(p.fish);b.writeFloat(p.fishPosition);b.writeFloat(p.progress);b.writeFloat(p.velocity);b.writeBoolean(p.held);b.writeBoolean(p.controlled);}
    };
    public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void handle(FishingPresentationPayload p,IPayloadContext context){context.enqueueWork(()->com.stardew.craft.client.fishing.FishingPresentationClient.receive(p));}
}

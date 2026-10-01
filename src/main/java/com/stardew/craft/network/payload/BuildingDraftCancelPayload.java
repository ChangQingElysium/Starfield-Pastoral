package com.stardew.craft.network.payload;

import com.stardew.craft.building.runtime.BuildingBlueprintItem;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/** No position or building ID: this can only cancel the sender's held document. */
public record BuildingDraftCancelPayload(InteractionHand hand,java.util.UUID document,boolean endMove) implements CustomPacketPayload {
    public static final Type<BuildingDraftCancelPayload> TYPE=new Type<>(new ResourceLocation("stardewcraft:building_draft_cancel"));
    public static final StreamCodec<FriendlyByteBuf,BuildingDraftCancelPayload> CODEC=StreamCodec.of(
            (b,p)->{b.writeEnum(p.hand);b.writeUUID(p.document);b.writeBoolean(p.endMove);},b->new BuildingDraftCancelPayload(b.readEnum(InteractionHand.class),b.readUUID(),b.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void handle(BuildingDraftCancelPayload payload,IPayloadContext context){
        context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player && payload.document.equals(com.stardew.craft.building.runtime.BuildingDrafts.id(player.getItemInHand(payload.hand))))BuildingBlueprintItem.cancel(player,payload.hand,payload.endMove);});
    }
}

package com.stardew.craft.network.payload;

import com.stardew.craft.building.runtime.BuildingBlueprintItem;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

public record BuildingRotatePayload(InteractionHand hand, boolean reverse) implements CustomPacketPayload {
    public static final Type<BuildingRotatePayload> TYPE = new Type<>(new ResourceLocation("stardewcraft:building_rotate"));
    public static final StreamCodec<FriendlyByteBuf, BuildingRotatePayload> CODEC = StreamCodec.of(
            (b, p) -> { b.writeEnum(p.hand); b.writeBoolean(p.reverse); },
            b -> new BuildingRotatePayload(b.readEnum(InteractionHand.class), b.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(BuildingRotatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) BuildingBlueprintItem.rotate(player, payload.hand, payload.reverse);
        });
    }
}

package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.nbt.CompoundTag;
import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

public record SpecialOrderStateSyncPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<SpecialOrderStateSyncPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "special_order_state_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpecialOrderStateSyncPayload> STREAM_CODEC =
        StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, SpecialOrderStateSyncPayload::data, SpecialOrderStateSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SpecialOrderStateSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.stardew.craft.client.gui.specialorder.ClientSpecialOrderBoardData.replace(payload.data()));
    }
}

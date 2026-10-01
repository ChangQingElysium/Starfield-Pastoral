package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> Client: sync equipped items so client can render them in UI.
 */
@SuppressWarnings("null")
public record EquipmentSyncPayload(ItemStack leftRing, ItemStack rightRing, ItemStack boots, ItemStack trinket,
                                   String hat, String shirt, String pants) implements CustomPacketPayload {

    public static final Type<EquipmentSyncPayload> TYPE =
            new Type<>(new ResourceLocation(StardewCraft.MODID, "equipment_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EquipmentSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                com.stardew.craft.port.PortCodecs.OPTIONAL_ITEM_STACK.encode(buf, payload.leftRing);
                com.stardew.craft.port.PortCodecs.OPTIONAL_ITEM_STACK.encode(buf, payload.rightRing);
                com.stardew.craft.port.PortCodecs.OPTIONAL_ITEM_STACK.encode(buf, payload.boots);
                com.stardew.craft.port.PortCodecs.OPTIONAL_ITEM_STACK.encode(buf, payload.trinket);
                buf.writeUtf(payload.hat);
                buf.writeUtf(payload.shirt);
                buf.writeUtf(payload.pants);
            },
            buf -> new EquipmentSyncPayload(com.stardew.craft.port.PortCodecs.OPTIONAL_ITEM_STACK.decode(buf),
                    com.stardew.craft.port.PortCodecs.OPTIONAL_ITEM_STACK.decode(buf), com.stardew.craft.port.PortCodecs.OPTIONAL_ITEM_STACK.decode(buf),
                    com.stardew.craft.port.PortCodecs.OPTIONAL_ITEM_STACK.decode(buf), buf.readUtf(), buf.readUtf(), buf.readUtf())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(EquipmentSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            com.stardew.craft.client.ClientPlayerDataCache.setEquippedLeftRingStack(payload.leftRing);
            com.stardew.craft.client.ClientPlayerDataCache.setEquippedRightRingStack(payload.rightRing);
            com.stardew.craft.client.ClientPlayerDataCache.setEquippedBootsStack(payload.boots);
            com.stardew.craft.client.ClientPlayerDataCache.setEquippedTrinket(payload.trinket);
            com.stardew.craft.client.ClientPlayerDataCache.setEquippedHat(payload.hat);
            com.stardew.craft.client.ClientPlayerDataCache.setEquippedShirt(payload.shirt);
            com.stardew.craft.client.ClientPlayerDataCache.setEquippedPants(payload.pants);
            if (context.player() != null) {
                com.stardew.craft.client.ClientPlayerDataCache.setCosmeticAppearance(
                        context.player().getUUID(), payload.hat, payload.shirt, payload.pants);
            }
        });
    }
}

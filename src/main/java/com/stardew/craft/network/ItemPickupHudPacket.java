package com.stardew.craft.network;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server → Client: tells the client to show an item-pickup HUD message (SDV parity).
 */
public record ItemPickupHudPacket(ItemStack stack, int count, boolean expensive) implements CustomPacketPayload {
    @SuppressWarnings("null")
    public static final Type<ItemPickupHudPacket> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "item_pickup_hud")
    );

    @SuppressWarnings("null")
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemPickupHudPacket> STREAM_CODEC = StreamCodec.composite(
        com.stardew.craft.port.PortCodecs.ITEM_STACK, ItemPickupHudPacket::stack,
        ByteBufCodecs.INT,         ItemPickupHudPacket::count,
        ByteBufCodecs.BOOL,        ItemPickupHudPacket::expensive,
        ItemPickupHudPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @SuppressWarnings("null")
    public static void handle(ItemPickupHudPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ItemStack stack = packet.stack().copy();
            if (!stack.isEmpty()) {
                stack.setCount(Math.max(1, packet.count()));
                com.stardew.craft.client.hud.StardewHudMessageManager.showItemPickup(stack, packet.count(), packet.expensive());
            }
        });
    }

    @SuppressWarnings("null")
    public static void sendTo(ServerPlayer player, ItemStack stack, int count, boolean expensive) {
        if (player == null || stack == null || stack.isEmpty()) {
            return;
        }
        int displayCount = Math.max(1, count);
        PacketDistributor.sendToPlayer(player, new ItemPickupHudPacket(stack.copyWithCount(displayCount), displayCount, expensive));
    }
}

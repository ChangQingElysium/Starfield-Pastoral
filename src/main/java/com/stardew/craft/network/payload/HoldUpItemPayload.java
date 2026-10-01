package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraftforge.network.PacketDistributor;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server → Client: triggers a "hold up item" animation (SDV holdUpItemThenMessage parity).
 * Uses Minecraft's displayItemActivation (totem-like) effect + sound.
 */
public record HoldUpItemPayload(String itemId) implements CustomPacketPayload {
    @SuppressWarnings("null")
    public static final Type<HoldUpItemPayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "hold_up_item")
    );

    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, HoldUpItemPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, HoldUpItemPayload::itemId,
        HoldUpItemPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @SuppressWarnings("null")
    public static void handle(HoldUpItemPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            com.stardew.craft.client.hud.HoldUpItemHandler.play(payload.itemId());
        });
    }

    @SuppressWarnings("null")
    public static void sendTo(ServerPlayer player, ItemStack stack) {
        ResourceLocation rl = BuiltInRegistries.ITEM.getKey(stack.getItem());
        PacketDistributor.sendToPlayer(player, new HoldUpItemPayload(rl.toString()));
    }
}

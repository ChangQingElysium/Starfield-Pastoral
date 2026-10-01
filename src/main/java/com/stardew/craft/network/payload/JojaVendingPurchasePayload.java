package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

public record JojaVendingPurchasePayload(BlockPos pos) implements CustomPacketPayload {
    private static final int PRICE = 75;

    @SuppressWarnings("null")
    public static final Type<JojaVendingPurchasePayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "joja_vending_purchase"));

    @SuppressWarnings("null")
    public static final StreamCodec<FriendlyByteBuf, JojaVendingPurchasePayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeBlockPos(payload.pos()),
        buf -> new JojaVendingPurchasePayload(buf.readBlockPos())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @SuppressWarnings("null")
    public static void handle(JojaVendingPurchasePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            // Validate distance
            BlockPos pos = payload.pos();
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) {
                return;
            }
            if (!PlayerStardewDataAPI.removeMoney(player, PRICE)) {
                return;
            }
            ItemStack cola = new ItemStack(ModItems.JOJA_COLA.get());
            ItemEntity entity = new ItemEntity(player.level(), pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, cola);
            entity.setDeltaMovement(0, 0.2, 0);
            player.level().addFreshEntity(entity);
            player.level().playSound(null, pos, SoundEvents.DISPENSER_DISPENSE, SoundSource.BLOCKS, 1.0f, 1.0f);
        });
    }
}

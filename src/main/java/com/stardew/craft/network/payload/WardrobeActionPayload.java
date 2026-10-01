package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.blockentity.WardrobeBlockEntity;
import com.stardew.craft.wardrobe.WardrobeCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record WardrobeActionPayload(BlockPos pos, int action, int index) implements CustomPacketPayload {
    public static final int ACTION_STORE_INVENTORY_SLOT = 0;
    public static final int ACTION_TAKE_WARDROBE_INDEX = 1;
    public static final int ACTION_STORE_ONE_INVENTORY_SLOT = 2;

    public static final Type<WardrobeActionPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "wardrobe_action"));

    public static final StreamCodec<FriendlyByteBuf, WardrobeActionPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeBlockPos(payload.pos());
            buf.writeVarInt(payload.action());
            buf.writeVarInt(payload.index());
        },
        buf -> new WardrobeActionPayload(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WardrobeActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (player.distanceToSqr(payload.pos().getX() + 0.5D, payload.pos().getY() + 0.5D, payload.pos().getZ() + 0.5D) > 64.0D) {
                return;
            }
            if (!(player.level().getBlockEntity(payload.pos()) instanceof WardrobeBlockEntity wardrobe)) {
                return;
            }

            if (payload.action() == ACTION_STORE_INVENTORY_SLOT) {
                storeInventorySlot(player, wardrobe, payload.index(), -1);
            } else if (payload.action() == ACTION_STORE_ONE_INVENTORY_SLOT) {
                storeInventorySlot(player, wardrobe, payload.index(), 1);
            } else if (payload.action() == ACTION_TAKE_WARDROBE_INDEX) {
                takeWardrobeIndex(player, wardrobe, payload.index());
            }

            PacketDistributor.sendToPlayer(player, OpenWardrobePayload.from(payload.pos(), wardrobe));
        });
    }

    private static void storeInventorySlot(ServerPlayer player, WardrobeBlockEntity wardrobe, int slot, int maxCount) {
        if (slot < 0 || slot >= player.getInventory().getContainerSize()) {
            return;
        }
        ItemStack stack = player.getInventory().getItem(slot);
        if (stack.isEmpty() || !WardrobeCategory.isAccepted(stack)) {
            return;
        }
        int movingCount = maxCount <= 0 ? stack.getCount() : Math.min(maxCount, stack.getCount());
        ItemStack moving = stack.copyWithCount(movingCount);
        if (wardrobe.addFromInventory(moving)) {
            stack.shrink(movingCount);
            if (stack.isEmpty()) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            }
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
        }
    }

    private static void takeWardrobeIndex(ServerPlayer player, WardrobeBlockEntity wardrobe, int index) {
        ItemStack stack = wardrobe.peek(index);
        if (stack.isEmpty()) {
            return;
        }
        ItemStack moving = stack.copy();
        if (player.getInventory().add(moving) && moving.isEmpty()) {
            wardrobe.removeAt(index);
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
        }
    }
}

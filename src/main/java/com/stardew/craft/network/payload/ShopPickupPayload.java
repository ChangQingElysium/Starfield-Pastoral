package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.shop.ShopPendingPickupData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Client → Server: player has closed the shop screen (or placed heldItem into a slot).
 * The server grants only items already recorded in the persistent purchase escrow.
 */
@SuppressWarnings("null")
public record ShopPickupPayload(
    String itemId,
    int    quantity,
    int    targetSlot  // ≥0 = place in this specific inventory slot; -1 = auto (first available)
) implements CustomPacketPayload {

    public static final Type<ShopPickupPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "shop_pickup"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShopPickupPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ShopPickupPayload::itemId,
            ByteBufCodecs.INT,         ShopPickupPayload::quantity,
            ByteBufCodecs.INT,         ShopPickupPayload::targetSlot,
            ShopPickupPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void recordValidatedPurchase(ServerPlayer player, String itemId, int quantity) {
        if (player == null || itemId == null || itemId.isEmpty() || quantity <= 0) return;
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) return;
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == null || item == Items.AIR) return;

        int remaining = quantity;
        int maxStackSize = Math.max(1, item.getDefaultMaxStackSize());
        ShopPendingPickupData data = ShopPendingPickupData.get(player.server);
        while (remaining > 0) {
            int count = Math.min(remaining, maxStackSize);
            data.add(player.getUUID(), new ItemStack(item, count));
            remaining -= count;
        }
    }

    public static void handle(ShopPickupPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (payload.itemId() == null || payload.itemId().isEmpty()) return;
            if (payload.itemId().startsWith("recipe:")) return;
            if (payload.itemId().startsWith("wallpaper:") || payload.itemId().startsWith("flooring:")) return;
            ResourceLocation rl;
            try { rl = ResourceLocation.parse(payload.itemId()); }
            catch (Exception ignored) { return; }

            List<ItemStack> claimed = ShopPendingPickupData.get(player.server)
                .take(player.getUUID(), rl, payload.quantity());
            if (claimed.isEmpty()) return;

            int targetSlot = payload.targetSlot();
            boolean firstStack = true;
            for (ItemStack stack : claimed) {
                if (firstStack && targetSlot >= 0 && targetSlot < player.getInventory().getContainerSize()) {
                    ItemStack existing = player.getInventory().getItem(targetSlot);
                    if (existing.isEmpty()) {
                        player.getInventory().setItem(targetSlot, stack);
                    } else if (ItemStack.isSameItemSameComponents(existing, stack)
                            && existing.getCount() < existing.getMaxStackSize()) {
                        int canAdd = existing.getMaxStackSize() - existing.getCount();
                        int toAdd = Math.min(canAdd, stack.getCount());
                        existing.grow(toAdd);
                        if (toAdd < stack.getCount()) {
                            ItemStack leftover = stack.copyWithCount(stack.getCount() - toAdd);
                            if (!player.getInventory().add(leftover)) player.drop(leftover, false);
                        }
                    } else if (!player.getInventory().add(stack)) {
                        player.drop(stack, false);
                    }
                } else if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }

                firstStack = false;
            }
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
        });
    }

    public static void deliverAllPending(ServerPlayer player) {
        if (player == null) return;
        List<ItemStack> pending = ShopPendingPickupData.get(player.server).takeAll(player.getUUID());
        if (pending.isEmpty()) return;
        for (ItemStack stack : pending) {
            if (!player.getInventory().add(stack)) player.drop(stack, false);
        }
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
    }
}

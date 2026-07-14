package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.item.cosmetic.StardewCosmeticItem;
import com.stardew.craft.item.cosmetic.StardewCosmeticSlot;
import com.stardew.craft.item.equipment.CombinedRingData;
import com.stardew.craft.item.trinket.StardewTrinketItem;
import com.stardew.craft.player.CosmeticAppearanceSync;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client -> Server: player clicks an equipment slot in the inventory page.
 * slotType: 0=left ring, 1=right ring, 2=boots, 3=trinket, 4=hat, 5=shirt, 6=pants
 */
@SuppressWarnings("null")
public record EquipmentActionPayload(int slotType) implements CustomPacketPayload {

    public static final int SLOT_LEFT_RING = 0;
    public static final int SLOT_RIGHT_RING = 1;
    public static final int SLOT_BOOTS = 2;
    public static final int SLOT_TRINKET = 3;
    public static final int SLOT_HAT = 4;
    public static final int SLOT_SHIRT = 5;
    public static final int SLOT_PANTS = 6;

    public static final Type<EquipmentActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "equipment_action"));

    public static final StreamCodec<FriendlyByteBuf, EquipmentActionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeVarInt(payload.slotType),
            buf -> new EquipmentActionPayload(buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(EquipmentActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            PlayerStardewData data = PlayerDataManager.getPlayerData(player);
            ItemStack carried = player.containerMenu.getCarried();

            switch (payload.slotType) {
                case SLOT_LEFT_RING -> handleRingSlot(player, data, carried, true);
                case SLOT_RIGHT_RING -> handleRingSlot(player, data, carried, false);
                case SLOT_BOOTS -> handleBootsSlot(player, data, carried);
                case SLOT_TRINKET -> handleTrinketSlot(player, data, carried);
                case SLOT_HAT -> handleCosmeticSlot(player, data, carried, StardewCosmeticSlot.HAT);
                case SLOT_SHIRT -> handleCosmeticSlot(player, data, carried, StardewCosmeticSlot.SHIRT);
                case SLOT_PANTS -> handleCosmeticSlot(player, data, carried, StardewCosmeticSlot.PANTS);
            }

            // sync back to client
            PacketDistributor.sendToPlayer(player, new EquipmentSyncPayload(
                    data.getEquippedLeftRingStack(),
                    data.getEquippedRightRingStack(),
                    data.getEquippedBootsStack(),
                    data.getEquippedTrinket(),
                    data.getEquippedHat(),
                    data.getEquippedShirt(),
                    data.getEquippedPants()
            ));
            CosmeticAppearanceSync.broadcast(player, data);
        });
    }

    private static void handleRingSlot(ServerPlayer player, PlayerStardewData data, ItemStack carried, boolean left) {
        ItemStack current = left ? data.getEquippedLeftRingStack() : data.getEquippedRightRingStack();

        if (carried.isEmpty()) {
            // unequip
            if (!current.isEmpty()) {
                player.containerMenu.setCarried(current);
                if (left) data.setEquippedLeftRingStack(ItemStack.EMPTY);
                else data.setEquippedRightRingStack(ItemStack.EMPTY);
            }
        } else if (com.stardew.craft.combat.equipment.EquipmentSlotResolver.isRing(carried)) {
            ItemStack equipped = carried.copyWithCount(1);
            if (!current.isEmpty()) {
                // swap
                player.containerMenu.setCarried(current);
            } else {
                carried.shrink(1);
                player.containerMenu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
            }
            if (left) data.setEquippedLeftRingStack(equipped);
            else data.setEquippedRightRingStack(equipped);
        }
    }

    private static void handleBootsSlot(ServerPlayer player, PlayerStardewData data, ItemStack carried) {
        ItemStack current = data.getEquippedBootsStack();

        if (carried.isEmpty()) {
            if (!current.isEmpty()) {
                player.containerMenu.setCarried(current);
                data.setEquippedBootsStack(ItemStack.EMPTY);
            }
        } else if (com.stardew.craft.combat.equipment.EquipmentSlotResolver.isBoots(carried)) {
            ItemStack equipped = carried.copyWithCount(1);
            if (!current.isEmpty()) {
                player.containerMenu.setCarried(current);
            } else {
                carried.shrink(1);
                player.containerMenu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
            }
            data.setEquippedBootsStack(equipped);
        }
    }

    private static void handleTrinketSlot(ServerPlayer player, PlayerStardewData data, ItemStack carried) {
        if (data.getUnlockedTrinketSlots() <= 0) {
            return;
        }

        ItemStack current = data.getEquippedTrinket();
        if (carried.isEmpty()) {
            if (!current.isEmpty()) {
                player.containerMenu.setCarried(current);
                data.setEquippedTrinket(ItemStack.EMPTY);
            }
        } else if (carried.getItem() instanceof StardewTrinketItem) {
            ItemStack newTrinket = carried.copyWithCount(1);
            StardewTrinketItem.ensureGenerated(newTrinket, data.getTotalMoneyEarned(), player.getRandom());
            if (!current.isEmpty()) {
                player.containerMenu.setCarried(current);
            } else {
                carried.shrink(1);
                player.containerMenu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
            }
            data.setEquippedTrinket(newTrinket);
        }
    }

    private static void handleCosmeticSlot(ServerPlayer player, PlayerStardewData data, ItemStack carried,
                                           StardewCosmeticSlot slot) {
        String currentId = getEquippedCosmetic(data, slot);

        if (carried.isEmpty()) {
            if (!currentId.isEmpty()) {
                player.containerMenu.setCarried(idToStack(currentId));
                setEquippedCosmetic(data, slot, "");
            }
            return;
        }

        if (!(carried.getItem() instanceof StardewCosmeticItem cosmetic) || cosmetic.getCosmeticSlot() != slot) {
            return;
        }

        String newId = BuiltInRegistries.ITEM.getKey(carried.getItem()).toString();
        if (!currentId.isEmpty()) {
            player.containerMenu.setCarried(idToStack(currentId));
        } else {
            carried.shrink(1);
            player.containerMenu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
        }
        setEquippedCosmetic(data, slot, newId);
    }

    private static String getEquippedCosmetic(PlayerStardewData data, StardewCosmeticSlot slot) {
        return switch (slot) {
            case HAT -> data.getEquippedHat();
            case SHIRT -> data.getEquippedShirt();
            case PANTS -> data.getEquippedPants();
        };
    }

    private static void setEquippedCosmetic(PlayerStardewData data, StardewCosmeticSlot slot, String itemId) {
        switch (slot) {
            case HAT -> data.setEquippedHat(itemId);
            case SHIRT -> data.setEquippedShirt(itemId);
            case PANTS -> data.setEquippedPants(itemId);
        }
    }

    private static ItemStack idToStack(String id) {
        if (CombinedRingData.isEncodedEquipmentSlot(id)) {
            return CombinedRingData.stackFromEquipmentSlot(id);
        }
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.get(rl);
        return new ItemStack(item);
    }
}

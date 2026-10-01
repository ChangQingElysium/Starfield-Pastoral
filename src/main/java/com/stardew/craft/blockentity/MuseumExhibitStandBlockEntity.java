package com.stardew.craft.blockentity;

import com.stardew.craft.museum.MuseumDonationData;
import com.stardew.craft.museum.MuseumExhibitStandManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.UUID;

/**
 * Museum exhibit stand block entity.
 * Server-side: display items are stored in MuseumDonationData per-player.
 * Client-side: displayItem is set via custom sync packet for the local player's view.
 */
public class MuseumExhibitStandBlockEntity extends BlockEntity {

    /** Client-side only: the item to render for the local player. */
    private ItemStack displayItem = ItemStack.EMPTY;

    public MuseumExhibitStandBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MUSEUM_EXHIBIT_STAND.get(), pos, state);
    }

    public ItemStack getDisplayItem() {
        return displayItem;
    }

    public boolean hasDisplayItem() {
        return !displayItem.isEmpty();
    }

    /**
     * Client-side: set the display item for rendering (called from sync packet).
     */
    public void setClientDisplayItem(ItemStack stack) {
        displayItem = (stack == null || stack.isEmpty()) ? ItemStack.EMPTY : stack.copy();
    }

    /**
     * Server-side: place an item on the stand for a specific player.
     */
    public void setDisplayItemForPlayer(UUID playerId, ItemStack stack) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (!MuseumExhibitStandManager.isManagedMuseumStand(serverLevel, worldPosition)) return;
        String standKey = MuseumDonationData.standKey(serverLevel, worldPosition);
        MuseumDonationData data = MuseumDonationData.get(serverLevel);
        if (stack == null || stack.isEmpty()) {
            data.setStandDisplayedItem(playerId, standKey, null);
        } else {
            net.minecraft.resources.ResourceLocation itemKey = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(stack.getItem()));
            if (itemKey != null) {
                data.setStandDisplayedItem(playerId, standKey, itemKey.toString());
            }
        }
    }

    /**
     * Server-side: remove the item from the stand for a specific player.
     * Returns the item that was on the stand (from MuseumDonationData).
     */
    public ItemStack removeDisplayItemForPlayer(UUID playerId) {
        if (!(level instanceof ServerLevel serverLevel)) return ItemStack.EMPTY;
        if (!MuseumExhibitStandManager.isManagedMuseumStand(serverLevel, worldPosition)) return ItemStack.EMPTY;
        String standKey = MuseumDonationData.standKey(serverLevel, worldPosition);
        MuseumDonationData data = MuseumDonationData.get(serverLevel);
        java.util.Map<String, String> stands = data.getStandDisplayItems(playerId);
        String itemId = stands.get(standKey);
        if (itemId == null || itemId.isBlank()) return ItemStack.EMPTY;

        data.setStandDisplayedItem(playerId, standKey, null);

        net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(itemId);
        if (rl != null) {
            net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                return new ItemStack(item);
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Server-side: check if a specific player has an item on this stand.
     */
    public boolean hasDisplayItemForPlayer(UUID playerId) {
        if (!(level instanceof ServerLevel serverLevel)) return false;
        if (!MuseumExhibitStandManager.isManagedMuseumStand(serverLevel, worldPosition)) return false;
        String standKey = MuseumDonationData.standKey(serverLevel, worldPosition);
        MuseumDonationData data = MuseumDonationData.get(serverLevel);
        java.util.Map<String, String> stands = data.getStandDisplayItems(playerId);
        String itemId = stands.get(standKey);
        return itemId != null && !itemId.isBlank();
    }

    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag) { net.minecraft.core.HolderLookup.Provider provider = com.stardew.craft.port.PortRegistries.lookup();
        super.saveAdditional(tag);
        // No longer save displayItem to NBT — data lives in MuseumDonationData per player
    }

    @Override
    public void load(@Nonnull CompoundTag tag) { net.minecraft.core.HolderLookup.Provider provider = com.stardew.craft.port.PortRegistries.lookup();
        super.load(tag);
        // Display item is authoritative server-side in MuseumDonationData and synced to the
        // client via MuseumStandSyncPacket → ClientMuseumStandCache. Do not touch displayItem
        // here; vanilla block update packets would otherwise clobber the cache-hydrated state.
    }

    @Override
    public CompoundTag getUpdateTag() { net.minecraft.core.HolderLookup.Provider provider = com.stardew.craft.port.PortRegistries.lookup();
        // Return empty tag — stand content is synced per-player via custom packet
        return new CompoundTag();
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @SuppressWarnings("null")
    @Override
    public void onLoad() {
        super.onLoad();
        net.minecraft.world.level.Level lvl = level;
        if (lvl != null && lvl.isClientSide) {
            com.stardew.craft.client.ClientMuseumStandCache.register(this);
        }
    }

    @SuppressWarnings("null")
    @Override
    public void setRemoved() {
        net.minecraft.world.level.Level lvl = level;
        if (lvl != null && lvl.isClientSide) {
            com.stardew.craft.client.ClientMuseumStandCache.unregister(this);
        }
        super.setRemoved();
    }
}

package com.stardew.craft.blockentity;

import com.stardew.craft.block.utility.ChestVariant;
import com.stardew.craft.inventory.JunimoChestData;
import com.stardew.craft.farm.FarmInstanceRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import java.util.UUID;

public final class StorageChestBlockEntity extends WoodenChestBlockEntity {
    private UUID owner;
    public StorageChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STORAGE_CHEST.get(), pos, state, ChestVariant.of(state.getBlock()).capacity);
    }
    public UUID sharedOwner() { return isSharedStorage() ? owner : null; }
    @Override public boolean isSharedStorage() { return variant() == ChestVariant.JUNIMO; }
    public void bindOwner(Player player) {
        if (!isSharedStorage() || owner != null || !(level instanceof ServerLevel server)) return;
        owner = server.dimension() == com.stardew.craft.core.ModDimensions.STARDEW_VALLEY
                ? com.stardew.craft.core.FarmAreaResolver.getOwnerAt(worldPosition) : null;
        if (owner == null) owner = FarmInstanceRegistry.get(server.getServer()).getOwnerForPlayer(player.getUUID());
        if (owner == null) owner = player.getUUID();
        setChanged();
        com.stardew.craft.port.PortCapabilities.invalidateCapabilities(level, worldPosition);
    }
    public boolean mayAccess(Player player) {
        if (!isSharedStorage()) return true;
        bindOwner(player);
        return owner != null && (owner.equals(player.getUUID()) || player.isCreative()
                || com.stardew.craft.farm.FarmPermissionManager.get().canModify(owner, player.getUUID()));
    }
    @Override protected NonNullList<ItemStack> inventoryItems() {
        if (isSharedStorage() && owner != null && level instanceof ServerLevel server) {
            return JunimoChestData.get(server.getServer()).items(owner);
        }
        return super.inventoryItems();
    }
    @Override public void setChanged() {
        super.setChanged();
        if (isSharedStorage() && owner != null && level instanceof ServerLevel server) JunimoChestData.get(server.getServer()).setDirty();
    }
    @Override public boolean isInUse() {
        if (isSharedStorage() && owner != null && level instanceof ServerLevel server) {
            return JunimoChestData.get(server.getServer()).inUse(owner, server.getServer());
        }
        return super.isInUse();
    }
    @Override public void startOpen(Player player) {
        super.startOpen(player);
        if (!player.isSpectator() && isSharedStorage() && owner != null && level instanceof ServerLevel server) {
            JunimoChestData.get(server.getServer()).opened(owner, player.getUUID());
        }
    }
    @Override public void stopOpen(Player player) {
        super.stopOpen(player);
        if (isSharedStorage() && owner != null && level instanceof ServerLevel server) JunimoChestData.get(server.getServer()).closed(owner, player.getUUID());
    }
    @Override public boolean stillValid(Player player) { return super.stillValid(player) && mayAccess(player); }
    @Override public void dropAllContents(Level level, BlockPos pos) {
        if (!isSharedStorage()) super.dropAllContents(level, pos);
    }
    @Override protected void saveAdditional(CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        super.saveAdditional(tag);
        if (owner != null) tag.putUUID("SharedOwner", owner);
    }
    @Override public void load(CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        super.load(tag);
        owner = tag.hasUUID("SharedOwner") ? tag.getUUID("SharedOwner") : null;
    }
}

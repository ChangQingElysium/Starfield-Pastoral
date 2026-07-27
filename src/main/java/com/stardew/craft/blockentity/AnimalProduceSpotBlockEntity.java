package com.stardew.craft.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nonnull;

@SuppressWarnings("null")
public class AnimalProduceSpotBlockEntity extends BlockEntity {
    private ItemStack produceStack = ItemStack.EMPTY;
    private long animalId = -1L;
    private String buildingId = "";
    private long produceLedgerEntryId = -1L;

    public AnimalProduceSpotBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.ANIMAL_PRODUCE_SPOT.get(), pos, blockState);
    }

    public ItemStack getProduceStack() {
        return produceStack;
    }

    public void setProduceStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            this.produceStack = ItemStack.EMPTY;
        } else {
            ItemStack single = stack.copy();
            single.setCount(1);
            this.produceStack = single;
        }
        setChangedAndSync();
    }

    public long getAnimalId() {
        return animalId;
    }

    public void setAnimalId(long animalId) {
        this.animalId = animalId;
        setChangedAndSync();
    }

    public String getBuildingId() {
        return buildingId;
    }

    public void setBuildingId(String buildingId) {
        this.buildingId = buildingId == null ? "" : buildingId;
        setChangedAndSync();
    }

    public long getProduceLedgerEntryId() {
        return produceLedgerEntryId;
    }

    public void setProduceLedgerEntryId(long produceLedgerEntryId) {
        this.produceLedgerEntryId = produceLedgerEntryId > 0L
                ? produceLedgerEntryId
                : -1L;
        setChangedAndSync();
    }

    public ItemStack harvestOne() {
        if (produceStack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack out = produceStack.copy();
        out.setCount(1);
        produceStack = ItemStack.EMPTY;
        setChangedAndSync();
        return out;
    }

    private void setChangedAndSync() {
        setChanged();
        Level lvl = getLevel();
        if (lvl != null) {
            BlockState state = getBlockState();
            lvl.sendBlockUpdated(worldPosition, state, state, 3);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        loadAdditional(tag, registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!produceStack.isEmpty()) {
            tag.put("produceStack", produceStack.save(registries));
        }
        tag.putLong("animalId", animalId);
        tag.putString("buildingId", buildingId);
        if (produceLedgerEntryId > 0L) {
            tag.putLong("produceLedgerEntryId", produceLedgerEntryId);
        }
    }

    @Override
    protected void loadAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        produceStack = tag.contains("produceStack") ? ItemStack.parse(registries, tag.getCompound("produceStack")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        animalId = tag.contains("animalId") ? tag.getLong("animalId") : -1L;
        buildingId = tag.contains("buildingId") ? tag.getString("buildingId") : "";
        produceLedgerEntryId = tag.contains("produceLedgerEntryId")
                ? tag.getLong("produceLedgerEntryId")
                : -1L;
    }
}

package com.stardew.craft.blockentity;

import com.stardew.craft.port.PortItemStacks;
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
    public CompoundTag getUpdateTag() { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        return saveWithoutMetadata();
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        load(tag);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        super.saveAdditional(tag);
        if (!produceStack.isEmpty()) {
            tag.put("produceStack", PortItemStacks.save(produceStack, registries));
        }
        tag.putLong("animalId", animalId);
        tag.putString("buildingId", buildingId);
        if (produceLedgerEntryId > 0L) {
            tag.putLong("produceLedgerEntryId", produceLedgerEntryId);
        }
    }

    @Override
    public void load(@Nonnull CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        super.load(tag);
        produceStack = tag.contains("produceStack") ? PortItemStacks.parse(registries, tag.getCompound("produceStack")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        animalId = tag.contains("animalId") ? tag.getLong("animalId") : -1L;
        buildingId = tag.contains("buildingId") ? tag.getString("buildingId") : "";
        produceLedgerEntryId = tag.contains("produceLedgerEntryId")
                ? tag.getLong("produceLedgerEntryId")
                : -1L;
    }
}

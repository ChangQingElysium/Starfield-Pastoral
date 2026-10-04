package com.stardew.craft.blockentity;

import com.stardew.craft.gingerisland.GingerIslandGemPedestalBlock;
import com.stardew.craft.gingerisland.GingerIslandStateDecorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** One saved stack at MAIN, no ticking and no inventory hidden in visual-state items. */
public final class GingerIslandGemPedestalBlockEntity extends BlockEntity {
    private ItemStack offering = ItemStack.EMPTY;

    public GingerIslandGemPedestalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GINGER_GEM_PEDESTAL.get(), pos, state);
    }

    public ItemStack offering() { return offering.copy(); }

    public boolean storeOffering(ItemStack stack) {
        if (!offering.isEmpty() || GingerIslandGemPedestalBlock.gemFor(stack) == GingerIslandStateDecorBlock.Gem.EMPTY)
            return false;
        offering = stack.copyWithCount(1);
        changed();
        return true;
    }

    public ItemStack clearOffering() {
        ItemStack result = offering;
        offering = ItemStack.EMPTY;
        changed();
        return result;
    }

    private void changed() {
        setChanged();
        syncVisual();
        if (level != null && !level.isClientSide)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    private void syncVisual() {
        if (level == null || level.isClientSide || !level.hasChunkAt(worldPosition)) return;
        BlockState state = level.getBlockState(worldPosition);
        if (!(state.getBlock() instanceof GingerIslandGemPedestalBlock block)
                || state.getValue(GingerIslandStateDecorBlock.PART) != GingerIslandStateDecorBlock.Part.MAIN) return;
        var gem = GingerIslandGemPedestalBlock.gemFor(offering);
        for (BlockPos part : block.placementPositions(worldPosition, state.getValue(GingerIslandStateDecorBlock.FACING))) {
            if (!level.hasChunkAt(part)) continue;
            BlockState actual = level.getBlockState(part);
            if (actual.is(block) && actual.getValue(GingerIslandStateDecorBlock.GEM) != gem)
                // The union footprint is unchanged. Avoid neighbor shape scans
                // (and loading adjacent chunks) when correcting a model onLoad.
                level.setBlock(part, actual.setValue(GingerIslandStateDecorBlock.GEM, gem), 2 | 16);
        }
    }

    @Override public void onLoad() {
        super.onLoad();
        // Old decorative gem states never represented paid inventory. Do not mint
        // gems from those states; only the saved stack can populate the model.
        syncVisual();
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!offering.isEmpty()) tag.put("Offering", offering.save(registries));
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ItemStack saved = tag.contains("Offering")
                ? ItemStack.parse(registries, tag.getCompound("Offering")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        offering = GingerIslandGemPedestalBlock.gemFor(saved) == GingerIslandStateDecorBlock.Gem.EMPTY
                ? ItemStack.EMPTY : saved.copyWithCount(1);
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}

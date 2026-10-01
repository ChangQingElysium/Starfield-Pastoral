package com.stardew.craft.blockentity;

import com.stardew.craft.port.PortItemStacks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Display-only copy: never expose an item handler or drop its contents. */
@SuppressWarnings("null")
public final class WoodSignBlockEntity extends BlockEntity {
    private ItemStack displayItem = ItemStack.EMPTY;

    public WoodSignBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WOOD_SIGN.get(), pos, state);
    }

    public ItemStack getDisplayItem() {
        return displayItem.copy();
    }

    public void setDisplayItem(ItemStack stack) {
        displayItem = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        super.saveAdditional(tag);
        if (!displayItem.isEmpty()) tag.put("DisplayItem", PortItemStacks.save(displayItem, registries));
    }

    @Override
    public void load(CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        super.load(tag);
        displayItem = PortItemStacks.parseOptional(registries, tag.getCompound("DisplayItem"));
        if (!displayItem.isEmpty()) displayItem.setCount(1);
    }

    @Override
    public CompoundTag getUpdateTag() { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        return saveWithoutMetadata();
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}

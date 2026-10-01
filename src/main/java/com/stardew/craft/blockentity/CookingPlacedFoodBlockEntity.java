package com.stardew.craft.blockentity;

import com.stardew.craft.port.PortItemStacks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class CookingPlacedFoodBlockEntity extends BlockEntity {
    private ItemStack storedFood = ItemStack.EMPTY;

    public CookingPlacedFoodBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PLACED_COOKING_FOOD.get(), pos, state);
    }

    public ItemStack getStoredFood() {
        return storedFood.copy();
    }

    public void setStoredFood(ItemStack stack) {
        storedFood = stack.copyWithCount(1);
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) { net.minecraft.core.HolderLookup.Provider provider = com.stardew.craft.port.PortRegistries.lookup();
        super.saveAdditional(tag);
        if (!storedFood.isEmpty()) {
            tag.put("StoredFood", PortItemStacks.save(storedFood, provider));
        }
    }

    @Override
    public void load(CompoundTag tag) { net.minecraft.core.HolderLookup.Provider provider = com.stardew.craft.port.PortRegistries.lookup();
        super.load(tag);
        storedFood = tag.contains("StoredFood", CompoundTag.TAG_COMPOUND)
                ? PortItemStacks.parseOptional(provider, tag.getCompound("StoredFood")) : ItemStack.EMPTY;
        if (!storedFood.isEmpty()) storedFood.setCount(1);
        if (level != null && level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public CompoundTag getUpdateTag() { net.minecraft.core.HolderLookup.Provider provider = com.stardew.craft.port.PortRegistries.lookup();
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}

package com.stardew.craft.blockentity;

import com.stardew.craft.model.AnimatedModel;
import com.stardew.craft.model.ModelAnimation;
import net.minecraft.core.BlockPos;
import com.stardew.craft.block.ModBlocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class LuauFestivalDecorBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity implements AnimatedModel {
    private boolean footprintChecked;

    public LuauFestivalDecorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LUAU_FESTIVAL_DECOR.get(), pos, state);
    }

    public void repairCauldronFootprint() {
        if (footprintChecked || level == null || level.isClientSide) return;
        if (!level.hasChunksAt(worldPosition.offset(-4, 0, -4), worldPosition.offset(4, 2, 4))) return;
        // Existing buildings are never overwritten. Try once after the surrounding chunks are ready.
        footprintChecked = true;
        if (getBlockState().getBlock() instanceof com.stardew.craft.block.decor.MapDecorStaticBlock block) {
            block.placeExtensions(level, worldPosition, getBlockState());
        }
    }

    @Override
    public ModelAnimation modelAnimation(boolean moving, float partialTick) {
        String name = getBlockState().is(ModBlocks.WIZARD_CAULDRON.get()) ? "wizard_cauldron"
                : getBlockState().is(ModBlocks.LUAU_SOUP_POT.get()) ? "luau_soup_pot" : null;
        return name == null ? null : ModelAnimation.loop("animation." + name + ".simmer");
    }

    @Override public int modelTransitionTicks() { return 0; }

    @SuppressWarnings("null")
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(5.0);
    }
}

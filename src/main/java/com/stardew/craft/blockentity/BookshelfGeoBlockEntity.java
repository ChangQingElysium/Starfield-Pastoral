package com.stardew.craft.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class BookshelfGeoBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity {
    public BookshelfGeoBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BOOKSHELF_GEO.get(), pos, state);
    }

    @SuppressWarnings("null")
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1.0, 0.0, 1.0).expandTowards(0.0, 2.0, 0.0);
    }
}

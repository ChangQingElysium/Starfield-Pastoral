package com.stardew.craft.templates;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WoodType;

public final class VanillaTemplateFenceGateBlock extends FenceGateBlock implements EntityBlock, TemplateBlock {
    public VanillaTemplateFenceGateBlock(BlockBehaviour.Properties properties) { super(properties, WoodType.OAK); } // PORT(1.20.1): 1.20.1 constructor takes Properties first
    @Override public TemplateShape templateShape() { return TemplateShape.FENCE_GATE; }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TemplateBlockEntity(pos, state);
    }
}

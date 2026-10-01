package com.stardew.craft.templates;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;

public final class VanillaTemplatePressurePlateBlock extends PressurePlateBlock implements EntityBlock, TemplateBlock {
    public VanillaTemplatePressurePlateBlock(BlockBehaviour.Properties properties) {
        // PORT(1.20.1): 1.21 derives the sensitivity from BlockSetType (EVERYTHING for OAK).
        super(PressurePlateBlock.Sensitivity.EVERYTHING, properties, BlockSetType.OAK);
    }
    @Override public TemplateShape templateShape() { return TemplateShape.PRESSURE_PLATE; }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TemplateBlockEntity(pos, state);
    }
}

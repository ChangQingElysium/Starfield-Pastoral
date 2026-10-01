package com.stardew.craft.templates;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;

public final class VanillaTemplateButtonBlock extends ButtonBlock implements EntityBlock, TemplateBlock {
    public VanillaTemplateButtonBlock(BlockBehaviour.Properties properties) {
        // PORT(1.20.1): Properties first; 1.21 derives arrow presses from BlockSetType (true for OAK).
        super(properties, BlockSetType.OAK, 20, true);
    }

    @Override public TemplateShape templateShape() { return TemplateShape.BUTTON; }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TemplateBlockEntity(pos, state);
    }
}

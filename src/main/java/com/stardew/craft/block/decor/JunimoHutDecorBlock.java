package com.stardew.craft.block.decor;

import com.stardew.craft.blockentity.JunimoHutDecorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@SuppressWarnings("null")
public class JunimoHutDecorBlock extends MapDecorStaticBlock implements EntityBlock {

    public JunimoHutDecorBlock(Properties properties, String modelId) {
        // Share the Wizard hut's authored collision and derived occupied cells.
        super(properties, modelId + "#aabb", true);
    }

    @Override
    public RenderShape getRenderShape(@Nonnull BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        if (state.getValue(PART) != Part.MAIN) {
            return null;
        }
        return new JunimoHutDecorBlockEntity(pos, state);
    }
}

package com.stardew.craft.block.terrain;

import com.mojang.serialization.MapCodec;
import com.stardew.craft.block.ModBlocks;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import com.stardew.craft.port.net.neoforged.neoforge.common.ItemAbilities;
import com.stardew.craft.port.net.neoforged.neoforge.common.ItemAbility;

/** Compact subsoil; cultivation and decay must retain its infertile identity. */
public final class HardSoilBlock extends Block {
    public static final MapCodec<HardSoilBlock> CODEC = simpleCodec(HardSoilBlock::new);

    public HardSoilBlock(Properties properties) { super(properties); }

    @Override public MapCodec<HardSoilBlock> codec() { return CODEC; }

    @Override
    @Nullable
    public BlockState getToolModifiedState(BlockState state, UseOnContext context, ItemAbility ability, boolean simulate) {
        if (ability == ItemAbilities.HOE_TILL) {
            return context.getClickedFace() != Direction.DOWN
                    && context.getLevel().getBlockState(context.getClickedPos().above()).isAir()
                    ? ModBlocks.INFERTILE_FARMLAND.get().defaultBlockState() : null;
        }
        return super.getToolModifiedState(state, context, ability, simulate);
    }
}

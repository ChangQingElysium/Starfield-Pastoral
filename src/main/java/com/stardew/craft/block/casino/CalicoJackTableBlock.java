package com.stardew.craft.block.casino;

import com.stardew.craft.casino.CasinoService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nonnull;

/** One original table model; map builders select the 1000-coin table with high_stakes=true. */
@SuppressWarnings("null")
public final class CalicoJackTableBlock extends CasinoInteractiveBlock {
    public static final BooleanProperty HIGH_STAKES = BooleanProperty.create("high_stakes");

    public CalicoJackTableBlock(Properties properties) {
        super(properties,
                "stardewcraft:casino/calico_jack_table",
                -16.0D, 0.0D, 0.0D, 16.0D, 15.0D, 32.0D,
                Kind.CALICO_JACK);
        registerDefaultState(defaultBlockState().setValue(HIGH_STAKES, false));
    }

    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HIGH_STAKES);
    }

    @Override
    protected InteractionResult useWithoutItem(
            @Nonnull BlockState state,
            @Nonnull Level level,
            @Nonnull BlockPos pos,
            @Nonnull Player player,
            @Nonnull BlockHitResult hit
    ) {
        if (state.getValue(PART) == Part.EXTENSION) {
            return super.useWithoutItem(state, level, pos, player, hit);
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            CasinoService.openCalicoJack(serverPlayer, state.getValue(HIGH_STAKES));
        }
        return InteractionResult.SUCCESS;
    }
}

package com.stardew.craft.block.casino;

import com.stardew.craft.block.decor.IntegratedAabbDecorBlock;
import com.stardew.craft.casino.CasinoService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nonnull;

/** Multi-cell casino furniture whose extensions forward interaction to one main block. */
@SuppressWarnings("null")
public class CasinoInteractiveBlock extends IntegratedAabbDecorBlock {
    private final Kind kind;

    public CasinoInteractiveBlock(
            Properties properties,
            String modelId,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            Kind kind
    ) {
        super(properties, modelId, minX, minY, minZ, maxX, maxY, maxZ);
        this.kind = kind;
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
            switch (kind) {
                case CLUB_COMPUTER -> CasinoService.openFarmerFile(serverPlayer);
                case CALICO_JACK -> CasinoService.openCalicoJack(serverPlayer, false);
                case SLOT_MACHINE -> CasinoService.openSlots(serverPlayer);
            }
        }
        return InteractionResult.SUCCESS;
    }

    public enum Kind {
        CLUB_COMPUTER,
        CALICO_JACK,
        SLOT_MACHINE
    }
}

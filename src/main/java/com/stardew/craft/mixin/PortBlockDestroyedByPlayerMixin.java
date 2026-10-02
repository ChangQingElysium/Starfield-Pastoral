package com.stardew.craft.mixin;

import com.stardew.craft.port.PortInheritance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;

/**
 * PORT(1.20.1): Forge 1.20.1's default {@code IForgeBlock#onDestroyedByPlayer} calls {@code playerWillDestroy} and
 * then sets the fluid's legacy block; NeoForge 1.21.1's default only removes the block, because 1.21
 * {@code ServerPlayerGameMode#destroyBlock} / {@code MultiPlayerGameMode#destroyBlock} call {@code playerWillDestroy}
 * themselves, before {@code canHarvestBlock}/{@code mineBlock} (see {@code PortServerPlayerGameModeWillDestroyMixin},
 * {@code PortMultiPlayerGameModeWillDestroyMixin}). No vanilla block overrides this hook, so this merged method
 * becomes the implementation every block inherits: StardewCraft blocks get the 1.21.1 body, all other blocks keep
 * the Forge 1.20.1 body verbatim. StardewCraft overrides that call {@code super} reach the 1.21.1 body, as in 1.21.1.
 */
@Mixin(Block.class)
public abstract class PortBlockDestroyedByPlayerMixin {
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest,
                                       FluidState fluid) {
        Block self = (Block) (Object) this;
        if (PortInheritance.isModBlock(self)) {
            if (level.isClientSide()) {
                return level.setBlock(pos, fluid.createLegacyBlock(), 11);
            }
            return level.removeBlock(pos, false);
        }
        self.playerWillDestroy(level, pos, state, player);
        return level.setBlock(pos, fluid.createLegacyBlock(), level.isClientSide ? 11 : 3);
    }
}

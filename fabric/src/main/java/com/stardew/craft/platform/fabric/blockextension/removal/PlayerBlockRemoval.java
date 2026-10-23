package com.stardew.craft.platform.fabric.blockextension.removal;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public final class PlayerBlockRemoval {
    private PlayerBlockRemoval() {}

    public static boolean defaultRemove(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        return level.isClientSide() ? level.setBlock(pos, fluid.createLegacyBlock(), 11) : level.removeBlock(pos, false);
    }
    public static boolean remove(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        return state.getBlock() instanceof PlayerDestroyProvider provider ? provider.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid)
                : defaultRemove(state, level, pos, player, willHarvest, fluid);
    }

    /** Invoked after native permissions and playerWillDestroy; survival tool wear precedes the removal callback. */
    public static boolean finishServer(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, Block originalBlock,
            BlockEntity blockEntity, boolean creative) {
        return finish(creative, new BreakActions() {
            @Override public ItemStack heldTool() { return player.getMainHandItem(); }
            @Override public boolean canHarvest() { return player.hasCorrectToolForDrops(state); }
            @Override public void mine(ItemStack stack) { stack.mineBlock(level, state, pos, player); }
            @Override public boolean remove(boolean harvest) { return PlayerBlockRemoval.remove(state, level, pos, player, harvest, level.getFluidState(pos)); }
            @Override public void destroyed() { state.getBlock().destroy(level, pos, state); }
            @Override public void drops(ItemStack snapshot) { originalBlock.playerDestroy(level, player, pos, state, blockEntity, snapshot); }
        });
    }

    public static boolean destroyClient(BlockState state, Level level, BlockPos pos, Player player) {
        var block = state.getBlock(); var removedState = block.playerWillDestroy(level, pos, state, player);
        boolean removed = remove(state, level, pos, player, false, level.getFluidState(pos));
        if (removed) block.destroy(level, pos, removedState);
        return removed;
    }

    interface BreakActions {
        ItemStack heldTool();
        boolean canHarvest();
        void mine(ItemStack stack);
        boolean remove(boolean harvest);
        void destroyed();
        void drops(ItemStack toolSnapshot);
    }
    static boolean finish(boolean creative, BreakActions actions) {
        if (creative) {
            if (actions.remove(false)) actions.destroyed();
        } else {
            ItemStack tool = actions.heldTool(), snapshot = tool.copy();
            boolean harvest = actions.canHarvest();
            actions.mine(tool);
            boolean removed = actions.remove(harvest);
            if (removed) actions.destroyed();
            if (harvest && removed) actions.drops(snapshot);
        }
        // Neo reports an approved break attempt as handled even when the provider vetoes actual removal.
        return true;
    }
}

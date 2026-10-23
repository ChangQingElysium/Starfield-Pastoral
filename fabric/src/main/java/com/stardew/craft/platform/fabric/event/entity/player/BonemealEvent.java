package com.stardew.craft.platform.fabric.event.entity.player;

import com.stardew.craft.platform.fabric.event.Event;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Bone meal application on both sides; dispensers supply a null player. */
public final class BonemealEvent extends Event {
    @Nullable private final Player player;
    private final Level level;
    private final BlockPos pos;
    private final BlockState state;
    private final ItemStack stack;
    private final boolean valid;
    private boolean successful;
    public BonemealEvent(@Nullable Player player, Level level, BlockPos pos, BlockState state, ItemStack stack) {
        super(true);
        this.player = player; this.level = level; this.pos = pos.immutable(); this.state = state; this.stack = stack;
        valid = state.getBlock() instanceof BonemealableBlock plant && plant.isValidBonemealTarget(level, pos, state);
    }
    @Nullable public Player getPlayer() { return player; }
    public Level getLevel() { return level; }
    public BlockPos getPos() { return pos; }
    public BlockState getState() { return state; }
    public ItemStack getStack() { return stack; }
    public boolean isValidBonemealTarget() { return valid; }
    public void setSuccessful(boolean successful) { this.successful = successful; setCanceled(true); }
    public boolean isSuccessful() { return successful; }
}

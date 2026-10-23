package com.stardew.craft.platform.fabric.event.sleep;

import com.stardew.craft.platform.fabric.event.PlayerEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player.BedSleepingProblem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class CanPlayerSleepEvent extends PlayerEvent {
    private final BlockPos pos;
    private final BlockState state;
    @Nullable private final BedSleepingProblem vanillaProblem;
    @Nullable private BedSleepingProblem problem;
    public CanPlayerSleepEvent(ServerPlayer player, BlockPos pos, @Nullable BedSleepingProblem problem) {
        this(player, pos, player.level().getBlockState(pos), problem);
    }
    CanPlayerSleepEvent(ServerPlayer player, BlockPos pos, BlockState state, @Nullable BedSleepingProblem problem) {
        super(player); this.pos = pos; this.state = state; this.vanillaProblem = this.problem = problem;
    }
    @Override public ServerPlayer getEntity() { return (ServerPlayer) super.getEntity(); }
    public Level getLevel() { return getEntity().level(); }
    public BlockPos getPos() { return pos; }
    public BlockState getState() { return state; }
    @Nullable public BedSleepingProblem getProblem() { return problem; }
    public void setProblem(@Nullable BedSleepingProblem problem) { this.problem = problem; }
    @Nullable public BedSleepingProblem getVanillaProblem() { return vanillaProblem; }
}

package com.stardew.craft.port.net.neoforged.neoforge.event.entity.player;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player.BedSleepingProblem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * PORT(1.20.1): NeoForge 21.1 {@code CanPlayerSleepEvent}, fired by
 * {@code com.stardew.craft.mixin.PortServerPlayerSleepMixin} in {@code ServerPlayer#startSleepInBed} with the vanilla
 * problem; a changed problem overrides the vanilla result (null forces sleeping).
 */
public class CanPlayerSleepEvent extends PlayerEvent {
    private final BlockPos pos;
    private final BlockState state;
    @Nullable
    private final BedSleepingProblem vanillaProblem;
    @Nullable
    private BedSleepingProblem problem;

    public CanPlayerSleepEvent() {
        super(null);
        this.pos = BlockPos.ZERO;
        this.state = null;
        this.vanillaProblem = null;
    }

    public CanPlayerSleepEvent(ServerPlayer player, BlockPos pos, @Nullable BedSleepingProblem problem) {
        super(player);
        this.pos = pos;
        this.state = player.level().getBlockState(pos);
        this.problem = this.vanillaProblem = problem;
    }

    @Override
    public ServerPlayer getEntity() {
        return (ServerPlayer) super.getEntity();
    }

    public Level getLevel() {
        return this.getEntity().level();
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public BlockState getState() {
        return this.state;
    }

    @Nullable
    public BedSleepingProblem getProblem() {
        return this.problem;
    }

    public void setProblem(@Nullable BedSleepingProblem problem) {
        this.problem = problem;
    }

    @Nullable
    public BedSleepingProblem getVanillaProblem() {
        return this.vanillaProblem;
    }
}

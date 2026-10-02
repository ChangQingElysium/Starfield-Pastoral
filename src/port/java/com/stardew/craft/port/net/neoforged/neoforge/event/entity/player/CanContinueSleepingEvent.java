package com.stardew.craft.port.net.neoforged.neoforge.event.entity.player;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player.BedSleepingProblem;
import net.minecraftforge.event.entity.living.LivingEvent;

/**
 * PORT(1.20.1): NeoForge 21.1 {@code CanContinueSleepingEvent}. Bridged from Forge {@code SleepingTimeCheckEvent}
 * (sleeping player tick; problem NOT_POSSIBLE_NOW during the day) and {@code SleepingLocationCheckEvent} (bed check;
 * problem NOT_POSSIBLE_HERE without a bed); a changed decision becomes the Forge ALLOW/DENY result.
 */
public class CanContinueSleepingEvent extends LivingEvent {
    @Nullable
    protected final BedSleepingProblem problem;
    protected boolean mayContinueSleeping;

    public CanContinueSleepingEvent() {
        this(null, null);
    }

    public CanContinueSleepingEvent(LivingEntity entity, @Nullable BedSleepingProblem problem) {
        super(entity);
        this.problem = problem;
        this.mayContinueSleeping = problem == null;
    }

    Optional<BlockPos> getSleepingPos() {
        return this.getEntity().getSleepingPos();
    }

    @Nullable
    public BedSleepingProblem getProblem() {
        return this.problem;
    }

    public boolean mayContinueSleeping() {
        return this.mayContinueSleeping;
    }

    public void setContinueSleeping(boolean sleeping) {
        this.mayContinueSleeping = sleeping;
    }
}

package com.stardew.craft.platform.fabric.event.sleep;

import com.stardew.craft.platform.fabric.event.entity.living.LivingEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player.BedSleepingProblem;
import org.jetbrains.annotations.Nullable;

public final class CanContinueSleepingEvent extends LivingEvent {
    @Nullable private final BedSleepingProblem problem;
    private boolean sleeping;
    public CanContinueSleepingEvent(LivingEntity entity, @Nullable BedSleepingProblem problem) {
        super(entity, false); this.problem = problem; sleeping = problem == null;
    }
    @Nullable public BedSleepingProblem getProblem() { return problem; }
    public boolean mayContinueSleeping() { return sleeping; }
    public void setContinueSleeping(boolean sleeping) { this.sleeping = sleeping; }
}

package com.stardew.craft.cutscene.command;

import com.stardew.craft.cutscene.runtime.EventPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * jump: make an actor jump.
 * JSON: {"cmd":"jump", "actor":"robin"}
 * Optional: "strength": 0.5 (default 0.5)
 */
public class JumpCommand implements EventCommand {
    private final String actorTag;
    private final double strength;

    private Mob actor;
    private double startY;
    private double verticalVelocity;
    private int ticks;
    private boolean done;

    public JumpCommand(String actorTag, double strength) {
        this.actorTag = actorTag;
        this.strength = strength;
    }

    @Override
    public void start(EventPlayer player) {
        actor = player.getActor(actorTag);
        if (actor == null) {
            done = true;
            return;
        }
        startY = actor.getY();
        verticalVelocity = strength;
        ticks = 0;
        done = false;
    }

    @Override
    public void tick(EventPlayer player) {
        if (done || actor == null) return;
        ticks++;
        verticalVelocity = (verticalVelocity - 0.08) * 0.98;
        actor.move(MoverType.SELF, new Vec3(0, verticalVelocity, 0));
        if ((ticks > 2 && actor.onGround()) || ticks >= 40) {
            if (ticks >= 40) {
                actor.setPos(actor.getX(), startY, actor.getZ());
            }
            done = true;
        }
    }

    @Override public boolean isComplete() { return done; }
}

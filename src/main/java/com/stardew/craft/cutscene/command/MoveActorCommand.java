package com.stardew.craft.cutscene.command;

import com.stardew.craft.cutscene.runtime.EventActorEntity;
import com.stardew.craft.cutscene.runtime.EventPlayerActorEntity;
import com.stardew.craft.cutscene.runtime.EventPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/**
 * move_actor: smoothly moves an actor to a target position over a number of ticks.
 * JSON: { "cmd": "move_actor", "actor": "alice", "x": 3, "y": 0, "z": 0, "ticks": 40, "relative": true }
 * X/Z define the authored route. Y is resolved from world collision so actors
 * stay grounded, step up blocks, and fall instead of drifting between heights.
 */
public class MoveActorCommand implements EventCommand {

    private static final int MAX_SETTLE_TICKS = 8;

    private final String actorTag;
    private final double dx, dy, dz;
    private final int totalTicks;
    private final boolean relative;
    private final String anchor;

    private Mob actor;
    private double startX, startZ;
    private double endX, endY, endZ;
    private int ticksElapsed;
    private int settleTicks;
    private double verticalVelocity;
    private boolean done;

    public MoveActorCommand(String actorTag, double x, double y, double z, int ticks, boolean relative) {
        this(actorTag, x, y, z, ticks, relative, null);
    }

    public MoveActorCommand(String actorTag, double x, double y, double z, int ticks,
                             boolean relative, String anchor) {
        this.actorTag = actorTag;
        this.dx = x;
        this.dy = y;
        this.dz = z;
        this.totalTicks = Math.max(1, ticks);
        this.relative = relative;
        this.anchor = anchor;
    }

    @Override
    public void start(EventPlayer player) {
        actor = player.getActor(actorTag);
        if (actor == null) {
            done = true;
            return;
        }

        startX = actor.getX();
        startZ = actor.getZ();

        if (relative) {
            endX = startX + dx;
            endY = actor.getY() + dy;
            endZ = startZ + dz;
        } else {
            endX = dx + com.stardew.craft.cutscene.runtime.CutsceneAnchorRegistry.offsetX(anchor);
            endY = dy + com.stardew.craft.cutscene.runtime.CutsceneAnchorRegistry.offsetY(anchor);
            endZ = dz + com.stardew.craft.cutscene.runtime.CutsceneAnchorRegistry.offsetZ(anchor);
        }

        // Face towards movement direction
        double dirX = endX - startX;
        double dirZ = endZ - startZ;
        if (dirX != 0 || dirZ != 0) {
            float yaw = (float) (Mth.atan2(dirZ, dirX) * Mth.RAD_TO_DEG) - 90.0f;
            actor.setYRot(yaw);
            actor.setYHeadRot(yaw);
            actor.setYBodyRot(yaw);
        }

        // Set walking flag on supported actor types
        if (actor instanceof EventActorEntity npcActor) {
            npcActor.setWalking(true);
        } else if (actor instanceof EventPlayerActorEntity playerActor) {
            playerActor.setWalking(true);
        }
        ticksElapsed = 0;
        settleTicks = 0;
        verticalVelocity = 0;
        done = false;

        // Establish ground contact before movement begins. Cutscene actors keep
        // noGravity=true so their normal Mob travel tick cannot fight this command.
        actor.move(MoverType.SELF, new Vec3(0, -0.5, 0));
    }

    @Override
    public void tick(EventPlayer player) {
        if (done) return;

        if (actor == null) {
            done = true;
            return;
        }

        ticksElapsed++;
        float t = (float) ticksElapsed / (float) totalTicks;
        t = Math.min(t, 1.0f);

        // Compute desired XZ position via lerp
        double desiredX = Mth.lerp(t, startX, endX);
        double desiredZ = Mth.lerp(t, startZ, endZ);
        double moveX = desiredX - actor.getX();
        double moveZ = desiredZ - actor.getZ();

        if (!actor.onGround()) {
            verticalVelocity -= 0.08;
            verticalVelocity *= 0.98;
        } else {
            verticalVelocity = -0.04;
        }

        // Collision movement keeps actors on floors, lets them fall naturally,
        // and uses their one-block step height for stairs.
        actor.move(MoverType.SELF, new Vec3(moveX, verticalVelocity, moveZ));

        if (ticksElapsed >= totalTicks) {
            setWalking(false);
            boolean reachedAuthoredY = Math.abs(actor.getY() - endY) <= 0.1;
            if ((actor.onGround() && reachedAuthoredY) || ++settleTicks >= MAX_SETTLE_TICKS) {
                // Collision/gravity owns the route, but authored event points own
                // the exact endpoint so dialogue never begins with an actor airborne.
                actor.setPos(endX, endY, endZ);
                done = true;
            }
        }
    }

    private void setWalking(boolean walking) {
        if (actor instanceof EventActorEntity npcActor) {
            npcActor.setWalking(walking);
        } else if (actor instanceof EventPlayerActorEntity playerActor) {
            playerActor.setWalking(walking);
        }
    }

    @Override
    public boolean isComplete() { return done; }
}

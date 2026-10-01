package com.stardew.craft.cutscene.command;

import com.stardew.craft.cutscene.runtime.EventCameraController;
import com.stardew.craft.cutscene.runtime.EventPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * camera_follow: makes the camera track an actor's position each tick.
 * The camera offset (dx, dy, dz) is relative to the actor.
 * JSON: {"cmd":"camera_follow", "actor":"wizard", "dx":0, "dy":3, "dz":5, "yaw":0, "pitch":30, "ticks":60}
 */
@OnlyIn(Dist.CLIENT)
public class CameraFollowCommand implements EventCommand {

    private final String actorTag;
    private final double dx, dy, dz;
    private final float yaw, pitch;
    private final int durationTicks;
    private int elapsed = 0;

    public CameraFollowCommand(String actorTag, double dx, double dy, double dz,
                                float yaw, float pitch, int durationTicks) {
        this.actorTag = actorTag;
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.yaw = yaw;
        this.pitch = pitch;
        this.durationTicks = durationTicks;
    }

    @Override
    public void start(EventPlayer player) {
        elapsed = 0;
        updateCamera(player);
    }

    @Override
    public void tick(EventPlayer player) {
        elapsed++;
        updateCamera(player);
    }

    private void updateCamera(EventPlayer player) {
        Mob actor = player.getActor(actorTag);
        if (actor != null) {
            EventCameraController.setPosition(
                    actor.getX() + dx,
                    actor.getY() + dy,
                    actor.getZ() + dz,
                    yaw, pitch
            );
        }
    }

    @Override
    public boolean isComplete() {
        return elapsed >= durationTicks;
    }
}

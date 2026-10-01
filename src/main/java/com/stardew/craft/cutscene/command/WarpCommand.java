package com.stardew.craft.cutscene.command;

import com.stardew.craft.cutscene.runtime.EventPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * warp: instantly teleport an actor (or the player) to a position.
 * JSON: {"cmd":"warp", "actor":"wizard", "x":100, "y":65, "z":200}
 * JSON: {"cmd":"warp", "actor":"player", "x":100, "y":65, "z":200}
 * If "relative" is true, coordinates are relative to current position.
 */
@OnlyIn(Dist.CLIENT)
public class WarpCommand implements EventCommand {

    private final String actorTag;
    private final double x, y, z;
    private final boolean relative;
    private final String anchor;

    public WarpCommand(String actorTag, double x, double y, double z, boolean relative) {
        this(actorTag, x, y, z, relative, null);
    }

    public WarpCommand(String actorTag, double x, double y, double z, boolean relative, String anchor) {
        this.actorTag = actorTag;
        this.x = x;
        this.y = y;
        this.z = z;
        this.relative = relative;
        this.anchor = anchor;
    }

    @SuppressWarnings("null")
    @Override
    public void start(EventPlayer player) {
        double ax = com.stardew.craft.cutscene.runtime.CutsceneAnchorRegistry.offsetX(anchor);
        double ay = com.stardew.craft.cutscene.runtime.CutsceneAnchorRegistry.offsetY(anchor);
        double az = com.stardew.craft.cutscene.runtime.CutsceneAnchorRegistry.offsetZ(anchor);
        if ("player".equals(actorTag)) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                mc.player.fallDistance = 0.0F;
            }
        } else {
            Mob actor = player.getActor(actorTag);
            if (actor != null) {
                double tx = relative ? actor.getX() + x : x + ax;
                double ty = relative ? actor.getY() + y : y + ay;
                double tz = relative ? actor.getZ() + z : z + az;
                actor.setPos(tx, ty, tz);
            }
        }
    }

    @Override public void tick(EventPlayer player) {}
    @Override public boolean isComplete() { return true; }
}

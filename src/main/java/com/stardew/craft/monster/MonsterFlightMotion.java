package com.stardew.craft.monster;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

/** Shared 3D inertia in source pixels per substep; each species supplies its own motion limits. */
public class MonsterFlightMotion {
    private Vec3 velocity = Vec3.ZERO, heading = new Vec3(0, 0, -1);
    private int slipperiness = 24, hitMilliseconds;
    private double minAcceleration, maxAcceleration, speedLimit, idleRatio;
    protected MonsterFlightMotion(double minAcceleration, double maxAcceleration, double speedLimit, double idleRatio) {
        configure(minAcceleration,maxAcceleration,speedLimit,idleRatio);
    }
    protected final void configure(double minAcceleration,double maxAcceleration,double speedLimit,double idleRatio) {
        this.minAcceleration=minAcceleration;this.maxAcceleration=maxAcceleration;this.speedLimit=speedLimit;this.idleRatio=idleRatio;
    }
    protected final void slipperiness(int value) { slipperiness=value; }
    public final double speedLimit() { return speedLimit; }
    public final int hitMilliseconds() { return hitMilliseconds; }
    public Vec3 velocity() { return velocity; }
    public Vec3 heading() { return heading; }
    public void hit() { hitMilliseconds = 500; }
    public void elapsed(int ms) { hitMilliseconds = Math.max(0, hitMilliseconds - ms); }
    public void knockback(double x, double z) {
        velocity = new Vec3(Math.abs(x) > Math.abs(velocity.x) ? x : velocity.x, velocity.y,
                Math.abs(z) > Math.abs(velocity.z) ? z : velocity.z);
    }
    public void steer(Vec3 offset, boolean chase, boolean turningAllowed) {
        velocity = velocity.scale(1 - 1.0 / slipperiness);
        if (offset == null || offset.lengthSqr() < .01) {
            velocity = velocity.scale(.9);
            return;
        }
        Vec3 desired = offset.normalize();
        if (turningAllowed && hitMilliseconds == 0) heading = turnTowards(heading, desired, Math.PI / 64);
        // Species speed is a total 3D limit, not an independent allowance per axis.
        double acceleration = com.stardew.craft.port.PortJava.clamp(maxAcceleration - offset.length() / 2, minAcceleration, maxAcceleration) / 6;
        double maximum = speedLimit * (chase ? 1 : idleRatio) * Math.min(1, offset.length() / .9);
        // Reduce powered motion while turning away from a wall; residual inertia still decays.
        maximum *= .2 + .8 * Math.max(0, heading.dot(desired));
        // Brake smoothly at the route node rather than teleporting or snapping the turn.
        Vec3 desiredVelocity = heading.scale(maximum);
        Vec3 change = desiredVelocity.subtract(velocity);
        velocity = velocity.add(change.normalize().scale(Math.min(acceleration, change.length())));
        if (velocity.length() > speedLimit) velocity = velocity.normalize().scale(speedLimit);
    }
    public void blocked(Vec3 normal) {
        double into = velocity.dot(normal);
        if (into < 0) velocity = velocity.subtract(normal.scale(into));
        velocity = velocity.scale(.35);
    }
    public void stop() { velocity = Vec3.ZERO; }
    public static Vec3 turnTowards(Vec3 from, Vec3 to, double maxAngle) {
        double dot = com.stardew.craft.port.PortJava.clamp(from.dot(to), -1, 1), angle = Math.acos(dot);
        if (angle <= maxAngle) return to;
        Vec3 tangent = to.subtract(from.scale(dot));
        if (tangent.lengthSqr() < 1e-10) {
            tangent = from.cross(Math.abs(from.y) < .9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0));
        }
        return from.scale(Math.cos(maxAngle)).add(tangent.normalize().scale(Math.sin(maxAngle))).normalize();
    }
    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putDouble("X", velocity.x); tag.putDouble("Y", velocity.y); tag.putDouble("Z", velocity.z);
        tag.putDouble("HeadingX", heading.x); tag.putDouble("HeadingY", heading.y); tag.putDouble("HeadingZ", heading.z);
        tag.putInt("Slipperiness", slipperiness); tag.putInt("HitMillis", hitMilliseconds);
        return tag;
    }
    public void load(CompoundTag tag) {
        velocity = new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"));
        heading = new Vec3(tag.getDouble("HeadingX"), tag.getDouble("HeadingY"), tag.getDouble("HeadingZ"));
        if (!Double.isFinite(velocity.lengthSqr()) || velocity.length() > 32) velocity = Vec3.ZERO;
        if (!Double.isFinite(heading.lengthSqr()) || heading.lengthSqr() < .5) heading = new Vec3(0, 0, -1);
        else if (Math.abs(heading.lengthSqr()-1)>1e-6) heading=heading.normalize();
        slipperiness = com.stardew.craft.port.PortJava.clamp(tag.getInt("Slipperiness"), 14, 33);
        hitMilliseconds = com.stardew.craft.port.PortJava.clamp(tag.getInt("HitMillis"), 0, 500);
    }
}

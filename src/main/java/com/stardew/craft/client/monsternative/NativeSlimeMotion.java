package com.stardew.craft.client.monsternative;

import com.stardew.craft.client.npcnative.NativeNpcModel;
import com.stardew.craft.client.npcnative.NativeNpcPose;
import com.stardew.craft.entity.monster.GreenSlimeEntity;

/** Per-observer pose state, sampled once per game time; never drives server attacks or movement. */
public final class NativeSlimeMotion {
    private final NativeNpcPose pose, outgoing;
    private double lastClock = Double.NaN, lastX, lastZ, lastMoved, walkTime;
    private double transitionStart, transitionDuration, outgoingLift, lift;
    private int action = -1, sequence = -1;

    public NativeSlimeMotion(NativeNpcModel model) {
        pose = new NativeNpcPose(model);
        outgoing = new NativeNpcPose(model);
    }
    public NativeNpcPose pose() { return pose; }
    public double lift() { return lift; }

    public void sample(int phase, int actionSequence, double actionTime, double clock,
                       double x, double z, float growth, boolean dying) {
        if (clock == lastClock) return; // Multiple render passes must not advance the motion twice.
        boolean reset = !Double.isFinite(lastClock) || clock < lastClock || clock - lastClock > .5
                || Math.hypot(x - lastX, z - lastZ) > 2;
        double dt = reset ? 0 : clock - lastClock;
        if (reset) { action = -1; walkTime = 0; lastMoved = clock - 1; }
        boolean moved = !reset && Math.hypot(x - lastX, z - lastZ) > .015 * dt;
        if (moved) lastMoved = clock;
        // Source animateTimer may keep WALK alive after stopping; don't keep hopping against a wall.
        int next = dying ? GreenSlimeEntity.REST : phase == GreenSlimeEntity.WALK && clock - lastMoved > .1
                ? GreenSlimeEntity.REST : phase;
        if (next == GreenSlimeEntity.WALK && moved) walkTime += dt;
        if (next != action || (next >= GreenSlimeEntity.CHARGE && sequence != actionSequence)) {
            outgoing.copyFrom(pose);
            outgoingLift = lift;
            transitionStart = clock;
            transitionDuration = action < 0 ? 0 : next == GreenSlimeEntity.DASH ? .08 : .18;
            action = next;
            sequence = actionSequence;
        }
        String clip = switch (next) {
            case GreenSlimeEntity.WALK -> "idle_hop";
            case GreenSlimeEntity.CHARGE -> "charge";
            case GreenSlimeEntity.DASH -> "airborne";
            case GreenSlimeEntity.LAND -> "land";
            default -> "idle";
        };
        double time = next == GreenSlimeEntity.WALK ? walkTime : next == GreenSlimeEntity.REST ? clock : actionTime;
        pose.reset();
        pose.apply("animation.slime." + clip, time);
        double targetLift = next == GreenSlimeEntity.WALK ? walkLift(time, growth)
                : NativeMonsterPresentation.slimeLift(next == GreenSlimeEntity.DASH, dying, time, growth);
        double t = transitionDuration == 0 ? 1 : com.stardew.craft.port.PortJava.clamp((clock - transitionStart) / transitionDuration, 0, 1);
        double weight = t * t * (3 - 2 * t);
        pose.blendFrom(outgoing, 1 - weight);
        lift = outgoingLift + (targetLift - outgoingLift) * weight;
        lastClock = clock; lastX = x; lastZ = z;
    }
    /** Small ordinary hop is an explicit MC presentation choice, not source locomotion physics. */
    public static double walkLift(double time, float growth) {
        double phase = time - Math.floor(time / 1.05) * 1.05;
        double t = com.stardew.craft.port.PortJava.clamp((phase - .14) / .51, 0, 1);
        double arch = Math.sin(Math.PI * t);
        return growth * .14 * arch * arch;
    }
}

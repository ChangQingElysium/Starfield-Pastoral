package com.stardew.craft.combat.skill.handler;

/** Shared authored values for the two heavy weapons; also used by presentation and tests. */
public final class HeavyHammerRules {
    public static final String SWEEP = "galaxy_hammer_starshock_sweep";
    public static final String QUAKE = "galaxy_hammer_starfall_quake";
    public static final String PRESS = "infinity_gavel_singularity_press";
    public static final String ENDLESS = "infinity_gavel_endless_pounding";
    public static final String POUND = "infinity_gavel_pound";
    public static final int BURST_START = 3, BURST_DURATION = 100, BURST_INTERVAL = 7;
    public static final int POUND_WINDUP = 2;
    public static int poundAnimationTicks(double interval) {
        return com.stardew.craft.port.PortJava.clamp((int)Math.ceil(interval), 2, BURST_INTERVAL);
    }
    public static int poundContactTicks(int animationTicks) {
        return Math.max(1, Math.round(animationTicks * (2f / 7f)));
    }
    private HeavyHammerRules() {}

    public static boolean isWeapon(String id) {
        return "galaxy_hammer".equals(id) || "infinity_gavel".equals(id);
    }
    public static boolean supports(String weapon, String skill) {
        return "galaxy_hammer".equals(weapon) ? SWEEP.equals(skill) || QUAKE.equals(skill)
                : "infinity_gavel".equals(weapon) && (PRESS.equals(skill) || ENDLESS.equals(skill) || POUND.equals(skill));
    }
    public static int cooldown(String skill) {
        return QUAKE.equals(skill) ? 22 : ENDLESS.equals(skill) ? 30 : 7;
    }
    public static int animationTicks(String skill) {
        return SWEEP.equals(skill) ? 12 : QUAKE.equals(skill) ? 16 : PRESS.equals(skill) ? 15 : ENDLESS.equals(skill) ? 3 : 7;
    }
    public static boolean inArc(double forward, double sideways, double radius, double halfAngleDegrees) {
        double distance = Math.hypot(forward, sideways);
        return distance <= radius && (distance < 1.0E-6 || forward / distance >= Math.cos(Math.toRadians(halfAngleDegrees)) - 1.0E-8);
    }
    public static double burstInterval(double normalTicks) {
        return Math.max(1, Math.min(BURST_INTERVAL, normalTicks));
    }
    public static float quakeDamage(int phase) {
        return switch (phase) { case 0 -> 2.2f; case 1 -> 1f; case 2 -> 2.8f; default -> throw new IllegalArgumentException("phase"); };
    }
    public static int quakeTick(int phase) {
        return switch (phase) { case 0 -> 7; case 1 -> 14; case 2 -> 22; default -> throw new IllegalArgumentException("phase"); };
    }
}

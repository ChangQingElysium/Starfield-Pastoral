package com.stardew.craft.model;

/** Client presentation request; contains no renderer or third-party animation objects. */
public record ModelAnimation(String clip, boolean loop, boolean hold, double time, boolean initiallyComplete) {
    public static ModelAnimation loop(String clip) { return new ModelAnimation(clip, true, false, Double.NaN, false); }
    public static ModelAnimation hold(String clip) { return new ModelAnimation(clip, false, true, Double.NaN, false); }
    public static ModelAnimation play(String clip) { return new ModelAnimation(clip, false, false, Double.NaN, false); }
    public static ModelAnimation at(String clip, double seconds) { return new ModelAnimation(clip, false, true, seconds, false); }
    public static ModelAnimation state(String clip) { return new ModelAnimation(clip, false, true, Double.NaN, true); }
    public ModelAnimation withTime(double seconds) { return new ModelAnimation(clip, loop, hold, seconds, initiallyComplete); }
}

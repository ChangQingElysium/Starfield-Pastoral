package com.stardew.craft.model;

/** State selection shared by ordinary entities and block entities, safe on a dedicated server. */
public interface AnimatedModel {
    default ModelAnimation modelAnimation(boolean moving, float partialTick) { return null; }
    default int modelTransitionTicks() { return 5; }
}

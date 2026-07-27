package com.stardew.craft.api.v1.npc;

/** Observes completed NPC entity lifecycle transitions. */
@FunctionalInterface
public interface StardewNpcLifecycleListener {
    void onLifecycle(StardewNpcLifecycleEvent event);
}

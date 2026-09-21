package com.stardew.craft.api.v1.progress;

@FunctionalInterface
public interface StardewProgressListener {
    void onTransition(StardewProgressEvent event);
}

package com.stardew.craft.platform.fabric.event;

/** Implemented on LevelChunk by the constructor mixin; true only for its first generated load. */
public interface ChunkLoadState {
    boolean stardewcraft$consumeNewlyGenerated();
}

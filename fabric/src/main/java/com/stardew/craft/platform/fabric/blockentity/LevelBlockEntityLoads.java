package com.stardew.craft.platform.fabric.blockentity;

/** Implemented on native Level by the lifecycle mixin; no global world cache. */
public interface LevelBlockEntityLoads {
    BlockEntityLoadQueue stardewcraft$blockEntityLoads();
}

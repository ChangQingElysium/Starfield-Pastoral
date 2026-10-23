package com.stardew.craft.platform.fabric.client.keys;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public interface IKeyConflictContext {
    boolean isActive();
    boolean conflicts(IKeyConflictContext other);
}

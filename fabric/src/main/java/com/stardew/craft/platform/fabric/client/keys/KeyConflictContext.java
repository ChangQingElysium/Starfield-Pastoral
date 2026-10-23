package com.stardew.craft.platform.fabric.client.keys;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;

@Environment(EnvType.CLIENT)
public enum KeyConflictContext implements IKeyConflictContext {
    UNIVERSAL {
        @Override public boolean isActive() { return true; }
        @Override public boolean conflicts(IKeyConflictContext other) { return true; }
    },
    GUI {
        @Override public boolean isActive() { return Minecraft.getInstance().screen != null; }
        @Override public boolean conflicts(IKeyConflictContext other) { return other == this; }
    },
    IN_GAME {
        @Override public boolean isActive() { return !GUI.isActive(); }
        @Override public boolean conflicts(IKeyConflictContext other) { return other == this; }
    }
}

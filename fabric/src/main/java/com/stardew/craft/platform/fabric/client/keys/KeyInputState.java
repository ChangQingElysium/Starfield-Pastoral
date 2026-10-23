package com.stardew.craft.platform.fabric.client.keys;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

@Environment(EnvType.CLIENT)
public interface KeyInputState {
    KeyInputState PHYSICAL = new KeyInputState() {
        @Override public boolean control() { return Screen.hasControlDown(); }
        @Override public boolean shift() { return Screen.hasShiftDown(); }
        @Override public boolean alt() { return Screen.hasAltDown(); }
        @Override public boolean mac() { return Minecraft.ON_OSX; }
        @Override public boolean isDown(InputConstants.Key key) {
            return InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), key.getValue());
        }
    };
    boolean control();
    boolean shift();
    boolean alt();
    boolean mac();
    boolean isDown(InputConstants.Key key);
}

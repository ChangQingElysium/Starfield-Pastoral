package com.stardew.craft.platform.fabric.client.keys;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public interface KeyMappingExtension {
    InputConstants.Key stardewcraft$key();
    KeyBindingState stardewcraft$bindingState();
    void stardewcraft$click();
}

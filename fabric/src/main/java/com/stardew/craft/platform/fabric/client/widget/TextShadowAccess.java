package com.stardew.craft.platform.fabric.client.widget;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public interface TextShadowAccess {
    void stardewcraft$setTextShadow(boolean shadow);
    boolean stardewcraft$getTextShadow();
}

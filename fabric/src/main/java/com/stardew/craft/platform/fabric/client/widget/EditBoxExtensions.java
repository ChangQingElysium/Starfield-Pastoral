package com.stardew.craft.platform.fabric.client.widget;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.EditBox;

@Environment(EnvType.CLIENT)
public final class EditBoxExtensions {
    private EditBoxExtensions() {}
    public static void setTextShadow(EditBox box, boolean shadow) { access(box).stardewcraft$setTextShadow(shadow); }
    public static boolean getTextShadow(EditBox box) { return access(box).stardewcraft$getTextShadow(); }
    private static TextShadowAccess access(EditBox box) {
        if (box instanceof TextShadowAccess access) return access;
        throw new IllegalStateException("Fabric EditBox text shadow mixin is missing");
    }
}

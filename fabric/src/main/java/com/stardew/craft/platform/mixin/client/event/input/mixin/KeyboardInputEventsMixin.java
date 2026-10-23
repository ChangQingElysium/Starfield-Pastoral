package com.stardew.craft.platform.mixin.client.event.input.mixin;


import com.stardew.craft.platform.fabric.client.event.input.FabricInputEvents;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(KeyboardHandler.class)
public abstract class KeyboardInputEventsMixin {
    // javac gives RELEASE its own RETURN. Both exits after getKey correspond to the source-level
    // post-processing hook; foreign windows and screen-consumed/fullscreen/screenshot exits do not.
    @Inject(method = "keyPress", at = @At("RETURN"), slice = @Slice(from = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/InputConstants;getKey(II)Lcom/mojang/blaze3d/platform/InputConstants$Key;")))
    private void stardewcraft$key(long window, int key, int scanCode, int action, int modifiers, CallbackInfo callback) {
        FabricInputEvents.key(key, scanCode, action, modifiers);
    }
}

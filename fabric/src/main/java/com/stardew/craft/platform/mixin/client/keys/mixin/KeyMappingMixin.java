package com.stardew.craft.platform.mixin.client.keys.mixin;


import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.platform.InputConstants;
import com.stardew.craft.platform.fabric.client.keys.FabricKeyMappings;
import com.stardew.craft.platform.fabric.client.keys.KeyBindingState;
import com.stardew.craft.platform.fabric.client.keys.KeyInputState;
import com.stardew.craft.platform.fabric.client.keys.KeyMappingExtension;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin implements KeyMappingExtension {
    @Shadow @Final private static Map<String, KeyMapping> ALL;
    @Shadow private InputConstants.Key key;
    @Shadow private int clickCount;
    @Unique private final KeyBindingState stardewcraft$state = new KeyBindingState();
    @Override public InputConstants.Key stardewcraft$key() { return key; }
    @Override public KeyBindingState stardewcraft$bindingState() { return stardewcraft$state; }
    @Override public void stardewcraft$click() { clickCount++; }

    @Inject(method = "click", at = @At("HEAD"), cancellable = true)
    private static void stardewcraft$clickAll(InputConstants.Key code, CallbackInfo callback) {
        FabricKeyMappings.click(ALL.values(), code, KeyInputState.PHYSICAL); callback.cancel();
    }
    @Inject(method = "set", at = @At("HEAD"), cancellable = true)
    private static void stardewcraft$setAll(InputConstants.Key code, boolean held, CallbackInfo callback) {
        FabricKeyMappings.set(ALL.values(), code, held, KeyInputState.PHYSICAL); callback.cancel();
    }
    @ModifyReturnValue(method = "isDown", at = @At("RETURN"))
    private boolean stardewcraft$activeDown(boolean down) { return down && stardewcraft$state.active(KeyInputState.PHYSICAL); }
    @Inject(method = "same", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$same(KeyMapping other, CallbackInfoReturnable<Boolean> callback) {
        callback.setReturnValue(FabricKeyMappings.same((KeyMapping) (Object) this, other));
    }
    @ModifyReturnValue(method = "isDefault", at = @At("RETURN"))
    private boolean stardewcraft$defaultModifier(boolean original) { return original && stardewcraft$state.modifier() == stardewcraft$state.defaultModifier(); }
    @ModifyReturnValue(method = "getTranslatedKeyMessage", at = @At("RETURN"))
    private Component stardewcraft$modifierName(Component original) {
        return stardewcraft$state.modifier().getCombinedName(key, () -> original);
    }
}

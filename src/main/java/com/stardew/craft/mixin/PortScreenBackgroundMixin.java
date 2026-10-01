package com.stardew.craft.mixin;

import com.stardew.craft.port.PortScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PORT(1.20.1): 1.21.1 {@code Screen} lifecycle steps missing on 1.20.1, applied to {@link PortScreen}s
 * (StardewCraft screens) only; vanilla 1.20.1 screens are untouched.
 * <ul>
 *   <li>{@code render} begins with {@code renderBackground(graphics, mouseX, mouseY, partialTick)}. Container
 *       screens get it at the 1.21 position inside {@code AbstractContainerScreen#render}
 *       ({@link PortContainerScreenMixin}).</li>
 *   <li>{@code init(Minecraft, int, int)} and {@code rebuildWidgets} call {@code setInitialFocus()} right after
 *       {@code init()} (inside the uncancelled Init.Pre branch): with keyboard as last input, the first tab-focusable
 *       element is focused.</li>
 * </ul>
 */
@Mixin(Screen.class)
public abstract class PortScreenBackgroundMixin {
    @Shadow protected Minecraft minecraft;

    @Shadow protected abstract void changeFocus(ComponentPath path);

    @Inject(method = "render", at = @At("HEAD"))
    private void port$renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        Object self = this;
        if (self instanceof PortScreen screen && !(self instanceof AbstractContainerScreen<?>)) {
            screen.renderBackground(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Inject(method = {"init(Lnet/minecraft/client/Minecraft;II)V", "rebuildWidgets"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;init()V", shift = At.Shift.AFTER))
    private void port$setInitialFocus(CallbackInfo ci) {
        if ((Object) this instanceof PortScreen && this.minecraft.getLastInputType().isKeyboard()) {
            // 1.21: super.nextFocusPath(...) — no StardewCraft screen overrides nextFocusPath, so this is the same call.
            ComponentPath path = ((Screen) (Object) this).nextFocusPath(new FocusNavigationEvent.TabNavigation(true));
            if (path != null) this.changeFocus(path);
        }
    }
}

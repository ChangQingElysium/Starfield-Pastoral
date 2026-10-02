package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.stardew.craft.client.gui.common.GuiLayoutMath;
import org.spongepowered.asm.mixin.injection.At;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Unique;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.stardew.craft.client.gui.common.StardewGuiViewport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;

/**
 * MouseHandler converts window coordinates before both MinecraftForge events and screen callbacks.
 *
 * <p>PORT(1.20.1): press/scroll still read the mouse fields synchronously. Movement is accumulated and mapped once
 * per frame by {@code PortMouseHandlerScreenInputMixin}; do not wrap its callbacks again with wildcard selectors.
 */
@Mixin(MouseHandler.class)
public abstract class StardewGuiMouseMixin {
    @WrapMethod(method = "onPress")
    private void stardewcraft$press(long window, int button, int action, int modifiers, Operation<Void> original) {
        var previous = stardewcraft$enter();
        try { original.call(window, button, action, modifiers); }
        finally { StardewGuiViewport.restore(previous); }
    }

    @WrapMethod(method = "onScroll")
    private void stardewcraft$scroll(long window, double x, double y, Operation<Void> original) {
        var previous = stardewcraft$enter();
        try { original.call(window, x, y); }
        finally { StardewGuiViewport.restore(previous); }
    }

    @ModifyExpressionValue(method = {"onPress", "onScroll"},
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = "Lnet/minecraft/client/MouseHandler;xpos:D"))
    private double stardewcraft$mouseX(double x) {
        var layout = StardewGuiViewport.active();
        return layout == null ? x : layout.windowMouseX(x, Minecraft.getInstance().getWindow().getScreenWidth());
    }

    @ModifyExpressionValue(method = {"onPress", "onScroll"},
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = "Lnet/minecraft/client/MouseHandler;ypos:D"))
    private double stardewcraft$mouseY(double y) {
        var layout = StardewGuiViewport.active();
        return layout == null ? y : layout.windowMouseY(y, Minecraft.getInstance().getWindow().getScreenHeight());
    }

    @Unique
    private static GuiLayoutMath.Viewport stardewcraft$enter() {
        Minecraft minecraft = Minecraft.getInstance();
        return StardewGuiViewport.enterForScreen(minecraft.screen, minecraft.getWindow());
    }
}

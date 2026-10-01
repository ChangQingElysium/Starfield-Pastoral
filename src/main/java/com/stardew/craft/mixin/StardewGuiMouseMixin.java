package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.client.gui.common.GuiLayoutMath;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.ForgeHooksClient;
import org.spongepowered.asm.mixin.Shadow;
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
 * <p>PORT(1.20.1): 1.21 dispatches mouseMoved/mouseDragged from {@code handleAccumulatedMovement}, reading the
 * {@code xpos}/{@code ypos} fields and the accumulated delta. 1.20.1 dispatches them from {@code onMove} using the
 * new raw position (method parameters) and {@code new - xpos} as the delta, inside lambdas. The viewport is entered
 * around {@code onMove} and the screen-callback arguments are recomputed with the same formulas 1.21 uses:
 * {@code windowMouseX(raw) * guiScaledWidth / screenWidth} and {@code windowDeltaX(delta) * guiScaledWidth / screenWidth}.
 */
@Mixin(MouseHandler.class)
public abstract class StardewGuiMouseMixin {
    @Shadow private double xpos;
    @Shadow private double ypos;
    @Shadow private boolean ignoreFirstMove;
    // Static: the mouseMoved callback lives in a static lambda (m_263857_), and Mixin only injects a "*" selector into
    // static targets from a static handler. MouseHandler is the client singleton, so static state is equivalent.
    @Unique private static double stardewcraft$rawX;
    @Unique private static double stardewcraft$rawY;
    @Unique private static double stardewcraft$previousX;
    @Unique private static double stardewcraft$previousY;

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

    @WrapMethod(method = "onMove")
    private void stardewcraft$move(long window, double x, double y, Operation<Void> original) {
        var previous = stardewcraft$enter();
        stardewcraft$rawX = x;
        stardewcraft$rawY = y;
        // onMove first snaps xpos/ypos to the new position when ignoreFirstMove is set (zero drag delta).
        stardewcraft$previousX = this.ignoreFirstMove ? x : this.xpos;
        stardewcraft$previousY = this.ignoreFirstMove ? y : this.ypos;
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

    // ---- onMove screen callbacks (1.20.1 lambdas) ----

    @WrapOperation(method = "*", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;mouseMoved(DD)V"))
    private static void stardewcraft$moved(Screen screen, double mouseX, double mouseY, Operation<Void> original) {
        var layout = StardewGuiViewport.active();
        if (layout == null) { original.call(screen, mouseX, mouseY); return; }
        original.call(screen, stardewcraft$guiX(layout), stardewcraft$guiY(layout));
    }

    @WrapOperation(method = "*", at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/client/ForgeHooksClient;onScreenMouseDragPre(Lnet/minecraft/client/gui/screens/Screen;DDIDD)Z",
            remap = false))
    private boolean stardewcraft$dragPre(Screen screen, double mouseX, double mouseY, int button, double dragX, double dragY,
                                         Operation<Boolean> original) {
        var layout = StardewGuiViewport.active();
        if (layout == null) return original.call(screen, mouseX, mouseY, button, dragX, dragY);
        return original.call(screen, stardewcraft$guiX(layout), stardewcraft$guiY(layout), button,
                stardewcraft$dragX(layout), stardewcraft$dragY(layout));
    }

    @WrapOperation(method = "*", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;mouseDragged(DDIDD)Z"))
    private boolean stardewcraft$dragged(Screen screen, double mouseX, double mouseY, int button, double dragX, double dragY,
                                         Operation<Boolean> original) {
        var layout = StardewGuiViewport.active();
        if (layout == null) return original.call(screen, mouseX, mouseY, button, dragX, dragY);
        return original.call(screen, stardewcraft$guiX(layout), stardewcraft$guiY(layout), button,
                stardewcraft$dragX(layout), stardewcraft$dragY(layout));
    }

    @WrapOperation(method = "*", at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/client/ForgeHooksClient;onScreenMouseDragPost(Lnet/minecraft/client/gui/screens/Screen;DDIDD)V",
            remap = false))
    private void stardewcraft$dragPost(Screen screen, double mouseX, double mouseY, int button, double dragX, double dragY,
                                       Operation<Void> original) {
        var layout = StardewGuiViewport.active();
        if (layout == null) { original.call(screen, mouseX, mouseY, button, dragX, dragY); return; }
        original.call(screen, stardewcraft$guiX(layout), stardewcraft$guiY(layout), button,
                stardewcraft$dragX(layout), stardewcraft$dragY(layout));
    }

    @Unique
    private static double stardewcraft$guiX(GuiLayoutMath.Viewport layout) {
        var window = Minecraft.getInstance().getWindow();
        return layout.windowMouseX(stardewcraft$rawX, window.getScreenWidth())
                * (double) window.getGuiScaledWidth() / (double) window.getScreenWidth();
    }

    @Unique
    private static double stardewcraft$guiY(GuiLayoutMath.Viewport layout) {
        var window = Minecraft.getInstance().getWindow();
        return layout.windowMouseY(stardewcraft$rawY, window.getScreenHeight())
                * (double) window.getGuiScaledHeight() / (double) window.getScreenHeight();
    }

    @Unique
    private static double stardewcraft$dragX(GuiLayoutMath.Viewport layout) {
        var window = Minecraft.getInstance().getWindow();
        return layout.windowDeltaX(stardewcraft$rawX - stardewcraft$previousX)
                * (double) window.getGuiScaledWidth() / (double) window.getScreenWidth();
    }

    @Unique
    private static double stardewcraft$dragY(GuiLayoutMath.Viewport layout) {
        var window = Minecraft.getInstance().getWindow();
        return layout.windowDeltaY(stardewcraft$rawY - stardewcraft$previousY)
                * (double) window.getGuiScaledHeight() / (double) window.getScreenHeight();
    }

    @Unique
    private static GuiLayoutMath.Viewport stardewcraft$enter() {
        Minecraft minecraft = Minecraft.getInstance();
        return StardewGuiViewport.enterForScreen(minecraft.screen, minecraft.getWindow());
    }
}

package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.stardew.craft.client.gui.common.GuiLayoutMath;
import com.stardew.craft.client.gui.common.StardewGuiViewport;
import com.stardew.craft.port.PortMouse;
import com.stardew.craft.port.PortMouseFrame;
import com.stardew.craft.port.PortMouseMovement;
import com.stardew.craft.port.PortScreen;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.ForgeHooksClient;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PORT(1.20.1): 1.21.1 screen mouse input for StardewCraft screens ({@link PortScreen}); vanilla and other mods'
 * screens keep the 1.20.1 handling.
 * <ul>
 *   <li>Movement: 1.21.1 {@code onMove} only accumulates {@code new - xpos} (when not ignoring the first move and the
 *       window is active) and {@code handleAccumulatedMovement}, once per frame, sends one {@code mouseMoved} and one
 *       {@code mouseDragged} with the accumulated delta, then {@code afterMouseMove}. 1.20.1 calls them for every GLFW
 *       cursor event. For a {@code PortScreen} the per-event calls are skipped and {@link #port$handleAccumulatedMovement}
 *       replays the 1.21.1 screen part (called by {@code PortMinecraftMouseFrameMixin} where 1.20.1 calls
 *       {@code turnPlayer()} once per frame, after the client ticks and before rendering, as 1.21.1).</li>
 *   <li>Scroll: 1.21.1 hands screens the vertical value only as {@code scrollY}; 1.20.1 on macOS substitutes a
 *       horizontal offset for a zero vertical one. For a {@code PortScreen} the substitution is off, so the delta is
 *       1.21.1's {@code scrollY}; both 1.21.1 axes are published through {@link PortMouse} for the scroll listeners.</li>
 * </ul>
 */
@Mixin(MouseHandler.class)
public abstract class PortMouseHandlerScreenInputMixin implements PortMouseFrame {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private double xpos;
    @Shadow private double ypos;
    @Shadow private boolean ignoreFirstMove;
    @Shadow private int activeButton;
    @Shadow private double mousePressedTime;

    @Unique private final PortMouseMovement port$movement = new PortMouseMovement();

    @Inject(method = "onMove", at = @At("HEAD"))
    private void port$accumulate(long window, double x, double y, CallbackInfo ci) {
        // 1.21.1 MouseHandler#onMove: no accumulation for the first move after a reset, nor while the window is inactive.
        if (window == Minecraft.getInstance().getWindow().getWindow()) {
            this.port$movement.accumulate(this.xpos, this.ypos, x, y, this.ignoreFirstMove,
                    this.minecraft.isWindowActive());
        }
    }

    @WrapOperation(method = "onMove", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;wrapScreenError(Ljava/lang/Runnable;Ljava/lang/String;Ljava/lang/String;)V"))
    private void port$deferScreenCallback(Runnable action, String errorDesc, String screenName, Operation<Void> original) {
        if (!(this.minecraft.screen instanceof PortScreen)) original.call(action, errorDesc, screenName);
    }

    @WrapOperation(method = "onMove", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;afterMouseMove()V"))
    private void port$deferAfterMouseMove(Screen screen, Operation<Void> original) {
        if (!(screen instanceof PortScreen)) original.call(screen);
    }

    /** 1.21.1 {@code MouseHandler#handleAccumulatedMovement}, screen part, for StardewCraft screens. */
    @Override
    public void port$handleAccumulatedMovement() {
        if (this.minecraft.isWindowActive()) {
            Screen screen = this.minecraft.screen;
            if (screen instanceof PortScreen && this.minecraft.getOverlay() == null
                    && this.port$movement.hasDelta()) {
                Window window = this.minecraft.getWindow();
                // 1.21.1 StardewGuiMouseMixin wraps handleAccumulatedMovement in the screen's viewport and maps the
                // xpos/ypos reads and the accumulated deltas through it.
                GuiLayoutMath.Viewport previous = StardewGuiViewport.enterForScreen(screen, window);
                try {
                    GuiLayoutMath.Viewport layout = StardewGuiViewport.active();
                    var movement = this.port$movement.frame(layout, this.xpos, this.ypos, window.getScreenWidth(),
                            window.getScreenHeight(), window.getGuiScaledWidth(), window.getGuiScaledHeight());
                    double mouseX = movement.mouseX();
                    double mouseY = movement.mouseY();
                    Screen.wrapScreenError(() -> screen.mouseMoved(mouseX, mouseY), "mouseMoved event handler",
                            screen.getClass().getCanonicalName());
                    if (this.activeButton != -1 && this.mousePressedTime > 0.0) {
                        double dragX = movement.dragX();
                        double dragY = movement.dragY();
                        int button = this.activeButton;
                        Screen.wrapScreenError(() -> {
                            if (ForgeHooksClient.onScreenMouseDragPre(screen, mouseX, mouseY, button, dragX, dragY)) return;
                            if (screen.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return;
                            ForgeHooksClient.onScreenMouseDragPost(screen, mouseX, mouseY, button, dragX, dragY);
                        }, "mouseDragged event handler", screen.getClass().getCanonicalName());
                    }
                    screen.afterMouseMove();
                } finally {
                    StardewGuiViewport.restore(previous);
                }
            }
        }
        this.port$movement.reset();
    }

    @WrapMethod(method = "onScroll")
    private void port$publishScroll(long window, double xOffset, double yOffset, Operation<Void> original) {
        double previousX = PortMouse.scrollDeltaX();
        double previousY = PortMouse.scrollDeltaY();
        boolean discrete = this.minecraft.options.discreteMouseScroll().get();
        double sensitivity = this.minecraft.options.mouseWheelSensitivity().get();
        PortMouse.setScroll((discrete ? Math.signum(xOffset) : xOffset) * sensitivity,
                (discrete ? Math.signum(yOffset) : yOffset) * sensitivity);
        try {
            original.call(window, xOffset, yOffset);
        } finally {
            PortMouse.setScroll(previousX, previousY);
        }
    }

    @Redirect(method = "onScroll", at = @At(value = "FIELD", opcode = Opcodes.GETSTATIC,
            target = "Lnet/minecraft/client/Minecraft;ON_OSX:Z"))
    private boolean port$verticalOnlyForModScreens() {
        return Minecraft.ON_OSX && !(this.minecraft.screen instanceof PortScreen && this.minecraft.getOverlay() == null);
    }
}

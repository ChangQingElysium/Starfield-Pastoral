package com.stardew.craft.mixin;

import com.stardew.craft.client.gui.common.GuiScissorMath;
import com.stardew.craft.client.gui.common.StardewGuiViewport;
import com.stardew.craft.port.PortGuiGraphics;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PORT(1.20.1): 1.21.1 {@code GuiGraphics#containsPointInScissor} = {@code ScissorStack#containsPoint}, which tests
 * only {@code stack.peek()} — the FIRST (outermost) rectangle pushed, not the current intersection. The depth and
 * that bottom rectangle are mirrored here from the rectangle handed to {@code ScissorStack#push} (after the Stardew
 * framebuffer transform of {@code StardewGuiScissorMixin}).
 * The Stardew GUI contract part (formerly a {@code @WrapMethod} on containsPointInScissor in StardewGuiScissorMixin)
 * transforms the queried point with the current pose into framebuffer pixels while a Stardew viewport is active.
 */
@Mixin(GuiGraphics.class)
public abstract class PortGuiGraphicsScissorMixin implements PortGuiGraphics {
    @Unique private int port$scissorDepth;
    @Unique private ScreenRectangle port$scissorBottom;

    @ModifyArg(method = "enableScissor", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics$ScissorStack;push(Lnet/minecraft/client/gui/navigation/ScreenRectangle;)Lnet/minecraft/client/gui/navigation/ScreenRectangle;"))
    private ScreenRectangle port$trackPush(ScreenRectangle rectangle) {
        if (this.port$scissorDepth++ == 0) this.port$scissorBottom = rectangle;
        return rectangle;
    }

    @Inject(method = "disableScissor", at = @At("HEAD"))
    private void port$trackPop(CallbackInfo ci) {
        // Vanilla throws on underflow right after this; keep the mirror consistent with the real stack.
        if (this.port$scissorDepth > 0 && --this.port$scissorDepth == 0) this.port$scissorBottom = null;
    }

    @Override
    public boolean port$containsPointInScissor(int x, int y) {
        var layout = StardewGuiViewport.active();
        if (layout != null) {
            var pose = ((GuiGraphics) (Object) this).pose().last().pose();
            int px = GuiScissorMath.pointX(pose, layout.windowScale(), x, y);
            int py = GuiScissorMath.pointY(pose, layout.windowScale(), x, y);
            x = px;
            y = py;
        }
        if (this.port$scissorDepth == 0) return true;
        ScreenRectangle r = this.port$scissorBottom;
        return x >= r.left() && x < r.right() && y >= r.top() && y < r.bottom();
    }
}

package com.stardew.craft.mixin;

import com.stardew.craft.port.PortInheritance;
import java.util.Optional;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * PORT(1.20.1): 1.21.1 {@code ContainerEventHandler#mouseReleased}: on a left-button release while dragging, the
 * release goes to the focused (dragged) child even when the cursor has left it, and dragging is only cleared then;
 * 1.20.1 always clears dragging and only notifies the child under the cursor (a slider released outside its bounds
 * never gets {@code onRelease}). Every StardewCraft screen/container that does not override {@code mouseReleased}
 * inherits this default. Mixin 0.8.5 cannot inject into interface methods, so the default is overwritten: classes
 * from the mod's packages get the 1.21.1 body, every other implementation keeps the 1.20.1 body verbatim.
 */
@Mixin(ContainerEventHandler.class)
public interface PortContainerEventHandlerMixin {
    @Shadow
    boolean isDragging();

    @Shadow
    void setDragging(boolean dragging);

    @Shadow
    GuiEventListener getFocused();

    @Shadow
    Optional<GuiEventListener> getChildAt(double mouseX, double mouseY);

    /**
     * @author StardewCraft
     * @reason PORT(1.20.1): 1.21.1 body for the mod's classes, 1.20.1 body otherwise (see class comment).
     */
    @Overwrite
    default boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (PortInheritance.isModClass(this)) {
            if (button == 0 && this.isDragging()) {
                this.setDragging(false);
                if (this.getFocused() != null) {
                    return this.getFocused().mouseReleased(mouseX, mouseY, button);
                }
            }
        } else {
            this.setDragging(false);
        }
        return this.getChildAt(mouseX, mouseY).filter(child -> child.mouseReleased(mouseX, mouseY, button)).isPresent();
    }
}

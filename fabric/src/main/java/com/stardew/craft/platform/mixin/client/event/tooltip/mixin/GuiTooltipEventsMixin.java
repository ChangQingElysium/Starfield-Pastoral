package com.stardew.craft.platform.mixin.client.event.tooltip.mixin;


import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.stardew.craft.platform.fabric.client.event.tooltip.FabricTooltipEvents;
import com.stardew.craft.platform.fabric.client.event.tooltip.TooltipBackground;
import com.stardew.craft.platform.fabric.client.event.tooltip.TooltipScope;
import java.util.List;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(GuiGraphics.class)
public abstract class GuiTooltipEventsMixin implements TooltipScope.Access {
    @Shadow public abstract int guiWidth();
    @Shadow public abstract int guiHeight();
    @Shadow protected abstract void renderTooltipInternal(Font font, List<ClientTooltipComponent> components, int x, int y, ClientTooltipPositioner positioner);
    @Unique private final TooltipScope stardewcraft$tooltipScope = new TooltipScope();
    @Override @Unique public TooltipScope stardewcraft$tooltipScope() { return stardewcraft$tooltipScope; }

    @WrapMethod(method = "renderTooltip(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V")
    private void stardewcraft$itemTooltip(Font font, ItemStack stack, int x, int y, Operation<Void> original) {
        stardewcraft$tooltipScope.with(stack, () -> original.call(font, stack, x, y));
    }

    @WrapMethod(method = "renderTooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;II)V")
    private void stardewcraft$gather(Font font, List<Component> text, Optional<TooltipComponent> image, int x, int y, Operation<Void> original) {
        // Gather precedes any split or factory invocation, so canceling it suppresses custom factory side effects too.
        renderTooltipInternal(font, FabricTooltipEvents.gather(stardewcraft$tooltipScope.stack(), text, image, x, guiWidth(), guiHeight(), font), x, y, DefaultTooltipPositioner.INSTANCE);
    }

    @WrapMethod(method = "renderComponentTooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;II)V")
    private void stardewcraft$gatherText(Font font, List<Component> text, int x, int y, Operation<Void> original) {
        renderTooltipInternal(font, FabricTooltipEvents.gather(stardewcraft$tooltipScope.stack(), text, Optional.empty(), x, guiWidth(), guiHeight(), font), x, y, DefaultTooltipPositioner.INSTANCE);
    }

    @WrapMethod(method = "renderTooltipInternal")
    private void stardewcraft$beforeRender(Font font, List<ClientTooltipComponent> components, int x, int y,
                                         ClientTooltipPositioner positioner, Operation<Void> original) {
        FabricTooltipEvents.render(stardewcraft$tooltipScope.stack(), (GuiGraphics) (Object) this, x, y, font, components, positioner,
                (selectedFont, selectedX, selectedY, selectedPositioner) -> original.call(selectedFont, components, selectedX, selectedY, selectedPositioner));
    }

    @WrapOperation(method = "renderTooltipInternal", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawManaged(Ljava/lang/Runnable;)V"))
    private void stardewcraft$color(GuiGraphics graphics, Runnable vanillaBackground, Operation<Void> original,
                                   @Local(argsOnly = true) Font font, @Local(argsOnly = true) List<ClientTooltipComponent> components,
                                   @Local(index = 8) int width, @Local(index = 9) int height,
                                   @Local(index = 11) int x, @Local(index = 12) int y) {
        // After native measuring/positioning and pose.pushPose, before background flush and text/image passes.
        var color = FabricTooltipEvents.color(stardewcraft$tooltipScope.stack(), graphics, x, y, font, components);
        original.call(graphics, (Runnable) () -> TooltipBackground.draw(graphics, x, y, width, height, 400, color));
    }
}

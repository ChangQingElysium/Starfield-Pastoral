package com.stardew.craft.platform.mixin.client.event.screen.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.client.event.screen.FabricScreenEvents;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(MouseHandler.class)
public abstract class ScreenMouseEventsMixin {
    // Some of these call sites are static compiler-generated error-handler lambdas. Static wrappers
    // work in both those lambdas and ordinary methods and keep the caller's transformed coordinates.
    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;mouseClicked(DDI)Z"))
    private static boolean stardewcraft$press(Screen screen, double x, double y, int button, Operation<Boolean> original) {
        return FabricScreenEvents.click(FabricScreenEvents.bus(), screen, x, y, button, () -> original.call(screen, x, y, button));
    }
    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;mouseReleased(DDI)Z"))
    private static boolean stardewcraft$release(Screen screen, double x, double y, int button, Operation<Boolean> original) {
        return FabricScreenEvents.release(FabricScreenEvents.bus(), screen, x, y, button, () -> original.call(screen, x, y, button));
    }
    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;mouseDragged(DDIDD)Z"))
    private static boolean stardewcraft$drag(Screen screen, double x, double y, int button, double dx, double dy, Operation<Boolean> original) {
        return FabricScreenEvents.drag(FabricScreenEvents.bus(), screen, x, y, button, dx, dy, () -> original.call(screen, x, y, button, dx, dy));
    }
    @WrapOperation(method = "onScroll", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;mouseScrolled(DDDD)Z"))
    private static boolean stardewcraft$scroll(Screen screen, double x, double y, double dx, double dy, Operation<Boolean> original) {
        return FabricScreenEvents.scroll(FabricScreenEvents.bus(), screen, x, y, dx, dy, () -> original.call(screen, x, y, dx, dy));
    }
}

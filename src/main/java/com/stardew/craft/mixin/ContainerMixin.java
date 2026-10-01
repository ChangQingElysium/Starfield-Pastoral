package com.stardew.craft.mixin;

import com.stardew.craft.config.StackSizeHolder;
import net.minecraft.world.Container;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(Container.class)
public interface ContainerMixin {

    /**
     * @author StardewCraft
     * @reason PORT(1.20.1): 1.21.1 injects at HEAD of this default method and always cancels with
     * {@link StackSizeHolder#get()}. Mixin 0.8.5 (Forge 47) rejects injectors in interface mixins, so the default
     * body is overwritten instead: every implementation that inherits the default returns the same value, and
     * implementations that override it keep their own value, exactly as in 1.21.1.
     */
    @Overwrite
    default int getMaxStackSize() {
        return StackSizeHolder.get();
    }
}

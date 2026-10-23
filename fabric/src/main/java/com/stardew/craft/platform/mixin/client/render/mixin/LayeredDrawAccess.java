package com.stardew.craft.platform.mixin.client.render.mixin;


import java.util.List;
import net.minecraft.client.gui.LayeredDraw;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LayeredDraw.class)
public interface LayeredDrawAccess {
    @Accessor("layers") List<LayeredDraw.Layer> stardewcraft$layers();
}

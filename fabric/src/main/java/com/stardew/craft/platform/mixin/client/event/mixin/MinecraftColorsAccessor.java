package com.stardew.craft.platform.mixin.client.event.mixin;


import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.item.ItemColors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(Minecraft.class)
public interface MinecraftColorsAccessor {
    @Accessor("itemColors")
    ItemColors stardewcraft$getItemColors();
}

// Upstream-SHA256: 004580ccdc419f1cc167af375748eb03fbafca60eabdd5005ccc49cf313412af
package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.stardew.craft.block.terrain.TerrainVariants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Runs after the contextual clone-stack wrapper, before native empty-result and Ctrl-NBT handling. */
@Mixin(Minecraft.class)
public abstract class MinecraftTerrainPickMixin {
    @ModifyVariable(method = "pickBlock", at = @At(value = "STORE", ordinal = 0), index = 3)
    private ItemStack stardewcraft$copyTerrainVariant(ItemStack stack, @Local(index = 6) BlockState state) {
        var player = ((Minecraft) (Object) this).player;
        return player.getAbilities().instabuild && Screen.hasControlDown()
                ? com.stardew.craft.block.decor.NaturalPlantBlock.fixedCopy(TerrainVariants.fixedCopy(stack, state), state) : stack;
    }
}

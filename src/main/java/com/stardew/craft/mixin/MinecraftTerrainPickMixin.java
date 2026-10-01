package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.block.terrain.TerrainVariants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Extend creative Ctrl-pick to terrain without adding block entities or copying neighbors. */
@Mixin(Minecraft.class)
public abstract class MinecraftTerrainPickMixin {
    // PORT(1.20.1): Forge's IForgeBlockState#getCloneItemStack takes a BlockGetter (NeoForge: LevelReader); not obfuscated.
    @WrapOperation(method = "pickBlock", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getCloneItemStack(Lnet/minecraft/world/phys/HitResult;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/item/ItemStack;",
            remap = false))
    private ItemStack stardewcraft$copyTerrainVariant(BlockState state, HitResult target, BlockGetter level,
            BlockPos pos, Player player, Operation<ItemStack> original) {
        ItemStack stack = original.call(state, target, level, pos, player);
        return player.getAbilities().instabuild && Screen.hasControlDown()
                ? com.stardew.craft.block.decor.NaturalPlantBlock.fixedCopy(TerrainVariants.fixedCopy(stack, state), state) : stack;
    }
}

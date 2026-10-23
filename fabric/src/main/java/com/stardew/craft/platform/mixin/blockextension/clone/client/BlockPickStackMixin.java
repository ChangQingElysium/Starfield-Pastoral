package com.stardew.craft.platform.mixin.blockextension.clone.client;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.blockextension.clone.CloneStacks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Keeps native empty-result, Ctrl-NBT, creative inventory and survival hotbar handling. */
@Mixin(Minecraft.class)
abstract class BlockPickStackMixin {
    @Shadow public HitResult hitResult;
    @Shadow public LocalPlayer player;
    @WrapOperation(method = "pickBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getCloneItemStack(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/item/ItemStack;"), require = 1)
    private ItemStack stardew$contextualPick(Block block, LevelReader level, BlockPos pos, BlockState state, Operation<ItemStack> original) {
        return CloneStacks.pick(state, hitResult, level, pos, player, () -> original.call(block, level, pos, state));
    }
}

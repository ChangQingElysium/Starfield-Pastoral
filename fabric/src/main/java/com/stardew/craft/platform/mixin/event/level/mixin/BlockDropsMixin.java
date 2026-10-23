package com.stardew.craft.platform.mixin.event.level.mixin;


import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.event.level.BlockDropHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
public abstract class BlockDropsMixin {
    @Inject(method = "popResource(Lnet/minecraft/world/level/Level;Ljava/util/function/Supplier;Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"), cancellable = true)
    private static void stardewcraft$skipRestorationDrops(Level level, java.util.function.Supplier<ItemEntity> supplier,
            ItemStack stack, CallbackInfo callback) {
        if (com.stardew.craft.platform.fabric.event.level.PlacementTransactions.isRestoring(level)) callback.cancel();
    }
    @Inject(method = "popExperience", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$skipRestorationExperience(ServerLevel level, BlockPos pos, int experience, CallbackInfo callback) {
        if (com.stardew.craft.platform.fabric.event.level.PlacementTransactions.isRestoring(level)) callback.cancel();
    }
    @WrapMethod(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V")
    private static void stardewcraft$simpleDrops(BlockState state, Level level, BlockPos pos, Operation<Void> original) {
        BlockDropHooks.run(state, level, pos, null, null, ItemStack.EMPTY, () -> original.call(state, level, pos));
    }
    @WrapMethod(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;)V")
    private static void stardewcraft$entityDrops(BlockState state, LevelAccessor level, BlockPos pos, BlockEntity blockEntity, Operation<Void> original) {
        if (!(level instanceof Level nativeLevel)) { original.call(state, level, pos, blockEntity); return; }
        BlockDropHooks.run(state, nativeLevel, pos, blockEntity, null, ItemStack.EMPTY, () -> original.call(state, level, pos, blockEntity));
    }
    @WrapMethod(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V")
    private static void stardewcraft$toolDrops(BlockState state, Level level, BlockPos pos, BlockEntity blockEntity, Entity breaker,
            ItemStack tool, Operation<Void> original) {
        BlockDropHooks.run(state, level, pos, blockEntity, breaker, tool, () -> original.call(state, level, pos, blockEntity, breaker, tool));
    }
    @WrapOperation(method = "dropResources", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;spawnAfterBreak(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;Z)V"))
    private static void stardewcraft$deferAfterBreak(BlockState state, ServerLevel level, BlockPos pos, ItemStack tool,
            boolean experience, Operation<Void> original) {
        if (!BlockDropHooks.isCapturing(level)) original.call(state, level, pos, tool, experience);
    }
    @WrapOperation(method = "popResource(Lnet/minecraft/world/level/Level;Ljava/util/function/Supplier;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private static boolean stardewcraft$capturePoppedItem(Level level, Entity entity, Operation<Boolean> original) {
        return entity instanceof ItemEntity item && BlockDropHooks.capture(level, item) || original.call(level, entity);
    }
}

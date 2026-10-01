package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.port.event.PortEventHooks;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
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

/**
 * PORT(1.20.1): MinecraftForge 21.1 routes every {@code Block.dropResources} overload through {@code BlockDropsEvent}
 * (captured {@code ItemEntity} drops, cancellable, owns the dropped XP). See {@link PortEventHooks#dropResources}.
 */
@Mixin(Block.class)
public abstract class PortBlockDropsMixin {
    @Inject(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V",
            at = @At("HEAD"), cancellable = true)
    private static void stardewcraft$dropsEventSimple(BlockState state, Level level, BlockPos pos, CallbackInfo ci) {
        if (level instanceof ServerLevel server) {
            PortEventHooks.dropResources(state, server, pos, null, null, ItemStack.EMPTY, true,
                    () -> Block.getDrops(state, server, pos, null));
            ci.cancel();
        }
    }

    @Inject(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;)V",
            at = @At("HEAD"), cancellable = true)
    private static void stardewcraft$dropsEventWithBlockEntity(BlockState state, LevelAccessor level, BlockPos pos,
            @Nullable BlockEntity blockEntity, CallbackInfo ci) {
        if (level instanceof ServerLevel server) {
            PortEventHooks.dropResources(state, server, pos, blockEntity, null, ItemStack.EMPTY, true,
                    () -> Block.getDrops(state, server, pos, blockEntity));
            ci.cancel();
        }
    }

    @Inject(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;Z)V",
            at = @At("HEAD"), cancellable = true)
    private static void stardewcraft$dropsEventWithBreaker(BlockState state, Level level, BlockPos pos, @Nullable BlockEntity blockEntity,
            @Nullable Entity breaker, ItemStack tool, boolean dropXp, CallbackInfo ci) {
        if (level instanceof ServerLevel server) {
            PortEventHooks.dropResources(state, server, pos, blockEntity, breaker, tool, dropXp,
                    () -> Block.getDrops(state, server, pos, blockEntity, breaker, tool));
            ci.cancel();
        }
    }

    /** Capture point equivalent to MinecraftForge's {@code Block.beginCapturingDrops}. */
    @WrapOperation(method = "popResource(Lnet/minecraft/world/level/Level;Ljava/util/function/Supplier;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private static boolean stardewcraft$captureDrop(Level level, Entity entity, Operation<Boolean> original) {
        return PortEventHooks.captureDrop(level, entity);
    }
}

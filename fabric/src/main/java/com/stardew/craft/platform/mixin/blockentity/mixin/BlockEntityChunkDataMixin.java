package com.stardew.craft.platform.mixin.blockentity.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.blockentity.BlockEntityDataProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelChunk.class)
abstract class BlockEntityChunkDataMixin {
    @Dynamic("Native 1.21.1 replaceWithPacketData consumer; bytecode contract checked")
    @WrapOperation(method = "method_31716", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/BlockEntity;loadWithComponents(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V"))
    private void stardewcraft$updateTag(BlockEntity entity, CompoundTag tag, HolderLookup.Provider registries,
            Operation<Void> original) {
        if (entity instanceof BlockEntityDataProvider provider) provider.handleUpdateTag(tag, registries);
        else original.call(entity, tag, registries);
    }
}

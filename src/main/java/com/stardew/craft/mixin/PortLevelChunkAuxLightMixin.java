package com.stardew.craft.mixin;

import com.stardew.craft.port.PortAuxLight;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PORT(1.20.1): NeoForge 1.21.1 patches {@code LevelChunk} so that whenever a block entity is retired
 * ({@code removeBlockEntity}, or replaced by a different one in {@code setBlockEntity}) the chunk's auxiliary light
 * value at that position is removed right after {@code BlockEntity#setRemoved()} ({@code auxLightManager.removeLightAt}).
 * The port keeps auxiliary light in {@link PortAuxLight}; this restores the same cleanup point.
 */
@Mixin(LevelChunk.class)
public abstract class PortLevelChunkAuxLightMixin {
    @Inject(method = "removeBlockEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/BlockEntity;setRemoved()V", shift = At.Shift.AFTER))
    private void stardewcraft$removeAuxLightOnRemove(BlockPos pos, CallbackInfo ci) {
        stardewcraft$removeAuxLight(pos);
    }

    @Inject(method = "setBlockEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/BlockEntity;setRemoved()V", shift = At.Shift.AFTER))
    private void stardewcraft$removeAuxLightOnReplace(BlockEntity blockEntity, CallbackInfo ci) {
        stardewcraft$removeAuxLight(blockEntity.getBlockPos());
    }

    private void stardewcraft$removeAuxLight(BlockPos pos) {
        var lights = PortAuxLight.getAuxLightManager((LevelChunk) (Object) this, pos);
        if (lights != null) lights.removeLightAt(pos);
    }
}

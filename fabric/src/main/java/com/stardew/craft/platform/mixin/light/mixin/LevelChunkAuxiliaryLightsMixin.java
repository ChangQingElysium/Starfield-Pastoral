package com.stardew.craft.platform.mixin.light.mixin;


import com.stardew.craft.platform.fabric.light.AuxiliaryLightManager;
import com.stardew.craft.platform.fabric.light.AuxiliaryLightView;
import com.stardew.craft.platform.fabric.light.AuxiliaryLights;
import com.stardew.craft.platform.fabric.light.ChunkAuxiliaryLightManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelChunk.class)
abstract class LevelChunkAuxiliaryLightsMixin implements AuxiliaryLightView {
    @Unique private final ChunkAuxiliaryLightManager stardewcraft$lights = new ChunkAuxiliaryLightManager(
            ((LevelChunk) (Object) this).getPos(), change -> AuxiliaryLights.changed((LevelChunk) (Object) this, change));

    @Override public AuxiliaryLightManager stardewcraft$auxiliaryLights(BlockPos pos) {
        var chunk = ((LevelChunk) (Object) this).getPos();
        return pos.getX() >> 4 == chunk.x && pos.getZ() >> 4 == chunk.z ? stardewcraft$lights : null;
    }

    @Inject(method = "removeBlockEntity", at = @At("HEAD"), require = 1)
    private void stardewcraft$removeCachedSource(BlockPos pos, CallbackInfo ci) {
        stardewcraft$lights.removeLightAt(pos);
    }

    @Inject(method = "setBlockEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/BlockEntity;setRemoved()V", shift = At.Shift.AFTER), require = 1)
    private void stardewcraft$clearReplacedSource(BlockEntity replacement, CallbackInfo ci) {
        stardewcraft$lights.removeLightAt(replacement.getBlockPos());
    }
}

package com.stardew.craft.platform.mixin.blockextension.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.stardew.craft.platform.fabric.blockextension.BlockBehaviors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Sniffer.class)
abstract class SnifferSoundMixin {
    @WrapOperation(method = "emitDiggingParticles", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"), require = 1)
    private SoundType stardewcraft$diggingMaterial(BlockState queried, Operation<SoundType> original, @Local BlockPos head) {
        var sniffer = (Sniffer) (Object) this;
        return BlockBehaviors.resolveSound(queried, sniffer.level(), head.below(), sniffer, original.call(queried));
    }
}

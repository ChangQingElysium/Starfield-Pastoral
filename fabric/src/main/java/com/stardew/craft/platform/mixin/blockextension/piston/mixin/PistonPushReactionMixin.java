package com.stardew.craft.platform.mixin.blockextension.piston.mixin;


import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.stardew.craft.platform.fabric.blockextension.piston.PistonPushHooks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** All native piston resolution and sticky-retraction paths consume this getter. */
@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class PistonPushReactionMixin {
    @ModifyReturnValue(method = "getPistonPushReaction", at = @At("RETURN"), require = 1)
    private PushReaction stardew$contextualReaction(PushReaction declared) {
        return PistonPushHooks.reaction((BlockState) (Object) this, declared);
    }
}

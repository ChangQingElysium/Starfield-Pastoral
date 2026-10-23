package com.stardew.craft.platform.mixin.blockextension.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.stardew.craft.platform.fabric.blockextension.BlockBehaviorProvider;
import com.stardew.craft.platform.fabric.blockextension.BlockBehaviors;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockItem.class)
abstract class BlockItemSoundMixin {
    @WrapOperation(method = "place", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"), require = 1)
    private SoundType stardewcraft$placeVolumeAndPitch(BlockState queried, Operation<SoundType> original, @Local(ordinal = 1) BlockPlaceContext context) {
        return BlockBehaviors.resolveSound(queried, context.getLevel(), context.getClickedPos(), context.getPlayer(), original.call(queried));
    }

    @WrapOperation(method = "place", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/BlockItem;getPlaceSound(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/sounds/SoundEvent;"), require = 1)
    private SoundEvent stardewcraft$placeSound(BlockItem item, BlockState queried, Operation<SoundEvent> original, @Local(ordinal = 1) BlockPlaceContext context) {
        return queried.getBlock() instanceof BlockBehaviorProvider
                ? BlockBehaviors.getSoundType(queried, context.getLevel(), context.getClickedPos(), context.getPlayer()).getPlaceSound()
                : original.call(item, queried);
    }
}

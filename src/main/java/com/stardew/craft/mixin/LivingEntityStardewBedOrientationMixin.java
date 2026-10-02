package com.stardew.craft.mixin;

import com.stardew.craft.block.decor.BedDecorBlock;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(LivingEntity.class)
public abstract class LivingEntityStardewBedOrientationMixin {

    @Shadow public abstract Optional<BlockPos> getSleepingPos();

    @Inject(method = "getBedOrientation", at = @At("HEAD"), cancellable = true)
    private void stardew$getDecorBedOrientation(CallbackInfoReturnable<Direction> cir) {
        Optional<BlockPos> sleepingPos = getSleepingPos();
        if (sleepingPos.isEmpty()) {
            return;
        }

        LivingEntity self = (LivingEntity) (Object) this;
        BlockState state = self.level().getBlockState(sleepingPos.get());
        if ((state.getBlock() instanceof com.stardew.craft.block.decor.BedDecorBlock)
                && state.hasProperty(MapDecorStaticBlock.FACING)) {
            cir.setReturnValue(state.getValue(MapDecorStaticBlock.FACING).getOpposite());
        }
    }

    @Inject(method = "startSleeping", at = @At("TAIL"))
    private void stardew$alignSleeperWithDecorBed(BlockPos pos, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        BlockState state = self.level().getBlockState(pos);
        if (state.getBlock() instanceof com.stardew.craft.block.decor.BedDecorBlock) {
            self.setPos(
                pos.getX() + 0.5D,
                pos.getY() + ((BedDecorBlock) state.getBlock()).sleepYOffset(),
                pos.getZ() + 0.5D
            );
        }
    }
}

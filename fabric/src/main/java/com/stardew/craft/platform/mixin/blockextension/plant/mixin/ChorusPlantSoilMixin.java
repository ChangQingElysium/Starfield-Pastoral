package com.stardew.craft.platform.mixin.blockextension.plant.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.blockextension.plant.PlantHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.ChorusPlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChorusPlantBlock.class)
abstract class ChorusPlantSoilMixin {
    @Inject(method = "canSurvive", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/world/level/block/Block;)Z", ordinal = 3), cancellable = true, require = 1)
    private void stardewcraft$soilAfterNativeNeighborRules(BlockState plant, LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> result) {
        var decision = PlantHooks.upwardDecision(plant, level, pos);
        if (!decision.isDefault()) result.setReturnValue(decision.isTrue());
    }
    @WrapOperation(method = "getStateWithConnections", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;trySetValue(Lnet/minecraft/world/level/block/state/properties/Property;Ljava/lang/Comparable;)Ljava/lang/Object;", ordinal = 0), require = 1)
    private static Object stardewcraft$initialDownConnection(BlockState state, Property<?> property, Comparable<?> value, Operation<Object> original,
            BlockGetter level, BlockPos pos, BlockState plant) {
        boolean connected = Boolean.TRUE.equals(value) || PlantHooks.upwardDecision(plant, level, pos).isTrue();
        return original.call(state, property, connected);
    }
    @WrapOperation(method = "updateShape", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;setValue(Lnet/minecraft/world/level/block/state/properties/Property;Ljava/lang/Comparable;)Ljava/lang/Object;"), require = 1)
    private Object stardewcraft$updatedDownConnection(BlockState state, Property<?> property, Comparable<?> value, Operation<Object> original,
            BlockState plant, Direction facing, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (facing == Direction.DOWN) {
            // Preserve Neo's explicit facing-position argument, including its additional relative offset.
            var decision = PlantHooks.canSustainPlant(neighbor, level, neighborPos.relative(facing), facing.getOpposite(), plant);
            if (!decision.isDefault()) value = decision.isTrue();
        }
        return original.call(state, property, value);
    }
}

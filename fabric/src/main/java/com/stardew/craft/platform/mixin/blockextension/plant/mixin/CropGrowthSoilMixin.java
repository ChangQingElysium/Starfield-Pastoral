package com.stardew.craft.platform.mixin.blockextension.plant.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.stardew.craft.platform.fabric.blockextension.plant.PlantHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CropBlock.class)
abstract class CropGrowthSoilMixin {
    @WrapOperation(method = "getGrowthSpeed", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/world/level/block/Block;)Z", ordinal = 0), require = 1)
    private static boolean stardewcraft$sustainingSoil(BlockState soil, Block queried, Operation<Boolean> original,
            Block crop, BlockGetter level, BlockPos plantPos, @Local(ordinal = 0) int x, @Local(ordinal = 1) int z) {
        // This native method receives a Block rather than BlockState. The project's sole support provider ignores crop age/state.
        var decision = PlantHooks.canSustainPlant(soil, level, plantPos.below().offset(x, 0, z), Direction.UP, crop.defaultBlockState());
        return decision.isDefault() ? soil.getBlock() instanceof FarmBlock : decision.isTrue();
    }
    @WrapOperation(method = "getGrowthSpeed", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getValue(Lnet/minecraft/world/level/block/state/properties/Property;)Ljava/lang/Comparable;"), require = 1)
    private static Comparable<?> stardewcraft$fertility(BlockState soil, Property<?> property, Operation<Comparable<?>> original,
            Block crop, BlockGetter level, BlockPos plantPos, @Local(ordinal = 0) int x, @Local(ordinal = 1) int z) {
        // Neo passes the crop plane, rather than soil Y, to isFertile. Preserve that contextual API argument.
        return PlantHooks.isFertile(soil, level, plantPos.offset(x, 0, z)) ? 1 : 0;
    }
}

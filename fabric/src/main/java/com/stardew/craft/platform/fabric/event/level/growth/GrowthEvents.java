package com.stardew.craft.platform.fabric.event.level.growth;

import com.stardew.craft.platform.fabric.event.FabricEvents;
import com.stardew.craft.platform.fabric.event.IEventBus;
import com.stardew.craft.platform.fabric.event.entity.player.BonemealEvent;
import com.stardew.craft.platform.fabric.event.level.BlockEvent;
import com.stardew.craft.platform.fabric.event.level.BlockGrowFeatureEvent;
import com.stardew.craft.platform.fabric.event.level.PistonEvent;
import com.stardew.craft.platform.fabric.event.level.block.CropGrowEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import org.jetbrains.annotations.Nullable;

/** Explicit native call sites own preconditions and draws; these decisions do not draw randomness. */
public final class GrowthEvents {
    private GrowthEvents() {}
    public static boolean canGrow(Level level, BlockPos pos, BlockState state, boolean nativeResult) {
        return canGrow(FabricEvents.GAME, level, pos, state, nativeResult);
    }
    public static boolean canGrow(IEventBus bus, Level level, BlockPos pos, BlockState state, boolean nativeResult) {
        var result = bus.post(new CropGrowEvent.Pre(level, pos, state)).getResult();
        return result == CropGrowEvent.Pre.Result.GROW || result == CropGrowEvent.Pre.Result.DEFAULT && nativeResult;
    }
    public static BlockGrowFeatureEvent feature(LevelAccessor level, RandomSource random, BlockPos pos, @Nullable Holder<ConfiguredFeature<?, ?>> feature) {
        return FabricEvents.GAME.post(new BlockGrowFeatureEvent(level, random, pos, feature));
    }
    public static BonemealEvent bonemeal(@Nullable Player player, ItemStack stack, Level level, BlockPos pos) {
        return FabricEvents.GAME.post(new BonemealEvent(player, level, pos, level.getBlockState(pos), stack));
    }
    public static boolean trample(Level level, BlockPos pos, float distance, Entity entity) {
        return !FabricEvents.GAME.post(new BlockEvent.FarmlandTrampleEvent(level, pos, Blocks.DIRT.defaultBlockState(), distance, entity)).isCanceled();
    }
    public static BlockState fluidState(LevelAccessor level, BlockPos pos, BlockPos liquidPos, BlockState state) {
        return fluidState(FabricEvents.GAME, level, pos, liquidPos, state);
    }
    public static BlockState fluidState(IEventBus bus, LevelAccessor level, BlockPos pos, BlockPos liquidPos, BlockState state) {
        // Intentional source parity: Neo consumes the mutable state, not the cancel flag.
        return bus.post(new BlockEvent.FluidPlaceBlockEvent(level, pos, liquidPos, state)).getNewState();
    }
    public static boolean piston(Level level, BlockPos pos, Direction facing, boolean extending) {
        return FabricEvents.GAME.post(new PistonEvent.Pre(level, pos, facing,
                extending ? PistonEvent.PistonMoveType.EXTEND : PistonEvent.PistonMoveType.RETRACT)).isCanceled();
    }
}

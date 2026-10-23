package com.stardew.craft.platform.fabric.fluid;

import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

/** The pond's own fluid family. Water physics come from the shipped WATER tag. */
public abstract class FishPondWaterFluid extends FlowingFluid {
    private final Supplier<? extends FlowingFluid> source;
    private final Supplier<? extends FlowingFluid> flowing;
    private final Supplier<? extends LiquidBlock> block;

    protected FishPondWaterFluid(Supplier<? extends FlowingFluid> source,
            Supplier<? extends FlowingFluid> flowing, Supplier<? extends LiquidBlock> block) {
        this.source = source;
        this.flowing = flowing;
        this.block = block;
    }

    @Override public FlowingFluid getSource() { return source.get(); }
    @Override public FlowingFluid getFlowing() { return flowing.get(); }

    @Override
    public boolean isSame(Fluid other) {
        return other == source.get() || other == flowing.get();
    }

    @Override
    protected boolean canConvertToSource(Level level) {
        // The upstream pond FluidType explicitly enables source conversion,
        // independently of the vanilla waterSourceConversion gamerule.
        return true;
    }

    @Override
    protected void beforeDestroyingBlock(LevelAccessor level, BlockPos position, BlockState state) {
        BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(position) : null;
        Block.dropResources(state, level, position, blockEntity);
    }

    @Override protected int getSlopeFindDistance(LevelReader level) { return 4; }
    @Override protected int getDropOff(LevelReader level) { return 1; }
    @Override public int getTickDelay(LevelReader level) { return 5; }
    @Override protected float getExplosionResistance() { return 100.0F; }

    @Override
    public Item getBucket() {
        // FishPondWaterBlock also refuses pickup: pond water has no bucket item.
        return Items.AIR;
    }

    @Override
    public Optional<SoundEvent> getPickupSound() {
        return Optional.of(SoundEvents.BUCKET_FILL);
    }

    @Override
    protected boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos position,
            Fluid incoming, Direction direction) {
        return direction == Direction.DOWN && !isSame(incoming);
    }

    @Override
    protected BlockState createLegacyBlock(FluidState state) {
        return block.get().defaultBlockState().setValue(LiquidBlock.LEVEL, getLegacyLevel(state));
    }

    public static final class Source extends FishPondWaterFluid {
        public Source(Supplier<? extends FlowingFluid> source, Supplier<? extends FlowingFluid> flowing,
                Supplier<? extends LiquidBlock> block) {
            super(source, flowing, block);
        }

        @Override public int getAmount(FluidState state) { return 8; }
        @Override public boolean isSource(FluidState state) { return true; }
    }

    public static final class Flowing extends FishPondWaterFluid {
        public Flowing(Supplier<? extends FlowingFluid> source, Supplier<? extends FlowingFluid> flowing,
                Supplier<? extends LiquidBlock> block) {
            super(source, flowing, block);
            registerDefaultState(getStateDefinition().any().setValue(LEVEL, 7));
        }

        @Override
        protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
            builder.add(LEVEL);
        }

        @Override public int getAmount(FluidState state) { return state.getValue(LEVEL); }
        @Override public boolean isSource(FluidState state) { return false; }
    }
}

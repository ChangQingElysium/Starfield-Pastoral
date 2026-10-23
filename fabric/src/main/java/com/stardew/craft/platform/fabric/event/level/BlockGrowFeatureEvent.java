package com.stardew.craft.platform.fabric.event.level;

import com.stardew.craft.platform.fabric.event.LevelEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import org.jetbrains.annotations.Nullable;

/** Selected feature before removing the plant or placing any feature blocks. */
public final class BlockGrowFeatureEvent extends LevelEvent {
    private final RandomSource random;
    private final BlockPos pos;
    @Nullable private Holder<ConfiguredFeature<?, ?>> feature;
    public BlockGrowFeatureEvent(LevelAccessor level, RandomSource random, BlockPos pos, @Nullable Holder<ConfiguredFeature<?, ?>> feature) {
        super(level, true);
        this.random = random; this.pos = pos.immutable(); this.feature = feature;
    }
    public RandomSource getRandom() { return random; }
    public BlockPos getPos() { return pos; }
    @Nullable public Holder<ConfiguredFeature<?, ?>> getFeature() { return feature; }
    public void setFeature(@Nullable Holder<ConfiguredFeature<?, ?>> feature) { this.feature = feature; }
}

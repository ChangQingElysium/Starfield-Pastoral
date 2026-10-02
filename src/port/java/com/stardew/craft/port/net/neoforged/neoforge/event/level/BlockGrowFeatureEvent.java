package com.stardew.craft.port.net.neoforged.neoforge.event.level;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.Cancelable;

/**
 * PORT(1.20.1): NeoForge 21.1 {@code BlockGrowFeatureEvent}, bridged from Forge {@code SaplingGrowTreeEvent}
 * (cancel -> DENY result; the feature is copied back).
 */
@Cancelable
public class BlockGrowFeatureEvent extends LevelEvent {
    private final RandomSource rand;
    private final BlockPos pos;
    @Nullable
    private Holder<ConfiguredFeature<?, ?>> feature;

    public BlockGrowFeatureEvent() {
        this(null, null, BlockPos.ZERO, null);
    }

    public BlockGrowFeatureEvent(LevelAccessor level, RandomSource rand, BlockPos pos, @Nullable Holder<ConfiguredFeature<?, ?>> feature) {
        super(level);
        this.rand = rand;
        this.pos = pos;
        this.feature = feature;
    }

    public RandomSource getRandom() {
        return this.rand;
    }

    public BlockPos getPos() {
        return this.pos;
    }

    @Nullable
    public Holder<ConfiguredFeature<?, ?>> getFeature() {
        return this.feature;
    }

    public void setFeature(@Nullable Holder<ConfiguredFeature<?, ?>> feature) {
        this.feature = feature;
    }
}

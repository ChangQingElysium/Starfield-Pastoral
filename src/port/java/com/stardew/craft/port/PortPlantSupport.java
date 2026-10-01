package com.stardew.craft.port;

import com.stardew.craft.port.net.neoforged.neoforge.common.util.TriState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.IPlantable;

/**
 * PORT(1.20.1): NeoForge 1.21.1 plant-support model on Forge 1.20.1.
 *
 * <p>1.21.1: every vanilla plant asks the soil {@code IBlockExtension#canSustainPlant(state, level, soilPos, facing,
 * plant BlockState) -> TriState}; a non-DEFAULT answer decides survival outright (no light, water or tag checks), DEFAULT
 * falls through to the plant's own vanilla rule ({@code mayPlaceOn}, tags, ...). Vanilla soil rules accept any
 * {@link FarmBlock} subclass ({@code instanceof FarmBlock}) and {@code isFertile} is true for any moist FarmBlock.
 *
 * <p>1.20.1 Forge: soils answer a boolean {@code canSustainPlant(..., IPlantable)} whose default adds Forge plant-type
 * rules on top of {@code mayPlaceOn}, only a few plants ask it, and vanilla rules/isFertile only accept
 * {@code Blocks.FARMLAND}. The {@code Port*PlantSupportMixin}s restore the 1.21.1 rules; mod soils that override the
 * 1.21.1 TriState method implement {@link Soil} with the unchanged 1.21.1 body.
 *
 * <p>Compatibility: a third-party 1.20.1 soil that overrides Forge's boolean method ({@link #legacyForgeSoil}) keeps the
 * Forge answer at the call sites where 1.20.1 asked it; no StardewCraft or vanilla block is such a soil.
 */
public final class PortPlantSupport {
    private PortPlantSupport() {}

    /** The 1.21.1 {@code IBlockExtension#canSustainPlant} override of a mod soil block. */
    public interface Soil {
        TriState canSustainPlant(BlockState state, BlockGetter level, BlockPos soilPosition, Direction facing, BlockState plant);
    }

    /** 1.21.1 {@code soil.canSustainPlant(level, soilPos, facing, plant)}: DEFAULT for every block without an override. */
    public static TriState decision(BlockState soil, BlockGetter level, BlockPos soilPos, Direction facing, BlockState plant) {
        return soil.getBlock() instanceof Soil s ? s.canSustainPlant(soil, level, soilPos, facing, plant) : TriState.DEFAULT;
    }

    /** 1.21.1 vanilla soil rule for farmland plants. */
    public static boolean farmland(BlockState soil) {
        return soil.getBlock() instanceof FarmBlock;
    }

    /** 1.21.1 {@code IBlockExtension#isFertile} default. */
    public static boolean fertile(BlockState state) {
        return state.getBlock() instanceof FarmBlock && state.getValue(FarmBlock.MOISTURE) > 0;
    }

    /** 1.21.1 {@code CropBlock#hasSufficientLight} (1.20.1 additionally accepted {@code canSeeSky}). */
    public static boolean sufficientCropLight(net.minecraft.world.level.LevelReader level, BlockPos pos) {
        return level.getRawBrightness(pos, 0) >= 8;
    }

    private static final ClassValue<Boolean> LEGACY = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            if (Soil.class.isAssignableFrom(type)) return false;
            try {
                return type.getMethod("canSustainPlant", BlockState.class, BlockGetter.class, BlockPos.class,
                        Direction.class, IPlantable.class).getDeclaringClass() != Block.class;
            } catch (NoSuchMethodException e) {
                return false;
            }
        }
    };

    /** A third-party Forge soil overriding the 1.20.1 boolean hook (never a vanilla or StardewCraft block). */
    public static boolean legacyForgeSoil(BlockState soil) {
        return LEGACY.get(soil.getBlock().getClass());
    }
}

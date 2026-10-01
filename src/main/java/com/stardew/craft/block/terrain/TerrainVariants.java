package com.stardew.craft.block.terrain;

import javax.annotation.Nullable;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Persistent visual choice, independent of the terrain's block identity and connections. */
public final class TerrainVariants {
    public static final IntegerProperty SAND = IntegerProperty.create("variant", 0, 3);
    public static final IntegerProperty ASPHALT = IntegerProperty.create("variant", 0, 2);
    public static final IntegerProperty GRASS = IntegerProperty.create("variant", 0, 2);
    public static final IntegerProperty DIRT = IntegerProperty.create("variant", 0, 4);
    public static final IntegerProperty PAVING = IntegerProperty.create("variant", 0, 5);

    public static final IntegerProperty CLIFF = IntegerProperty.create("variant", 0, 5);

    private TerrainVariants() {}

    @Nullable
    public static IntegerProperty property(BlockState state) {
        if (state.getBlock() instanceof TerrainShapeBlock) return property(TerrainShapeBlock.material(state));
        if (state.getBlock() instanceof PlaygroundSandBlock) return SAND;
        if (state.getBlock() instanceof AsphaltRoadBlock) return ASPHALT;
        if (state.getBlock() instanceof VariedTerrainGrassBlock) return GRASS;
        if (state.getBlock() instanceof TerrainDirtBlock) return DIRT;
        if (state.getBlock() instanceof TerrainCliffBlock) return CLIFF;
        if (state.getBlock() instanceof TownPavingBlock) return PAVING;
        return null;
    }

    public static BlockState placement(BlockState state, BlockPlaceContext context) {
        if (state.getBlock() instanceof TerrainShapeBlock) {
            BlockState selected = placement(TerrainShapeBlock.material(state), context);
            IntegerProperty variant = property(selected);
            return variant == null ? state : state.setValue(variant, selected.getValue(variant));
        }
        IntegerProperty property = property(state);
        if (property == null) return state;
        Integer fixed = context.getItemInHand().getOrDefault(DataComponents.BLOCK_STATE,
                BlockItemStateProperties.EMPTY).get(property);
        if (fixed != null) return state.setValue(property, fixed);
        // The server chooses once; the block state is then saved and synchronized normally.
        if (context.getLevel().isClientSide) return state;
        if (state.getBlock() instanceof TerrainCliffBlock) return state.setValue(property,
                TerrainVariantWeights.cliff(context.getLevel().getRandom().nextInt(1000)));
        if (property == SAND) return state.setValue(property, context.getLevel().getRandom().nextInt(4));
        if (property == ASPHALT) return state.setValue(property, context.getLevel().getRandom().nextInt(3));
        if (property == GRASS || property == DIRT) {
            return randomGroundVariant(state, context.getLevel().getRandom());
        }
        int roll = context.getLevel().getRandom().nextInt(100);
        if (state.is(com.stardew.craft.block.ModBlocks.PLAZA_RED_BRICKS.get()))
            return state.setValue(property, TerrainVariantWeights.redPaving(roll));
        return state.setValue(property, TerrainVariantWeights.paving(roll));
    }

    /** Re-rolls grass or dirt with exactly the same weights as ordinary item placement. */
    public static BlockState randomGroundVariant(BlockState state, RandomSource random) {
        IntegerProperty property = property(state);
        if (property != GRASS && property != DIRT) return state;
        int roll = random.nextInt(100);
        return state.setValue(property, property == GRASS
                ? TerrainVariantWeights.grass(roll)
                : TerrainVariantWeights.dirt(roll));
    }

    public static ItemStack fixedCopy(ItemStack original, BlockState state) {
        IntegerProperty property = property(state);
        if (property == null || original.isEmpty()) return original;
        ItemStack copy = original.copy();
        copy.set(DataComponents.BLOCK_STATE, copy.getOrDefault(DataComponents.BLOCK_STATE,
                BlockItemStateProperties.EMPTY).with(property, state));
        return copy;
    }
}

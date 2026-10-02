package com.stardew.craft.gingerisland;

import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** One object owns all its visual states; assembly pieces never become separate items. */
public abstract class GingerIslandStateDecorBlock extends MapDecorStaticBlock {
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 5);
    public static final EnumProperty<Gem> GEM = EnumProperty.create("gem", Gem.class);
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final BooleanProperty SOLVED = BooleanProperty.create("solved");
    public static final BooleanProperty NIGHT = BooleanProperty.create("night");
    public static final BooleanProperty FLOWING = BooleanProperty.create("flowing");
    public static final BooleanProperty REPAIRED = BooleanProperty.create("repaired");
    private final String property;
    private final String baseModel;
    private final Map<String, String> models;

    private GingerIslandStateDecorBlock(Properties properties, GingerIslandAssets.BlockAsset asset) {
        super(properties, asset.model());
        this.property = asset.state_property();
        this.baseModel = asset.model();
        this.models = Map.copyOf(asset.state_models());
    }

    public static GingerIslandStateDecorBlock create(Properties properties, GingerIslandAssets.BlockAsset asset) {
        return switch (asset.state_property()) {
            case "variant" -> new Variant(properties, asset);
            case "gem" -> new Pedestal(properties, asset);
            case "active" -> new Active(properties, asset);
            case "solved" -> new Solved(properties, asset);
            case "night" -> new Night(properties, asset);
            case "flowing" -> new Flowing(properties, asset);
            case "repaired" -> new Repaired(properties, asset);
            default -> throw new IllegalArgumentException("Unknown Ginger Island state: " + asset.state_property());
        };
    }

    @Override protected VoxelShape canonicalShape() {
        // Reserve the union once. A restored fossil may grow above the empty
        // stand; changing state must not claim previously unreserved cells.
        VoxelShape shape = Shapes.empty();
        for (String model : models.values()) shape = Shapes.or(shape, ModelVoxelShapeCache.shapeFromModelId(model));
        return shape.optimize();
    }

    public String modelForState(BlockState state) {
        return models.getOrDefault(state.getValues().entrySet().stream()
                .filter(e -> e.getKey().getName().equals(property))
                .map(e -> e.getValue().toString()).findFirst().orElse(""), baseModel);
    }

    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        BlockPos main = findMainPos(level, pos, state);
        if (main == null) return Shapes.empty();
        BlockState actual = state.getValue(PART) == Part.MAIN ? state : level.getBlockState(main);
        VoxelShape shape = rotateShapeForFacing(ModelVoxelShapeCache.shapeFromModelId(modelForState(actual)), actual.getValue(FACING));
        return shape.move(main.getX() - pos.getX(), main.getY() - pos.getY(), main.getZ() - pos.getZ());
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) return null;
        return context.getItemInHand().getOrDefault(DataComponents.BLOCK_STATE,
                BlockItemStateProperties.EMPTY).apply(state);
    }

    @Override public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        BlockPos main = findMainPos(level, pos, state);
        if (main != null) state = level.getBlockState(main);
        ItemStack stack = new ItemStack(this);
        var values = new java.util.HashMap<String, String>();
        BlockState selected = state;
        state.getProperties().stream().filter(p -> p.getName().equals(property))
                .forEach(p -> values.put(property, selected.getValues().get(p).toString()));
        stack.set(DataComponents.BLOCK_STATE, new BlockItemStateProperties(values));
        return stack;
    }

    public enum Gem implements StringRepresentable {
        EMPTY, AMETHYST, AQUAMARINE, EMERALD, RUBY, TOPAZ;
        @Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
        @Override public String toString() { return getSerializedName(); }
    }

    private static final class Variant extends GingerIslandStateDecorBlock {
        private Variant(Properties p, GingerIslandAssets.BlockAsset a) { super(p, a); registerDefaultState(defaultBlockState().setValue(VARIANT, 0)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { super.createBlockStateDefinition(b); b.add(VARIANT); }
    }
    private static final class Pedestal extends GingerIslandStateDecorBlock {
        private Pedestal(Properties p, GingerIslandAssets.BlockAsset a) { super(p, a); registerDefaultState(defaultBlockState().setValue(GEM, Gem.EMPTY)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { super.createBlockStateDefinition(b); b.add(GEM); }
    }
    private static final class Active extends GingerIslandStateDecorBlock {
        private Active(Properties p, GingerIslandAssets.BlockAsset a) { super(p, a); registerDefaultState(defaultBlockState().setValue(ACTIVE, false)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { super.createBlockStateDefinition(b); b.add(ACTIVE); }
    }
    private static final class Solved extends GingerIslandStateDecorBlock {
        private Solved(Properties p, GingerIslandAssets.BlockAsset a) { super(p, a); registerDefaultState(defaultBlockState().setValue(SOLVED, false)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { super.createBlockStateDefinition(b); b.add(SOLVED); }
    }
    private static final class Night extends GingerIslandStateDecorBlock {
        private Night(Properties p, GingerIslandAssets.BlockAsset a) { super(p, a); registerDefaultState(defaultBlockState().setValue(NIGHT, false)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { super.createBlockStateDefinition(b); b.add(NIGHT); }
    }
    private static final class Flowing extends GingerIslandStateDecorBlock {
        private Flowing(Properties p, GingerIslandAssets.BlockAsset a) { super(p, a); registerDefaultState(defaultBlockState().setValue(FLOWING, false)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { super.createBlockStateDefinition(b); b.add(FLOWING); }
    }
    private static final class Repaired extends GingerIslandStateDecorBlock {
        private Repaired(Properties p, GingerIslandAssets.BlockAsset a) { super(p, a); registerDefaultState(defaultBlockState().setValue(REPAIRED, false)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { super.createBlockStateDefinition(b); b.add(REPAIRED); }
    }
}

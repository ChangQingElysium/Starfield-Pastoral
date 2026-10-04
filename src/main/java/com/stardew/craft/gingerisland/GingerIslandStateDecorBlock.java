package com.stardew.craft.gingerisland;

import com.stardew.craft.port.PortItemData;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
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
    private final boolean buildingVariants;
    private final Map<ShapeKey, OrientedShape> orientedShapes = new ConcurrentHashMap<>();

    protected GingerIslandStateDecorBlock(Properties properties, GingerIslandAssets.BlockAsset asset) {
        super(properties, asset.model());
        this.property = asset.state_property();
        this.baseModel = asset.model();
        this.models = Map.copyOf(asset.state_models());
        this.buildingVariants = "appearance".equals(asset.state_role());
    }

    public static GingerIslandStateDecorBlock create(Properties properties, GingerIslandAssets.BlockAsset asset) {
        return switch (asset.state_property()) {
            case "variant" -> new Variant(properties, asset);
            case "gem" -> new GingerIslandGemPedestalBlock(properties, asset);
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
        String model = modelForState(actual);
        VoxelShape source = ModelVoxelShapeCache.shapeFromModelId(model);
        ShapeKey key = new ShapeKey(model, actual.getValue(FACING));
        OrientedShape cached = orientedShapes.get(key);
        if (cached == null || cached.source() != source) {
            // Source identity changes when the model cache is cleared. Do not
            // keep stale geometry across sessions or repeat expensive rotation each tick.
            cached = orientedShapes.compute(key, (unused, previous) ->
                    previous != null && previous.source() == source ? previous
                            : new OrientedShape(source, rotateShapeForFacing(source, key.facing()), new ConcurrentHashMap<>()));
        }
        if (main.equals(pos)) return cached.rotated();
        OrientedShape oriented = cached;
        return oriented.parts().computeIfAbsent(main.subtract(pos), offset ->
                oriented.rotated().move(offset.getX(), offset.getY(), offset.getZ()));
    }

    private record ShapeKey(String model, Direction facing) {}
    private record OrientedShape(VoxelShape source, VoxelShape rotated, Map<BlockPos, VoxelShape> parts) {}

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) return null;
        return itemState(context.getItemInHand()).apply(state);
    }

    /** All authored world states, including interaction and progression states. */
    public List<String> visualStateValues() {
        String base = visualValue(defaultBlockState());
        return defaultBlockState().getProperties().stream()
                .filter(p -> p.getName().equals(property)).findFirst().orElseThrow()
                .getPossibleValues().stream().map(Object::toString).filter(models::containsKey)
                .sorted((left, right) -> left.equals(right) ? 0 : left.equals(base) ? -1 : right.equals(base) ? 1
                        : property.equals("variant") ? Integer.compare(Integer.parseInt(left), Integer.parseInt(right))
                        : left.compareTo(right)).toList();
    }

    public String visualStateProperty() { return property; }

    public boolean hasBuildingVariants() { return buildingVariants; }

    public List<String> buildingStateValues() {
        return buildingVariants ? visualStateValues() : List.of(visualValue(defaultBlockState()));
    }

    public BlockItemStateProperties itemState(ItemStack stack) {
        String value = PortItemData.getOrDefault(stack, DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY)
                .properties().get(property);
        if (!buildingVariants || value == null || !models.containsKey(value)) value = visualValue(defaultBlockState());
        return new BlockItemStateProperties(Map.of(property, value));
    }

    public ItemStack stackForVisualState(String value) {
        if (!models.containsKey(value)) throw new IllegalArgumentException("Unauthored Ginger Island state: " + value);
        ItemStack stack = new ItemStack(this);
        PortItemData.set(stack, DataComponents.BLOCK_STATE, new BlockItemStateProperties(Map.of(property, value)));
        return stack;
    }

    private String visualValue(BlockState state) {
        return state.getValues().entrySet().stream().filter(e -> e.getKey().getName().equals(property))
                .map(e -> e.getValue().toString()).findFirst().orElseThrow();
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        // BlockItem reapplies BLOCK_STATE after getStateForPlacement. Normalize again
        // before the multiblock copies it, so a legal integer with no authored model
        // cannot survive as a phantom variant on the main or its extensions.
        BlockState selected = buildingVariants && models.containsKey(visualValue(state)) ? state : itemState(stack).apply(state);
        if (selected != state) level.setBlock(pos, selected, 2);
        super.setPlacedBy(level, pos, selected, placer, stack);
    }

    @Override public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        if (!buildingVariants) return new ItemStack(this);
        BlockPos main = findMainPos(level, pos, state);
        if (main != null) state = level.getBlockState(main);
        String value = visualValue(state);
        return GingerIslandVariantStacks.nameStack(
                stackForVisualState(models.containsKey(value) ? value : visualValue(defaultBlockState())));
    }

    @Override public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (state.getValue(PART) == Part.EXTENSION) return List.of();
        if (!buildingVariants) return List.of(new ItemStack(this));
        String value = visualValue(state);
        return List.of(GingerIslandVariantStacks.nameStack(stackForVisualState(
                models.containsKey(value) ? value : visualValue(defaultBlockState()))));
    }

    @Override protected ItemStack extensionRemovalDrop(Level level, BlockPos mainPos) {
        return getCloneItemStack(level, mainPos, level.getBlockState(mainPos));
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

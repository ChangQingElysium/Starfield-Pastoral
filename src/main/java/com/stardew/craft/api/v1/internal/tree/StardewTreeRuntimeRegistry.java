package com.stardew.craft.api.v1.internal.tree;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.tree.StardewTreeRuntimeAdapter;
import com.stardew.craft.api.v1.tree.StardewTreeState;
import com.stardew.craft.api.v1.tree.StardewTreeType;
import com.stardew.craft.block.tree.WildTreeSaplingBlock;
import com.stardew.craft.block.tree.fruit.FruitTreeBlock;
import com.stardew.craft.block.tree.fruit.FruitTreeExtensionBlock;
import com.stardew.craft.block.tree.fruit.FruitTreeSaplingBlock;
import com.stardew.craft.manager.FruitTreeGrowthManager;
import com.stardew.craft.manager.TreeGrowthManager;
import com.stardew.craft.tree.WildTrees;
import com.stardew.craft.tree.fruit.FruitTreeType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Core tree runtime dispatch bridge. Not part of the public compatibility surface. */
public final class StardewTreeRuntimeRegistry {
    private static final Map<ResourceLocation, StardewTreeType> CORE_TYPES = createCoreTypes();
    private static final Map<ResourceLocation, Registration> ADDONS = new HashMap<>();
    private static volatile Catalog catalog = new Catalog(
            Map.of(),
            List.of(),
            sortedDefinitions(CORE_TYPES.values()));

    private StardewTreeRuntimeRegistry() {
    }

    public static synchronized void register(
            StardewTreeType type,
            int priority,
            StardewTreeRuntimeAdapter adapter
    ) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(adapter, "adapter");
        if (type.id().getNamespace().equals(StardewCraft.MODID)) {
            throw new IllegalArgumentException(
                    "Addon tree types cannot use the reserved stardewcraft namespace: " + type.id());
        }
        if (ADDONS.containsKey(type.id()) || CORE_TYPES.containsKey(type.id())) {
            throw new IllegalStateException("Stardew tree type already registered: " + type.id());
        }
        ADDONS.put(type.id(), new Registration(type, priority, adapter));
        ArrayList<Registration> ordered = new ArrayList<>(ADDONS.values());
        ordered.sort(Comparator.comparingInt(Registration::priority).reversed()
                .thenComparing(value -> value.type().id().toString()));

        ArrayList<StardewTreeType> definitions = new ArrayList<>(CORE_TYPES.values());
        for (Registration registration : ADDONS.values()) {
            definitions.add(registration.type());
        }
        catalog = new Catalog(
                Map.copyOf(ADDONS),
                List.copyOf(ordered),
                sortedDefinitions(definitions));
    }

    @Nullable
    public static StardewTreeType definition(ResourceLocation typeId) {
        Objects.requireNonNull(typeId, "typeId");
        StardewTreeType core = CORE_TYPES.get(typeId);
        if (core != null) {
            return core;
        }
        Registration addon = catalog.byId().get(typeId);
        return addon == null ? null : addon.type();
    }

    public static List<StardewTreeType> definitions() {
        return catalog.definitions();
    }

    @Nullable
    public static StardewTreeState inspect(LevelReader level, BlockPos position) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(position, "position");
        StardewTreeState core = inspectCore(level, position);
        return core != null ? core : inspectAddon(level, position);
    }

    @Nullable
    public static StardewTreeState inspectAddon(LevelReader level, BlockPos position) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(position, "position");
        for (Registration registration : catalog.ordered()) {
            try {
                StardewTreeState state = registration.adapter().inspect(level, position.immutable());
                if (state == null) {
                    continue;
                }
                if (!state.typeId().equals(registration.type().id())) {
                    StardewCraft.LOGGER.error(
                            "Stardew tree adapter {} returned mismatched type {}",
                            registration.type().id(), state.typeId());
                    continue;
                }
                if (state.visualStage() >= registration.type().visualStageCount()) {
                    StardewCraft.LOGGER.error(
                            "Stardew tree adapter {} returned visual stage {} outside 0..{}",
                            registration.type().id(),
                            state.visualStage(),
                            registration.type().visualStageCount() - 1);
                    continue;
                }
                return state;
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "Stardew tree adapter {} failed to inspect {}",
                        registration.type().id(), position, exception);
            }
        }
        return null;
    }

    public static boolean growOneDay(ServerLevel level, BlockPos position) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(position, "position");
        StardewTreeState core = inspectCore(level, position);
        if (core != null) {
            if (core.typeId().getPath().startsWith("fruit/")) {
                FruitTreeGrowthManager.get(level).growOneDay(level, core.root());
                return true;
            }
            if (core.typeId().getPath().startsWith("wild/")) {
                TreeGrowthManager.get(level).growOneDay(level, core.root());
                return true;
            }
        }

        StardewTreeState addon = inspectAddon(level, position);
        Registration registration = addon == null
                ? null : catalog.byId().get(addon.typeId());
        if (registration == null || !isStillSameTree(level, addon, registration)) {
            return false;
        }
        try {
            return registration.adapter().growOneDay(level, addon);
        } catch (RuntimeException exception) {
            logOperationFailure("grow", registration, addon, exception);
            return false;
        }
    }

    public static StardewTreeRuntimeAdapter.FertilizerResult fertilize(
            ServerLevel level,
            BlockPos position
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(position, "position");
        StardewTreeState core = inspectCore(level, position);
        if (core != null) {
            if (core.mature()) {
                return StardewTreeRuntimeAdapter.FertilizerResult.MATURE;
            }
            if (core.typeId().getPath().startsWith("wild/")) {
                TreeGrowthManager manager = TreeGrowthManager.get(level);
                if (manager.isFertilized(level, core.root())) {
                    return StardewTreeRuntimeAdapter.FertilizerResult.ALREADY_APPLIED;
                }
                return manager.fertilize(level, core.root())
                        ? StardewTreeRuntimeAdapter.FertilizerResult.APPLIED
                        : StardewTreeRuntimeAdapter.FertilizerResult.CANNOT_APPLY;
            }
            return StardewTreeRuntimeAdapter.FertilizerResult.CANNOT_APPLY;
        }

        StardewTreeState addon = inspectAddon(level, position);
        Registration registration = addon == null
                ? null : catalog.byId().get(addon.typeId());
        if (registration == null || !isStillSameTree(level, addon, registration)) {
            return StardewTreeRuntimeAdapter.FertilizerResult.PASS;
        }
        try {
            StardewTreeRuntimeAdapter.FertilizerResult result =
                    registration.adapter().fertilize(level, addon);
            return result == null
                    ? StardewTreeRuntimeAdapter.FertilizerResult.PASS
                    : result;
        } catch (RuntimeException exception) {
            logOperationFailure("fertilize", registration, addon, exception);
            return StardewTreeRuntimeAdapter.FertilizerResult.PASS;
        }
    }

    @Nullable
    public static StardewTreeState findAddonTapperSupport(
            LevelReader level,
            BlockPos supportPosition
    ) {
        StardewTreeState state = inspectAddon(level, supportPosition);
        if (state == null || !state.mature()) {
            return null;
        }
        StardewTreeType type = definition(state.typeId());
        if (type == null || !type.tapperEligible()) {
            return null;
        }
        return state.tapperSupports().contains(supportPosition) ? state : null;
    }

    @Nullable
    public static StardewTreeRuntimeAdapter.TapperCycle resolveAddonTapperCycle(
            ServerLevel level,
            StardewTreeState expectedTree,
            BlockPos supportPosition
    ) {
        StardewTreeState current = findAddonTapperSupport(level, supportPosition);
        if (current == null
                || !current.typeId().equals(expectedTree.typeId())
                || !current.root().equals(expectedTree.root())) {
            return null;
        }
        Registration registration = catalog.byId().get(current.typeId());
        if (registration == null) {
            return null;
        }
        try {
            return registration.adapter().resolveTapperCycle(
                    level, current, supportPosition.immutable());
        } catch (RuntimeException exception) {
            logOperationFailure("resolve tapper cycle", registration, current, exception);
            return null;
        }
    }

    private static boolean isStillSameTree(
            LevelReader level,
            StardewTreeState expected,
            Registration registration
    ) {
        try {
            StardewTreeState current = registration.adapter().inspect(level, expected.root());
            return current != null
                    && current.typeId().equals(expected.typeId())
                    && current.root().equals(expected.root());
        } catch (RuntimeException exception) {
            logOperationFailure("revalidate", registration, expected, exception);
            return false;
        }
    }

    @Nullable
    private static StardewTreeState inspectCore(LevelReader level, BlockPos position) {
        BlockState state = level.getBlockState(position);
        if (state.getBlock() instanceof FruitTreeSaplingBlock sapling) {
            BlockPos root = FruitTreeSaplingBlock.lowerPos(state, position);
            int stage = level.getBlockState(root).getValue(FruitTreeSaplingBlock.AGE);
            return new StardewTreeState(
                    fruitId(sapling.getType()),
                    root,
                    StardewTreeState.Part.SAPLING,
                    stage,
                    false,
                    List.of()
            );
        }
        if (state.getBlock() instanceof FruitTreeBlock fruitTree) {
            return matureFruitState(fruitTree.getType(), position, StardewTreeState.Part.ROOT);
        }
        if (state.getBlock() instanceof FruitTreeExtensionBlock extension) {
            BlockPos root = FruitTreeBlock.findRoot(level, position);
            if (root != null) {
                return matureFruitState(
                        extension.getType(), root, StardewTreeState.Part.EXTENSION);
            }
        }

        WildTrees.Def saplingDef = WildTrees.findBySapling(state);
        if (saplingDef != null) {
            int stage = state.getBlock() instanceof WildTreeSaplingBlock sapling
                    ? sapling.getStage() * 3
                    : 0;
            if (level instanceof ServerLevel serverLevel) {
                stage = TreeGrowthManager.get(serverLevel).getGrowthStage(serverLevel, position);
            }
            return new StardewTreeState(
                    wildId(saplingDef),
                    position,
                    StardewTreeState.Part.SAPLING,
                    stage,
                    false,
                    List.of()
            );
        }

        WildTrees.Def matureDef = WildTrees.findByAnyPart(state);
        if (matureDef != null) {
            BlockPos root = WildTrees.findGeneratedModernRoot(level, position, matureDef);
            if (root == null && matureDef.isTrunk0(state)) {
                root = position;
            }
            if (root != null) {
                return new StardewTreeState(
                        wildId(matureDef),
                        root,
                        matureDef.isModernRoot(state)
                                ? StardewTreeState.Part.ROOT
                                : StardewTreeState.Part.TRUNK,
                        5,
                        true,
                        List.of()
                );
            }
        }
        return null;
    }

    private static StardewTreeState matureFruitState(
            FruitTreeType type,
            BlockPos root,
            StardewTreeState.Part part
    ) {
        return new StardewTreeState(fruitId(type), root, part, 4, true, List.of());
    }

    private static Map<ResourceLocation, StardewTreeType> createCoreTypes() {
        LinkedHashMap<ResourceLocation, StardewTreeType> definitions = new LinkedHashMap<>();
        for (FruitTreeType type : FruitTreeType.values()) {
            StardewTreeType definition = new StardewTreeType(
                    fruitId(type),
                    StardewTreeType.Kind.FRUIT,
                    "block.stardewcraft." + type.matureBlockId(),
                    FruitTreeType.DAYS_TO_MATURE,
                    5,
                    false
            );
            definitions.put(definition.id(), definition);
        }
        for (WildTrees.Def def : WildTrees.ALL) {
            StardewTreeType definition = new StardewTreeType(
                    wildId(def),
                    StardewTreeType.Kind.WILD,
                    "block.stardewcraft." + def.id() + "_tree",
                    28,
                    6,
                    true
            );
            definitions.put(definition.id(), definition);
        }
        return Map.copyOf(definitions);
    }

    private static ResourceLocation fruitId(FruitTreeType type) {
        return ResourceLocation.fromNamespaceAndPath(
                StardewCraft.MODID, "fruit/" + type.id());
    }

    private static ResourceLocation wildId(WildTrees.Def def) {
        return ResourceLocation.fromNamespaceAndPath(
                StardewCraft.MODID, "wild/" + def.id());
    }

    private static List<StardewTreeType> sortedDefinitions(
            java.util.Collection<StardewTreeType> definitions
    ) {
        ArrayList<StardewTreeType> sorted = new ArrayList<>(definitions);
        sorted.sort(Comparator.comparing(value -> value.id().toString()));
        return List.copyOf(sorted);
    }

    private static void logOperationFailure(
            String operation,
            Registration registration,
            StardewTreeState tree,
            RuntimeException exception
    ) {
        StardewCraft.LOGGER.error(
                "Stardew tree adapter {} failed to {} tree at {}",
                registration.type().id(), operation, tree.root(), exception);
    }

    private record Registration(
            StardewTreeType type,
            int priority,
            StardewTreeRuntimeAdapter adapter
    ) {
    }

    private record Catalog(
            Map<ResourceLocation, Registration> byId,
            List<Registration> ordered,
            List<StardewTreeType> definitions
    ) {
    }
}

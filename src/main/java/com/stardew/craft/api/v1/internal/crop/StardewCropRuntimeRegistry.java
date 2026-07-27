package com.stardew.craft.api.v1.internal.crop;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.agriculture.StardewCropDailyContext;
import com.stardew.craft.api.v1.agriculture.StardewCropHarvestContext;
import com.stardew.craft.api.v1.agriculture.StardewCropHarvestResult;
import com.stardew.craft.api.v1.agriculture.StardewCropRemovalCause;
import com.stardew.craft.api.v1.agriculture.StardewCropRuntimeAdapter;
import com.stardew.craft.api.v1.agriculture.StardewCropState;
import com.stardew.craft.api.v1.agriculture.StardewCropType;
import com.stardew.craft.block.crop.StardewCropBlock;
import com.stardew.craft.farming.SeasonLocationRules;
import com.stardew.craft.manager.CropGrowthManager;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Core crop runtime dispatch bridge. Not part of the public compatibility surface. */
public final class StardewCropRuntimeRegistry {
    private static final Map<ResourceLocation, Registration> ADDONS = new HashMap<>();
    private static volatile Catalog catalog = Catalog.empty();

    private StardewCropRuntimeRegistry() {
    }

    public static synchronized void register(
            StardewCropType type,
            int priority,
            StardewCropRuntimeAdapter adapter
    ) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(adapter, "adapter");
        if (type.id().getNamespace().equals(StardewCraft.MODID)) {
            throw new IllegalArgumentException(
                    "Addon crop types cannot use the reserved stardewcraft namespace: " + type.id());
        }
        if (ADDONS.containsKey(type.id())) {
            throw new IllegalStateException("Stardew crop type already registered: " + type.id());
        }
        ADDONS.put(type.id(), new Registration(type, priority, adapter));

        ArrayList<Registration> ordered = new ArrayList<>(ADDONS.values());
        ordered.sort(Comparator.comparingInt(Registration::priority).reversed()
                .thenComparing(value -> value.type().id().toString()));
        HashMap<ResourceLocation, ArrayList<Registration>> mutableIndex =
                new HashMap<>();
        for (Registration registration : ordered) {
            for (ResourceLocation blockId : registration.type().blockIds()) {
                mutableIndex.computeIfAbsent(
                        blockId, ignored -> new ArrayList<>()).add(registration);
            }
        }
        HashMap<ResourceLocation, List<Registration>> immutableIndex =
                new HashMap<>();
        mutableIndex.forEach((blockId, registrations) ->
                immutableIndex.put(blockId, List.copyOf(registrations)));

        ArrayList<StardewCropType> definitions = new ArrayList<>();
        for (Registration registration : ADDONS.values()) {
            definitions.add(registration.type());
        }
        definitions.sort(Comparator.comparing(value -> value.id().toString()));
        catalog = new Catalog(
                Map.copyOf(ADDONS),
                List.copyOf(definitions),
                Map.copyOf(immutableIndex));
    }

    @Nullable
    public static StardewCropType definition(ResourceLocation typeId) {
        Objects.requireNonNull(typeId, "typeId");
        Registration registration = catalog.byId().get(typeId);
        return registration == null ? null : registration.type();
    }

    public static List<StardewCropType> definitions() {
        return catalog.definitions();
    }

    @Nullable
    public static StardewCropState inspect(LevelReader level, BlockPos position) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(position, "position");
        StardewCropState core = inspectCore(level, position);
        return core != null ? core : inspectAddon(level, position);
    }

    @Nullable
    public static StardewCropState inspectAddon(LevelReader level, BlockPos position) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(position, "position");
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(
                level.getBlockState(position).getBlock());
        Catalog current = catalog;
        for (Registration registration :
                current.byBlock().getOrDefault(blockId, List.of())) {
            try {
                StardewCropState state =
                        registration.adapter().inspect(level, position.immutable());
                if (state == null) {
                    continue;
                }
                if (!isValidState(registration, state)) {
                    continue;
                }
                return state;
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "Stardew crop adapter {} failed to inspect {}",
                        registration.type().id(), position, exception);
            }
        }
        return null;
    }

    public static boolean isRegisteredBlock(BlockState state) {
        Objects.requireNonNull(state, "state");
        return state.getBlock() instanceof StardewCropBlock
                || catalog.byBlock().containsKey(
                        BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    public static StardewCropRuntimeAdapter.DailyResult growOneDay(
            ServerLevel level,
            BlockPos position,
            boolean watered,
            boolean offlineCatchUp
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(position, "position");

        BlockState state = level.getBlockState(position);
        if (state.getBlock() instanceof StardewCropBlock cropBlock) {
            CropGrowthManager.CropGrowthState growthState =
                    CropGrowthManager.get(level).getOrCreateState(level, position);
            cropBlock.growCropOneDay(level, position, state, watered, growthState);
            return level.getBlockState(position).getBlock() instanceof StardewCropBlock
                    ? StardewCropRuntimeAdapter.DailyResult.CHANGED
                    : StardewCropRuntimeAdapter.DailyResult.REMOVED;
        }

        StardewCropState addon = inspectAddon(level, position);
        Registration registration = addon == null
                ? null : catalog.byId().get(addon.typeId());
        if (registration == null || !isStillSameCrop(level, addon, registration)) {
            return StardewCropRuntimeAdapter.DailyResult.REMOVED;
        }

        StardewTimeManager time = StardewTimeManager.get();
        StardewCropDailyContext context = new StardewCropDailyContext(
                watered,
                Math.max(0, Math.min(3, time.getCurrentSeason())),
                SeasonLocationRules.seedsIgnoreSeasonsHere(level, addon.root()),
                Math.max(0, (time.getCurrentYear() - 1) * 112
                        + time.getCurrentSeason() * 28 + time.getCurrentDay()),
                offlineCatchUp
        );
        try {
            StardewCropRuntimeAdapter.DailyResult result =
                    registration.adapter().growOneDay(level, addon, context);
            if (result == null) {
                result = StardewCropRuntimeAdapter.DailyResult.UNCHANGED;
            }
            if (result == StardewCropRuntimeAdapter.DailyResult.REMOVED
                    || !isStillSameCrop(level, addon, registration)) {
                return StardewCropRuntimeAdapter.DailyResult.REMOVED;
            }
            return result;
        } catch (RuntimeException exception) {
            StardewCraft.LOGGER.error(
                    "Stardew crop adapter {} failed to grow crop at {}",
                    registration.type().id(), addon.root(), exception);
            return StardewCropRuntimeAdapter.DailyResult.UNCHANGED;
        }
    }

    public static StardewCropHarvestResult harvest(
            ServerLevel level,
            BlockPos position,
            StardewCropHarvestContext context,
            Consumer<ItemStack> output
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(output, "output");

        StardewCropState crop = inspect(level, position);
        if (crop == null) {
            return StardewCropHarvestResult.pass();
        }
        if (context.player() != null
                && !canPlayerHarvest(context.player(), level, crop.root())) {
            return StardewCropHarvestResult.pass();
        }

        BlockState rootState = level.getBlockState(crop.root());
        if (rootState.getBlock() instanceof StardewCropBlock coreCrop) {
            return harvestCore(level, coreCrop, crop.root(), rootState, context, output);
        }

        Registration registration = catalog.byId().get(crop.typeId());
        if (registration == null || !isStillSameCrop(level, crop, registration)) {
            return StardewCropHarvestResult.pass();
        }

        ArrayList<ItemStack> staged = new ArrayList<>();
        StardewCropHarvestResult result;
        try {
            result = registration.adapter().harvest(
                    level,
                    crop,
                    context,
                    stack -> {
                        if (stack != null && !stack.isEmpty()) {
                            staged.add(stack.copy());
                        }
                    }
            );
        } catch (RuntimeException exception) {
            StardewCraft.LOGGER.error(
                    "Stardew crop adapter {} failed to harvest crop at {}",
                    registration.type().id(), crop.root(), exception);
            return StardewCropHarvestResult.pass();
        }
        if (result == null || !result.harvested()) {
            return result == null ? StardewCropHarvestResult.pass() : result;
        }
        for (ItemStack stack : staged) {
            output.accept(stack.copy());
        }
        if (context.source() != StardewCropHarvestContext.Source.AUTOMATION
                && context.player() != null
                && !context.player().isCreative()
                && result.farmingExperience() > 0) {
            com.stardew.craft.player.PlayerStardewDataAPI.addExperience(
                    context.player(),
                    com.stardew.craft.player.SkillType.FARMING,
                    result.farmingExperience());
        }
        return result;
    }

    private static StardewCropHarvestResult harvestCore(
            ServerLevel level,
            StardewCropBlock crop,
            BlockPos root,
            BlockState state,
            StardewCropHarvestContext context,
            Consumer<ItemStack> output
    ) {
        if (context.source() == StardewCropHarvestContext.Source.AUTOMATION) {
            ItemStack harvested = crop.tryHarvestByJunimo(
                    level, root, state, context.farmingLevel(), output);
            return harvested.isEmpty()
                    ? StardewCropHarvestResult.notReady()
                    : StardewCropHarvestResult.harvested(0);
        }
        if (context.source() == StardewCropHarvestContext.Source.TOOL) {
            boolean harvested = crop.tryHarvestByTool(
                    level,
                    root,
                    state,
                    context.player(),
                    context.forceToolHarvest());
            if (harvested) {
                return StardewCropHarvestResult.harvested(0);
            }
            return crop.requiresScytheHarvest() || context.forceToolHarvest()
                    ? StardewCropHarvestResult.notReady()
                    : StardewCropHarvestResult.wrongTool();
        }
        if (crop.requiresScytheHarvest()) {
            return StardewCropHarvestResult.wrongTool();
        }
        return crop.tryHarvestByHand(level, root, state, context.player())
                ? StardewCropHarvestResult.harvested(0)
                : StardewCropHarvestResult.notReady();
    }

    public static boolean remove(
            ServerLevel level,
            BlockPos position,
            StardewCropRemovalCause cause
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(cause, "cause");
        StardewCropState crop = inspect(level, position);
        if (crop == null) {
            return false;
        }

        BlockState rootState = level.getBlockState(crop.root());
        if (rootState.getBlock() instanceof StardewCropBlock) {
            level.setBlock(crop.root(),
                    net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            CropGrowthManager.get(level).removeCrop(level, crop.root());
            return true;
        }

        Registration registration = catalog.byId().get(crop.typeId());
        if (registration == null || !isStillSameCrop(level, crop, registration)) {
            return false;
        }
        try {
            if (!registration.adapter().remove(level, crop, cause)) {
                return false;
            }
            CropGrowthManager.get(level).removeCrop(level, crop.root());
            return true;
        } catch (RuntimeException exception) {
            StardewCraft.LOGGER.error(
                    "Stardew crop adapter {} failed to remove crop at {} for {}",
                    registration.type().id(), crop.root(), cause, exception);
            return false;
        }
    }

    private static boolean canPlayerHarvest(
            ServerPlayer player,
            ServerLevel level,
            BlockPos position
    ) {
        if (player.isCreative()
                || level.dimension() != com.stardew.craft.core.ModDimensions.STARDEW_VALLEY) {
            return true;
        }
        return com.stardew.craft.greenhouse.GreenhouseManager
                        .isInGreenhouseInterior(level, position)
                ? com.stardew.craft.event.FarmAreaProtectionEvents
                        .canModifyGreenhouseAt(player, level, position)
                : com.stardew.craft.event.FarmAreaProtectionEvents
                        .canModifyAt(player, position);
    }

    private static boolean isStillSameCrop(
            LevelReader level,
            StardewCropState expected,
            Registration registration
    ) {
        try {
            StardewCropState current =
                    registration.adapter().inspect(level, expected.root());
            return current != null
                    && isValidState(registration, current)
                    && current.typeId().equals(expected.typeId())
                    && current.root().equals(expected.root());
        } catch (RuntimeException exception) {
            StardewCraft.LOGGER.error(
                    "Stardew crop adapter {} failed to revalidate crop at {}",
                    registration.type().id(), expected.root(), exception);
            return false;
        }
    }

    private static boolean isValidState(Registration registration, StardewCropState state) {
        if (!state.typeId().equals(registration.type().id())) {
            StardewCraft.LOGGER.error(
                    "Stardew crop adapter {} returned mismatched type {}",
                    registration.type().id(), state.typeId());
            return false;
        }
        if (state.visualStage() >= registration.type().visualStageCount()) {
            StardewCraft.LOGGER.error(
                    "Stardew crop adapter {} returned visual stage {} outside 0..{}",
                    registration.type().id(),
                    state.visualStage(),
                    registration.type().visualStageCount() - 1);
            return false;
        }
        return true;
    }

    @Nullable
    private static StardewCropState inspectCore(LevelReader level, BlockPos position) {
        BlockState state = level.getBlockState(position);
        if (!(state.getBlock() instanceof StardewCropBlock)) {
            return null;
        }

        BlockPos root = position;
        StardewCropState.Part part = StardewCropState.Part.ROOT;
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            BlockPos below = position.below();
            if (level.getBlockState(below).getBlock() == state.getBlock()) {
                root = below;
                part = StardewCropState.Part.UPPER;
                state = level.getBlockState(root);
            }
        }
        int stage = state.hasProperty(StardewCropBlock.AGE)
                ? state.getValue(StardewCropBlock.AGE)
                : 0;
        boolean mature = stage >= StardewCropBlock.MAX_AGE;
        if (level instanceof ServerLevel serverLevel
                && state.getBlock() instanceof StardewCropBlock cropBlock) {
            mature = cropBlock.isReadyForFarmComputer(serverLevel, root, state);
        }
        return new StardewCropState(
                BuiltInRegistries.BLOCK.getKey(state.getBlock()),
                root,
                part,
                stage,
                mature,
                List.of(root.below())
        );
    }

    private record Registration(
            StardewCropType type,
            int priority,
            StardewCropRuntimeAdapter adapter
    ) {
    }

    private record Catalog(
            Map<ResourceLocation, Registration> byId,
            List<StardewCropType> definitions,
            Map<ResourceLocation, List<Registration>> byBlock
    ) {
        private static Catalog empty() {
            return new Catalog(
                    Map.of(), List.of(), Map.of());
        }
    }
}

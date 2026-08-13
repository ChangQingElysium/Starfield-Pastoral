package com.stardew.craft.manager;

import com.stardew.craft.api.v1.world.StardewLocation;
import com.stardew.craft.block.nature.TeaBushBlock;
import com.stardew.craft.block.utility.GardenPotBlock;
import com.stardew.craft.farming.SeasonLocationRules;
import com.stardew.craft.interior.InteriorRegionRegistry;
import com.stardew.craft.interior.SunroomService;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Persistent planting day and daily harvest state for tea bushes. */
public final class TeaBushManager extends SavedData {
    public static final int DAYS_TO_MATURE = 20;
    private static final String DATA_NAME = "stardew_tea_bush_manager";

    private final Map<GlobalPos, Entry> bushes = new ConcurrentHashMap<>();

    public void add(ServerLevel level, BlockPos lowerPos) {
        GlobalPos key = key(level, lowerPos);
        int plantedDay = SunroomService.isCentralTeaBush(level, lowerPos)
                ? currentDay() - DAYS_TO_MATURE : currentDay();
        bushes.putIfAbsent(key, new Entry(plantedDay, Integer.MIN_VALUE));
        setDirty();
        synchronize(level, lowerPos);
    }

    public void ensureMature(ServerLevel level, BlockPos lowerPos) {
        Entry entry = entry(level, lowerPos);
        int maturePlantingDay = currentDay() - DAYS_TO_MATURE;
        if (entry.plantedDay > maturePlantingDay) {
            entry.plantedDay = maturePlantingDay;
            setDirty();
        }
        synchronize(level, lowerPos, entry);
    }

    public void remove(ServerLevel level, BlockPos lowerPos) {
        if (bushes.remove(key(level, lowerPos)) != null) {
            setDirty();
        }
    }

    public int getAgeDays(ServerLevel level, BlockPos lowerPos) {
        Entry entry = entry(level, lowerPos);
        return Math.max(0, currentDay() - entry.plantedDay);
    }

    public boolean harvest(ServerLevel level, BlockPos lowerPos) {
        Entry entry = entry(level, lowerPos);
        synchronize(level, lowerPos, entry);
        int today = currentDay();
        if (!isReady(level, lowerPos, entry, today)) {
            return false;
        }
        entry.harvestedDay = today;
        setDirty();
        synchronize(level, lowerPos, entry);
        return true;
    }

    public boolean isReadyForHarvest(ServerLevel level, BlockPos lowerPos) {
        Entry entry = entry(level, lowerPos);
        synchronize(level, lowerPos, entry);
        return isReady(level, lowerPos, entry, currentDay());
    }

    /** Advances this bush by one growth day for the shared creative growth debug action. */
    public boolean growOneDay(ServerLevel level, BlockPos lowerPos) {
        Entry entry = entry(level, lowerPos);
        if (Math.max(0, currentDay() - entry.plantedDay) >= DAYS_TO_MATURE) {
            synchronize(level, lowerPos, entry);
            return false;
        }
        entry.plantedDay--;
        setDirty();
        synchronize(level, lowerPos, entry);
        return true;
    }

    public void growDaily(ServerLevel level) {
        for (Map.Entry<GlobalPos, Entry> mapEntry : new java.util.ArrayList<>(bushes.entrySet())) {
            GlobalPos globalPos = mapEntry.getKey();
            if (!globalPos.dimension().equals(level.dimension())) {
                continue;
            }
            BlockPos pos = globalPos.pos();
            if (level.isLoaded(pos)) {
                synchronize(level, pos, mapEntry.getValue());
            }
        }
    }

    public void synchronizeChunk(ServerLevel level, ChunkPos chunkPos) {
        for (Map.Entry<GlobalPos, Entry> mapEntry : new java.util.ArrayList<>(bushes.entrySet())) {
            GlobalPos globalPos = mapEntry.getKey();
            if (globalPos.dimension().equals(level.dimension())
                    && new ChunkPos(globalPos.pos()).equals(chunkPos)) {
                synchronize(level, globalPos.pos(), mapEntry.getValue());
            }
        }

    }

    public void synchronize(ServerLevel level, BlockPos lowerPos) {
        synchronize(level, lowerPos, entry(level, lowerPos));
    }

    private void synchronize(ServerLevel level, BlockPos lowerPos, Entry entry) {
        BlockState lower = level.getBlockState(lowerPos);
        if (!(lower.getBlock() instanceof TeaBushBlock)
                || lower.getValue(TeaBushBlock.HALF) != DoubleBlockHalf.LOWER) {
            remove(level, lowerPos);
            return;
        }

        int stage = visualStage(level, lowerPos, entry, currentDay());
        if (com.stardew.craft.core.FarmAreaResolver.isInAnyFarm(level, lowerPos)
                && inBloom(
                Math.max(0, currentDay() - entry.plantedDay),
                StardewTimeManager.get().getCurrentDay(),
                StardewTimeManager.get().getCurrentSeason(),
                isSheltered(level, lowerPos))) {
            com.stardew.craft.npc.runtime.NpcDialogueTopicService
                    .onCropMatured(level.getServer(), "815");
        }
        BlockState desiredLower = lower.setValue(TeaBushBlock.STAGE, stage)
                .setValue(TeaBushBlock.HALF, DoubleBlockHalf.LOWER);
        if (!desiredLower.equals(lower)) {
            level.setBlock(lowerPos, desiredLower, Block.UPDATE_ALL);
        }

        BlockPos upperPos = lowerPos.above();
        BlockState upper = level.getBlockState(upperPos);
        BlockState desiredUpper = desiredLower.setValue(TeaBushBlock.HALF, DoubleBlockHalf.UPPER);
        if (!desiredUpper.equals(upper)) {
            level.setBlock(upperPos, desiredUpper, Block.UPDATE_ALL);
        }
    }

    private int visualStage(ServerLevel level, BlockPos lowerPos, Entry entry, int today) {
        int age = Math.max(0, today - entry.plantedDay);
        StardewTimeManager time = StardewTimeManager.get();
        return visualStageFor(
                age,
                time.getCurrentDay(),
                time.getCurrentSeason(),
                isSheltered(level, lowerPos),
                entry.harvestedDay == today);
    }

    private boolean isReady(ServerLevel level, BlockPos lowerPos, Entry entry, int today) {
        StardewTimeManager time = StardewTimeManager.get();
        int age = Math.max(0, today - entry.plantedDay);
        return readyForHarvest(
                age,
                time.getCurrentDay(),
                time.getCurrentSeason(),
                isSheltered(level, lowerPos),
                entry.harvestedDay == today);
    }

    static int visualStageFor(int ageDays, int dayOfMonth, int season, boolean sheltered, boolean harvestedToday) {
        if (readyForHarvest(ageDays, dayOfMonth, season, sheltered, harvestedToday)) {
            return 3;
        }
        return Math.min(2, Math.max(0, ageDays) / 10);
    }

    static boolean readyForHarvest(
            int ageDays, int dayOfMonth, int season, boolean sheltered, boolean harvestedToday) {
        return inBloom(ageDays, dayOfMonth, season, sheltered) && !harvestedToday;
    }

    static boolean inBloom(int ageDays, int dayOfMonth, int season, boolean sheltered) {
        return ageDays >= DAYS_TO_MATURE
                && dayOfMonth >= 22
                && (season != 3 || sheltered);
    }

    /** Equivalent to Bush.IsSheltered(): greenhouse/seasonless area, or an indoor garden pot. */
    public boolean isSheltered(ServerLevel level, BlockPos lowerPos) {
        boolean potted = level.getBlockState(lowerPos.below()).getBlock() instanceof GardenPotBlock;
        boolean indoorPot = potted
                && InteriorRegionRegistry.locationAt(level.dimension().location(), lowerPos)
                .map(StardewLocation::indoor)
                .orElse(false);
        return SeasonLocationRules.seedsIgnoreSeasonsHere(level, lowerPos) || indoorPot;
    }

    private Entry entry(ServerLevel level, BlockPos lowerPos) {
        GlobalPos key = key(level, lowerPos);
        Entry existing = bushes.get(key);
        if (existing != null) {
            return existing;
        }
        int plantedDay = SunroomService.isCentralTeaBush(level, lowerPos)
                ? currentDay() - DAYS_TO_MATURE : currentDay();
        Entry created = new Entry(plantedDay, Integer.MIN_VALUE);
        bushes.put(key, created);
        setDirty();
        return created;
    }

    private static int currentDay() {
        return StardewTimeManager.get().getAbsoluteDay();
    }

    private static GlobalPos key(Level level, BlockPos pos) {
        return GlobalPos.of(
                Objects.requireNonNull(level.dimension(), "dimension"),
                Objects.requireNonNull(pos.immutable(), "pos"));
    }

    @Override
    public @Nonnull CompoundTag save(@Nonnull CompoundTag tag, @Nonnull net.minecraft.core.HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (Map.Entry<GlobalPos, Entry> mapEntry : bushes.entrySet()) {
            CompoundTag entryTag = writeGlobalPos(mapEntry.getKey());
            Entry entry = mapEntry.getValue();
            entryTag.putInt("PlantedDay", entry.plantedDay);
            entryTag.putInt("HarvestedDay", entry.harvestedDay);
            list.add(entryTag);
        }
        tag.put("Bushes", list);
        return tag;
    }

    public static TeaBushManager load(@Nonnull CompoundTag tag, @Nonnull net.minecraft.core.HolderLookup.Provider provider) {
        TeaBushManager manager = new TeaBushManager();
        if (!tag.contains("Bushes", Tag.TAG_LIST)) {
            return manager;
        }
        ListTag list = tag.getList("Bushes", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            GlobalPos pos = readGlobalPos(entryTag);
            if (pos != null) {
                manager.bushes.put(pos, new Entry(
                        entryTag.getInt("PlantedDay"),
                        entryTag.getInt("HarvestedDay")));
            }
        }
        return manager;
    }

    public static TeaBushManager get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(TeaBushManager::new, TeaBushManager::load),
                DATA_NAME);
    }

    private static CompoundTag writeGlobalPos(GlobalPos globalPos) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Dimension", globalPos.dimension().location().toString());
        tag.put("Pos", NbtUtils.writeBlockPos(globalPos.pos()));
        return tag;
    }

    private static GlobalPos readGlobalPos(CompoundTag tag) {
        if (!tag.contains("Dimension", Tag.TAG_STRING) || !tag.contains("Pos", Tag.TAG_COMPOUND)) {
            return null;
        }
        ResourceKey<Level> dimension = ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                net.minecraft.resources.ResourceLocation.parse(tag.getString("Dimension")));
        BlockPos pos = NbtUtils.readBlockPos(tag, "Pos").orElse(null);
        return pos == null ? null : GlobalPos.of(dimension, pos);
    }

    private static final class Entry {
        private int plantedDay;
        private int harvestedDay;

        private Entry(int plantedDay, int harvestedDay) {
            this.plantedDay = plantedDay;
            this.harvestedDay = harvestedDay;
        }
    }
}

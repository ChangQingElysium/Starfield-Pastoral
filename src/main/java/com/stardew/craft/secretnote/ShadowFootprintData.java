package com.stardew.craft.secretnote;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.nature.ShadowFootprintBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Persistent authoring index for placed Shadow Footprint debug blocks. */
public final class ShadowFootprintData extends SavedData {
    private static final String DATA_NAME = "stardewcraft_shadow_footprints";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Set<Long> positions = new HashSet<>();
    private boolean nextLeft = true;

    public ShadowFootprintBlock.Foot takeNextFoot() {
        ShadowFootprintBlock.Foot foot = nextLeft
                ? ShadowFootprintBlock.Foot.LEFT
                : ShadowFootprintBlock.Foot.RIGHT;
        nextLeft = !nextLeft;
        setDirty();
        return foot;
    }

    public void add(BlockPos pos) {
        if (positions.add(pos.asLong())) setDirty();
    }

    public void remove(BlockPos pos) {
        if (positions.remove(pos.asLong())) setDirty();
    }

    public ExportResult export(ServerLevel level) throws IOException {
        List<BlockPos> valid = positions.stream()
                .map(position -> BlockPos.of(position.longValue()))
                .filter(pos -> level.getBlockState(pos).is(ModBlocks.SHADOW_FOOTPRINT.get()))
                .sorted(Comparator.comparingInt((BlockPos pos) -> pos.getY())
                        .thenComparingInt(pos -> pos.getZ())
                        .thenComparingInt(pos -> pos.getX()))
                .toList();

        if (valid.size() != positions.size()) {
            positions.clear();
            valid.forEach(pos -> positions.add(pos.asLong()));
            setDirty();
        }

        JsonObject root = new JsonObject();
        root.addProperty("dimension", level.dimension().location().toString());
        JsonArray footprints = new JsonArray();
        for (BlockPos pos : valid) {
            BlockState state = level.getBlockState(pos);
            JsonObject point = new JsonObject();
            point.addProperty("x", pos.getX());
            point.addProperty("y", pos.getY());
            point.addProperty("z", pos.getZ());
            point.addProperty("direction", state.getValue(ShadowFootprintBlock.FACING).getName());
            point.addProperty("foot", state.getValue(ShadowFootprintBlock.FOOT).getSerializedName());
            footprints.add(point);
        }
        root.add("footprints", footprints);

        Path output = level.getServer().getWorldPath(LevelResource.ROOT)
                .resolve("stardewcraft_debug")
                .resolve("shadow_footprints.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + System.lineSeparator(), StandardCharsets.UTF_8);
        return new ExportResult(valid.size(), output);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.put("Positions", new LongArrayTag(positions.stream().mapToLong(Long::longValue).toArray()));
        tag.putBoolean("NextLeft", nextLeft);
        return tag;
    }

    private static ShadowFootprintData load(CompoundTag tag, HolderLookup.Provider provider) {
        ShadowFootprintData data = new ShadowFootprintData();
        for (long position : tag.getLongArray("Positions")) {
            data.positions.add(position);
        }
        if (tag.contains("NextLeft")) {
            data.nextLeft = tag.getBoolean("NextLeft");
        }
        return data;
    }

    public static ShadowFootprintData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ShadowFootprintData::new, ShadowFootprintData::load),
                DATA_NAME);
    }

    public record ExportResult(int count, Path path) {}
}

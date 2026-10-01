package com.stardew.craft.festival;

import com.stardew.craft.StardewCraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class SchematicSnapshot {
    private final BlockState[] blocks;
    private final Map<Integer, CompoundTag> blockEntities;
    private final int width;
    private final int height;
    private final int length;

    private SchematicSnapshot(BlockState[] blocks, Map<Integer, CompoundTag> blockEntities, int width, int height, int length) {
        this.blocks = blocks;
        this.blockEntities = blockEntities;
        this.width = width;
        this.height = height;
        this.length = length;
    }

    static SchematicSnapshot load(String path) {
        if (path == null || path.isBlank()) {
            return empty();
        }
        try (InputStream stream = open(path)) {
            if (stream == null) {
                StardewCraft.LOGGER.error("[FESTIVAL_OVERLAY] Schematic not found: {}", path);
                return empty();
            }

            CompoundTag root = com.stardew.craft.port.PortNbtIo.readCompressed(stream, NbtAccounter.UNLIMITED);
            CompoundTag schematic = root;
            if ((readDimension(root, "Width") == 0 || readDimension(root, "Height") == 0 || readDimension(root, "Length") == 0)
                    && root.contains("Schematic", Tag.TAG_COMPOUND)) {
                schematic = root.getCompound("Schematic");
            }

            int width = readDimension(schematic, "Width");
            int height = readDimension(schematic, "Height");
            int length = readDimension(schematic, "Length");
            if (width <= 0 || height <= 0 || length <= 0) {
                StardewCraft.LOGGER.error("[FESTIVAL_OVERLAY] Invalid schematic size for {}: {}x{}x{}", path, width, height, length);
                return empty();
            }

            CompoundTag paletteTag = schematic.getCompound("Palette");
            byte[] blockDataRaw = schematic.getByteArray("BlockData");
            if ((paletteTag.isEmpty() || blockDataRaw.length == 0) && schematic.contains("Blocks", Tag.TAG_COMPOUND)) {
                CompoundTag blocksTag = schematic.getCompound("Blocks");
                if (paletteTag.isEmpty()) {
                    paletteTag = blocksTag.getCompound("Palette");
                }
                if (blockDataRaw.length == 0) {
                    blockDataRaw = blocksTag.getByteArray("Data");
                }
            }
            if (paletteTag.isEmpty()) {
                StardewCraft.LOGGER.error("[FESTIVAL_OVERLAY] Schematic {} has empty palette", path);
                return empty();
            }

            BlockState[] paletteStates = paletteStates(paletteTag, schematic);
            int expected = width * height * length;
            int[] blockIndices = decodeVarIntArray(blockDataRaw, expected);
            BlockState[] blocks = new BlockState[expected];
            for (int index = 0; index < expected; index++) {
                int paletteIndex = index < blockIndices.length ? blockIndices[index] : 0;
                blocks[index] = paletteIndex >= 0 && paletteIndex < paletteStates.length && paletteStates[paletteIndex] != null
                    ? paletteStates[paletteIndex]
                    : Blocks.AIR.defaultBlockState();
            }

            Map<Integer, CompoundTag> blockEntities = blockEntities(root, schematic, width, height, length);
            return new SchematicSnapshot(blocks, blockEntities, width, height, length);
        } catch (Exception ex) {
            StardewCraft.LOGGER.error("[FESTIVAL_OVERLAY] Failed to load schematic {}: {}", path, ex.getMessage(), ex);
            return empty();
        }
    }

    boolean isEmpty() {
        return width <= 0 || height <= 0 || length <= 0;
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    int length() {
        return length;
    }

    boolean hasSameSize(SchematicSnapshot other) {
        return other != null && width == other.width && height == other.height && length == other.length && width > 0;
    }

    BlockState block(int localX, int localY, int localZ) {
        int index = index(localX, localY, localZ);
        return index >= 0 && index < blocks.length ? blocks[index] : Blocks.AIR.defaultBlockState();
    }

    CompoundTag blockEntity(int localX, int localY, int localZ) {
        CompoundTag tag = blockEntities.get(index(localX, localY, localZ));
        return tag == null ? null : tag.copy();
    }

    private int index(int localX, int localY, int localZ) {
        return localY * length * width + localZ * width + localX;
    }

    private static SchematicSnapshot empty() {
        return new SchematicSnapshot(new BlockState[0], Map.of(), 0, 0, 0);
    }

    private static int readDimension(CompoundTag tag, String name) {
        if (tag.contains(name, Tag.TAG_SHORT)) {
            return tag.getShort(name) & 0xFFFF;
        }
        if (tag.contains(name, Tag.TAG_INT)) {
            return tag.getInt(name);
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (tag.contains(lower, Tag.TAG_SHORT)) {
            return tag.getShort(lower) & 0xFFFF;
        }
        if (tag.contains(lower, Tag.TAG_INT)) {
            return tag.getInt(lower);
        }
        return 0;
    }

    private static BlockState[] paletteStates(CompoundTag paletteTag, CompoundTag schematic) {
        int paletteMax = Math.max(1, schematic.getInt("PaletteMax"));
        BlockState[] paletteStates = new BlockState[Math.max(paletteMax, paletteTag.size()) + 1];
        for (String stateString : paletteTag.getAllKeys()) {
            int paletteId = paletteTag.getInt(stateString);
            if (paletteId >= 0 && paletteId < paletteStates.length) {
                paletteStates[paletteId] = parseBlockState(stateString);
            }
        }
        return paletteStates;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static BlockState parseBlockState(String stateString) {
        String blockId = stateString;
        String propsPart = null;
        int bracketStart = stateString.indexOf('[');
        if (bracketStart >= 0 && stateString.endsWith("]")) {
            blockId = stateString.substring(0, bracketStart);
            propsPart = stateString.substring(bracketStart + 1, stateString.length() - 1);
        }

        ResourceLocation id = ResourceLocation.tryParse(blockId);
        if (id == null) {
            return Blocks.AIR.defaultBlockState();
        }
        Block block = BuiltInRegistries.BLOCK.get(id);
        BlockState state = block.defaultBlockState();
        if (propsPart == null || propsPart.isBlank()) {
            return state;
        }

        Map<String, String> values = new HashMap<>();
        for (String token : propsPart.split(",")) {
            String[] keyValue = token.split("=", 2);
            if (keyValue.length == 2) {
                values.put(keyValue[0].trim(), keyValue[1].trim());
            }
        }
        for (Property<?> property : state.getProperties()) {
            String value = values.get(property.getName());
            if (value != null) {
                var parsed = property.getValue(value);
                if (parsed.isPresent()) {
                    state = state.setValue((Property) property, (Comparable) parsed.get());
                }
            }
        }
        return state;
    }

    static String formatBlockState(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        StringBuilder builder = new StringBuilder(id == null ? "minecraft:air" : id.toString());
        if (!state.getProperties().isEmpty()) {
            builder.append('[');
            boolean first = true;
            for (Property<?> property : state.getProperties()) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append(property.getName()).append('=').append(propertyValue(property, state));
            }
            builder.append(']');
        }
        return builder.toString();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String propertyValue(Property property, BlockState state) {
        return property.getName((Comparable) state.getValue(property));
    }

    private static InputStream open(String path) throws java.io.IOException {
        InputStream resource = SchematicSnapshot.class.getClassLoader().getResourceAsStream(path);
        if (resource != null) {
            return resource;
        }
        Path direct = Path.of(path);
        if (Files.exists(direct)) {
            return Files.newInputStream(direct);
        }
        Path fromRunDirectory = Path.of("..").resolve(path).normalize();
        if (Files.exists(fromRunDirectory)) {
            return Files.newInputStream(fromRunDirectory);
        }
        return null;
    }

    private static int[] decodeVarIntArray(byte[] data, int expectedCount) {
        int[] result = new int[expectedCount];
        int outputIndex = 0;
        int byteIndex = 0;
        while (byteIndex < data.length && outputIndex < expectedCount) {
            int value = 0;
            int shift = 0;
            while (true) {
                if (byteIndex >= data.length) {
                    return Arrays.copyOf(result, outputIndex);
                }
                int current = data[byteIndex++] & 0xFF;
                value |= (current & 0x7F) << shift;
                if ((current & 0x80) == 0) {
                    break;
                }
                shift += 7;
                if (shift > 35) {
                    return Arrays.copyOf(result, outputIndex);
                }
            }
            result[outputIndex++] = value;
        }
        return outputIndex == expectedCount ? result : Arrays.copyOf(result, outputIndex);
    }

    private static Map<Integer, CompoundTag> blockEntities(CompoundTag root, CompoundTag schematic, int width, int height, int length) {
        ListTag blockEntities = findBlockEntityList(root, schematic);
        if (blockEntities == null || blockEntities.isEmpty()) {
            return Map.of();
        }
        Map<Integer, CompoundTag> result = new HashMap<>();
        for (int listIndex = 0; listIndex < blockEntities.size(); listIndex++) {
            CompoundTag raw = blockEntities.getCompound(listIndex);
            int[] relative = resolveRelativePos(raw);
            if (relative == null || relative.length < 3) {
                continue;
            }
            int localX = relative[0];
            int localY = relative[1];
            int localZ = relative[2];
            if (localX < 0 || localY < 0 || localZ < 0 || localX >= width || localY >= height || localZ >= length) {
                continue;
            }
            result.put(localY * length * width + localZ * width + localX, raw.copy());
        }
        return result;
    }

    private static ListTag findBlockEntityList(CompoundTag root, CompoundTag schematic) {
        if (schematic.contains("BlockEntities", Tag.TAG_LIST)) {
            return schematic.getList("BlockEntities", Tag.TAG_COMPOUND);
        }
        if (schematic.contains("Blocks", Tag.TAG_COMPOUND)) {
            CompoundTag blocksTag = schematic.getCompound("Blocks");
            if (blocksTag.contains("BlockEntities", Tag.TAG_LIST)) {
                return blocksTag.getList("BlockEntities", Tag.TAG_COMPOUND);
            }
        }
        if (root.contains("BlockEntities", Tag.TAG_LIST)) {
            return root.getList("BlockEntities", Tag.TAG_COMPOUND);
        }
        return null;
    }

    private static int[] resolveRelativePos(CompoundTag tag) {
        if (tag.contains("Pos", Tag.TAG_INT_ARRAY)) {
            return tag.getIntArray("Pos");
        }
        if (tag.contains("x", Tag.TAG_INT) && tag.contains("y", Tag.TAG_INT) && tag.contains("z", Tag.TAG_INT)) {
            return new int[] { tag.getInt("x"), tag.getInt("y"), tag.getInt("z") };
        }
        if (tag.contains("X", Tag.TAG_INT) && tag.contains("Y", Tag.TAG_INT) && tag.contains("Z", Tag.TAG_INT)) {
            return new int[] { tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z") };
        }
        return null;
    }
}
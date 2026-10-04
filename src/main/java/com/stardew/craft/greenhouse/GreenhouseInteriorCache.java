package com.stardew.craft.greenhouse;

import com.stardew.craft.StardewCraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.io.InputStream;

/**
 * 缓存温室室内 schem 的非空气占位，用于区分原始结构块与玩家后续放置方块。
 */
@SuppressWarnings("null")
public final class GreenhouseInteriorCache {

    private static GreenhouseInteriorCache instance;

    private final boolean[] nonAirMask;
    private final boolean[] plantingBedMask;
    private final int width;
    private final int height;
    private final int length;
    private final boolean loaded;

    private GreenhouseInteriorCache(boolean[] nonAirMask, boolean[] plantingBedMask, int width, int height, int length, boolean loaded) {
        this.nonAirMask = nonAirMask;
        this.plantingBedMask = plantingBedMask;
        this.width = width;
        this.height = height;
        this.length = length;
        this.loaded = loaded;
    }

    public static GreenhouseInteriorCache get() {
        if (instance == null || !instance.loaded) {
            instance = load();
        }
        return instance;
    }

    public static void invalidate() {
        instance = null;
    }

    public boolean isOriginalStructureBlock(int rx, int ry, int rz) {
        if (!loaded) {
            return false;
        }
        if (rx < 0 || rx >= width || ry < 0 || ry >= height || rz < 0 || rz >= length) {
            return false;
        }
        int index = ry * length * width + rz * width + rx;
        return nonAirMask[index];
    }

    /** Authored soil columns, independent of the current player's soil or crop state. */
    public boolean isPlantingBed(int rx, int rz) {
        return loaded && rx >= 0 && rx < width && rz >= 0 && rz < length
                && plantingBedMask[rz * width + rx];
    }

    private static GreenhouseInteriorCache load() {
        try (InputStream stream = GreenhouseInteriorCache.class.getClassLoader()
                .getResourceAsStream(GreenhouseManager.INTERIOR_STRUCTURE_PATH)) {
            if (stream == null) {
                StardewCraft.LOGGER.error("[GREENHOUSE] Interior schematic not found: {}",
                        GreenhouseManager.INTERIOR_STRUCTURE_PATH);
                return empty();
            }

            CompoundTag root = NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap());
            CompoundTag schematic = root;
            if ((readDim(root, "Width") == 0 || readDim(root, "Height") == 0 || readDim(root, "Length") == 0)
                    && root.contains("Schematic", Tag.TAG_COMPOUND)) {
                schematic = root.getCompound("Schematic");
            }

            int width = readDim(schematic, "Width");
            int height = readDim(schematic, "Height");
            int length = readDim(schematic, "Length");
            if (width <= 0 || height <= 0 || length <= 0) {
                StardewCraft.LOGGER.error("[GREENHOUSE] Invalid interior schematic size: {}x{}x{}",
                        width, height, length);
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

            if (paletteTag.isEmpty() || blockDataRaw.length == 0) {
                StardewCraft.LOGGER.error("[GREENHOUSE] Interior schematic palette or block data is empty");
                return empty();
            }

            int paletteMax = Math.max(1, schematic.getInt("PaletteMax"));
            boolean[] paletteNonAir = new boolean[Math.max(paletteMax, paletteTag.size()) + 1];
            boolean[] paletteSoil = new boolean[paletteNonAir.length];
            for (String stateString : paletteTag.getAllKeys()) {
                int paletteId = paletteTag.getInt(stateString);
                if (paletteId < 0 || paletteId >= paletteNonAir.length) {
                    continue;
                }
                var state = parseBlock(stateString).defaultBlockState();
                paletteNonAir[paletteId] = !state.isAir();
                paletteSoil[paletteId] = state.is(com.stardew.craft.block.ModBlocks.DIRT.get())
                        || com.stardew.craft.block.terrain.TerrainSoils.farmland(state);
            }

            int expected = width * height * length;
            int[] blockIndices = decodeVarIntArray(blockDataRaw, expected);
            boolean[] nonAirMask = new boolean[expected];
            boolean[] plantingBedMask = new boolean[width * length];
            for (int i = 0; i < expected; i++) {
                int paletteIndex = i < blockIndices.length ? blockIndices[i] : 0;
                if (i < plantingBedMask.length) plantingBedMask[i] = paletteIndex >= 0
                        && paletteIndex < paletteSoil.length && paletteSoil[paletteIndex];
                nonAirMask[i] = paletteIndex >= 0
                        && paletteIndex < paletteNonAir.length
                        && paletteNonAir[paletteIndex];
            }

            StardewCraft.LOGGER.info("[GREENHOUSE] Loaded interior cache: {}x{}x{}", width, height, length);
            return new GreenhouseInteriorCache(nonAirMask, plantingBedMask, width, height, length, true);
        } catch (Exception e) {
            StardewCraft.LOGGER.error("[GREENHOUSE] Failed to load interior cache: {}", e.getMessage(), e);
            return empty();
        }
    }

    private static GreenhouseInteriorCache empty() {
        return new GreenhouseInteriorCache(new boolean[0], new boolean[0], 0, 0, 0, false);
    }

    private static int readDim(CompoundTag tag, String key) {
        if (tag.contains(key, Tag.TAG_SHORT)) {
            return tag.getShort(key) & 0xFFFF;
        }
        if (tag.contains(key, Tag.TAG_INT)) {
            return tag.getInt(key);
        }
        return 0;
    }

    private static Block parseBlock(String stateString) {
        String blockId = stateString;
        int bracket = stateString.indexOf('[');
        if (bracket >= 0) {
            blockId = stateString.substring(0, bracket);
        }
        ResourceLocation id = ResourceLocation.tryParse(blockId);
        if (id == null) {
            return Blocks.AIR;
        }
        return BuiltInRegistries.BLOCK.get(id);
    }

    private static int[] decodeVarIntArray(byte[] data, int expected) {
        int[] result = new int[expected];
        int outIndex = 0;
        int byteIndex = 0;

        while (byteIndex < data.length && outIndex < expected) {
            int value = 0;
            int shift = 0;
            int current;
            do {
                if (byteIndex >= data.length) {
                    return result;
                }
                current = data[byteIndex++] & 0xFF;
                value |= (current & 0x7F) << shift;
                shift += 7;
            } while ((current & 0x80) != 0);
            result[outIndex++] = value;
        }
        return result;
    }
}
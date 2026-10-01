package com.stardew.craft.communitycenter.restore;

import com.stardew.craft.StardewCraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 缓存 community_center_refurbished.schem 的完整方块数据。
 * 在区域修复时，从缓存中取出指定范围的方块用于覆盖 ruins。
 * <p>
 * 方块数据以 3D 数组存储，索引为 schem 相对坐标 (x, y, z)。
 * BlockEntity NBT 以 Map&lt;相对坐标索引, CompoundTag&gt; 存储。
 */
@SuppressWarnings("null")
public final class CCRefurbishedCache {

    private static final String REFURBISHED_PATH = "data/stardewcraft/structures/interior/community_center_refurbished.schem";

    private static CCRefurbishedCache instance;

    private final BlockState[] blockData;
    private final Map<Integer, CompoundTag> blockEntityData; // key = ry*length*width + rz*width + rx
    private final int width, height, length;
    private boolean loaded = false;

    private CCRefurbishedCache(BlockState[] blockData, Map<Integer, CompoundTag> blockEntityData, int width, int height, int length) {
        this.blockData = blockData;
        this.blockEntityData = blockEntityData;
        this.width = width;
        this.height = height;
        this.length = length;
        this.loaded = true;
    }

    /**
     * 获取缓存实例，懒加载。
     */
    public static CCRefurbishedCache get() {
        if (instance == null || !instance.loaded) {
            instance = load();
        }
        return instance;
    }

    /** 强制重新加载 (用于 LAYOUT_VERSION 变更后) */
    public static void invalidate() {
        instance = null;
    }

    /**
     * 获取 schem 相对坐标处的方块状态。
     * @param rx 相对 X (0 ~ width-1)
     * @param ry 相对 Y (0 ~ height-1)
     * @param rz 相对 Z (0 ~ length-1)
     */
    @Nullable
    public BlockState getBlock(int rx, int ry, int rz) {
        if (!loaded) return null;
        if (rx < 0 || rx >= width || ry < 0 || ry >= height || rz < 0 || rz >= length) return null;
        int index = ry * length * width + rz * width + rx;
        return blockData[index];
    }

    /**
     * 获取 schem 相对坐标处的 BlockEntity NBT (如果有)。
     */
    @Nullable
    public CompoundTag getBlockEntityTag(int rx, int ry, int rz) {
        if (!loaded) return null;
        if (rx < 0 || rx >= width || ry < 0 || ry >= height || rz < 0 || rz >= length) return null;
        int index = ry * length * width + rz * width + rx;
        return blockEntityData.get(index);
    }

    public int getWidth()  { return width;  }
    public int getHeight() { return height; }
    public int getLength() { return length; }

    // ── 加载逻辑 (复用 StructureLoader 的 .schem 解析) ──

    private static CCRefurbishedCache load() {
        try (InputStream stream = CCRefurbishedCache.class.getClassLoader().getResourceAsStream(REFURBISHED_PATH)) {
            if (stream == null) {
                StardewCraft.LOGGER.error("[CC] Refurbished schematic not found: {}", REFURBISHED_PATH);
                return empty();
            }

            CompoundTag root = com.stardew.craft.port.PortNbtIo.readCompressed(stream, NbtAccounter.UNLIMITED);
            CompoundTag schematic = root;
            if ((readDim(root, "Width") == 0 || readDim(root, "Height") == 0 || readDim(root, "Length") == 0)
                    && root.contains("Schematic", Tag.TAG_COMPOUND)) {
                schematic = root.getCompound("Schematic");
            }

            int width = readDim(schematic, "Width");
            int height = readDim(schematic, "Height");
            int length = readDim(schematic, "Length");
            if (width <= 0 || height <= 0 || length <= 0) {
                StardewCraft.LOGGER.error("[CC] Invalid refurbished schematic size: {}x{}x{}", width, height, length);
                return empty();
            }

            CompoundTag paletteTag = schematic.getCompound("Palette");
            byte[] blockDataRaw = schematic.getByteArray("BlockData");

            if ((paletteTag.isEmpty() || blockDataRaw.length == 0) && schematic.contains("Blocks", Tag.TAG_COMPOUND)) {
                CompoundTag blocksTag = schematic.getCompound("Blocks");
                if (paletteTag.isEmpty()) paletteTag = blocksTag.getCompound("Palette");
                if (blockDataRaw.length == 0) blockDataRaw = blocksTag.getByteArray("Data");
            }

            if (paletteTag.isEmpty()) {
                StardewCraft.LOGGER.error("[CC] Refurbished schematic has empty palette");
                return empty();
            }

            int paletteMax = Math.max(1, schematic.getInt("PaletteMax"));
            BlockState[] paletteStates = new BlockState[Math.max(paletteMax, paletteTag.size()) + 1];
            for (String stateString : paletteTag.getAllKeys()) {
                int paletteId = paletteTag.getInt(stateString);
                if (paletteId >= 0 && paletteId < paletteStates.length) {
                    paletteStates[paletteId] = parseBlockState(stateString);
                }
            }

            int expected = width * height * length;
            int[] blockIndices = decodeVarIntArray(blockDataRaw, expected);

            BlockState[] data = new BlockState[expected];
            for (int i = 0; i < expected; i++) {
                int idx = i < blockIndices.length ? blockIndices[i] : 0;
                data[i] = (idx >= 0 && idx < paletteStates.length && paletteStates[idx] != null)
                        ? paletteStates[idx]
                        : Blocks.AIR.defaultBlockState();
            }

            StardewCraft.LOGGER.info("[CC] Loaded refurbished cache: {}x{}x{} ({} blocks)", width, height, length, expected);
            
            // ── 解析 BlockEntity 数据 ────────────────────────────────────
            Map<Integer, CompoundTag> beData = new HashMap<>();
            net.minecraft.nbt.ListTag blockEntities = findBlockEntityList(root, schematic);
            if (blockEntities != null) {
                for (int i = 0; i < blockEntities.size(); i++) {
                    CompoundTag raw = blockEntities.getCompound(i);
                    int[] rel = resolveRelativePos(raw);
                    if (rel == null || rel.length < 3) continue;
                    int rx = rel[0], ry = rel[1], rz = rel[2];
                    if (rx < 0 || rx >= width || ry < 0 || ry >= height || rz < 0 || rz >= length) continue;
                    int idx = ry * length * width + rz * width + rx;
                    beData.put(idx, raw.copy());
                }
                StardewCraft.LOGGER.info("[CC] Cached {} block entities from refurbished schematic", beData.size());
            }

            return new CCRefurbishedCache(data, beData, width, height, length);

        } catch (Exception e) {
            StardewCraft.LOGGER.error("[CC] Failed to load refurbished schematic: {}", e.getMessage(), e);
            return empty();
        }
    }

    private static CCRefurbishedCache empty() {
        return new CCRefurbishedCache(new BlockState[0], new HashMap<>(), 0, 0, 0);
    }

    private static int readDim(CompoundTag tag, String key) {
        if (tag.contains(key, Tag.TAG_SHORT)) return tag.getShort(key) & 0xFFFF;
        if (tag.contains(key, Tag.TAG_INT)) return tag.getInt(key);
        return 0;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState parseBlockState(String stateString) {
        String blockId = stateString;
        String propsPart = null;
        int bracket = stateString.indexOf('[');
        if (bracket >= 0 && stateString.endsWith("]")) {
            blockId = stateString.substring(0, bracket);
            propsPart = stateString.substring(bracket + 1, stateString.length() - 1);
        }

        ResourceLocation id = ResourceLocation.tryParse(blockId);
        if (id == null) return Blocks.AIR.defaultBlockState();

        Block block = BuiltInRegistries.BLOCK.get(id);
        BlockState state = block.defaultBlockState();
        if (propsPart == null || propsPart.isBlank()) return state;

        Map<String, String> values = new HashMap<>();
        for (String token : propsPart.split(",")) {
            String[] kv = token.split("=", 2);
            if (kv.length == 2) values.put(kv[0].trim(), kv[1].trim());
        }
        for (Property<?> property : state.getProperties()) {
            String value = values.get(property.getName());
            if (value != null) {
                var opt = property.getValue(value);
                if (opt.isPresent()) {
                    state = state.setValue((Property) property, (Comparable) opt.get());
                }
            }
        }
        return state;
    }

    private static int[] decodeVarIntArray(byte[] data, int expected) {
        int[] result = new int[expected];
        int outIdx = 0, i = 0;
        while (i < data.length && outIdx < expected) {
            int value = 0, shift = 0;
            int b;
            do {
                if (i >= data.length) break;
                b = data[i++] & 0xFF;
                value |= (b & 0x7F) << shift;
                shift += 7;
            } while ((b & 0x80) != 0);
            result[outIdx++] = value;
        }
        return result;
    }

    // ── BlockEntity 解析辅助 (与 StructureLoader 一致) ──────────────────

    @Nullable
    private static net.minecraft.nbt.ListTag findBlockEntityList(CompoundTag root, CompoundTag schematic) {
        if (schematic.contains("BlockEntities", Tag.TAG_LIST))
            return schematic.getList("BlockEntities", Tag.TAG_COMPOUND);
        if (schematic.contains("Blocks", Tag.TAG_COMPOUND)) {
            CompoundTag blocksTag = schematic.getCompound("Blocks");
            if (blocksTag.contains("BlockEntities", Tag.TAG_LIST))
                return blocksTag.getList("BlockEntities", Tag.TAG_COMPOUND);
        }
        if (root.contains("BlockEntities", Tag.TAG_LIST))
            return root.getList("BlockEntities", Tag.TAG_COMPOUND);
        return null;
    }

    @Nullable
    private static int[] resolveRelativePos(CompoundTag tag) {
        if (tag.contains("Pos", Tag.TAG_INT_ARRAY))
            return tag.getIntArray("Pos");
        if (tag.contains("x", Tag.TAG_INT) && tag.contains("y", Tag.TAG_INT) && tag.contains("z", Tag.TAG_INT))
            return new int[] { tag.getInt("x"), tag.getInt("y"), tag.getInt("z") };
        if (tag.contains("X", Tag.TAG_INT) && tag.contains("Y", Tag.TAG_INT) && tag.contains("Z", Tag.TAG_INT))
            return new int[] { tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z") };
        return null;
    }
}

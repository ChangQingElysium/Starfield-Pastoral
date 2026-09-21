package com.stardew.craft.tree.prefab;

import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.io.InputStream;
import java.util.List;

import org.slf4j.Logger;

/**
 * Reads vanilla structure NBT block contents for the prefab-tree layer.
 *
 * <p>This is intentionally only the non-placing reader slice from the
 * NeoForge structure loader. Placement, schematic decoding and the prefab-tree
 * manager remain outside the Forge migration boundary.</p>
 */
public final class ForgeTreeStructureReader {
	private static final Logger LOGGER = LogUtils.getLogger();

	private ForgeTreeStructureReader() {
	}

	/** A structure cell: an offset from the structure origin and its block state. */
	public record PositionedState(int dx, int dy, int dz, BlockState state) {
	}

	/** Parsed structure contents containing only non-air blocks. */
	public record StructureBlocks(int width, int height, int length, List<PositionedState> states) {
	}

	/**
	 * Reads the block contents of a vanilla NBT structure (the structure-block
	 * export format) without placing it. Offsets are relative to the structure's
	 * own origin ({@code 0,0,0}); air is filtered out.
	 *
	 * @param structurePath resource path relative to the class path, such as
	 *                      {@code data/stardewcraft/structures/tree/1_3.nbt}
	 * @return parsed contents, or {@code null} when the resource or structure is
	 *         invalid
	 */
	public static StructureBlocks readStructureNbtBlocks(String structurePath) {
		if (structurePath == null) {
			return null;
		}
		try (InputStream stream = ForgeTreeStructureReader.class.getClassLoader()
				.getResourceAsStream(structurePath)) {
			if (stream == null) {
				return null;
			}
			CompoundTag root = NbtIo.readCompressed(stream);

			// size: [x, y, z]
			ListTag sizeTag = root.getList("size", Tag.TAG_INT);
			int width = sizeTag.size() > 0 ? sizeTag.getInt(0) : 0;
			int height = sizeTag.size() > 1 ? sizeTag.getInt(1) : 0;
			int length = sizeTag.size() > 2 ? sizeTag.getInt(2) : 0;

			// palette (single palette) or palettes (multiple palettes, use the first)
			ListTag paletteTag = root.getList("palette", Tag.TAG_COMPOUND);
			if (paletteTag.isEmpty() && root.contains("palettes", Tag.TAG_LIST)) {
				ListTag palettes = root.getList("palettes", Tag.TAG_LIST);
				if (!palettes.isEmpty()) {
					paletteTag = palettes.getList(0);
				}
			}
			if (paletteTag.isEmpty()) {
				LOGGER.error("Structure {} has empty palette", structurePath);
				return null;
			}

			HolderGetter<Block> lookup = BuiltInRegistries.BLOCK.asLookup();
			BlockState[] paletteStates = new BlockState[paletteTag.size()];
			for (int i = 0; i < paletteTag.size(); i++) {
				paletteStates[i] = NbtUtils.readBlockState(lookup, paletteTag.getCompound(i));
			}

			ListTag blocks = root.getList("blocks", Tag.TAG_COMPOUND);
			java.util.List<PositionedState> states = new java.util.ArrayList<>(blocks.size());
			int maxX = 0;
			int maxY = 0;
			int maxZ = 0;
			for (int i = 0; i < blocks.size(); i++) {
				CompoundTag b = blocks.getCompound(i);
				int paletteIndex = b.getInt("state");
				if (paletteIndex < 0 || paletteIndex >= paletteStates.length) {
					continue;
				}
				BlockState state = paletteStates[paletteIndex];
				if (state == null || state.isAir()) {
					continue;
				}
				ListTag pos = b.getList("pos", Tag.TAG_INT);
				if (pos.size() < 3) {
					continue;
				}
				int x = pos.getInt(0);
				int y = pos.getInt(1);
				int z = pos.getInt(2);
				maxX = Math.max(maxX, x);
				maxY = Math.max(maxY, y);
				maxZ = Math.max(maxZ, z);
				states.add(new PositionedState(x, y, z, state));
			}
			if (states.isEmpty()) {
				LOGGER.error("Structure {} has no non-air blocks", structurePath);
				return null;
			}
			if (width <= 0) width = maxX + 1;
			if (height <= 0) height = maxY + 1;
			if (length <= 0) length = maxZ + 1;
			return new StructureBlocks(width, height, length, states);
		} catch (Exception e) {
			LOGGER.error("Failed to read structure blocks {}: {}", structurePath, e.getMessage(), e);
			return null;
		}
	}
}

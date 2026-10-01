package com.stardew.craft.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public class NewTreePartBlockEntity extends BlockEntity {
	private static final String TAG_TREE_ID = "StardewGeneratedTreeId";
	private static final String TAG_TREE_SPECIES = "StardewGeneratedTreeSpecies";
	private static final String TAG_TREE_ROOT = "StardewGeneratedTreeRoot";

	private UUID generatedTreeId;
	private String generatedTreeSpecies;
	private BlockPos generatedTreeRoot;

	public NewTreePartBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.NEW_TREE_PART.get(), pos, state);
	}

	public boolean hasGeneratedTreeMarker() {
		return generatedTreeId != null
				&& generatedTreeSpecies != null
				&& !generatedTreeSpecies.isBlank()
				&& generatedTreeRoot != null;
	}

	public UUID getGeneratedTreeId() {
		return generatedTreeId;
	}

	public String getGeneratedTreeSpecies() {
		return generatedTreeSpecies;
	}

	public BlockPos getGeneratedTreeRoot() {
		return generatedTreeRoot;
	}

	public void markGeneratedTree(UUID treeId, String species, BlockPos root) {
		if (treeId == null || species == null || species.isBlank() || root == null) {
			return;
		}
		this.generatedTreeId = treeId;
		this.generatedTreeSpecies = species;
		this.generatedTreeRoot = root.immutable();
		setChanged();
	}

	public void clearGeneratedTreeMarker() {
		if (!hasGeneratedTreeMarker()) {
			return;
		}
		this.generatedTreeId = null;
		this.generatedTreeSpecies = null;
		this.generatedTreeRoot = null;
		setChanged();
	}

	@Override
	protected void saveAdditional(CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
		super.saveAdditional(tag);
		if (hasGeneratedTreeMarker()) {
			tag.put(TAG_TREE_ID, NbtUtils.createUUID(generatedTreeId));
			tag.putString(TAG_TREE_SPECIES, generatedTreeSpecies);
			tag.put(TAG_TREE_ROOT, com.stardew.craft.port.PortNbtUtils.writeBlockPos(generatedTreeRoot));
		}
	}

	@Override
	public void load(CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
		super.load(tag);
		generatedTreeId = tag.contains(TAG_TREE_ID, Tag.TAG_INT_ARRAY) ? NbtUtils.loadUUID(tag.get(TAG_TREE_ID)) : null;
		generatedTreeSpecies = tag.contains(TAG_TREE_SPECIES, Tag.TAG_STRING) ? tag.getString(TAG_TREE_SPECIES) : null;
		generatedTreeRoot = tag.contains(TAG_TREE_ROOT, Tag.TAG_COMPOUND)
				? com.stardew.craft.port.PortNbtUtils.readBlockPos(tag, TAG_TREE_ROOT).orElse(null)
				: null;
	}
}

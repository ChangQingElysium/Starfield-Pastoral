package com.stardew.craft.festival;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.portal.PortalTriggerBlock;
import com.stardew.craft.tree.prefab.PrefabTreeInstance;
import com.stardew.craft.tree.prefab.PrefabTreeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record FestivalMapPatch(
    String overlayId,
    BlockPos origin,
    int width,
    int height,
    int length,
    List<FestivalMapPatchEntry> entries
) {
    private static final int CONNECTED_TREE_HORIZONTAL_RADIUS = 16;
    private static final int CONNECTED_TREE_VERTICAL_RADIUS = 64;

    public static FestivalMapPatch buildFromRuntimeBase(ServerLevel level, FestivalMapOverlayDefinition definition) {
        SchematicSnapshot festival = SchematicSnapshot.load(definition.festivalSchematicPath());
        if (festival.isEmpty()) {
            return null;
        }
        if (!matchesBounds(definition, festival)) {
            return null;
        }

        Map<BlockPos, PendingPatchEntry> pendingEntries = new LinkedHashMap<>();
        for (int localY = 0; localY < festival.height(); localY++) {
            for (int localZ = 0; localZ < festival.length(); localZ++) {
                for (int localX = 0; localX < festival.width(); localX++) {
                    BlockPos worldPos = definition.origin().offset(localX, localY, localZ);
                    BlockState baseState = level.getBlockState(worldPos);
                    if (baseState.getBlock() instanceof PortalTriggerBlock) {
                        continue;
                    }
                    BlockState festivalState = festival.block(localX, localY, localZ);
                    CompoundTag baseBlockEntity = saveBlockEntity(level, worldPos);
                    CompoundTag festivalBlockEntity = festival.blockEntity(localX, localY, localZ);
                    if (!Objects.equals(baseState, festivalState) || !tagEquals(baseBlockEntity, festivalBlockEntity)) {
                        addPending(pendingEntries, new PendingPatchEntry(
                            worldPos,
                            baseState,
                            festivalState,
                            copyOrNull(baseBlockEntity),
                            copyOrNull(festivalBlockEntity)
                        ));
                    }
                }
            }
        }

        Set<BlockPos> treeClearancePositions = collectTreeClearancePositions(level, definition);
        for (BlockPos worldPos : treeClearancePositions) {
            if (pendingEntries.containsKey(worldPos)) {
                continue;
            }
            BlockState baseState = level.getBlockState(worldPos);
            if (baseState.isAir() || baseState.getBlock() instanceof PortalTriggerBlock) {
                continue;
            }
            addPending(pendingEntries, new PendingPatchEntry(
                worldPos,
                baseState,
                Blocks.AIR.defaultBlockState(),
                copyOrNull(saveBlockEntity(level, worldPos)),
                null
            ));
        }

        PatchBounds patchBounds = runtimeBounds(definition.origin(), festival, pendingEntries.keySet());
        List<FestivalMapPatchEntry> entries = new ArrayList<>(pendingEntries.size());
        for (PendingPatchEntry pending : pendingEntries.values()) {
            entries.add(pending.toEntry(patchBounds.origin()));
        }
        StardewCraft.LOGGER.info("[FESTIVAL_OVERLAY] Built runtime patch {} with {} changed blocks ({} tree-clearance blocks)",
            definition.overlayId(), entries.size(), treeClearancePositions.size());
        return new FestivalMapPatch(definition.overlayId(), patchBounds.origin(), patchBounds.width(), patchBounds.height(), patchBounds.length(), List.copyOf(entries));
    }

    public static FestivalMapPatch build(FestivalMapOverlayDefinition definition) {
        SchematicSnapshot base = SchematicSnapshot.load(definition.baseSchematicPath());
        SchematicSnapshot festival = SchematicSnapshot.load(definition.festivalSchematicPath());
        if (!base.hasSameSize(festival)) {
            StardewCraft.LOGGER.error("[FESTIVAL_OVERLAY] Cannot build patch {}: base/festival size mismatch ({}x{}x{} vs {}x{}x{})",
                definition.overlayId(), base.width(), base.height(), base.length(), festival.width(), festival.height(), festival.length());
            return new FestivalMapPatch(definition.overlayId(), definition.origin(), 0, 0, 0, List.of());
        }

        List<FestivalMapPatchEntry> entries = new ArrayList<>();
        for (int localY = 0; localY < base.height(); localY++) {
            for (int localZ = 0; localZ < base.length(); localZ++) {
                for (int localX = 0; localX < base.width(); localX++) {
                    BlockState baseState = base.block(localX, localY, localZ);
                    BlockState festivalState = festival.block(localX, localY, localZ);
                    CompoundTag baseBlockEntity = base.blockEntity(localX, localY, localZ);
                    CompoundTag festivalBlockEntity = festival.blockEntity(localX, localY, localZ);
                    if (!Objects.equals(baseState, festivalState) || !tagEquals(baseBlockEntity, festivalBlockEntity)) {
                        entries.add(new FestivalMapPatchEntry(
                            new BlockPos(localX, localY, localZ),
                            baseState,
                            festivalState,
                            copyOrNull(baseBlockEntity),
                            copyOrNull(festivalBlockEntity)
                        ));
                    }
                }
            }
        }
        StardewCraft.LOGGER.info("[FESTIVAL_OVERLAY] Built patch {} with {} changed blocks", definition.overlayId(), entries.size());
        return new FestivalMapPatch(definition.overlayId(), definition.origin(), base.width(), base.height(), base.length(), List.copyOf(entries));
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("OverlayId", overlayId);
        tag.putIntArray("Origin", new int[] { origin.getX(), origin.getY(), origin.getZ() });
        tag.putInt("Width", width);
        tag.putInt("Height", height);
        tag.putInt("Length", length);
        ListTag entryList = new ListTag();
        for (FestivalMapPatchEntry entry : entries) {
            entryList.add(entry.save());
        }
        tag.put("Entries", entryList);
        return tag;
    }

    static FestivalMapPatch load(CompoundTag tag) {
        int[] originArray = tag.getIntArray("Origin");
        BlockPos origin = originArray.length >= 3 ? new BlockPos(originArray[0], originArray[1], originArray[2]) : BlockPos.ZERO;
        List<FestivalMapPatchEntry> entries = new ArrayList<>();
        ListTag entryList = tag.getList("Entries", Tag.TAG_COMPOUND);
        for (int index = 0; index < entryList.size(); index++) {
            entries.add(FestivalMapPatchEntry.load(entryList.getCompound(index)));
        }
        return new FestivalMapPatch(
            tag.getString("OverlayId"),
            origin,
            tag.getInt("Width"),
            tag.getInt("Height"),
            tag.getInt("Length"),
            List.copyOf(entries)
        );
    }

    private static boolean tagEquals(CompoundTag first, CompoundTag second) {
        if (first == null || first.isEmpty()) {
            return second == null || second.isEmpty();
        }
        return first.equals(second);
    }

    private static CompoundTag copyOrNull(CompoundTag tag) {
        return tag == null || tag.isEmpty() ? null : tag.copy();
    }

    private static boolean matchesBounds(FestivalMapOverlayDefinition definition, SchematicSnapshot snapshot) {
        int expectedWidth = Math.abs(definition.boundsMax().getX() - definition.boundsMin().getX()) + 1;
        int expectedHeight = Math.abs(definition.boundsMax().getY() - definition.boundsMin().getY()) + 1;
        int expectedLength = Math.abs(definition.boundsMax().getZ() - definition.boundsMin().getZ()) + 1;
        if (expectedWidth == snapshot.width() && expectedHeight == snapshot.height() && expectedLength == snapshot.length()) {
            return true;
        }
        StardewCraft.LOGGER.error("[FESTIVAL_OVERLAY] Schematic {} size mismatch for {}: expected {}x{}x{}, got {}x{}x{}",
            definition.festivalSchematicPath(), definition.overlayId(), expectedWidth, expectedHeight, expectedLength,
            snapshot.width(), snapshot.height(), snapshot.length());
        return false;
    }

    private static CompoundTag saveBlockEntity(ServerLevel level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity == null ? null : blockEntity.saveWithFullMetadata();
    }

    private static void addPending(Map<BlockPos, PendingPatchEntry> pendingEntries, PendingPatchEntry entry) {
        pendingEntries.put(entry.worldPos().immutable(), entry.withImmutablePos());
    }

    private static Set<BlockPos> collectTreeClearancePositions(ServerLevel level, FestivalMapOverlayDefinition definition) {
        FestivalMapOverlayDefinition.TreeClearance clearance = definition.treeClearance();
        if (clearance == null || !clearance.enabled()) {
            return Set.of();
        }

        BlockPos boundsMin = min(definition.boundsMin(), definition.boundsMax());
        BlockPos boundsMax = max(definition.boundsMin(), definition.boundsMax());
        int minX = boundsMin.getX() - clearance.horizontalRadius();
        int maxX = boundsMax.getX() + clearance.horizontalRadius();
        int minY = boundsMin.getY() - clearance.down();
        int maxY = boundsMax.getY() + clearance.up();
        int minZ = boundsMin.getZ() - clearance.horizontalRadius();
        int maxZ = boundsMax.getZ() + clearance.horizontalRadius();

        PrefabTreeRegistry prefabRegistry = PrefabTreeRegistry.get(level);
        Set<BlockPos> positions = new HashSet<>();
        for (BlockPos cursor : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            BlockPos pos = cursor.immutable();
            if (positions.contains(pos)) {
                continue;
            }
            PrefabTreeInstance prefab = prefabRegistry.getByMember(pos);
            if (prefab != null && !prefab.felled()) {
                addNonAirMembers(level, prefab.members(), positions);
                continue;
            }

        }
        return positions;
    }

    private static boolean withinTreeFloodBounds(BlockPos start, BlockPos pos) {
        return Math.abs(pos.getX() - start.getX()) <= CONNECTED_TREE_HORIZONTAL_RADIUS
            && Math.abs(pos.getZ() - start.getZ()) <= CONNECTED_TREE_HORIZONTAL_RADIUS
            && Math.abs(pos.getY() - start.getY()) <= CONNECTED_TREE_VERTICAL_RADIUS;
    }

    private static void addNonAirMembers(ServerLevel level, Collection<BlockPos> source, Set<BlockPos> target) {
        for (BlockPos pos : source) {
            if (pos == null) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && !(state.getBlock() instanceof PortalTriggerBlock)) {
                target.add(pos.immutable());
            }
        }
    }

    private static PatchBounds runtimeBounds(BlockPos origin, SchematicSnapshot festival, Collection<BlockPos> extraWorldPositions) {
        int minX = origin.getX();
        int minY = origin.getY();
        int minZ = origin.getZ();
        int maxX = origin.getX() + festival.width() - 1;
        int maxY = origin.getY() + festival.height() - 1;
        int maxZ = origin.getZ() + festival.length() - 1;
        for (BlockPos pos : extraWorldPositions) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return new PatchBounds(new BlockPos(minX, minY, minZ), maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
    }

    private static BlockPos min(BlockPos first, BlockPos second) {
        return new BlockPos(
            Math.min(first.getX(), second.getX()),
            Math.min(first.getY(), second.getY()),
            Math.min(first.getZ(), second.getZ())
        );
    }

    private static BlockPos max(BlockPos first, BlockPos second) {
        return new BlockPos(
            Math.max(first.getX(), second.getX()),
            Math.max(first.getY(), second.getY()),
            Math.max(first.getZ(), second.getZ())
        );
    }

    private record PendingPatchEntry(
        BlockPos worldPos,
        BlockState baseState,
        BlockState festivalState,
        CompoundTag baseBlockEntityTag,
        CompoundTag festivalBlockEntityTag
    ) {
        PendingPatchEntry withImmutablePos() {
            return new PendingPatchEntry(worldPos.immutable(), baseState, festivalState, baseBlockEntityTag, festivalBlockEntityTag);
        }

        FestivalMapPatchEntry toEntry(BlockPos patchOrigin) {
            return new FestivalMapPatchEntry(
                new BlockPos(
                    worldPos.getX() - patchOrigin.getX(),
                    worldPos.getY() - patchOrigin.getY(),
                    worldPos.getZ() - patchOrigin.getZ()
                ),
                baseState,
                festivalState,
                baseBlockEntityTag,
                festivalBlockEntityTag
            );
        }
    }

    private record PatchBounds(BlockPos origin, int width, int height, int length) {
    }
}

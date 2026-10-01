package com.stardew.craft.building.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Durable desired world state for one upgrade or translation. No inventory drops during projection. */
public record BuildingTransfer(BuildingRecord before, BuildingRecord after, List<Cell> contents, List<BlockPos> removed, CompoundTag extras) {
    public record Cell(BlockPos pos, BlockState state, CompoundTag data) {}
    public BuildingTransfer {
        if (!before.id().equals(after.id()) || !before.farmId().equals(after.farmId()) || before.farmSlot() != after.farmSlot()
                || !before.family().equals(after.family()) || !before.dimension().equals(after.dimension())
                || after.mode() != before.mode() || after.phase() != BuildingRecord.Phase.READY
                || after.revision() != before.revision() + 1 || after.tier() < before.tier() || after.tier() > before.tier() + 1) throw new IllegalArgumentException("Invalid building transfer identity");
        java.util.Set<BlockPos> positions = new java.util.HashSet<>();
        for (Cell cell : contents) if (!after.claim().contains(cell.pos()) || !positions.add(cell.pos())) throw new IllegalArgumentException("Invalid transfer cell");
        contents = List.copyOf(contents); removed = List.copyOf(removed); extras = extras.copy();
    }

    public static BuildingTransfer move(ServerLevel level, BuildingRecord record, BlockPos anchor) {
        return move(level, record, anchor, record.facing());
    }
    public static BuildingTransfer move(ServerLevel level, BuildingRecord record, BlockPos anchor, net.minecraft.core.Direction facing) {
        var oldRotation = PrefabDefinitions.rotation(record.facing());
        var newRotation = PrefabDefinitions.rotation(facing);
        var deltaRotation = newRotation.getRotated(PrefabDefinitions.inverse(oldRotation));
        java.util.function.Function<BlockPos, BlockPos> destination = pos -> record.mode() == BuildingRecord.Mode.SELF_BUILT ? anchor.offset(pos.subtract(record.anchor()).rotate(deltaRotation)) : anchor.offset(PrefabDefinitions.rotateCell(
                PrefabDefinitions.rotateCell(pos.subtract(record.anchor()), PrefabDefinitions.inverse(oldRotation)), newRotation));
        BuildingRecord target = new BuildingRecord(record.id(), record.farmId(), record.farmSlot(), record.family(), record.mode(),
                record.dimension(), anchor, destination.apply(record.manager()), facing,
                record.mode() == BuildingRecord.Mode.SELF_BUILT ? UtilityBuildings.moveBounds(record, anchor, facing) : PrefabDefinitions.transform(PrefabDefinitions.get(record.family()).reservation(), anchor, newRotation),
                BuildingRecord.Phase.READY, record.tier(), record.residence(), record.revision() + 1, record.displayName());
        List<Cell> contents = new ArrayList<>();
        for (BlockPos pos : sourcePositions(level, record)) {
            Cell cell = capture(level, pos);
            for(var box:cell.state().getShape(level,pos).toAabbs()) {
                var worldBox=box.move(pos);var scope=BuildingPlacementService.aabb(contentBounds(record)).inflate(.001);
                if(!scope.contains(worldBox.minX,worldBox.minY,worldBox.minZ) || !scope.contains(worldBox.maxX,worldBox.maxY,worldBox.maxZ))throw new Collision(pos.immutable());
            }
            if (!cell.state().isAir()) contents.add(new Cell(destination.apply(pos), cell.state().rotate(deltaRotation), cell.data()));
        }
        return new BuildingTransfer(record, target, List.copyOf(contents), sourcePositions(level, record), BuildingTransferExtras.capture(level, record, target));
    }

    public static BuildingTransfer upgrade(ServerLevel level, BuildingRecord record) {
        var oldNative = nativeCells(level, record, record.tier());
        var nextNative = nativeCells(level, record, record.tier() + 1);
        Map<BlockPos, Cell> contents = new LinkedHashMap<>(nextNative);
        var current = contentBounds(record);
        var retainedGround = PrefabDefinitions.retainedGround(level, record);
        var targetBounds=PrefabDefinitions.transform(PrefabDefinitions.get(record.family()).tier(record.tier()+1).bounds(),record.anchor(),PrefabDefinitions.rotation(record.facing()));
        for (BlockPos pos : BlockPos.betweenClosed(record.claim().min(), record.claim().maxInclusive())) {
            if (oldNative.containsKey(pos)) continue;
            var state = level.getBlockState(pos);
            if (state.isAir() || state.is(com.stardew.craft.block.ModBlocks.UPGRADE_NOTICE.get())) continue;
            if(pos.getY()==record.claim().min().getY() && (!current.contains(pos) || retainedGround.contains(pos))
                    && state.isCollisionShapeFullBlock(level,pos) && state.getFluidState().isEmpty() && level.getBlockEntity(pos)==null) continue;
            if (current.contains(pos) || targetBounds.contains(pos)) throw new Collision(pos.immutable());
        }
        BuildingTransferExtras.requireUpgradeClear(level, record);
        // Pair native functional block entities by type and nearest anchor-relative position.
        // A used facility with no matching destination blocks completion, rather than losing its data.
        var available = new java.util.LinkedHashSet<>(nextNative.keySet());
        for (var entry : oldNative.entrySet()) {
            Cell actual = capture(level, entry.getKey());
            if (actual.data() == null) continue;
            var sourceEntity = level.getBlockEntity(entry.getKey());
            // Only carry runtime changes. Empty decorative containers and authored sign text belong
            // to the new template; compare against an identically initialized native block entity.
            if (entry.getValue().state().getBlock() instanceof net.minecraft.world.level.block.EntityBlock block) {
                var baseline = block.newBlockEntity(entry.getKey(), entry.getValue().state());
                if (baseline != null) {
                    baseline.setLevel(level);
                    if (entry.getValue().data() != null) baseline.loadWithComponents(entry.getValue().data().copy(), level.registryAccess());
                    if (actual.data().equals(baseline.saveWithFullMetadata())) continue;
                }
            }
            BlockPos destination = available.stream().filter(pos -> nextNative.get(pos).state().getBlock() instanceof net.minecraft.world.level.block.EntityBlock entityBlock
                    && entityBlock.newBlockEntity(pos, nextNative.get(pos).state()) != null).filter(pos -> com.stardew.craft.api.v1.agriculture.StardewAnimalFacilities.canUpgrade(actual.state().getBlock(),nextNative.get(pos).state().getBlock()))
                    .min(java.util.Comparator.comparingDouble(pos -> pos.distSqr(entry.getKey()))).orElse(null);
            if (destination == null && sourceEntity instanceof net.minecraft.world.Container container && container.isEmpty()
                    && !actual.data().contains("LootTable")) continue;
            if (destination == null) throw new Collision(entry.getKey());
            available.remove(destination);
            // Geometry/facing comes from the target template; dynamic block-entity data is carried over.
            BlockState state = nextNative.get(destination).state();
            for (var property : actual.state().getProperties()) if (java.util.Set.of("full", "has_hay", "honey_level", "lit").contains(property.getName())) state = carryProperty(state, actual.state(), property);
            contents.put(destination, new Cell(destination, state, com.stardew.craft.api.v1.agriculture.StardewAnimalFacilities.upgrade(actual.state().getBlock(),state.getBlock(),actual.data())));
        }
        BuildingRecord target = new BuildingRecord(record.id(), record.farmId(), record.farmSlot(), record.family(), record.mode(),
                record.dimension(), record.anchor(), record.manager(), record.facing(), record.claim(), BuildingRecord.Phase.READY,
                record.tier() + 1, BuildingRecord.Residence.VALID, record.revision() + 1, record.displayName());
        // A terrain placeholder in the next tier must not erase an existing solid floor either.
        var nextGround = PrefabDefinitions.retainedGround(level, target);
        var removed = sourcePositions(level, record).stream()
                .filter(pos -> !nextGround.contains(pos) || !isGround(level, pos)).toList();
        return new BuildingTransfer(record, target, List.copyOf(contents.values()), removed, BuildingTransferExtras.capture(level, record, target));
    }

    public static BuildingBounds contentBounds(BuildingRecord record) {
        return record.mode() == BuildingRecord.Mode.SELF_BUILT ? record.claim() : PrefabDefinitions.transform(
                PrefabDefinitions.get(record.family()).tier(record.tier()).bounds(), record.anchor(), PrefabDefinitions.rotation(record.facing()));
    }
    public static List<BlockPos> sourcePositions(ServerLevel level, BuildingRecord record) {
        var result = new ArrayList<BlockPos>();
        var bounds = contentBounds(record);
        var retainedGround = PrefabDefinitions.retainedGround(level, record);
        for (BlockPos pos : BlockPos.betweenClosed(bounds.min(), bounds.maxInclusive())) {
            if (retainedGround.contains(pos) && isGround(level, pos)) continue;
            if (!level.getBlockState(pos).isAir()) result.add(pos.immutable());
        }
        if (record.phase() == BuildingRecord.Phase.UPGRADING) for (BlockPos pos : BlockPos.betweenClosed(record.claim().min(), record.claim().maxInclusive()))
            if (level.getBlockState(pos).is(com.stardew.craft.block.ModBlocks.UPGRADE_NOTICE.get()) && !result.contains(pos)) result.add(pos.immutable());
        return result;
    }
    static boolean isGround(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.getFluidState().isEmpty() && state.isCollisionShapeFullBlock(level, pos)
                && level.getBlockEntity(pos) == null;
    }

    public static BlockPos destination(BuildingRecord before, BuildingRecord after, BlockPos pos) {
        if (before.mode() == BuildingRecord.Mode.SELF_BUILT) {
            var delta = PrefabDefinitions.rotation(after.facing()).getRotated(PrefabDefinitions.inverse(PrefabDefinitions.rotation(before.facing())));
            return after.anchor().offset(pos.subtract(before.anchor()).rotate(delta));
        }
        return after.anchor().offset(PrefabDefinitions.rotateCell(PrefabDefinitions.rotateCell(pos.subtract(before.anchor()),
                PrefabDefinitions.inverse(PrefabDefinitions.rotation(before.facing()))), PrefabDefinitions.rotation(after.facing())));
    }

    public static Map<BlockPos, Cell> nativeCells(ServerLevel level, BuildingRecord record, int number) {
        var tier = PrefabDefinitions.get(record.family()).tier(number);
        var rotation = PrefabDefinitions.rotation(record.facing());
        Map<BlockPos, Cell> result = new LinkedHashMap<>();
        for (var cell : PrefabDefinitions.template(level, tier).cells()) if (!cell.state().isAir()) {
            BlockPos pos = PrefabDefinitions.world(cell.pos(), tier.anchor(), record.anchor(), rotation);
            result.put(pos, new Cell(pos, cell.state().rotate(rotation), cell.blockEntity()));
        }
        return result;
    }

    private static <T extends Comparable<T>> BlockState carryProperty(BlockState target, BlockState source, net.minecraft.world.level.block.state.properties.Property<T> property) {
        return target.hasProperty(property) ? target.setValue(property, source.getValue(property)) : target;
    }
    private static Cell capture(ServerLevel level, BlockPos pos) {
        var entity = level.getBlockEntity(pos);
        return new Cell(pos.immutable(), level.getBlockState(pos), entity == null ? null : entity.saveWithFullMetadata());
    }

    public void project(ServerLevel level) {
        BuildingProtection.transfer(() -> {
            // Remove block entities before their blocks so containers/facilities cannot drop duplicates.
            BuildingTransferExtras.clear(level, extras);
            java.util.Set<BlockPos> clear = new java.util.LinkedHashSet<>(removed);
            contents.forEach(cell -> clear.add(cell.pos()));
            for (BlockPos pos : clear) {
                level.removeBlockEntity(pos);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
            }
            for (Cell cell : contents) {
                level.setBlock(cell.pos(), cell.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                if (cell.data() != null && level.getBlockEntity(cell.pos()) != null) {
                    CompoundTag tag = cell.data().copy();
                    tag.putInt("x", cell.pos().getX()); tag.putInt("y", cell.pos().getY()); tag.putInt("z", cell.pos().getZ());
                    var entity = level.getBlockEntity(cell.pos());
                    entity.load(tag); entity.setChanged();
                }
            }
            BuildingTransferExtras.project(level, before, after, extras);
            for (Cell cell : contents) level.updateNeighborsAt(cell.pos(), cell.state().getBlock());
        });
    }

    /** Hide the captured source without changing its durable building record. */
    public void lift(ServerLevel level) {
        BuildingProtection.transfer(() -> {
            BuildingTransferExtras.clear(level, extras);
            for (BlockPos pos : removed) {
                level.removeBlockEntity(pos);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
                                | Block.UPDATE_SUPPRESS_DROPS);
            }
        });
    }

    /** Restore a lifted source exactly, including air cells that may have filled while hidden. */
    public void restoreLift(ServerLevel level) {
        BuildingProtection.transfer(() -> {
            var retained = before.mode() == BuildingRecord.Mode.PREFAB
                    ? PrefabDefinitions.retainedGround(level, before) : java.util.Set.<BlockPos>of();
            var bounds = contentBounds(before);
            for (BlockPos pos : BlockPos.betweenClosed(bounds.min(), bounds.maxInclusive())) {
                if (retained.contains(pos)) continue;
                level.removeBlockEntity(pos);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
                                | Block.UPDATE_SUPPRESS_DROPS);
            }
        });
        project(level);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag(); tag.put("Before", before.save()); tag.put("After", after.save());
        ListTag list = new ListTag();
        for (Cell cell : contents) {
            CompoundTag value = new CompoundTag(); value.putLong("Pos", cell.pos().asLong()); value.put("State", NbtUtils.writeBlockState(cell.state()));
            if (cell.data() != null) value.put("Data", cell.data().copy()); list.add(value);
        }
        tag.put("Contents", list); tag.putLongArray("Removed", removed.stream().mapToLong(BlockPos::asLong).toArray()); tag.put("Extras", extras.copy()); return tag;
    }
    public static BuildingTransfer load(CompoundTag tag, HolderLookup.Provider registries) {
        List<Cell> cells = new ArrayList<>();
        for (var entry : tag.getList("Contents", 10)) {
            CompoundTag cell = (CompoundTag) entry;
            cells.add(new Cell(BlockPos.of(cell.getLong("Pos")), NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), cell.getCompound("State")),
                    cell.contains("Data", 10) ? cell.getCompound("Data").copy() : null));
        }
        return new BuildingTransfer(BuildingRecord.load(tag.getCompound("Before")), BuildingRecord.load(tag.getCompound("After")), List.copyOf(cells), java.util.Arrays.stream(tag.getLongArray("Removed")).mapToObj(BlockPos::of).toList(), tag.getCompound("Extras"));
    }
    public static final class Collision extends RuntimeException {
        public final BlockPos pos;
        public Collision(BlockPos pos) { super("Building transfer conflict at " + pos); this.pos = pos; }
    }
}

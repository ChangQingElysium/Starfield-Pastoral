package com.stardew.craft.building.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;

/** Non-residential farm services share identity/claims, not the coop facility contract. */
public final class UtilityBuildings {
    public static final ResourceLocation SILO = new ResourceLocation("stardewcraft:silo");
    public static final int SILO_CAPACITY = 240;
    private UtilityBuildings() {}
    public static boolean supported(ResourceLocation family) { return SILO.equals(family); }
    public static boolean managed(ResourceLocation family) { return supported(family) || PrefabDefinitions.supported(family); }
    /** All 2x2x10 columns containing the manager, including a manager above the base. */
    public static BuildingBounds bounds(ResourceLocation family, BlockPos manager) {
        return supported(family) ? new BuildingBounds(manager.offset(-1, -9, -1), manager.offset(2, 10, 2)) : PrefabDefinitions.get(family).selfBounds(manager);
    }
    public record SiloAssessment(boolean loaded, int bricks, BuildingBounds column) {
        public boolean valid() { return loaded && bricks == 39 && column != null; }
    }
    public static SiloAssessment scanSilo(ServerLevel level, BlockPos manager, BuildingBounds scope) {
        if (!level.hasChunksAt(scope.min(), scope.maxInclusive())) return new SiloAssessment(false, 0, null);
        int best = -1;
        BuildingBounds column = null;
        for (int down = 0; down < 10; down++) for (int dx = 0; dx >= -1; dx--) for (int dz = 0; dz >= -1; dz--) {
            var min = manager.offset(dx, -down, dz);
            var candidate = new BuildingBounds(min, min.offset(2, 10, 2));
            if (!scope.contains(min) || !scope.contains(candidate.maxInclusive())) continue;
            int count = 0;
            for (var pos : BlockPos.betweenClosed(min, candidate.maxInclusive()))
                if (!pos.equals(manager) && level.getBlockState(pos).is(Blocks.BRICKS)) count++;
            if (count > best) { best = count; column = candidate; }
            if (count == 39) break;
        }
        return new SiloAssessment(true, Math.max(0, best), column);
    }
    public static boolean acceptSilo(ServerLevel level, BuildingRecord record) {
        if (!supported(record.family()) || record.mode() != BuildingRecord.Mode.SELF_BUILT || record.phase() != BuildingRecord.Phase.WAITING) return false;
        var scan = scanSilo(level, record.manager(), record.claim());
        return scan.valid() && BuildingWorldData.get(level.getServer()).acceptSelfColumn(record.id(), record.revision(), scan.column()) == BuildingWorldData.Result.SUCCESS;
    }
    public static void refresh(ServerLevel level, BuildingRecord record) {
        if (FishPondPrefabs.isPond(record.family())) { FishPondPrefabs.bind(level,record); return; }
        if (!supported(record.family())) { BuildingResidence.refresh(level, record); return; }
        if (record.phase() != BuildingRecord.Phase.READY || !level.hasChunkAt(record.manager())) return;
        boolean valid = level.getBlockState(record.manager()).is(PrefabDefinitions.managerBlock(record.family()));
        if (record.mode() == BuildingRecord.Mode.SELF_BUILT) {
            var scan = scanSilo(level, record.manager(), record.claim());
            if (!scan.loaded()) return;
            valid &= scan.valid();
        }
        var status = valid ? BuildingRecord.Residence.VALID : BuildingRecord.Residence.INVALID;
        if (record.residence() != status) BuildingWorldData.get(level.getServer()).assessResidence(record.id(), record.revision(), valid ? 1 : 0);
    }
    /** Self-built moves rotate cell centers, whereas prefabs rotate corner-based coordinates. */
    public static BuildingBounds rotateBounds(BuildingBounds local, BlockPos origin, Rotation rotation) {
        var a = local.min().rotate(rotation).offset(origin);
        var b = local.maxInclusive().rotate(rotation).offset(origin);
        return new BuildingBounds(new BlockPos(Math.min(a.getX(),b.getX()),Math.min(a.getY(),b.getY()),Math.min(a.getZ(),b.getZ())),
                new BlockPos(Math.max(a.getX(),b.getX())+1,Math.max(a.getY(),b.getY())+1,Math.max(a.getZ(),b.getZ())+1));
    }
    public static BuildingBounds moveBounds(BuildingRecord record, BlockPos anchor, Direction facing) {
        var relative = new BuildingBounds(record.claim().min().subtract(record.anchor()), record.claim().maxExclusive().subtract(record.anchor()));
        var delta = PrefabDefinitions.rotation(facing).getRotated(PrefabDefinitions.inverse(PrefabDefinitions.rotation(record.facing())));
        return rotateBounds(relative, anchor, delta);
    }
}

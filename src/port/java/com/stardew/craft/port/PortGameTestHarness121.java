package com.stardew.craft.port;

import java.util.Collections;
import java.util.List;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

/**
 * PORT(1.20.1): the 1.21.1 GameTest harness (only used by {@code runGameTestServer}), so every test runs in the same
 * world conditions as in the 1.21.1 baseline. Implemented by the {@code GameTestInfo} mixin; the static helpers are
 * the 1.21.1 {@code StructureUtils} bodies, which differ from 1.20.1:
 * <ul>
 * <li>bounds start at the structure origin (structure block + {@code structurePos}) and the AABB covers whole blocks
 * ({@code AABB.of}); 1.20.1 starts at the structure block and leaves out the last block on every axis;</li>
 * <li>{@code clearSpaceForStructure} fills the two layers below the structure block with stone, 1.20.1 with the flat
 * preset layers;</li>
 * <li>every test is encased in barrier walls plus a barrier ceiling one block above the structure
 * ({@code skyAccess = false}, the default, which no StardewCraft test overrides);</li>
 * <li>all chunks the structure intersects are force-loaded (1.20.1 a fixed 5x5 chunk area from the corner).</li>
 * </ul>
 */
public interface PortGameTestHarness121 {
    /** 1.21.1 {@code GameTestInfo#prepareTestStructure}: structure block, command block, encasement; no placement yet. */
    void stardewcraft$prepareTestStructure121(BlockPos northWestCorner);

    /** 1.21.1 {@code StructureUtils#getStructureBoundingBox(StructureBlockEntity)}. */
    static BoundingBox structureBoundingBox(StructureBlockEntity structureBlockEntity) {
        BlockPos origin = structureBlockEntity.getBlockPos().offset(structureBlockEntity.getStructurePos());
        BlockPos farCorner = transformedFarCorner(origin, structureBlockEntity.getStructureSize(), structureBlockEntity.getRotation());
        return BoundingBox.fromCorners(origin, farCorner);
    }

    /** 1.21.1 {@code StructureUtils#getStructureBounds(StructureBlockEntity)}. */
    static AABB structureBounds(StructureBlockEntity structureBlockEntity) {
        return AABB.of(structureBoundingBox(structureBlockEntity));
    }

    /** 1.21.1 {@code StructureUtils#getTransformedFarCorner}. */
    static BlockPos transformedFarCorner(BlockPos pos, Vec3i offset, Rotation rotation) {
        BlockPos blockpos = pos.offset(offset).offset(-1, -1, -1);
        return StructureTemplate.transform(blockpos, Mirror.NONE, rotation, pos);
    }

    /** 1.21.1 {@code StructureUtils#getStructureBoundingBox(BlockPos, Vec3i, Rotation)}. */
    static BoundingBox structureBoundingBox(BlockPos pos, Vec3i offset, Rotation rotation) {
        BlockPos blockpos = transformedFarCorner(pos, offset, rotation);
        BoundingBox boundingbox = BoundingBox.fromCorners(pos, blockpos);
        int i = Math.min(boundingbox.minX(), boundingbox.maxX());
        int j = Math.min(boundingbox.minZ(), boundingbox.maxZ());
        return boundingbox.move(pos.getX() - i, 0, pos.getZ() - j);
    }

    /** 1.21.1 {@code StructureUtils#forceLoadChunks(BoundingBox, ServerLevel)} ({@code intersectingChunks}). */
    static void forceLoadChunks(BoundingBox boundingBox, ServerLevel level) {
        for (int x = boundingBox.minX() >> 4; x <= boundingBox.maxX() >> 4; x++) {
            for (int z = boundingBox.minZ() >> 4; z <= boundingBox.maxZ() >> 4; z++) level.setChunkForced(x, z, true);
        }
    }

    /** 1.21.1 {@code BoundingBox#intersectingChunks().allMatch(c -> level.isPositionEntityTicking(c.getWorldPosition()))}. */
    static boolean chunksEntityTicking(BoundingBox boundingBox, ServerLevel level) {
        for (int x = boundingBox.minX() >> 4; x <= boundingBox.maxX() >> 4; x++) {
            for (int z = boundingBox.minZ() >> 4; z <= boundingBox.maxZ() >> 4; z++) {
                if (!level.isPositionEntityTicking(new BlockPos(x << 4, 0, z << 4))) return false;
            }
        }
        return true;
    }

    /** 1.21.1 {@code StructureUtils#clearSpaceForStructure(BoundingBox, ServerLevel)}. */
    static void clearSpaceForStructure(BoundingBox boundingBox, ServerLevel level) {
        int structureBlockY = boundingBox.minY() - 1;
        BoundingBox cleared = new BoundingBox(boundingBox.minX() - 2, boundingBox.minY() - 3, boundingBox.minZ() - 3,
                boundingBox.maxX() + 3, boundingBox.maxY() + 20, boundingBox.maxZ() + 3);
        BlockPos.betweenClosedStream(cleared).forEach(pos -> {
            BlockState state = pos.getY() < structureBlockY ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
            new BlockInput(state, Collections.emptySet(), null).place(level, pos, 2);
            level.blockUpdated(pos, state.getBlock());
        });
        level.getBlockTicks().clearArea(cleared);
        level.clearBlockEvents(cleared);
        List<Entity> entities = level.getEntitiesOfClass(Entity.class, AABB.of(cleared), entity -> !(entity instanceof Player));
        entities.forEach(Entity::discard);
    }

    /** 1.21.1 {@code StructureUtils#encaseStructure(AABB, ServerLevel, boolean)}. */
    static void encaseStructure(AABB bounds, ServerLevel level, boolean placeBarriers) {
        BlockPos min = BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ).offset(-1, 0, -1);
        BlockPos max = BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ);
        BlockPos.betweenClosedStream(min, max).forEach(pos -> {
            boolean wall = pos.getX() == min.getX() || pos.getX() == max.getX() || pos.getZ() == min.getZ() || pos.getZ() == max.getZ();
            boolean ceiling = pos.getY() == max.getY();
            if (wall || ceiling && placeBarriers) level.setBlockAndUpdate(pos, Blocks.BARRIER.defaultBlockState());
        });
    }
}

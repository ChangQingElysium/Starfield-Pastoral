package com.stardew.craft.entity.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Set;

/**
 * Custom WalkNodeEvaluator that assigns higher traversal cost to "off-road"
 * blocks, making NPCs strongly prefer roads, paths, planks and other
 * constructed surfaces — just like Stardew Valley originals.
 *
 * <p>Yellow dirt is the preferred Stardew road surface, other road/paved blocks
 * are still cheap, and ordinary terrain is expensive.
 * This causes the A* inside vanilla PathFinder to strongly prefer road nodes
 * when a road path exists, while still allowing off-road navigation when
 * no road is available.</p>
 */
public class NpcNodeEvaluator extends WalkNodeEvaluator {
    private static final double TRAFFIC_SCAN_RADIUS = 12.0D;
    private static final double TRAFFIC_PERSONAL_SPACE = 0.35D;
    private static final float TRAFFIC_COST_PER_ACTOR = 8.0F;
    private static final float MAX_TRAFFIC_COST = 32.0F;
    com.stardew.craft.npc.runtime.NpcSquareArea squareArea;
    private record Failure(float cost, long expiresAt) {}
    private final java.util.LinkedHashMap<BlockPos, Failure> failures = new java.util.LinkedHashMap<>();
    private List<AABB> trafficBodies = List.of();

    @Override
    public void prepare(PathNavigationRegion region, Mob mob) {
        super.prepare(region, mob);
        // Snapshot nearby actors once per A* search. NPCs are dynamic soft obstacles:
        // prefer an open aisle when one exists, but never declare a one-tile doorway
        // permanently unreachable just because another NPC is passing through it.
        trafficBodies = mob.level().getEntitiesOfClass(
                StardewNpcEntity.class,
                mob.getBoundingBox().inflate(TRAFFIC_SCAN_RADIUS, 2.0D, TRAFFIC_SCAN_RADIUS),
                other -> other != mob && other.isAlive() && other.isPushable())
            .stream()
            .map(other -> other.getBoundingBox().inflate(TRAFFIC_PERSONAL_SPACE, 0.0D, TRAFFIC_PERSONAL_SPACE))
            .toList();
    }

    @Override
    public void done() {
        trafficBodies = List.of();
        super.done();
    }

    void penalize(BlockPos pos, long now) {
        failures.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
        Failure previous = failures.get(pos);
        failures.put(pos.immutable(), new Failure(Math.min(48, previous == null ? 12 : previous.cost + 12), now + 200));
        if (failures.size() > 32) failures.remove(failures.keySet().iterator().next());
    }

    @Override
    public PathType getPathType(net.minecraft.world.level.pathfinder.PathfindingContext context, int x, int y, int z) {
        PathType type = super.getPathType(context,x,y,z);
        if (type == PathType.FENCE && context.level().getBlockState(new BlockPos(x,y,z)).getBlock()
                instanceof net.minecraft.world.level.block.FenceGateBlock) {
            // The route executor can operate gates just like wooden doors. Do not
            // classify their closed 1.5-block collision as an impassable fence.
            return PathType.DOOR_WOOD_CLOSED;
        }
        return type;
    }

    private static final float YELLOW_DIRT_COST = 0.0F;
    private static final float ROAD_COST = 1.0F;
    private static final float OFF_ROAD_COST = 6.0F;

    /**
     * Set of vanilla blocks considered "road" or "paved" surfaces.
     * NPCs will strongly prefer walking on these.
     */
    private static final Set<Block> ROAD_BLOCKS = Set.of(
        // Vanilla paths
        Blocks.DIRT_PATH,
        // Coarse dirt
        Blocks.COARSE_DIRT,
        // Cobblestone roads
        Blocks.COBBLESTONE, Blocks.COBBLESTONE_SLAB, Blocks.COBBLESTONE_STAIRS,
        Blocks.MOSSY_COBBLESTONE, Blocks.MOSSY_COBBLESTONE_SLAB, Blocks.MOSSY_COBBLESTONE_STAIRS,
        // Stone roads
        Blocks.STONE, Blocks.STONE_SLAB, Blocks.STONE_STAIRS,
        Blocks.SMOOTH_STONE, Blocks.SMOOTH_STONE_SLAB,
        Blocks.STONE_BRICKS, Blocks.STONE_BRICK_SLAB, Blocks.STONE_BRICK_STAIRS,
        Blocks.MOSSY_STONE_BRICKS, Blocks.MOSSY_STONE_BRICK_SLAB, Blocks.MOSSY_STONE_BRICK_STAIRS,
        // Wooden bridges/walkways
        Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.BIRCH_PLANKS,
        Blocks.JUNGLE_PLANKS, Blocks.ACACIA_PLANKS, Blocks.DARK_OAK_PLANKS,
        Blocks.MANGROVE_PLANKS, Blocks.CHERRY_PLANKS, Blocks.BAMBOO_PLANKS,
        Blocks.OAK_SLAB, Blocks.SPRUCE_SLAB, Blocks.BIRCH_SLAB,
        Blocks.JUNGLE_SLAB, Blocks.JUNGLE_STAIRS,
        Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_STAIRS,
        Blocks.OAK_STAIRS, Blocks.SPRUCE_STAIRS, Blocks.BIRCH_STAIRS,
        // Bricks
        Blocks.BRICKS, Blocks.BRICK_SLAB, Blocks.BRICK_STAIRS,
        // Gravel paths
        Blocks.GRAVEL,
        // Sandstone
        Blocks.SANDSTONE, Blocks.SANDSTONE_SLAB, Blocks.SANDSTONE_STAIRS,
        Blocks.SMOOTH_SANDSTONE, Blocks.SMOOTH_SANDSTONE_SLAB, Blocks.SMOOTH_SANDSTONE_STAIRS,
        // Deepslate
        Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICK_SLAB, Blocks.DEEPSLATE_BRICK_STAIRS,
        Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_TILE_SLAB, Blocks.DEEPSLATE_TILE_STAIRS,
        // Polished variants
        Blocks.POLISHED_ANDESITE, Blocks.POLISHED_ANDESITE_SLAB, Blocks.POLISHED_ANDESITE_STAIRS,
        Blocks.POLISHED_DIORITE, Blocks.POLISHED_DIORITE_SLAB, Blocks.POLISHED_DIORITE_STAIRS,
        Blocks.POLISHED_GRANITE, Blocks.POLISHED_GRANITE_SLAB, Blocks.POLISHED_GRANITE_STAIRS,
        // Misc
        Blocks.SMOOTH_QUARTZ, Blocks.SMOOTH_QUARTZ_SLAB, Blocks.SMOOTH_QUARTZ_STAIRS
    );

    @Override
    public int getNeighbors(@javax.annotation.Nonnull Node[] buffer, @javax.annotation.Nonnull Node node) {
        int count = super.getNeighbors(buffer, node);
        if (this.currentContext == null) return count;

        BlockGetter level = this.currentContext.level();
        int accepted = 0;
        for (int i = 0; i < count; i++) {
            Node neighbor = buffer[i];
            if(squareArea!=null&&(!squareArea.contains(new net.minecraft.world.phys.Vec3(neighbor.x+.5,
                    getFloorLevel(new BlockPos(neighbor.x,neighbor.y,neighbor.z)),neighbor.z+.5),this.mob.getBbWidth()/2.)
                    ||!com.stardew.craft.interior.InteriorRegionRegistry.fixedInteriorIdAt(this.mob.blockPosition())
                    .equals(com.stardew.craft.interior.InteriorRegionRegistry.fixedInteriorIdAt(new BlockPos(neighbor.x,neighbor.y,neighbor.z)))))continue;
            // WalkNodeEvaluator may offer a diagonal between two individually valid
            // cells even though the NPC's body clips the inside corner between them.
            // That produced valid-looking saloon paths which the move controller could
            // never physically follow. Reject only the clipped edge so A* can choose
            // the adjacent cardinal cell and round the corner normally.
            if (!hasClearHorizontalEdge(node, neighbor)) continue;
            // Vanilla accepts a one-block jump even with a 0.6 step height. Reject
            // that edge, not the cached node: the same node may have a level approach.
            if (!canStepBetween(node, neighbor)) continue;
            if (neighbor.type == PathType.WATER || neighbor.type == PathType.WATER_BORDER || neighbor.type == PathType.LAVA) {
                neighbor.costMalus = -1.0F;
                continue;
            }
            if (neighbor.type != PathType.DOOR_WOOD_CLOSED && neighbor.type != PathType.WALKABLE_DOOR
                    && !hasNpcClearance(neighbor)) {
                neighbor.costMalus = -1.0F;
                continue;
            }
            if (neighbor.type != PathType.WALKABLE
                && neighbor.type != PathType.WALKABLE_DOOR
                && neighbor.type != PathType.DOOR_OPEN
                && neighbor.type != PathType.DOOR_WOOD_CLOSED) {
                buffer[accepted++] = neighbor;
                continue;
            }

            // The surface block is the one directly below the node's feet
            BlockPos surfacePos = new BlockPos(neighbor.x, neighbor.y - 1, neighbor.z);
            BlockState surface = level.getBlockState(surfacePos);
            Block surfaceBlock = surface.getBlock();

            // SET costMalus (idempotent), NOT +=.
            // Nodes are cached by position in PathFinder — the same Node object
            // is returned as a neighbor of multiple parent nodes. Using += would
            // accumulate the penalty each time, inflating cost to infinity and
            // making the pathfinder give up.
            if (isYellowDirt(surfaceBlock)) {
                neighbor.costMalus = YELLOW_DIRT_COST;
            } else if (isRoadBlock(surfaceBlock)) {
                neighbor.costMalus = ROAD_COST;
            } else {
                neighbor.costMalus = OFF_ROAD_COST;
            }
            Failure failure = failures.get(new BlockPos(neighbor.x, neighbor.y, neighbor.z));
            if (failure != null && failure.expiresAt > this.mob.level().getGameTime()) {
                // Finite cost: still usable when this is the only exit. Never accumulate
                // on cached Nodes across visits, and never change another NPC's costs.
                neighbor.costMalus += failure.cost;
            }
            neighbor.costMalus += trafficCost(neighbor);
            buffer[accepted++] = neighbor;
        }
        return accepted;
    }

    private boolean hasNpcClearance(Node node) {
        if (this.mob == null) return true;
        double halfWidth = this.mob.getBbWidth() * 0.5D;
        double x = node.x + 0.5D;
        double z = node.z + 0.5D;
        double feetY = getFloorLevel(new BlockPos(node.x, node.y, node.z));
        AABB box = new AABB(
            x - halfWidth,
            feetY,
            z - halfWidth,
            x + halfWidth,
            feetY + this.mob.getBbHeight(),
            z + halfWidth
        ).deflate(1.0E-7D);
        // Entity occupancy is transient and must not erase the only route through
        // a doorway. Physical NPC collision and the finite traffic cost below own
        // crowd handling; this check is strictly architectural clearance.
        return this.mob.level().noBlockCollision(this.mob, box);
    }

    private float trafficCost(Node node) {
        if (trafficBodies.isEmpty() || this.mob == null) return 0.0F;
        double halfWidth = this.mob.getBbWidth() * 0.5D;
        double x = node.x + 0.5D;
        double z = node.z + 0.5D;
        double feetY = getFloorLevel(new BlockPos(node.x, node.y, node.z));
        AABB body = new AABB(
            x - halfWidth,
            feetY,
            z - halfWidth,
            x + halfWidth,
            feetY + this.mob.getBbHeight(),
            z + halfWidth
        ).deflate(1.0E-7D);
        float cost = 0.0F;
        for (AABB occupied : trafficBodies) {
            if (body.intersects(occupied)) {
                cost = Math.min(MAX_TRAFFIC_COST, cost + TRAFFIC_COST_PER_ACTOR);
            }
        }
        return cost;
    }

    private boolean hasClearHorizontalEdge(Node from, Node to) {
        if (this.mob == null || from.x == to.x || from.z == to.z) return true;
        double fromFloor = getFloorLevel(new BlockPos(from.x, from.y, from.z));
        double toFloor = getFloorLevel(new BlockPos(to.x, to.y, to.z));
        // A diagonal rise can clip both a stair/furniture edge and its neighbour.
        // Use the cardinal riser nodes so physical stepping follows the same edges.
        if (Math.abs(fromFloor - toFloor) > 0.05D) return false;

        double halfWidth = this.mob.getBbWidth() * 0.5D;
        AABB body = new AABB(
            from.x + 0.5D - halfWidth,
            fromFloor,
            from.z + 0.5D - halfWidth,
            from.x + 0.5D + halfWidth,
            fromFloor + this.mob.getBbHeight(),
            from.z + 0.5D + halfWidth
        ).deflate(1.0E-7D);
        return this.mob.level().noBlockCollision(this.mob, body.expandTowards(
                to.x - from.x, 0.0D, to.z - from.z));
    }

    private boolean canStepBetween(Node from, Node to) {
        if (this.mob == null) return true;
        double rise = getFloorLevel(new BlockPos(to.x, to.y, to.z))
            - getFloorLevel(new BlockPos(from.x, from.y, from.z));
        if (rise <= this.mob.maxUpStep() + 1.0E-7D) return true;
        // The node is at the stair's top, but entering from its low side crosses
        // two half-block risers. A full block or the stair's high side needs a jump.
        var surface = this.currentContext.level().getBlockState(new BlockPos(to.x, to.y - 1, to.z));
        if (!(surface.getBlock() instanceof StairBlock) || surface.getValue(StairBlock.HALF) != Half.BOTTOM
                || this.mob.maxUpStep() < 0.5F || rise > 1.0D + 1.0E-7D) return false;
        var uphill = surface.getValue(StairBlock.FACING);
        return to.x - from.x == uphill.getStepX() && to.z - from.z == uphill.getStepZ();
    }

    /**
     * Check if a block is considered a "road" surface.
     * Road blocks get no extra malus; off-road blocks get penalized.
     */
    private static boolean isRoadBlock(Block block) {
        if (ROAD_BLOCKS.contains(block) || block instanceof com.stardew.craft.block.terrain.AsphaltRoadBlock) return true;
        // Mod blocks (resolved at runtime to avoid class-load ordering issues)
        if (isYellowDirt(block)) return true;
        return false;
    }

    private static boolean isYellowDirt(Block block) {
        return (block == com.stardew.craft.block.ModBlocks.YELLOW_DIRT.get() || block == com.stardew.craft.block.ModBlocks.DIRT.get());
    }
}

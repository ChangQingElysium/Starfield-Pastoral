package com.stardew.craft.cutscene.command;

import com.stardew.craft.cutscene.runtime.CutsceneAnchorRegistry;
import com.stardew.craft.cutscene.runtime.EventActorEntity;
import com.stardew.craft.cutscene.runtime.EventPlayer;
import com.stardew.craft.cutscene.runtime.EventPlayerActorEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * navigate_actor: moves a cutscene actor through walkable tiles instead of
 * linearly interpolating through walls or furniture.
 */
public class NavigateActorCommand implements EventCommand {
    private static final int MAX_SEARCH_NODES = 4096;
    private static final int DOOR_CLOSE_TIMEOUT_TICKS = 30;
    private static final int[][] DIRS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };

    private final String actorTag;
    private final double targetX;
    private final double targetY;
    private final double targetZ;
    private final boolean relative;
    private final double speedBlocksPerTick;
    private final String anchor;

    private Mob actor;
    private List<Vec3> path = List.of();
    private int segmentIndex;
    private Vec3 segmentStart;
    private Vec3 segmentEnd;
    private double segmentProgress;
    private boolean done;
    private ClientLevel level;
    private int ticksElapsed;
    private final Map<BlockPos, Integer> openedDoors = new HashMap<>();

    public NavigateActorCommand(String actorTag, double x, double y, double z,
                                boolean relative, double speedBlocksPerTick, String anchor) {
        this.actorTag = actorTag;
        this.targetX = x;
        this.targetY = y;
        this.targetZ = z;
        this.relative = relative;
        this.speedBlocksPerTick = Math.max(0.02D, speedBlocksPerTick);
        this.anchor = anchor;
    }

    @Override
    public void start(EventPlayer player) {
        actor = player.getActor(actorTag);
        level = Minecraft.getInstance().level;
        if (actor == null || level == null) {
            done = true;
            return;
        }

        openedDoors.clear();
        ticksElapsed = 0;

        double endX;
        double endY;
        double endZ;
        if (relative) {
            endX = actor.getX() + targetX;
            endY = actor.getY() + targetY;
            endZ = actor.getZ() + targetZ;
        } else {
            endX = targetX + CutsceneAnchorRegistry.offsetX(anchor);
            endY = targetY + CutsceneAnchorRegistry.offsetY(anchor);
            endZ = targetZ + CutsceneAnchorRegistry.offsetZ(anchor);
        }

        path = buildPath(level, actor, new Vec3(actor.getX(), actor.getY(), actor.getZ()), new Vec3(endX, endY, endZ));
        if (path.size() < 2) {
            setWalking(false);
            done = true;
            return;
        }

        segmentIndex = 0;
        segmentStart = path.get(0);
        segmentEnd = path.get(1);
        segmentProgress = 0.0D;
        setWalking(true);
        faceSegment();
        updateDoors(actor.position());
        done = false;
    }

    @Override
    public void tick(EventPlayer player) {
        if (done || actor == null) return;

        ticksElapsed++;

        double length = segmentStart.distanceTo(segmentEnd);
        if (length < 1.0E-5D) {
            advanceSegment();
            return;
        }

        segmentProgress = Math.min(1.0D, segmentProgress + speedBlocksPerTick / length);
        double x = Mth.lerp(segmentProgress, segmentStart.x, segmentEnd.x);
        double y = Mth.lerp(segmentProgress, segmentStart.y, segmentEnd.y);
        double z = Mth.lerp(segmentProgress, segmentStart.z, segmentEnd.z);
        updateDoors(new Vec3(x, y, z));
        actor.setPos(x, y, z);
        closePassedDoors(false);

        if (segmentProgress >= 1.0D) {
            advanceSegment();
        }
    }

    @Override
    public boolean isComplete() {
        return done;
    }

    @Override
    public void onSkip(EventPlayer player) {
        setWalking(false);
        if (!path.isEmpty() && actor != null) {
            Vec3 end = path.get(path.size() - 1);
            actor.setPos(end.x, end.y, end.z);
        }
        closePassedDoors(true);
        done = true;
    }

    private void advanceSegment() {
        segmentIndex++;
        if (segmentIndex >= path.size() - 1) {
            Vec3 end = path.get(path.size() - 1);
            actor.setPos(end.x, end.y, end.z);
            setWalking(false);
            closePassedDoors(true);
            done = true;
            return;
        }
        segmentStart = path.get(segmentIndex);
        segmentEnd = path.get(segmentIndex + 1);
        segmentProgress = 0.0D;
        faceSegment();
    }

    private void faceSegment() {
        double dirX = segmentEnd.x - segmentStart.x;
        double dirZ = segmentEnd.z - segmentStart.z;
        if (dirX == 0.0D && dirZ == 0.0D) return;
        float yaw = (float) (Mth.atan2(dirZ, dirX) * Mth.RAD_TO_DEG) - 90.0f;
        actor.setYRot(yaw);
        actor.setYHeadRot(yaw);
        actor.setYBodyRot(yaw);
    }

    private void setWalking(boolean walking) {
        if (actor instanceof EventActorEntity npcActor) {
            npcActor.setWalking(walking);
        } else if (actor instanceof EventPlayerActorEntity playerActor) {
            playerActor.setWalking(walking);
        }
    }

    /**
     * Cutscene actors are client-only, so their door animation must also be
     * client-local. This preserves private multiplayer cutscenes without
     * opening the shared server door for unrelated players.
     */
    private void updateDoors(Vec3 nextPosition) {
        if (level == null || actor == null) {
            return;
        }
        openDoorsAround(actor.blockPosition());
        openDoorsAround(BlockPos.containing(nextPosition));
    }

    private void openDoorsAround(BlockPos center) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos base = center.offset(dx, 0, dz);
                openDoorAt(base.below());
                openDoorAt(base);
                openDoorAt(base.above());
            }
        }
    }

    private void openDoorAt(BlockPos probePos) {
        BlockState state = level.getBlockState(probePos);
        if (!(state.getBlock() instanceof DoorBlock door)) {
            return;
        }

        BlockPos lowerPos = probePos;
        if (state.hasProperty(DoorBlock.HALF)
                && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
            lowerPos = probePos.below();
            state = level.getBlockState(lowerPos);
            if (!(state.getBlock() instanceof DoorBlock lowerDoor)) {
                return;
            }
            door = lowerDoor;
        }

        if (state.getBlock() == Blocks.IRON_DOOR
                || !state.hasProperty(DoorBlock.OPEN)
                || state.getValue(DoorBlock.OPEN)) {
            return;
        }

        door.setOpen(actor, level, state, lowerPos, true);
        openedDoors.put(lowerPos.immutable(), ticksElapsed);
    }

    private void closePassedDoors(boolean force) {
        if (level == null || actor == null || openedDoors.isEmpty()) {
            return;
        }

        List<BlockPos> closed = new ArrayList<>();
        for (Map.Entry<BlockPos, Integer> entry : openedDoors.entrySet()) {
            BlockPos pos = entry.getKey();
            if (!force && (ticksElapsed - entry.getValue() < DOOR_CLOSE_TIMEOUT_TICKS
                    || actor.blockPosition().distSqr(pos) <= 4.0D)) {
                continue;
            }

            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof DoorBlock door)
                    || !state.hasProperty(DoorBlock.OPEN)
                    || !state.getValue(DoorBlock.OPEN)) {
                closed.add(pos);
                continue;
            }
            if (!force && new AABB(pos).inflate(0.2D, 0.0D, 0.2D)
                    .expandTowards(0.0D, 1.0D, 0.0D).intersects(actor.getBoundingBox())) {
                continue;
            }

            door.setOpen(actor, level, state, pos, false);
            closed.add(pos);
        }
        closed.forEach(openedDoors::remove);
    }

    private static List<Vec3> buildPath(ClientLevel level, Mob actor, Vec3 start, Vec3 end) {
        BlockPos startCell = BlockPos.containing(start.x, start.y, start.z);
        BlockPos endCell = BlockPos.containing(end.x, end.y, end.z);
        int y = startCell.getY();

        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(n -> n.fScore));
        Map<GridKey, Node> nodes = new HashMap<>();
        Set<GridKey> closed = new HashSet<>();

        GridKey startKey = new GridKey(startCell.getX(), startCell.getZ());
        GridKey endKey = new GridKey(endCell.getX(), endCell.getZ());
        Node startNode = new Node(startKey, null, 0.0D, heuristic(startKey, endKey));
        nodes.put(startKey, startNode);
        open.add(startNode);

        int searched = 0;
        Node found = null;
        while (!open.isEmpty() && searched++ < MAX_SEARCH_NODES) {
            Node current = open.poll();
            if (!closed.add(current.key)) continue;
            if (current.key.equals(endKey)) {
                found = current;
                break;
            }

            for (int[] dir : DIRS) {
                GridKey nextKey = new GridKey(current.key.x + dir[0], current.key.z + dir[1]);
                if (closed.contains(nextKey)) continue;
                if (!canStandAt(level, actor, nextKey.x + 0.5D, y, nextKey.z + 0.5D)) continue;

                double tentativeG = current.gScore + 1.0D;
                Node existing = nodes.get(nextKey);
                if (existing == null || tentativeG < existing.gScore) {
                    Node next = new Node(nextKey, current, tentativeG, tentativeG + heuristic(nextKey, endKey));
                    nodes.put(nextKey, next);
                    open.add(next);
                }
            }
        }

        if (found == null) {
            return List.of(start);
        }

        List<GridKey> cells = new ArrayList<>();
        for (Node n = found; n != null; n = n.parent) {
            cells.add(n.key);
        }
        java.util.Collections.reverse(cells);

        List<Vec3> result = new ArrayList<>();
        result.add(start);
        for (int i = 1; i < cells.size() - 1; i++) {
            GridKey cell = cells.get(i);
            result.add(new Vec3(cell.x + 0.5D, start.y, cell.z + 0.5D));
        }
        result.add(end);
        return result;
    }

    private static boolean canStandAt(ClientLevel level, Mob actor, double x, double y, double z) {
        AABB moved = actor.getBoundingBox().move(
                x - actor.getX(),
                y - actor.getY(),
                z - actor.getZ()
        ).deflate(1.0E-7D);
        if (level.noCollision(actor, moved)) {
            return true;
        }

        // Closed wooden doors are valid route cells: updateDoors opens them
        // before the actor reaches the collision plane. Every other collision
        // still blocks the A* search.
        for (BlockPos pos : BlockPos.betweenClosed(
                Mth.floor(moved.minX), Mth.floor(moved.minY), Mth.floor(moved.minZ),
                Mth.floor(moved.maxX), Mth.floor(moved.maxY), Mth.floor(moved.maxZ))) {
            BlockState state = level.getBlockState(pos);
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty() || !intersects(moved, shape, pos)) {
                continue;
            }
            if (!(state.getBlock() instanceof DoorBlock) || state.getBlock() == Blocks.IRON_DOOR) {
                return false;
            }
        }
        return true;
    }

    private static boolean intersects(AABB box, VoxelShape shape, BlockPos pos) {
        for (AABB localBox : shape.toAabbs()) {
            if (box.intersects(localBox.move(pos))) {
                return true;
            }
        }
        return false;
    }

    private static double heuristic(GridKey a, GridKey b) {
        return Math.abs(a.x - b.x) + Math.abs(a.z - b.z);
    }

    private record GridKey(int x, int z) {
    }

    private static final class Node {
        private final GridKey key;
        private final Node parent;
        private final double gScore;
        private final double fScore;

        private Node(GridKey key, Node parent, double gScore, double fScore) {
            this.key = key;
            this.parent = parent;
            this.gScore = gScore;
            this.fScore = fScore;
        }
    }
}

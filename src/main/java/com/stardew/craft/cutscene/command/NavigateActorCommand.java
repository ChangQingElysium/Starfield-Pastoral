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
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import com.stardew.craft.client.npcnative.NativeNpcAssets;
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
    private Vec3 destination;
    private double walkSpeed;
    /** move_actor's legacy timeline is an authored travel duration, not an animation rate. */
    protected int authoredTicks = -1;
    private double currentSpeed;
    private double verticalVelocity;
    private int blockedTicks;
    private int replans;
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
        this.speedBlocksPerTick = speedBlocksPerTick;
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

        actor.getNavigation().stop();
        actor.setDeltaMovement(Vec3.ZERO);
        if (actor instanceof EventActorEntity npc) {
            npc.stopWalking();
            npc.clearCustomAnimation();
            var model = NativeNpcAssets.model(NativeNpcAssets.renderId(npc.getNpcId()));
            var profile = model == null ? null : model.profile();
            // A scripted navigate speed is the world-motion value authored by
            // the event.  It must not be clamped to the model's preview speed:
            // the walk clip has its own fixed cadence while the scene decides
            // how quickly the actor crosses the floor.
            walkSpeed = Double.isFinite(speedBlocksPerTick) && speedBlocksPerTick > 0
                    ? speedBlocksPerTick
                    : ActorWalkPace.normalSpeed(
                            profile == null || profile.gait() == null ? 0 : profile.gait().previewSpeed(),
                            profile == null ? 0 : profile.walkStride(), 0);
        } else {
            walkSpeed = ActorWalkPace.normalSpeed(0, 0, speedBlocksPerTick);
        }
        currentSpeed = 0;
        verticalVelocity = 0;
        blockedTicks = 0;
        replans = 0;
        done = false;
        destination = new Vec3(endX, endY, endZ);
        planRoute();
        if (authoredTicks > 0 && path.size() > 1) {
            double distance = 0;
            for (int i = 1; i < path.size(); i++) distance += path.get(i - 1).distanceTo(path.get(i));
            if (distance > 1.0E-5) walkSpeed = distance / authoredTicks;
        }
        setWalking(false);
    }

    private void planRoute() {
        path = buildPath(level, actor, actor.position(), destination);
        if (path.size() < 2) {
            failRoute("no supported route");
        }
        segmentIndex = 1;
    }

    @Override
    public void tick(EventPlayer player) {
        if (done || actor == null) return;
        if (actor.isRemoved()) failRoute("actor removed during walk");
        ticksElapsed++;
        Vec3 target = path.get(segmentIndex);
        Vec3 before = actor.position();
        double dx = target.x - before.x;
        double dz = target.z - before.z;
        double distance = Math.hypot(dx, dz);
        double yawError = 0;
        if (distance > 1.0E-5) {
            float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
            float facing = Mth.approachDegrees(actor.getYRot(), yaw, ActorWalkPace.TURN_PER_TICK);
            actor.setYRot(facing);
            actor.setYHeadRot(facing);
            actor.setYBodyRot(facing);
            yawError = Mth.wrapDegrees(yaw - facing);
        }
        // Preserve pace through collinear waypoints; brake for the destination or a corner.
        double remaining = distance;
        if (segmentIndex < path.size() - 1) {
            Vec3 next = path.get(segmentIndex + 1).subtract(target);
            if (distance > 0 && (dx * next.x + dz * next.z) / distance > next.horizontalDistance() * .99) {
                remaining += next.horizontalDistance();
            }
        }
        // move_actor's ticks describe the authored travel timeline.  Starting it
        // with the generic acceleration ramp would make every route arrive late
        // (and changed the old 0.5.6 pacing); navigate_actor keeps the eased entry.
        currentSpeed = authoredTicks > 0
                ? Math.min(walkSpeed, remaining)
                : ActorWalkPace.nextSpeed(currentSpeed, walkSpeed, remaining, yawError);
        double step = Math.min(distance, currentSpeed);
        double moveX = distance > 1.0E-5 ? dx * step / distance : 0;
        double moveZ = distance > 1.0E-5 ? dz * step / distance : 0;
        verticalVelocity = actor.onGround() ? -0.04 : (verticalVelocity - 0.08) * 0.98;
        updateDoors(before.add(moveX, 0, moveZ));
        // Only step towards a higher route surface, never climb an unrelated side obstacle.
        var stepAttribute = actor.getAttribute(com.stardew.craft.port.PortAttributes.STEP_HEIGHT.get());
        double oldStep = stepAttribute == null ? 0 : stepAttribute.getBaseValue();
        if (stepAttribute != null) stepAttribute.setBaseValue(Math.min(oldStep, Math.max(0, target.y - before.y + 1.0E-5)));
        try {
            actor.move(MoverType.SELF, new Vec3(moveX, verticalVelocity, moveZ));
        } finally {
            if (stepAttribute != null) stepAttribute.setBaseValue(oldStep);
        }
        actor.setDeltaMovement(Vec3.ZERO);
        double travelled = Math.hypot(actor.getX() - before.x, actor.getZ() - before.z);
        setWalking(actor.onGround() && travelled > 1.0E-5);
        closePassedDoors(false);

        boolean reached = Math.hypot(target.x - actor.getX(), target.z - actor.getZ()) < 0.015
                && Math.abs(target.y - actor.getY()) < 0.08 && actor.onGround();
        if (reached) {
            blockedTicks = 0;
            if (++segmentIndex >= path.size()) {
                setWalking(false);
                closePassedDoors(true);
                done = true;
            }
            return;
        }
        // Waiting never accumulates speed or position debt. Replan a changed obstacle,
        // then abort playback cleanly if the authored destination is inaccessible.
        boolean stalled = Math.abs(yawError) < 60 && travelled < 1.0E-5
                && Math.abs(actor.getY() - before.y) < 1.0E-5;
        blockedTicks = stalled ? blockedTicks + 1 : 0;
        if (blockedTicks >= 40) {
            currentSpeed = 0;
            blockedTicks = 0;
            if (++replans > 2) failRoute("blocked route");
            planRoute();
        }
        if (ticksElapsed > 20 * 180) failRoute("walk timeout");
    }

    private void failRoute(String reason) {
        setWalking(false);
        closePassedDoors(true);
        throw new IllegalStateException("Cutscene walk " + actorTag + " to " + destination + ": " + reason);
    }

    @Override
    public boolean isComplete() { return done; }

    @Override
    public void onSkip(EventPlayer player) {
        setWalking(false);
        closePassedDoors(true);
        done = true;
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
        AABB swept = actor.getBoundingBox().expandTowards(nextPosition.subtract(actor.position())).inflate(0.05);
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(swept.minX, swept.minY, swept.minZ),
                BlockPos.containing(swept.maxX, swept.maxY, swept.maxZ))) {
            openDoorAt(pos);
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

    private static List<Vec3> buildPath(ClientLevel level, Mob actor, Vec3 start, Vec3 authoredEnd) {
        Vec3 end = supportedPosition(level, actor, authoredEnd.x, authoredEnd.y, authoredEnd.z, 0.6, 0.6);
        if (end == null) return List.of();
        // Most authored movements are short, clear approaches. Keep their exact X/Z
        // rather than making every actor zigzag through tile centres.
        List<Vec3> direct = directRoute(level, actor, start, end);
        if (!direct.isEmpty()) return direct;

        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(n -> n.fScore));
        Map<GridKey, Node> nodes = new HashMap<>();
        Set<GridKey> closed = new HashSet<>();
        GridKey startKey = key(start);
        GridKey endKey = key(end);
        Node startNode = new Node(startKey, start, null, 0, heuristic(startKey, endKey));
        nodes.put(startKey, startNode);
        open.add(startNode);
        int searched = 0;
        while (!open.isEmpty() && searched++ < MAX_SEARCH_NODES) {
            Node current = open.poll();
            if (!closed.add(current.key)) continue;
            if (current.key.x == endKey.x && current.key.z == endKey.z
                    && Math.abs(current.position.y - end.y) <= actor.maxUpStep() + 0.01) {
                List<Vec3> tail = directRoute(level, actor, current.position, end);
                if (!tail.isEmpty()) {
                    List<Vec3> result = new ArrayList<>();
                    for (Node n = current; n != null; n = n.parent) result.add(n.position);
                    java.util.Collections.reverse(result);
                    result.addAll(tail.subList(1, tail.size()));
                    return result;
                }
            }
            for (int[] dir : DIRS) {
                double x = current.key.x + dir[0] + .5;
                double z = current.key.z + dir[1] + .5;
                Vec3 nextPosition = supportedPosition(level, actor, x, current.position.y, z, actor.maxUpStep(), 1);
                if (nextPosition == null || directRoute(level, actor, current.position, nextPosition).isEmpty()) continue;
                GridKey nextKey = key(nextPosition);
                if (closed.contains(nextKey)) continue;
                double cost = current.gScore + current.position.distanceTo(nextPosition);
                Node existing = nodes.get(nextKey);
                if (existing == null || cost < existing.gScore) {
                    Node next = new Node(nextKey, nextPosition, current, cost, cost + heuristic(nextKey, endKey));
                    nodes.put(nextKey, next);
                    open.add(next);
                }
            }
        }
        return List.of();
    }

    private static GridKey key(Vec3 position) {
        return new GridKey(Mth.floor(position.x), Mth.floor(position.z), (int) Math.round(position.y * 16));
    }

    private static List<Vec3> directRoute(ClientLevel level, Mob actor, Vec3 start, Vec3 end) {
        int samples = Math.max(1, (int) Math.ceil(start.distanceTo(end) / .2));
        if (samples > 2048) return List.of();
        List<Vec3> points = new ArrayList<>();
        points.add(start);
        Vec3 previous = start;
        for (int i = 1; i <= samples; i++) {
            double t = (double) i / samples;
            Vec3 point = supportedPosition(level, actor, Mth.lerp(t, start.x, end.x), previous.y,
                    Mth.lerp(t, start.z, end.z), actor.maxUpStep(), 1);
            if (point == null) return List.of();
            // Preserve elevation transitions as waypoints; flat clear stretches stay straight.
            if (Math.abs(point.y - previous.y) > .001) {
                if (points.get(points.size() - 1).distanceToSqr(previous) > 1.0E-6) points.add(previous);
                points.add(point);
            }
            previous = point;
        }
        if (Math.abs(previous.y - end.y) > .08) return List.of();
        points.add(end);
        return points;
    }

    /** Resolve actual floor shapes, including thin paving and stairs. Air is never a route floor. */
    private static Vec3 supportedPosition(ClientLevel level, Mob actor, double x, double y, double z,
                                          double rise, double drop) {
        AABB feet = actor.getBoundingBox().move(x - actor.getX(), y - actor.getY(), z - actor.getZ());
        List<Double> heights = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(feet.minX), Mth.floor(y - drop - 1), Mth.floor(feet.minZ),
                Mth.floor(feet.maxX), Mth.floor(y + rise), Mth.floor(feet.maxZ))) {
            if (!level.hasChunkAt(pos)) return null;
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof DoorBlock) continue;
            for (AABB local : state.getCollisionShape(level, pos).toAabbs()) {
                AABB shape = local.move(pos);
                if (shape.maxX <= feet.minX + 1.0E-6 || shape.minX >= feet.maxX - 1.0E-6
                        || shape.maxZ <= feet.minZ + 1.0E-6 || shape.minZ >= feet.maxZ - 1.0E-6) continue;
                if (shape.maxY >= y - drop - .001 && shape.maxY <= y + rise + .001) heights.add(shape.maxY);
            }
        }
        heights.sort(Comparator.reverseOrder());
        for (double height : heights) {
            if (!level.getFluidState(BlockPos.containing(x, height, z)).isEmpty()) continue;
            if (canStandAt(level, actor, x, height, z)) return new Vec3(x, height, z);
        }
        return null;
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

    private record GridKey(int x, int z, int height) {
    }

    private static final class Node {
        private final GridKey key;
        private final Vec3 position;
        private final Node parent;
        private final double gScore;
        private final double fScore;

        private Node(GridKey key, Vec3 position, Node parent, double gScore, double fScore) {
            this.key = key;
            this.position = position;
            this.parent = parent;
            this.gScore = gScore;
            this.fScore = fScore;
        }
    }
}

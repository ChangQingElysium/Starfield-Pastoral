package com.stardew.craft.entity.npc;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.Set;

/**
 * Custom GroundPathNavigation that uses {@link NpcNodeEvaluator} instead of
 * the vanilla WalkNodeEvaluator. This makes NPCs prefer roads and paved
 * surfaces when planning paths.
 */
public class NpcPathNavigation extends GroundPathNavigation {
    private String lastStopDiagnostic = "";
    public String lastStopDiagnostic() { return lastStopDiagnostic; }
    private int initialNodeBudget;
    private BlockPos watchedNode;
    private double bestNodeDistance;
    private long lastNodeProgressTick;
    private boolean recoveryRequested;
    private boolean allowIncompleteRecomputation;
    private boolean preserveAuthoredTargetHeight;
    private int authoredTargetAccuracy = 1;


    public NpcPathNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new NpcNodeEvaluator();
        this.nodeEvaluator.setCanPassDoors(true);
        this.nodeEvaluator.setCanOpenDoors(true);
        this.nodeEvaluator.setCanFloat(false);
        initialNodeBudget=com.stardew.craft.npc.runtime.NpcNavigationPolicy.current().maxVisitedNodes();
        return new PathFinder(this.nodeEvaluator, initialNodeBudget);
    }
    public void refreshSearchBudget() {
        setMaxVisitedNodesMultiplier((float)com.stardew.craft.npc.runtime.NpcNavigationPolicy.current().maxVisitedNodes()/initialNodeBudget);
    }

    /** Admin-only comparison used by the NPC debug command to separate map gaps from evaluator regressions. */
    public Path createVanillaDiagnosticPath(BlockPos target) {
        var evaluator = new WalkNodeEvaluator();
        evaluator.setCanPassDoors(true);
        evaluator.setCanOpenDoors(true);
        evaluator.setCanFloat(false);
        return createDiagnosticPath(target, evaluator);
    }

    /** Admin-only fresh custom search, unaffected by the navigation object's cached path. */
    public Path createNpcDiagnosticPath(BlockPos target) {
        var evaluator = new NpcNodeEvaluator();
        evaluator.setCanPassDoors(true);
        evaluator.setCanOpenDoors(true);
        evaluator.setCanFloat(false);
        return createDiagnosticPath(target, evaluator);
    }

    private Path createDiagnosticPath(BlockPos target,
                                      net.minecraft.world.level.pathfinder.NodeEvaluator evaluator) {
        var policy = com.stardew.craft.npc.runtime.NpcNavigationPolicy.current();
        float followRange = policy.searchRange();
        int extent = (int) followRange + 8;
        var region = new PathNavigationRegion(level,
                mob.blockPosition().offset(-extent, -extent, -extent),
                mob.blockPosition().offset(extent, extent, extent));
        return new PathFinder(evaluator, policy.maxVisitedNodes())
                .findPath(region, mob, Set.of(target), followRange, 1, 1.0F);
    }

    /** Keep automatic path recomputation bounded too; stop/ordinary movement releases the restriction. */
    public boolean moveWithin(Vec3 target,double speed,com.stardew.craft.npc.runtime.NpcSquareArea area) {
        var evaluator=(NpcNodeEvaluator)nodeEvaluator;stop();evaluator.squareArea=area;
        boolean started=false;
        try {
            var candidate=createPath(BlockPos.containing(target),0);
            if(candidate==null||!candidate.canReach())return false;
            for(int i=0;i<candidate.getNodeCount();i++)if(!area.contains(candidate.getEntityPosAtNode(mob,i),mob.getBbWidth()/2.))return false;
            started=moveTo(candidate,speed);return started;
        } finally {if(!started)evaluator.squareArea=null;}
    }

    @Override
    public boolean moveTo(double x,double y,double z,double speed) {
        ((NpcNodeEvaluator)nodeEvaluator).squareArea=null;
        allowIncompleteRecomputation=false;
        preserveAuthoredTargetHeight=false;
        return super.moveTo(x,y,z,speed);
    }

    /**
     * Route to an authored portal marker without GroundPathNavigation rewriting a
     * solid marker to the first air block above its entire column. Indoor exit
     * markers commonly live in a door frame; the route only needs to reach the
     * closest node inside the movement service's portal approach radius.
     */
    public boolean moveToAuthoredPortalApproach(Vec3 target, double speed) {
        return moveToAuthoredTarget(target, speed, 1);
    }

    public boolean moveToAuthoredTarget(Vec3 target, double speed, int accuracy) {
        ((NpcNodeEvaluator)nodeEvaluator).squareArea=null;
        allowIncompleteRecomputation=false;
        preserveAuthoredTargetHeight=true;
        authoredTargetAccuracy=accuracy;
        Path candidate=createAuthoredHeightPath(BlockPos.containing(target));
        boolean started=candidate!=null&&super.moveTo(candidate,speed);
        if(!started)preserveAuthoredTargetHeight=false;
        return started;
    }

    private Path createAuthoredHeightPath(BlockPos target) {
        // The Set overload is implemented by PathNavigation and therefore skips
        // GroundPathNavigation#createPath(BlockPos), whose solid-column scan is
        // incorrect for an interaction marker embedded in a doorway.
        return super.createPath(Collections.singleton(target),authoredTargetAccuracy);
    }

    /**
     * A nearby blocked furniture centre may intentionally use the closest reachable
     * path endpoint. Every other authored route requires a path which reaches its
     * target, including paths recreated asynchronously after a door or chunk update.
     */
    public void allowIncompleteRecomputation(boolean allow) {
        allowIncompleteRecomputation=allow;
    }

    @Override
    public void recomputePath() {
        if(preserveAuthoredTargetHeight&&getTargetPos()!=null) {
            if(level.getGameTime()-timeLastRecompute>20L) {
                path=null;
                path=createAuthoredHeightPath(getTargetPos());
                timeLastRecompute=level.getGameTime();
                hasDelayedRecomputation=false;
            } else {
                hasDelayedRecomputation=true;
            }
            return;
        }
        super.recomputePath();
        if (!allowIncompleteRecomputation && path != null && !path.canReach()) {
            // PathNavigation keeps targetPos after stop(). A delayed recomputation can
            // therefore resurrect the exact partial path which the runtime rejected,
            // bypassing its validation and walking an NPC into a dead end.
            stop();
        }
    }

    @Override
    protected void followThePath() {
        Vec3 position = getTempMobPos();
        Vec3 waypoint = path.getNextEntityPos(mob);
        maxDistanceToWaypoint = mob.getBbWidth() > .75F ? mob.getBbWidth() / 2 : .75F - mob.getBbWidth() / 2;
        boolean near = Math.abs(mob.getX() - waypoint.x) <= maxDistanceToWaypoint
                && Math.abs(mob.getZ() - waypoint.z) <= maxDistanceToWaypoint
                && Math.abs(mob.getY() - waypoint.y) < 1;
        if (near) {
            int next = path.getNextNodeIndex() + 1;
            boolean clearTurn = true;
            if (next < path.getNodeCount()) {
                Vec3 following = path.getEntityPosAtNode(mob, next);
                // A waypoint radius is not permission to cut across a wall corner.
                // For level turns check the whole body, not just the centre ray.
                if (Math.abs(getGroundY(following) - mob.getY()) < .05) {
                    Vec3 delta = following.subtract(mob.position());
                    clearTurn = level.noBlockCollision(mob, mob.getBoundingBox()
                            .expandTowards(delta.x, 0, delta.z).deflate(1.0E-7));
                }
            }
            // Door frames and another actor's push can keep a body a few tenths from
            // the mathematical node centre. Requiring 0.1 blocks made the first node
            // inside the saloon an infinite target. Once most of the normal waypoint
            // radius has been consumed, advance and let block collision constrain the
            // next segment; the full-body shortcut check above still handles early turns.
            double reachedRadius = Math.max(0.1D, maxDistanceToWaypoint * 0.9D);
            if (clearTurn || mob.position().subtract(waypoint).horizontalDistanceSqr() < reachedRadius * reachedRadius) {
                path.advance();
            }
        }
        doStuckDetection(position);
    }

    @Override
    protected void doStuckDetection(Vec3 position) {
        BlockPos activeNode = !isDone() ? path.getNextNodePos() : null;
        if (!isDone()) {
            BlockPos next = path.getNextNodePos();
            double distance = mob.position().distanceTo(path.getNextEntityPos(mob));
            long now = level.getGameTime();
            if (!next.equals(watchedNode) || distance < bestNodeDistance - .1) {
                watchedNode = next;
                bestNodeDistance = distance;
                lastNodeProgressTick = now;
            } else if (now - lastNodeProgressTick >= 60) {
                recoveryRequested = true;
            }
        }
        super.doStuckDetection(position);
        if (activeNode != null && isDone()) {
            // Vanilla's timeout calls stop(). Preserve the failure for the executor
            // before its normal idle-path branch can erase it with a fresh moveTo.
            watchedNode = activeNode;
            recoveryRequested = true;
        }
    }

    public boolean needsRecovery() {
        return recoveryRequested;
    }

    /** Remember the failed approach briefly, then force a fresh search instead of cached createPath. */
    public void prepareRecovery() {
        BlockPos failed = !isDone() ? path.getNextNodePos() : watchedNode;
        if (failed != null) ((NpcNodeEvaluator) nodeEvaluator).penalize(failed, level.getGameTime());
        stop();
    }

    @Override
    public void stop() {
        if (Boolean.getBoolean("stardewcraft.npcScheduleAudit")) {
            lastStopDiagnostic = StackWalker.getInstance().walk(frames -> frames.skip(1).limit(4)
                    .map(StackWalker.StackFrame::toString).collect(java.util.stream.Collectors.joining(" <- ")));
        }
        super.stop();
        ((NpcNodeEvaluator)nodeEvaluator).squareArea=null;
        allowIncompleteRecomputation=false;
        preserveAuthoredTargetHeight=false;
        watchedNode = null;
        recoveryRequested = false;
    }
}

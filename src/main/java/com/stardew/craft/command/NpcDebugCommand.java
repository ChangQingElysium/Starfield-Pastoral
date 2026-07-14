package com.stardew.craft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.npc.runtime.NpcRuntimeDataManager;
import com.stardew.craft.npc.runtime.NpcRuntimeState;
import com.stardew.craft.npc.runtime.NpcCentralMovementService;
import com.stardew.craft.npc.runtime.NpcPathfinder;
import com.stardew.craft.npc.runtime.NpcSpawnManager;
import com.stardew.craft.npc.runtime.NpcScheduleRuntimeService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@SuppressWarnings("null")
public final class NpcDebugCommand {
    private static final net.minecraft.world.phys.AABB GLOBAL_NPC_SCAN = new net.minecraft.world.phys.AABB(
        -30_000_000D, -2_048D, -30_000_000D,
        30_000_000D, 4_096D, 30_000_000D
    );

    private NpcDebugCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("stardew")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("npc")
                    .then(Commands.literal("debug")
                        .executes(NpcDebugCommand::debugAll)
                        .then(Commands.argument("npcId", StringArgumentType.word())
                            .executes(NpcDebugCommand::debugSingle)
                        )
                    )
                )
        );
    }

    private static int debugAll(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getServer().getLevel(ModDimensions.STARDEW_VALLEY);
        if (level == null) {
            context.getSource().sendFailure(Component.translatable("stardewcraft.command.npc.dimension_unavailable"));
            return 0;
        }

        Map<String, NpcRuntimeState> states = NpcRuntimeDataManager.get(level).states();
        if (states.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.translatable("stardewcraft.command.npc.no_runtime_state"), false);
            return 1;
        }

        List<String> ids = new ArrayList<>(states.keySet());
        ids.sort(Comparator.naturalOrder());
        int shown = 0;

        context.getSource().sendSuccess(() -> Component.translatable("stardewcraft.command.npc.overview_header", 20), false);
        for (String npcId : ids) {
            if (shown >= 20) {
                break;
            }
            shown++;
            sendNpcSummary(context.getSource(), level, npcId, states.get(npcId));
        }

        int hidden = ids.size() - shown;
        if (hidden > 0) {
            int finalHidden = hidden;
            context.getSource().sendSuccess(() -> Component.translatable(
                "stardewcraft.command.npc.hidden_count", finalHidden), false);
        }
        return 1;
    }

    private static int debugSingle(CommandContext<CommandSourceStack> context) {
        String npcId = StringArgumentType.getString(context, "npcId").toLowerCase(Locale.ROOT);
        ServerLevel level = context.getSource().getServer().getLevel(ModDimensions.STARDEW_VALLEY);
        if (level == null) {
            context.getSource().sendFailure(Component.translatable("stardewcraft.command.npc.dimension_unavailable"));
            return 0;
        }

        NpcRuntimeState state = NpcRuntimeDataManager.get(level).states().get(npcId);
        if (state == null) {
            context.getSource().sendFailure(Component.translatable("stardewcraft.command.npc.not_found", npcId));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.translatable("stardewcraft.command.npc.details_header", npcId), false);
        sendNpcDetails(context.getSource(), level, npcId, state);
        return 1;
    }

    private static void sendNpcSummary(CommandSourceStack source, ServerLevel level, String npcId, NpcRuntimeState state) {
        List<StardewNpcEntity> entities = level.getEntitiesOfClass(
            StardewNpcEntity.class,
            GLOBAL_NPC_SCAN,
            entity -> npcId.equals(entity.getNpcId())
        );

        StardewNpcEntity entity = entities.isEmpty() ? null : entities.get(0);
        Vec3 defaultPosition = entity != null ? entity.position() : Vec3.atCenterOf(level.getSharedSpawnPos());
        NpcScheduleRuntimeService.TargetPoint target = NpcScheduleRuntimeService.resolveWorldTarget(level, state, defaultPosition);

        Vec3 t = target == null ? null : target.position();
        String targetText = t == null ? "<none>" : String.format(Locale.ROOT, "(%.1f, %.1f, %.1f)", t.x, t.y, t.z);
        source.sendSuccess(() -> Component.translatable(
            "stardewcraft.command.npc.summary",
            npcId, state.activeScheduleKey(), state.scheduleCheckpoint(), state.scheduleNodeIndex(),
            state.locationName(), state.facing(), targetText,
            Component.translatable(entity == null
                ? "stardewcraft.command.npc.entity.missing"
                : "stardewcraft.command.npc.entity.online")), false);
    }

    private static void sendNpcDetails(CommandSourceStack source, ServerLevel level, String npcId, NpcRuntimeState state) {
        List<StardewNpcEntity> entities = level.getEntitiesOfClass(
            StardewNpcEntity.class,
            GLOBAL_NPC_SCAN,
            entity -> npcId.equals(entity.getNpcId())
        );

        StardewNpcEntity entity = entities.isEmpty() ? null : entities.get(0);
        Vec3 defaultPosition = entity != null ? entity.position() : Vec3.atCenterOf(level.getSharedSpawnPos());
        NpcScheduleRuntimeService.TargetPoint target = NpcScheduleRuntimeService.resolveWorldTarget(level, state, defaultPosition);

        source.sendSuccess(() -> Component.translatable("stardewcraft.command.npc.basic_header"), false);
        source.sendSuccess(() -> Component.translatable(
            "stardewcraft.command.npc.schedule",
            state.activeScheduleKey(), state.scheduleCheckpoint(), state.scheduleNodeIndex()), false);
        source.sendSuccess(() -> Component.translatable(
            "stardewcraft.command.npc.location",
            state.locationName(), state.tileX(), state.tileY(), state.facing(),
            state.routeBehaviorToken().isBlank() ? "-" : state.routeBehaviorToken(),
            state.namedPointId().isBlank() ? "-" : state.namedPointId()), false);

        if (entity == null) {
            source.sendSuccess(() -> Component.translatable("stardewcraft.command.npc.entity_header"), false);
            source.sendSuccess(() -> Component.translatable("stardewcraft.command.npc.entity_missing"), false);
            source.sendSuccess(() -> Component.translatable("stardewcraft.command.npc.target",
                formatVec(target == null ? null : target.position()), target != null && target.indoorTarget()), false);
            return;
        }

        Vec3 pos = entity.position();
        Vec3 t = target == null ? null : target.position();
        if (t == null) {
            source.sendSuccess(() -> Component.translatable("stardewcraft.command.npc.entity_header"), false);
            source.sendSuccess(() -> Component.translatable("stardewcraft.command.npc.no_target"), false);
            return;
        }
        double dx = t.x - pos.x;
        double dz = t.z - pos.z;
        double dist2d = Math.sqrt(dx * dx + dz * dz);
        var nav = entity.getNavigation();
        boolean hasPath = nav.getPath() != null;
        boolean pathDone = !hasPath || nav.getPath().isDone();
        var navTarget = nav.getTargetPos();

        source.sendSuccess(() -> Component.translatable("stardewcraft.command.npc.entity_header"), false);
        source.sendSuccess(() -> Component.translatable(
            "stardewcraft.command.npc.entity_position",
            formatVec(pos), formatVec(t), String.format(Locale.ROOT, "%.2f", dist2d), target.indoorTarget()), false);
        source.sendSuccess(() -> Component.translatable(
            "stardewcraft.command.npc.navigation",
            hasPath, pathDone, navTarget == null ? "<none>" : navTarget.toShortString(), entities.size()), false);

        NpcCentralMovementService.DebugSnapshot movement = NpcCentralMovementService.getDebugSnapshot(npcId);
        if (movement != null) {
            source.sendSuccess(() -> Component.translatable("stardewcraft.command.npc.movement_header"), false);
            source.sendSuccess(() -> Component.literal(String.format(
                Locale.ROOT,
                "  stage=%s loc=%s point=%s path=%d/%d forcedTp=%s",
                movement.stage,
                movement.location,
                movement.pointId,
                movement.pathIndex,
                movement.pathSize,
                movement.forcedTeleportUsed
            )), false);
            source.sendSuccess(() -> Component.literal(String.format(
                Locale.ROOT,
                "  target=%s next=%s repath=%s noPathTicks=%d forcedChunk=%s",
                formatVec(movement.target),
                formatVec(movement.nextWaypoint),
                movement.repathReason,
                movement.noPathTicks,
                movement.forcedTargetChunk
            )), false);
            source.sendSuccess(() -> Component.literal(String.format(
                Locale.ROOT,
                "  routeStatus=%s reason=%s missingPoint=%s missingLink=%s",
                movement.routeStatus,
                movement.routeDiagnosticReason,
                movement.missingPointId == null || movement.missingPointId.isBlank() ? "<none>" : movement.missingPointId,
                movement.missingPortalLinkId == null || movement.missingPortalLinkId.isBlank() ? "<none>" : movement.missingPortalLinkId
            )), false);
        }

        NpcScheduleRuntimeService.ScheduleKeyTrace keyTrace = NpcScheduleRuntimeService.getLastKeyTrace(npcId);
        if (keyTrace != null) {
            source.sendSuccess(() -> Component.translatable("stardewcraft.command.npc.schedule_key_header"), false);
            source.sendSuccess(() -> Component.literal(String.format(
                Locale.ROOT,
                "  day=%d season=%s weekday=%s weather=%s hearts=%d selected=%s",
                keyTrace.day(),
                keyTrace.season(),
                keyTrace.weekday(),
                keyTrace.weather().isBlank() ? "<none>" : keyTrace.weather(),
                keyTrace.hearts(),
                keyTrace.selectedKey()
            )), false);
            source.sendSuccess(() -> Component.literal("  candidates=" + compactList(keyTrace.candidates(), 8)), false);
            source.sendSuccess(() -> Component.literal("  rejects=" + compactList(keyTrace.rejections(), 5)), false);
        }

        NpcSpawnManager.SpawnDebugSnapshot spawn = NpcSpawnManager.getDebugSnapshot(level, npcId);
        if (spawn != null) {
            source.sendSuccess(() -> Component.translatable("stardewcraft.command.npc.spawn_header"), false);
            source.sendSuccess(() -> Component.literal(String.format(
                Locale.ROOT,
                "  tracked=%s alive=%s loaded=%d first=%s miss=%d ageTicks=%d forcedChunk=%s",
                spawn.trackedUuid(),
                spawn.trackedAlive(),
                spawn.loadedCount(),
                spawn.firstLoadedUuid(),
                spawn.missCount(),
                spawn.spawnAgeTicks(),
                spawn.forcedChunk()
            )), false);
        }

        // Pathfinding diagnostic: check canStand at entity pos and target pos
        if (entity != null && target.position() != null) {
            BlockPos entityBP = entity.blockPosition();
            BlockPos targetBP = BlockPos.containing(target.position());
            boolean entityLoaded = level.isLoaded(entityBP);
            boolean targetLoaded = level.isLoaded(targetBP);
            boolean entityCanStand = entityLoaded && NpcPathfinder.canStand(level, entityBP);
            boolean targetCanStand = targetLoaded && NpcPathfinder.canStand(level, targetBP);
            BlockPos entityWalkable = NpcPathfinder.nearestWalkable(level, entity.position());
            BlockPos targetWalkable = NpcPathfinder.nearestWalkable(level, target.position());
            source.sendSuccess(() -> Component.translatable("stardewcraft.command.npc.pathfinding_header"), false);
            source.sendSuccess(() -> Component.literal(String.format(
                Locale.ROOT,
                "  entityPos=%s loaded=%s canStand=%s walkable=%s",
                entityBP.toShortString(), entityLoaded, entityCanStand,
                entityWalkable == null ? "null" : entityWalkable.toShortString()
            )), false);
            source.sendSuccess(() -> Component.literal(String.format(
                Locale.ROOT,
                "  targetPos=%s loaded=%s canStand=%s walkable=%s",
                targetBP.toShortString(), targetLoaded, targetCanStand,
                targetWalkable == null ? "null" : targetWalkable.toShortString()
            )), false);
            if (entityLoaded) {
                BlockState eFeet = level.getBlockState(entityBP);
                BlockState eHead = level.getBlockState(entityBP.above());
                BlockState eBelow = level.getBlockState(entityBP.below());
                source.sendSuccess(() -> Component.literal(String.format(
                    Locale.ROOT,
                    "  entity blocks: feet=%s head=%s below=%s",
                    eFeet.getBlock().getDescriptionId(),
                    eHead.getBlock().getDescriptionId(),
                    eBelow.getBlock().getDescriptionId()
                )), false);
            }
            if (targetLoaded) {
                BlockState tFeet = level.getBlockState(targetBP);
                BlockState tHead = level.getBlockState(targetBP.above());
                BlockState tBelow = level.getBlockState(targetBP.below());
                source.sendSuccess(() -> Component.literal(String.format(
                    Locale.ROOT,
                    "  target blocks: feet=%s head=%s below=%s",
                    tFeet.getBlock().getDescriptionId(),
                    tHead.getBlock().getDescriptionId(),
                    tBelow.getBlock().getDescriptionId()
                )), false);
            }
        }
    }

    private static String formatVec(Vec3 v) {
        if (v == null) {
            return "<none>";
        }
        return String.format(Locale.ROOT, "(%.2f, %.2f, %.2f)", v.x, v.y, v.z);
    }

    private static String compactList(List<String> values, int max) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        int end = Math.min(values.size(), Math.max(1, max));
        String suffix = values.size() > end ? " ..." : "";
        return values.subList(0, end).toString() + suffix;
    }
}

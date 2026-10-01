package com.stardew.craft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceInitializer;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.farm.FarmPermissionManager;
import com.stardew.craft.farm.FarmType;
import com.stardew.craft.greenhouse.GreenhouseManager;
import com.stardew.craft.interior.FarmCaveData;
import com.stardew.craft.interior.FarmCaveLayout;
import com.stardew.craft.interior.FarmCaveRuntime;
import com.stardew.craft.interior.PlayerInteriorAllocator;
import com.stardew.craft.lostandfound.LostAndFoundData;
import com.stardew.craft.warp.ModTeleport;
import com.stardew.craft.world.event.WorldEventSavedData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.event.server.ServerStoppedEvent;
import com.stardew.craft.port.net.neoforged.neoforge.event.tick.ServerTickEvent;

/** OP-only multi-farm creation, selection, travel and complete removal tools. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class FarmDebugCommand {
    private static final int MAX_POSITIONS_PER_TICK = 16_384;
    private static final long MAX_NANOS_PER_TICK = 3_000_000L;
    private static final BlockPos SAFE_EXIT = new BlockPos(0, 70, 0);
    private static final Map<MinecraftServer, List<RemovalTask>> REMOVALS = new HashMap<>();

    private FarmDebugCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("stardew")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("debug")
                        .then(Commands.literal("farm")
                                .then(Commands.literal("add")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("type", StringArgumentType.word())
                                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                                Arrays.stream(FarmType.values()).map(FarmType::getId), builder))
                                                        .executes(FarmDebugCommand::add))))
                                .then(Commands.literal("list")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(FarmDebugCommand::list)))
                                .then(Commands.literal("visit")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("farm_id", UuidArgument.uuid())
                                                        .executes(FarmDebugCommand::visit))))
                                .then(Commands.literal("delete")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("farm_id", UuidArgument.uuid())
                                                        .then(Commands.literal("confirm")
                                                                .executes(FarmDebugCommand::delete))))))));
    }

    private static int add(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        String requested = StringArgumentType.getString(context, "type");
        FarmType type = Arrays.stream(FarmType.values())
                .filter(value -> value.getId().equals(requested))
                .findFirst().orElse(null);
        if (type == null) {
            context.getSource().sendFailure(Component.translatable(
                    "stardewcraft.command.debug.farm.unknown_type", requested));
            return 0;
        }
        ServerLevel level = context.getSource().getServer().getLevel(ModDimensions.STARDEW_VALLEY);
        if (level == null) {
            context.getSource().sendFailure(Component.translatable("stardewcraft.farm.not_found"));
            return 0;
        }
        FarmInstanceRegistry registry = FarmInstanceRegistry.get(context.getSource().getServer());
        int sequence = registry.getDebugFarms(target.getUUID()).size() + 1;
        String playerName = com.stardew.craft.player.PlayerDisplayName.get(target);
        FarmInstance farm = registry.createDebugFarm(target.getUUID(), playerName,
                playerName + " Debug " + sequence, type);
        context.getSource().sendSuccess(() -> Component.translatable(
                "stardewcraft.command.debug.farm.preparing", farm.getInstanceId().toString(), type.getId()), true);
        FarmInstanceInitializer.prepareFarmForTeleport(level, farm).thenAcceptAsync(ready -> {
            if (!ready) {
                context.getSource().sendFailure(Component.translatable(
                        "stardewcraft.command.debug.farm.prepare_failed", farm.getInstanceId().toString()));
                beginRemoval(context.getSource(), target, farm, true);
                return;
            }
            if (target.isRemoved()) return;
            registry.selectFarm(target.getUUID(), farm.getInstanceId());
            ModTeleport.to(target, level, farm.getSpawnPoint(), farm.getSpawnYaw(), 0.0F);
            context.getSource().sendSuccess(() -> Component.translatable(
                    "stardewcraft.command.debug.farm.created", farm.getInstanceId().toString(),
                    farm.getSlotIndex(), type.getId()), true);
        }, context.getSource().getServer());
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        FarmInstanceRegistry registry = FarmInstanceRegistry.get(context.getSource().getServer());
        List<FarmInstance> farms = registry.getFarmsForPlayer(target.getUUID());
        FarmInstance selected = registry.getFarmForPlayer(target.getUUID());
        context.getSource().sendSuccess(() -> Component.translatable(
                "stardewcraft.command.debug.farm.list_header",
                com.stardew.craft.player.PlayerDisplayName.get(target), farms.size()), false);
        for (FarmInstance farm : farms) {
            boolean active = selected != null && selected.getInstanceId().equals(farm.getInstanceId());
            context.getSource().sendSuccess(() -> Component.translatable(
                    "stardewcraft.command.debug.farm.list_entry",
                    active ? "*" : "-", farm.getInstanceId().toString(), farm.getFarmLayoutId().toString(),
                    farm.getSlotIndex(), farm.getFarmName()), false);
        }
        return farms.size();
    }

    private static int visit(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        UUID instanceId = UuidArgument.getUuid(context, "farm_id");
        FarmInstanceRegistry registry = FarmInstanceRegistry.get(context.getSource().getServer());
        FarmInstance farm = registry.getFarmByInstanceId(instanceId);
        if (farm == null || !farm.isFarmer(target.getUUID())
                || !registry.selectFarm(target.getUUID(), instanceId)) {
            context.getSource().sendFailure(Component.translatable(
                    "stardewcraft.command.debug.farm.not_owned", instanceId.toString()));
            return 0;
        }
        ServerLevel level = context.getSource().getServer().getLevel(ModDimensions.STARDEW_VALLEY);
        if (level == null) return 0;
        FarmInstanceInitializer.prepareFarmForTeleport(level, farm).thenAcceptAsync(ready -> {
            if (!ready || target.isRemoved()) {
                context.getSource().sendFailure(Component.translatable(
                        "stardewcraft.command.debug.farm.prepare_failed", instanceId.toString()));
                return;
            }
            ModTeleport.to(target, level, farm.getSpawnPoint(), farm.getSpawnYaw(), 0.0F);
            context.getSource().sendSuccess(() -> Component.translatable(
                    "stardewcraft.command.debug.farm.visited", instanceId.toString()), false);
        }, context.getSource().getServer());
        return 1;
    }

    private static int delete(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        UUID instanceId = UuidArgument.getUuid(context, "farm_id");
        FarmInstanceRegistry registry = FarmInstanceRegistry.get(context.getSource().getServer());
        FarmInstance farm = registry.getFarmByInstanceId(instanceId);
        if (farm == null || !registry.isDebugFarm(instanceId, target.getUUID())) {
            context.getSource().sendFailure(Component.translatable(
                    "stardewcraft.command.debug.farm.not_debug_owned", instanceId.toString()));
            return 0;
        }
        if (!beginRemoval(context.getSource(), target, farm, false)) return 0;
        return 1;
    }

    private static boolean beginRemoval(CommandSourceStack source, ServerPlayer controller,
            FarmInstance farm, boolean silentStart) {
        MinecraftServer server = source.getServer();
        List<RemovalTask> tasks = REMOVALS.computeIfAbsent(server, ignored -> new ArrayList<>());
        if (tasks.stream().anyMatch(task -> task.farm.getInstanceId().equals(farm.getInstanceId()))) {
            source.sendFailure(Component.translatable("stardewcraft.command.debug.farm.delete_busy"));
            return false;
        }
        ServerLevel level = server.getLevel(ModDimensions.STARDEW_VALLEY);
        if (level == null) return false;
        FarmInstanceRegistry registry = FarmInstanceRegistry.get(server);
        if (!registry.beginDebugFarmDeletion(controller.getUUID(), farm.getInstanceId())) {
            source.sendFailure(Component.translatable("stardewcraft.command.debug.farm.delete_busy"));
            return false;
        }
        evacuate(level, farm);
        cleanupLogical(level, farm);
        tasks.add(new RemovalTask(source, controller.getUUID(), farm,
                new ClearJob(farm.getFarmBoundsMin().below(), farm.getFarmBoundsMax())));
        if (!silentStart) source.sendSuccess(() -> Component.translatable(
                "stardewcraft.command.debug.farm.delete_started", farm.getInstanceId().toString()), true);
        return true;
    }

    private static void evacuate(ServerLevel level, FarmInstance farm) {
        FarmCaveData.Entry cave = FarmCaveData.get(level).find(farm.getInstanceId());
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.serverLevel() != level) continue;
            boolean inFarm = farm.contains(player.blockPosition());
            boolean inCave = cave != null && FarmCaveLayout.contains(cave.origin(), player.blockPosition());
            if (inFarm || inCave) ModTeleport.to(player, level, SAFE_EXIT, 180.0F, 0.0F);
        }
        BlockPos min = farm.getFarmBoundsMin().below();
        BlockPos max = farm.getFarmBoundsMax();
        AABB bounds = new AABB(min.getX(), min.getY(), min.getZ(),
                max.getX() + 1.0, max.getY() + 1.0, max.getZ() + 1.0);
        for (Entity entity : level.getEntities((Entity) null, bounds,
                entity -> !(entity instanceof ServerPlayer))) entity.discard();
    }

    private static void cleanupLogical(ServerLevel level, FarmInstance farm) {
        UUID registryKey = FarmInstanceRegistry.get(level.getServer()).getRegistryKey(farm);
        if (registryKey != null) {
            GreenhouseManager.get(level).clearForOwner(registryKey);
            FarmPermissionManager.get().clearAllForOwner(registryKey);
            LostAndFoundData.get(level).removeFarm(registryKey);
            PlayerInteriorAllocator.get(level).removeDebugOwner(level, registryKey);
        }
        WorldEventSavedData.get(level.getServer()).remove(farm.getInstanceId());
        com.stardew.craft.floor.SurfaceFloorData.get(level)
                .removeRegion(farm.getFarmBoundsMin().below(), farm.getFarmBoundsMax());
        FarmCaveRuntime.retire(level, farm.getInstanceId());
        com.stardew.craft.animal.runtime.LivestockService.recover(level.getServer());
        var buildings = com.stardew.craft.building.runtime.BuildingWorldData.get(level.getServer());
        var homes = buildings.all().stream().filter(record -> record.farmId().equals(farm.getInstanceId()))
                .map(com.stardew.craft.building.runtime.BuildingRecord::id).collect(java.util.stream.Collectors.toSet());
        com.stardew.craft.animal.runtime.LivestockWorldData.get(level.getServer())
                .removeFarm(farm.getInstanceId(), homes);
        com.stardew.craft.pet.PetWorldData.get(level.getServer()).removeFarm(farm.getInstanceId());
        buildings.removeFarm(farm.getInstanceId());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ServerLevel level = event.getServer().getLevel(ModDimensions.STARDEW_VALLEY);
        if (level == null) return;
        List<RemovalTask> tasks = REMOVALS.computeIfAbsent(
                event.getServer(), ignored -> new ArrayList<>());
        FarmInstanceRegistry registry = FarmInstanceRegistry.get(event.getServer());
        for (UUID instanceId : registry.getPendingDebugFarmDeletions()) {
            if (tasks.stream().anyMatch(task -> task.farm.getInstanceId().equals(instanceId))) continue;
            FarmInstance farm = registry.getFarmByInstanceId(instanceId);
            UUID controller = registry.getDebugFarmController(instanceId);
            if (farm == null || controller == null) continue;
            evacuate(level, farm);
            cleanupLogical(level, farm);
            tasks.add(new RemovalTask(event.getServer().createCommandSourceStack(), controller, farm,
                    new ClearJob(farm.getFarmBoundsMin().below(), farm.getFarmBoundsMax())));
        }
        if (tasks.isEmpty()) {
            REMOVALS.remove(event.getServer());
            return;
        }
        long deadline = System.nanoTime() + MAX_NANOS_PER_TICK;
        for (RemovalTask task : List.copyOf(tasks)) {
            task.job.process(level, MAX_POSITIONS_PER_TICK, deadline);
            if (!task.job.complete) break;
            tasks.remove(task);
            FarmInstance removed = FarmInstanceRegistry.get(event.getServer())
                    .deleteDebugFarm(task.controller, task.farm.getInstanceId());
            if (removed != null) task.source.sendSuccess(() -> Component.translatable(
                    "stardewcraft.command.debug.farm.deleted", removed.getInstanceId().toString(),
                    task.job.cleared), true);
            if (System.nanoTime() >= deadline) break;
        }
        if (tasks.isEmpty()) REMOVALS.remove(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        REMOVALS.remove(event.getServer());
    }

    private static final class ClearJob {
        private final Iterator<BlockPos> positions;
        private long cleared;
        private boolean complete;

        private ClearJob(BlockPos min, BlockPos max) {
            positions = BlockPos.betweenClosed(min, max).iterator();
        }

        private void process(ServerLevel level, int limit, long deadline) {
            int processed = 0;
            while (processed < limit && positions.hasNext()) {
                BlockPos pos = positions.next();
                if (!level.getBlockState(pos).isAir()
                        && level.setBlock(pos, Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS)) {
                    cleared++;
                }
                processed++;
                if ((processed & 127) == 0 && System.nanoTime() >= deadline) break;
            }
            complete = !positions.hasNext();
        }
    }

    private record RemovalTask(CommandSourceStack source, UUID controller,
                               FarmInstance farm, ClearJob job) {}
}

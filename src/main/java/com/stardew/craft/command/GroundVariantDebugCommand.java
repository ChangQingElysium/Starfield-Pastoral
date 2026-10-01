package com.stardew.craft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.terrain.TerrainVariants;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.event.server.ServerStoppedEvent;
import com.stardew.craft.port.net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Temporary repair command for terrain placed by commands before variants were selected. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class GroundVariantDebugCommand {
    private static final int MAX_POSITIONS_PER_TICK = 8_192;
    private static final long MAX_NANOS_PER_TICK = 2_000_000L;
    private static final Map<MinecraftServer, ActiveTask> ACTIVE_TASKS = new HashMap<>();

    private GroundVariantDebugCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("stardew")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("debug")
                        .then(Commands.literal("reroll_ground_variants")
                                .then(Commands.argument("from", BlockPosArgument.blockPos())
                                        .then(Commands.argument("to", BlockPosArgument.blockPos())
                                                .executes(GroundVariantDebugCommand::execute))))));
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        if (ACTIVE_TASKS.containsKey(server)) {
            source.sendFailure(Component.translatable("stardewcraft.command.debug.ground_variants.busy"));
            return 0;
        }

        BlockPos from = BlockPosArgument.getBlockPos(context, "from");
        BlockPos to = BlockPosArgument.getBlockPos(context, "to");
        ACTIVE_TASKS.put(server, new ActiveTask(
                source.getLevel().dimension(), source, new BatchJob(from, to)));
        source.sendSuccess(() -> Component.translatable(
                "stardewcraft.command.debug.ground_variants.started"), true);
        return 1;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ActiveTask active = ACTIVE_TASKS.get(server);
        if (active == null) return;

        ServerLevel level = server.getLevel(active.dimension());
        if (level == null) {
            ACTIVE_TASKS.remove(server);
            return;
        }

        long deadline = System.nanoTime() + MAX_NANOS_PER_TICK;
        active.job().processBatch(level, level.getRandom(), MAX_POSITIONS_PER_TICK, deadline);
        if (!active.job().isComplete()) return;

        ACTIVE_TASKS.remove(server);
        Result result = active.job().result();
        active.source().sendSuccess(() -> Component.translatable(
                "stardewcraft.command.debug.ground_variants.success",
                result.matched(), result.changed(), result.skippedUnloaded()), true);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE_TASKS.remove(event.getServer());
    }

    public static final class BatchJob {
        private final Iterator<BlockPos> positions;
        private long matched;
        private long changed;
        private long skippedUnloaded;
        private boolean complete;

        public BatchJob(BlockPos from, BlockPos to) {
            positions = BlockPos.betweenClosed(from, to).iterator();
        }

        public int processBatch(ServerLevel level, RandomSource random, int maxPositions) {
            return processBatch(level, random, maxPositions, Long.MAX_VALUE);
        }

        private int processBatch(ServerLevel level, RandomSource random, int maxPositions, long deadline) {
            if (complete || maxPositions <= 0) return 0;

            int processed = 0;
            while (processed < maxPositions && positions.hasNext()) {
                processPosition(level, random, positions.next());
                processed++;
                if ((processed & 63) == 0 && System.nanoTime() >= deadline) break;
            }
            complete = !positions.hasNext();
            return processed;
        }

        private void processPosition(ServerLevel level, RandomSource random, BlockPos pos) {
            if (level.isOutsideBuildHeight(pos)) return;
            if (!level.hasChunkAt(pos)) {
                skippedUnloaded++;
                return;
            }
            var current = level.getBlockState(pos);
            var property = TerrainVariants.property(current);
            if (property != TerrainVariants.GRASS && property != TerrainVariants.DIRT) return;
            matched++;
            var next = TerrainVariants.randomGroundVariant(current, random);
            if (!next.equals(current) && level.setBlock(pos, next, Block.UPDATE_CLIENTS)) changed++;
        }

        public boolean isComplete() {
            return complete;
        }

        public Result result() {
            return new Result(matched, changed, skippedUnloaded);
        }
    }

    private record ActiveTask(ResourceKey<Level> dimension, CommandSourceStack source, BatchJob job) {
    }

    public record Result(long matched, long changed, long skippedUnloaded) {
    }
}

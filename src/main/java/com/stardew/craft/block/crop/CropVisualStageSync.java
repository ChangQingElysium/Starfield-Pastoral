package com.stardew.craft.block.crop;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.manager.CropGrowthManager;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import com.stardew.craft.port.net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Restores precise visual stages for already planted crops as their chunks load. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class CropVisualStageSync {
    private static final int CHUNKS_PER_TICK = 16;
    // Server-thread only. Keep chunk identity so an unload/reload cannot apply an obsolete request.
    private static final Map<ServerLevel, LinkedHashMap<Long, LevelChunk>> PENDING = new IdentityHashMap<>();

    private CropVisualStageSync() {}

    static int[] legacyDays(StardewCropBlock crop, int[] current) {
        return switch (BuiltInRegistries.BLOCK.getKey(crop).getPath()) {
            case "green_bean_crop" -> new int[]{1, 2, 3, 4};
            case "cauliflower_crop" -> new int[]{1, 3, 4, 4};
            case "potato_crop" -> new int[]{1, 1, 2, 2};
            case "rhubarb_crop" -> new int[]{2, 3, 4, 4};
            case "strawberry_crop" -> new int[]{1, 2, 2, 3};
            case "red_cabbage_crop" -> new int[]{2, 2, 2, 3};
            case "artichoke_crop" -> new int[]{2, 2, 2, 2};
            case "carrot_crop" -> new int[]{1, 1, 0, 1};
            case "broccoli_crop" -> new int[]{2, 2, 2, 2};
            case "melon_crop" -> new int[]{1, 3, 4, 4};
            case "starfruit_crop" -> new int[]{2, 4, 3, 4};
            case "pumpkin_crop" -> new int[]{1, 3, 4, 5};
            case "ancient_fruit_crop" -> new int[]{2, 9, 9, 8};
            case "tomato_crop" -> new int[]{2, 3, 3, 3};
            case "blueberry_crop" -> new int[]{1, 4, 4, 4};
            case "hot_pepper_crop" -> new int[]{1, 1, 1, 2};
            case "coffee_bean_crop" -> new int[]{1, 3, 3, 3};
            case "grape_crop" -> new int[]{1, 2, 3, 4};
            case "hops_crop" -> new int[]{1, 2, 4, 4};
            case "cranberry_crop" -> new int[]{1, 2, 2, 2};
            case "eggplant_crop" -> new int[]{1, 1, 1, 2};
            case "corn_crop" -> new int[]{2, 4, 4, 4};
            case "sweet_gem_berry_crop" -> new int[]{2, 4, 12, 6};
            case "summer_squash_crop" -> new int[]{1, 1, 2, 2};
            default -> current;
        };
    }

    static int sourcePhaseVersion(StardewCropBlock crop) {
        return crop instanceof BroccoliCropBlock || crop instanceof MelonCropBlock
                || crop instanceof StarfruitCropBlock || crop instanceof PumpkinCropBlock
                || crop instanceof AncientFruitCropBlock || crop instanceof TomatoCropBlock
                || crop instanceof BlueberryCropBlock || crop instanceof HotPepperCropBlock
                || crop instanceof CoffeeBeanCropBlock || crop instanceof CornCropBlock
                || crop instanceof EggplantCropBlock || crop instanceof CranberryCropBlock
                || crop instanceof HopsCropBlock || crop instanceof GrapeCropBlock
                || crop instanceof SweetGemBerryCropBlock ? 1 : 0;
    }

    @SubscribeEvent
    public static void loaded(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getChunk() instanceof LevelChunk chunk)) return;
        // execute() runs inline on the server thread. Even a queued server task can run inside a
        // managed chunk wait: only a normal level tick may perform these neighbor-updating writes.
        PENDING.computeIfAbsent(level, ignored -> new LinkedHashMap<>()).put(chunk.getPos().toLong(), chunk);
    }

    @SubscribeEvent
    public static void unloaded(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var pending = PENDING.get(level);
        if (pending == null) return;
        pending.remove(event.getChunk().getPos().toLong(), event.getChunk());
        if (pending.isEmpty()) PENDING.remove(level);
    }

    @SubscribeEvent
    public static void levelUnloaded(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) PENDING.remove(level);
    }

    @SubscribeEvent
    public static void afterLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var pending = PENDING.get(level);
        if (pending == null) return;

        // Detach the batch before touching the world. Loads triggered by this batch belong to a later tick.
        Map<Long, LevelChunk> batch = new LinkedHashMap<>();
        var iterator = pending.entrySet().iterator();
        while (iterator.hasNext() && batch.size() < CHUNKS_PER_TICK) {
            var entry = iterator.next();
            batch.put(entry.getKey(), entry.getValue());
            iterator.remove();
        }
        if (pending.isEmpty()) PENDING.remove(level);

        var manager = CropGrowthManager.get(level);
        // One registry snapshot per batch, rather than scanning every farm for each loaded chunk.
        for (var global : manager.getAllCropPositions()) {
            if (!global.dimension().equals(level.dimension())) continue;
            var pos = global.pos();
            int chunkX = pos.getX() >> 4, chunkZ = pos.getZ() >> 4;
            var chunk = batch.get(ChunkPos.asLong(chunkX, chunkZ));
            if (chunk == null || level.getChunkSource().getChunkNow(chunkX, chunkZ) != chunk) continue;
            if (chunk.getBlockState(pos).getBlock() instanceof StardewCropBlock crop) {
                crop.syncVisualStage(level, pos, manager.getState(level, pos));
            }
        }
    }
}

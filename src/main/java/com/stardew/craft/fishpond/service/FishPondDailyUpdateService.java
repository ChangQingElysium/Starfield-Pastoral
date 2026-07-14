package com.stardew.craft.fishpond.service;

import com.stardew.craft.blockentity.FishPondBucketBlockEntity;
import com.stardew.craft.fishpond.data.FishPondWorldData;
import com.stardew.craft.fishpond.model.FishPondRecord;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.artisan.PreserveType;
import com.stardew.craft.item.artisan.PreservesItem;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.player.SkillType;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;

public final class FishPondDailyUpdateService {
    private static final int QUEST_BASE_EXP = 20;
    private static final float QUEST_SPAWNRATE_EXP_MULTIPLIER = 5F;
    private static final double JUMP_SYNC_RADIUS = 48.0D;
    private static final double JUMP_SYNC_RADIUS_SQR = JUMP_SYNC_RADIUS * JUMP_SYNC_RADIUS;
    private static final Map<String, DebugAdvanceCursor> DEBUG_ADVANCE_CURSORS = new HashMap<>();

    private FishPondDailyUpdateService() {
    }

    public static void onNewDay(ServerLevel level) {
        applyDayUpdates(level, null, 1, currentAbsoluteDay());
    }

    public static void advanceNearby(ServerLevel level, BlockPos center, int days) {
        applyDayUpdates(level, center, days, reserveDebugAdvanceStartDay(level, days));
    }

    private static void applyDayUpdates(ServerLevel level, BlockPos center, int days, int startDay) {
        if (days <= 0) {
            return;
        }

        FishPondWorldData worldData = FishPondWorldData.get(level);
        String dimensionId = level.dimension().location().toString();
        boolean anyColorChanged = false;

        for (FishPondRecord pond : worldData.getPonds()) {
            if (!dimensionId.equals(pond.dimensionId())) {
                continue;
            }
            if (center != null && !isNearPond(center, pond, 5)) {
                continue;
            }
            for (int i = 0; i < days; i++) {
                if (applySingleDay(level, worldData, pond, startDay + i)) {
                    anyColorChanged = true;
                }
            }
        }

        if (anyColorChanged) {
            FishPondColorSyncService.broadcastSnapshot(level);
        }
    }

    private static boolean applySingleDay(ServerLevel level,
                                          FishPondWorldData worldData,
                                          FishPondRecord pond,
                                          int absoluteDay) {
        if (pond.currentPopulation() <= 0 || pond.fishTypeId().isBlank()) {
            return false;
        }

        FishPondDataService dataService = FishPondDataService.get();
        var pondDataOpt = dataService.resolveFishTypeId(pond.fishTypeId());
        if (pondDataOpt.isEmpty()) {
            return false;
        }

        var pondData = pondDataOpt.get();
    long seed = mixSeed(pond.pondId().hashCode(), absoluteDay);
        RandomSource random = RandomSource.create(seed);
        boolean changed = false;

        if (pond.hasCompletedRequest()) {
            pond.setNeededItemId("");
            pond.setNeededItemCount(0);
            pond.setHasCompletedRequest(false);
            changed = true;
        }

        double produceChance = pondData.baseMinProduceChance() >= pondData.baseMaxProduceChance()
            ? pondData.baseMinProduceChance()
            : Mth.lerp((float) pond.currentPopulation() / 10.0F,
                (float) pondData.baseMinProduceChance(),
                (float) pondData.baseMaxProduceChance());

        if (random.nextDouble() < produceChance) {
            ItemStack outputStack = createProducedItemStack(pond, dataService.rollProducedItem(pond, random).orElse(null), random);
            if (!outputStack.isEmpty()) {
                ResourceLocation outputId = BuiltInRegistries.ITEM.getKey(outputStack.getItem());
                if (outputId != null) {
                    pond.setOutputItemId(outputId.toString());
                    pond.setOutputCount(outputStack.getCount());
                    changed = true;
                }
            }
        }

        int spawnTime = Math.max(1, dataService.resolveSpawnTime(pond));
        pond.setDaysSinceSpawn(Math.min(spawnTime, pond.daysSinceSpawn() + 1));
        int resolvedMaxPopulation = dataService.resolveCurrentMaxPopulation(pond);
        if (resolvedMaxPopulation != pond.maxPopulation()) {
            pond.setMaxPopulation(resolvedMaxPopulation);
            changed = true;
        }

        if (pond.daysSinceSpawn() >= spawnTime
            && pond.currentPopulation() > 0) {
            var neededItem = dataService.resolveNeededItem(pond);
            if (neededItem.isPresent()) {
                if (pond.currentPopulation() >= pond.maxPopulation() && pond.neededItemId().isBlank()) {
                    pond.setNeededItemId(neededItem.get().itemId());
                    pond.setNeededItemCount(neededItem.get().count());
                    changed = true;
                }
            } else if (spawnFish(level, pond, random)) {
                changed = true;
            }
        }

        int resolvedColor = dataService.resolveWaterColor(pond);
        if (resolvedColor != pond.waterColor()) {
            pond.setWaterColor(resolvedColor);
            changed = true;
        }

        if (changed) {
            worldData.markChanged();
            FishPondBucketBlockEntity.syncVisualState(level, pond.bucketPos());
        }
        return changed;
    }

    private static ItemStack createProducedItemStack(FishPondRecord pond,
                                                     FishPondDataService.ProducedItem producedItem,
                                                     RandomSource random) {
        if (producedItem == null) {
            return ItemStack.EMPTY;
        }

        int count = Math.max(1, producedItem.rollStackCount(random));
        if ("(O)812".equals(producedItem.itemId()) || "stardewcraft:roe".equals(producedItem.itemId())) {
            count = applyRoeBonusRolls(count, random);
            return createRoeStack(pond, count);
        }
        ItemStack stack = FishPondQualifiedItemService.createItemStack(producedItem.itemId(), count);
        if (!stack.isEmpty() && pond.goldenAnimalCracker()) {
            stack.grow(stack.getCount());
        }
        return stack;
    }

    public static ItemStack createOutputStack(FishPondRecord pond) {
        if (pond.outputItemId().isBlank() || pond.outputCount() <= 0) {
            return ItemStack.EMPTY;
        }

        ResourceLocation outputId = ResourceLocation.tryParse(pond.outputItemId());
        if (outputId == null || !BuiltInRegistries.ITEM.containsKey(outputId)) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(outputId), pond.outputCount());
        if (stack.getItem() == ModItems.ROE.get()) {
            PreservesItem.createFlavored(PreserveType.ROE, createFishStack(pond), stack);
        }
        return stack;
    }

    private static ItemStack createRoeStack(FishPondRecord pond, int count) {
        if (pond.goldenAnimalCracker()) {
            count *= 2;
        }
        ItemStack stack = new ItemStack(ModItems.ROE.get(), count);
        PreservesItem.createFlavored(PreserveType.ROE, createFishStack(pond), stack);
        return stack;
    }

    private static int applyRoeBonusRolls(int count, RandomSource random) {
        int adjusted = count;
        while (random.nextDouble() < 0.2D) {
            adjusted++;
        }
        return adjusted;
    }

    public static void resolveNeeds(FishPondRecord pond) {
        pond.setNeededItemCount(0);
        pond.setHasCompletedRequest(true);
        pond.setLastUnlockedPopulationGate(pond.maxPopulation() + 1);
        pond.setMaxPopulation(FishPondDataService.get().resolveCurrentMaxPopulation(pond));
        pond.setDaysSinceSpawn(0);
    }

    public static void resolveNeeds(ServerLevel level, FishPondRecord pond, ServerPlayer player) {
        resolveNeeds(pond);

        int spawnTime = Math.max(0, FishPondDataService.get().resolveSpawnTime(pond));
        if (player != null) {
            int bonusExperience = (int) (spawnTime * QUEST_SPAWNRATE_EXP_MULTIPLIER);
            PlayerStardewDataAPI.addExperience(player, SkillType.FISHING, QUEST_BASE_EXP + bonusExperience);
        }

        FishPondBucketBlockEntity.syncVisualState(level, pond.bucketPos());
        FishPondColorSyncService.broadcastSnapshot(level);
        broadcastHappyFishJump(level, pond);
    }

    private static void broadcastHappyFishJump(ServerLevel level, FishPondRecord pond) {
        RandomSource random = level.getRandom();
        int delayTicks = 20;
        int jumps = Math.max(1, Math.min(pond.currentPopulation(), 10));
        for (int i = 0; i < jumps; i++) {
            broadcastSingleFishJump(level, pond, random, delayTicks);
            delayTicks += Mth.floor(Mth.lerp(random.nextFloat(), 3.0F, 5.0F));
        }
    }

    public static void broadcastAmbientFishJump(ServerLevel level, FishPondRecord pond) {
        broadcastSingleFishJump(level, pond, level.getRandom(), 0);
    }

    private static void broadcastSpawnFishJump(ServerLevel level, FishPondRecord pond, RandomSource random) {
        ResourceLocation fishId = ResourceLocation.tryParse(pond.fishTypeId());
        if (fishId == null) {
            return;
        }
        String path = fishId.getPath();
        if ("coral".equals(path) || "sea_urchin".equals(path)) {
            return;
        }
        int delayTicks = Mth.floor(Mth.lerp(random.nextFloat(), 40.0F, 100.0F));
        broadcastSingleFishJump(level, pond, random, delayTicks);
    }

    private static void broadcastSingleFishJump(ServerLevel level, FishPondRecord pond, RandomSource random, int delayTicks) {
        if (pond.currentPopulation() <= 0 || pond.fishTypeId().isBlank()) {
            return;
        }

        ResourceLocation fishId = ResourceLocation.tryParse(pond.fishTypeId());
        if (fishId == null || !BuiltInRegistries.ITEM.containsKey(fishId)) {
            return;
        }

        Vec3 end = new Vec3(
            (pond.minX() + pond.maxX() + 1) * 0.5D,
            pond.maxY() + 0.05D,
            (pond.minZ() + pond.maxZ() + 1) * 0.5D
        );
        Vec3 start = pickJumpStart(pond, random, end);
        float jumpHeight = Mth.lerp(random.nextFloat(), 75.0F / 64.0F, 100.0F / 64.0F);
        float angularVelocity = (float) Math.toRadians(Mth.lerp(random.nextFloat(), 20.0F, 40.0F));
        boolean flipped = start.x > end.x;

        com.stardew.craft.network.payload.FishPondJumpSyncPayload payload =
            new com.stardew.craft.network.payload.FishPondJumpSyncPayload(
                level.dimension().location().toString(),
                pond.fishTypeId(),
                start.x,
                start.y,
                start.z,
                end.x,
                end.y,
                end.z,
                jumpHeight,
                angularVelocity,
                delayTicks,
                flipped
            );

        for (ServerPlayer target : level.players()) {
            if (target.position().distanceToSqr(end) > JUMP_SYNC_RADIUS_SQR) {
                continue;
            }
            PacketDistributor.sendToPlayer(target, payload);
        }
    }

    private static Vec3 pickJumpStart(FishPondRecord pond, RandomSource random, Vec3 fallback) {
        if (pond.waterCells().isEmpty()) {
            return fallback;
        }

        int targetIndex = random.nextInt(pond.waterCells().size());
        int index = 0;
        for (Long packedPos : pond.waterCells()) {
            if (index++ != targetIndex) {
                continue;
            }

            BlockPos cell = BlockPos.of(packedPos);
            double offsetX = Mth.lerp(random.nextDouble(), -0.25D, 0.25D);
            double offsetZ = Mth.lerp(random.nextDouble(), -0.25D, 0.25D);
            return new Vec3(cell.getX() + 0.5D + offsetX, pond.maxY() + 0.05D, cell.getZ() + 0.5D + offsetZ);
        }

        return fallback;
    }

    private static boolean spawnFish(ServerLevel level, FishPondRecord pond, RandomSource random) {
        if (pond.currentPopulation() >= pond.maxPopulation() || pond.currentPopulation() <= 0) {
            return false;
        }
        pond.setDaysSinceSpawn(0);
        pond.setCurrentPopulation(Math.min(pond.maxPopulation(), pond.currentPopulation() + 1));
        broadcastSpawnFishJump(level, pond, random);
        return true;
    }

    private static ItemStack createFishStack(FishPondRecord pond) {
        ResourceLocation fishId = ResourceLocation.tryParse(pond.fishTypeId());
        if (fishId == null || !BuiltInRegistries.ITEM.containsKey(fishId)) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(fishId));
    }

    private static boolean isNearPond(BlockPos center, FishPondRecord pond, int margin) {
        return center.getX() >= pond.minX() - margin && center.getX() <= pond.maxX() + margin
            && center.getY() >= pond.minY() - margin && center.getY() <= pond.maxY() + margin
            && center.getZ() >= pond.minZ() - margin && center.getZ() <= pond.maxZ() + margin;
    }

    private static int currentAbsoluteDay() {
        StardewTimeManager time = StardewTimeManager.get();
        if (time == null) {
            return 1;
        }
        return (time.getCurrentYear() - 1) * (28 * 4) + time.getCurrentSeason() * 28 + time.getCurrentDay();
    }

    private static int reserveDebugAdvanceStartDay(ServerLevel level, int days) {
        int currentDay = currentAbsoluteDay();
        String key = level.getServer().getWorldData().getLevelName() + "|" + level.dimension().location();
        DebugAdvanceCursor cursor = DEBUG_ADVANCE_CURSORS.get(key);
        if (cursor == null || cursor.baseDay() != currentDay) {
            cursor = new DebugAdvanceCursor(currentDay, 0);
        }

        int startDay = currentDay + cursor.nextOffset() + 1;
        DEBUG_ADVANCE_CURSORS.put(key, new DebugAdvanceCursor(currentDay, cursor.nextOffset() + days));
        return startDay;
    }

    private static long mixSeed(long pondSeed, long daySeed) {
        long seed = 1469598103934665603L;
        seed = (seed ^ pondSeed) * 1099511628211L;
        seed = (seed ^ daySeed) * 1099511628211L;
        return seed;
    }

    private record DebugAdvanceCursor(int baseDay, int nextOffset) {
    }
}

package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.fishpond.data.FishPondWorldData;
import com.stardew.craft.fishpond.model.FishPondRecord;
import com.stardew.craft.fishpond.service.FishPondColorSyncService;
import com.stardew.craft.fishpond.service.FishPondInteractionService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class FishPondGameplayEvents {
    private static final long ITEM_SCAN_INTERVAL = 10L;
    private static final java.util.Map<String, Long> NEXT_AMBIENT_JUMP_TICKS = new java.util.HashMap<>();

    private FishPondGameplayEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FishPondColorSyncService.sendFullSnapshot(player, player.serverLevel());
        }
    }

    @SubscribeEvent
    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FishPondColorSyncService.sendFullSnapshot(player, player.serverLevel());
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || com.stardew.craft.time.StardewTimePauseService.shouldPauseLevel(level)
                || level.getGameTime() % ITEM_SCAN_INTERVAL != 0L) {
            return;
        }

        FishPondWorldData worldData = FishPondWorldData.get(level);
        String dimensionId = level.dimension().location().toString();
        boolean anyChanged = false;

        for (FishPondRecord pond : worldData.getPonds()) {
            if (!dimensionId.equals(pond.dimensionId())) {
                continue;
            }
            if (pond.waterCells().isEmpty()) {
                continue;
            }
            if (tickPond(level, worldData, pond)) {
                anyChanged = true;
            }
            com.stardew.craft.blockentity.FishPondBucketBlockEntity.syncVisualState(level, pond.bucketPos());
            tickAmbientJump(level, pond);
        }

        if (anyChanged) {
            FishPondColorSyncService.broadcastSnapshot(level);
        }
    }

    private static boolean tickPond(ServerLevel level, FishPondWorldData worldData, FishPondRecord pond) {
        AABB scanBox = new AABB(
            pond.minX(), pond.minY(), pond.minZ(),
            pond.maxX() + 1.0D, pond.maxY() + 2.0D, pond.maxZ() + 1.0D
        );
        boolean changed = false;
        for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class, scanBox, Entity::isAlive)) {
            if (!isInsidePondWater(pond, itemEntity)) {
                continue;
            }
            if (FishPondInteractionService.absorbItemEntity(level, pond, itemEntity).changedState()) {
                changed = true;
            }
        }
        return changed;
    }

    private static boolean isInsidePondWater(FishPondRecord pond, ItemEntity itemEntity) {
        BlockPos blockPos = itemEntity.blockPosition();
        if (isNearRecordedWater(pond, blockPos)) {
            return true;
        }

        AABB sampleBox = itemEntity.getBoundingBox().inflate(0.02D);
        int minX = Mth.floor(sampleBox.minX);
        int minY = Mth.floor(sampleBox.minY);
        int minZ = Mth.floor(sampleBox.minZ);
        int maxX = Mth.floor(sampleBox.maxX - 1.0E-5D);
        int maxY = Mth.floor(sampleBox.maxY - 1.0E-5D);
        int maxZ = Mth.floor(sampleBox.maxZ - 1.0E-5D);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (pond.containsWater(new BlockPos(x, y, z))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isNearRecordedWater(FishPondRecord pond, BlockPos origin) {
        for (int dy = 1; dy >= -2; dy--) {
            BlockPos candidate = origin.offset(0, dy, 0);
            if (pond.containsWater(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static void tickAmbientJump(ServerLevel level, FishPondRecord pond) {
        if (pond.currentPopulation() <= 0 || pond.fishTypeId().isBlank() || pond.waterCells().isEmpty()) {
            return;
        }
        ResourceLocation fishId = ResourceLocation.tryParse(pond.fishTypeId());
        if (fishId != null) {
            String path = fishId.getPath();
            if ("coral".equals(path) || "sea_urchin".equals(path)) {
                return;
            }
        }
        long gameTime = level.getGameTime();
        long nextJump = NEXT_AMBIENT_JUMP_TICKS.getOrDefault(pond.pondId(), Long.MIN_VALUE);
        if (gameTime < nextJump) {
            return;
        }
        com.stardew.craft.fishpond.service.FishPondDailyUpdateService.broadcastAmbientFishJump(level, pond);
        long nextDelay = Mth.floor(200.0F + level.getRandom().nextFloat() * 200.0F);
        NEXT_AMBIENT_JUMP_TICKS.put(pond.pondId(), gameTime + nextDelay);
    }

    @EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
    public static final class ClientEvents {
        private ClientEvents() {
        }

        @SubscribeEvent
        public static void onClientDisconnect(PlayerEvent.PlayerLoggedOutEvent event) {
            com.stardew.craft.client.fishpond.ClientFishPondWaterColorCache.clearAll();
        }
    }
}

package com.stardew.craft.shop;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.book.BooksellerSchedule;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.npc.BooksellerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;

@EventBusSubscriber(modid = StardewCraft.MODID)
@SuppressWarnings("null")
public final class BooksellerEvents {
    private static final String MARKER_TAG = "stardewcraft_bookseller";
    public static final BlockPos POS = new BlockPos(131, 67, -52);
    private static final float FACING_YAW = 0.0f;
    private static final int CHECK_INTERVAL_TICKS = 40;
    private static final double SCAN_RADIUS = 6.0;

    private static int tickCounter = 0;

    private BooksellerEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter < CHECK_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;

        ServerLevel level = event.getServer().getLevel(ModDimensions.STARDEW_VALLEY);
        if (level == null || level.players().isEmpty()) {
            return;
        }
        if (com.stardew.craft.time.StardewTimePauseService.isPaused(event.getServer())) {
            return;
        }

        if (!BooksellerSchedule.isToday(level)) {
            releaseSpawnChunk(level);
            removeManagedEntities(level);
            return;
        }

        loadSpawnChunk(level);
        ensureSingleEntity(level);
    }

    private static void loadSpawnChunk(ServerLevel level) {
        int chunkX = POS.getX() >> 4;
        int chunkZ = POS.getZ() >> 4;
        level.setChunkForced(chunkX, chunkZ, true);
        level.getChunk(chunkX, chunkZ);
    }

    private static void releaseSpawnChunk(ServerLevel level) {
        int chunkX = POS.getX() >> 4;
        int chunkZ = POS.getZ() >> 4;
        level.setChunkForced(chunkX, chunkZ, false);
    }

    private static void removeManagedEntities(ServerLevel level) {
        AABB scanBox = new AABB(POS).inflate(SCAN_RADIUS);
        List<BooksellerEntity> nearby = level.getEntitiesOfClass(
                BooksellerEntity.class,
                scanBox,
                entity -> entity.getTags().contains(MARKER_TAG));
        for (BooksellerEntity entity : nearby) {
            entity.discard();
        }
    }

    private static void ensureSingleEntity(ServerLevel level) {
        AABB scanBox = new AABB(POS).inflate(SCAN_RADIUS);
        List<BooksellerEntity> nearby = level.getEntitiesOfClass(
                BooksellerEntity.class,
                scanBox,
                entity -> entity.getTags().contains(MARKER_TAG));

        BooksellerEntity managed = nearby.isEmpty() ? null : nearby.get(0);
        for (BooksellerEntity entity : nearby) {
            if (managed != null && !entity.getUUID().equals(managed.getUUID())) {
                entity.discard();
            }
        }

        if (managed == null) {
            managed = spawnNewEntity(level);
            if (managed == null) {
                return;
            }
        }

        forceHoldPose(managed);
    }

    private static BooksellerEntity spawnNewEntity(ServerLevel level) {
        BooksellerEntity entity = ModEntities.BOOKSELLER.get().create(level);
        if (entity == null) {
            return null;
        }

        entity.moveTo(POS.getX() + 0.5, POS.getY(), POS.getZ() + 0.5, FACING_YAW, 0.0f);
        entity.setYHeadRot(FACING_YAW);
        entity.setYBodyRot(FACING_YAW);
        entity.setNoAi(true);
        entity.setInvulnerable(true);
        entity.setPersistenceRequired();
        entity.setSilent(true);
        entity.setCustomName(Component.translatable("entity.stardewcraft.bookseller"));
        entity.setCustomNameVisible(true);
        entity.addTag(MARKER_TAG);

        if (!level.addFreshEntity(entity)) {
            return null;
        }
        return entity;
    }

    public static void forceCheckNow(ServerLevel level) {
        if (level == null) {
            return;
        }
        if (!BooksellerSchedule.isToday(level)) {
            releaseSpawnChunk(level);
            removeManagedEntities(level);
            return;
        }
        loadSpawnChunk(level);
        ensureSingleEntity(level);
    }

    private static void forceHoldPose(BooksellerEntity entity) {
        if (!entity.isNoAi()) {
            entity.setNoAi(true);
        }
        if (!entity.isInvulnerable()) {
            entity.setInvulnerable(true);
        }
        if (!entity.isSilent()) {
            entity.setSilent(true);
        }
        if (!entity.isPersistenceRequired()) {
            entity.setPersistenceRequired();
        }
        if (!entity.getTags().contains(MARKER_TAG)) {
            entity.addTag(MARKER_TAG);
        }

        double dx = entity.getX() - (POS.getX() + 0.5);
        double dy = entity.getY() - POS.getY();
        double dz = entity.getZ() - (POS.getZ() + 0.5);
        if (dx * dx + dy * dy + dz * dz > 1.0e-4) {
            entity.teleportTo(POS.getX() + 0.5, POS.getY(), POS.getZ() + 0.5);
        }
        if (Math.abs(entity.getYRot() - FACING_YAW) > 0.01f
                || Math.abs(entity.getYHeadRot() - FACING_YAW) > 0.01f) {
            entity.setYRot(FACING_YAW);
            entity.setYHeadRot(FACING_YAW);
            entity.setYBodyRot(FACING_YAW);
        }
        entity.setDeltaMovement(0, 0, 0);
        entity.hasImpulse = false;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(event.getTarget() instanceof BooksellerEntity bookseller)) {
            return;
        }
        if (!bookseller.getTags().contains(MARKER_TAG)) {
            return;
        }
        if (!BooksellerSchedule.isToday(player.serverLevel())) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
            bookseller.discard();
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);

        BooksellerService.handleInteraction(player);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getTarget() instanceof BooksellerEntity bookseller)) {
            return;
        }
        if (!bookseller.getTags().contains(MARKER_TAG)) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
    }

}

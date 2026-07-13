package com.stardew.craft.shop;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.npc.TravelingCartEntity;
import com.stardew.craft.festival.desert.DesertFestivalService;
import com.stardew.craft.festival.desert.DesertFestivalSpecialInteractionService;
import com.stardew.craft.festival.nightmarket.NightMarketShopService;
import com.stardew.craft.network.payload.OpenShopScreenPayload;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;

@EventBusSubscriber(modid = StardewCraft.MODID)
@SuppressWarnings("null")
public final class TravelingCartEvents {
    private static final String MARKER_TAG = "stardewcraft_traveling_cart";
    private static final BlockPos POS = new BlockPos(-135, 64, 21);
    private static final float FACING_YAW = 90.0f;
    private static final int CHECK_INTERVAL_TICKS = 40;
    private static final double SCAN_RADIUS = 8.0;
    private static int tickCounter = 0;

    private TravelingCartEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter < CHECK_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;

        ServerLevel level = event.getServer().getLevel(ModDimensions.STARDEW_VALLEY);
        if (level == null) {
            return;
        }
        if (level.players().isEmpty()) {
            return;
        }

        com.stardew.craft.time.StardewTimeManager time = com.stardew.craft.time.StardewTimeManager.get();
        boolean visitDay = shouldTravelingMerchantVisitToday(time.getCurrentDay());
        boolean guaranteeVisitDay = visitDay || isNightMarketDay(time);
        TravelingCartManager manager = TravelingCartManager.get(level);
        manager.ensureGuaranteeInitialized(level.getServer().overworld().getSeed(), time.getCurrentYear());
        manager.processDay(time.getAbsoluteDay(), guaranteeVisitDay, time.getCurrentYear());

        if (!visitDay) {
            setSpawnChunkForced(level, false);
            removeManagedEntity(level, manager);
            return;
        }

        setSpawnChunkForced(level, true);
        loadSpawnChunk(level);
        ensureSingleEntity(level, manager);
    }

    private static boolean shouldTravelingMerchantVisitToday(int dayOfMonth) {
        int dayOfWeek = Math.floorMod(dayOfMonth - 1, 7);
        return dayOfWeek == 4 || dayOfWeek == 6;
    }

    private static boolean isNightMarketDay(com.stardew.craft.time.StardewTimeManager time) {
        return time.getCurrentSeason() == com.stardew.craft.festival.FestivalRegistry.WINTER
                && time.getCurrentDay() >= 15
                && time.getCurrentDay() <= 17;
    }

    private static void loadSpawnChunk(ServerLevel level) {
        level.getChunk(POS.getX() >> 4, POS.getZ() >> 4);
    }

    private static void setSpawnChunkForced(ServerLevel level, boolean forced) {
        level.setChunkForced(POS.getX() >> 4, POS.getZ() >> 4, forced);
    }

    private static boolean canOpenShopNow() {
        com.stardew.craft.time.StardewTimeManager time = com.stardew.craft.time.StardewTimeManager.get();
        return shouldTravelingMerchantVisitToday(time.getCurrentDay())
                && time.getCurrentTime() < 1200;
    }

    private static void ensureSingleEntity(ServerLevel level, TravelingCartManager manager) {
        TravelingCartEntity managed = null;
        if (manager.getEntityUuid() != null) {
            Entity entity = level.getEntity(manager.getEntityUuid());
            if (entity instanceof TravelingCartEntity cart && cart.isAlive()) {
                managed = cart;
            }
        }

        AABB scanBox = new AABB(POS).inflate(SCAN_RADIUS);
        List<TravelingCartEntity> nearby = level.getEntitiesOfClass(
                TravelingCartEntity.class,
                scanBox,
                entity -> entity.getTags().contains(MARKER_TAG));

        if (managed == null && !nearby.isEmpty()) {
            managed = nearby.get(0);
            manager.setEntityUuid(managed.getUUID());
        }

        for (TravelingCartEntity entity : nearby) {
            if (!entity.getUUID().equals(managed.getUUID())) {
                entity.discard();
            }
        }

        if (managed == null) {
            managed = spawnNewEntity(level);
            if (managed == null) {
                manager.clearEntityUuid();
                return;
            }
            manager.setEntityUuid(managed.getUUID());
        }

        forceHoldPose(managed);
    }

    private static void removeManagedEntity(ServerLevel level, TravelingCartManager manager) {
        if (manager.getEntityUuid() != null) {
            Entity entity = level.getEntity(manager.getEntityUuid());
            if (entity instanceof TravelingCartEntity cart) {
                cart.discard();
            }
            manager.clearEntityUuid();
        }

        AABB scanBox = new AABB(POS).inflate(SCAN_RADIUS);
        List<TravelingCartEntity> nearby = level.getEntitiesOfClass(
                TravelingCartEntity.class,
                scanBox,
                entity -> entity.getTags().contains(MARKER_TAG));
        for (TravelingCartEntity entity : nearby) {
            entity.discard();
        }
    }

    private static TravelingCartEntity spawnNewEntity(ServerLevel level) {
        TravelingCartEntity entity = ModEntities.TRAVELING_CART.get().create(level);
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
        entity.setCustomName(Component.translatable("entity.stardewcraft.traveling_cart"));
        entity.setCustomNameVisible(false);
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

        com.stardew.craft.time.StardewTimeManager time = com.stardew.craft.time.StardewTimeManager.get();
        boolean visitDay = shouldTravelingMerchantVisitToday(time.getCurrentDay());
        TravelingCartManager manager = TravelingCartManager.get(level);
        manager.ensureGuaranteeInitialized(level.getServer().overworld().getSeed(), time.getCurrentYear());
        manager.processDay(time.getAbsoluteDay(), visitDay, time.getCurrentYear());

        if (!visitDay) {
            setSpawnChunkForced(level, false);
            removeManagedEntity(level, manager);
            return;
        }

        setSpawnChunkForced(level, true);
        loadSpawnChunk(level);
        ensureSingleEntity(level, manager);
    }

    private static void forceHoldPose(TravelingCartEntity entity) {
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
        if (!(event.getTarget() instanceof TravelingCartEntity cart)) {
            return;
        }
        boolean regularCart = cart.getTags().contains(MARKER_TAG);
        boolean festivalCart = cart.getTags().contains(DesertFestivalSpecialInteractionService.FESTIVAL_TRAVELING_CART_MARKER_TAG);
        boolean nightMarketCart = cart.getTags().contains(NightMarketShopService.TRAVELING_CART_MARKER_TAG);
        if (!regularCart && !festivalCart && !nightMarketCart) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);

        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        tryOpenTravelingCartShop(player, regularCart, festivalCart, nightMarketCart);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getTarget() instanceof TravelingCartEntity cart)) {
            return;
        }
        if (!cart.getTags().contains(MARKER_TAG)
            && !cart.getTags().contains(DesertFestivalSpecialInteractionService.FESTIVAL_TRAVELING_CART_MARKER_TAG)
            && !cart.getTags().contains(NightMarketShopService.TRAVELING_CART_MARKER_TAG)) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);

        if (event.getEntity() instanceof ServerPlayer player) {
            boolean regularCart = cart.getTags().contains(MARKER_TAG);
            boolean festivalCart = cart.getTags().contains(DesertFestivalSpecialInteractionService.FESTIVAL_TRAVELING_CART_MARKER_TAG);
            boolean nightMarketCart = cart.getTags().contains(NightMarketShopService.TRAVELING_CART_MARKER_TAG);
            tryOpenTravelingCartShop(player, regularCart, festivalCart, nightMarketCart);
        }
    }

    private static void tryOpenTravelingCartShop(
            ServerPlayer player,
            boolean regularCart,
            boolean festivalCart,
            boolean nightMarketCart) {
        if (regularCart && !canOpenShopNow()) {
            return;
        }
        if (festivalCart && !DesertFestivalService.isFestivalOpen()) {
            return;
        }
        if (festivalCart && !DesertFestivalSpecialInteractionService.isFestivalTravelingCartShopOpen()) {
            player.displayClientMessage(Component.translatable("stardewcraft.desert_festival.traveling_cart.closed"), true);
            return;
        }
        if (nightMarketCart && !NightMarketShopService.isNightMarketOpen()) {
            return;
        }
        openTravelingCartShop(player, nightMarketCart);
    }

    private static void openTravelingCartShop(ServerPlayer player, boolean nightMarketCart) {
        ShopRegistry.ShopDefinition shop = ShopRegistry.get("Traveler");
        if (shop == null) {
            return;
        }

        int money = PlayerStardewDataAPI.getMoney(player);
        List<ShopItemEntry> items = ShopRegistry.getFilteredItemsForPlayer("Traveler", shop, player);
        OpenShopScreenPayload payload = new OpenShopScreenPayload(
                "Traveler",
                money,
                items,
                shop.ownerNpcId(),
                nightMarketCart ? NightMarketShopService.TRAVELING_CART_DIALOGUE : shop.ownerDialogue(),
                new java.util.ArrayList<>(shop.acceptedSellTypes())
        );
        PacketDistributor.sendToPlayer(player, payload);
    }
}

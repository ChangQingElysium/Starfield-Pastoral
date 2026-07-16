package com.stardew.craft.shop;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.npc.CamelMerchantEntity;
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
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * 沙漠骆驼商人占位逻辑（GeckoLib 自定义实体 {@link CamelMerchantEntity}）。
 * <ul>
 *   <li>位置固定，朝南（yaw=0），无 AI、不可移动、不可受伤、不可消失。</li>
 *   <li>每存档持久化管理，玩家靠近时确保唯一实例。</li>
 *   <li>右键交互不会触发原版交易，直接打开 ShopRegistry "DesertTrade"。</li>
 *   <li>多人服务器安全：所有逻辑均运行在服务端单线程内。</li>
 * </ul>
 */
@EventBusSubscriber(modid = StardewCraft.MODID)
@SuppressWarnings("null")
public final class CamelMerchantEvents {
    /** 实体持久化标签：用于识别本模组生成的占位实体，方便清理重复。 */
    private static final String MARKER_TAG = "stardewcraft_camel_merchant";

    /** 固定位置（沙漠骆驼商人）。 */
    public static final BlockPos POS = new BlockPos(-193, 64, -185);

    /** 朝南，yaw=0；MC 中南方向 = +Z。 */
    private static final float FACING_YAW = 0.0f;

    /** 维持频率：每 40 tick (=2 秒) 巡检一次，足够轻量。 */
    private static final int CHECK_INTERVAL_TICKS = 40;

    /** 重复实体清理 / 位置矫正的检测半径（方块）。 */
    private static final double SCAN_RADIUS = 6.0;

    private static int tickCounter = 0;

    private CamelMerchantEvents() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter < CHECK_INTERVAL_TICKS) return;
        tickCounter = 0;

        ServerLevel level = event.getServer().getLevel(ModDimensions.STARDEW_VALLEY);
        if (level == null) return;
        if (level.players().isEmpty()) return;
        if (com.stardew.craft.time.StardewTimePauseService.isPaused(event.getServer())) return;

        loadSpawnChunk(level);
        ensureSingleEntity(level);
    }

    private static void loadSpawnChunk(ServerLevel level) {
        int chunkX = POS.getX() >> 4;
        int chunkZ = POS.getZ() >> 4;
        level.setChunkForced(chunkX, chunkZ, true);
        level.getChunk(chunkX, chunkZ);
    }

    private static void ensureSingleEntity(ServerLevel level) {
        CamelMerchantManager mgr = CamelMerchantManager.get(level);

        // 0. 清理旧存档遗留的占位实体（任何带 MARKER_TAG 但不是 CamelMerchantEntity 的实体，
        //    例如此前用 Villager 实现时留下的村民）。
        AABB cleanupBox = new AABB(POS).inflate(SCAN_RADIUS);
        List<Entity> legacy = level.getEntitiesOfClass(
                Entity.class, cleanupBox,
                e -> e.getTags().contains(MARKER_TAG) && !(e instanceof CamelMerchantEntity));
        for (Entity e : legacy) {
            e.discard();
        }

        // 1. 取出当前管理实体
        CamelMerchantEntity managed = null;
        if (mgr.getVillagerUuid() != null) {
            Entity e = level.getEntity(mgr.getVillagerUuid());
            if (e instanceof CamelMerchantEntity cm && cm.isAlive()) {
                managed = cm;
            }
        }

        // 2. 清理重复（同区域内带 MARKER_TAG 但不是 managed 的）
        AABB scanBox = new AABB(POS).inflate(SCAN_RADIUS);
        List<CamelMerchantEntity> nearby = level.getEntitiesOfClass(
                CamelMerchantEntity.class, scanBox,
                e -> e.getTags().contains(MARKER_TAG));

        if (managed == null && !nearby.isEmpty()) {
            managed = nearby.get(0);
            mgr.setVillagerUuid(managed.getUUID());
        }

        for (CamelMerchantEntity e : nearby) {
            if (!e.getUUID().equals(managed.getUUID())) {
                e.discard();
            }
        }

        // 3. 不存在则重建
        if (managed == null) {
            managed = spawnNewEntity(level);
            if (managed == null) {
                return;
            }
            mgr.setVillagerUuid(managed.getUUID());
        }

        // 4. 维持状态
        forceHoldPose(managed);
    }

    private static CamelMerchantEntity spawnNewEntity(ServerLevel level) {
        CamelMerchantEntity e = ModEntities.CAMEL_MERCHANT.get().create(level);
        if (e == null) return null;

        e.moveTo(POS.getX() + 0.5, POS.getY(), POS.getZ() + 0.5, FACING_YAW, 0.0f);
        e.setYHeadRot(FACING_YAW);
        e.setYBodyRot(FACING_YAW);

        e.setNoAi(true);
        e.setInvulnerable(true);
        e.setPersistenceRequired();
        e.setSilent(true);
        e.setCustomName(Component.translatable("entity.stardewcraft.camel_merchant"));
        e.setCustomNameVisible(true);
        e.addTag(MARKER_TAG);

        if (!level.addFreshEntity(e)) {
            return null;
        }
        return e;
    }

    public static void forceCheckNow(ServerLevel level) {
        if (level == null) {
            return;
        }
        loadSpawnChunk(level);
        ensureSingleEntity(level);
    }

    /** 每个 tick 强制把实体锁死在固定位置/朝向 + 各种状态位上，防止任何外力扰动。 */
    private static void forceHoldPose(CamelMerchantEntity e) {
        if (!e.isNoAi()) e.setNoAi(true);
        if (!e.isInvulnerable()) e.setInvulnerable(true);
        if (!e.isSilent()) e.setSilent(true);
        if (!e.isPersistenceRequired()) e.setPersistenceRequired();
        if (!e.getTags().contains(MARKER_TAG)) e.addTag(MARKER_TAG);

        double dx = e.getX() - (POS.getX() + 0.5);
        double dy = e.getY() - POS.getY();
        double dz = e.getZ() - (POS.getZ() + 0.5);
        if (dx * dx + dy * dy + dz * dz > 1.0e-4) {
            e.teleportTo(POS.getX() + 0.5, POS.getY(), POS.getZ() + 0.5);
        }
        if (Math.abs(e.getYRot() - FACING_YAW) > 0.01f
                || Math.abs(e.getYHeadRot() - FACING_YAW) > 0.01f) {
            e.setYRot(FACING_YAW);
            e.setYHeadRot(FACING_YAW);
            e.setYBodyRot(FACING_YAW);
        }
        e.setDeltaMovement(0, 0, 0);
        e.hasImpulse = false;
    }

    // -------------------- 交互：打开沙漠骆驼商人商店 --------------------

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getTarget() instanceof CamelMerchantEntity cm)) return;
        if (!cm.getTags().contains(MARKER_TAG)) return;

        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);

        openDesertTradeShop(player);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getTarget() instanceof CamelMerchantEntity cm)) return;
        if (!cm.getTags().contains(MARKER_TAG)) return;

        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
    }

    private static void openDesertTradeShop(ServerPlayer player) {
        ShopRegistry.ShopDefinition shop = ShopRegistry.get("DesertTrade");
        if (shop == null) return;

        int money = PlayerStardewDataAPI.getMoney(player);
        List<ShopItemEntry> items = ShopRegistry.getFilteredItemsForPlayer("DesertTrade", shop, player);

        OpenShopScreenPayload payload = new OpenShopScreenPayload(
            "DesertTrade",
            money,
            items,
            shop.ownerNpcId(),
            shop.ownerDialogue(),
            new java.util.ArrayList<>(shop.acceptedSellTypes())
        );
        PacketDistributor.sendToPlayer(player, payload);
    }
}

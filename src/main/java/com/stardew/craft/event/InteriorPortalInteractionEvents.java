package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.festival.desert.DesertFestivalService;
import com.stardew.craft.interior.CrossDimensionTeleporter;
import com.stardew.craft.interior.InteriorPortalRegistry;
import com.stardew.craft.interior.InteriorSubspaceManager;
import com.stardew.craft.interior.PlayerInteriorAllocator;
import com.stardew.craft.network.ObjectDialogueService;
import com.stardew.craft.network.payload.DesertBusFadePayload;
import com.stardew.craft.shop.ShopHoursService;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.mining.MineEntranceBootstrap;
import com.stardew.craft.warp.ModTeleport;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Optional;
import java.util.Set;

@EventBusSubscriber(modid = StardewCraft.MODID)
@SuppressWarnings("null")
public class InteriorPortalInteractionEvents {

    private static final String TAG_TARGET_PREFIX = "sdv_portal_target:";

    private static final String PLAYER_FLAG_INTERIOR = "stardewcraft_interior_space";
    private static final String PLAYER_LAST_PORTAL_TICK = "stardewcraft_last_portal_tick";
    private static final String PLAYER_PENDING_PORTAL_ID = "stardewcraft_pending_portal_id";
    private static final String PLAYER_PENDING_PORTAL_START = "stardewcraft_pending_portal_start";

    private static final long PORTAL_COOLDOWN_TICKS = 8L;
    private static final int PORTAL_FADE_OUT_TICKS = 12;
    private static final int PORTAL_WARP_AT = 12;
    private static final int PORTAL_FADE_IN_AT = 14;
    private static final int PORTAL_FADE_IN_TICKS = 12;
    private static final int PORTAL_TOTAL_TICKS = PORTAL_FADE_IN_AT + PORTAL_FADE_IN_TICKS + 2;

    // ======================== 旧实体兼容入口（已废弃，Interaction 实体由 InteriorSubspaceLifecycleEvents 拦截取消加载） ========================

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        Entity target = event.getTarget();
        if (target.getTags().contains(com.stardew.craft.festival.desert.DesertFestivalSpecialInteractionService.WARPER_MARKER_TAG)) {
            com.stardew.craft.festival.desert.DesertFestivalSpecialInteractionService.openWarper(player);
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
            return;
        }
        if (target.getTags().contains(com.stardew.craft.festival.nightmarket.NightMarketWarperService.INTERACTION_MARKER_TAG)) {
            com.stardew.craft.festival.nightmarket.NightMarketWarperService.open(player);
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
            return;
        }
        Optional<String> targetId = findTagValue(target.getTags(), TAG_TARGET_PREFIX);
        if (targetId.isEmpty()) return;

        handlePortalInteraction(player, targetId.get());
        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        Entity target = event.getTarget();
        if (target.getTags().contains(com.stardew.craft.festival.desert.DesertFestivalSpecialInteractionService.WARPER_MARKER_TAG)) {
            com.stardew.craft.festival.desert.DesertFestivalSpecialInteractionService.openWarper(player);
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
            return;
        }
        if (target.getTags().contains(com.stardew.craft.festival.nightmarket.NightMarketWarperService.INTERACTION_MARKER_TAG)) {
            com.stardew.craft.festival.nightmarket.NightMarketWarperService.open(player);
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
            return;
        }

        Optional<String> targetId = findTagValue(target.getTags(), TAG_TARGET_PREFIX);
        if (targetId.isEmpty()) return;

        handlePortalInteraction(player, targetId.get());
        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
    }

    // ======================== 公共入口（Block 和 Entity 共用） ========================

    /**
     * 统一的传送门交互处理入口。
     * 由 PortalTriggerBlock 和旧 Interaction 实体共同调用。
     *
     * @param player   触发交互的玩家
     * @param targetId 传送目标 ID（如 "pierre_house_enter", "farm_exit_south" 等）
     */
    public static void handlePortalInteraction(ServerPlayer player, String targetId) {
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim = player.serverLevel().dimension();
        boolean inStardew = ModDimensions.STARDEW_VALLEY.equals(dim);
        boolean inOverworld = net.minecraft.world.level.Level.OVERWORLD.equals(dim);
        boolean inMine = ModMiningDimensions.STARDEW_MINING.equals(dim);

        if (!inStardew && !inOverworld && !inMine) return;

        // ── 矿井维度：仅处理矿井出口 ──
        if (inMine) {
            if ("mine_exit".equals(targetId)) {
                handleMineExit(player);
            } else if ("skull_cavern_exit".equals(targetId)) {
                handleSkullCavernExit(player);
            }
            return;
        }

        // ── 主世界：巫师塔入口 ──
        if (inOverworld) {
            if ("wizard_tower_overworld_enter".equals(targetId)) {
                CrossDimensionTeleporter.overworldToWizardInterior(player);
            }
            return;
        }

        // ── 星露谷维度 ──

        if (com.stardew.craft.world.OldMasterCannoliService.TARGET_ID.equals(targetId)) {
            com.stardew.craft.world.OldMasterCannoliService.interact(player);
            return;
        }

        if (com.stardew.craft.qi.MrQiQuestInteractionService.SAND_DRAGON_TARGET_ID.equals(targetId)) {
            com.stardew.craft.qi.MrQiQuestInteractionService.interactSandDragon(player);
            return;
        }

        if (com.stardew.craft.casino.CasinoAccessService.ENTRY_TARGET_ID.equals(targetId)
                || com.stardew.craft.casino.CasinoAccessService.EXIT_TARGET_ID.equals(targetId)
                || com.stardew.craft.casino.CasinoAccessService.QI_COIN_MACHINE_TARGET_ID.equals(targetId)
                || com.stardew.craft.casino.CasinoAccessService.QI_COIN_SHOP_TARGET_ID.equals(targetId)) {
            com.stardew.craft.casino.CasinoAccessService.handlePortal(player, targetId);
            return;
        }

        // Fair participants stay on the festival map. Consume every ordinary building/mine/bus
        // portal before it can start a fade transition, while preserving the dedicated Fair targets.
        if (com.stardew.craft.festival.FairFestivalService.isParticipant(player)
                && !isFairPortalTarget(targetId)) {
            return;
        }

        if (com.stardew.craft.world.MutantBugLairService.ENTRANCE_TARGET_ID.equals(targetId)) {
            com.stardew.craft.world.MutantBugLairService.enter(player);
            return;
        }

        if (com.stardew.craft.world.MutantBugLairService.EXIT_TARGET_ID.equals(targetId)) {
            com.stardew.craft.world.MutantBugLairService.exit(player);
            return;
        }

        if (com.stardew.craft.world.WitchAreaService.HUT_ENTRANCE_TARGET_ID.equals(targetId)) {
            com.stardew.craft.world.WitchAreaService.enterHut(player);
            return;
        }

        if (com.stardew.craft.world.WitchAreaService.HUT_EXIT_TARGET_ID.equals(targetId)) {
            com.stardew.craft.world.WitchAreaService.exitHut(player);
            return;
        }

        // 矿井入口
        if ("mine_entrance".equals(targetId)) {
            handleMineEntrance(player);
            return;
        }

        // 沙漠节三花蛋商店
        if (DesertFestivalService.EGG_SHOP_TARGET_ID.equals(targetId)) {
            DesertFestivalService.openEggShop(player);
            return;
        }

        if (com.stardew.craft.festival.desert.DesertFestivalRaceService.RACE_MAN_TARGET_ID.equals(targetId)) {
            com.stardew.craft.festival.desert.DesertFestivalRaceService.openRaceScreen(player);
            return;
        }

        if (com.stardew.craft.festival.desert.DesertFestivalRaceService.SHADY_GUY_TARGET_ID.equals(targetId)) {
            com.stardew.craft.festival.desert.DesertFestivalRaceService.openShadyGuyScreen(player);
            return;
        }

        if (com.stardew.craft.festival.desert.DesertFestivalSpecialInteractionService.SCHOLAR_TARGET_ID.equals(targetId)) {
            com.stardew.craft.festival.desert.DesertFestivalSpecialInteractionService.openScholar(player);
            return;
        }

        if (com.stardew.craft.festival.desert.DesertFestivalSpecialInteractionService.WARPER_TARGET_ID.equals(targetId)) {
            com.stardew.craft.festival.desert.DesertFestivalSpecialInteractionService.openWarper(player);
            return;
        }

        if (com.stardew.craft.festival.desert.DesertFestivalWillyFishingService.TARGET_ID.equals(targetId)) {
            if (com.stardew.craft.festival.desert.DesertFestivalWillyFishingService.tryCompleteChallengeAtBoard(player)) {
                return;
            }
            com.stardew.craft.festival.desert.DesertFestivalWillyFishingService.openChallengeBoard(player);
            return;
        }

        if (com.stardew.craft.festival.desert.DesertFestivalCookService.TARGET_ID.equals(targetId)) {
            com.stardew.craft.festival.desert.DesertFestivalCookService.openCook(player);
            return;
        }

        if (com.stardew.craft.festival.fair.FairSlingshotGameService.TARGET_ID.equals(targetId)) {
            com.stardew.craft.festival.fair.FairSlingshotGameService.open(player);
            return;
        }

        if (com.stardew.craft.festival.fair.FairFishingGameService.TARGET_ID.equals(targetId)) {
            com.stardew.craft.festival.fair.FairFishingGameService.open(player);
            return;
        }

        if (com.stardew.craft.festival.FairFestivalService.STAR_TOKEN_SHOP_TARGET_ID.equals(targetId)) {
            com.stardew.craft.festival.FairFestivalService.openStarTokenShop(player);
            return;
        }

        if (com.stardew.craft.festival.FairFestivalService.STAR_TOKEN_PURCHASE_TARGET_ID.equals(targetId)) {
            com.stardew.craft.festival.FairFestivalService.openStarTokenPurchase(player);
            return;
        }

        if (com.stardew.craft.festival.FairFestivalService.FORTUNE_TELLER_TARGET_ID.equals(targetId)) {
            com.stardew.craft.festival.FairFestivalService.openFortuneTeller(player);
            return;
        }

        if (com.stardew.craft.festival.FestivalOfIceService.TRAVELING_MERCHANT_TARGET_ID.equals(targetId)) {
            com.stardew.craft.festival.FestivalOfIceService.openTravelingMerchantShop(player);
            return;
        }

        if (com.stardew.craft.festival.nightmarket.NightMarketPainterService.TARGET_ID.equals(targetId)) {
            com.stardew.craft.festival.nightmarket.NightMarketPainterService.open(player);
            return;
        }

        if (com.stardew.craft.festival.nightmarket.NightMarketCoffeeService.TARGET_ID.equals(targetId)) {
            com.stardew.craft.festival.nightmarket.NightMarketCoffeeService.open(player);
            return;
        }

        if (com.stardew.craft.festival.nightmarket.NightMarketWarperService.TARGET_ID.equals(targetId)) {
            com.stardew.craft.festival.nightmarket.NightMarketWarperService.open(player);
            return;
        }

        if (com.stardew.craft.festival.nightmarket.NightMarketShopService.DECORATION_BOAT_TARGET_ID.equals(targetId)) {
            com.stardew.craft.festival.nightmarket.NightMarketShopService.openDecorationBoat(player);
            return;
        }

        if (com.stardew.craft.festival.nightmarket.NightMarketShopService.MAGIC_BOAT_TARGET_ID.equals(targetId)) {
            com.stardew.craft.festival.nightmarket.NightMarketShopService.openMagicBoat(player);
            return;
        }

        if (com.stardew.craft.festival.nightmarket.NightMarketSubmarineService.ENTRANCE_TARGET_ID.equals(targetId)) {
            com.stardew.craft.festival.nightmarket.NightMarketSubmarineService.openEntrance(player);
            return;
        }

        if (com.stardew.craft.festival.nightmarket.NightMarketSubmarineService.EXIT_TARGET_ID.equals(targetId)) {
            com.stardew.craft.festival.nightmarket.NightMarketSubmarineService.openExit(player);
            return;
        }

        if (com.stardew.craft.festival.nightmarket.NightMarketMermaidService.TARGET_ID.equals(targetId)) {
            com.stardew.craft.festival.nightmarket.NightMarketMermaidService.open(player);
            return;
        }

        // 骷髅矿入口（沙漠 → floor 121）
        if ("desert_mine_enter".equals(targetId)) {
            handleDesertMineEntrance(player);
            return;
        }

        // 沙漠公交站（买票前往沙漠）
        if ("desert_bus".equals(targetId)) {
            handleDesertBus(player);
            return;
        }

        // 沙漠公交站（返程鹈鹕镇，免费）
        if ("desert_bus_return".equals(targetId)) {
            handleDesertBusReturn(player);
            return;
        }

        // 农场入口
        if (targetId.startsWith("farm_entry_")) {
            handleFarmEntry(player, targetId);
            return;
        }

        // 农场出口
        if (targetId.startsWith("farm_exit_")) {
            handleFarmExit(player, targetId);
            return;
        }

        // 社区中心入口
        if ("community_center_enter".equals(targetId)) {
            handleCCEntry(player);
            return;
        }

        // 社区中心出口
        if ("community_center_exit".equals(targetId)) {
            handleCCExit(player);
            return;
        }

        // 温室入口
        if ("greenhouse_enter".equals(targetId)) {
            handleGreenhouseEntry(player);
            return;
        }

        // 温室出口
        if ("greenhouse_exit".equals(targetId)) {
            handleGreenhouseExit(player);
            return;
        }

        // 农场洞穴入口
        if ("farm_cave_enter".equals(targetId)) {
            handleFarmCaveEntry(player);
            return;
        }

        // 农场洞穴出口
        if ("farm_cave_exit".equals(targetId)) {
            handleFarmCaveExit(player);
            return;
        }

        // 采石场入口（需要完成工艺室献祭）
        if ("quarry_entrance".equals(targetId)) {
            handleQuarryEntrance(player);
            return;
        }

        // 采石场出口（无条件返程）
        if ("quarry_exit".equals(targetId)) {
            handleQuarryExit(player);
            return;
        }

        // 下水道入口（需要生锈的钥匙）
        if ("sewer_enter".equals(targetId)) {
            handleSewerEntrance(player);
            return;
        }

        // 下水道出口（无条件返程）
        if ("sewer_exit".equals(targetId)) {
            handleSewerExit(player);
            return;
        }

        // Farm creation registers its instance before the schematic is ready. Disable both
        // Wizard Tower exits during that window so neither can start a competing transition or
        // poison the completion warp's shared cooldown.
        if ("wizard_tower_exit".equals(targetId) || "wizard_tower_return_overworld".equals(targetId)) {
            com.stardew.craft.farm.FarmInstance farm = com.stardew.craft.farm.FarmInstanceRegistry.get()
                    .getFarmForPlayer(player.getUUID());
            if (farm != null && !farm.isInitialized()) {
                return;
            }
        }

        // 巫师塔 → 回主世界
        if ("wizard_tower_return_overworld".equals(targetId)) {
            CrossDimensionTeleporter.wizardInteriorToOverworld(player);
            return;
        }

        // 巫师塔出口（基于任务状态路由）
        if ("wizard_tower_exit".equals(targetId)) {
            com.stardew.craft.player.PlayerStardewData pdata = com.stardew.craft.player.PlayerDataManager.getPlayerData(player);
            if (!pdata.isWizardQuestComplete()) {
                net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> src = pdata.getWizardSourceDimension();
                if (src != null && net.minecraft.world.level.Level.OVERWORLD.equals(src)) {
                    CrossDimensionTeleporter.wizardInteriorToOverworld(player);
                    return;
                }
            }
            // 任务已完成 或 来自星露谷 → 走下面的通用 portal 逻辑
        }

        // 巫师塔入口（记录来源维度）
        if ("wizard_tower_enter".equals(targetId)) {
            com.stardew.craft.player.PlayerStardewData pdata = com.stardew.craft.player.PlayerDataManager.getPlayerData(player);
            pdata.setWizardSourceDimension(ModDimensions.STARDEW_VALLEY);
        }

        if (LuckyPurpleShortsWorldEvents.LEWIS_BASEMENT_EXIT_TARGET.equals(targetId)) {
            InteriorSubspaceManager.ensureLoaded(player.serverLevel(), "lewis_basement_exit");
            long now = player.serverLevel().getGameTime();
            long last = player.getPersistentData().getLong(PLAYER_LAST_PORTAL_TICK);
            if (now - last < PORTAL_COOLDOWN_TICKS) return;
            beginPortalTransition(player, "mayor_house_enter", now);
            return;
        }

        if ("mayor_house_enter".equals(targetId)) {
            if (player.getPersistentData().getBoolean("stardewcraft_auction_enter_house_once")) {
                player.getPersistentData().remove("stardewcraft_auction_enter_house_once");
            } else if (com.stardew.craft.auction.AuctionService.tryOpenAuctionEntryChoice(player)) {
                return;
            }
        }

        if (ShopHoursService.blockClosedPortal(player, targetId)) {
            return;
        }

        // ── 通用 Portal Registry 查找 ──
        Optional<InteriorPortalRegistry.PortalTarget> resolved =
                InteriorPortalRegistry.resolve(
                        targetId, player.serverLevel().dimension().location());
        if (resolved.isEmpty()) return;

        // Museum exit guard
        if ("museum_exit".equals(targetId)) {
            com.stardew.craft.museum.MuseumDonationData museumData =
                com.stardew.craft.museum.MuseumDonationData.get(player.serverLevel());
            if (museumData.isDonationModeActive(player.getUUID())) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new com.stardew.craft.network.payload.OpenNpcDialogueScreenPayload(
                        "gunther",
                        "stardewcraft.npc.gunther.donation_exit_blocked",
                        0
                    ));
                return;
            }
        }

        // 确保室内布局已初始化
        InteriorSubspaceManager.ensureLoaded(player.serverLevel(), "portal_interaction");

        // 冷却检查
        long now = player.serverLevel().getGameTime();
        long last = player.getPersistentData().getLong(PLAYER_LAST_PORTAL_TICK);
        if (now - last < PORTAL_COOLDOWN_TICKS) return;

        beginPortalTransition(player, targetId, now);
    }

    // ======================== 工具方法 ========================

    private static Optional<String> findTagValue(Set<String> tags, String prefix) {
        for (String tag : tags) {
            if (tag != null && tag.startsWith(prefix) && tag.length() > prefix.length()) {
                return Optional.of(tag.substring(prefix.length()));
            }
        }
        return Optional.empty();
    }

    private static boolean isFairPortalTarget(String targetId) {
        return com.stardew.craft.festival.fair.FairSlingshotGameService.TARGET_ID.equals(targetId)
            || com.stardew.craft.festival.fair.FairFishingGameService.TARGET_ID.equals(targetId)
            || com.stardew.craft.festival.FairFestivalService.STAR_TOKEN_SHOP_TARGET_ID.equals(targetId)
            || com.stardew.craft.festival.FairFestivalService.STAR_TOKEN_PURCHASE_TARGET_ID.equals(targetId)
            || com.stardew.craft.festival.FairFestivalService.FORTUNE_TELLER_TARGET_ID.equals(targetId);
    }

    private static void applyInteriorFlag(ServerPlayer player, InteriorPortalRegistry.PortalMode mode) {
        if (mode == InteriorPortalRegistry.PortalMode.ENTRANCE) {
            markInteriorEnter(player);
            return;
        }
        if (mode == InteriorPortalRegistry.PortalMode.EXIT) {
            clearInteriorState(player);
        }
    }

    private static void beginPortalTransition(ServerPlayer player, String targetId, long now) {
        player.closeContainer();
        player.stopUsingItem();
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);

        player.getPersistentData().putString(PLAYER_PENDING_PORTAL_ID, targetId);
        player.getPersistentData().putLong(PLAYER_PENDING_PORTAL_START, now);
        player.getPersistentData().putLong(PLAYER_LAST_PORTAL_TICK, now);

        playPlayerSound(player, ModSounds.DOOR_CLOSE.get(), 0.9F, 1.0F);
        PacketDistributor.sendToPlayer(player, new DesertBusFadePayload((byte) 0, PORTAL_FADE_OUT_TICKS));
    }

    private static void tickPendingPortalTransition(ServerPlayer player) {
        long start = player.getPersistentData().getLong(PLAYER_PENDING_PORTAL_START);
        if (start == 0L) return;

        long elapsed = player.serverLevel().getGameTime() - start;
        if (elapsed == PORTAL_WARP_AT) {
            String targetId = player.getPersistentData().getString(PLAYER_PENDING_PORTAL_ID);
            Optional<InteriorPortalRegistry.PortalTarget> resolved =
                    InteriorPortalRegistry.resolve(
                            targetId,
                            player.serverLevel().dimension().location());
            if (resolved.isEmpty()) {
                clearPendingPortalTransition(player);
                return;
            }

            InteriorPortalRegistry.PortalTarget target = resolved.get();
            player.teleportTo(
                player.serverLevel(),
                target.x(), target.y(), target.z(),
                target.yaw(), target.pitch()
            );
            applyInteriorFlag(player, target.mode());
        } else if (elapsed == PORTAL_FADE_IN_AT) {
            PacketDistributor.sendToPlayer(player, new DesertBusFadePayload((byte) 1, PORTAL_FADE_IN_TICKS));
        } else if (elapsed >= PORTAL_TOTAL_TICKS) {
            clearPendingPortalTransition(player);
        }
    }

    private static void clearPendingPortalTransition(ServerPlayer player) {
        player.getPersistentData().remove(PLAYER_PENDING_PORTAL_ID);
        player.getPersistentData().remove(PLAYER_PENDING_PORTAL_START);
    }

    private static void playPlayerSound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
            sound, SoundSource.PLAYERS, volume, pitch);
    }

    public static void markInteriorEnter(ServerPlayer player) {
        player.getPersistentData().putBoolean(PLAYER_FLAG_INTERIOR, true);
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, -1, 0, false, false, false));
    }

    public static void clearInteriorState(ServerPlayer player) {
        player.getPersistentData().putBoolean(PLAYER_FLAG_INTERIOR, false);
        player.removeEffect(MobEffects.NIGHT_VISION);
    }

    public static boolean isPlayerInInteriorSpace(ServerPlayer player) {
        return player.getPersistentData().getBoolean(PLAYER_FLAG_INTERIOR);
    }

    public static boolean isRecentPortalTeleport(ServerPlayer player, long graceTicks) {
        long last = player.getPersistentData().getLong(PLAYER_LAST_PORTAL_TICK);
        return player.serverLevel().getGameTime() - last <= graceTicks;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!event.getEntity().level().isClientSide && event.getEntity() instanceof ServerPlayer player) {
            tickPendingPortalTransition(player);
            if (player.tickCount % 40 == 0) {
                if (isPlayerInInteriorSpace(player)) {
                    if (!player.hasEffect(MobEffects.NIGHT_VISION)) {
                        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, -1, 0, false, false, false));
                    }
                }
            }
        }
    }

    // ======================== 沙漠公交站 ========================

    private static void handleDesertBus(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        long last = player.getPersistentData().getLong(PLAYER_LAST_PORTAL_TICK);
        if (now - last < PORTAL_COOLDOWN_TICKS) return;
        player.getPersistentData().putLong(PLAYER_LAST_PORTAL_TICK, now);

        com.stardew.craft.desert.DesertBusService.beginBusRide(player);
    }

    private static void handleDesertBusReturn(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        long last = player.getPersistentData().getLong(PLAYER_LAST_PORTAL_TICK);
        if (now - last < PORTAL_COOLDOWN_TICKS) return;
        player.getPersistentData().putLong(PLAYER_LAST_PORTAL_TICK, now);

        com.stardew.craft.desert.DesertBusService.beginReturnRide(player);
    }

    // ======================== 矿井跨维度传送 ========================

    private static final double MINE_OUTDOOR_X = 84.5;
    private static final double MINE_OUTDOOR_Y = 81.0;
    private static final double MINE_OUTDOOR_Z = -145.5;
    // 矿井大厅落点统一走 MiningCoordinates（0 层中心 (0,64,0)、出生点 (0.5,66,-7.5) 面朝北）。

    private static void handleFarmEntry(ServerPlayer player, String entryTag) {
        long now = player.serverLevel().getGameTime();
        long last = player.getPersistentData().getLong(PLAYER_LAST_PORTAL_TICK);
        if (now - last < PORTAL_COOLDOWN_TICKS) return;
        player.getPersistentData().putLong(PLAYER_LAST_PORTAL_TICK, now);

        com.stardew.craft.farm.FarmInstanceRegistry registry = com.stardew.craft.farm.FarmInstanceRegistry.get();
        com.stardew.craft.farm.FarmInstance myFarm = registry.getFarmForPlayer(player.getUUID());

        if (myFarm == null) {
            com.stardew.craft.farm.FarmJoinManager.syncPendingState(
                player,
                com.stardew.craft.farm.FarmJoinManager.hasPending(player.getUUID())
            );
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new com.stardew.craft.network.payload.OpenFarmSelectionPayload());
            StardewCraft.LOGGER.info("[FARM_ENTRY] {} has no farm, opening selection screen",
                    player.getName().getString());
            return;
        }

        com.stardew.craft.network.payload.FarmListSyncPayload.sendToPlayer(player, entryTag);
        StardewCraft.LOGGER.info("[FARM_ENTRY] {} opening farm entry GUI via {}",
                player.getName().getString(), entryTag);
    }

    private static final net.minecraft.core.BlockPos PUBLIC_EAST_FARM_TARGET = new net.minecraft.core.BlockPos(-62, 64, -43);
    private static final net.minecraft.core.BlockPos PUBLIC_SOUTH_FARM_TARGET = new net.minecraft.core.BlockPos(-114, 64, -2);
    private static final net.minecraft.core.BlockPos PUBLIC_NORTH_FARM_TARGET = new net.minecraft.core.BlockPos(-114, 69, -64);

    private static void handleFarmExit(ServerPlayer player, String exitId) {
        long now = player.serverLevel().getGameTime();
        long last = player.getPersistentData().getLong(PLAYER_LAST_PORTAL_TICK);
        long elapsed = now - last;
        if (elapsed >= 0L && elapsed < PORTAL_COOLDOWN_TICKS) return;
        player.getPersistentData().putLong(PLAYER_LAST_PORTAL_TICK, now);

        com.stardew.craft.farm.FarmChunkManager.get().onPlayerLeaveFarm(
                player.serverLevel(), player);

        net.minecraft.core.BlockPos target = switch (exitId) {
            case "farm_exit_south" -> PUBLIC_NORTH_FARM_TARGET;
            case "farm_exit_east" -> PUBLIC_SOUTH_FARM_TARGET;
            case "farm_exit_west" -> PUBLIC_EAST_FARM_TARGET;
            default -> PUBLIC_EAST_FARM_TARGET;
        };

        float yaw = switch (exitId) {
            case "farm_exit_south" -> 180.0F;
            case "farm_exit_east" -> 0.0F;
            case "farm_exit_west" -> -90.0F;
            default -> -90.0F;
        };

        player.teleportTo(player.serverLevel(), target.getX() + 0.5, target.getY(), target.getZ() + 0.5, yaw, 0.0F);
        StardewCraft.LOGGER.info("[FARM_EXIT] {} exited farm via {} to {}",
                player.getName().getString(), exitId, target);
    }

    private static void handleMineEntrance(ServerPlayer player) {
        ServerLevel mineLevel = player.server.getLevel(ModMiningDimensions.STARDEW_MINING);
        if (mineLevel == null) {
            StardewCraft.LOGGER.warn("[MINE_PORTAL] Mine dimension not available");
            return;
        }

        long now = player.serverLevel().getGameTime();
        long last = player.getPersistentData().getLong(PLAYER_LAST_PORTAL_TICK);
        if (now - last < PORTAL_COOLDOWN_TICKS) return;

        MineEntranceBootstrap.ensureGenerated(mineLevel);
        com.stardew.craft.mining.MiningCoordinates.teleportPlayerToFloor(player, mineLevel, 0);
        player.getPersistentData().putLong(PLAYER_LAST_PORTAL_TICK, now);
    }

    /** 骷髅矿入口：传送到 floor 121 大厅 schem 内部 (origin + 3, 1, 3) */
    private static void handleDesertMineEntrance(ServerPlayer player) {
        ServerLevel mineLevel = player.server.getLevel(ModMiningDimensions.STARDEW_MINING);
        if (mineLevel == null) {
            StardewCraft.LOGGER.warn("[SKULL_CAVERN] Mine dimension not available");
            return;
        }

        long now = player.serverLevel().getGameTime();
        long last = player.getPersistentData().getLong(PLAYER_LAST_PORTAL_TICK);
        if (now - last < PORTAL_COOLDOWN_TICKS) return;

        // SDV 原版门禁：必须拥有 SkullKey 才能进入 (GameLocation.SkullDoor)
        com.stardew.craft.player.PlayerStardewData sdData =
                com.stardew.craft.player.PlayerDataManager.getPlayerData(player);
        if (!sdData.hasMailFlag(com.stardew.craft.communitycenter.state.CCStoryFlags.HAS_SKULL_KEY)) {
            ObjectDialogueService.show(player, "message.stardewcraft.skull_door_locked");
            player.playNotifySound(net.minecraft.sounds.SoundEvents.IRON_DOOR_OPEN,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 0.7f);
            player.getPersistentData().putLong(PLAYER_LAST_PORTAL_TICK, now);
            return;
        }

        unlockSkullDoorQuest(player);

        // 生成 floor 121 入口大厅（使用 skullkeyentrance.schem）
        com.stardew.craft.mining.MineFloorGenerator.generateFloor(mineLevel, 121);

        // 传送到 schem 内部 spawn (origin + 3, 1, 3) — ModTeleport 自动跳过维度拦截
        net.minecraft.core.BlockPos spawn = com.stardew.craft.mining.MineFloorGenerator.SKULL_CAVERN_LOBBY_SPAWN;
        player.invulnerableTime = Math.max(player.invulnerableTime, 20);
        ModTeleport.to(player, mineLevel,
                spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
                0.0F, 0.0F);
        player.setDeltaMovement(0, 0, 0);
        player.fallDistance = 0;
        player.hurtMarked = true;
        player.invulnerableTime = Math.max(player.invulnerableTime, 20);

        // 更新玩家矿井数据
        com.stardew.craft.mining.MiningPlayerData pData = com.stardew.craft.mining.MiningDataManager.getPlayerData(player);
        if (pData != null) {
            pData.setCurrentFloor(121);
            com.stardew.craft.mining.MiningDataManager.savePlayerData(player, pData);
        }

        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
            player,
            new com.stardew.craft.network.MiningFloorSyncPacket(121)
        );

        com.stardew.craft.mining.SkullCavernSessionManager.onPlayerEnter(player);

        player.getPersistentData().putLong(PLAYER_LAST_PORTAL_TICK, now);
        StardewCraft.LOGGER.info("[SKULL_CAVERN] {} entered skull cavern lobby at {}", player.getName().getString(), spawn);
    }

    private static void unlockSkullDoorQuest(ServerPlayer player) {
        boolean firstUnlock = !com.stardew.craft.mail.MailService.hasOrWillReceiveMail(player, "skullCave");
        if (firstUnlock) {
            ObjectDialogueService.show(player, "message.stardewcraft.skull_door_unlock");
            com.stardew.craft.mail.MailService.addMailForTomorrow(player, "skullCave");
        }

        com.stardew.craft.quest.QuestManager qm = com.stardew.craft.quest.QuestManager.of(player);
        if (qm == null || qm.isQuestCompleted("19")) return;
        if (!qm.hasQuest("19")) {
            qm.acceptQuest("19", player);
        }
        com.stardew.craft.quest.StardewQuest quest = qm.getQuest("19");
        if (quest != null && !quest.isCompleted()) {
            qm.completeActiveQuest("19", player);
        }
    }

    /** 骷髅矿大厅出口：传回沙漠，朝南 */
    private static void handleSkullCavernExit(ServerPlayer player) {
        ServerLevel stardewLevel = player.server.getLevel(ModDimensions.STARDEW_VALLEY);
        if (stardewLevel == null) {
            StardewCraft.LOGGER.warn("[SKULL_CAVERN] Stardew Valley dimension not available");
            return;
        }

        long now = player.serverLevel().getGameTime();
        long last = player.getPersistentData().getLong(PLAYER_LAST_PORTAL_TICK);
        if (now - last < PORTAL_COOLDOWN_TICKS) return;

        player.closeContainer();
        player.stopUsingItem();

        // session 清理（若本层无其他玩家则重置）
        com.stardew.craft.mining.SkullCavernSessionManager.onPlayerLeave(player, player.serverLevel());

        net.minecraft.core.BlockPos arrival = com.stardew.craft.desert.DesertConstants.worldPos(
                com.stardew.craft.desert.DesertConstants.SKULL_CAVERN_EXIT_OFFSET);
        ModTeleport.to(player, stardewLevel,
                arrival.getX() + 0.5D, arrival.getY(), arrival.getZ() + 0.5D,
                180.0F, 0.0F);

        // 重置楼层显示为 0（离开骷髅矿）
        com.stardew.craft.mining.MiningPlayerData pData = com.stardew.craft.mining.MiningDataManager.getPlayerData(player);
        if (pData != null) {
            pData.setCurrentFloor(0);
            com.stardew.craft.mining.MiningDataManager.savePlayerData(player, pData);
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
            player,
            new com.stardew.craft.network.MiningFloorSyncPacket(0)
        );

        player.getPersistentData().putLong(PLAYER_LAST_PORTAL_TICK, now);
        StardewCraft.LOGGER.info("[SKULL_CAVERN] {} exited skull cavern to desert at {}", player.getName().getString(), arrival);
    }

    private static void handleMineExit(ServerPlayer player) {
        ServerLevel stardewLevel = player.server.getLevel(ModDimensions.STARDEW_VALLEY);
        if (stardewLevel == null) {
            StardewCraft.LOGGER.warn("[MINE_PORTAL] Stardew Valley dimension not available");
            return;
        }

        long now = player.serverLevel().getGameTime();
        long last = player.getPersistentData().getLong(PLAYER_LAST_PORTAL_TICK);
        if (now - last < PORTAL_COOLDOWN_TICKS) return;

        player.closeContainer();
        player.stopUsingItem();

        ModTeleport.to(player, stardewLevel, MINE_OUTDOOR_X, MINE_OUTDOOR_Y, MINE_OUTDOOR_Z,
                          180.0F, 0.0F);
        player.getPersistentData().putLong(PLAYER_LAST_PORTAL_TICK, now);
    }

    // ════════════════════════════════════════════════════════════════
    //  社区中心 / 温室 — 玩家独立室内空间
    // ════════════════════════════════════════════════════════════════

    private static void handleCCEntry(ServerPlayer player) {
        com.stardew.craft.player.PlayerStardewData data =
                com.stardew.craft.player.PlayerDataManager.getPlayerData(player);
        // 本项目简化：Joja 会员视同"CC 已被 Joja 接管"，不可进入。
        // SDV 原版是进入后看到仓库外观；我们服务器共享地图不做两套装饰层，直接拦入口。
        if (data.hasMailFlag(com.stardew.craft.communitycenter.state.CCStoryFlags.JOJA_MEMBER)) {
            ObjectDialogueService.show(player, "stardewcraft.portal.cc.joja_warehouse");
            return;
        }
        // SDV parity: CC door is locked until event 611439 (lewis_cc_tour) sets ccDoorUnlock
        if (!data.hasMailFlag(com.stardew.craft.communitycenter.state.CCStoryFlags.CC_DOOR_UNLOCKED)) {
            ObjectDialogueService.show(player, "stardewcraft.portal.cc.locked");
            return;
        }
        handleCCEntryCore(player);
    }

    /**
     * Public entry for cutscene use — skips cooldown check.
     */
    public static void handleCCEntryForCutscene(ServerPlayer player) {
        handleCCEntryCore(player);
    }

    private static void handleCCEntryCore(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        InteriorSubspaceManager.ensureLoaded(level, "cc_entry");

        PlayerInteriorAllocator alloc = PlayerInteriorAllocator.get(level);
        net.minecraft.core.BlockPos ccOrigin = alloc.ensureCCLoaded(level, player.getUUID());
        net.minecraft.core.BlockPos spawnPos = ccOrigin.offset(InteriorSubspaceManager.CC_INDOOR_SPAWN_OFFSET);

        player.closeContainer();
        player.stopUsingItem();

        player.teleportTo(level,
            spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D,
            -90.0F, 0.0F);
        player.getPersistentData().putLong(PLAYER_LAST_PORTAL_TICK, player.serverLevel().getGameTime());
        applyInteriorFlag(player, InteriorPortalRegistry.PortalMode.ENTRANCE);

    }

    private static void handleCCExit(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        long last = player.getPersistentData().getLong(PLAYER_LAST_PORTAL_TICK);
        if (now - last < PORTAL_COOLDOWN_TICKS) return;

        net.minecraft.core.BlockPos exitPos = InteriorSubspaceManager.CC_OUTDOOR_EXIT_POS;

        player.closeContainer();
        player.stopUsingItem();

        player.teleportTo(player.serverLevel(),
            exitPos.getX() + 0.5D, exitPos.getY(), exitPos.getZ() + 0.5D,
            180.0F, 0.0F);
        player.getPersistentData().putLong(PLAYER_LAST_PORTAL_TICK, now);
        applyInteriorFlag(player, InteriorPortalRegistry.PortalMode.EXIT);
    }

    private static void handleGreenhouseEntry(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        long last = player.getPersistentData().getLong(PLAYER_LAST_PORTAL_TICK);
        if (now - last < PORTAL_COOLDOWN_TICKS) return;

        ServerLevel level = player.serverLevel();
        InteriorSubspaceManager.ensureLoaded(level, "greenhouse_entry");

        PlayerInteriorAllocator alloc = PlayerInteriorAllocator.get(level);
        net.minecraft.core.BlockPos ghOrigin = alloc.ensureGreenhouseLoaded(level, player.getUUID());
        net.minecraft.core.BlockPos spawnPos = ghOrigin.offset(InteriorSubspaceManager.GREENHOUSE_INDOOR_SPAWN_OFFSET);

        player.closeContainer();
        player.stopUsingItem();

        player.teleportTo(level,
            spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D,
            -90.0F, 0.0F);
        com.stardew.craft.manager.FertilizerManager.get(level).syncAllFertilizersToPlayer(player);
        player.getPersistentData().putLong(PLAYER_LAST_PORTAL_TICK, now);
        applyInteriorFlag(player, InteriorPortalRegistry.PortalMode.ENTRANCE);

    }

    // ════════════════════════════════════════════════════════════════
    //  农场洞穴（每玩家独立）
    // ════════════════════════════════════════════════════════════════

    private static void handleFarmCaveEntry(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        long last = player.getPersistentData().getLong(PLAYER_LAST_PORTAL_TICK);
        if (now - last < PORTAL_COOLDOWN_TICKS) return;

        ServerLevel level = player.serverLevel();

        // 农场 owner 决定用谁的洞穴（成员共享 owner 的洞穴）
        com.stardew.craft.farm.FarmInstance farm = com.stardew.craft.farm.FarmInstanceRegistry.get()
                .getFarmForPlayer(player.getUUID());
        java.util.UUID caveOwner = (farm != null) ? farm.getOwnerUUID() : player.getUUID();

        PlayerInteriorAllocator alloc = PlayerInteriorAllocator.get(level);
        net.minecraft.core.BlockPos caveOrigin = alloc.ensureCaveLoaded(level, caveOwner);
        net.minecraft.core.BlockPos spawnPos = caveOrigin.offset(InteriorSubspaceManager.FARM_CAVE_INDOOR_SPAWN_OFFSET);

        player.closeContainer();
        player.stopUsingItem();

        player.teleportTo(level,
            spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D,
            -90.0F, 0.0F);
        player.getPersistentData().putLong(PLAYER_LAST_PORTAL_TICK, now);
        applyInteriorFlag(player, InteriorPortalRegistry.PortalMode.ENTRANCE);

    }

    private static void handleFarmCaveExit(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        long last = player.getPersistentData().getLong(PLAYER_LAST_PORTAL_TICK);
        if (now - last < PORTAL_COOLDOWN_TICKS) return;

        ServerLevel level = player.serverLevel();
        // 反查当前所在的洞穴属于哪个玩家
        java.util.UUID caveOwner = PlayerInteriorAllocator.get(level).findCaveOwner(player.blockPosition());
        com.stardew.craft.farm.FarmInstance farm = null;
        if (caveOwner != null) {
            farm = com.stardew.craft.farm.FarmInstanceRegistry.get().getFarm(caveOwner);
        }
        if (farm == null) {
            // 兜底：按玩家自身农场反查
            farm = com.stardew.craft.farm.FarmInstanceRegistry.get().getFarmForPlayer(player.getUUID());
        }
        if (farm == null || farm.getFarmLayout().caveExitSpawn() == null) {
            StardewCraft.LOGGER.warn("[FARM-CAVE] Cannot resolve exit target for {}, aborting", player.getName().getString());
            return;
        }

        net.minecraft.core.BlockPos exitOffset =
                farm.getFarmLayout().caveExitSpawn();
        float yaw = farm.getFarmLayout().caveExitYaw();
        net.minecraft.core.BlockPos exitAbs = farm.getOrigin().offset(exitOffset);

        player.closeContainer();
        player.stopUsingItem();
        player.teleportTo(level,
            exitAbs.getX() + 0.5D, exitAbs.getY(), exitAbs.getZ() + 0.5D,
            yaw, 0.0F);
        player.getPersistentData().putLong(PLAYER_LAST_PORTAL_TICK, now);
        applyInteriorFlag(player, InteriorPortalRegistry.PortalMode.EXIT);

    }

    // ════════════════════════════════════════════════════════════════
    //  采石场访问（工艺室献祭解锁）
    // ════════════════════════════════════════════════════════════════

    private static void handleQuarryEntrance(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        long last = player.getPersistentData().getLong(PLAYER_LAST_PORTAL_TICK);
        if (now - last < PORTAL_COOLDOWN_TICKS) return;

        com.stardew.craft.player.PlayerStardewData data =
                com.stardew.craft.player.PlayerDataManager.getPlayerData(player);
        if (!data.hasMailFlag(com.stardew.craft.communitycenter.state.CCStoryFlags.CC_CRAFTS_ROOM)) {
            ObjectDialogueService.show(player, "stardewcraft.portal.quarry.blocked");
            player.getPersistentData().putLong(PLAYER_LAST_PORTAL_TICK, now);
            return;
        }

        player.closeContainer();
        player.stopUsingItem();
        player.teleportTo(player.serverLevel(),
                com.stardew.craft.communitycenter.quarry.QuarryAccessManager.ENTRY_DEST_X,
                com.stardew.craft.communitycenter.quarry.QuarryAccessManager.ENTRY_DEST_Y,
                com.stardew.craft.communitycenter.quarry.QuarryAccessManager.ENTRY_DEST_Z,
                com.stardew.craft.communitycenter.quarry.QuarryAccessManager.ENTRY_DEST_YAW,
                com.stardew.craft.communitycenter.quarry.QuarryAccessManager.ENTRY_DEST_PITCH);
        player.getPersistentData().putLong(PLAYER_LAST_PORTAL_TICK, now);
    }

    private static void handleQuarryExit(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        long last = player.getPersistentData().getLong(PLAYER_LAST_PORTAL_TICK);
        if (now - last < PORTAL_COOLDOWN_TICKS) return;

        player.closeContainer();
        player.stopUsingItem();
        player.teleportTo(player.serverLevel(),
                com.stardew.craft.communitycenter.quarry.QuarryAccessManager.EXIT_DEST_X,
                com.stardew.craft.communitycenter.quarry.QuarryAccessManager.EXIT_DEST_Y,
                com.stardew.craft.communitycenter.quarry.QuarryAccessManager.EXIT_DEST_Z,
                com.stardew.craft.communitycenter.quarry.QuarryAccessManager.EXIT_DEST_YAW,
                com.stardew.craft.communitycenter.quarry.QuarryAccessManager.EXIT_DEST_PITCH);
        player.getPersistentData().putLong(PLAYER_LAST_PORTAL_TICK, now);
    }

    private static void handleSewerEntrance(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        long last = player.getPersistentData().getLong(PLAYER_LAST_PORTAL_TICK);
        if (now - last < PORTAL_COOLDOWN_TICKS) return;

        com.stardew.craft.player.PlayerStardewData data =
                com.stardew.craft.player.PlayerDataManager.getPlayerData(player);
        if (!com.stardew.craft.sewer.SewerService.hasRustyKey(data)) {
            ObjectDialogueService.show(player, "stardewcraft.portal.sewer.locked");
            player.getPersistentData().putLong(PLAYER_LAST_PORTAL_TICK, now);
            return;
        }

        player.closeContainer();
        player.stopUsingItem();
        player.teleportTo(player.serverLevel(),
                com.stardew.craft.sewer.SewerAccessManager.ENTRY_DEST_X,
                com.stardew.craft.sewer.SewerAccessManager.ENTRY_DEST_Y,
                com.stardew.craft.sewer.SewerAccessManager.ENTRY_DEST_Z,
                com.stardew.craft.sewer.SewerAccessManager.ENTRY_DEST_YAW,
                com.stardew.craft.sewer.SewerAccessManager.ENTRY_DEST_PITCH);
        player.getPersistentData().putLong(PLAYER_LAST_PORTAL_TICK, now);
        com.stardew.craft.sewer.SewerService.markOpenedSewer(player);
    }

    private static void handleSewerExit(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        long last = player.getPersistentData().getLong(PLAYER_LAST_PORTAL_TICK);
        if (now - last < PORTAL_COOLDOWN_TICKS) return;

        player.closeContainer();
        player.stopUsingItem();
        player.teleportTo(player.serverLevel(),
                com.stardew.craft.sewer.SewerAccessManager.EXIT_DEST_X,
                com.stardew.craft.sewer.SewerAccessManager.EXIT_DEST_Y,
                com.stardew.craft.sewer.SewerAccessManager.EXIT_DEST_Z,
                com.stardew.craft.sewer.SewerAccessManager.EXIT_DEST_YAW,
                com.stardew.craft.sewer.SewerAccessManager.EXIT_DEST_PITCH);
        player.getPersistentData().putLong(PLAYER_LAST_PORTAL_TICK, now);
    }

    private static void handleGreenhouseExit(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        long last = player.getPersistentData().getLong(PLAYER_LAST_PORTAL_TICK);
        if (now - last < PORTAL_COOLDOWN_TICKS) return;

        player.closeContainer();
        player.stopUsingItem();

        net.minecraft.core.BlockPos exitPos = com.stardew.craft.greenhouse.GreenhouseManager.getExitPosForPlayer(player);
        if (exitPos == null) {
            ObjectDialogueService.show(player, net.minecraft.network.chat.Component.translatable("stardewcraft.farm.create_first"));
            StardewCraft.LOGGER.warn("[GREENHOUSE] Refused greenhouse exit for {}: no personal farm",
                    player.getName().getString());
            return;
        }

        player.teleportTo(player.serverLevel(),
            exitPos.getX() + 0.5D, exitPos.getY(), exitPos.getZ() + 0.5D,
            -90.0F, 0.0F);
        com.stardew.craft.manager.FertilizerManager.get(player.serverLevel()).syncAllFertilizersToPlayer(player);
        player.getPersistentData().putLong(PLAYER_LAST_PORTAL_TICK, now);
        applyInteriorFlag(player, InteriorPortalRegistry.PortalMode.EXIT);
    }
}

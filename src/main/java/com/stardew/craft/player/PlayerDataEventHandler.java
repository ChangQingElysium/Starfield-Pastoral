package com.stardew.craft.player;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.book.BookPowerEffects;
import com.stardew.craft.combat.CombatDamageHistory;
import com.stardew.craft.combat.DamageAdjustment;
import com.stardew.craft.combat.DamageOutcome;
import com.stardew.craft.combat.DamagePipeline;
import com.stardew.craft.combat.DamageRequest;
import com.stardew.craft.combat.IncomingDamageResolver;
import com.stardew.craft.combat.MonsterStats;
import com.stardew.craft.combat.WeaponStats;
import com.stardew.craft.combat.equipment.CombatRingRules;
import com.stardew.craft.combat.equipment.YobaProtectionState;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.mining.MineRewardClaimManager;
import com.stardew.craft.mining.MiningDataManager;
import com.stardew.craft.mining.MiningPlayerData;
import com.stardew.craft.network.PlayerDataSyncPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 玩家数据事件处理器
 * 负责数据的自动保存和同步
 */
@EventBusSubscriber(modid = StardewCraft.MODID)
@SuppressWarnings("null")
public class PlayerDataEventHandler {
    
    private static int tickCounter = 0;
    private static final int AUTO_SAVE_INTERVAL = 6000; // 5分钟 (6000 ticks)
    private static final double MAGNET_DIRECT_PICKUP_DISTANCE = 1.35D;
    private static final double MAGNET_BASE_ACCELERATION = 0.18D;
    private static final double MAGNET_NEAR_ACCELERATION = 0.48D;
    private static final double MAGNET_MAX_SPEED = 1.35D;
    
    /**
     * 玩家登录时初始化数据
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Recover shop purchases that were paid for but not placed before disconnect/restart.
            com.stardew.craft.network.payload.ShopPickupPayload.deliverAllPending(player);

            // 初始化 AFK 跟踪
            com.stardew.craft.event.SleepVoteTracker.markActive(player);
            // 获取或创建玩家数据（会自动从NBT加载）
            PlayerStardewData data = PlayerDataManager.getPlayerData(player);
            data.setLastKnownName(player.getName().getString());
            if (!data.getPreferredName().isBlank()) {
                com.stardew.craft.farm.FarmInstanceRegistry.get()
                        .updateOwnerName(player.getUUID(), data.getPreferredName());
            }
            handlePregenRelocationIfNeeded(player, data);
            PlayerStardewDataAPI.applyStardewCraftingConditionUnlocks(player);
            backfillMine100StardropReward(player, data);
            com.stardew.craft.festival.desert.DesertFestivalService.cleanupExpiredEggsOnLogin(player);
            StardewCraft.LOGGER.info("Player {} logged in, loaded Stardew data", player.getName().getString());

            if (PassOutService.isKnockedOut(player)) {
                // Replays either the collapse payload or the destination-load
                // handshake from the durable transaction.
                PassOutService.resumePending(player);
            } else if (PassOutService.hasPendingPassOutResult(player.getUUID())) {
                PassOutService.resumePendingOvernight(player);
            } else if (data.getHealth() <= 0) {
                // Migration for saves written by the old static-map flow: a
                // restart could forget the transaction while leaving custom
                // health at zero forever.
                data.setHealth(Math.min(10, data.getMaxHealth()));
                PlayerDataManager.get().setDirty();
            }
            
            // 同步数据到客户端
            syncPlayerData(player, data);
            // 旧存档没有性别/称呼/喜好字段：登录后单独补录，不伪造默认值。
            if (!data.isProfileComplete()
                    && com.stardew.craft.farm.FarmInstanceRegistry.get().getFarmForPlayer(player.getUUID()) != null) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                        new com.stardew.craft.network.payload.OpenPlayerProfileSetupPayload());
            }
            CosmeticAppearanceSync.syncAllTo(player);
            CosmeticAppearanceSync.broadcast(player, data);
            JoinAnnouncementService.schedule(player);

            try {
                com.stardew.craft.network.overnight.OvernightSettlementPayload pendingShipping =
                    com.stardew.craft.network.overnight.OvernightSettlementTracker.consumePayload(player);
                com.stardew.craft.player.PlayerStardewDataAPI.recordOvernightShippedItems(player, pendingShipping.shippedItems());
            } catch (Exception ex) {
                StardewCraft.LOGGER.warn("Failed to settle pending offline shipping on login: {}", ex.getMessage());
            }

            com.stardew.craft.network.overnight.OvernightSettlementPayload pendingOvernight =
                    com.stardew.craft.network.overnight.OvernightSettlementTracker
                            .peekPendingSettlement(player);
            if (pendingOvernight != null) {
                com.stardew.craft.cutscene.server.WakeUpEventScheduler
                        .enqueueAtNightSettlement(player);
                com.stardew.craft.time.StardewTimePauseService
                        .beginOvernightSettlement(player);
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        player, pendingOvernight);
            }

            // 同步星露谷时间到客户端。原本 TimeSyncPacket 只在切维度/睡觉时发，
            // 如果玩家上次下线时就在星露谷维度，不会触发 PlayerChangedDimensionEvent，
            // 客户端时间缓存会停留在默认 day1/spring/year1，导致 days_played / season
            // 等剧情前置在真实进度很深的老存档上评估失败。
            {
                com.stardew.craft.time.StardewTimeManager tmForSync = com.stardew.craft.time.StardewTimeManager.get();
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                    player,
                    com.stardew.craft.network.TimeSyncPacket.fromTimeManager(tmForSync));
            }

            // Re-sync active festival client state after login. HUD/music are client-local,
            // so persistent participation tags alone are not enough after reconnect.
            com.stardew.craft.festival.ActiveFestivalHandlers.onPlayerLogin(player);
            com.stardew.craft.api.v1.internal.festival
                    .StardewFestivalSessionSyncService.syncToPlayer(player);

            // 同步社区中心 bundle 数据到客户端 (星盘渲染等需要)
            com.stardew.craft.communitycenter.network.BundleSyncPayload.sendFullSync(player);

            // 同步淘金点 — 否则玩家上次下线时已生成的点位重新登录后看不见，
            // 必须等下一次 10-min tick 重新生成才能看到。
            try {
                if (player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                    com.stardew.craft.communitycenter.reward.panning.OrePanPointManager
                            .get(sl).syncToClient(player);
                }
            } catch (Exception ex) {
                StardewCraft.LOGGER.warn("Failed to push initial ore-pan point on login: {}", ex.getMessage());
            }

            // 同步气泡（fish splash points）
            try {
                if (player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                    com.stardew.craft.fishing.splash.FishSplashState fs =
                            com.stardew.craft.fishing.splash.FishSplashState.getStardewState(sl);
                    if (fs != null) fs.sendFullSnapshot(player);
                }
            } catch (Exception ex) {
                StardewCraft.LOGGER.warn("Failed to push initial fish splash points on login: {}", ex.getMessage());
            }

            // 同步 NPC 好感度概览到客户端 — 否则 EventTriggerChecker 因为
            // NpcFriendshipClientCache.isSynced()==false 永远跑不起来，
            // 玩家进入触发区域的剧情（lewis_cc_tour / willy_fishing_rod /
            // marlon_mine_intro 等）会被无声卡住直到玩家手动打开社交菜单。
            try {
                com.stardew.craft.network.payload.RequestNpcFriendshipOverviewPayload.sendOverviewTo(player);
            } catch (Exception ex) {
                StardewCraft.LOGGER.warn("Failed to push initial NPC friendship overview on login: {}", ex.getMessage());
            }

            try {
                com.stardew.craft.npc.runtime.NpcFriendshipRewardService.applyAllEligibleRewards(player);
            } catch (Exception ex) {
                StardewCraft.LOGGER.warn("Failed to apply NPC friendship rewards on login: {}", ex.getMessage());
            }

            // 同步任务日志到客户端
            com.stardew.craft.quest.QuestManager qm = data.getQuestManager();
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                com.stardew.craft.quest.network.QuestLogSyncPayload.fromQuests(
                    qm.getQuestLog(), qm.getBillboardQuestsDone(), qm.getDailyQuestCompletedDays()));
            com.stardew.craft.specialorder.SpecialOrderManager.syncState(player);

            // 如果玩家登录时已在星露谷维度，复用维度进入时的分帧初始化队列。
            // （PlayerChangedDimensionEvent 在这种情况下不会触发）
            if (player.serverLevel().dimension() == com.stardew.craft.core.ModDimensions.STARDEW_VALLEY) {
                com.stardew.craft.farm.FarmChunkManager.get().reconcilePlayerOccupancy(player);
                com.stardew.craft.event.DimensionEventHandler.scheduleDeferredInit(player.serverLevel());
            }

            // 多人农场：离线追赶——批量推进离线期间的作物/树苗生长
            {
                net.minecraft.server.level.ServerLevel stardewLevel =
                        player.server.getLevel(com.stardew.craft.core.ModDimensions.STARDEW_VALLEY);
                if (stardewLevel != null) {
                    com.stardew.craft.farm.OfflineFarmCatchUp.catchUp(stardewLevel, player.getUUID());
                    // 老存档/老服务器兼容：补放农场洞穴（早于洞穴系统的存档 cavePlaced=false）
                    com.stardew.craft.farm.FarmInstance ownFarm =
                            com.stardew.craft.farm.FarmInstanceRegistry.get().getFarmForPlayer(player.getUUID());
                    if (ownFarm != null) {
                        com.stardew.craft.farm.FarmInstanceInitializer.backfillFarmCaveIfMissing(stardewLevel, ownFarm);
                        com.stardew.craft.api.v1.farm.StardewFarmLayoutMigrations.runPending(
                                stardewLevel, ownFarm.getOwnerUUID());
                        com.stardew.craft.api.v1.farm.StardewFarmInitializationSteps.runPending(
                                stardewLevel, ownFarm.getOwnerUUID());
                    }
                }
            }

            // 首次登录/每次登录时触发 fireDayStarted 以补偿新存档第1天没有过夜结算的情况
            // （advanceDay 只在过夜时调用，新存档春1没有过夜，quest trigger 不会触发）
            com.stardew.craft.time.StardewTimeManager tm = com.stardew.craft.time.StardewTimeManager.get();
            int absDay = (tm.getCurrentYear() - 1) * 112 + tm.getCurrentSeason() * 28 + tm.getCurrentDay();
            // 立即初始化 firstJoinDay，避免第 1 天没过夜时它仍为 -1，导致首次 advanceDay
            // 把"加入日"误记为"加入日+1"，使所有按 personalDay 触发的信件晚 1 天送达。
            com.stardew.craft.player.PlayerStardewData pData =
                    com.stardew.craft.player.PlayerDataManager.getPlayerData(player);
            if (pData.getFirstJoinDay() < 0) {
                // 与 scheduleMailByDate 中的公式保持一致（currentDay 为 1-based，不减 1）
                int globalDays = (tm.getCurrentYear() - 1) * (28 * 4)
                        + tm.getCurrentSeason() * 28 + tm.getCurrentDay();
                pData.setFirstJoinDay(globalDays);
            }

            // 离线跨日后，登录时需要先 flush 已排队到“明天”的邮件，
            // 再补跑当天日期邮件调度；否则成员/离线玩家会漏掉个人信件，
            // 进一步卡住依赖邮件的个人剧情与触发。
            com.stardew.craft.mail.MailService.flushOnLogin(player);
            tm.syncDateTriggeredMailOnLogin(player);

            com.stardew.craft.quest.StardewQuestEvents.fireDayStarted(player, absDay);
        }
    }

    private static void handlePregenRelocationIfNeeded(ServerPlayer player, PlayerStardewData data) {
        int requiredVersion = com.stardew.craft.dimension.StardewValleyPrebuiltRegionInstaller
            .getRequiredRelocationVersion(player.server);
        if (requiredVersion <= 0 || data.getHandledPregenRelocationVersion() >= requiredVersion) {
            return;
        }

        net.minecraft.server.level.ServerLevel stardewLevel =
            player.server.getLevel(com.stardew.craft.core.ModDimensions.STARDEW_VALLEY);
        if (stardewLevel == null) {
            StardewCraft.LOGGER.warn("[VALLEY_PREGEN] Cannot relocate {} for pregen version {}: Stardew level missing",
                player.getName().getString(), requiredVersion);
            return;
        }

        com.stardew.craft.farm.FarmInstance farm =
            com.stardew.craft.farm.FarmInstanceRegistry.get().getFarmForPlayer(player.getUUID());
        if (farm == null) {
            StardewCraft.LOGGER.warn("[VALLEY_PREGEN] Cannot relocate {} for pregen version {}: no farm yet",
                player.getName().getString(), requiredVersion);
            return;
        }

        net.minecraft.core.BlockPos spawn = farm.getSpawnPoint();
        player.closeContainer();
        player.stopUsingItem();
        com.stardew.craft.warp.ModTeleport.to(player, stardewLevel, spawn, farm.getSpawnYaw(), 0.0F);
        data.setHandledPregenRelocationVersion(requiredVersion);
        PlayerDataManager.get().savePlayerData(player.getUUID(), data);
        StardewCraft.LOGGER.info("[VALLEY_PREGEN] Relocated {} to farm spawn after pregen upgrade version {}",
            player.getName().getString(), requiredVersion);
    }

    private static void backfillMine100StardropReward(ServerPlayer player, PlayerStardewData data) {
        if (data.isMine100StardropCompensationProcessed()) {
            return;
        }

        MiningPlayerData miningData = MiningDataManager.getPlayerData(player);
        if (miningData == null || miningData.getMaxFloorReached() < 100) {
            data.setMine100StardropCompensationProcessed(true);
            return;
        }

        ItemStack reward = new ItemStack(ModItems.STARDROP.get());
        if (!player.getInventory().add(reward)) {
            player.drop(reward, false);
        }
        MineRewardClaimManager.get(player.serverLevel()).markClaimed(player.getUUID(), 100);
        data.setMine100StardropCompensationProcessed(true);
        player.sendSystemMessage(Component.translatable("stardewcraft.migration.floor_100_stardrop"));
        StardewCraft.LOGGER.info("Backfilled mine floor 100 Stardrop reward for {}", player.getGameProfile().getName());
    }
    
    /**
     * 玩家退出时保存数据
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            com.stardew.craft.farm.FarmChunkManager.get()
                    .onPlayerLogout(player.serverLevel(), player);
            // Settle cursor-held shop purchases before the player inventory is saved.
            com.stardew.craft.network.payload.ShopPickupPayload.deliverAllPending(player);

            // 清理动态光源
            PlayerGlowHandler.onPlayerLeave(player);

            // Clean up any pending geode treasure (prevents memory leak + item loss)
            com.stardew.craft.shop.GeodeLootService.onPlayerLogout(player);

            // Clean up all combat tracker static maps (prevents memory leak)
            cleanupTransientCombat(player);

            // Clean up cave transition and per-player locked-area rollback state.
            com.stardew.craft.manager.WitchWarpCaveService.onPlayerLogout(player);

            // Clean up active trinket companions/state.
            com.stardew.craft.item.trinket.TrinketEffectHandler.onPlayerLogout(player);

            // Clean up tree chopping state
            com.stardew.craft.event.WildTreeChopEvents.removePlayer(player.getUUID());

            // Clean up fishing session
            com.stardew.craft.fishing.server.FishingSessionManager.onPlayerLogout(player);

            // Clean up casino sessions and refund an uncollected slot-machine wager.
            com.stardew.craft.casino.CasinoService.onPlayerLogout(player);

            // Clean up E112 wizard cutscene state (remove per-player Junimo + timer)
            com.stardew.craft.interior.WizardQuestHandler.onPlayerLogout(player);

            // Release any NPC dialogue movement lock owned by this player.
            com.stardew.craft.npc.runtime.NpcInteractionService.onPlayerLogout(player);

            // Let active festivals clear only per-connection state; durable same-day choices
            // such as Flower Dance partners must survive reconnect.
            com.stardew.craft.festival.ActiveFestivalHandlers.onPlayerLogout(player);

            // 睡眠投票：玩家登出后如果剩余人全部已投票，推进日期
            if (com.stardew.craft.event.SleepVoteTracker.hasAnyVotes()) {
                if (com.stardew.craft.event.SleepVoteTracker.onPlayerLogout(player)) {
                    net.minecraft.server.level.ServerLevel stardewLevel =
                            player.server.getLevel(com.stardew.craft.core.ModDimensions.STARDEW_VALLEY);
                    if (stardewLevel != null) {
                        int sleepMinute = com.stardew.craft.event.SleepVoteTracker.getLatestSleepMinute();
                        com.stardew.craft.event.SleepVoteTracker.clearVotes();
                        com.stardew.craft.event.DimensionEventHandler.triggerAdvance(stardewLevel, sleepMinute, "sleep_vote_logout");
                    }
                }
            }

            PlayerStardewData data = PlayerDataManager.getPlayerData(player);
            if (data.isDirty()) {
                data.markClean();
                PlayerDataManager.get().setDirty();
                StardewCraft.LOGGER.info("Player {} logged out, saved Stardew data", player.getName().getString());
            }
        }
    }
    
    /**
     * 玩家死亡时的处理
     */
    @SubscribeEvent
    public static void onPlayerDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // 星露谷维度：不走 MC 原版死亡（后续要接“晕倒/结算”流程）。
            if (player.level().dimension() == ModDimensions.STARDEW_VALLEY
                || player.level().dimension() == ModMiningDimensions.STARDEW_MINING) {
                event.setCanceled(true);
                if (PassOutService.isInCombatDeathRecovery(player)) {
                    PassOutService.restoreDuringCombatDeathRecovery(player);
                    return;
                }
                // 击倒状态下不再重复处理
                if (PassOutService.isKnockedOut(player)) {
                    player.setHealth(player.getMaxHealth());
                    return;
                }
                // 兜底触发：若死因绕过了 onPlayerHurt（例如虚空、/kill），
                // 由 PassOutService 的防重入机制保证不会和 onPlayerHurt 重复执行。
                cleanupTransientCombat(player);
                StardewDamageHooks.onHealthDepleted(player, event.getSource());
                // 保险：防止已进入 dying 状态
                player.setHealth(player.getMaxHealth());
                player.getFoodData().setFoodLevel(20);
                player.getFoodData().setSaturation(5.0f);
                return;
            }

            cleanupTransientCombat(player);
            PlayerStardewData data = PlayerDataManager.getPlayerData(player);
            
            // 星露谷死亡机制：损失10%金币（最多1000）
            int moneyLoss = Math.min(PlayerStardewDataAPI.getMoney(player) / 10, 1000);
            if (moneyLoss > 0) {
                PlayerStardewDataAPI.removeMoney(player, moneyLoss);
            }
            
            // 重置生命值为满
            data.setHealth(data.getMaxHealth());
            
            // TODO: 可能还需要掉落一些物品
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer original) {
            cleanupTransientCombat(original);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            cleanupTransientCombat(player);
        }
    }

    /**
     * 星露谷维度：拦截原版受伤，并映射到星露谷生命值。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerHurt(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (player.isCreative() || player.isSpectator()) {
            return;
        }

        float rawAmount = event.getAmount();
        if (rawAmount <= 0.0f) {
            return;
        }
        float amount = rawAmount;

        if (com.stardew.craft.item.trinket.TrinketEffectHandler.cancelBasiliskDamage(player, event.getSource())) {
            event.setAmount(0.0f);
            return;
        }

        @SuppressWarnings("null")
        MobEffectInstance shelter = player.getEffect(ModMobEffects.SHELTER);
        float shelterMultiplier = 1.0f;
        if (shelter != null) {
            shelterMultiplier = ModMobEffects.shelterDamageMultiplier(shelter.getAmplifier());
            amount *= shelterMultiplier;
        }

        if (player.level().dimension() != ModDimensions.STARDEW_VALLEY
            && player.level().dimension() != ModMiningDimensions.STARDEW_MINING) {
            if (com.stardew.craft.combat.equipment.CrossDimensionCombatHandler
                    .tryBlockIncoming(player, event)) {
                return;
            }
            com.stardew.craft.item.trinket.TrinketEffectHandler.onReceiveDamage(player,
                    Math.max(1, (int) Math.ceil(amount * com.stardew.craft.combat.DimensionDamageMapper.getHealthRatio())));
            event.setAmount(amount);
            return;
        }

        if (PassOutService.isInCombatDeathRecovery(player)) {
            event.setAmount(0.0f);
            PassOutService.restoreDuringCombatDeathRecovery(player);
            return;
        }

        // 击倒状态：完全免疫所有伤害（等待传送中）
        if (PassOutService.isKnockedOut(player)) {
            event.setAmount(0.0f);
            return;
        }

        // 取消 MC 原版扣血（我们用星露谷血条承载伤害）。
        event.setAmount(0.0f);

        // 虚空坠落：直接触发击倒，无需慢慢扣血。
        // 原版虚空伤害有 bypasses_invulnerability 标签每 tick 命中，
        // 但我们的无敌帧逻辑会错误地阻挡它，导致死亡极慢。
        if (event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)) {
            cleanupTransientCombat(player);
            StardewDamageHooks.onHealthDepleted(player, event.getSource());
            player.setHealth(player.getMaxHealth());
            player.getFoodData().setFoodLevel(20);
            player.getFoodData().setSaturation(5.0f);
            return;
        }

        // 原版伤害被设为 0 后不会建立受击无敌帧，由星露谷战斗层统一维护。
        if (player.invulnerableTime > 0) {
            player.setHealth(player.getMaxHealth());
            return;
        }

        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        long nowTick = player.level().getGameTime();
        if (YobaProtectionState.isActive(player, nowTick)) {
            player.setHealth(player.getMaxHealth());
            player.getFoodData().setFoodLevel(20);
            player.getFoodData().setSaturation(5.0f);
            return;
        }
        int sdMax = Math.max(1, data.getMaxHealth());
        float mcMax = Math.max(1.0f, player.getMaxHealth());

        net.minecraft.world.entity.Entity dmgSourceEntity = event.getSource().getEntity();
        DamageRequest.SourceKind sourceKind;
        if (dmgSourceEntity instanceof net.minecraft.world.entity.Mob) {
            sourceKind = DamageRequest.SourceKind.MONSTER_ATTACK;
        } else if (dmgSourceEntity != null) {
            sourceKind = DamageRequest.SourceKind.DIRECT_ENTITY;
        } else {
            sourceKind = DamageRequest.SourceKind.ENVIRONMENT;
        }

        float authoritativeMonsterDamage =
                dmgSourceEntity instanceof net.minecraft.world.entity.Mob sourceMob
                        ? MonsterStats.fromEntity(sourceMob).getDamage()
                        : 0.0f;
        IncomingDamageResolver.DamageRange incomingRange =
                IncomingDamageResolver.resolveRange(
                        rawAmount,
                        event.getOriginalAmount(),
                        sourceKind,
                        authoritativeMonsterDamage,
                        sdMax,
                        mcMax
                );
        DamageRequest.Builder incomingDamage = DamageRequest.builder(
                        "incoming:" + event.getSource().getMsgId())
                .sourceKind(sourceKind)
                .skillId("incoming")
                .baseDamage(incomingRange.minimum(), incomingRange.maximum())
                .critical(0.0f, 1.0f, false)
                .variance(1.0f, 1.0f)
                .minimumFinalDamage(1.0f)
                .accuracy(0.0f, 0.0f)
                .inStardewDimension(true);
        if (shelterMultiplier != 1.0f) {
            incomingDamage.addPreDefenseAdjustment(
                    DamageAdjustment.multiply("shelter", shelterMultiplier)
            );
        }

        float afterShelter = incomingRange.maximum() * shelterMultiplier;
        if (event.getSource().is(net.minecraft.world.damagesource.DamageTypes.EXPLOSION)
                || event.getSource().is(net.minecraft.world.damagesource.DamageTypes.PLAYER_EXPLOSION)) {
            float reducedBombDamage = BookPowerEffects.applyBombDamageReduction(data, afterShelter);
            float bombMultiplier = afterShelter > 0.0f ? reducedBombDamage / afterShelter : 1.0f;
            incomingDamage.addPreDefenseAdjustment(
                    DamageAdjustment.multiply("book_bomb_resistance", bombMultiplier)
            );
        }

        // 武器防御（握持生效）
        WeaponStats weaponStats = WeaponStats.fromItemStack(player.getMainHandItem());
        float weaponDefense = weaponStats.getDefense();
        float foodDefense = data.getTempDefenseBonus();
        float bookDefense = BookPowerEffects.getDefenseBonus(data);
        // 装备防御（戒指+靴子）
        com.stardew.craft.combat.equipment.EquipmentStats eqStats = com.stardew.craft.combat.equipment.EquipmentResolver.getMergedStats(player);
        float equipDefense = eqStats.getDefense();
        float totalDefense = weaponDefense + foodDefense + equipDefense + bookDefense;
        incomingDamage
                .defense(totalDefense, false)
                .defenseRule(DamageRequest.DefenseRule.STARDEW_PLAYER_DEFENSE);
        if (dmgSourceEntity != null
                && player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel
                && com.stardew.craft.festival.desert.DesertFestivalMineService.isInFestivalSkullCavern(player)) {
            incomingDamage.addPreDefenseAdjustment(DamageAdjustment.multiply(
                    "desert_festival_difficulty",
                    com.stardew.craft.festival.desert.DesertFestivalMineService.monsterDamageMultiplier(serverLevel)
            ));
        }

        // ── 戒指被动效果 ──

        // 史莱姆克星戒指：免疫史莱姆伤害
        net.minecraft.world.entity.Entity sourceEntity = event.getSource().getEntity();
        if (eqStats.hasSlimeCharmer() && sourceEntity != null) {
            net.minecraft.resources.ResourceLocation entityTypeId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                    .getKey(sourceEntity.getType());
            String entityType = entityTypeId.toString();
            if (entityType.contains("slime") || entityType.contains("green_slime")
                    || entityType.contains("frost_jelly") || entityType.contains("sludge")) {
                player.setHealth(player.getMaxHealth());
                player.getFoodData().setFoodLevel(20);
                player.getFoodData().setSaturation(5.0f);
                return; // 完全免疫史莱姆伤害
            }
        }

        // 约巴之戒：概率随当前生命降低而上升，触发后获得 5 秒完全保护。
        if (eqStats.hasYobaProtection()) {
            float luckLevel = PlayerStardewDataAPI.getLuckBuffLevel(player);
            float protectionChance = CombatRingRules.yobaProtectionChance(data.getHealth(), luckLevel);
            if (player.getRandom().nextFloat() < protectionChance) {
                YobaProtectionState.start(player, nowTick);
                player.playNotifySound(
                        com.stardew.craft.sound.ModSounds.YOBA.get(),
                        net.minecraft.sounds.SoundSource.PLAYERS,
                        1.0f,
                        1.0f
                );
                player.setHealth(player.getMaxHealth());
                player.getFoodData().setFoodLevel(20);
                player.getFoodData().setSaturation(5.0f);
                return;
            }
        }

        DamageOutcome incomingOutcome = DamagePipeline.evaluate(incomingDamage.build());
        CombatDamageHistory.record(player, player.level().getGameTime(), incomingOutcome);
        float sdDamageFloat = incomingOutcome.getFinalDamage();
        int sdDamage = (int) Math.ceil(sdDamageFloat);
        if (sdDamage < 1) {
            sdDamage = 1;
        }
        com.stardew.craft.item.trinket.TrinketEffectHandler.onReceiveDamage(player, sdDamage);

        // 钢脊之怒：4秒内首次受击充能
        com.stardew.craft.combat.skill.SteelSpineFuryState.onDamageTaken(player, nowTick, sdDamage);
        // 矮人剑：堡垒态受击触发地脉震波
        com.stardew.craft.combat.skill.DwarfFortressTracker.onDamageTaken(player, nowTick);

        // 荆棘戒指：受伤时反射伤害给攻击者
        if (eqStats.hasThorns() && sourceEntity instanceof net.minecraft.world.entity.Mob attacker) {
            int damageBeforeDefense = (int) Math.ceil(
                    incomingOutcome.getStages().stream()
                            .filter(stage -> stage.phase() == DamageOutcome.Phase.DEFENSE)
                            .findFirst()
                            .map(DamageOutcome.Stage::before)
                            .orElse(sdDamageFloat)
            );
            int reflectedDamage = CombatRingRules.thornsDamage(
                    damageBeforeDefense,
                    sdDamage,
                    eqStats.getThornsCount()
            );
            net.minecraft.world.damagesource.DamageSource thornsDmg = player.damageSources().thorns(player);
            attacker.hurt(thornsDmg, reflectedDamage);
        }

        int oldSdHealth = data.getHealth();
        int newSdHealth = Math.max(0, oldSdHealth - sdDamage);
        data.setHealth(newSdHealth);

        // 凤凰戒指：生命值归零时复活（每天一次）
        if (newSdHealth == 0 && eqStats.hasPhoenix()) {
            long lastPhoenixDay = data.getLastPhoenixReviveDay();
            long currentDay = com.stardew.craft.time.StardewTimeManager.get().getAbsoluteDay();
            if (lastPhoenixDay != currentDay) {
                data.setLastPhoenixReviveDay(currentDay);
                int reviveHealth = CombatRingRules.phoenixReviveHealth(
                        data.getMaxHealth(),
                        eqStats.getPhoenixCount()
                );
                data.setHealth(reviveHealth);
                syncPlayerVitals(player, data);
                player.invulnerableTime = Math.max(
                        player.invulnerableTime,
                        CombatRingRules.invulnerabilityTicks(eqStats.getProtectionCount())
                );
                player.setHealth(player.getMaxHealth());
                player.getFoodData().setFoodLevel(20);
                player.getFoodData().setSaturation(5.0f);
                return; // 复活成功，跳过晕倒
            }
        }

        syncPlayerVitals(player, data);

        // 生命值清零：不要死，走接口（后续接"晕倒"等）。
        if (newSdHealth == 0) {
            DamageSource source = event.getSource();
            cleanupTransientCombat(player);
            StardewDamageHooks.onHealthDepleted(player, source);
        }

        // setAmount(0) 不会触发 MC 无敌帧。保护戒指延长的是受击后的无敌时间，
        // 不是随机减伤；每枚戒指在原版 1200ms 基础上增加 400ms。
        player.invulnerableTime = Math.max(
                player.invulnerableTime,
                CombatRingRules.invulnerabilityTicks(eqStats.getProtectionCount())
        );

        // 维持原版血/饱食度满，避免被其他机制"补刀"。
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0f);
    }

    private static void cleanupTransientCombat(ServerPlayer player) {
        com.stardew.craft.combat.CombatTrackerCleanup.onPlayerUnavailable(
                player
        );
    }

    /**
     * 星露谷维度：维持原版血量/饱食度为满值（取消原版生命/饱食机制）。
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // AFK 检测：每 20 tick（1秒）检查一次玩家是否移动/旋转
        if (player.tickCount % 20 == 0
                && com.stardew.craft.event.SleepVoteTracker.isInStardewDimension(player)) {
            updateAfkTracking(player);
        }

        com.stardew.craft.book.BookService.tickReadingFreeze(player);

        if (!player.isCreative() && !player.isSpectator() && player.isInvulnerable()) {
            player.setInvulnerable(false);
        }

        // Buff同步/过期驱动（不依赖维度）：
        // - MobEffect 负责 UI/持续时间（也支持 /effect 指令）
        // - PlayerStardewData 负责把加成落到星露谷数值体系里
        try {
            PlayerStardewData data = PlayerDataManager.getPlayerData(player);
            long now = player.level().getGameTime();
            boolean changed = false;

            @SuppressWarnings("null")
            MobEffectInstance vigorous = player.getEffect(ModMobEffects.VIGOROUS);
            if (vigorous != null) {
                int bonus = ModMobEffects.vigorousMaxEnergyBonus(vigorous.getAmplifier());
                long endTick = now + vigorous.getDuration();
                changed |= data.setTempMaxEnergyBonusDirect(bonus, endTick);
            } else if (!data.hasActiveTempMaxEnergyBonus(now)) {
                changed |= data.clearTempMaxEnergyBonus();
            }

            @SuppressWarnings("null")
            MobEffectInstance seaKing = player.getEffect(ModMobEffects.SEA_KING_BLESSING);
            if (seaKing != null) {
                int bonus = ModMobEffects.seaKingFishingLevelBonus(seaKing.getAmplifier());
                long endTick = now + seaKing.getDuration();
                changed |= data.setTempFishingLevelBonusDirect(bonus, endTick);
            } else {
                changed |= data.clearTempFishingLevelBonus();
            }

            @SuppressWarnings("null")
            MobEffectInstance spirit = player.getEffect(ModMobEffects.SPIRIT_BLESSING);
            @SuppressWarnings("null")
            MobEffectInstance statueLuck = player.getEffect(ModMobEffects.STATUE_OF_BLESSINGS_1);
            if (spirit != null || statueLuck != null) {
                int bonus = (spirit != null ? ModMobEffects.spiritLuckLevelBonus(spirit.getAmplifier()) : 0)
                          + (statueLuck != null ? 1 : 0); // SDV Buffs.json: LuckLevel=1.0
                long endTick = now + Math.max(
                    spirit != null ? spirit.getDuration() : 0L,
                    statueLuck != null ? statueLuck.getDuration() : 0L);
                changed |= data.setTempLuckBonusDirect(bonus, endTick);
            } else {
                changed |= data.clearTempLuckBonus();
            }

            @SuppressWarnings("null")
            MobEffectInstance farmerBlessing = player.getEffect(ModMobEffects.FARMER_BLESSING);
            if (farmerBlessing != null) {
                int bonus = ModMobEffects.farmerFarmingLevelBonus(farmerBlessing.getAmplifier());
                long endTick = now + farmerBlessing.getDuration();
                changed |= data.setTempFarmingLevelBonusDirect(bonus, endTick);
            } else {
                changed |= data.clearTempFarmingLevelBonus();
            }

            @SuppressWarnings("null")
            MobEffectInstance foragerBlessing = player.getEffect(ModMobEffects.FORAGER_BLESSING);
            if (foragerBlessing != null) {
                int bonus = ModMobEffects.foragerForagingLevelBonus(foragerBlessing.getAmplifier());
                long endTick = now + foragerBlessing.getDuration();
                changed |= data.setTempForagingLevelBonusDirect(bonus, endTick);
            } else {
                changed |= data.clearTempForagingLevelBonus();
            }

            @SuppressWarnings("null")
            MobEffectInstance minerBlessing = player.getEffect(ModMobEffects.MINER_BLESSING);
            if (minerBlessing != null) {
                int bonus = ModMobEffects.minerMiningLevelBonus(minerBlessing.getAmplifier());
                long endTick = now + minerBlessing.getDuration();
                changed |= data.setTempMiningLevelBonusDirect(bonus, endTick);
            } else {
                changed |= data.clearTempMiningLevelBonus();
            }

            @SuppressWarnings("null")
            MobEffectInstance warriorBlessing = player.getEffect(ModMobEffects.WARRIOR_BLESSING);
            if (warriorBlessing != null) {
                int bonus = ModMobEffects.warriorAttackBonus(warriorBlessing.getAmplifier());
                long endTick = now + warriorBlessing.getDuration();
                changed |= data.setTempAttackBonusDirect(bonus, endTick);
            } else {
                changed |= data.clearTempAttackBonus();
            }

            @SuppressWarnings("null")
            MobEffectInstance guardianBlessing = player.getEffect(ModMobEffects.GUARDIAN_BLESSING);
            if (guardianBlessing != null) {
                int bonus = ModMobEffects.guardianDefenseBonus(guardianBlessing.getAmplifier());
                long endTick = now + guardianBlessing.getDuration();
                changed |= data.setTempDefenseBonusDirect(bonus, endTick);
            } else {
                changed |= data.clearTempDefenseBonus();
            }

            @SuppressWarnings("null")
            MobEffectInstance magnetism = player.getEffect(ModMobEffects.MAGNETISM);
            if (magnetism != null) {
                int bonus = ModMobEffects.magnetismRadiusBonus(magnetism.getAmplifier());
                long endTick = now + magnetism.getDuration();
                changed |= data.setTempMagneticRadiusBonusDirect(bonus, endTick);
            } else {
                changed |= data.clearTempMagneticRadiusBonus();
            }

            // 兼容：若存在非 MobEffect 驱动的 timed buff，这里负责过期清理。
            changed |= data.tickTimedBuffs(now);
            BookPowerEffects.tickMovement(player, data);

            if (changed || data.isDirty()) {
                data.markClean();
                syncPlayerData(player, data);
            }
        } catch (Exception e) {
            StardewCraft.LOGGER.error("Error ticking player buffs", e);
        }

        long gameTime = player.level().getGameTime();
        com.stardew.craft.item.trinket.TrinketEffectHandler.tick(player);
        com.stardew.craft.mastery.PrismaticButterflyService.tickPlayer(player);
        com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime.tickPlayer(player, gameTime);
        // 刻刀：刻痕连刺
        com.stardew.craft.combat.skill.CarvingKnifeThrustTracker.tick(player, gameTime);
        // 铱针：三针连斩
        com.stardew.craft.combat.skill.IridiumNeedleThrustTracker.tick(player, gameTime);
        // 残破的三叉戟：鱼获试刺 + 鱼获状态
        com.stardew.craft.combat.skill.BrokenTridentThrustTracker.tick(player, gameTime);
        com.stardew.craft.combat.skill.BrokenTridentCatchTracker.tick(player, gameTime);
        // 水晶匕首：晶层持续
        com.stardew.craft.combat.skill.CrystalDaggerLayerTracker.tick(player, gameTime);
        // 黑曜石之刃：玄刃共鸣 + 裂界一线
        com.stardew.craft.combat.skill.ObsidianResonanceTracker.tick(player, gameTime);
        // 骨化剑：白骨行刑
        com.stardew.craft.combat.skill.OssifiedExecutionTracker.tick(player, gameTime);
        // 圣剑：晨曦圣域
        com.stardew.craft.combat.skill.HolyBladeSanctuaryTracker.tick(player, gameTime);
        // 淬火阔剑：回炉淬火延迟爆鸣 + 熔锻飞坯火环
        com.stardew.craft.combat.skill.TemperedQuenchTracker.tick(player, gameTime);
        com.stardew.craft.combat.skill.TemperedFireRingTracker.tick(player, gameTime);
        // 钢刀：疾锋刻线 / 斩迹回响
        com.stardew.craft.combat.skill.SteelFalchionLineTracker.tick(player, gameTime);
        // 黑暗剑：祭血斩 / 血月收割
        // 熔岩武士刀：熔潮回鸣
        // 矮人剑与熔岩武士刀的持续技能由 WeaponSkillRuntime 驱动。
            com.stardew.craft.combat.skill.RiftPathDamageTracker.tick(player, gameTime);

        // 温泉运行时：静止时恢复 energy/health，移动时按节奏播放水声，
        // 进出温泉播放 pullItemFromWater。维度判断在 Registry 内完成。
        com.stardew.craft.hotspring.HotSpringRuntimeService.tick(player);
        com.stardew.craft.combat.equipment.EquipmentPlayerAttributes.sync(player);
        com.stardew.craft.combat.equipment.EquipmentFireProtection.tick(player);
        applyMagneticPull(player, PlayerDataManager.getPlayerData(player));

        // 发光戒指：动态光源
        PlayerGlowHandler.tick(player);
        com.stardew.craft.manager.SecretWoodsAccessManager.tickPlayer(player);
        com.stardew.craft.manager.WitchWarpCaveService.tickPlayer(player);

        // 法师塔指南针：服务端查找最近结构
        if (player.getMainHandItem().getItem() instanceof com.stardew.craft.item.tool.WizardTowerCompassItem
            || player.getOffhandItem().getItem() instanceof com.stardew.craft.item.tool.WizardTowerCompassItem) {
            com.stardew.craft.item.tool.WizardTowerCompassItem.serverTick(player);
        }

        if (player.level().dimension() != ModDimensions.STARDEW_VALLEY
            && player.level().dimension() != ModMiningDimensions.STARDEW_MINING) {
            return;
        }

        // 创造/旁观不强制。
        if (player.isCreative() || player.isSpectator()) {
            return;
        }

        // SDV: 精疲力竭时持续给予缓慢 I 效果
        {
            PlayerStardewData tickData = PlayerDataManager.getPlayerData(player);
            if (tickData.getEnergy() <= -15.0F) {
                PassOutService.onExhaustionPassOut(player);
            }
            if (tickData.isExhausted()) {
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 60, 0, false, false, true));
            }
        }

        if (player.getHealth() < player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }

        var food = player.getFoodData();
        if (food.getFoodLevel() != 20) {
            food.setFoodLevel(20);
        }
        if (food.getSaturationLevel() < 5.0f) {
            food.setSaturation(5.0f);
        }
    }
    
    /**
     * 服务器tick - 定时保存数据
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        
        if (tickCounter >= AUTO_SAVE_INTERVAL) {
            tickCounter = 0;
            
            try {
                PlayerDataManager manager = PlayerDataManager.get();
                manager.tickAndSaveDirty();
            } catch (Exception e) {
                StardewCraft.LOGGER.error("Error during player data auto-save", e);
            }
        }

        // 多人睡眠等待时缓慢恢复体力
        if (event.getServer() != null) {
            com.stardew.craft.event.SleepVoteTracker.tickSleepEnergyRegen(event.getServer());
        }
    }
    
    /**
     * 服务器关闭时强制保存所有数据
     */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        try {
            PlayerDataManager manager = PlayerDataManager.get();
            manager.setDirty();
            StardewCraft.LOGGER.info("Server stopping, saved all player data");
        } catch (Exception e) {
            StardewCraft.LOGGER.error("Error saving player data on server stop", e);
        }

        ServerLevel stardewLevel = event.getServer().getLevel(ModDimensions.STARDEW_VALLEY);
        try {
            com.stardew.craft.farm.FarmChunkManager.get().onServerStopping(stardewLevel);
        } catch (RuntimeException exception) {
            StardewCraft.LOGGER.error("Error releasing temporary farm chunks on server stop", exception);
        }

        // 释放服务端静态缓存，防止内存泄漏
        com.stardew.craft.combat.equipment.EquipmentFireProtection.clearAll();
        com.stardew.craft.interior.InteriorSubspaceManager.clearPortalRegistry();
        com.stardew.craft.block.shape.ModelVoxelShapeCache.clearAll();
        com.stardew.craft.npc.data.NpcContentFilter.clearCache();
        com.stardew.craft.casino.CasinoService.clearSessions();
    }
    
    /**
     * 同步玩家数据到客户端
     */
    @SuppressWarnings("null")
    public static void syncPlayerData(ServerPlayer player, PlayerStardewData data) {
        data.setMoney(com.stardew.craft.money.SharedMoneyService.getMoney(player));
        PlayerDataSyncPacket packet = PlayerDataSyncPacket.fromPlayerData(data);
        // Inject farm name into sync NBT so client can resolve %farm placeholder
        com.stardew.craft.farm.FarmInstanceRegistry farmRegistry = com.stardew.craft.farm.FarmInstanceRegistry.get();
        com.stardew.craft.farm.FarmInstance farm = farmRegistry.getFarmForPlayer(player.getUUID());
        packet.data().putBoolean("HasFarm", farm != null);
        if (farm != null && farm.getFarmName() != null) {
            packet.data().putString("FarmName", farm.getFarmName());
        } else {
            packet.data().remove("FarmName");
        }
        java.util.UUID farmOwner = farmRegistry.getOwnerForPlayer(player.getUUID());
        if (farmOwner != null) {
            packet.data().putUUID("FarmOwnerUUID", farmOwner);
        } else {
            packet.data().remove("FarmOwnerUUID");
        }
        com.stardew.craft.mining.MiningPlayerData miningData = com.stardew.craft.mining.MiningDataManager.getPlayerData(player);
        packet.data().putInt("MaxMineFloorReached", miningData != null ? miningData.getMaxFloorReached() : 0);
        packet.data().putString("WinterStarRecipient",
            com.stardew.craft.festival.WinterStarFestivalService.getSecretFriendId(player));
        PacketDistributor.sendToPlayer(player, packet);
        // sync equipment slots
        PacketDistributor.sendToPlayer(player, new com.stardew.craft.network.payload.EquipmentSyncPayload(
                data.getEquippedLeftRingStack(),
                data.getEquippedRightRingStack(),
                data.getEquippedBootsStack(),
                data.getEquippedTrinket(),
                data.getEquippedHat(),
                data.getEquippedShirt(),
                data.getEquippedPants()
        ));
    }

    /**
     * Combat hot path: synchronize only the values needed to redraw the health
     * bar. Persistent/full state remains covered by the normal dirty-data sync.
     */
    public static void syncPlayerVitals(ServerPlayer player, PlayerStardewData data) {
        PacketDistributor.sendToPlayer(
                player,
                new com.stardew.craft.network.payload.PlayerVitalsSyncPayload(
                        data.getHealth(),
                        data.getMaxHealth()
                )
        );
    }

    /**
     * 吸附掉落物：远处快速拉近，靠近后直接尝试放入玩家背包。
     */
    @SuppressWarnings("null")
    private static void applyMagneticPull(ServerPlayer player, PlayerStardewData data) {
        int radiusBonus = data.getTempMagneticRadiusBonus();
        // Add equipment magnetic radius (rings/boots).
        com.stardew.craft.combat.equipment.EquipmentStats eqStats = com.stardew.craft.combat.equipment.EquipmentResolver.getMergedStats(player);
        radiusBonus += eqStats.getMagneticRadius();
        if (radiusBonus <= 0) {
            return;
        }
        if (player.isSpectator()) {
            return;
        }

        // Radius is configured directly in blocks (e.g. +3 => pull items within 3 blocks).
        double radius = Math.max(1.0, radiusBonus);
        AABB playerBox = player.getBoundingBox();
        AABB range = playerBox.inflate(radius, Math.max(2.0, radius * 0.65), radius);
        Vec3 target = player.position().add(0.0, 0.45, 0.0);

        for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class, range, ItemEntity::isAlive)) {
            if (!canMagnetAffectItem(player, item)) {
                continue;
            }

            Vec3 itemPos = item.position().add(0.0, 0.1, 0.0);
            Vec3 delta = target.subtract(itemPos);
            double dist = delta.length();
            if (dist < 0.05 || dist > radius) {
                continue;
            }

            if (dist <= MAGNET_DIRECT_PICKUP_DISTANCE && tryPickupMagneticItem(player, item)) {
                continue;
            }

            Vec3 dir = delta.scale(1.0 / dist);
            double t = 1.0 - (dist / radius);
            double accel = MAGNET_BASE_ACCELERATION + t * MAGNET_NEAR_ACCELERATION;
            Vec3 pull = dir.scale(accel);

            Vec3 nextMotion = item.getDeltaMovement().scale(0.55).add(pull);
            if (nextMotion.lengthSqr() > MAGNET_MAX_SPEED * MAGNET_MAX_SPEED) {
                nextMotion = nextMotion.normalize().scale(MAGNET_MAX_SPEED);
            }

            item.setPickUpDelay(0);
            item.setDeltaMovement(nextMotion);
            item.hasImpulse = true;
            item.hurtMarked = true;
        }
    }

    private static boolean canMagnetAffectItem(ServerPlayer player, ItemEntity item) {
        if (item.getItem().isEmpty()) {
            return false;
        }
        Entity owner = item.getOwner();
        return !(owner instanceof ServerPlayer ownerPlayer) || ownerPlayer.getUUID().equals(player.getUUID());
    }

    private static boolean tryPickupMagneticItem(ServerPlayer player, ItemEntity item) {
        ItemStack stack = item.getItem();
        if (stack.isEmpty()) {
            return false;
        }

        int originalCount = stack.getCount();
        item.setPickUpDelay(0);
        item.playerTouch(player);
        return !item.isAlive() || item.getItem().isEmpty() || item.getItem().getCount() < originalCount;
    }

    // ═══════════════════════════════════════════════════════════
    // AFK 检测（用于睡眠投票排除挂机玩家）
    // ═══════════════════════════════════════════════════════════

    /** 上次检测时的 (x,y,z,yRot,xRot) 快照，用 persistentData 存储避免额外 Map */
    private static final String TAG_AFK_X = "stardewcraft_afk_x";
    private static final String TAG_AFK_Z = "stardewcraft_afk_z";
    private static final String TAG_AFK_YROT = "stardewcraft_afk_yrot";

    private static void updateAfkTracking(ServerPlayer player) {
        var data = player.getPersistentData();
        double prevX = data.getDouble(TAG_AFK_X);
        double prevZ = data.getDouble(TAG_AFK_Z);
        float prevYRot = data.getFloat(TAG_AFK_YROT);

        double curX = player.getX();
        double curZ = player.getZ();
        float curYRot = player.getYRot();

        // 检测是否有实质性移动或转向
        boolean moved = Math.abs(curX - prevX) > 0.05
                || Math.abs(curZ - prevZ) > 0.05
                || Math.abs(curYRot - prevYRot) > 1.0f;

        if (moved) {
            com.stardew.craft.event.SleepVoteTracker.markActive(player);
        }

        data.putDouble(TAG_AFK_X, curX);
        data.putDouble(TAG_AFK_Z, curZ);
        data.putFloat(TAG_AFK_YROT, curYRot);
    }
}

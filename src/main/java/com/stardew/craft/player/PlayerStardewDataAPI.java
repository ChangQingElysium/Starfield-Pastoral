package com.stardew.craft.player;

import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.mining.MiningDataManager;
import com.stardew.craft.network.overnight.OvernightSettlementPayload;
import com.stardew.craft.network.payload.SkillExperienceGainPayload;
import com.stardew.craft.deco.DecorationType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * 玩家星露谷数据 API
 * 提供便捷的静态方法来访问和操作玩家数据
 */
@SuppressWarnings("null")
public class PlayerStardewDataAPI {

    /**
     * lucky 命令允许设置的范围（包含 Spirit Altar 的 ±0.12）
     */
    public static final double DAILY_LUCK_MIN = -0.12D;
    public static final double DAILY_LUCK_MAX = 0.12D;
    
    // ============ 数据获取 ============
    
    /**
     * 获取玩家数据
     */
    public static PlayerStardewData getData(ServerPlayer player) {
        return PlayerDataManager.getPlayerData(player);
    }

    // ============ 通用统计（SDV Stats parity）============

    public static int getStat(ServerPlayer player, String key) {
        return getData(player).getStat(key);
    }

    public static boolean setStat(ServerPlayer player, String key, int value) {
        PlayerStardewData data = getData(player);
        boolean changed = data.setStat(key, value);
        if (changed) {
            PlayerDataEventHandler.syncPlayerData(player, data);
        }
        return changed;
    }

    public static int incrementStat(ServerPlayer player, String key) {
        return incrementStat(player, key, 1);
    }

    public static int incrementStat(ServerPlayer player, String key, int amount) {
        PlayerStardewData data = getData(player);
        int next = data.incrementStat(key, amount);
        if (amount > 0) {
            PlayerDataEventHandler.syncPlayerData(player, data);
        }
        return next;
    }

    public static int incrementStat(
            UUID playerId,
            String key,
            int amount
    ) {
        if (playerId == null) {
            return 0;
        }
        return PlayerDataManager.getPlayerData(
                playerId).incrementStat(key, amount);
    }

    public static int getTicketPrizesClaimed(ServerPlayer player) {
        return getData(player).getTicketPrizesClaimed();
    }

    public static boolean setTicketPrizesClaimed(ServerPlayer player, int value) {
        PlayerStardewData data = getData(player);
        boolean changed = data.setTicketPrizesClaimed(value);
        if (changed) {
            PlayerDataEventHandler.syncPlayerData(player, data);
        }
        return changed;
    }

    public static int incrementTicketPrizesClaimed(ServerPlayer player) {
        PlayerStardewData data = getData(player);
        int next = data.incrementTicketPrizesClaimed();
        PlayerDataEventHandler.syncPlayerData(player, data);
        return next;
    }

    public static int getSpecialOrderPrizeTickets(ServerPlayer player) {
        return getData(player).getSpecialOrderPrizeTickets();
    }

    public static boolean setSpecialOrderPrizeTickets(ServerPlayer player, int value) {
        PlayerStardewData data = getData(player);
        boolean changed = data.setSpecialOrderPrizeTickets(value);
        if (changed) {
            PlayerDataEventHandler.syncPlayerData(player, data);
        }
        return changed;
    }

    public static int addSpecialOrderPrizeTickets(ServerPlayer player, int amount) {
        PlayerStardewData data = getData(player);
        int next = data.addSpecialOrderPrizeTickets(amount);
        if (amount > 0) {
            PlayerDataEventHandler.syncPlayerData(player, data);
        }
        return next;
    }

    public static boolean consumeSpecialOrderPrizeTicket(ServerPlayer player) {
        PlayerStardewData data = getData(player);
        boolean consumed = data.consumeSpecialOrderPrizeTicket();
        if (consumed) {
            PlayerDataEventHandler.syncPlayerData(player, data);
        }
        return consumed;
    }

    public static int getFairStarTokens(ServerPlayer player) {
        return getData(player).getFairStarTokens();
    }

    public static boolean setFairStarTokens(ServerPlayer player, int value) {
        PlayerStardewData data = getData(player);
        int before = data.getFairStarTokens();
        data.setFairStarTokens(value);
        boolean changed = before != data.getFairStarTokens();
        if (changed) {
            PlayerDataEventHandler.syncPlayerData(player, data);
        }
        return changed;
    }

    public static int addFairStarTokens(ServerPlayer player, int amount) {
        PlayerStardewData data = getData(player);
        int next = data.addFairStarTokens(amount);
        if (amount > 0) {
            PlayerDataEventHandler.syncPlayerData(player, data);
        }
        return next;
    }

    public static boolean consumeFairStarTokens(ServerPlayer player, int amount) {
        PlayerStardewData data = getData(player);
        boolean consumed = data.consumeFairStarTokens(amount);
        if (consumed && amount > 0) {
            PlayerDataEventHandler.syncPlayerData(player, data);
        }
        return consumed;
    }

    public static boolean hasBookPower(ServerPlayer player, String bookId) {
        return getStat(player, bookId) > 0;
    }

    // ============ 运气相关 ============

    /**
     * 获取今日运气（per-player）。
     *
     * 规则：以星露谷时间（StardewTimeManager 的日期）为准；当日期变化时惰性重新掷一次。
     */
    public static double getDailyLuck(ServerPlayer player) {
        PlayerStardewData data = getData(player);
        StardewTimeManager timeManager = StardewTimeManager.get();
        int dateKey = computeDateKey(timeManager);

        if (data.getDailyLuckDateKey() != dateKey) {
            double rolled = rollDailyLuck(player, dateKey);
            data.setDailyLuckForDate(rolled, dateKey);
            PlayerDataEventHandler.syncPlayerData(player, data);
        }

        return data.getDailyLuck();
    }

    /**
     * 为“今天”强制设置运气（调试用）。下一天会被正常刷新覆盖。
     */
    public static void setDailyLuckForToday(ServerPlayer player, double value) {
        PlayerStardewData data = getData(player);
        StardewTimeManager timeManager = StardewTimeManager.get();
        int dateKey = computeDateKey(timeManager);
        data.setDailyLuckForDate(value, dateKey);
        PlayerDataEventHandler.syncPlayerData(player, data);
    }

    private static int computeDateKey(StardewTimeManager timeManager) {
        // year 从 1 开始，day 为 1-28，season 为 0-3
        int year = timeManager.getCurrentYear();
        int season = timeManager.getCurrentSeason();
        int day = timeManager.getCurrentDay();
        return ((year * 4) + season) * 28 + (day - 1);
    }

    private static double rollDailyLuck(ServerPlayer player, int dateKey) {
        // SV 原版 sharedDailyLuck: random.Next(-100, 101) / 1000.0 并 cap 到 0.1
        // 这里改为 per-player：用 UUID + dateKey 做确定性 seed（方便重启/重连一致）。
        long seed = player.getUUID().getMostSignificantBits()
            ^ player.getUUID().getLeastSignificantBits()
            ^ (long) dateKey * 0x9E3779B97F4A7C15L;
        Random random = new Random(seed);
        int r = random.nextInt(201) - 100; // [-100, 100]
        double luck = r / 1000.0;
        return Math.min(luck, 0.1);
    }
    
    // ============ 经验相关 ============
    
    /**
     * 添加经验值
     * @return 是否升级
     */
    public static boolean addExperience(ServerPlayer player, SkillType skill, int amount) {
        if (amount <= 0) {
            return false;
        }
        PlayerStardewData data = getData(player);
        int beforeExp = data.getSkillExperience(skill);
        int beforeDisplayLevel = data.getSkillLevel(skill);
        boolean leveledUp = data.addExperience(skill, amount);
        int afterExp = data.getSkillExperience(skill);
        int afterDisplayLevel = data.getSkillLevel(skill);
        PlayerDataEventHandler.syncPlayerData(player, data);
        PacketDistributor.sendToPlayer(player, new SkillExperienceGainPayload(
            skill.getId(),
            amount,
            beforeExp,
            afterExp,
            beforeDisplayLevel,
            afterDisplayLevel
        ));
        return leveledUp;
    }

    public static java.util.List<PlayerStardewData.SkillLevelUp> applyPendingSkillLevelUps(ServerPlayer player) {
        PlayerStardewData data = getData(player);
        return data.applyPendingSkillLevelUps();
    }
    
    /**
     * 设置经验值（绝对值）
     */
    public static void setExperience(ServerPlayer player, SkillType skill, int amount) {
        PlayerStardewData data = getData(player);
        data.setSkillExperience(skill, Math.max(0, amount));
        PlayerDataEventHandler.syncPlayerData(player, data);
    }
    
    /**
     * 移除经验值
     */
    public static void removeExperience(ServerPlayer player, SkillType skill, int amount) {
        PlayerStardewData data = getData(player);
        int currentExp = data.getSkillExperience(skill);
        data.setSkillExperience(skill, Math.max(0, currentExp - amount));
        PlayerDataEventHandler.syncPlayerData(player, data);
    }
    
    /**
     * 获取技能等级
     */
    public static int getSkillLevel(ServerPlayer player, SkillType skill) {
        return Math.max(0, getData(player).getSkillLevel(skill));
    }

    /**
     * 获取“运气等级”(Luck Buff)。
     * 仅用于还原星露谷中 Luck Buff 对钓鱼/宝箱概率等的影响。
     */
    public static int getLuckLevel(ServerPlayer player) {
        return getData(player).getLuckLevel();
    }
    
    /**
     * 获取技能经验
     */
    public static int getSkillExperience(ServerPlayer player, SkillType skill) {
        return getData(player).getSkillExperience(skill);
    }
    
    /**
     * 获取升级进度（0.0-1.0）
     */
    public static float getLevelProgress(ServerPlayer player, SkillType skill) {
        return getData(player).getLevelProgress(skill);
    }
    
    /**
     * 获取升级所需经验
     */
    public static int getExpForNextLevel(ServerPlayer player, SkillType skill) {
        return getData(player).getExpForNextLevel(skill);
    }
    
    // ============ 能量相关 ============
    
    /**
     * 获取当前能量
     */
    public static float getEnergy(ServerPlayer player) {
        return getData(player).getEnergy();
    }
    
    /**
     * 获取最大能量
     */
    public static int getMaxEnergy(ServerPlayer player) {
        return getData(player).getMaxEnergy();
    }
    
    /**
     * 消耗能量
     * @return 是否成功（能量是否足够）
     */
    public static boolean consumeEnergy(ServerPlayer player, float amount) {
        if (amount > 0.0f && player.hasEffect(ModMobEffects.STATUE_OF_BLESSINGS_2)) {
            return true;
        }
        PlayerStardewData data = getData(player);
        boolean success = data.consumeEnergy(amount);
        if (success) {
            PlayerDataEventHandler.syncPlayerVitals(player, data);
        }
        return success;
    }

    /** Returns whether the complete cost can be paid without mutating player data. */
    public static boolean canConsumeEnergy(ServerPlayer player, float amount) {
        if (amount <= 0.0F || player.hasEffect(ModMobEffects.STATUE_OF_BLESSINGS_2)) {
            return true;
        }
        return getData(player).getEnergy() >= amount;
    }

    /** Pays an all-or-nothing action cost and shows the Stardew HUD error on failure. */
    public static boolean consumeEnergyOrNotify(ServerPlayer player, float amount) {
        if (consumeEnergy(player, amount)) {
            return true;
        }
        com.stardew.craft.network.payload.HudHintPayload.send(
                player, "stardewcraft.message.player.exhausted");
        return false;
    }

    public static int getBlessingOfWatersRemaining(ServerPlayer player) {
        return getData(player).getBlessingOfWatersRemaining();
    }

    public static boolean consumeBlessingOfWatersUse(ServerPlayer player) {
        if (!player.hasEffect(ModMobEffects.STATUE_OF_BLESSINGS_3)) {
            return false;
        }
        PlayerStardewData data = getData(player);
        if (data.getBlessingOfWatersRemaining() <= 0) {
            player.removeEffect(ModMobEffects.STATUE_OF_BLESSINGS_3);
            PlayerDataEventHandler.syncPlayerData(player, data);
            return false;
        }
        int remaining = data.consumeBlessingOfWatersUse();
        if (remaining <= 0) {
            player.removeEffect(ModMobEffects.STATUE_OF_BLESSINGS_3);
        }
        PlayerDataEventHandler.syncPlayerData(player, data);
        return true;
    }
    
    /**
     * 恢复能量
     */
    public static void restoreEnergy(ServerPlayer player, float amount) {
        PlayerStardewData data = getData(player);
        data.restoreEnergy(amount);
        PlayerDataEventHandler.syncPlayerVitals(player, data);
    }

    public static void rollbackEnergyPayment(
            ServerPlayer player,
            float amount,
            boolean exhaustedBefore
    ) {
        PlayerStardewData data = getData(player);
        data.rollbackEnergyPayment(amount, exhaustedBefore);
        PlayerDataEventHandler.syncPlayerVitals(player, data);
    }
    
    /**
     * 是否疲惫
     */
    public static boolean isExhausted(ServerPlayer player) {
        return getData(player).isExhausted();
    }
    
    /**
     * 治愈疲惫状态
     */
    public static void cureExhaustion(ServerPlayer player) {
        getData(player).cureExhaustion();
    }
    
    /**
     * 睡觉恢复能量
     */
    public static void sleep(ServerPlayer player, int sleepTime) {
        getData(player).sleep(sleepTime);
        com.stardew.craft.mastery.MasteryBuffLifecycle.clearAllDailyMasteryBuffs(player);
    }
    
    // ============ 生命值相关 ============
    
    /**
     * 获取当前生命值
     */
    public static int getHealth(ServerPlayer player) {
        return getData(player).getHealth();
    }
    
    /**
     * 设置生命值
     */
    public static void setHealth(ServerPlayer player, int health) {
        PlayerStardewData data = getData(player);
        data.setHealth(health);
        PlayerDataEventHandler.syncPlayerVitals(player, data);
    }
    
    /**
     * 获取最大生命值
     */
    public static int getMaxHealth(ServerPlayer player) {
        return getData(player).getMaxHealth();
    }

    // ============ 临时 Buff（食物增益） ============

    public static int getLuckBuffLevel(ServerPlayer player) {
        int foodLuck = getData(player).getTempLuckBonus();
        // 加上装备幸运加成（Lucky Ring = +1）
        com.stardew.craft.combat.equipment.EquipmentStats eqStats = com.stardew.craft.combat.equipment.EquipmentResolver.getMergedStats(player);
        return foodLuck + (int) eqStats.getLuck();
    }

    public static int getMagneticRadiusBuff(ServerPlayer player) {
        return getData(player).getTempMagneticRadiusBonus();
    }

    @SuppressWarnings("null")
    public static void applyFishingLevelBuff(ServerPlayer player, int bonus, int durationTicks) {
        if (bonus == 0 || durationTicks <= 0) {
            return;
        }

        // 以 MobEffect 形式显示（支持等级/图标）
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                com.stardew.craft.effect.ModMobEffects.SEA_KING_BLESSING,
                durationTicks,
                Math.max(0, bonus - 1)
        ));

        PlayerStardewData data = getData(player);
        long endTick = player.level().getGameTime() + durationTicks;
        data.applyTempFishingLevelBonus(bonus, endTick);
        PlayerDataEventHandler.syncPlayerData(player, data);
    }

    @SuppressWarnings("null")
    public static void applyLuckBuff(ServerPlayer player, int bonus, int durationTicks) {
        if (bonus == 0 || durationTicks <= 0) {
            return;
        }

        // 以 MobEffect 形式显示（支持等级/图标）
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                com.stardew.craft.effect.ModMobEffects.SPIRIT_BLESSING,
                durationTicks,
                Math.max(0, bonus - 1)
        ));

        PlayerStardewData data = getData(player);
        long endTick = player.level().getGameTime() + durationTicks;
        data.applyTempLuckBonus(bonus, endTick);
        PlayerDataEventHandler.syncPlayerData(player, data);
    }

    @SuppressWarnings("null")
    public static void applyMaxEnergyBuff(ServerPlayer player, int bonus, int durationTicks) {
        if (bonus == 0 || durationTicks <= 0) {
            return;
        }

        // 以 MobEffect 形式显示（支持等级/图标）。当前规则：每级 +30。
        if (bonus > 0 && bonus % 30 == 0) {
            int amplifier = Math.max(0, (bonus / 30) - 1);
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    com.stardew.craft.effect.ModMobEffects.VIGOROUS,
                    durationTicks,
                    amplifier
            ));
        }

        PlayerStardewData data = getData(player);
        long endTick = player.level().getGameTime() + durationTicks;
        data.applyTempMaxEnergyBonus(bonus, endTick);
        PlayerDataEventHandler.syncPlayerData(player, data);
    }

    @SuppressWarnings("null")
    public static void applyFarmingLevelBuff(ServerPlayer player, int bonus, int durationTicks) {
        if (bonus == 0 || durationTicks <= 0) {
            return;
        }
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                com.stardew.craft.effect.ModMobEffects.FARMER_BLESSING,
                durationTicks,
                Math.max(0, bonus - 1)
        ));
        PlayerStardewData data = getData(player);
        long endTick = player.level().getGameTime() + durationTicks;
        data.applyTempFarmingLevelBonus(bonus, endTick);
        PlayerDataEventHandler.syncPlayerData(player, data);
    }

    @SuppressWarnings("null")
    public static void applyForagingLevelBuff(ServerPlayer player, int bonus, int durationTicks) {
        if (bonus == 0 || durationTicks <= 0) {
            return;
        }
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                com.stardew.craft.effect.ModMobEffects.FORAGER_BLESSING,
                durationTicks,
                Math.max(0, bonus - 1)
        ));
        PlayerStardewData data = getData(player);
        long endTick = player.level().getGameTime() + durationTicks;
        data.applyTempForagingLevelBonus(bonus, endTick);
        PlayerDataEventHandler.syncPlayerData(player, data);
    }

    @SuppressWarnings("null")
    public static void applyMiningLevelBuff(ServerPlayer player, int bonus, int durationTicks) {
        if (bonus == 0 || durationTicks <= 0) {
            return;
        }
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                com.stardew.craft.effect.ModMobEffects.MINER_BLESSING,
                durationTicks,
                Math.max(0, bonus - 1)
        ));
        PlayerStardewData data = getData(player);
        long endTick = player.level().getGameTime() + durationTicks;
        data.applyTempMiningLevelBonus(bonus, endTick);
        PlayerDataEventHandler.syncPlayerData(player, data);
    }

    @SuppressWarnings("null")
    public static void applyAttackBuff(ServerPlayer player, int bonus, int durationTicks) {
        if (bonus == 0 || durationTicks <= 0) {
            return;
        }
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                com.stardew.craft.effect.ModMobEffects.WARRIOR_BLESSING,
                durationTicks,
                Math.max(0, bonus - 1)
        ));
        PlayerStardewData data = getData(player);
        long endTick = player.level().getGameTime() + durationTicks;
        data.applyTempAttackBonus(bonus, endTick);
        PlayerDataEventHandler.syncPlayerData(player, data);
    }

    @SuppressWarnings("null")
    public static void applyDefenseBuff(ServerPlayer player, int bonus, int durationTicks) {
        if (bonus == 0 || durationTicks <= 0) {
            return;
        }
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                com.stardew.craft.effect.ModMobEffects.GUARDIAN_BLESSING,
                durationTicks,
                Math.max(0, bonus - 1)
        ));
        PlayerStardewData data = getData(player);
        long endTick = player.level().getGameTime() + durationTicks;
        data.applyTempDefenseBonus(bonus, endTick);
        PlayerDataEventHandler.syncPlayerData(player, data);
    }

    @SuppressWarnings("null")
    public static void applyMagneticRadiusBuff(ServerPlayer player, int bonus, int durationTicks) {
        if (bonus == 0 || durationTicks <= 0) {
            return;
        }
        int amplifier = Math.max(0, bonus - 1);
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                com.stardew.craft.effect.ModMobEffects.MAGNETISM,
                durationTicks,
                amplifier
        ));
        PlayerStardewData data = getData(player);
        long endTick = player.level().getGameTime() + durationTicks;
        data.applyTempMagneticRadiusBonus(bonus, endTick);
        PlayerDataEventHandler.syncPlayerData(player, data);
    }
    
    /**
     * 设置最大生命值
     */
    public static void setMaxHealth(ServerPlayer player, int maxHealth) {
        PlayerStardewData data = getData(player);
        data.setMaxHealth(maxHealth);
        PlayerDataEventHandler.syncPlayerVitals(player, data);
    }
    
    // ============ 金币相关 ============
    
    /**
     * 获取金币
     */
    public static int getMoney(ServerPlayer player) {
        return com.stardew.craft.money.SharedMoneyService.getMoney(player);
    }
    
    /**
     * 设置金币
     */
    public static void setMoney(ServerPlayer player, int money) {
        com.stardew.craft.money.SharedMoneyService.setMoney(player, money);
    }
    
    /**
     * 添加金币
     */
    public static void addMoney(ServerPlayer player, int amount) {
        com.stardew.craft.money.SharedMoneyService.addMoney(player, amount);
    }
    
    /**
     * 移除金币
     * @return 是否成功(金币是否足够)
     */
    public static boolean removeMoney(ServerPlayer player, int amount) {
        return com.stardew.craft.money.SharedMoneyService.removeMoney(player, amount);
    }
    
    // ============ 职业相关 ============
    
    /**
     * 添加职业
     */
    public static boolean addProfession(ServerPlayer player, ProfessionType profession) {
        PlayerStardewData data = getData(player);
        boolean changed = data.addProfession(profession);
        if (changed) {
            PlayerDataEventHandler.syncPlayerData(player, data);
        }
        return changed;
    }

    /**
     * 移除职业
     */
    public static boolean removeProfession(ServerPlayer player, ProfessionType profession) {
        PlayerStardewData data = getData(player);
        boolean changed = data.removeProfession(profession);
        if (changed) {
            PlayerDataEventHandler.syncPlayerData(player, data);
        }
        return changed;
    }

    /**
     * 清空职业并重新根据等级补齐待选项
     */
    public static boolean clearProfessions(ServerPlayer player) {
        PlayerStardewData data = getData(player);
        boolean changed = data.clearProfessions();
        if (changed) {
            PlayerDataEventHandler.syncPlayerData(player, data);
        }
        return changed;
    }

    public static boolean respecProfessionsForSkill(ServerPlayer player, SkillType skill) {
        PlayerStardewData data = getData(player);
        boolean changed = data.respecProfessionsForSkill(skill);
        if (changed) {
            PlayerDataEventHandler.syncPlayerData(player, data);
        }
        return changed;
    }

    /**
     * 选择当前待处理职业
     */
    public static boolean choosePendingProfession(ServerPlayer player, ProfessionType profession) {
        PlayerStardewData data = getData(player);
        boolean changed = data.choosePendingProfession(profession);
        if (changed) {
            PlayerDataEventHandler.syncPlayerData(player, data);
        }
        return changed;
    }

    /**
     * 按当前等级修复漏掉的职业待选项
     */
    public static void repairMissingProfessionChoices(ServerPlayer player) {
        PlayerStardewData data = getData(player);
        data.repairMissingProfessionChoices();
        PlayerDataEventHandler.syncPlayerData(player, data);
    }

    public static java.util.List<PlayerStardewData.ProfessionChoicePrompt> getPendingProfessionChoices(ServerPlayer player) {
        return getData(player).getPendingProfessionChoices();
    }
    
    /**
     * 是否拥有职业
     */
    public static boolean hasProfession(ServerPlayer player, ProfessionType profession) {
        return getData(player).hasProfession(profession);
    }

    // ============ 钓鱼记录（CatchLimit） ============

    public static int getFishCatchCount(ServerPlayer player, String itemId) {
        return getData(player).getFishCatchCount(itemId);
    }

    public static void addFishCatchCount(ServerPlayer player, String itemId, int amount) {
        PlayerStardewData data = getData(player);
        if (data.addFishCatchCount(itemId, amount)) {
            PlayerDataEventHandler.syncPlayerData(player, data);
        }
    }

    public static boolean isSpecialOrderRuleActive(ServerPlayer player, String ruleId) {
        return getData(player).isSpecialOrderRuleActive(ruleId);
    }

    public static void setSpecialOrderRuleActive(ServerPlayer player, String ruleId, boolean active) {
        PlayerStardewData data = getData(player);
        if (data.setSpecialOrderRuleActive(ruleId, active)) {
            PlayerDataEventHandler.syncPlayerData(player, data);
        }
    }

    @SuppressWarnings("null")
    public static void recordOvernightShippedItems(ServerPlayer player, java.util.List<OvernightSettlementPayload.ShippedItem> shippedItems) {
        if (player == null || shippedItems == null || shippedItems.isEmpty()) {
            return;
        }

        PlayerStardewData data = getData(player);
        boolean changed = false;
        int totalGold = 0;
        for (OvernightSettlementPayload.ShippedItem shippedItem : shippedItems) {
            if (shippedItem == null || shippedItem.stack().isEmpty() || shippedItem.stack().getCount() <= 0) {
                continue;
            }

            // SDV parity: 夜间结算时统一发放金币
            totalGold += shippedItem.pricePerItem() * shippedItem.stack().getCount();

            String itemId = BuiltInRegistries.ITEM.getKey(shippedItem.stack().getItem()).toString();
            if (data.recordShippedItem(itemId, shippedItem.stack().getCount())) {
                changed = true;
            }
            com.stardew.craft.specialorder.SpecialOrderManager.recordShipped(player, shippedItem.stack(), shippedItem.stack().getCount());
        }

        if (totalGold > 0) {
            data.addTotalShippingGold(totalGold);
            com.stardew.craft.money.SharedMoneyService.addMoney(player, totalGold);
            changed = true;
        }

        if (changed) {
            PlayerDataEventHandler.syncPlayerData(player, data);
        }
    }

    public static void applySkillLevelRecipeUnlocks(ServerPlayer player, java.util.List<PlayerStardewData.SkillLevelUp> levelUps) {
        if (player == null || levelUps == null || levelUps.isEmpty()) {
            return;
        }

        PlayerStardewData data = getData(player);
        boolean changed = false;
        for (PlayerStardewData.SkillLevelUp levelUp : levelUps) {
            if (levelUp == null) {
                continue;
            }

            String sourceId = UnlockSourceData.skillLevelSourceId(levelUp.skill(), levelUp.newLevel());
            if (UnlockSourceData.hasSource(sourceId)) {
                changed |= applyUnlockBundle(data, UnlockSourceData.getUnlocks(sourceId));
                continue;
            }

            // Legacy fallback: if a skill-level source isn't configured yet,
            // continue to honor the old recipe-only data table.
            java.util.List<String> unlocks = SkillLevelRecipeUnlocks.getUnlocks(levelUp.skill(), levelUp.newLevel());
            for (String recipeId : unlocks) {
                if (data.unlockRecipe(recipeId)) {
                    changed = true;
                }
            }
        }

        if (changed) {
            PlayerDataEventHandler.syncPlayerData(player, data);
        }

        // Stardew 原版会在登录/等级变化时按配方 unlock 条件补齐漏学配方。
        applyStardewCraftingConditionUnlocks(player);
    }

    public static boolean applyStardewCraftingConditionUnlocks(ServerPlayer player) {
        if (player == null) {
            return false;
        }

        PlayerStardewData data = getData(player);
        int maxMineFloor = 0;
        try {
            maxMineFloor = MiningDataManager.getPlayerData(player).getMaxFloorReached();
        } catch (Exception ignored) {
            maxMineFloor = 0;
        }

        boolean changed = false;
        for (StardewCraftingRecipeData.RecipeEntry recipe : StardewCraftingRecipeData.getRecipes()) {
            if (recipe == null || recipe.id() == null || recipe.id().isBlank()) {
                continue;
            }
            if (data.isRecipeUnlocked(recipe.id())) {
                continue;
            }
            boolean matchesModern = !recipe.unlockWhen().isEmpty()
                    && recipe.unlockWhen().stream().allMatch(condition ->
                    com.stardew.craft.api.v1.condition.StardewConditions.test(
                            condition,
                            com.stardew.craft.api.v1.condition.StardewConditionContext.forPlayer(player))
                            .result().orElse(false));
            boolean matchesLegacy = recipe.unlockWhen().isEmpty()
                    && matchesCraftingUnlockCondition(recipe.unlockCondition(), data, maxMineFloor);
            if ((matchesModern || matchesLegacy) && data.unlockRecipe(recipe.id())) {
                changed = true;
            }
        }

        if (changed) {
            PlayerDataEventHandler.syncPlayerData(player, data);
        }
        return changed;
    }

    private static boolean matchesCraftingUnlockCondition(String rawCondition, PlayerStardewData data, int maxMineFloor) {
        if (data == null) {
            return false;
        }

        if (rawCondition == null) {
            return false;
        }

        String condition = rawCondition.trim();
        if (condition.isEmpty() || "null".equalsIgnoreCase(condition)) {
            return false;
        }
        if ("default".equalsIgnoreCase(condition)) {
            return true;
        }

        String[] parts = condition.split("\\s+");
        if (parts.length == 0) {
            return false;
        }

        String head = parts[0].toLowerCase(java.util.Locale.ROOT);
        if ("s".equals(head) && parts.length >= 3) {
            SkillType skill = SkillType.fromName(parts[1]);
            Integer level = parseInt(parts[2]);
            return skill != null && level != null && data.getRawSkillLevel(skill) >= level;
        }

        if ("farming".equals(head) || "fishing".equals(head) || "foraging".equals(head)
                || "mining".equals(head) || "combat".equals(head)) {
            SkillType skill = SkillType.fromName(parts[0]);
            Integer level = parts.length >= 2 ? parseInt(parts[1]) : null;
            return skill != null && level != null && data.getRawSkillLevel(skill) >= level;
        }

        // NOTE: SDV's `l N` (mine-floor) is a display/filter condition, not an
        // auto-unlock. The only way to learn a recipe gated by `l N` in vanilla
        // is via the relevant teaching event (e.g. Furnace ← Event 992553).
        // Do not auto-unlock on mine-floor progress.

        return false;
    }

    private static Integer parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static int getRecipeCraftCount(ServerPlayer player, String recipeId) {
        if (player == null || recipeId == null || recipeId.isBlank()) {
            return 0;
        }
        return getData(player).getRecipeCraftCount(recipeId);
    }

    public static Map<String, Integer> getAllRecipeCraftCounts(ServerPlayer player) {
        if (player == null) {
            return Collections.emptyMap();
        }
        return getData(player).getAllRecipeCraftCounts();
    }

    public static boolean recordRecipeCrafted(ServerPlayer player, String recipeId, int craftedAmount) {
        if (player == null || recipeId == null || recipeId.isBlank() || craftedAmount <= 0) {
            return false;
        }

        PlayerStardewData data = getData(player);
        boolean changed = data.recordRecipeCrafted(recipeId, craftedAmount);
        if (changed) {
            PlayerDataEventHandler.syncPlayerData(player, data);
        }
        return changed;
    }

    public static void recordGiftGiven(ServerPlayer player) {
        if (player == null) {
            return;
        }
        PlayerStardewData data = getData(player);
        data.recordGiftGiven();
        PlayerDataEventHandler.syncPlayerData(player, data);
    }

    public static void recordCombatDeath(ServerPlayer player) {
        if (player == null) {
            return;
        }
        PlayerStardewData data = getData(player);
        data.recordCombatDeath();
        PlayerDataEventHandler.syncPlayerData(player, data);
    }

    public static void record2AmPassOut(ServerPlayer player) {
        if (player == null) {
            return;
        }
        PlayerStardewData data = getData(player);
        data.record2AmPassOut();
        PlayerDataEventHandler.syncPlayerData(player, data);
    }

    public static void recordExhaustionPassOut(ServerPlayer player) {
        if (player == null) {
            return;
        }
        PlayerStardewData data = getData(player);
        data.recordExhaustionPassOut();
        PlayerDataEventHandler.syncPlayerData(player, data);
    }

    public static void recordAnimalProductsCollected(ServerPlayer player, int amount) {
        if (player == null || amount <= 0) {
            return;
        }
        PlayerStardewData data = getData(player);
        data.recordAnimalProductsCollected(amount);
        PlayerDataEventHandler.syncPlayerData(player, data);
    }

    public static void recordAnimalProductsCollected(UUID playerId, int amount) {
        if (playerId == null || amount <= 0) {
            return;
        }
        PlayerStardewData data = PlayerDataManager.getPlayerData(playerId);
        data.recordAnimalProductsCollected(amount);
    }

    public static boolean applyUnlockSource(ServerPlayer player, String sourceId) {
        if (player == null || sourceId == null || sourceId.isBlank()) {
            return false;
        }

        if (!UnlockSourceData.hasSource(sourceId)) {
            return false;
        }

        UnlockSourceData.UnlockBundle bundle = UnlockSourceData.getUnlocks(sourceId);
        PlayerStardewData data = getData(player);
        boolean changed = applyUnlockBundle(data, bundle);

        if (changed) {
            PlayerDataEventHandler.syncPlayerData(player, data);
        }
        return changed;
    }

    public static boolean applyUnlockSources(ServerPlayer player, Collection<String> sourceIds) {
        if (player == null || sourceIds == null || sourceIds.isEmpty()) {
            return false;
        }

        PlayerStardewData data = getData(player);
        boolean changed = false;
        for (String sourceId : sourceIds) {
            if (sourceId == null || sourceId.isBlank() || !UnlockSourceData.hasSource(sourceId)) {
                continue;
            }

            UnlockSourceData.UnlockBundle bundle = UnlockSourceData.getUnlocks(sourceId);
            changed |= applyUnlockBundle(data, bundle);
        }

        if (changed) {
            PlayerDataEventHandler.syncPlayerData(player, data);
        }

        return changed;
    }

    private static boolean applyUnlockBundle(PlayerStardewData data, UnlockSourceData.UnlockBundle bundle) {
        if (data == null || bundle == null) {
            return false;
        }

        boolean changed = false;
        for (String recipeId : bundle.recipes()) {
            if (data.unlockRecipe(recipeId)) {
                changed = true;
            }
        }
        for (String styleId : bundle.wallpapers()) {
            if (data.unlockDecoration(DecorationType.WALLPAPER, styleId)) {
                changed = true;
            }
        }
        for (String styleId : bundle.floorings()) {
            if (data.unlockDecoration(DecorationType.FLOORING, styleId)) {
                changed = true;
            }
        }
        return changed;
    }
}

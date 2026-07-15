package com.stardew.craft.player;

import com.stardew.craft.deco.DecorationStyleRegistry;
import com.stardew.craft.deco.DecorationType;
import com.stardew.craft.leaderboard.LeaderboardMetric;
import com.stardew.craft.leaderboard.LeaderboardPeriod;
import com.stardew.craft.mastery.MasteryProgress;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.stardew.craft.quest.QuestManager;

/**
 * 玩家星露谷数据
 * 存储玩家的所有星露谷相关数据（生命、能量、技能、金币等）
 */
@SuppressWarnings("null")
public class PlayerStardewData {
    private static final float MIN_ENERGY = -16.0F;
    
    // 玩家UUID
    private UUID playerUUID;
    private String lastKnownName = "";

    // ============ 玩家档案（对话个性化） ============
    // -1=未选择，0=男性，1=女性。三态值用于识别需要补录资料的旧存档。
    private int gender = -1;
    private String preferredName = "";
    private String favoriteThing = "";
    
    // ============ 基础属性 ============
    private int health;              // 当前生命值
    private int maxHealth;           // 最大生命值（基础100）
    
    private float energy;            // 当前能量值（精力值）
    private int maxEnergy;           // 最大能量值（基础270）
    private int stardropsConsumed;    // 已食用星之果实数量
    private boolean exhausted;       // 是否处于疲惫状态
    
    private int money;               // 金币数量
    private long totalMoneyEarned;    // SDV totalMoneyEarned：累计赚到的金币（不随花费减少）
    private int fairStarTokens;       // SDV Farmer.festivalScore：Stardew Valley Fair 星星币
    private int lastFairGrillBurgerDateKey; // Fall 16 fair grill: one Survival Burger per player per day
    
    // ============ 技能系统 ============
    // 技能等级（0-10级）
    private final int[] skillLevels = new int[5];  // farming(0), fishing(1), foraging(2), mining(3), combat(4)
    
    // 技能经验值
    private final int[] experiencePoints = new int[5];
    
    // 职业选择
    private final List<Integer> professions = new ArrayList<>();
    private final List<ProfessionChoicePrompt> pendingProfessionChoices = new ArrayList<>();

    // SDV parity: newLevels — 白天获得的等级提升列表，睡觉时消费（用于升级动画 + 职业选择）
    private final List<SkillLevelUp> pendingNewLevels = new ArrayList<>();

    // ============ 精通系统 (Mastery) ============
    // SDV parity: StatKeys.MasteryExp / MasteryLevelsSpent / Mastery(skill)
    private long masteryExp;                       // 累积精通经验（满级 100k）
    private int masteryLevelsSpent;                // 已花掉的精通等级数
    private final boolean[] claimedMasteryRewards = new boolean[5]; // 各技能 pedestal 奖励是否已领（0-4）
    private boolean gotMasteryHint;                // 是否已收到 mastery_hint 邮件
    private boolean visitedMasteryCave;            // 是否首次进入过山洞（cutscene 标记）
    private int unlockedTrinketSlots;              // Mastery4 领奖后置 1
    private boolean blessedByStatueToday;          // 今天右键过 Statue of Blessings 拿过 buff（早晨清零）
    private int blessingOfWatersRemaining;         // statue_of_blessings_3: 今天剩余的水域祝福次数
    /** 客户端动画用：每次升级 push 一次，UI 消费后清掉。 */
    private final List<Integer> pendingMasteryLevelUps = new ArrayList<>();
    
    // ============ 其他数据 ============
    private long lastSyncTime;       // 最后一次同步时间
    private boolean dirty;           // 数据是否需要保存

    // ============ 运气系统 ============
    // 每日运气（per-player），按星露谷日期刷新（由 PlayerStardewDataAPI 惰性刷新）
    private double dailyLuck;
    private int dailyLuckDateKey;

    // ============ 临时Buff（食物增益） ============
    // 说明：使用 level.getGameTime() 作为时间基准，支持跨保存/重启继续倒计时。
    private int tempFishingLevelBonus;
    private long tempFishingLevelBonusEndTick;

    private int tempLuckBonus;
    private long tempLuckBonusEndTick;

    private int tempMaxEnergyBonus;
    private long tempMaxEnergyBonusEndTick;

    private int tempFarmingLevelBonus;
    private long tempFarmingLevelBonusEndTick;

    private int tempForagingLevelBonus;
    private long tempForagingLevelBonusEndTick;

    private int tempMiningLevelBonus;
    private long tempMiningLevelBonusEndTick;

    private int tempAttackBonus;
    private long tempAttackBonusEndTick;

    private int tempDefenseBonus;
    private long tempDefenseBonusEndTick;

    private int tempMagneticRadiusBonus;
    private long tempMagneticRadiusBonusEndTick;

    // ============ 装饰解锁 ============
    private final Set<String> unlockedWallpaperStyles = new HashSet<>();
    private final Set<String> unlockedFlooringStyles = new HashSet<>();

    // ============ 配方解锁 ============
    private final Set<String> unlockedRecipes = new HashSet<>();
    private final Map<String, Integer> recipeCraftCounts = new HashMap<>();
    private int queenOfSauceWatchDay = -1;
    private String queenOfSauceRecipeId = "";

    // ============ 钓鱼记录 ============
    // 用于对齐原版 CatchLimit（例如传奇鱼一次性）
    private final Map<String, Integer> fishCatchCounts = new HashMap<>();
    /** SDV {@code Stats.PreciseFishCaught}: monotonic counter incremented per individual catch (incl. duplicates). */
    private int preciseFishCaught = 0;

    // ============ 出货统计（夜间结算） ============
    // 对齐原版 player.shippedBasic 与 stats.ItemsShipped 的基础数据结构。
    private final Set<String> shippedBasic = new HashSet<>();
    private final Map<String, Integer> itemsShipped = new HashMap<>();
    private long totalShippingGold;

    // ============ 特殊订单规则 ============
    // 对齐原版 PLAYER_SPECIAL_ORDER_RULE_ACTIVE 条件分支。
    private final Set<String> activeSpecialOrderRules = new HashSet<>();

    // ============ 垃圾桶统计 ============
    private int trashCansChecked;

    // ============ 排行榜统计 ============
    private int mineBlocksBrokenTotal;
    private int mineStonesBroken;
    private int mineOresBroken;
    private int mineGemOresBroken;
    private int mineMineralNodesBroken;
    private int mineBlocksBombed;
    private int giftsGivenTotal;
    private int combatDeaths;
    private int passOuts2Am;
    private int passOutsExhaustion;
    private int animalProductsCollectedTotal;
    private int basementShortsStolen;
    private int leaderboardDayKey = Integer.MIN_VALUE;
    private int leaderboardWeekKey = Integer.MIN_VALUE;
    private int leaderboardSeasonKey = Integer.MIN_VALUE;
    private final Map<String, Long> leaderboardDayValues = new HashMap<>();
    private final Map<String, Long> leaderboardWeekValues = new HashMap<>();
    private final Map<String, Long> leaderboardSeasonValues = new HashMap<>();

    // ============ 通用统计（SDV Stats parity）============
    private final Map<String, Integer> stats = new HashMap<>();

    // ============ 兑奖券系统（SDV Prize Machine parity）============
    private int ticketPrizesClaimed;
    private int specialOrderPrizeTickets;

    // ============ 邮件标记（SDV mailReceived parity） ============
    private final Set<String> mailFlags = new HashSet<>();

    // ============ 邮箱系统（SDV mailbox / mailForTomorrow parity） ============
    // mailbox: 当前可读的邮件ID队列（SDV Farmer.mailbox）
    private final List<String> mailbox = new ArrayList<>();
    // mailForTomorrow: 明天投递的邮件ID队列（SDV Farmer.mailForTomorrow）
    private final List<String> mailForTomorrow = new ArrayList<>();
    // mailFlagsForTomorrow: 次日生效的标志位队列（SDV noLetter: true 等价）。
    // Key = flag id，Value = queuedOnAbsoluteDay。flush 条件：currentDay > queuedOnAbsoluteDay。
    // 这样离线玩家跨日登录时也能按各自 queued 日正确判定是否该 flush（多人服务器安全）。
    private final java.util.LinkedHashMap<String, Integer> mailFlagsForTomorrow = new java.util.LinkedHashMap<>();

    // ============ 任务系统 ============
    private final QuestManager questManager = new QuestManager();

    // ============ 怪物讨伐（SDV MonsterSlayerQuests parity） ============
    private final Map<String, Integer> monsterKillCounts = new HashMap<>(); // goalKey → kills
    private final Set<String> claimedSlayerRewards = new HashSet<>();      // goalKeys already claimed

    // ============ 特殊物品去重（SDV Farmer.specialItems parity） ============
    // Tracks unique items already obtained (e.g. Neptune's Glaive from fishing treasure)
    private final Set<String> specialItems = new HashSet<>();

    // ============ 秘密纸条（SDV Farmer.secretNotesSeen parity） ============
    // 使用命名空间 ID，原版 1..27 对应 stardewcraft:1 .. stardewcraft:27。
    private final Set<String> secretNotesSeen = new HashSet<>();
    // 原版礼物日志：%revealtaste 和玩家亲自送过的物品都记为已知，不改变实际送礼口味。
    private final Set<String> revealedGiftTastes = new HashSet<>();

    // ============ 首次加入日（邮件调度基准） ============
    // -1 表示尚未初始化，首次处理邮件时设为当天全局总天数
    private int firstJoinDay = -1;

    // ============ 巫师塔枢纽（维度传送） ============
    private boolean wizardQuestComplete;          // 末影之眼已交付，星露谷入口已解锁
    private boolean wizardFirstMet;               // 是否已经看过巫师的初次剧情
    private boolean starterToolsGiven;            // 是否已给予新手工具六件套
    private boolean bilibiliRewardClaimed;          // 是否已领取B站关注奖励（彩虹猫之刃）
    private boolean mine100StardropCompensationProcessed; // 100层旧奖励修正补偿是否已处理
    private int handledPregenRelocationVersion;   // 已处理的 pregen 升级回农场版本
    private int lastGotDesertFestivalRatingPrizeFromMarlon = Integer.MIN_VALUE;
    private int desertFestivalMarlonChallengeDay = Integer.MIN_VALUE;
    private String desertFestivalMarlonChallengeId = "";
    private int desertFestivalMarlonChallengeProgress;
    private boolean desertFestivalMarlonChallengeRewardClaimed;
    @Nullable
    private BlockPos overworldReturnPos;           // 玩家从主世界进入巫师塔时记录的返回坐标
    @Nullable
    private ResourceKey<Level> wizardSourceDimension;  // 进入巫师塔内部时的来源维度

    // ============ 装备系统（戒指 + 靴子 + 外观装饰） ============
    // SDV parity: Farmer.leftRing + Farmer.rightRing + Farmer.boots + appearance slots
    // 保存完整物品栈，避免动态装备 Provider 依赖的 Data Component 在装备后丢失。
    private net.minecraft.world.item.ItemStack equippedLeftRing = net.minecraft.world.item.ItemStack.EMPTY;
    private net.minecraft.world.item.ItemStack equippedRightRing = net.minecraft.world.item.ItemStack.EMPTY;
    private net.minecraft.world.item.ItemStack equippedBoots = net.minecraft.world.item.ItemStack.EMPTY;
    private net.minecraft.world.item.ItemStack equippedTrinket = net.minecraft.world.item.ItemStack.EMPTY;
    private String equippedHat = "";
    private String equippedShirt = "";
    private String equippedPants = "";
    private long lastPhoenixReviveDay = -1;  // 凤凰戒指：上次复活的游戏日（每天只能复活一次）

    // ============ 工具升级（铁匠铺） ============
    // SDV parity: Farmer.toolBeingUpgraded + Farmer.daysLeftForToolUpgrade
    private String toolBeingUpgraded = "";   // item ID e.g. "stardewcraft:copper_axe", empty = none
    private int daysLeftForToolUpgrade;      // 0 = ready for pickup
    private boolean toolUpgradeNotified;     // true = already notified player this morning

    // ============ 晕倒/死亡系统 ============
    // 战斗死亡标志：次日 sleep() 后压体力到 2
    private boolean passedOutFromCombat;
    // 上次死亡丢失的物品（供 Marlon 物品找回商店使用）
    private final List<net.minecraft.world.item.ItemStack> itemsLostLastDeath = new ArrayList<>();
    
    // 经验值升级表（根据星露谷物语）
    private static final int[] EXP_TO_LEVEL = {
        0,      // Level 0
        100,    // Level 1
        380,    // Level 2
        770,    // Level 3
        1300,   // Level 4
        2150,   // Level 5
        3300,   // Level 6
        4800,   // Level 7
        6900,   // Level 8
        10000,  // Level 9
        15000   // Level 10
    };
    
    /**
     * 创建新的玩家数据（默认值）
     */
    public PlayerStardewData(UUID playerUUID) {
        this.playerUUID = playerUUID;
        this.health = 100;
        this.maxHealth = 100;
        this.energy = 270f;
        this.maxEnergy = 270;
        this.stardropsConsumed = 0;
        this.exhausted = false;
        this.money = 500;  // 初始金币
        this.fairStarTokens = 0;
        this.lastFairGrillBurgerDateKey = -1;
        this.totalMoneyEarned = 500L;
        this.lastSyncTime = System.currentTimeMillis();
        this.dirty = false;

        this.dailyLuck = 0.0;
        this.dailyLuckDateKey = -1;

        this.tempFishingLevelBonus = 0;
        this.tempFishingLevelBonusEndTick = 0L;
        this.tempLuckBonus = 0;
        this.tempLuckBonusEndTick = 0L;
        this.tempMaxEnergyBonus = 0;
        this.tempMaxEnergyBonusEndTick = 0L;
        this.tempFarmingLevelBonus = 0;
        this.tempFarmingLevelBonusEndTick = 0L;
        this.tempForagingLevelBonus = 0;
        this.tempForagingLevelBonusEndTick = 0L;
        this.tempMiningLevelBonus = 0;
        this.tempMiningLevelBonusEndTick = 0L;
        this.tempAttackBonus = 0;
        this.tempAttackBonusEndTick = 0L;
        this.tempDefenseBonus = 0;
        this.tempDefenseBonusEndTick = 0L;
        this.tempMagneticRadiusBonus = 0;
        this.tempMagneticRadiusBonusEndTick = 0L;

        this.unlockedRecipes.add("fried_egg");
        this.unlockedWallpaperStyles.add(DecorationStyleRegistry.getDefaultStyleId(DecorationType.WALLPAPER));
        this.unlockedFlooringStyles.add(DecorationStyleRegistry.getDefaultStyleId(DecorationType.FLOORING));
    }
    
    /**
     * 从NBT加载数据
     */
    public static PlayerStardewData fromNBT(CompoundTag tag, UUID playerUUID) {
        return fromNBT(tag, playerUUID, null);
    }

    public static PlayerStardewData fromNBT(
            CompoundTag tag, UUID playerUUID, @Nullable net.minecraft.core.HolderLookup.Provider registries) {
        PlayerStardewData data = new PlayerStardewData(playerUUID);
        
        // 基础属性
        data.health = tag.contains("Health") ? tag.getInt("Health") : 100;
        data.maxHealth = tag.contains("MaxHealth") ? tag.getInt("MaxHealth") : 100;
        data.energy = tag.contains("Energy") ? tag.getFloat("Energy") : 270f;
        data.maxEnergy = tag.contains("MaxEnergy") ? tag.getInt("MaxEnergy") : 270;
        data.stardropsConsumed = tag.contains("StardropsConsumed")
            ? Math.max(0, Math.min(7, tag.getInt("StardropsConsumed")))
            : Math.max(0, Math.min(7, (data.maxEnergy - 270) / 34));
        data.exhausted = tag.getBoolean("Exhausted");
        data.money = tag.contains("Money") ? tag.getInt("Money") : 500;
        data.fairStarTokens = tag.contains("FairStarTokens") ? Math.max(0, tag.getInt("FairStarTokens")) : 0;
        data.lastFairGrillBurgerDateKey = tag.contains("LastFairGrillBurgerDateKey") ? tag.getInt("LastFairGrillBurgerDateKey") : -1;
        data.totalMoneyEarned = tag.contains("TotalMoneyEarned")
            ? Math.max(0L, tag.getLong("TotalMoneyEarned"))
            : Math.max(data.money, data.totalShippingGold);
        data.lastKnownName = tag.contains("LastKnownName") ? tag.getString("LastKnownName") : "";
        data.gender = tag.contains("Gender") ? Math.max(-1, Math.min(1, tag.getInt("Gender"))) : -1;
        data.preferredName = tag.contains("PreferredName") ? tag.getString("PreferredName").trim() : "";
        data.favoriteThing = tag.contains("FavoriteThing") ? tag.getString("FavoriteThing").trim() : "";

        // 晕倒/死亡系统
        data.passedOutFromCombat = tag.getBoolean("PassedOutFromCombat");
        data.itemsLostLastDeath.clear();
        if (tag.contains("ItemsLostLastDeath")) {
            ListTag lostItemsTag = tag.getList("ItemsLostLastDeath", 10); // 10 = CompoundTag
            for (int li = 0; li < lostItemsTag.size(); li++) {
                CompoundTag itemTag = lostItemsTag.getCompound(li);
                var itemRL = ResourceLocation.tryParse(itemTag.getString("Id"));
                if (itemRL != null && net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(itemRL)) {
                    net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(itemRL);
                    data.itemsLostLastDeath.add(new net.minecraft.world.item.ItemStack(item, itemTag.getInt("Count")));
                }
            }
        }
        
        // 技能等级
        if (tag.contains("SkillLevels")) {
            int[] levels = tag.getIntArray("SkillLevels");
            System.arraycopy(levels, 0, data.skillLevels, 0, Math.min(levels.length, 5));
        }
        
        // 技能经验
        if (tag.contains("Experience")) {
            int[] exp = tag.getIntArray("Experience");
            System.arraycopy(exp, 0, data.experiencePoints, 0, Math.min(exp.length, 5));
        }
        
        // 职业
        data.professions.clear();
        if (tag.contains("Professions")) {
            int[] profs = tag.getIntArray("Professions");
            for (int prof : profs) {
                data.professions.add(prof);
            }
        }

        data.pendingProfessionChoices.clear();
        if (tag.contains("PendingProfessionChoices")) {
            ListTag list = tag.getList("PendingProfessionChoices", 10);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                if (!entry.contains("SkillId") || !entry.contains("Level")) {
                    continue;
                }
                SkillType skill = SkillType.fromId(entry.getInt("SkillId"));
                int level = entry.getInt("Level");
                if ((level == 5 || level == 10) && !data.hasPendingProfessionChoice(skill, level)) {
                    data.pendingProfessionChoices.add(new ProfessionChoicePrompt(skill, level));
                }
            }
        }

        // pendingNewLevels
        data.pendingNewLevels.clear();
        if (tag.contains("PendingNewLevels")) {
            ListTag nlList = tag.getList("PendingNewLevels", 10);
            for (int i = 0; i < nlList.size(); i++) {
                CompoundTag entry = nlList.getCompound(i);
                if (!entry.contains("SkillId") || !entry.contains("Level")) continue;
                SkillType skill = SkillType.fromId(entry.getInt("SkillId"));
                data.pendingNewLevels.add(new SkillLevelUp(skill, entry.getInt("Level")));
            }
        }
        
        // 元数据
        data.lastSyncTime = tag.getLong("LastSyncTime");

        // 精通系统
        data.masteryExp = tag.contains("MasteryExp") ? tag.getLong("MasteryExp") : 0L;
        data.masteryLevelsSpent = tag.contains("MasteryLevelsSpent") ? tag.getInt("MasteryLevelsSpent") : 0;
        if (tag.contains("ClaimedMasteryRewards")) {
            byte[] bits = tag.getByteArray("ClaimedMasteryRewards");
            for (int i = 0; i < Math.min(bits.length, 5); i++) {
                data.claimedMasteryRewards[i] = bits[i] != 0;
            }
        }
        data.gotMasteryHint = tag.getBoolean("GotMasteryHint");
        data.visitedMasteryCave = tag.getBoolean("VisitedMasteryCave");
        data.unlockedTrinketSlots = tag.contains("UnlockedTrinketSlots") ? tag.getInt("UnlockedTrinketSlots") : 0;
        data.blessedByStatueToday = tag.getBoolean("BlessedByStatueToday");
        data.blessingOfWatersRemaining = tag.contains("BlessingOfWatersRemaining") ? Math.max(0, tag.getInt("BlessingOfWatersRemaining")) : 0;
        data.pendingMasteryLevelUps.clear();
        if (tag.contains("PendingMasteryLevelUps")) {
            int[] mlu = tag.getIntArray("PendingMasteryLevelUps");
            for (int lvl : mlu) data.pendingMasteryLevelUps.add(lvl);
        }

        // 运气
        data.dailyLuck = tag.contains("DailyLuck") ? tag.getDouble("DailyLuck") : 0.0;
        data.dailyLuckDateKey = tag.contains("DailyLuckDateKey") ? tag.getInt("DailyLuckDateKey") : -1;

        // 临时Buff
        data.tempFishingLevelBonus = tag.contains("TempFishingLevelBonus") ? tag.getInt("TempFishingLevelBonus") : 0;
        data.tempFishingLevelBonusEndTick = tag.contains("TempFishingLevelBonusEndTick") ? tag.getLong("TempFishingLevelBonusEndTick") : 0L;
        data.tempLuckBonus = tag.contains("TempLuckBonus") ? tag.getInt("TempLuckBonus") : 0;
        data.tempLuckBonusEndTick = tag.contains("TempLuckBonusEndTick") ? tag.getLong("TempLuckBonusEndTick") : 0L;
        data.tempMaxEnergyBonus = tag.contains("TempMaxEnergyBonus") ? tag.getInt("TempMaxEnergyBonus") : 0;
        data.tempMaxEnergyBonusEndTick = tag.contains("TempMaxEnergyBonusEndTick") ? tag.getLong("TempMaxEnergyBonusEndTick") : 0L;
        data.tempFarmingLevelBonus = tag.contains("TempFarmingLevelBonus") ? tag.getInt("TempFarmingLevelBonus") : 0;
        data.tempFarmingLevelBonusEndTick = tag.contains("TempFarmingLevelBonusEndTick") ? tag.getLong("TempFarmingLevelBonusEndTick") : 0L;
        data.tempForagingLevelBonus = tag.contains("TempForagingLevelBonus") ? tag.getInt("TempForagingLevelBonus") : 0;
        data.tempForagingLevelBonusEndTick = tag.contains("TempForagingLevelBonusEndTick") ? tag.getLong("TempForagingLevelBonusEndTick") : 0L;
        data.tempMiningLevelBonus = tag.contains("TempMiningLevelBonus") ? tag.getInt("TempMiningLevelBonus") : 0;
        data.tempMiningLevelBonusEndTick = tag.contains("TempMiningLevelBonusEndTick") ? tag.getLong("TempMiningLevelBonusEndTick") : 0L;
        data.tempAttackBonus = tag.contains("TempAttackBonus") ? tag.getInt("TempAttackBonus") : 0;
        data.tempAttackBonusEndTick = tag.contains("TempAttackBonusEndTick") ? tag.getLong("TempAttackBonusEndTick") : 0L;
        data.tempDefenseBonus = tag.contains("TempDefenseBonus") ? tag.getInt("TempDefenseBonus") : 0;
        data.tempDefenseBonusEndTick = tag.contains("TempDefenseBonusEndTick") ? tag.getLong("TempDefenseBonusEndTick") : 0L;
        data.tempMagneticRadiusBonus = tag.contains("TempMagneticRadiusBonus") ? tag.getInt("TempMagneticRadiusBonus") : 0;
        data.tempMagneticRadiusBonusEndTick = tag.contains("TempMagneticRadiusBonusEndTick") ? tag.getLong("TempMagneticRadiusBonusEndTick") : 0L;

        data.unlockedWallpaperStyles.clear();
        data.unlockedFlooringStyles.clear();
        data.unlockedRecipes.clear();
        data.recipeCraftCounts.clear();
        if (tag.contains("UnlockedWallpaperStyles")) {
            ListTag list = tag.getList("UnlockedWallpaperStyles", 8);
            for (int i = 0; i < list.size(); i++) {
                data.unlockedWallpaperStyles.add(list.getString(i));
            }
        }
        if (tag.contains("UnlockedFlooringStyles")) {
            ListTag list = tag.getList("UnlockedFlooringStyles", 8);
            for (int i = 0; i < list.size(); i++) {
                data.unlockedFlooringStyles.add(list.getString(i));
            }
        }
        if (tag.contains("UnlockedRecipes")) {
            ListTag list = tag.getList("UnlockedRecipes", 8);
            for (int i = 0; i < list.size(); i++) {
                String recipeId = RecipeIdNormalizer.storageId(list.getString(i));
                if (!recipeId.isBlank()) {
                    data.unlockedRecipes.add(recipeId);
                }
            }
        }
        if (tag.contains("RecipeCraftCounts")) {
            ListTag list = tag.getList("RecipeCraftCounts", 10);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                if (!entry.contains("Recipe")) {
                    continue;
                }
                String recipeId = RecipeIdNormalizer.storageId(entry.getString("Recipe"));
                int count = Math.max(0, entry.getInt("Count"));
                if (!recipeId.isBlank() && count > 0) {
                    data.recipeCraftCounts.put(recipeId, count);
                }
            }
        }
        data.queenOfSauceWatchDay = tag.contains("QueenOfSauceWatchDay") ? tag.getInt("QueenOfSauceWatchDay") : -1;
        data.queenOfSauceRecipeId = tag.contains("QueenOfSauceRecipeId") ? tag.getString("QueenOfSauceRecipeId") : "";
        data.unlockedWallpaperStyles.add(DecorationStyleRegistry.getDefaultStyleId(DecorationType.WALLPAPER));
        data.unlockedFlooringStyles.add(DecorationStyleRegistry.getDefaultStyleId(DecorationType.FLOORING));

        data.fishCatchCounts.clear();
        if (tag.contains("FishCatchCounts")) {
            ListTag list = tag.getList("FishCatchCounts", 10);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                if (!entry.contains("Item")) {
                    continue;
                }
                String itemId = entry.getString("Item");
                int count = Math.max(0, entry.getInt("Count"));
                if (!itemId.isBlank() && count > 0) {
                    data.fishCatchCounts.put(itemId, count);
                }
            }
        }
        data.preciseFishCaught = Math.max(0, tag.getInt("PreciseFishCaught"));

        data.shippedBasic.clear();
        if (tag.contains("ShippedBasic")) {
            ListTag list = tag.getList("ShippedBasic", 8);
            for (int i = 0; i < list.size(); i++) {
                String itemId = list.getString(i);
                if (!itemId.isBlank()) {
                    data.shippedBasic.add(itemId);
                }
            }
        }

        data.itemsShipped.clear();
        if (tag.contains("ItemsShipped")) {
            ListTag list = tag.getList("ItemsShipped", 10);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                if (!entry.contains("Item")) {
                    continue;
                }
                String itemId = entry.getString("Item");
                int count = Math.max(0, entry.getInt("Count"));
                if (!itemId.isBlank() && count > 0) {
                    data.itemsShipped.put(itemId, count);
                }
            }
        }

        data.totalShippingGold = tag.contains("TotalShippingGold") ? Math.max(0L, tag.getLong("TotalShippingGold")) : 0L;
        if (!tag.contains("TotalMoneyEarned")) {
            data.totalMoneyEarned = Math.max(data.totalMoneyEarned, data.totalShippingGold);
        }

        data.activeSpecialOrderRules.clear();
        if (tag.contains("ActiveSpecialOrderRules")) {
            ListTag list = tag.getList("ActiveSpecialOrderRules", 8);
            for (int i = 0; i < list.size(); i++) {
                String rule = list.getString(i);
                if (!rule.isBlank()) {
                    data.activeSpecialOrderRules.add(rule);
                }
            }
        }

        data.trashCansChecked = tag.contains("TrashCansChecked") ? tag.getInt("TrashCansChecked") : 0;
        data.mineBlocksBrokenTotal = tag.contains("MineBlocksBrokenTotal") ? Math.max(0, tag.getInt("MineBlocksBrokenTotal")) : 0;
        data.mineStonesBroken = tag.contains("MineStonesBroken") ? Math.max(0, tag.getInt("MineStonesBroken")) : 0;
        data.mineOresBroken = tag.contains("MineOresBroken") ? Math.max(0, tag.getInt("MineOresBroken")) : 0;
        data.mineGemOresBroken = tag.contains("MineGemOresBroken") ? Math.max(0, tag.getInt("MineGemOresBroken")) : 0;
        data.mineMineralNodesBroken = tag.contains("MineMineralNodesBroken") ? Math.max(0, tag.getInt("MineMineralNodesBroken")) : 0;
        data.mineBlocksBombed = tag.contains("MineBlocksBombed") ? Math.max(0, tag.getInt("MineBlocksBombed")) : 0;
        data.giftsGivenTotal = tag.contains("GiftsGivenTotal") ? Math.max(0, tag.getInt("GiftsGivenTotal")) : 0;
        data.combatDeaths = tag.contains("CombatDeaths") ? Math.max(0, tag.getInt("CombatDeaths")) : 0;
        data.passOuts2Am = tag.contains("PassOuts2Am") ? Math.max(0, tag.getInt("PassOuts2Am")) : 0;
        data.passOutsExhaustion = tag.contains("PassOutsExhaustion") ? Math.max(0, tag.getInt("PassOutsExhaustion")) : 0;
        data.animalProductsCollectedTotal = tag.contains("AnimalProductsCollectedTotal") ? Math.max(0, tag.getInt("AnimalProductsCollectedTotal")) : 0;
        data.basementShortsStolen = tag.contains("BasementShortsStolen") ? Math.max(0, tag.getInt("BasementShortsStolen")) : 0;
        if (tag.contains("LeaderboardPeriodStats", 10)) {
            CompoundTag statsTag = tag.getCompound("LeaderboardPeriodStats");
            data.leaderboardDayKey = statsTag.contains("DayKey") ? statsTag.getInt("DayKey") : Integer.MIN_VALUE;
            data.leaderboardWeekKey = statsTag.contains("WeekKey") ? statsTag.getInt("WeekKey") : Integer.MIN_VALUE;
            data.leaderboardSeasonKey = statsTag.contains("SeasonKey") ? statsTag.getInt("SeasonKey") : Integer.MIN_VALUE;
            readLeaderboardValues(statsTag, "DayValues", data.leaderboardDayValues);
            readLeaderboardValues(statsTag, "WeekValues", data.leaderboardWeekValues);
            readLeaderboardValues(statsTag, "SeasonValues", data.leaderboardSeasonValues);
        }

        data.stats.clear();
        if (tag.contains("Stats", 10)) {
            CompoundTag statsTag = tag.getCompound("Stats");
            for (String key : statsTag.getAllKeys()) {
                int value = Math.max(0, statsTag.getInt(key));
                if (!key.isBlank() && value > 0) {
                    data.stats.put(key, value);
                }
            }
        }

        data.ticketPrizesClaimed = tag.contains("TicketPrizesClaimed") ? Math.max(0, tag.getInt("TicketPrizesClaimed")) : 0;
        data.specialOrderPrizeTickets = tag.contains("SpecialOrderPrizeTickets") ? Math.max(0, tag.getInt("SpecialOrderPrizeTickets")) : 0;


        // 邮件标记
        if (tag.contains("MailFlags", 9)) { // 9 = TAG_List
            ListTag mailList = tag.getList("MailFlags", 8); // 8 = TAG_String
            for (int i = 0; i < mailList.size(); i++) {
                String flag = mailList.getString(i);
                if (!flag.isBlank()) data.mailFlags.add(flag);
            }
        }

        // 邮箱队列
        if (tag.contains("Mailbox", 9)) {
            ListTag mboxList = tag.getList("Mailbox", 8);
            for (int i = 0; i < mboxList.size(); i++) {
                String mid = mboxList.getString(i);
                if (!mid.isBlank()) data.mailbox.add(mid);
            }
        }
        if (tag.contains("MailForTomorrow", 9)) {
            ListTag mftList = tag.getList("MailForTomorrow", 8);
            for (int i = 0; i < mftList.size(); i++) {
                String mid = mftList.getString(i);
                if (!mid.isBlank()) data.mailForTomorrow.add(mid);
            }
        }
        if (tag.contains("MailFlagsForTomorrow", 10)) {
            CompoundTag mfftTag = tag.getCompound("MailFlagsForTomorrow");
            for (String flag : mfftTag.getAllKeys()) {
                if (!flag.isBlank()) {
                    data.mailFlagsForTomorrow.put(flag, mfftTag.getInt(flag));
                }
            }
        }

        // 怪物讨伐击杀计数
        if (tag.contains("MonsterKillCounts", 10)) { // 10 = TAG_Compound
            CompoundTag kills = tag.getCompound("MonsterKillCounts");
            for (String key : kills.getAllKeys()) {
                data.monsterKillCounts.put(key, kills.getInt(key));
            }
        }
        // 已领取的讨伐奖励
        if (tag.contains("ClaimedSlayerRewards", 9)) {
            ListTag claimed = tag.getList("ClaimedSlayerRewards", 8);
            for (int i = 0; i < claimed.size(); i++) {
                String key = claimed.getString(i);
                if (!key.isBlank()) data.claimedSlayerRewards.add(key);
            }
        }
        // 特殊物品去重
        if (tag.contains("SpecialItems", 9)) {
            ListTag specials = tag.getList("SpecialItems", 8);
            for (int i = 0; i < specials.size(); i++) {
                String id = specials.getString(i);
                if (!id.isBlank()) data.specialItems.add(id);
            }
        }

        if (tag.contains("SecretNotesSeen", Tag.TAG_LIST)) {
            ListTag notes = tag.getList("SecretNotesSeen", Tag.TAG_STRING);
            for (int i = 0; i < notes.size(); i++) {
                String id = notes.getString(i);
                if (!id.isBlank()) data.secretNotesSeen.add(id);
            }
        }
        if (tag.contains("RevealedGiftTastes", Tag.TAG_LIST)) {
            ListTag reveals = tag.getList("RevealedGiftTastes", Tag.TAG_STRING);
            for (int i = 0; i < reveals.size(); i++) {
                String reveal = reveals.getString(i);
                if (!reveal.isBlank()) data.revealedGiftTastes.add(reveal);
            }
        }

        // 工具升级
        // 装备
        data.equippedLeftRing = loadEquipmentStack(tag, "EquippedLeftRingStack", "EquippedLeftRing", registries);
        data.equippedRightRing = loadEquipmentStack(tag, "EquippedRightRingStack", "EquippedRightRing", registries);
        data.equippedBoots = loadEquipmentStack(tag, "EquippedBootsStack", "EquippedBoots", registries);
        data.equippedTrinket = tag.contains("EquippedTrinket", Tag.TAG_COMPOUND)
            ? com.stardew.craft.item.trinket.StardewTrinketItem.loadStackFromTag(tag.getCompound("EquippedTrinket"))
            : net.minecraft.world.item.ItemStack.EMPTY;
        data.equippedHat = tag.contains("EquippedHat") ? tag.getString("EquippedHat") : "";
        data.equippedShirt = tag.contains("EquippedShirt") ? tag.getString("EquippedShirt") : "";
        data.equippedPants = tag.contains("EquippedPants") ? tag.getString("EquippedPants") : "";
        data.lastPhoenixReviveDay = tag.contains("LastPhoenixReviveDay") ? tag.getLong("LastPhoenixReviveDay") : -1;

        data.toolBeingUpgraded = tag.contains("ToolBeingUpgraded") ? tag.getString("ToolBeingUpgraded") : "";
        data.daysLeftForToolUpgrade = tag.contains("DaysLeftForToolUpgrade") ? tag.getInt("DaysLeftForToolUpgrade") : 0;
        data.toolUpgradeNotified = tag.getBoolean("ToolUpgradeNotified");

        // 巫师塔枢纽
        // 任务系统
        if (tag.contains("QuestManager", 10)) {
            data.questManager.load(tag.getCompound("QuestManager"));
        }

        data.firstJoinDay = tag.contains("FirstJoinDay") ? tag.getInt("FirstJoinDay") : -1;

        data.wizardQuestComplete = tag.getBoolean("WizardQuestComplete");
        data.wizardFirstMet = tag.getBoolean("WizardFirstMet");
        data.starterToolsGiven = tag.getBoolean("StarterToolsGiven");
        data.bilibiliRewardClaimed = tag.getBoolean("BilibiliRewardClaimed");
        data.mine100StardropCompensationProcessed = tag.getBoolean("Mine100StardropCompensationProcessed");
        data.handledPregenRelocationVersion = tag.contains("HandledPregenRelocationVersion") ? tag.getInt("HandledPregenRelocationVersion") : 0;
        data.lastGotDesertFestivalRatingPrizeFromMarlon = tag.contains("LastGotDesertFestivalRatingPrizeFromMarlon")
            ? tag.getInt("LastGotDesertFestivalRatingPrizeFromMarlon")
            : Integer.MIN_VALUE;
        data.desertFestivalMarlonChallengeDay = tag.contains("DesertFestivalMarlonChallengeDay")
            ? tag.getInt("DesertFestivalMarlonChallengeDay")
            : Integer.MIN_VALUE;
        data.desertFestivalMarlonChallengeId = tag.contains("DesertFestivalMarlonChallengeId")
            ? tag.getString("DesertFestivalMarlonChallengeId")
            : "";
        data.desertFestivalMarlonChallengeProgress = tag.contains("DesertFestivalMarlonChallengeProgress")
            ? Math.max(0, tag.getInt("DesertFestivalMarlonChallengeProgress"))
            : 0;
        data.desertFestivalMarlonChallengeRewardClaimed = tag.getBoolean("DesertFestivalMarlonChallengeRewardClaimed");
        if (tag.contains("OverworldReturnX")) {
            data.overworldReturnPos = new BlockPos(
                tag.getInt("OverworldReturnX"),
                tag.getInt("OverworldReturnY"),
                tag.getInt("OverworldReturnZ")
            );
        }
        if (tag.contains("WizardSourceDim")) {
            String dimStr = tag.getString("WizardSourceDim");
            if (!dimStr.isBlank()) {
                @SuppressWarnings("null")
                var parsedKey = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimStr));
                data.wizardSourceDimension = parsedKey;
            }
        }

        // 安全钳制：避免异常值导致越界（Buff 允许 maxEnergy 临时上升）
        data.maxHealth = Math.max(100, data.maxHealth);
        data.maxEnergy = Math.max(270, data.maxEnergy);
        int effectiveMaxEnergy = data.maxEnergy + Math.max(0, data.tempMaxEnergyBonus);
        data.health = Math.max(0, Math.min(data.health, data.maxHealth));
        data.energy = Math.max(MIN_ENERGY, Math.min(data.energy, effectiveMaxEnergy));

        data.repairMissingProfessionChoices();

        data.dirty = false;
        
        return data;
    }

    private static void readLeaderboardValues(CompoundTag statsTag, String key, Map<String, Long> target) {
        target.clear();
        if (!statsTag.contains(key, 9)) {
            return;
        }
        ListTag list = statsTag.getList(key, 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String metric = entry.getString("Metric");
            long value = Math.max(0L, entry.getLong("Value"));
            if (!metric.isBlank() && value > 0L) {
                target.put(metric, value);
            }
        }
    }

    private static void writeLeaderboardValues(CompoundTag statsTag, String key, Map<String, Long> values) {
        ListTag list = new ListTag();
        for (Map.Entry<String, Long> entry : values.entrySet()) {
            String metric = entry.getKey();
            long value = entry.getValue() == null ? 0L : entry.getValue();
            if (metric == null || metric.isBlank() || value <= 0L) {
                continue;
            }
            CompoundTag valueTag = new CompoundTag();
            valueTag.putString("Metric", metric);
            valueTag.putLong("Value", value);
            list.add(valueTag);
        }
        statsTag.put(key, list);
    }
    
    /**
     * 保存到NBT
     */
    @SuppressWarnings("null")
    public CompoundTag toNBT() {
        return toNBT(null);
    }

    @SuppressWarnings("null")
    public CompoundTag toNBT(@Nullable net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        
        // 基础属性
        tag.putInt("Health", health);
        tag.putInt("MaxHealth", maxHealth);
        tag.putFloat("Energy", energy);
        tag.putInt("MaxEnergy", maxEnergy);
        tag.putInt("StardropsConsumed", stardropsConsumed);
        tag.putBoolean("Exhausted", exhausted);
        tag.putInt("Money", money);
        tag.putInt("FairStarTokens", fairStarTokens);
        tag.putInt("LastFairGrillBurgerDateKey", lastFairGrillBurgerDateKey);
        tag.putLong("TotalMoneyEarned", totalMoneyEarned);
        tag.putString("LastKnownName", lastKnownName == null ? "" : lastKnownName);
        tag.putInt("Gender", gender);
        tag.putString("PreferredName", preferredName == null ? "" : preferredName);
        tag.putString("FavoriteThing", favoriteThing == null ? "" : favoriteThing);

        // 晕倒/死亡系统
        tag.putBoolean("PassedOutFromCombat", passedOutFromCombat);
        if (!itemsLostLastDeath.isEmpty()) {
            ListTag lostItemsTag = new ListTag();
            for (net.minecraft.world.item.ItemStack stack : itemsLostLastDeath) {
                CompoundTag itemTag = new CompoundTag();
                itemTag.putString("Id", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                itemTag.putInt("Count", stack.getCount());
                lostItemsTag.add(itemTag);
            }
            tag.put("ItemsLostLastDeath", lostItemsTag);
        }
        
        // 技能等级
        tag.putIntArray("SkillLevels", skillLevels);
        
        // 技能经验
        tag.putIntArray("Experience", experiencePoints);
        
        // 职业
        int[] profsArray = professions.stream().mapToInt(Integer::intValue).toArray();
        tag.putIntArray("Professions", profsArray);

        ListTag pendingChoicesTag = new ListTag();
        for (ProfessionChoicePrompt prompt : pendingProfessionChoices) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("SkillId", prompt.skill().getId());
            entry.putInt("Level", prompt.level());
            pendingChoicesTag.add(entry);
        }
        tag.put("PendingProfessionChoices", pendingChoicesTag);

        // pendingNewLevels（白天获得的升级，睡觉时消费）
        ListTag newLevelsTag = new ListTag();
        for (SkillLevelUp levelUp : pendingNewLevels) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("SkillId", levelUp.skill().getId());
            entry.putInt("Level", levelUp.newLevel());
            newLevelsTag.add(entry);
        }
        tag.put("PendingNewLevels", newLevelsTag);
        
        // 元数据
        tag.putLong("LastSyncTime", lastSyncTime);

        // 精通系统
        tag.putLong("MasteryExp", masteryExp);
        tag.putInt("MasteryLevelsSpent", masteryLevelsSpent);
        byte[] claimedBits = new byte[5];
        for (int i = 0; i < 5; i++) claimedBits[i] = (byte) (claimedMasteryRewards[i] ? 1 : 0);
        tag.putByteArray("ClaimedMasteryRewards", claimedBits);
        tag.putBoolean("GotMasteryHint", gotMasteryHint);
        tag.putBoolean("VisitedMasteryCave", visitedMasteryCave);
        tag.putInt("UnlockedTrinketSlots", unlockedTrinketSlots);
        tag.putBoolean("BlessedByStatueToday", blessedByStatueToday);
        tag.putInt("BlessingOfWatersRemaining", blessingOfWatersRemaining);
        if (!pendingMasteryLevelUps.isEmpty()) {
            int[] mlu = new int[pendingMasteryLevelUps.size()];
            for (int i = 0; i < mlu.length; i++) mlu[i] = pendingMasteryLevelUps.get(i);
            tag.putIntArray("PendingMasteryLevelUps", mlu);
        }

        // 运气
        tag.putDouble("DailyLuck", dailyLuck);
        tag.putInt("DailyLuckDateKey", dailyLuckDateKey);

        // 临时Buff
        tag.putInt("TempFishingLevelBonus", tempFishingLevelBonus);
        tag.putLong("TempFishingLevelBonusEndTick", tempFishingLevelBonusEndTick);
        tag.putInt("TempLuckBonus", tempLuckBonus);
        tag.putLong("TempLuckBonusEndTick", tempLuckBonusEndTick);
        tag.putInt("TempMaxEnergyBonus", tempMaxEnergyBonus);
        tag.putLong("TempMaxEnergyBonusEndTick", tempMaxEnergyBonusEndTick);
        tag.putInt("TempFarmingLevelBonus", tempFarmingLevelBonus);
        tag.putLong("TempFarmingLevelBonusEndTick", tempFarmingLevelBonusEndTick);
        tag.putInt("TempForagingLevelBonus", tempForagingLevelBonus);
        tag.putLong("TempForagingLevelBonusEndTick", tempForagingLevelBonusEndTick);
        tag.putInt("TempMiningLevelBonus", tempMiningLevelBonus);
        tag.putLong("TempMiningLevelBonusEndTick", tempMiningLevelBonusEndTick);
        tag.putInt("TempAttackBonus", tempAttackBonus);
        tag.putLong("TempAttackBonusEndTick", tempAttackBonusEndTick);
        tag.putInt("TempDefenseBonus", tempDefenseBonus);
        tag.putLong("TempDefenseBonusEndTick", tempDefenseBonusEndTick);
        tag.putInt("TempMagneticRadiusBonus", tempMagneticRadiusBonus);
        tag.putLong("TempMagneticRadiusBonusEndTick", tempMagneticRadiusBonusEndTick);

        ListTag unlockedWallpapers = new ListTag();
        for (String styleId : unlockedWallpaperStyles) {
            unlockedWallpapers.add(StringTag.valueOf(styleId));
        }
        tag.put("UnlockedWallpaperStyles", unlockedWallpapers);

        ListTag unlockedFloorings = new ListTag();
        for (String styleId : unlockedFlooringStyles) {
            unlockedFloorings.add(StringTag.valueOf(styleId));
        }
        tag.put("UnlockedFlooringStyles", unlockedFloorings);

        ListTag unlockedRecipesTag = new ListTag();
        for (String recipeId : unlockedRecipes) {
            unlockedRecipesTag.add(StringTag.valueOf(recipeId));
        }
        tag.put("UnlockedRecipes", unlockedRecipesTag);

        ListTag recipeCraftCountsTag = new ListTag();
        for (Map.Entry<String, Integer> entry : recipeCraftCounts.entrySet()) {
            String recipeId = entry.getKey();
            int count = entry.getValue() == null ? 0 : entry.getValue();
            if (recipeId == null || recipeId.isBlank() || count <= 0) {
                continue;
            }
            CompoundTag recipeTag = new CompoundTag();
            recipeTag.putString("Recipe", recipeId);
            recipeTag.putInt("Count", count);
            recipeCraftCountsTag.add(recipeTag);
        }
        tag.put("RecipeCraftCounts", recipeCraftCountsTag);
        tag.putInt("QueenOfSauceWatchDay", queenOfSauceWatchDay);
        tag.putString("QueenOfSauceRecipeId", queenOfSauceRecipeId == null ? "" : queenOfSauceRecipeId);

        ListTag fishCounts = new ListTag();
        for (Map.Entry<String, Integer> entry : fishCatchCounts.entrySet()) {
            String itemId = entry.getKey();
            int count = entry.getValue() == null ? 0 : entry.getValue();
            if (itemId == null || itemId.isBlank() || count <= 0) {
                continue;
            }
            CompoundTag fishCountTag = new CompoundTag();
            fishCountTag.putString("Item", itemId);
            fishCountTag.putInt("Count", count);
            fishCounts.add(fishCountTag);
        }
        tag.put("FishCatchCounts", fishCounts);
        tag.putInt("PreciseFishCaught", preciseFishCaught);

        ListTag shippedBasicTag = new ListTag();
        for (String itemId : shippedBasic) {
            if (itemId != null && !itemId.isBlank()) {
                shippedBasicTag.add(StringTag.valueOf(itemId));
            }
        }
        tag.put("ShippedBasic", shippedBasicTag);

        ListTag itemsShippedTag = new ListTag();
        for (Map.Entry<String, Integer> entry : itemsShipped.entrySet()) {
            String itemId = entry.getKey();
            int count = entry.getValue() == null ? 0 : entry.getValue();
            if (itemId == null || itemId.isBlank() || count <= 0) {
                continue;
            }
            CompoundTag shippedTag = new CompoundTag();
            shippedTag.putString("Item", itemId);
            shippedTag.putInt("Count", count);
            itemsShippedTag.add(shippedTag);
        }
        tag.put("ItemsShipped", itemsShippedTag);
        tag.putLong("TotalShippingGold", totalShippingGold);

        ListTag activeRules = new ListTag();
        for (String rule : activeSpecialOrderRules) {
            if (rule != null && !rule.isBlank()) {
                activeRules.add(StringTag.valueOf(rule));
            }
        }
        tag.put("ActiveSpecialOrderRules", activeRules);

        tag.putInt("TrashCansChecked", trashCansChecked);
        tag.putInt("MineBlocksBrokenTotal", mineBlocksBrokenTotal);
        tag.putInt("MineStonesBroken", mineStonesBroken);
        tag.putInt("MineOresBroken", mineOresBroken);
        tag.putInt("MineGemOresBroken", mineGemOresBroken);
        tag.putInt("MineMineralNodesBroken", mineMineralNodesBroken);
        tag.putInt("MineBlocksBombed", mineBlocksBombed);
        tag.putInt("GiftsGivenTotal", giftsGivenTotal);
        tag.putInt("CombatDeaths", combatDeaths);
        tag.putInt("PassOuts2Am", passOuts2Am);
        tag.putInt("PassOutsExhaustion", passOutsExhaustion);
        tag.putInt("AnimalProductsCollectedTotal", animalProductsCollectedTotal);
        tag.putInt("BasementShortsStolen", basementShortsStolen);
        CompoundTag periodStatsTag = new CompoundTag();
        periodStatsTag.putInt("DayKey", leaderboardDayKey);
        periodStatsTag.putInt("WeekKey", leaderboardWeekKey);
        periodStatsTag.putInt("SeasonKey", leaderboardSeasonKey);
        writeLeaderboardValues(periodStatsTag, "DayValues", leaderboardDayValues);
        writeLeaderboardValues(periodStatsTag, "WeekValues", leaderboardWeekValues);
        writeLeaderboardValues(periodStatsTag, "SeasonValues", leaderboardSeasonValues);
        tag.put("LeaderboardPeriodStats", periodStatsTag);

        if (!stats.isEmpty()) {
            CompoundTag statsTag = new CompoundTag();
            for (Map.Entry<String, Integer> entry : stats.entrySet()) {
                String key = entry.getKey();
                int value = entry.getValue() == null ? 0 : entry.getValue();
                if (key != null && !key.isBlank() && value > 0) {
                    statsTag.putInt(key, value);
                }
            }
            tag.put("Stats", statsTag);
        }

        tag.putInt("TicketPrizesClaimed", ticketPrizesClaimed);
        tag.putInt("SpecialOrderPrizeTickets", specialOrderPrizeTickets);

        // 邮件标记
        if (!mailFlags.isEmpty()) {
            ListTag mailList = new ListTag();
            for (String flag : mailFlags) {
                mailList.add(StringTag.valueOf(flag));
            }
            tag.put("MailFlags", mailList);
        }

        // 邮箱队列
        if (!mailbox.isEmpty()) {
            ListTag mboxList = new ListTag();
            for (String mid : mailbox) {
                mboxList.add(StringTag.valueOf(mid));
            }
            tag.put("Mailbox", mboxList);
        }
        if (!mailForTomorrow.isEmpty()) {
            ListTag mftList = new ListTag();
            for (String mid : mailForTomorrow) {
                mftList.add(StringTag.valueOf(mid));
            }
            tag.put("MailForTomorrow", mftList);
        }
        if (!mailFlagsForTomorrow.isEmpty()) {
            CompoundTag mfftTag = new CompoundTag();
            for (java.util.Map.Entry<String, Integer> entry : mailFlagsForTomorrow.entrySet()) {
                mfftTag.putInt(entry.getKey(), entry.getValue());
            }
            tag.put("MailFlagsForTomorrow", mfftTag);
        }

        // 怪物讨伐击杀计数
        if (!monsterKillCounts.isEmpty()) {
            CompoundTag kills = new CompoundTag();
            for (var entry : monsterKillCounts.entrySet()) {
                kills.putInt(entry.getKey(), entry.getValue());
            }
            tag.put("MonsterKillCounts", kills);
        }
        // 已领取的讨伐奖励
        if (!claimedSlayerRewards.isEmpty()) {
            ListTag claimed = new ListTag();
            for (String key : claimedSlayerRewards) {
                claimed.add(StringTag.valueOf(key));
            }
            tag.put("ClaimedSlayerRewards", claimed);
        }
        // 特殊物品去重
        if (!specialItems.isEmpty()) {
            ListTag specials = new ListTag();
            for (String id : specialItems) {
                specials.add(StringTag.valueOf(id));
            }
            tag.put("SpecialItems", specials);
        }

        if (!secretNotesSeen.isEmpty()) {
            ListTag notes = new ListTag();
            secretNotesSeen.stream().sorted().forEach(id -> notes.add(StringTag.valueOf(id)));
            tag.put("SecretNotesSeen", notes);
        }
        if (!revealedGiftTastes.isEmpty()) {
            ListTag reveals = new ListTag();
            revealedGiftTastes.stream().sorted().forEach(value -> reveals.add(StringTag.valueOf(value)));
            tag.put("RevealedGiftTastes", reveals);
        }

        // 装备
        String leftRingId = getEquippedLeftRing();
        String rightRingId = getEquippedRightRing();
        String bootsId = getEquippedBoots();
        if (!leftRingId.isEmpty()) tag.putString("EquippedLeftRing", leftRingId);
        if (!rightRingId.isEmpty()) tag.putString("EquippedRightRing", rightRingId);
        if (!bootsId.isEmpty()) tag.putString("EquippedBoots", bootsId);
        if (registries != null) {
            if (!equippedLeftRing.isEmpty()) tag.put("EquippedLeftRingStack", equippedLeftRing.save(registries));
            if (!equippedRightRing.isEmpty()) tag.put("EquippedRightRingStack", equippedRightRing.save(registries));
            if (!equippedBoots.isEmpty()) tag.put("EquippedBootsStack", equippedBoots.save(registries));
        }
        if (!equippedTrinket.isEmpty()) tag.put("EquippedTrinket", com.stardew.craft.item.trinket.StardewTrinketItem.saveStackToTag(equippedTrinket));
        if (!equippedHat.isEmpty()) tag.putString("EquippedHat", equippedHat);
        if (!equippedShirt.isEmpty()) tag.putString("EquippedShirt", equippedShirt);
        if (!equippedPants.isEmpty()) tag.putString("EquippedPants", equippedPants);
        if (lastPhoenixReviveDay >= 0) tag.putLong("LastPhoenixReviveDay", lastPhoenixReviveDay);

        // 工具升级
        tag.putString("ToolBeingUpgraded", toolBeingUpgraded != null ? toolBeingUpgraded : "");
        tag.putInt("DaysLeftForToolUpgrade", daysLeftForToolUpgrade);
        tag.putBoolean("ToolUpgradeNotified", toolUpgradeNotified);

        // 任务系统
        tag.put("QuestManager", questManager.save());

        // 巫师塔枢纽
        tag.putInt("FirstJoinDay", firstJoinDay);

        tag.putBoolean("WizardQuestComplete", wizardQuestComplete);
        tag.putBoolean("WizardFirstMet", wizardFirstMet);
        tag.putBoolean("StarterToolsGiven", starterToolsGiven);
        tag.putBoolean("BilibiliRewardClaimed", bilibiliRewardClaimed);
        tag.putBoolean("Mine100StardropCompensationProcessed", mine100StardropCompensationProcessed);
        tag.putInt("HandledPregenRelocationVersion", handledPregenRelocationVersion);
        tag.putInt("LastGotDesertFestivalRatingPrizeFromMarlon", lastGotDesertFestivalRatingPrizeFromMarlon);
        tag.putInt("DesertFestivalMarlonChallengeDay", desertFestivalMarlonChallengeDay);
        if (!desertFestivalMarlonChallengeId.isEmpty()) {
            tag.putString("DesertFestivalMarlonChallengeId", desertFestivalMarlonChallengeId);
        }
        tag.putInt("DesertFestivalMarlonChallengeProgress", desertFestivalMarlonChallengeProgress);
        tag.putBoolean("DesertFestivalMarlonChallengeRewardClaimed", desertFestivalMarlonChallengeRewardClaimed);
        if (overworldReturnPos != null) {
            tag.putInt("OverworldReturnX", overworldReturnPos.getX());
            tag.putInt("OverworldReturnY", overworldReturnPos.getY());
            tag.putInt("OverworldReturnZ", overworldReturnPos.getZ());
        }
        if (wizardSourceDimension != null) {
            tag.putString("WizardSourceDim", wizardSourceDimension.location().toString());
        }
        
        return tag;
    }
    
    // ============ 经验和等级相关方法 ============
    
    /**
     * 添加经验值 — SDV parity: 等级立即生效（Farmer.gainExperience）
     * @return 是否升级
     */
    public boolean addExperience(SkillType skill, int amount) {
        if (amount <= 0) return false;

        int skillId = skill.getId();
        int oldLevel = skillLevels[skillId];

        // SDV parity (Farmer.cs:3041): original checks Farmer.Level >= 25 before converting
        // skill exp to mastery exp. This mod has the five visible skills, so that maps to 5x Lv10.
        if (hasAllSkillsMaxed()) {
            addMasteryExp(MasteryProgress.masteryExpFromSkillGain(skillId, amount));
        }

        experiencePoints[skillId] += amount;
        addLeaderboardPeriodValue(skillMetricId(skill), amount);
        int newLevel = Math.min(10, calculateLevel(experiencePoints[skillId]));

        if (newLevel > oldLevel) {
            skillLevels[skillId] = newLevel;
            for (int lvl = oldLevel + 1; lvl <= newLevel; lvl++) {
                pendingNewLevels.add(new SkillLevelUp(skill, lvl));
                // 战斗升级立即加血上限（SDV parity）
                if (skill == SkillType.COMBAT && lvl != 5 && lvl != 10) {
                    maxHealth += 5;
                    health = Math.min(health + 5, maxHealth);
                }
            }
        }

        markDirty();
        return newLevel > oldLevel;
    }

    public int getLevelByExperience(SkillType skill) {
        return calculateLevel(experiencePoints[skill.getId()]);
    }

    // ============ Mastery API ============

    public long getMasteryExp() { return masteryExp; }

    public int getMasteryLevel() { return MasteryProgress.currentLevel(masteryExp); }

    public int getMasteryLevelsSpent() { return masteryLevelsSpent; }

    public int getUnspentMasteryLevels() {
        return MasteryProgress.unspentLevels(masteryExp, masteryLevelsSpent);
    }

    public boolean hasAllSkillsMaxed() {
        for (SkillType skill : SkillType.values()) {
            if (getRawSkillLevel(skill) < 10) return false;
        }
        return true;
    }

    public int getMaxedSkillCount() {
        int count = 0;
        for (SkillType skill : SkillType.values()) {
            if (getRawSkillLevel(skill) >= 10) count++;
        }
        return count;
    }

    public boolean hasClaimedMasteryReward(SkillType skill) {
        return claimedMasteryRewards[skill.getId()];
    }

    /** Mastery2 != 0 / Mastery1 != 0 等行为判定 — SDV stats.Get(StatKeys.Mastery(skill)) != 0。 */
    public boolean hasMastery(SkillType skill) {
        return claimedMasteryRewards[skill.getId()];
    }

    public boolean isGotMasteryHint() { return gotMasteryHint; }
    public void setGotMasteryHint(boolean v) { gotMasteryHint = v; markDirty(); }

    public boolean hasVisitedMasteryCave() { return visitedMasteryCave; }
    public void setVisitedMasteryCave(boolean v) { visitedMasteryCave = v; markDirty(); }

    public int getUnlockedTrinketSlots() { return unlockedTrinketSlots; }

    public boolean isBlessedByStatueToday() { return blessedByStatueToday; }
    public void setBlessedByStatueToday(boolean v) { blessedByStatueToday = v; markDirty(); }

    public int getBlessingOfWatersRemaining() { return Math.max(0, blessingOfWatersRemaining); }
    public void setBlessingOfWatersRemaining(int value) { blessingOfWatersRemaining = Math.max(0, value); markDirty(); }
    public int consumeBlessingOfWatersUse() {
        if (blessingOfWatersRemaining <= 0) return 0;
        blessingOfWatersRemaining--;
        markDirty();
        return blessingOfWatersRemaining;
    }

    public List<Integer> drainPendingMasteryLevelUps() {
        if (pendingMasteryLevelUps.isEmpty()) return Collections.emptyList();
        List<Integer> out = new ArrayList<>(pendingMasteryLevelUps);
        pendingMasteryLevelUps.clear();
        markDirty();
        return out;
    }

    /** 累计 mastery exp，并在跨越阈值时推入 pendingMasteryLevelUps（供客户端 UI 提示）。 */
    public void addMasteryExp(long amount) {
        if (amount <= 0) return;
        int oldLevel = MasteryProgress.currentLevel(masteryExp);
        masteryExp += amount;
        int newLevel = MasteryProgress.currentLevel(masteryExp);
        for (int lvl = oldLevel + 1; lvl <= newLevel; lvl++) {
            pendingMasteryLevelUps.add(lvl);
        }
        markDirty();
    }

    /** 服务端发奖路径调用：标记技能 pedestal 已领 + 自增 spent。Combat 额外解锁饰品槽。 */
    public boolean claimMasteryReward(SkillType skill) {
        if (claimedMasteryRewards[skill.getId()]) return false;
        if (getUnspentMasteryLevels() <= 0) return false;
        claimedMasteryRewards[skill.getId()] = true;
        masteryLevelsSpent++;
        if (skill == SkillType.COMBAT) {
            unlockedTrinketSlots = Math.max(unlockedTrinketSlots, 1);
        }
        markDirty();
        return true;
    }

    /** 全部 5 个奖励都已领取，用于触发完成 cutscene。 */
    public boolean hasClaimedAllMasteryRewards() {
        for (boolean b : claimedMasteryRewards) {
            if (!b) return false;
        }
        return true;
    }

    /**
     * 消费白天积累的 pendingNewLevels（睡觉时调用）。
     * SDV parity: skillLevels 已在 addExperience 中实时更新，
     * 此方法只处理职业选择提示并返回列表供升级动画使用。
     */
    public List<SkillLevelUp> applyPendingSkillLevelUps() {
        List<SkillLevelUp> applied = new ArrayList<>(pendingNewLevels);

        for (SkillLevelUp levelUp : applied) {
            SkillType skill = levelUp.skill();
            int level = levelUp.newLevel();
            if (level == 5 && !hasLevel5Profession(skill) && !hasPendingProfessionChoice(skill, 5)) {
                pendingProfessionChoices.add(new ProfessionChoicePrompt(skill, 5));
            }
            if (level == 10 && hasLevel5Profession(skill) && !hasLevel10Profession(skill) && !hasPendingProfessionChoice(skill, 10)) {
                pendingProfessionChoices.add(new ProfessionChoicePrompt(skill, 10));
            }
        }

        pendingNewLevels.clear();
        if (!applied.isEmpty()) {
            markDirty();
        }
        return applied;
    }
    
    /**
     * 根据经验值计算等级
     */
    private int calculateLevel(int exp) {
        for (int level = 10; level >= 1; level--) {
            if (exp >= EXP_TO_LEVEL[level]) {
                return level;
            }
        }
        return 0;
    }
    
    /**
     * 获取升级所需经验
     */
    public int getExpForNextLevel(SkillType skill) {
        int level = skillLevels[skill.getId()];
        if (level >= 10) return 0;
        return EXP_TO_LEVEL[level + 1];
    }
    
    /**
     * 获取当前等级的进度（0.0-1.0）
     */
    public float getLevelProgress(SkillType skill) {
        int skillId = skill.getId();
        int level = skillLevels[skillId];
        if (level >= 10) return 1.0f;
        
        int currentLevelExp = EXP_TO_LEVEL[level];
        int nextLevelExp = EXP_TO_LEVEL[level + 1];
        int currentExp = experiencePoints[skillId];
        
        float progress = (float)(currentExp - currentLevelExp) / (nextLevelExp - currentLevelExp);
        if (progress < 0.0f) {
            return 0.0f;
        }
        return Math.min(progress, 1.0f);
    }
    
    // ============ 职业相关方法 ============
    
    /**
     * 添加职业
     */
    public boolean addProfession(ProfessionType profession) {
        if (profession == null || professions.contains(profession.getId())) {
            return false;
        }
        professions.add(profession.getId());

        // 应用职业效果
        applyProfessionEffect(profession);
        markDirty();
        return true;
    }
    
    /**
     * 检查是否拥有职业
     */
    public boolean hasProfession(ProfessionType profession) {
        return professions.contains(profession.getId());
    }

    public boolean removeProfession(ProfessionType profession) {
        if (profession == null || !professions.remove(Integer.valueOf(profession.getId()))) {
            return false;
        }
        revertProfessionEffect(profession);
        markDirty();
        return true;
    }

    public boolean clearProfessions() {
        if (professions.isEmpty()) {
            return false;
        }
        List<Integer> copy = new ArrayList<>(professions);
        professions.clear();
        for (int professionId : copy) {
            ProfessionType profession = ProfessionType.fromId(professionId);
            if (profession != null) {
                revertProfessionEffect(profession);
            }
        }
        pendingProfessionChoices.clear();
        repairMissingProfessionChoices();
        markDirty();
        return true;
    }

    public boolean canRespecProfessions(SkillType skill) {
        return skill != null
            && getRawSkillLevel(skill) >= 5
            && !hasPendingNewLevel(skill, 5)
            && !hasPendingNewLevel(skill, 10)
            && !hasPendingProfessionChoice(skill, 5)
            && !hasPendingProfessionChoice(skill, 10);
    }

    public boolean respecProfessionsForSkill(SkillType skill) {
        if (!canRespecProfessions(skill)) {
            return false;
        }

        boolean changed = false;
        List<Integer> copy = new ArrayList<>(professions);
        for (int professionId : copy) {
            ProfessionType profession = ProfessionType.fromId(professionId);
            if (profession != null && profession.getSkillType() == skill
                    && professions.remove(Integer.valueOf(professionId))) {
                revertProfessionEffect(profession);
                changed = true;
            }
        }

        int rawLevel = getRawSkillLevel(skill);
        if (rawLevel >= 5) {
            addPendingNewLevel(skill, 5);
        }
        if (rawLevel >= 10) {
            addPendingNewLevel(skill, 10);
        }
        markDirty();
        return true;
    }

    public List<ProfessionChoicePrompt> getPendingProfessionChoices() {
        return new ArrayList<>(pendingProfessionChoices);
    }

    public boolean hasPendingProfessionChoices() {
        return !pendingProfessionChoices.isEmpty();
    }

    public boolean choosePendingProfession(ProfessionType selectedProfession) {
        if (selectedProfession == null || pendingProfessionChoices.isEmpty()) {
            return false;
        }

        ProfessionChoicePrompt prompt = pendingProfessionChoices.get(0);
        SkillType promptSkill = prompt.skill();
        int promptLevel = prompt.level();

        if (selectedProfession.getSkillType() != promptSkill || selectedProfession.getRequiredLevel() != promptLevel) {
            return false;
        }

        if (promptLevel == 5) {
            ProfessionType[] options = ProfessionType.getLevel5Options(promptSkill);
            if (selectedProfession != options[0] && selectedProfession != options[1]) {
                return false;
            }
        } else if (promptLevel == 10) {
            ProfessionType level5Profession = getLevel5Profession(promptSkill);
            if (level5Profession == null) {
                return false;
            }
            ProfessionType[] options = ProfessionType.getLevel10Options(promptSkill, level5Profession);
            if (selectedProfession != options[0] && selectedProfession != options[1]) {
                return false;
            }
        } else {
            return false;
        }

        if (!addProfession(selectedProfession)) {
            return false;
        }

        pendingProfessionChoices.remove(0);

        if (promptLevel == 5 && getRawSkillLevel(promptSkill) >= 10 && !hasLevel10Profession(promptSkill) && !hasPendingProfessionChoice(promptSkill, 10)) {
            pendingProfessionChoices.add(0, new ProfessionChoicePrompt(promptSkill, 10));
        }

        markDirty();
        return true;
    }
    
    /**
     * 应用职业效果
     */
    @SuppressWarnings("incomplete-switch")
    private void applyProfessionEffect(ProfessionType profession) {
        switch (profession) {
            case FIGHTER -> {
                maxHealth += 15;
                health = Math.min(health + 15, maxHealth);
            }
            case DEFENDER -> {
                maxHealth += 25;
                health = Math.min(health + 25, maxHealth);
            }
        }
    }

    @SuppressWarnings("incomplete-switch")
    private void revertProfessionEffect(ProfessionType profession) {
        switch (profession) {
            case FIGHTER -> {
                maxHealth = Math.max(100, maxHealth - 15);
                health = Math.min(health, maxHealth);
            }
            case DEFENDER -> {
                maxHealth = Math.max(100, maxHealth - 25);
                health = Math.min(health, maxHealth);
            }
        }
    }

    private boolean hasPendingProfessionChoice(SkillType skill, int level) {
        for (ProfessionChoicePrompt prompt : pendingProfessionChoices) {
            if (prompt.skill() == skill && prompt.level() == level) {
                return true;
            }
        }
        return false;
    }

    private boolean hasPendingNewLevel(SkillType skill, int level) {
        for (SkillLevelUp pendingLevel : pendingNewLevels) {
            if (pendingLevel.skill() == skill && pendingLevel.newLevel() == level) {
                return true;
            }
        }
        return false;
    }

    private void addPendingNewLevel(SkillType skill, int level) {
        if (!hasPendingNewLevel(skill, level)) {
            pendingNewLevels.add(new SkillLevelUp(skill, level));
        }
    }

    private boolean hasLevel5Profession(SkillType skill) {
        return getLevel5Profession(skill) != null;
    }

    private ProfessionType getLevel5Profession(SkillType skill) {
        for (int professionId : professions) {
            ProfessionType profession = ProfessionType.fromId(professionId);
            if (profession != null && profession.getSkillType() == skill && profession.getRequiredLevel() == 5) {
                return profession;
            }
        }
        return null;
    }

    private boolean hasLevel10Profession(SkillType skill) {
        for (int professionId : professions) {
            ProfessionType profession = ProfessionType.fromId(professionId);
            if (profession != null && profession.getSkillType() == skill && profession.getRequiredLevel() == 10) {
                return true;
            }
        }
        return false;
    }

    public void repairMissingProfessionChoices() {
        pendingProfessionChoices.removeIf(prompt -> getRawSkillLevel(prompt.skill()) < prompt.level());

        for (SkillType skill : SkillType.values()) {
            int rawLevel = getRawSkillLevel(skill);
            if (rawLevel >= 5 && !hasLevel5Profession(skill) && !hasPendingProfessionChoice(skill, 5)) {
                pendingProfessionChoices.add(new ProfessionChoicePrompt(skill, 5));
            }
            if (rawLevel >= 10 && hasLevel5Profession(skill) && !hasLevel10Profession(skill) && !hasPendingProfessionChoice(skill, 10)) {
                pendingProfessionChoices.add(new ProfessionChoicePrompt(skill, 10));
            }
        }
    }
    
    // ============ 能量相关方法 ============

    private int getEffectiveMaxEnergy() {
        return Math.max(270, maxEnergy + tempMaxEnergyBonus);
    }

    private float clampEnergy(float value) {
        return Math.max(MIN_ENERGY, Math.min(value, getEffectiveMaxEnergy()));
    }
    
    /**
     * 消耗能量
     * @return 是否成功消耗（体力充足）
     */
    public boolean consumeEnergy(float amount) {
        if (amount <= 0) return true;

        boolean enough = energy >= amount;
        energy = clampEnergy(energy - amount);

        if (energy <= 0) {
            exhausted = true;
        }

        markDirty();
        return enough;
    }
    
    /**
     * 恢复能量
     */
    public void restoreEnergy(float amount) {
        energy = clampEnergy(energy + amount);
        
        // 恢复到0以上时解除疲惫（需要其他方式治愈）
        // 注意：星露谷中疲惫状态不会自动解除
        
        markDirty();
    }
    
    /**
     * 治愈疲惫状态
     */
    public void cureExhaustion() {
        exhausted = false;
        markDirty();
    }
    
    /**
     * 睡觉恢复能量
     * @param sleepTime 入睡时间（游戏分钟）
     */
    public void sleep(int sleepTime) {
        // 参照 Stardew Valley 原版 Farmer.dayupdate(int timeWentToSleep)
        // 我们的 sleepTime 使用“分钟(0..1560)”表示（6:00=360，2:00=1560），
        // 这里转换成 SV 的 clock 格式（例如 6:00 -> 600，2:00 -> 2600）。

        // 每日重置：Statue of Blessings 祝福标记
        blessedByStatueToday = false;
        blessingOfWatersRemaining = 0;

        float oldEnergy = energy;

        int hour = sleepTime / 60;
        int minute = sleepTime % 60;
        int timeWentToSleepClock = (hour * 100) + minute;
        int bedTimeClock = timeWentToSleepClock;

        energy = getEffectiveMaxEnergy();

        boolean wasExhausted = exhausted;
        if (wasExhausted) {
            exhausted = false;
            energy = (getEffectiveMaxEnergy() / 2.0f) + 1.0f;
        }

        if (bedTimeClock > 2400) {
            float staminaRestorationReduction = (1.0f - (float) (2600 - Math.min(2600, bedTimeClock)) / 200.0f) * (getEffectiveMaxEnergy() / 2.0f);
            energy -= staminaRestorationReduction;
            if (timeWentToSleepClock > 2700) {
                energy /= 2.0f;
            }
        }

        if (timeWentToSleepClock < 2700 && oldEnergy > energy && !wasExhausted) {
            energy = oldEnergy;
        }

        energy = clampEnergy(energy);
        markDirty();
    }
    
    // ============ 金币相关方法 ============
    
    /**
     * 添加金币
     */
    public void addMoney(int amount) {
        if (amount > 0) {
            money += amount;
            totalMoneyEarned = Math.max(0L, totalMoneyEarned + amount);
            markDirty();
        }
    }
    
    /**
     * 移除金币
     * @return 是否成功（金币是否足够）
     */
    public boolean removeMoney(int amount) {
        if (amount <= 0) return true;
        if (money >= amount) {
            money -= amount;
            markDirty();
            return true;
        }
        return false;
    }
    
    // ============ Getters & Setters ============

    // ---- 晕倒/死亡系统 ----
    public boolean isPassedOutFromCombat() { return passedOutFromCombat; }
    public void setPassedOutFromCombat(boolean value) { passedOutFromCombat = value; markDirty(); }

    public List<net.minecraft.world.item.ItemStack> getItemsLostLastDeath() { return itemsLostLastDeath; }
    public void setItemsLostLastDeath(List<net.minecraft.world.item.ItemStack> items) {
        itemsLostLastDeath.clear();
        itemsLostLastDeath.addAll(items);
        markDirty();
    }
    public void clearItemsLostLastDeath() { itemsLostLastDeath.clear(); markDirty(); }
    
    public UUID getPlayerUUID() { return playerUUID; }
    
    public int getHealth() { return health; }
    public void setHealth(int health) { 
        this.health = Math.max(0, Math.min(health, maxHealth));
        markDirty();
    }
    
    public int getMaxHealth() { return maxHealth; }
    public void setMaxHealth(int maxHealth) {
        this.maxHealth = Math.max(100, maxHealth);
        this.health = Math.min(this.health, this.maxHealth);
        markDirty();
    }
    
    public float getEnergy() { return energy; }
    public void setEnergy(float energy) {
        this.energy = clampEnergy(energy);
        markDirty();
    }
    
    public int getMaxEnergy() { return getEffectiveMaxEnergy(); }
    public void setMaxEnergy(int maxEnergy) {
        this.maxEnergy = Math.max(270, maxEnergy);
        this.energy = Math.min(this.energy, getEffectiveMaxEnergy());
        markDirty();
    }

    public int getStardropsConsumed() { return stardropsConsumed; }
    public boolean canConsumeStardrop(int maxConsumed) { return stardropsConsumed < maxConsumed; }
    public boolean consumeStardrop(int energyGain, int maxConsumed) {
        if (!canConsumeStardrop(maxConsumed)) {
            return false;
        }
        stardropsConsumed++;
        maxEnergy = Math.max(270, maxEnergy + Math.max(0, energyGain));
        energy = getEffectiveMaxEnergy();
        markDirty();
        return true;
    }
    
    public boolean isExhausted() { return exhausted; }
    
    public int getMoney() { return money; }
    public void setMoney(int money) {
        this.money = Math.max(0, money);
        markDirty();
    }

    public int getFairStarTokens() { return fairStarTokens; }

    public void setFairStarTokens(int value) {
        this.fairStarTokens = Math.max(0, value);
        markDirty();
    }

    public int addFairStarTokens(int amount) {
        if (amount > 0) {
            fairStarTokens = Math.max(0, fairStarTokens + amount);
            markDirty();
        }
        return fairStarTokens;
    }

    public boolean consumeFairStarTokens(int amount) {
        if (amount <= 0) {
            return true;
        }
        if (fairStarTokens < amount) {
            return false;
        }
        fairStarTokens -= amount;
        markDirty();
        return true;
    }

    public int getLastFairGrillBurgerDateKey() { return lastFairGrillBurgerDateKey; }

    public void setLastFairGrillBurgerDateKey(int dateKey) {
        this.lastFairGrillBurgerDateKey = dateKey;
        markDirty();
    }

    public long getTotalMoneyEarned() { return totalMoneyEarned; }

    public String getLastKnownName() { return lastKnownName == null ? "" : lastKnownName; }

    public void setLastKnownName(String name) {
        String normalized = name == null ? "" : name.trim();
        if (!normalized.equals(lastKnownName)) {
            lastKnownName = normalized;
            markDirty();
        }
    }

    public int getGender() { return gender; }

    public boolean isMale() { return gender != 1; }

    public String getPreferredName() { return preferredName == null ? "" : preferredName; }

    public String getFavoriteThing() { return favoriteThing == null ? "" : favoriteThing; }

    public boolean isProfileComplete() {
        return gender >= 0 && !getPreferredName().isBlank() && !getFavoriteThing().isBlank();
    }

    public void setProfile(String preferredName, String favoriteThing, int gender) {
        String normalizedName = preferredName == null ? "" : preferredName.trim();
        String normalizedFavorite = favoriteThing == null ? "" : favoriteThing.trim();
        int normalizedGender = gender == 1 ? 1 : 0;
        if (!normalizedName.equals(this.preferredName)
                || !normalizedFavorite.equals(this.favoriteThing)
                || normalizedGender != this.gender) {
            this.preferredName = normalizedName;
            this.favoriteThing = normalizedFavorite;
            this.gender = normalizedGender;
            markDirty();
        }
    }
    
    public int getSkillLevel(SkillType skill) {
        int base = skillLevels[skill.getId()];
        return switch (skill) {
            case FISHING -> Math.max(0, base + tempFishingLevelBonus);
            case FARMING -> Math.max(0, base + tempFarmingLevelBonus);
            case FORAGING -> Math.max(0, base + tempForagingLevelBonus);
            case MINING -> Math.max(0, base + tempMiningLevelBonus);
            default -> base;
        };
    }

    public int getRawSkillLevel(SkillType skill) {
        return skillLevels[skill.getId()];
    }

    /**
     * 星露谷“Luck Buff”等级（目前仅来自临时Buff）。
     * 注意：这不是 dailyLuck。
     */
    public int getLuckLevel() {
        return Math.max(0, tempLuckBonus);
    }
    
    public int getSkillExperience(SkillType skill) {
        return experiencePoints[skill.getId()];
    }
    
    public void setSkillExperience(SkillType skill, int experience) {
        int skillId = skill.getId();
        experiencePoints[skillId] = Math.max(0, experience);
        
        // 重新计算等级
        int newLevel = calculateLevel(experiencePoints[skillId]);
        skillLevels[skillId] = newLevel;
        repairMissingProfessionChoices();
        
        markDirty();
    }
    
    public List<Integer> getProfessions() {
        return new ArrayList<>(professions);
    }

    public record ProfessionChoicePrompt(SkillType skill, int level) {
    }

    public record SkillLevelUp(SkillType skill, int newLevel) {
    }

    public boolean isDecorationUnlocked(DecorationType type, String styleId) {
        if (styleId == null || styleId.isBlank()) {
            return false;
        }
        return (type == DecorationType.WALLPAPER ? unlockedWallpaperStyles : unlockedFlooringStyles).contains(styleId);
    }

    public boolean unlockDecoration(DecorationType type, String styleId) {
        if (styleId == null || styleId.isBlank()) {
            return false;
        }
        boolean changed = (type == DecorationType.WALLPAPER ? unlockedWallpaperStyles : unlockedFlooringStyles).add(styleId);
        if (changed) {
            markDirty();
        }
        return changed;
    }

    public boolean lockDecoration(DecorationType type, String styleId) {
        if (styleId == null || styleId.isBlank()) {
            return false;
        }
        String defaultStyle = DecorationStyleRegistry.getDefaultStyleId(type);
        if (defaultStyle.equals(styleId)) {
            return false;
        }
        boolean changed = (type == DecorationType.WALLPAPER ? unlockedWallpaperStyles : unlockedFlooringStyles).remove(styleId);
        if (changed) {
            markDirty();
        }
        return changed;
    }

    public Set<String> getUnlockedDecorations(DecorationType type) {
        return new HashSet<>(type == DecorationType.WALLPAPER ? unlockedWallpaperStyles : unlockedFlooringStyles);
    }

    public boolean isRecipeUnlocked(String recipeId) {
        String normalized = RecipeIdNormalizer.storageId(recipeId);
        if (normalized.isBlank()) {
            return false;
        }
        return unlockedRecipes.contains(normalized);
    }

    public boolean unlockRecipe(String recipeId) {
        String normalized = RecipeIdNormalizer.storageId(recipeId);
        if (normalized.isBlank()) {
            return false;
        }
        boolean changed = unlockedRecipes.add(normalized);
        if (changed) {
            markDirty();
        }
        return changed;
    }

    public boolean lockRecipe(String recipeId) {
        String normalized = RecipeIdNormalizer.storageId(recipeId);
        if (normalized.isBlank()) {
            return false;
        }
        boolean changed = unlockedRecipes.remove(normalized);
        if (changed) {
            markDirty();
        }
        return changed;
    }

    public boolean hasWatchedQueenOfSauceOnDay(int dayKey) {
        return dayKey >= 0 && queenOfSauceWatchDay == dayKey;
    }

    public String getQueenOfSauceRecipeIdForDay(int dayKey) {
        if (!hasWatchedQueenOfSauceOnDay(dayKey)) {
            return "";
        }
        return queenOfSauceRecipeId == null ? "" : queenOfSauceRecipeId;
    }

    public boolean markQueenOfSauceWatched(int dayKey, String recipeId) {
        if (dayKey < 0) {
            return false;
        }
        String normalizedRecipeId = recipeId == null ? "" : recipeId;
        if (queenOfSauceWatchDay == dayKey && normalizedRecipeId.equals(queenOfSauceRecipeId)) {
            return false;
        }
        queenOfSauceWatchDay = dayKey;
        queenOfSauceRecipeId = normalizedRecipeId;
        markDirty();
        return true;
    }

    public Set<String> getUnlockedRecipes() {
        return new HashSet<>(unlockedRecipes);
    }

    public int getRecipeCraftCount(String recipeId) {
        String normalized = RecipeIdNormalizer.storageId(recipeId);
        if (normalized.isBlank()) {
            return 0;
        }
        return Math.max(0, recipeCraftCounts.getOrDefault(normalized, 0));
    }

    public Map<String, Integer> getAllRecipeCraftCounts() {
        return new HashMap<>(recipeCraftCounts);
    }

    public boolean recordRecipeCrafted(String recipeId, int craftedAmount) {
        String normalized = RecipeIdNormalizer.storageId(recipeId);
        if (normalized.isBlank() || craftedAmount <= 0) {
            return false;
        }

        int current = Math.max(0, recipeCraftCounts.getOrDefault(normalized, 0));
        int next = current + craftedAmount;
        if (next == current) {
            return false;
        }

        recipeCraftCounts.put(normalized, next);
        if (RecipeCatalogData.getCookingRecipeIds().contains(normalized)) {
            addLeaderboardPeriodValue(LeaderboardMetric.COOKING_COUNT.id(), craftedAmount);
        }
        markDirty();
        return true;
    }

    public int getFishCatchCount(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return 0;
        }
        return Math.max(0, fishCatchCounts.getOrDefault(itemId, 0));
    }

    /** SDV parity: {@code who.fishCaught.Length} — number of distinct fish species caught. */
    public int getDistinctFishCaughtCount() {
        return fishCatchCounts.size();
    }

    public boolean addFishCatchCount(String itemId, int amount) {
        if (itemId == null || itemId.isBlank() || amount <= 0) {
            return false;
        }
        int next = Math.max(0, fishCatchCounts.getOrDefault(itemId, 0)) + amount;
        fishCatchCounts.put(itemId, next);
        preciseFishCaught += amount;
        addLeaderboardPeriodValue(LeaderboardMetric.FISH_CAUGHT.id(), amount);
        markDirty();
        return true;
    }

    /** SDV {@code Stats.PreciseFishCaught}: total fish caught (counts duplicates). */
    public int getPreciseFishCaught() {
        return preciseFishCaught;
    }

    public boolean isSpecialOrderRuleActive(String ruleId) {
        if (ruleId == null || ruleId.isBlank()) {
            return false;
        }
        return activeSpecialOrderRules.contains(ruleId);
    }

    public boolean recordShippedItem(String itemId, int amount) {
        if (itemId == null || itemId.isBlank() || amount <= 0) {
            return false;
        }

        boolean changed = shippedBasic.add(itemId);
        int next = Math.max(0, itemsShipped.getOrDefault(itemId, 0)) + amount;
        if (next != itemsShipped.getOrDefault(itemId, 0)) {
            itemsShipped.put(itemId, next);
            addLeaderboardPeriodValue(LeaderboardMetric.ITEMS_SHIPPED.id(), amount);
            changed = true;
        }

        if (changed) {
            markDirty();
        }
        return changed;
    }

    public Set<String> getShippedBasic() {
        return new HashSet<>(shippedBasic);
    }

    public Map<String, Integer> getAllItemsShipped() {
        return Collections.unmodifiableMap(itemsShipped);
    }

    public int getItemsShippedCount(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return 0;
        }
        return Math.max(0, itemsShipped.getOrDefault(itemId, 0));
    }

    public long getTotalShippingGold() { return totalShippingGold; }

    public void addTotalShippingGold(long amount) {
        if (amount <= 0L) return;
        totalShippingGold = Math.max(0L, totalShippingGold + amount);
        addLeaderboardPeriodValue(LeaderboardMetric.SHIPPING_VALUE.id(), amount);
        markDirty();
    }

    public boolean setSpecialOrderRuleActive(String ruleId, boolean active) {
        if (ruleId == null || ruleId.isBlank()) {
            return false;
        }
        boolean changed = active ? activeSpecialOrderRules.add(ruleId) : activeSpecialOrderRules.remove(ruleId);
        if (changed) {
            markDirty();
        }
        return changed;
    }

    public int getStat(String key) {
        if (key == null || key.isBlank()) {
            return 0;
        }
        return Math.max(0, stats.getOrDefault(key, 0));
    }

    public boolean setStat(String key, int value) {
        if (key == null || key.isBlank()) {
            return false;
        }
        int normalized = Math.max(0, value);
        int current = getStat(key);
        if (current == normalized) {
            return false;
        }
        if (normalized == 0) {
            stats.remove(key);
        } else {
            stats.put(key, normalized);
        }
        markDirty();
        return true;
    }

    public int incrementStat(String key, int amount) {
        if (key == null || key.isBlank() || amount <= 0) {
            return getStat(key);
        }
        int next = getStat(key) + amount;
        stats.put(key, next);
        markDirty();
        return next;
    }

    public Map<String, Integer> getAllStats() {
        return Collections.unmodifiableMap(stats);
    }

    public int getTicketPrizesClaimed() {
        return Math.max(0, ticketPrizesClaimed);
    }

    public boolean setTicketPrizesClaimed(int value) {
        int normalized = Math.max(0, value);
        if (ticketPrizesClaimed == normalized) {
            return false;
        }
        ticketPrizesClaimed = normalized;
        markDirty();
        return true;
    }

    public int incrementTicketPrizesClaimed() {
        ticketPrizesClaimed = Math.max(0, ticketPrizesClaimed + 1);
        markDirty();
        return ticketPrizesClaimed;
    }

    public int getSpecialOrderPrizeTickets() {
        return Math.max(0, specialOrderPrizeTickets);
    }

    public boolean setSpecialOrderPrizeTickets(int value) {
        int normalized = Math.max(0, value);
        if (specialOrderPrizeTickets == normalized) {
            return false;
        }
        specialOrderPrizeTickets = normalized;
        markDirty();
        return true;
    }

    public int addSpecialOrderPrizeTickets(int amount) {
        if (amount <= 0) {
            return getSpecialOrderPrizeTickets();
        }
        specialOrderPrizeTickets = Math.max(0, specialOrderPrizeTickets + amount);
        markDirty();
        return specialOrderPrizeTickets;
    }

    public boolean consumeSpecialOrderPrizeTicket() {
        if (specialOrderPrizeTickets <= 0) {
            return false;
        }
        specialOrderPrizeTickets--;
        markDirty();
        return true;
    }
    
    public boolean isDirty() { return dirty; }

    public double getDailyLuck() {
        return com.stardew.craft.secretnote.SecretNote20Service.applyDailyLuckBonus(this, dailyLuck);
    }

    public int getDailyLuckDateKey() { return dailyLuckDateKey; }

    public int getTrashCansChecked() { return trashCansChecked; }

    public void incrementTrashCansChecked() {
        trashCansChecked++;
        addLeaderboardPeriodValue(LeaderboardMetric.TRASH_CANS_CHECKED.id(), 1L);
        markDirty();
    }

    public int getMineBlocksBrokenTotal() { return mineBlocksBrokenTotal; }

    public int getMineStonesBroken() { return mineStonesBroken; }

    public int getMineOresBroken() { return mineOresBroken; }

    public int getMineGemOresBroken() { return mineGemOresBroken; }

    public int getMineMineralNodesBroken() { return mineMineralNodesBroken; }

    public int getMineBlocksBombed() { return mineBlocksBombed; }

    public void addMineBlocksBroken(int amount) {
        if (amount <= 0) return;
        mineBlocksBrokenTotal = Math.max(0, mineBlocksBrokenTotal + amount);
        addLeaderboardPeriodValue(LeaderboardMetric.MINE_BLOCKS_BROKEN.id(), amount);
        markDirty();
    }

    public void recordMineBlockBroken(boolean ore, boolean gemOre, boolean mineralNode) {
        mineBlocksBrokenTotal = Math.max(0, mineBlocksBrokenTotal + 1);
        addLeaderboardPeriodValue(LeaderboardMetric.MINE_BLOCKS_BROKEN.id(), 1L);
        if (mineralNode) {
            mineMineralNodesBroken = Math.max(0, mineMineralNodesBroken + 1);
            addLeaderboardPeriodValue(LeaderboardMetric.MINE_MINERAL_NODES_BROKEN.id(), 1L);
        } else if (ore) {
            mineOresBroken = Math.max(0, mineOresBroken + 1);
            addLeaderboardPeriodValue(LeaderboardMetric.MINE_ORES_BROKEN.id(), 1L);
            if (gemOre) {
                mineGemOresBroken = Math.max(0, mineGemOresBroken + 1);
                addLeaderboardPeriodValue(LeaderboardMetric.MINE_GEM_ORES_BROKEN.id(), 1L);
            }
        } else {
            mineStonesBroken = Math.max(0, mineStonesBroken + 1);
            addLeaderboardPeriodValue(LeaderboardMetric.MINE_STONES_BROKEN.id(), 1L);
        }
        markDirty();
    }

    public void addMineBlocksBombed(int amount) {
        if (amount <= 0) return;
        mineBlocksBombed = Math.max(0, mineBlocksBombed + amount);
        addLeaderboardPeriodValue(LeaderboardMetric.MINE_BLOCKS_BOMBED.id(), amount);
        markDirty();
    }

    public int getGiftsGivenTotal() { return giftsGivenTotal; }

    public void recordGiftGiven() {
        giftsGivenTotal = Math.max(0, giftsGivenTotal + 1);
        addLeaderboardPeriodValue(LeaderboardMetric.GIFTS_GIVEN.id(), 1L);
        markDirty();
    }

    public int getCombatDeaths() { return combatDeaths; }

    public int getPassOuts2Am() { return passOuts2Am; }

    public int getPassOutsExhaustion() { return passOutsExhaustion; }

    public int getPassOutsTotal() { return Math.max(0, combatDeaths + passOuts2Am + passOutsExhaustion); }

    public void recordCombatDeath() {
        combatDeaths = Math.max(0, combatDeaths + 1);
        addLeaderboardPeriodValue(LeaderboardMetric.COMBAT_DEATHS.id(), 1L);
        addLeaderboardPeriodValue(LeaderboardMetric.PASS_OUTS.id(), 1L);
        markDirty();
    }

    public void record2AmPassOut() {
        passOuts2Am = Math.max(0, passOuts2Am + 1);
        addLeaderboardPeriodValue(LeaderboardMetric.PASS_OUTS.id(), 1L);
        markDirty();
    }

    public void recordExhaustionPassOut() {
        passOutsExhaustion = Math.max(0, passOutsExhaustion + 1);
        addLeaderboardPeriodValue(LeaderboardMetric.PASS_OUTS.id(), 1L);
        markDirty();
    }

    public int getAnimalProductsCollectedTotal() { return animalProductsCollectedTotal; }

    public void recordAnimalProductsCollected(int amount) {
        if (amount <= 0) return;
        animalProductsCollectedTotal = Math.max(0, animalProductsCollectedTotal + amount);
        addLeaderboardPeriodValue(LeaderboardMetric.ANIMAL_PRODUCTS_COLLECTED.id(), amount);
        markDirty();
    }

    public int getBasementShortsStolen() { return basementShortsStolen; }

    public void recordBasementShortsStolen() {
        basementShortsStolen = Math.max(0, basementShortsStolen + 1);
        addLeaderboardPeriodValue(LeaderboardMetric.BASEMENT_SHORTS_STOLEN.id(), 1L);
        markDirty();
    }

    public long getLeaderboardPeriodValue(LeaderboardPeriod period, String metricId) {
        if (period == null || period.isTotal() || metricId == null || metricId.isBlank()) {
            return 0L;
        }
        return switch (period) {
            case DAY -> leaderboardDayKey == currentLeaderboardDayKey() ? leaderboardDayValues.getOrDefault(metricId, 0L) : 0L;
            case WEEK -> leaderboardWeekKey == currentLeaderboardWeekKey() ? leaderboardWeekValues.getOrDefault(metricId, 0L) : 0L;
            case SEASON -> leaderboardSeasonKey == currentLeaderboardSeasonKey() ? leaderboardSeasonValues.getOrDefault(metricId, 0L) : 0L;
            case TOTAL -> 0L;
        };
    }

    public void addLeaderboardPeriodValue(String metricId, long amount) {
        if (metricId == null || metricId.isBlank() || amount <= 0L) {
            return;
        }
        refreshLeaderboardPeriodKeysForWrite();
        leaderboardDayValues.merge(metricId, amount, Long::sum);
        leaderboardWeekValues.merge(metricId, amount, Long::sum);
        leaderboardSeasonValues.merge(metricId, amount, Long::sum);
        markDirty();
    }

    private void refreshLeaderboardPeriodKeysForWrite() {
        int dayKey = currentLeaderboardDayKey();
        if (leaderboardDayKey != dayKey) {
            leaderboardDayKey = dayKey;
            leaderboardDayValues.clear();
        }
        int weekKey = currentLeaderboardWeekKey();
        if (leaderboardWeekKey != weekKey) {
            leaderboardWeekKey = weekKey;
            leaderboardWeekValues.clear();
        }
        int seasonKey = currentLeaderboardSeasonKey();
        if (leaderboardSeasonKey != seasonKey) {
            leaderboardSeasonKey = seasonKey;
            leaderboardSeasonValues.clear();
        }
    }

    private int currentLeaderboardDayKey() {
        return com.stardew.craft.time.StardewTimeManager.get().getAbsoluteDay();
    }

    private int currentLeaderboardWeekKey() {
        return Math.max(0, currentLeaderboardDayKey() - 1) / 7;
    }

    private int currentLeaderboardSeasonKey() {
        com.stardew.craft.time.StardewTimeManager time = com.stardew.craft.time.StardewTimeManager.get();
        return (time.getCurrentYear() - 1) * 4 + time.getCurrentSeason();
    }

    private static String skillMetricId(SkillType skill) {
        return switch (skill) {
            case FARMING -> LeaderboardMetric.SKILL_FARMING.id();
            case FISHING -> LeaderboardMetric.SKILL_FISHING.id();
            case FORAGING -> LeaderboardMetric.SKILL_FORAGING.id();
            case MINING -> LeaderboardMetric.SKILL_MINING.id();
            case COMBAT -> LeaderboardMetric.SKILL_COMBAT.id();
        };
    }

    // ──── Mail Flags (SDV mailReceived parity) ────
    public Set<String> getMailFlags() { return Collections.unmodifiableSet(mailFlags); }
    public boolean hasMailFlag(String flag) { return mailFlags.contains(flag); }
    public void addMailFlag(String flag) { if (mailFlags.add(flag)) markDirty(); }
    public void removeMailFlag(String flag) { if (mailFlags.remove(flag)) markDirty(); }

    // ──── Mailbox Queue (SDV Farmer.mailbox parity) ────
    public List<String> getMailbox() { return Collections.unmodifiableList(mailbox); }
    public boolean hasMailInMailbox() { return !mailbox.isEmpty(); }
    /**
     * 从队列头部取出一封邮件（SDV Farmer.mailbox 的 pop 语义）。
     */
    @Nullable
    public String popMailFromMailbox() {
        if (mailbox.isEmpty()) return null;
        String id = mailbox.remove(0);
        markDirty();
        return id;
    }
    public void addToMailbox(String mailId) {
        if (!mailbox.contains(mailId)) {
            mailbox.add(mailId);
            markDirty();
        }
    }

    // ──── Mail For Tomorrow (SDV Farmer.mailForTomorrow parity) ────
    public List<String> getMailForTomorrow() { return Collections.unmodifiableList(mailForTomorrow); }
    public void addMailForTomorrow(String mailId) {
        if (!mailForTomorrow.contains(mailId)) {
            mailForTomorrow.add(mailId);
            markDirty();
        }
    }
    /**
     * 夜间结算 / 玩家登录时调用：
     * - mailForTomorrow     → mailbox（可读信件） —— 全部 flush（原逻辑，不含日期判断）
     * - mailFlagsForTomorrow → mailFlags —— 只 flush queuedDay &lt; currentAbsoluteDay 的项
     *
     * <p>多人服务器语义：每个 flag 记录 queue 时的 day；currentDay 大于它才生效。
     * 离线玩家跨日登录时也能正确补 flush。
     *
     * @param currentAbsoluteDay 当前服务器 {@link com.stardew.craft.time.StardewTimeManager#getAbsoluteDay()}
     * @return 本次新 flush 进 mailFlags 的 flag 清单
     */
    public java.util.List<String> deliverTomorrowMail(int currentAbsoluteDay) {
        java.util.List<String> flushedFlags = java.util.Collections.emptyList();
        boolean dirty = false;
        if (!mailForTomorrow.isEmpty()) {
            for (String mid : mailForTomorrow) {
                if (!mailbox.contains(mid)) mailbox.add(mid);
            }
            mailForTomorrow.clear();
            dirty = true;
        }
        if (!mailFlagsForTomorrow.isEmpty()) {
            java.util.List<String> due = new ArrayList<>();
            for (java.util.Map.Entry<String, Integer> e : mailFlagsForTomorrow.entrySet()) {
                if (currentAbsoluteDay > e.getValue()) due.add(e.getKey());
            }
            if (!due.isEmpty()) {
                for (String flag : due) {
                    mailFlags.add(flag);
                    mailFlagsForTomorrow.remove(flag);
                }
                flushedFlags = due;
                dirty = true;
            }
        }
        if (dirty) markDirty();
        return flushedFlags;
    }

    // ──── Mail Flags For Tomorrow（SDV addMailForTomorrow + noLetter:true parity）────
    /** 查询某 flag 是否已排队在明天生效（还没 flush 到 mailFlags）。 */
    public boolean hasMailFlagForTomorrow(String flag) { return mailFlagsForTomorrow.containsKey(flag); }
    /**
     * 次日才生效的标志位：flush 时直接进 mailFlags，不进 mailbox。
     * @param flag                  flag id
     * @param currentAbsoluteDay    当前 absoluteDay —— 记录为 queuedDay，flush 条件 currentDay &gt; queuedDay。
     */
    public void addMailFlagForTomorrow(String flag, int currentAbsoluteDay) {
        if (flag == null || flag.isBlank()) return;
        if (!mailFlagsForTomorrow.containsKey(flag)) {
            mailFlagsForTomorrow.put(flag, currentAbsoluteDay);
            markDirty();
        }
    }

    // ──── Monster Slayer (Gil Goals) ────
    public int getMonsterKills(String goalKey) { return monsterKillCounts.getOrDefault(goalKey, 0); }
    public Map<String, Integer> getAllMonsterKills() { return Collections.unmodifiableMap(monsterKillCounts); }
    public void addMonsterKills(String goalKey, int count) {
        monsterKillCounts.merge(goalKey, count, Integer::sum);
        addLeaderboardPeriodValue(LeaderboardMetric.MONSTERS_SLAIN.id(), count);
        markDirty();
    }
    public boolean hasClaimedSlayerReward(String goalKey) { return claimedSlayerRewards.contains(goalKey); }
    public Set<String> getClaimedSlayerRewards() { return Collections.unmodifiableSet(claimedSlayerRewards); }
    public void claimSlayerReward(String goalKey) { if (claimedSlayerRewards.add(goalKey)) markDirty(); }

    // ──── Special Items (unique treasure dedup) ────
    public boolean hasSpecialItem(String itemId) { return specialItems.contains(itemId); }
    public void addSpecialItem(String itemId) { if (specialItems.add(itemId)) markDirty(); }
    public void removeSpecialItem(String itemId) { if (specialItems.remove(itemId)) markDirty(); }

    // ──── Secret Notes (per-player collection) ────
    public Set<String> getSecretNotesSeen() { return Collections.unmodifiableSet(secretNotesSeen); }
    public boolean hasSeenSecretNote(String noteId) { return noteId != null && secretNotesSeen.contains(noteId); }
    public boolean markSecretNoteSeen(String noteId) {
        if (noteId == null || noteId.isBlank()) return false;
        boolean changed = secretNotesSeen.add(noteId);
        if (changed) markDirty();
        return changed;
    }
    public boolean forgetSecretNote(String noteId) {
        boolean changed = noteId != null && secretNotesSeen.remove(noteId);
        if (changed) markDirty();
        return changed;
    }
    public void clearSecretNotesSeen() {
        if (!secretNotesSeen.isEmpty()) {
            secretNotesSeen.clear();
            markDirty();
        }
    }

    public Set<String> getRevealedGiftTastes() { return Collections.unmodifiableSet(revealedGiftTastes); }
    public boolean hasRevealedGiftTaste(String npcId, String objectId) {
        return revealedGiftTastes.contains(secretNoteGiftTasteKey(npcId, objectId));
    }
    public boolean revealGiftTaste(String npcId, String objectId) {
        String key = secretNoteGiftTasteKey(npcId, objectId);
        if (key.isBlank()) return false;
        boolean changed = revealedGiftTastes.add(key);
        if (changed) markDirty();
        return changed;
    }

    private static String secretNoteGiftTasteKey(String npcId, String objectId) {
        if (npcId == null || npcId.isBlank() || objectId == null || objectId.isBlank()) return "";
        return npcId.trim().toLowerCase(java.util.Locale.ROOT) + ":" + objectId.trim();
    }

    // ──── Equipment (Rings + Boots + Cosmetics) ────
    public String getEquippedLeftRing() { return equipmentStorageId(equippedLeftRing); }
    public void setEquippedLeftRing(String id) { setEquippedLeftRingStack(equipmentStackFromStorageId(id)); }
    public net.minecraft.world.item.ItemStack getEquippedLeftRingStack() { return equippedLeftRing.copy(); }
    public void setEquippedLeftRingStack(net.minecraft.world.item.ItemStack stack) {
        this.equippedLeftRing = singleEquipmentStack(stack);
        markDirty();
    }
    public String getEquippedRightRing() { return equipmentStorageId(equippedRightRing); }
    public void setEquippedRightRing(String id) { setEquippedRightRingStack(equipmentStackFromStorageId(id)); }
    public net.minecraft.world.item.ItemStack getEquippedRightRingStack() { return equippedRightRing.copy(); }
    public void setEquippedRightRingStack(net.minecraft.world.item.ItemStack stack) {
        this.equippedRightRing = singleEquipmentStack(stack);
        markDirty();
    }
    public String getEquippedBoots() { return equipmentStorageId(equippedBoots); }
    public void setEquippedBoots(String id) { setEquippedBootsStack(equipmentStackFromStorageId(id)); }
    public net.minecraft.world.item.ItemStack getEquippedBootsStack() { return equippedBoots.copy(); }
    public void setEquippedBootsStack(net.minecraft.world.item.ItemStack stack) {
        this.equippedBoots = singleEquipmentStack(stack);
        markDirty();
    }
    public net.minecraft.world.item.ItemStack getEquippedTrinket() { return equippedTrinket.copy(); }
    public void setEquippedTrinket(net.minecraft.world.item.ItemStack stack) {
        this.equippedTrinket = stack == null ? net.minecraft.world.item.ItemStack.EMPTY : stack.copyWithCount(1);
        markDirty();
    }
    public String getEquippedHat() { return equippedHat; }
    public void setEquippedHat(String id) { this.equippedHat = id != null ? id : ""; markDirty(); }
    public String getEquippedShirt() { return equippedShirt; }
    public void setEquippedShirt(String id) { this.equippedShirt = id != null ? id : ""; markDirty(); }
    public String getEquippedPants() { return equippedPants; }
    public void setEquippedPants(String id) { this.equippedPants = id != null ? id : ""; markDirty(); }
    public long getLastPhoenixReviveDay() { return lastPhoenixReviveDay; }
    public void setLastPhoenixReviveDay(long day) { this.lastPhoenixReviveDay = day; markDirty(); }

    /**
     * 检查玩家是否装备了指定类型的戒指（左或右）
     */
    public boolean hasRingEquipped(String itemId) {
        return itemId != null && (itemId.equals(getEquippedLeftRing()) || itemId.equals(getEquippedRightRing()));
    }

    /**
     * 获取所有装备的戒指ID列表
     */
    public java.util.List<String> getEquippedRingIds() {
        java.util.List<String> rings = new java.util.ArrayList<>(2);
        String left = getEquippedLeftRing();
        String right = getEquippedRightRing();
        if (!left.isEmpty()) rings.add(left);
        if (!right.isEmpty()) rings.add(right);
        return rings;
    }

    private static net.minecraft.world.item.ItemStack singleEquipmentStack(net.minecraft.world.item.ItemStack stack) {
        return stack == null || stack.isEmpty()
                ? net.minecraft.world.item.ItemStack.EMPTY
                : stack.copyWithCount(1);
    }

    private static String equipmentStorageId(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        if (com.stardew.craft.item.equipment.CombinedRingData.isCombinedRing(stack)) {
            return com.stardew.craft.item.equipment.CombinedRingData.encodeForEquipmentSlot(stack);
        }
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static net.minecraft.world.item.ItemStack equipmentStackFromStorageId(String id) {
        if (id == null || id.isBlank()) return net.minecraft.world.item.ItemStack.EMPTY;
        if (com.stardew.craft.item.equipment.CombinedRingData.isEncodedEquipmentSlot(id)) {
            return com.stardew.craft.item.equipment.CombinedRingData.stackFromEquipmentSlot(id);
        }
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(location)) {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }
        return new net.minecraft.world.item.ItemStack(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(location), 1);
    }

    private static net.minecraft.world.item.ItemStack loadEquipmentStack(
            CompoundTag root, String stackKey, String legacyKey,
            @Nullable net.minecraft.core.HolderLookup.Provider registries) {
        if (registries != null && root.contains(stackKey, Tag.TAG_COMPOUND)) {
            net.minecraft.world.item.ItemStack loaded = net.minecraft.world.item.ItemStack.parseOptional(
                    registries, root.getCompound(stackKey));
            if (!loaded.isEmpty()) return loaded.copyWithCount(1);
        }
        return equipmentStackFromStorageId(root.contains(legacyKey) ? root.getString(legacyKey) : "");
    }

    // ──── Quest System ────
    public QuestManager getQuestManager() { return questManager; }

    // ──── Tool Upgrade (Blacksmith) ────
    public String getToolBeingUpgraded() { return toolBeingUpgraded; }
    public void setToolBeingUpgraded(String id) { this.toolBeingUpgraded = id != null ? id : ""; markDirty(); }
    public int getDaysLeftForToolUpgrade() { return daysLeftForToolUpgrade; }
    public void setDaysLeftForToolUpgrade(int days) { this.daysLeftForToolUpgrade = days; markDirty(); }
    public boolean isToolUpgradeNotified() { return toolUpgradeNotified; }
    public void setToolUpgradeNotified(boolean notified) { this.toolUpgradeNotified = notified; markDirty(); }

    // ──── First Join Day (Mail scheduling baseline) ────
    public int getFirstJoinDay() { return firstJoinDay; }
    public void setFirstJoinDay(int day) { this.firstJoinDay = day; markDirty(); }

    // ──── Wizard Tower Hub (Cross-dimension) ────
    public boolean isWizardQuestComplete() { return wizardQuestComplete; }
    public void setWizardQuestComplete(boolean complete) { this.wizardQuestComplete = complete; markDirty(); }
    public boolean isWizardFirstMet() { return wizardFirstMet; }
    public void setWizardFirstMet(boolean met) { this.wizardFirstMet = met; markDirty(); }
    @Nullable
    public BlockPos getOverworldReturnPos() { return overworldReturnPos; }
    public void setOverworldReturnPos(@Nullable BlockPos pos) { this.overworldReturnPos = pos; markDirty(); }
    @Nullable
    public ResourceKey<Level> getWizardSourceDimension() { return wizardSourceDimension; }
    public void setWizardSourceDimension(@Nullable ResourceKey<Level> dim) { this.wizardSourceDimension = dim; markDirty(); }
    public boolean isStarterToolsGiven() { return starterToolsGiven; }
    public void setStarterToolsGiven(boolean given) { this.starterToolsGiven = given; markDirty(); }
    public boolean isBilibiliRewardClaimed() { return bilibiliRewardClaimed; }
    public void setBilibiliRewardClaimed(boolean claimed) { this.bilibiliRewardClaimed = claimed; markDirty(); }

    public int getLastGotDesertFestivalRatingPrizeFromMarlon() { return lastGotDesertFestivalRatingPrizeFromMarlon; }
    public void setLastGotDesertFestivalRatingPrizeFromMarlon(int absoluteDay) {
        this.lastGotDesertFestivalRatingPrizeFromMarlon = absoluteDay;
        markDirty();
    }
    public int getDesertFestivalMarlonChallengeDay() { return desertFestivalMarlonChallengeDay; }
    public String getDesertFestivalMarlonChallengeId() { return desertFestivalMarlonChallengeId; }
    public int getDesertFestivalMarlonChallengeProgress() { return desertFestivalMarlonChallengeProgress; }
    public boolean isDesertFestivalMarlonChallengeRewardClaimed() { return desertFestivalMarlonChallengeRewardClaimed; }
    public void setDesertFestivalMarlonChallenge(int absoluteDay, String challengeId, int progress, boolean rewardClaimed) {
        this.desertFestivalMarlonChallengeDay = absoluteDay;
        this.desertFestivalMarlonChallengeId = challengeId == null ? "" : challengeId;
        this.desertFestivalMarlonChallengeProgress = Math.max(0, progress);
        this.desertFestivalMarlonChallengeRewardClaimed = rewardClaimed;
        markDirty();
    }
    public void setDesertFestivalMarlonChallengeProgress(int progress) {
        this.desertFestivalMarlonChallengeProgress = Math.max(0, progress);
        markDirty();
    }
    public void setDesertFestivalMarlonChallengeRewardClaimed(boolean claimed) {
        this.desertFestivalMarlonChallengeRewardClaimed = claimed;
        markDirty();
    }
    public boolean isMine100StardropCompensationProcessed() { return mine100StardropCompensationProcessed; }
    public void setMine100StardropCompensationProcessed(boolean processed) { this.mine100StardropCompensationProcessed = processed; markDirty(); }
    public int getHandledPregenRelocationVersion() { return handledPregenRelocationVersion; }
    public void setHandledPregenRelocationVersion(int version) { this.handledPregenRelocationVersion = version; markDirty(); }

    public void setDailyLuckForDate(double dailyLuck, int dateKey) {
        this.dailyLuck = dailyLuck;
        this.dailyLuckDateKey = dateKey;
        markDirty();
    }

    // ============ 临时Buff相关 ============

    public int getTempFishingLevelBonus() {
        return tempFishingLevelBonus;
    }

    public int getTempLuckBonus() {
        return tempLuckBonus;
    }

    public int getTempMaxEnergyBonus() {
        return tempMaxEnergyBonus;
    }

    public boolean hasActiveTempMaxEnergyBonus(long gameTime) {
        return tempMaxEnergyBonus > 0 && tempMaxEnergyBonusEndTick > gameTime;
    }

    public int getTempFarmingLevelBonus() {
        return tempFarmingLevelBonus;
    }

    public int getTempForagingLevelBonus() {
        return tempForagingLevelBonus;
    }

    public int getTempMiningLevelBonus() {
        return tempMiningLevelBonus;
    }

    public int getTempAttackBonus() {
        return tempAttackBonus;
    }

    public int getTempDefenseBonus() {
        return tempDefenseBonus;
    }

    public int getTempMagneticRadiusBonus() {
        return tempMagneticRadiusBonus;
    }

    /**
     * 直接设置临时Buff（用于从 MobEffect 同步）。
     * 不使用 max 叠加规则；仅当值发生变化时才 markDirty。
     */
    public boolean setTempFishingLevelBonusDirect(int bonus, long endTick) {
        bonus = Math.max(0, bonus);
        endTick = Math.max(0L, endTick);
        if (tempFishingLevelBonus == bonus && tempFishingLevelBonusEndTick == endTick) {
            return false;
        }
        tempFishingLevelBonus = bonus;
        tempFishingLevelBonusEndTick = endTick;
        markDirty();
        return true;
    }

    public boolean setTempLuckBonusDirect(int bonus, long endTick) {
        bonus = Math.max(0, bonus);
        endTick = Math.max(0L, endTick);
        if (tempLuckBonus == bonus && tempLuckBonusEndTick == endTick) {
            return false;
        }
        tempLuckBonus = bonus;
        tempLuckBonusEndTick = endTick;
        markDirty();
        return true;
    }

    public boolean setTempMaxEnergyBonusDirect(int bonus, long endTick) {
        bonus = Math.max(0, bonus);
        endTick = Math.max(0L, endTick);
        if (tempMaxEnergyBonus == bonus && tempMaxEnergyBonusEndTick == endTick) {
            return false;
        }
        tempMaxEnergyBonus = bonus;
        tempMaxEnergyBonusEndTick = endTick;
        // maxEnergy buff 影响能量上限，确保当前能量不超过新的上限
        this.energy = Math.min(this.energy, getEffectiveMaxEnergy());
        markDirty();
        return true;
    }

    public boolean setTempFarmingLevelBonusDirect(int bonus, long endTick) {
        bonus = Math.max(0, bonus);
        endTick = Math.max(0L, endTick);
        if (tempFarmingLevelBonus == bonus && tempFarmingLevelBonusEndTick == endTick) {
            return false;
        }
        tempFarmingLevelBonus = bonus;
        tempFarmingLevelBonusEndTick = endTick;
        markDirty();
        return true;
    }

    public boolean setTempForagingLevelBonusDirect(int bonus, long endTick) {
        bonus = Math.max(0, bonus);
        endTick = Math.max(0L, endTick);
        if (tempForagingLevelBonus == bonus && tempForagingLevelBonusEndTick == endTick) {
            return false;
        }
        tempForagingLevelBonus = bonus;
        tempForagingLevelBonusEndTick = endTick;
        markDirty();
        return true;
    }

    public boolean setTempMiningLevelBonusDirect(int bonus, long endTick) {
        bonus = Math.max(0, bonus);
        endTick = Math.max(0L, endTick);
        if (tempMiningLevelBonus == bonus && tempMiningLevelBonusEndTick == endTick) {
            return false;
        }
        tempMiningLevelBonus = bonus;
        tempMiningLevelBonusEndTick = endTick;
        markDirty();
        return true;
    }

    public boolean setTempAttackBonusDirect(int bonus, long endTick) {
        bonus = Math.max(0, bonus);
        endTick = Math.max(0L, endTick);
        if (tempAttackBonus == bonus && tempAttackBonusEndTick == endTick) {
            return false;
        }
        tempAttackBonus = bonus;
        tempAttackBonusEndTick = endTick;
        markDirty();
        return true;
    }

    public boolean setTempDefenseBonusDirect(int bonus, long endTick) {
        bonus = Math.max(0, bonus);
        endTick = Math.max(0L, endTick);
        if (tempDefenseBonus == bonus && tempDefenseBonusEndTick == endTick) {
            return false;
        }
        tempDefenseBonus = bonus;
        tempDefenseBonusEndTick = endTick;
        markDirty();
        return true;
    }

    public boolean setTempMagneticRadiusBonusDirect(int bonus, long endTick) {
        bonus = Math.max(0, bonus);
        endTick = Math.max(0L, endTick);
        if (tempMagneticRadiusBonus == bonus && tempMagneticRadiusBonusEndTick == endTick) {
            return false;
        }
        tempMagneticRadiusBonus = bonus;
        tempMagneticRadiusBonusEndTick = endTick;
        markDirty();
        return true;
    }

    public boolean clearTempFishingLevelBonus() {
        return setTempFishingLevelBonusDirect(0, 0L);
    }

    public boolean clearTempLuckBonus() {
        return setTempLuckBonusDirect(0, 0L);
    }

    public boolean clearTempMaxEnergyBonus() {
        return setTempMaxEnergyBonusDirect(0, 0L);
    }

    public boolean clearTempFarmingLevelBonus() {
        return setTempFarmingLevelBonusDirect(0, 0L);
    }

    public boolean clearTempForagingLevelBonus() {
        return setTempForagingLevelBonusDirect(0, 0L);
    }

    public boolean clearTempMiningLevelBonus() {
        return setTempMiningLevelBonusDirect(0, 0L);
    }

    public boolean clearTempAttackBonus() {
        return setTempAttackBonusDirect(0, 0L);
    }

    public boolean clearTempDefenseBonus() {
        return setTempDefenseBonusDirect(0, 0L);
    }

    public boolean clearTempMagneticRadiusBonus() {
        return setTempMagneticRadiusBonusDirect(0, 0L);
    }

    public void applyTempFishingLevelBonus(int bonus, long endTick) {
        this.tempFishingLevelBonus = Math.max(this.tempFishingLevelBonus, bonus);
        this.tempFishingLevelBonusEndTick = Math.max(this.tempFishingLevelBonusEndTick, endTick);
        markDirty();
    }

    public void applyTempLuckBonus(int bonus, long endTick) {
        this.tempLuckBonus = Math.max(this.tempLuckBonus, bonus);
        this.tempLuckBonusEndTick = Math.max(this.tempLuckBonusEndTick, endTick);
        markDirty();
    }

    public void applyTempMaxEnergyBonus(int bonus, long endTick) {
        this.tempMaxEnergyBonus = Math.max(this.tempMaxEnergyBonus, bonus);
        this.tempMaxEnergyBonusEndTick = Math.max(this.tempMaxEnergyBonusEndTick, endTick);
        // maxEnergy buff 影响能量上限，确保当前能量不超过新的上限
        this.energy = Math.min(this.energy, getEffectiveMaxEnergy());
        markDirty();
    }

    public void applyTempFarmingLevelBonus(int bonus, long endTick) {
        this.tempFarmingLevelBonus = Math.max(this.tempFarmingLevelBonus, bonus);
        this.tempFarmingLevelBonusEndTick = Math.max(this.tempFarmingLevelBonusEndTick, endTick);
        markDirty();
    }

    public void applyTempForagingLevelBonus(int bonus, long endTick) {
        this.tempForagingLevelBonus = Math.max(this.tempForagingLevelBonus, bonus);
        this.tempForagingLevelBonusEndTick = Math.max(this.tempForagingLevelBonusEndTick, endTick);
        markDirty();
    }

    public void applyTempMiningLevelBonus(int bonus, long endTick) {
        this.tempMiningLevelBonus = Math.max(this.tempMiningLevelBonus, bonus);
        this.tempMiningLevelBonusEndTick = Math.max(this.tempMiningLevelBonusEndTick, endTick);
        markDirty();
    }

    public void applyTempAttackBonus(int bonus, long endTick) {
        this.tempAttackBonus = Math.max(this.tempAttackBonus, bonus);
        this.tempAttackBonusEndTick = Math.max(this.tempAttackBonusEndTick, endTick);
        markDirty();
    }

    public void applyTempDefenseBonus(int bonus, long endTick) {
        this.tempDefenseBonus = Math.max(this.tempDefenseBonus, bonus);
        this.tempDefenseBonusEndTick = Math.max(this.tempDefenseBonusEndTick, endTick);
        markDirty();
    }

    public void applyTempMagneticRadiusBonus(int bonus, long endTick) {
        this.tempMagneticRadiusBonus = Math.max(this.tempMagneticRadiusBonus, bonus);
        this.tempMagneticRadiusBonusEndTick = Math.max(this.tempMagneticRadiusBonusEndTick, endTick);
        markDirty();
    }

    /**
     * 在服务端 tick 中调用，用于清理过期 buff。
     * @return 是否发生变化（需要同步到客户端）
     */
    public boolean tickTimedBuffs(long gameTime) {
        boolean changed = false;

        if (tempFishingLevelBonus != 0 && gameTime >= tempFishingLevelBonusEndTick) {
            tempFishingLevelBonus = 0;
            tempFishingLevelBonusEndTick = 0L;
            changed = true;
        }
        if (tempLuckBonus != 0 && gameTime >= tempLuckBonusEndTick) {
            tempLuckBonus = 0;
            tempLuckBonusEndTick = 0L;
            changed = true;
        }
        if (tempMaxEnergyBonus != 0 && gameTime >= tempMaxEnergyBonusEndTick) {
            tempMaxEnergyBonus = 0;
            tempMaxEnergyBonusEndTick = 0L;
            // buff 结束后，能量上限降低，夹紧当前能量
            energy = Math.min(energy, getEffectiveMaxEnergy());
            changed = true;
        }
        if (tempFarmingLevelBonus != 0 && gameTime >= tempFarmingLevelBonusEndTick) {
            tempFarmingLevelBonus = 0;
            tempFarmingLevelBonusEndTick = 0L;
            changed = true;
        }
        if (tempForagingLevelBonus != 0 && gameTime >= tempForagingLevelBonusEndTick) {
            tempForagingLevelBonus = 0;
            tempForagingLevelBonusEndTick = 0L;
            changed = true;
        }
        if (tempMiningLevelBonus != 0 && gameTime >= tempMiningLevelBonusEndTick) {
            tempMiningLevelBonus = 0;
            tempMiningLevelBonusEndTick = 0L;
            changed = true;
        }
        if (tempAttackBonus != 0 && gameTime >= tempAttackBonusEndTick) {
            tempAttackBonus = 0;
            tempAttackBonusEndTick = 0L;
            changed = true;
        }
        if (tempDefenseBonus != 0 && gameTime >= tempDefenseBonusEndTick) {
            tempDefenseBonus = 0;
            tempDefenseBonusEndTick = 0L;
            changed = true;
        }
        if (tempMagneticRadiusBonus != 0 && gameTime >= tempMagneticRadiusBonusEndTick) {
            tempMagneticRadiusBonus = 0;
            tempMagneticRadiusBonusEndTick = 0L;
            changed = true;
        }

        if (changed) {
            markDirty();
        }
        return changed;
    }
    
    public void markDirty() {
        this.dirty = true;
        this.lastSyncTime = System.currentTimeMillis();
        com.stardew.craft.leaderboard.LeaderboardService.invalidateCache();
    }
    
    public void markClean() {
        this.dirty = false;
    }
}

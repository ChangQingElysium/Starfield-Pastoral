package com.stardew.craft.quest;

import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * SDV Quest 基类 — 1:1 复刻 StardewValley.Quests.Quest
 */
@SuppressWarnings("null")
public class StardewQuest {

    // ─── 类型常量（SDV Quest.type_xxx） ───
    public static final int TYPE_BASIC     = 1;
    public static final int TYPE_CRAFTING  = 2;
    public static final int TYPE_DELIVERY  = 3;
    public static final int TYPE_MONSTER   = 4;
    public static final int TYPE_SOCIALIZE = 5;
    public static final int TYPE_LOCATION  = 6;
    public static final int TYPE_FISHING   = 7;
    public static final int TYPE_BUILDING  = 8;
    public static final int TYPE_HARVEST   = 9;
    public static final int TYPE_RESOURCE  = 10;
    public static final int TYPE_WEEDING   = 11;
    public static final int TYPE_DATA_DRIVEN = 12;

    // ─── 核心字段（SDV NetFields） ───
    protected String id = "";
    @Nullable
    protected ResourceLocation definitionId;
    protected int questType = TYPE_BASIC;
    /** Legacy literal 文案；为空且本地化 key 为空时才显示。新代码用 {@link #titleKey}。 */
    protected String title = "";
    protected String description = "";
    protected String objectiveText = "";
    // ─── 本地化字段（优先于 legacy 文本） ───
    /** 翻译键（空 = 使用 legacy title）。 */
    protected String titleKey = "";
    protected String descriptionKey = "";
    protected String objectiveKey = "";
    /** 翻译参数。字符串形式；在 getComponent() 里智能包装成 Component：
     *  以 "item."/"entity."/"stardewcraft." 开头的 arg 会自动 `Component.translatable` 包一层，
     *  让物品名/NPC 名跟随客户端语言解析；其他 arg 保持字面量（数字等）。 */
    protected List<String> titleArgs = new ArrayList<>();
    protected List<String> descriptionArgs = new ArrayList<>();
    protected List<String> objectiveArgs = new ArrayList<>();
    @Nullable
    protected String rewardDescription;
    protected int moneyReward;
    protected boolean accepted;
    protected boolean completed;
    protected boolean dailyQuest;
    protected boolean showNew;
    protected boolean canBeCancelled;
    protected boolean destroy;
    protected boolean notifiedComplete;
    protected int daysLeft = -1;
    protected int dayQuestAccepted = -1;
    protected List<String> nextQuests = new ArrayList<>();

    // ─── 构造 ───

    public StardewQuest() {}

    public StardewQuest(String id) {
        this.id = id;
    }

    // ─── 事件回调（子类重写） ───

    public void onAccept(ServerPlayer player) {}

    public void onMonsterSlain(ServerPlayer player, String monsterType) {}

    public void onFishCaught(ServerPlayer player, String itemId, int count) {}

    public void onItemReceived(ServerPlayer player, String itemId, int count) {}

    /** @return true if this quest consumed the offered item (SDV: intercepts before gift processing) */
    public boolean onItemOfferedToNpc(ServerPlayer player, String npcId, String itemId) { return false; }

    public void onRecipeCrafted(ServerPlayer player, String recipeId) {}

    public void onNpcSocialized(ServerPlayer player, String npcId) {}

    public void onWarped(ServerPlayer player, String location) {}

    public void onBuildingExists(ServerPlayer player, String buildingType) {}

    public void onMineFloorReached(ServerPlayer player, int floor) {}

    // ─── 显示接口 ───

    /** 把字符串 arg 按规则打包成 Component 数组。前缀识别：
     *  item.* / entity.* / stardewcraft.* → 走 translatable；其它保持 literal。 */
    protected static Object[] buildArgs(List<String> rawArgs) {
        Object[] out = new Object[rawArgs.size()];
        for (int i = 0; i < rawArgs.size(); i++) {
            String a = rawArgs.get(i);
            if (a != null && (a.startsWith("item.") || a.startsWith("entity.")
                    || a.startsWith("stardewcraft."))) {
                out[i] = Component.translatable(a);
            } else {
                out[i] = a;
            }
        }
        return out;
    }

    /** 返回标题 Component：优先用 titleKey，回退到 legacy title literal。 */
    public Component getTitleComponent() {
        if (titleKey != null && !titleKey.isEmpty()) {
            return Component.translatable(titleKey, buildArgs(titleArgs));
        }
        return Component.literal(title == null ? "" : title);
    }

    public Component getDescriptionComponent() {
        if (descriptionKey != null && !descriptionKey.isEmpty()) {
            return Component.translatable(descriptionKey, buildArgs(descriptionArgs));
        }
        return Component.literal(description == null ? "" : description);
    }

    public Component getObjectiveComponent() {
        if (objectiveKey != null && !objectiveKey.isEmpty()) {
            return Component.translatable(objectiveKey, buildArgs(objectiveArgs));
        }
        return Component.literal(objectiveText == null ? "" : objectiveText);
    }

    /** 设置本地化标题（翻译键 + 字符串 args）。 */
    public void setLocalizedTitle(String key, String... args) {
        this.titleKey = key == null ? "" : key;
        this.titleArgs.clear();
        if (args != null) for (String a : args) this.titleArgs.add(a);
    }

    public void setLocalizedDescription(String key, String... args) {
        this.descriptionKey = key == null ? "" : key;
        this.descriptionArgs.clear();
        if (args != null) for (String a : args) this.descriptionArgs.add(a);
    }

    public void setLocalizedObjective(String key, String... args) {
        this.objectiveKey = key == null ? "" : key;
        this.objectiveArgs.clear();
        if (args != null) for (String a : args) this.objectiveArgs.add(a);
    }

    public List<String> getObjectiveDescriptions() {
        // 优先本地化
        if (objectiveKey != null && !objectiveKey.isEmpty()) {
            return Collections.singletonList(getObjectiveComponent().getString());
        }
        if (objectiveText != null && !objectiveText.isEmpty()) {
            return Collections.singletonList(objectiveText);
        }
        return Collections.emptyList();
    }

    /** 动态（含当前进度）的 objective Component 列表。子类可重写以返回多行本地化内容。 */
    public List<Component> getObjectiveComponents() {
        Component single = getObjectiveComponent();
        if (single.getString().isEmpty()) return Collections.emptyList();
        return Collections.singletonList(single);
    }

    /** Current progress count for objectives with numeric progress, or -1 if not applicable. */
    public int getCurrentObjectiveCount() { return -1; }
    /** Total target count for objectives with numeric progress, or -1 if not applicable. */
    public int getTotalObjectiveCount() { return -1; }

    public boolean isTimedQuest() {
        return daysLeft > 0;
    }

    public boolean shouldDisplayAsNew() {
        return showNew;
    }

    public boolean shouldDisplayAsComplete() {
        return completed;
    }

    public boolean hasReward() {
        return moneyReward > 0 || (rewardDescription != null && !rewardDescription.isEmpty());
    }

    public boolean hasMoneyReward() {
        return moneyReward > 0;
    }

    // ─── 完成逻辑（SDV Quest.questComplete） ───

    public void questComplete(ServerPlayer player) {
        if (completed) return;
        completed = true;

        // dailyQuest 统计
        if (dailyQuest) {
            QuestManager mgr = QuestManager.of(player);
            if (mgr != null) {
                mgr.incrementBillboardQuestsDone();
                int billboardQuestsDone = mgr.getBillboardQuestsDone();
                PlayerStardewData data = PlayerDataManager.getPlayerData(player);
                if (billboardQuestsDone >= 1) {
                    data.addMailFlag("completedFirstBillboardQuest");
                }
                if (billboardQuestsDone % 3 == 0) {
                    givePrizeTicket(player);
                }
                if (billboardQuestsDone >= 6) {
                    data.addMailFlag("gotFirstBillboardPrizeTicket");
                }
                PlayerDataEventHandler.syncPlayerData(player, data);
                // 记录当季第几天完成了每日任务（日历星标用）
                try {
                    com.stardew.craft.time.StardewTimeManager tm = com.stardew.craft.time.StardewTimeManager.get();
                    if (tm != null) {
                        mgr.markDailyQuestCompletedDay(tm.getCurrentDay());
                    }
                } catch (Exception ignored) {}
            }
        }

        // 播放完成音效（由 QuestManager 发送网络包触发客户端播放）

        // 如果没有可领取的奖励，立即标记销毁
        if (moneyReward <= 0 && (rewardDescription == null || rewardDescription.isEmpty())) {
            destroy = true;
        }
    }

    private static void givePrizeTicket(ServerPlayer player) {
        ItemStack ticket = new ItemStack(com.stardew.craft.item.ModItems.PRIZE_TICKET.get());
        ItemStack hudStack = ticket.copy();
        if (!player.getInventory().add(ticket)) {
            player.drop(ticket, false);
        }
        com.stardew.craft.network.ItemPickupHudPacket.sendTo(player, hudStack, hudStack.getCount(), false);
        player.inventoryMenu.broadcastChanges();
    }

    // ─── 天数推进 ───

    /**
     * SDV 在天结束时递减 daysLeft。我们在天开始时调用，
     * 所以跳过接受任务当天的 tick，确保 daysLeft=2 给玩家 2 个完整天。
     */
    public void tickDay(int currentGameDay) {
        if (daysLeft > 0) {
            // 接受当天不 tick（SDV 是天结束时递减）
            if (dayQuestAccepted >= 0 && dayQuestAccepted >= currentGameDay) return;
            daysLeft--;
            if (daysLeft <= 0 && !completed) {
                destroy = true;
            }
        }
    }

    // ─── NBT 序列化 ───

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id);
        if (definitionId != null) tag.putString("DefinitionId", definitionId.toString());
        tag.putInt("Type", questType);
        tag.putString("Title", title);
        tag.putString("Description", description);
        tag.putString("ObjectiveText", objectiveText);
        if (rewardDescription != null) tag.putString("RewardDescription", rewardDescription);
        tag.putInt("MoneyReward", moneyReward);
        tag.putBoolean("Accepted", accepted);
        tag.putBoolean("Completed", completed);
        tag.putBoolean("DailyQuest", dailyQuest);
        tag.putBoolean("ShowNew", showNew);
        tag.putBoolean("CanBeCancelled", canBeCancelled);
        tag.putBoolean("Destroy", destroy);
        tag.putBoolean("NotifiedComplete", notifiedComplete);
        tag.putInt("DaysLeft", daysLeft);
        tag.putInt("DayQuestAccepted", dayQuestAccepted);
        if (!nextQuests.isEmpty()) {
            ListTag list = new ListTag();
            for (String nq : nextQuests) list.add(StringTag.valueOf(nq));
            tag.put("NextQuests", list);
        }
        // 本地化字段（空字符串不写，节省 NBT 体积；老存档缺失时读取会得到默认空值）
        if (titleKey != null && !titleKey.isEmpty()) tag.putString("TitleKey", titleKey);
        if (descriptionKey != null && !descriptionKey.isEmpty()) tag.putString("DescriptionKey", descriptionKey);
        if (objectiveKey != null && !objectiveKey.isEmpty()) tag.putString("ObjectiveKey", objectiveKey);
        if (!titleArgs.isEmpty()) {
            ListTag list = new ListTag();
            for (String a : titleArgs) list.add(StringTag.valueOf(a == null ? "" : a));
            tag.put("TitleArgs", list);
        }
        if (!descriptionArgs.isEmpty()) {
            ListTag list = new ListTag();
            for (String a : descriptionArgs) list.add(StringTag.valueOf(a == null ? "" : a));
            tag.put("DescriptionArgs", list);
        }
        if (!objectiveArgs.isEmpty()) {
            ListTag list = new ListTag();
            for (String a : objectiveArgs) list.add(StringTag.valueOf(a == null ? "" : a));
            tag.put("ObjectiveArgs", list);
        }
        saveExtra(tag);
        return tag;
    }

    /** Persistent form for definition-backed quests: immutable definition fields stay in datapacks. */
    public CompoundTag saveState() {
        if (definitionId == null || !(this instanceof DataDrivenQuest)) {
            return save();
        }
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("StateOnly", true);
        tag.putString("Id", id);
        tag.putString("DefinitionId", definitionId.toString());
        tag.putInt("Type", questType);
        saveMutableFields(tag);
        saveStateExtra(tag);
        return tag;
    }

    private void saveMutableFields(CompoundTag tag) {
        tag.putBoolean("Accepted", accepted);
        tag.putBoolean("Completed", completed);
        tag.putBoolean("DailyQuest", dailyQuest);
        tag.putBoolean("ShowNew", showNew);
        tag.putBoolean("CanBeCancelled", canBeCancelled);
        tag.putBoolean("Destroy", destroy);
        tag.putBoolean("NotifiedComplete", notifiedComplete);
        tag.putInt("DaysLeft", daysLeft);
        tag.putInt("DayQuestAccepted", dayQuestAccepted);
    }

    /** 子类重写以保存额外字段 */
    protected void saveExtra(CompoundTag tag) {}

    /** Definition-backed persistent state hook. */
    protected void saveStateExtra(CompoundTag tag) { saveExtra(tag); }

    /** 子类重写以加载额外字段 */
    protected void loadExtra(CompoundTag tag) {}

    /** Definition-backed persistent state hook. */
    protected void loadStateExtra(CompoundTag tag) { loadExtra(tag); }

    public static StardewQuest load(CompoundTag tag) {
        int type = tag.getInt("Type");
        ResourceLocation savedDefinitionId = tag.contains("DefinitionId", 8)
                ? ResourceLocation.tryParse(tag.getString("DefinitionId")) : null;
        if (tag.getBoolean("StateOnly") && savedDefinitionId != null) {
            StardewQuest restored = QuestDataLoader.createQuest(savedDefinitionId.toString());
            if (restored == null) {
                restored = createByType(type);
                restored.definitionId = savedDefinitionId;
                restored.id = tag.getString("Id");
                restored.title = "Unavailable quest: " + savedDefinitionId;
                restored.canBeCancelled = true;
            }
            restored.loadMutableFields(tag);
            restored.loadStateExtra(tag);
            return restored;
        }
        StardewQuest quest = createByType(type);
        quest.definitionId = savedDefinitionId;
        quest.id = tag.getString("Id");
        quest.questType = type;
        quest.title = tag.getString("Title");
        quest.description = tag.getString("Description");
        quest.objectiveText = tag.getString("ObjectiveText");
        quest.rewardDescription = tag.contains("RewardDescription") ? tag.getString("RewardDescription") : null;
        quest.moneyReward = tag.getInt("MoneyReward");
        quest.accepted = tag.getBoolean("Accepted");
        quest.completed = tag.getBoolean("Completed");
        quest.dailyQuest = tag.getBoolean("DailyQuest");
        quest.showNew = tag.getBoolean("ShowNew");
        quest.canBeCancelled = tag.getBoolean("CanBeCancelled");
        quest.destroy = tag.getBoolean("Destroy");
        quest.notifiedComplete = tag.getBoolean("NotifiedComplete");
        quest.daysLeft = tag.getInt("DaysLeft");
        quest.dayQuestAccepted = tag.getInt("DayQuestAccepted");
        if (tag.contains("NextQuests", 9)) {
            ListTag list = tag.getList("NextQuests", 8);
            for (int i = 0; i < list.size(); i++) {
                quest.nextQuests.add(list.getString(i));
            }
        }
        // 本地化字段（老存档没有则保持空字符串/空列表 → getTitleComponent 自动回退到 legacy title）
        if (tag.contains("TitleKey", 8)) quest.titleKey = tag.getString("TitleKey");
        if (tag.contains("DescriptionKey", 8)) quest.descriptionKey = tag.getString("DescriptionKey");
        if (tag.contains("ObjectiveKey", 8)) quest.objectiveKey = tag.getString("ObjectiveKey");
        if (tag.contains("TitleArgs", 9)) {
            ListTag list = tag.getList("TitleArgs", 8);
            for (int i = 0; i < list.size(); i++) quest.titleArgs.add(list.getString(i));
        }
        if (tag.contains("DescriptionArgs", 9)) {
            ListTag list = tag.getList("DescriptionArgs", 8);
            for (int i = 0; i < list.size(); i++) quest.descriptionArgs.add(list.getString(i));
        }
        if (tag.contains("ObjectiveArgs", 9)) {
            ListTag list = tag.getList("ObjectiveArgs", 8);
            for (int i = 0; i < list.size(); i++) quest.objectiveArgs.add(list.getString(i));
        }
        quest.loadExtra(tag);
        return quest;
    }

    private void loadMutableFields(CompoundTag tag) {
        accepted = tag.getBoolean("Accepted");
        completed = tag.getBoolean("Completed");
        dailyQuest = tag.getBoolean("DailyQuest");
        showNew = tag.getBoolean("ShowNew");
        canBeCancelled = tag.getBoolean("CanBeCancelled");
        destroy = tag.getBoolean("Destroy");
        notifiedComplete = tag.getBoolean("NotifiedComplete");
        daysLeft = tag.getInt("DaysLeft");
        dayQuestAccepted = tag.getInt("DayQuestAccepted");
    }

    private static StardewQuest createByType(int type) {
        return switch (type) {
            case TYPE_CRAFTING  -> new CraftingQuest();
            case TYPE_DELIVERY  -> new ItemDeliveryQuest();
            case TYPE_MONSTER   -> new SlayMonsterQuest();
            case TYPE_SOCIALIZE -> new SocializeQuest();
            case TYPE_LOCATION  -> new GoSomewhereQuest();
            case TYPE_FISHING   -> new FishingQuest();
            case TYPE_BUILDING  -> new HaveBuildingQuest();
            case TYPE_HARVEST   -> new ItemHarvestQuest();
            case TYPE_RESOURCE  -> new ResourceCollectionQuest();
            case TYPE_DATA_DRIVEN -> new DataDrivenQuest();
            default             -> new StardewQuest();
        };
    }

    // ─── Getters / Setters ───

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public int getQuestType() { return questType; }
    public void setQuestType(int questType) { this.questType = questType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getObjectiveText() { return objectiveText; }
    public void setObjectiveText(String objectiveText) { this.objectiveText = objectiveText; }
    public String getTitleKey() { return titleKey; }
    public String getDescriptionKey() { return descriptionKey; }
    public String getObjectiveKey() { return objectiveKey; }
    @Nullable public String getRewardDescription() { return rewardDescription; }
    public void setRewardDescription(@Nullable String rewardDescription) { this.rewardDescription = rewardDescription; }
    public int getMoneyReward() { return moneyReward; }
    public void setMoneyReward(int moneyReward) { this.moneyReward = moneyReward; }
    public boolean isAccepted() { return accepted; }
    public void setAccepted(boolean accepted) { this.accepted = accepted; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
    public boolean isDailyQuest() { return dailyQuest; }
    public void setDailyQuest(boolean dailyQuest) { this.dailyQuest = dailyQuest; }
    public boolean isShowNew() { return showNew; }
    public void setShowNew(boolean showNew) { this.showNew = showNew; }
    public boolean isCanBeCancelled() { return canBeCancelled; }
    public void setCanBeCancelled(boolean canBeCancelled) { this.canBeCancelled = canBeCancelled; }
    public boolean isDestroy() { return destroy; }
    public void setDestroy(boolean destroy) { this.destroy = destroy; }
    public boolean isNotifiedComplete() { return notifiedComplete; }
    public void setNotifiedComplete(boolean notifiedComplete) { this.notifiedComplete = notifiedComplete; }
    public int getDaysLeft() { return daysLeft; }
    public void setDaysLeft(int daysLeft) { this.daysLeft = daysLeft; }
    public int getDayQuestAccepted() { return dayQuestAccepted; }
    public void setDayQuestAccepted(int dayQuestAccepted) { this.dayQuestAccepted = dayQuestAccepted; }
    public List<String> getNextQuests() { return nextQuests; }
    public void setNextQuests(List<String> nextQuests) { this.nextQuests = nextQuests; }
    @Nullable public ResourceLocation getDefinitionId() { return definitionId; }
    public boolean matchesItemDelivery(String npcId, String itemId) { return false; }
    public String getDeliveryTargetMessage() { return ""; }
    public boolean isSecretQuest() { return false; }
}

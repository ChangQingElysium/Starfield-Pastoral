package com.stardew.craft.quest;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.stardew.craft.StardewCraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * SDV Data/Quests 数据加载器
 * 从 data/stardewcraft/quests.json 加载任务定义
 *
 * JSON 格式：{ "questId": "type/title/desc/objective/conditions/nextQuests/money/rewardDesc/canCancel/targetMsg" }
 */
@SuppressWarnings("null")
public class QuestDataLoader {

    private static final Gson GSON = new Gson();
    private static final Map<String, String> RAW_DATA = new HashMap<>();

    /**
     * 加载 quests.json（应在服务端启动时调用）
     */
    public static void load(ResourceManager resourceManager) {
        RAW_DATA.clear();

        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
                StardewCraft.MODID,
                "quests.json"
        );

        Optional<Resource> resource = resourceManager.getResource(location);

        if (resource.isEmpty()) {
            StardewCraft.LOGGER.warn("[Quest] quests.json not found, no quests loaded");
            return;
        }

        try (InputStreamReader reader = new InputStreamReader(
                resource.get().open(),
                StandardCharsets.UTF_8
        )) {

            Type mapType = new TypeToken<Map<String, String>>() {}.getType();
            Map<String, String> parsed = GSON.fromJson(reader, mapType);

            if (parsed != null) {
                RAW_DATA.putAll(parsed);
            }

            StardewCraft.LOGGER.info("[Quest] Loaded {} quest definitions", RAW_DATA.size());

        } catch (Exception e) {
            StardewCraft.LOGGER.error("[Quest] Failed to load quests.json", e);
        }
    }

    /**
     * 根据 questId 创建一个新的 Quest 实例（从 JSON 数据初始化）
     */
    @Nullable
    public static StardewQuest createQuest(String questId) {
        String raw = RAW_DATA.get(questId);
        if (raw == null) return null;
        return parseQuestData(questId, raw);
    }

    /**
     * 获取所有已加载的 questId
     */
    public static java.util.Set<String> getAllQuestIds() {
        return RAW_DATA.keySet();
    }

    /**
     * 解析 SDV Data/Quests 格式的 '/' 分隔字符串
     *
     * index 0: questType string
     * index 1: title
     * index 2: description
     * index 3: objective (可空)
     * index 4: conditions (空格分隔, 类型相关)
     * index 5: nextQuests (空格分隔 ID, "h"前缀=仅主机)
     * index 6: moneyReward (-1=无)
     * index 7: rewardDescription
     * index 8: canBeCancelled (true/false)
     * index 9: targetMessage (仅部分类型)
     */
    @Nullable
    private static StardewQuest parseQuestData(String questId, String raw) {
        String[] parts = raw.split("/", -1);
        if (parts.length < 9) {
            StardewCraft.LOGGER.warn("[Quest] Invalid quest data for {}: only {} fields", questId, parts.length);
            return null;
        }

        String typeStr = parts[0].trim();
        int questType = parseQuestType(typeStr);

        StardewQuest quest = createByTypeId(questType);
        quest.setId(questId);
        quest.setQuestType(questType);
        quest.setTitle(parts[1].trim());
        quest.setDescription(parts[2].trim());
        quest.setObjectiveText(parts[3].trim());

        // conditions (index 4) — 类型相关, 解析到子类字段
        String conditions = parts[4].trim();
        parseConditions(quest, conditions);

        // nextQuests (index 5)
        String nextQuestsStr = parts[5].trim();
        if (!nextQuestsStr.isEmpty() && !"null".equalsIgnoreCase(nextQuestsStr) && !"-1".equals(nextQuestsStr)) {
            List<String> nextQuests = new ArrayList<>();
            for (String nq : nextQuestsStr.split("\\s+")) {
                String cleaned = nq.startsWith("h") ? nq.substring(1) : nq;
                if (!cleaned.isEmpty() && !"null".equalsIgnoreCase(cleaned) && !"-1".equals(cleaned)) nextQuests.add(cleaned);
            }
            quest.setNextQuests(nextQuests);
        }

        // moneyReward (index 6)
        try {
            int money = Integer.parseInt(parts[6].trim());
            quest.setMoneyReward(Math.max(0, money));
        } catch (NumberFormatException ignored) {}

        // rewardDescription (index 7)
        String rewardDesc = parts[7].trim();
        quest.setRewardDescription(rewardDesc.isEmpty() || "null".equalsIgnoreCase(rewardDesc) || "-1".equals(rewardDesc) ? null : rewardDesc);

        // canBeCancelled (index 8)
        quest.setCanBeCancelled("true".equalsIgnoreCase(parts[8].trim()));

        // targetMessage (index 9, 可选)
        if (parts.length > 9) {
            String targetMsg = parts[9].trim();
            if (!targetMsg.isEmpty()) {
                applyTargetMessage(quest, targetMsg);
            }
        }

        return quest;
    }

    private static int parseQuestType(String typeStr) {
        return switch (typeStr) {
            case "Basic" -> StardewQuest.TYPE_BASIC;
            case "Crafting" -> StardewQuest.TYPE_CRAFTING;
            case "ItemDelivery" -> StardewQuest.TYPE_DELIVERY;
            case "Monster" -> StardewQuest.TYPE_MONSTER;
            case "Social" -> StardewQuest.TYPE_SOCIALIZE;
            case "Location" -> StardewQuest.TYPE_LOCATION;
            case "Fishing" -> StardewQuest.TYPE_FISHING;
            case "Building" -> StardewQuest.TYPE_BUILDING;
            case "ItemHarvest" -> StardewQuest.TYPE_HARVEST;
            case "Resource", "ResourceCollect" -> StardewQuest.TYPE_RESOURCE;
            case "LostItem", "SecretLostItem" -> StardewQuest.TYPE_BASIC; // Phase 1 简化
            default -> StardewQuest.TYPE_BASIC;
        };
    }

    private static StardewQuest createByTypeId(int type) {
        return switch (type) {
            case StardewQuest.TYPE_CRAFTING  -> new CraftingQuest();
            case StardewQuest.TYPE_DELIVERY  -> new ItemDeliveryQuest();
            case StardewQuest.TYPE_MONSTER   -> new SlayMonsterQuest();
            case StardewQuest.TYPE_SOCIALIZE -> new SocializeQuest();
            case StardewQuest.TYPE_LOCATION  -> new GoSomewhereQuest();
            case StardewQuest.TYPE_FISHING   -> new FishingQuest();
            case StardewQuest.TYPE_BUILDING  -> new HaveBuildingQuest();
            case StardewQuest.TYPE_HARVEST   -> new ItemHarvestQuest();
            case StardewQuest.TYPE_RESOURCE  -> new ResourceCollectionQuest();
            default -> new StardewQuest();
        };
    }

    /**
     * 解析 conditions 字段到子类
     * SDV conditions 格式因类型而异:
     * - ItemDelivery: "npcName itemId"
     * - Monster: "monsterName numberToKill targetNpc"
     * - Fishing: (conditions 通常为空, 由 Billboard 动态生成)
     * - Location: "locationName"
     * - Building: "buildingType"
     * - Resource: "itemId number targetNpc"
     */
    private static void parseConditions(StardewQuest quest, String conditions) {
        if (conditions.isEmpty()) return;
        String[] tokens = conditions.split("\\s+");

        if (quest instanceof ItemDeliveryQuest idq && tokens.length >= 2) {
            idq.setTargetNpc(tokens[0]);
            idq.setItemId(tokens[1]);
            if (tokens.length >= 3) {
                try { idq.setNumber(Integer.parseInt(tokens[2])); } catch (NumberFormatException ignored) {}
            }
        } else if (quest instanceof SlayMonsterQuest smq && tokens.length >= 2) {
            smq.setMonsterName(tokens[0]);
            try { smq.setNumberToKill(Integer.parseInt(tokens[1])); } catch (NumberFormatException ignored) {}
            if (tokens.length >= 3) smq.setTargetNpc(tokens[2]);
        } else if (quest instanceof GoSomewhereQuest gsq) {
            gsq.setWhereToGo(tokens[0]);
        } else if (quest instanceof HaveBuildingQuest hbq) {
            hbq.setBuildingType(tokens[0]);
        } else if (quest instanceof ResourceCollectionQuest rcq && tokens.length >= 2) {
            rcq.setItemId(tokens[0]);
            try { rcq.setNumber(Integer.parseInt(tokens[1])); } catch (NumberFormatException ignored) {}
            if (tokens.length >= 3) rcq.setTargetNpc(tokens[2]);
        } else if (quest instanceof CraftingQuest cq) {
            cq.setItemId(tokens[0]);
        } else if (quest instanceof FishingQuest fq && tokens.length >= 2) {
            // Format: [npc] itemId numberToFish — 支持带NPC前缀的3-token格式
            if (tokens.length >= 3) {
                fq.setTargetNpc(tokens[0]);
                fq.setItemId(tokens[1]);
                try { fq.setNumberToFish(Integer.parseInt(tokens[2])); } catch (NumberFormatException ignored) {}
            } else {
                fq.setItemId(tokens[0]);
                try { fq.setNumberToFish(Integer.parseInt(tokens[1])); } catch (NumberFormatException ignored) {}
            }
        } else if (quest instanceof ItemHarvestQuest ihq && tokens.length >= 2) {
            ihq.setItemId(tokens[0]);
            try { ihq.setNumber(Integer.parseInt(tokens[1])); } catch (NumberFormatException ignored) {}
        }
    }

    private static void applyTargetMessage(StardewQuest quest, String targetMsg) {
        if (quest instanceof ItemDeliveryQuest idq) {
            idq.setTargetMessage(targetMsg);
        }
    }
}

package com.stardew.craft.client;

import com.stardew.craft.player.SkillType;
import com.stardew.craft.player.ProfessionType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 客户端玩家数据缓存
 * 用于在HUD等客户端界面显示同步的服务端数据
 */
public class ClientPlayerDataCache {
    
    private static int health = 100;
    private static int maxHealth = 100;
    private static float energy = 270.0f;
    private static int maxEnergy = 270;
    private static int baseMaxEnergy = 270;
    private static int money = 500;  // 默认500金币
    private static int[] experience = new int[5];
    private static int[] skillLevels = new int[5];
    private static List<String> professions = new ArrayList<>();
    private static final java.util.Set<String> unlockedRecipes = new java.util.HashSet<>();
    private static final java.util.Map<String, Integer> recipeCraftCounts = new java.util.HashMap<>();
    private static final java.util.Map<String, Integer> fishCatchCounts = new java.util.HashMap<>();
    private static final java.util.Set<String> shippedBasic = new java.util.HashSet<>();
    private static final java.util.Map<String, Integer> itemsShipped = new java.util.HashMap<>();
    private static final java.util.Set<String> mailFlags = new java.util.HashSet<>();
    private static final java.util.Set<String> specialItems = new java.util.HashSet<>();
    private static final java.util.Set<String> secretNotesSeen = new java.util.HashSet<>();
    private static final java.util.Set<String> revealedGiftTastes = new java.util.HashSet<>();
    private static final java.util.Map<String, Integer> stats = new java.util.HashMap<>();
    private static int ticketPrizesClaimed = 0;
    private static int specialOrderPrizeTickets = 0;
    private static int fairStarTokens = 0;
    private static boolean fairStarTokenHudActive = false;
    private static int maxMineFloorReached = 0;
    private static int firstJoinDay = -1;
    private static String winterStarRecipient = "";
    private static int gender = -1;
    private static String preferredName = "";
    private static String favoriteThing = "";

    // 临时Buff（客户端显示/计算用）
    private static int tempFishingLevelBonus = 0;
    private static int tempLuckBonus = 0;
    private static int tempMaxEnergyBonus = 0;
    private static int tempFarmingLevelBonus = 0;
    private static int tempForagingLevelBonus = 0;
    private static int tempMiningLevelBonus = 0;

    // 农场名
    private static boolean hasFarm = false;
    private static String farmName = "";
    private static String farmOwnerUuid = "";

    // 装备槽
    private static ItemStack equippedLeftRing = ItemStack.EMPTY;
    private static ItemStack equippedRightRing = ItemStack.EMPTY;
    private static ItemStack equippedBoots = ItemStack.EMPTY;
    private static ItemStack equippedTrinket = ItemStack.EMPTY;
    private static String equippedHat = "";
    private static String equippedShirt = "";
    private static String equippedPants = "";
    private static final Map<UUID, CosmeticAppearance> cosmeticAppearances = new HashMap<>();

    /**
     * True after the server has sent at least one PlayerDataSyncPacket for this session.
     * Gates logic that would otherwise false-negative on an empty cache (e.g. cutscene
     * {@code not_mail}/{@code not_flag} preconditions incorrectly passing before sync).
     */
    // ============ 精通系统 ============
    private static long masteryExp = 0L;
    private static int masteryLevelsSpent = 0;
    private static final boolean[] claimedMasteryRewards = new boolean[5];
    private static boolean gotMasteryHint = false;
    private static boolean visitedMasteryCave = false;
    private static int unlockedTrinketSlots = 0;

    private static volatile boolean syncedFromServer = false;

    public static boolean isSynced() { return syncedFromServer; }

    /**
     * 从NBT更新缓存
     */
    public static void updateFromNBT(CompoundTag nbt) {
        health = nbt.getInt("Health");
        maxHealth = nbt.getInt("MaxHealth");
        energy = nbt.getFloat("Energy");
        baseMaxEnergy = nbt.contains("MaxEnergy") ? nbt.getInt("MaxEnergy") : 270;
        tempMaxEnergyBonus = nbt.contains("TempMaxEnergyBonus") ? nbt.getInt("TempMaxEnergyBonus") : 0;
        maxEnergy = baseMaxEnergy + tempMaxEnergyBonus;
        money = nbt.getInt("Money");
        maxMineFloorReached = nbt.contains("MaxMineFloorReached") ? Math.max(0, nbt.getInt("MaxMineFloorReached")) : 0;
        ticketPrizesClaimed = nbt.contains("TicketPrizesClaimed") ? Math.max(0, nbt.getInt("TicketPrizesClaimed")) : 0;
        specialOrderPrizeTickets = nbt.contains("SpecialOrderPrizeTickets") ? Math.max(0, nbt.getInt("SpecialOrderPrizeTickets")) : 0;
        fairStarTokens = nbt.contains("FairStarTokens") ? Math.max(0, nbt.getInt("FairStarTokens")) : 0;
        firstJoinDay = nbt.contains("FirstJoinDay") ? nbt.getInt("FirstJoinDay") : -1;
        winterStarRecipient = nbt.contains("WinterStarRecipient") ? nbt.getString("WinterStarRecipient") : "";
        gender = nbt.contains("Gender") ? Math.max(-1, Math.min(1, nbt.getInt("Gender"))) : -1;
        preferredName = nbt.contains("PreferredName") ? nbt.getString("PreferredName").trim() : "";
        favoriteThing = nbt.contains("FavoriteThing") ? nbt.getString("FavoriteThing").trim() : "";

        tempFishingLevelBonus = nbt.contains("TempFishingLevelBonus") ? nbt.getInt("TempFishingLevelBonus") : 0;
        tempLuckBonus = nbt.contains("TempLuckBonus") ? nbt.getInt("TempLuckBonus") : 0;
        tempFarmingLevelBonus = nbt.contains("TempFarmingLevelBonus") ? nbt.getInt("TempFarmingLevelBonus") : 0;
        tempForagingLevelBonus = nbt.contains("TempForagingLevelBonus") ? nbt.getInt("TempForagingLevelBonus") : 0;
        tempMiningLevelBonus = nbt.contains("TempMiningLevelBonus") ? nbt.getInt("TempMiningLevelBonus") : 0;
        
        // 同步更新HUD的金币显示
        com.stardew.craft.client.hud.StardewTimeHud.updateClientMoney(money);
        
        // 读取经验数组
        int[] expArray = nbt.getIntArray("Experience");
        if (expArray.length == 5) {
            experience = expArray;
        }

        int[] levelArray = nbt.getIntArray("SkillLevels");
        if (levelArray.length == 5) {
            skillLevels = levelArray;
        } else if (expArray.length == 5) {
            int[] fallback = new int[5];
            for (int i = 0; i < fallback.length; i++) {
                fallback[i] = calculateLevel(expArray[i]);
            }
            skillLevels = fallback;
        }

        // 精通系统
        masteryExp = nbt.contains("MasteryExp") ? nbt.getLong("MasteryExp") : 0L;
        masteryLevelsSpent = nbt.contains("MasteryLevelsSpent") ? nbt.getInt("MasteryLevelsSpent") : 0;
        java.util.Arrays.fill(claimedMasteryRewards, false);
        if (nbt.contains("ClaimedMasteryRewards")) {
            byte[] bits = nbt.getByteArray("ClaimedMasteryRewards");
            for (int i = 0; i < Math.min(bits.length, 5); i++) {
                claimedMasteryRewards[i] = bits[i] != 0;
            }
        }
        gotMasteryHint = nbt.getBoolean("GotMasteryHint");
        visitedMasteryCave = nbt.getBoolean("VisitedMasteryCave");
        unlockedTrinketSlots = nbt.contains("UnlockedTrinketSlots") ? nbt.getInt("UnlockedTrinketSlots") : 0;
        
        // 读取职业列表（服务端标准格式为 int[]，同时兼容旧 string-list）
        professions.clear();
        int[] professionIds = nbt.getIntArray("Professions");
        if (professionIds.length > 0) {
            for (int professionId : professionIds) {
                ProfessionType profession = ProfessionType.fromId(professionId);
                if (profession != null) {
                    professions.add(profession.getName());
                }
            }
        } else {
            ListTag profList = nbt.getList("Professions", Tag.TAG_STRING);
            for (int i = 0; i < profList.size(); i++) {
                professions.add(profList.getString(i));
            }
        }

        // 读取已解锁的配方
        unlockedRecipes.clear();
        if (nbt.contains("UnlockedRecipes")) {
            ListTag recipesList = nbt.getList("UnlockedRecipes", Tag.TAG_STRING);
            for (int i = 0; i < recipesList.size(); i++) {
                unlockedRecipes.add(recipesList.getString(i));
            }
        }

        // 读取邮件标记（含 CC 献祭进度：ccPantry/ccCraftsRoom/...）
        mailFlags.clear();
        if (nbt.contains("MailFlags")) {
            ListTag flagList = nbt.getList("MailFlags", Tag.TAG_STRING);
            for (int i = 0; i < flagList.size(); i++) {
                String flag = flagList.getString(i);
                if (!flag.isBlank()) mailFlags.add(flag);
            }
        }

        specialItems.clear();
        if (nbt.contains("SpecialItems")) {
            ListTag itemList = nbt.getList("SpecialItems", Tag.TAG_STRING);
            for (int i = 0; i < itemList.size(); i++) {
                String itemId = itemList.getString(i);
                if (!itemId.isBlank()) specialItems.add(itemId);
            }
        }

        secretNotesSeen.clear();
        if (nbt.contains("SecretNotesSeen", Tag.TAG_LIST)) {
            ListTag noteList = nbt.getList("SecretNotesSeen", Tag.TAG_STRING);
            for (int i = 0; i < noteList.size(); i++) {
                String noteId = noteList.getString(i);
                if (!noteId.isBlank()) secretNotesSeen.add(noteId);
            }
        }
        revealedGiftTastes.clear();
        if (nbt.contains("RevealedGiftTastes", Tag.TAG_LIST)) {
            ListTag revealList = nbt.getList("RevealedGiftTastes", Tag.TAG_STRING);
            for (int i = 0; i < revealList.size(); i++) {
                String reveal = revealList.getString(i);
                if (!reveal.isBlank()) revealedGiftTastes.add(reveal);
            }
        }

        stats.clear();
        if (nbt.contains("Stats", Tag.TAG_COMPOUND)) {
            CompoundTag statsTag = nbt.getCompound("Stats");
            for (String key : statsTag.getAllKeys()) {
                int value = Math.max(0, statsTag.getInt(key));
                if (!key.isBlank() && value > 0) {
                    stats.put(key, value);
                }
            }
        }

        // 农场信息
        hasFarm = nbt.getBoolean("HasFarm");
        if (hasFarm && nbt.contains("FarmName")) {
            farmName = nbt.getString("FarmName");
        } else {
            farmName = "";
        }
        if (hasFarm && nbt.hasUUID("FarmOwnerUUID")) {
            farmOwnerUuid = nbt.getUUID("FarmOwnerUUID").toString();
        } else {
            farmOwnerUuid = "";
        }

        recipeCraftCounts.clear();
        if (nbt.contains("RecipeCraftCounts")) {
            ListTag countList = nbt.getList("RecipeCraftCounts", Tag.TAG_COMPOUND);
            for (int i = 0; i < countList.size(); i++) {
                CompoundTag entry = countList.getCompound(i);
                if (!entry.contains("Recipe", Tag.TAG_STRING)) {
                    continue;
                }
                String recipeId = entry.getString("Recipe");
                int count = Math.max(0, entry.getInt("Count"));
                if (!recipeId.isBlank() && count > 0) {
                    recipeCraftCounts.put(recipeId, count);
                }
            }
        }

        fishCatchCounts.clear();
        if (nbt.contains("FishCatchCounts", Tag.TAG_LIST)) {
            ListTag countList = nbt.getList("FishCatchCounts", Tag.TAG_COMPOUND);
            for (int i = 0; i < countList.size(); i++) {
                CompoundTag entry = countList.getCompound(i);
                String itemId = entry.getString("Item");
                int count = Math.max(0, entry.getInt("Count"));
                if (!itemId.isBlank() && count > 0) {
                    fishCatchCounts.put(itemId, count);
                }
            }
        }

        shippedBasic.clear();
        if (nbt.contains("ShippedBasic", Tag.TAG_LIST)) {
            ListTag shippedList = nbt.getList("ShippedBasic", Tag.TAG_STRING);
            for (int i = 0; i < shippedList.size(); i++) {
                String itemId = shippedList.getString(i);
                if (!itemId.isBlank()) {
                    shippedBasic.add(itemId);
                }
            }
        }

        itemsShipped.clear();
        if (nbt.contains("ItemsShipped", Tag.TAG_LIST)) {
            ListTag shippedList = nbt.getList("ItemsShipped", Tag.TAG_COMPOUND);
            for (int i = 0; i < shippedList.size(); i++) {
                CompoundTag entry = shippedList.getCompound(i);
                String itemId = entry.getString("Item");
                int count = Math.max(0, entry.getInt("Count"));
                if (!itemId.isBlank() && count > 0) {
                    itemsShipped.put(itemId, count);
                }
            }
        }

        syncedFromServer = true;
        com.stardew.craft.client.specialorder.ClientSpecialOrderUnlockState.refreshBoardRenderIfChanged();
    }

    // Getters
    public static int getHealth() {
        return health;
    }
    
    public static int getMaxHealth() {
        return maxHealth;
    }
    
    public static float getEnergy() {
        return energy;
    }
    
    public static int getMaxEnergy() {
        return maxEnergy;
    }

    public static int getBaseMaxEnergy() {
        return baseMaxEnergy;
    }

    public static int getMaxMineFloorReached() {
        return maxMineFloorReached;
    }

    public static int getFirstJoinDay() {
        return firstJoinDay;
    }
    
    public static int getMoney() {
        return money;
    }

    public static int getFairStarTokens() {
        return Math.max(0, fairStarTokens);
    }

    public static boolean isFairStarTokenHudActive() {
        return fairStarTokenHudActive;
    }

    public static void setFairStarTokenHudActive(boolean active) {
        fairStarTokenHudActive = active;
    }

    public static String getFarmName() {
        return farmName;
    }

    public static boolean isPlayerMale() {
        return gender != 1;
    }

    public static String getPreferredName() {
        return preferredName;
    }

    public static String getFavoriteThing() {
        return favoriteThing;
    }

    public static boolean isProfileComplete() {
        return gender >= 0 && !preferredName.isBlank() && !favoriteThing.isBlank();
    }

    public static boolean hasFarm() {
        return hasFarm;
    }

    public static boolean isStoryHost() {
        if (!hasFarm) {
            return true;
        }
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.client.player.LocalPlayer player = mc.player;
        if (player == null) {
            return true;
        }
        if (farmOwnerUuid == null || farmOwnerUuid.isBlank()) {
            return true;
        }
        return farmOwnerUuid.equals(player.getUUID().toString());
    }

    /** Update cached money on client (e.g. immediate feedback for geode cost deduction). */
    public static void setMoney(int value) {
        money = value;
    }
    
    public static int[] getExperience() {
        return experience.clone();
    }
    
    public static int getExperience(SkillType skill) {
        return experience[skill.ordinal()];
    }

    // ============ Mastery 客户端只读 API ============
    public static long getMasteryExp() { return masteryExp; }
    public static int getMasteryLevelsSpent() { return masteryLevelsSpent; }
    public static boolean hasClaimedMasteryReward(SkillType skill) {
        return claimedMasteryRewards[skill.getId()];
    }
    public static boolean gotMasteryHint() { return gotMasteryHint; }
    public static boolean visitedMasteryCave() { return visitedMasteryCave; }
    public static int getUnlockedTrinketSlots() { return unlockedTrinketSlots; }

    /** 客户端：当前玩家是否 5 项技能均 Lv10。 */
    public static boolean hasAllSkillsMaxed() {
        for (int i = 0; i < 5; i++) if (skillLevels[i] < 10) return false;
        return true;
    }


    public static int getSkillLevel(SkillType skill) {
        int level = skillLevels[skill.ordinal()];
        if (skill == SkillType.FISHING) level += tempFishingLevelBonus;
        if (skill == SkillType.FARMING) level += tempFarmingLevelBonus;
        if (skill == SkillType.FORAGING) level += tempForagingLevelBonus;
        if (skill == SkillType.MINING) level += tempMiningLevelBonus;
        return Math.max(0, level);
    }

    public static int getTempLuckBonus() {
        return Math.max(0, tempLuckBonus);
    }
    
    public static List<String> getProfessions() {
        return new ArrayList<>(professions);
    }

    public static boolean hasProfession(ProfessionType profession) {
        if (profession == null) {
            return false;
        }

        String expectedName = profession.getName();
        for (String name : professions) {
            if (expectedName.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }
    
    public static java.util.Set<String> getUnlockedRecipes() {
        return new java.util.HashSet<>(unlockedRecipes);
    }

    public static boolean hasRecipe(String recipeId) {
        return unlockedRecipes.contains(recipeId);
    }

    public static int getRecipeCraftCount(String recipeId) {
        if (recipeId == null || recipeId.isBlank()) {
            return 0;
        }
        return Math.max(0, recipeCraftCounts.getOrDefault(recipeId, 0));
    }

    public static java.util.Map<String, Integer> getRecipeCraftCounts() {
        return new java.util.HashMap<>(recipeCraftCounts);
    }

    public static int getFishCatchCount(String itemId) {
        return itemId == null ? 0 : Math.max(0, fishCatchCounts.getOrDefault(itemId, 0));
    }

    public static boolean hasShipped(String itemId) {
        return itemId != null && (shippedBasic.contains(itemId)
                || itemsShipped.getOrDefault(itemId, 0) > 0);
    }

    public static boolean hasShippedMatching(java.util.function.Predicate<String> predicate) {
        if (predicate == null) return false;
        for (String itemId : shippedBasic) {
            if (predicate.test(itemId)) return true;
        }
        for (java.util.Map.Entry<String, Integer> entry : itemsShipped.entrySet()) {
            if (entry.getValue() > 0 && predicate.test(entry.getKey())) return true;
        }
        return false;
    }

    public static boolean hasMailFlag(String flag) {
        return flag != null && mailFlags.contains(flag);
    }

    public static java.util.Set<String> getMailFlags() {
        return new java.util.HashSet<>(mailFlags);
    }

    public static String getWinterStarRecipient() {
        return winterStarRecipient;
    }

    public static boolean hasSpecialItem(String itemId) {
        return itemId != null && specialItems.contains(itemId);
    }

    public static java.util.Set<String> getSpecialItems() {
        return new java.util.HashSet<>(specialItems);
    }

    public static boolean hasSeenSecretNote(String noteId) {
        return noteId != null && secretNotesSeen.contains(noteId);
    }

    public static java.util.Set<String> getSecretNotesSeen() {
        return new java.util.HashSet<>(secretNotesSeen);
    }

    public static boolean hasRevealedGiftTaste(String npcId, String objectId) {
        if (npcId == null || objectId == null) return false;
        return revealedGiftTastes.contains(npcId.trim().toLowerCase(java.util.Locale.ROOT) + ":" + objectId.trim());
    }

    public static java.util.Set<String> getRevealedGiftTastes() {
        return new java.util.HashSet<>(revealedGiftTastes);
    }

    public static int getStat(String key) {
        if (key == null || key.isBlank()) {
            return 0;
        }
        return Math.max(0, stats.getOrDefault(key, 0));
    }

    public static boolean hasStat(String key) {
        return getStat(key) > 0;
    }

    public static int getTicketPrizesClaimed() {
        return ticketPrizesClaimed;
    }

    public static int getSpecialOrderPrizeTickets() {
        return specialOrderPrizeTickets;
    }

    // Equipment getters/setters
    public static String getEquippedLeftRing() { return equipmentStorageId(equippedLeftRing); }
    public static String getEquippedRightRing() { return equipmentStorageId(equippedRightRing); }
    public static String getEquippedBoots() { return equipmentStorageId(equippedBoots); }
    public static ItemStack getEquippedLeftRingStack() { return equippedLeftRing.copy(); }
    public static ItemStack getEquippedRightRingStack() { return equippedRightRing.copy(); }
    public static ItemStack getEquippedBootsStack() { return equippedBoots.copy(); }
    public static ItemStack getEquippedTrinket() { return equippedTrinket.copy(); }
    public static String getEquippedHat() { return equippedHat; }
    public static String getEquippedShirt() { return equippedShirt; }
    public static String getEquippedPants() { return equippedPants; }
    public static String getEquippedHat(UUID playerId) {
        if (playerId == null) {
            return "";
        }
        CosmeticAppearance appearance = cosmeticAppearances.get(playerId);
        return appearance == null ? "" : appearance.hat();
    }
    public static void setEquippedLeftRing(String id) { equippedLeftRing = equipmentStackFromStorageId(id); }
    public static void setEquippedRightRing(String id) { equippedRightRing = equipmentStackFromStorageId(id); }
    public static void setEquippedBoots(String id) { equippedBoots = equipmentStackFromStorageId(id); }
    public static void setEquippedLeftRingStack(ItemStack stack) { equippedLeftRing = singleEquipmentStack(stack); }
    public static void setEquippedRightRingStack(ItemStack stack) { equippedRightRing = singleEquipmentStack(stack); }
    public static void setEquippedBootsStack(ItemStack stack) { equippedBoots = singleEquipmentStack(stack); }
    public static void setEquippedTrinket(ItemStack stack) {
        equippedTrinket = stack == null ? ItemStack.EMPTY : stack.copy();
    }
    public static void setEquippedHat(String id) { equippedHat = id == null ? "" : id; }
    public static void setEquippedShirt(String id) { equippedShirt = id == null ? "" : id; }
    public static void setEquippedPants(String id) { equippedPants = id == null ? "" : id; }
    public static void setCosmeticAppearance(UUID playerId, String hat, String shirt, String pants) {
        if (playerId == null) {
            return;
        }
        String safeHat = hat == null ? "" : hat;
        String safeShirt = shirt == null ? "" : shirt;
        String safePants = pants == null ? "" : pants;
        cosmeticAppearances.put(playerId, new CosmeticAppearance(safeHat, safeShirt, safePants));
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null && playerId.equals(mc.player.getUUID())) {
            equippedHat = safeHat;
            equippedShirt = safeShirt;
            equippedPants = safePants;
        }
    }

    /**
     * 重置所有缓存（用于退出世界时）
     */
    public static void reset() {
        health = 100;
        maxHealth = 100;
        energy = 270.0f;
        maxEnergy = 270;
        baseMaxEnergy = 270;
        money = 0;
        experience = new int[5];
        skillLevels = new int[5];
        professions.clear();
        unlockedRecipes.clear();
        recipeCraftCounts.clear();
        mailFlags.clear();
        specialItems.clear();
        secretNotesSeen.clear();
        revealedGiftTastes.clear();
        stats.clear();
        ticketPrizesClaimed = 0;
        specialOrderPrizeTickets = 0;
        fairStarTokens = 0;
        fairStarTokenHudActive = false;
        maxMineFloorReached = 0;
        gender = -1;
        preferredName = "";
        favoriteThing = "";
        hasFarm = false;
        farmName = "";
        farmOwnerUuid = "";
        tempFishingLevelBonus = 0;
        tempLuckBonus = 0;
        tempMaxEnergyBonus = 0;
        tempFarmingLevelBonus = 0;
        tempForagingLevelBonus = 0;
        tempMiningLevelBonus = 0;
        equippedLeftRing = ItemStack.EMPTY;
        equippedRightRing = ItemStack.EMPTY;
        equippedBoots = ItemStack.EMPTY;
        equippedTrinket = ItemStack.EMPTY;
        equippedHat = "";
        equippedShirt = "";
        equippedPants = "";
        cosmeticAppearances.clear();
        syncedFromServer = false;
        NpcFriendshipClientCache.reset();
    }
    
    /**
     * 根据经验值计算等级
     */
    private static int calculateLevel(int exp) {
        int[] expTable = {
            100, 380, 770, 1300, 2150,
            3300, 4800, 6900, 10000, 15000
        };
        
        for (int level = 0; level < 10; level++) {
            if (exp < expTable[level]) {
                return level;
            }
        }
        return 10;
    }

    private static ItemStack singleEquipmentStack(ItemStack stack) {
        return stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
    }

    private static String equipmentStorageId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        if (com.stardew.craft.item.equipment.CombinedRingData.isCombinedRing(stack)) {
            return com.stardew.craft.item.equipment.CombinedRingData.encodeForEquipmentSlot(stack);
        }
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static ItemStack equipmentStackFromStorageId(String id) {
        if (id == null || id.isBlank()) return ItemStack.EMPTY;
        if (com.stardew.craft.item.equipment.CombinedRingData.isEncodedEquipmentSlot(id)) {
            return com.stardew.craft.item.equipment.CombinedRingData.stackFromEquipmentSlot(id);
        }
        net.minecraft.resources.ResourceLocation location = net.minecraft.resources.ResourceLocation.tryParse(id);
        if (location == null || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(location)) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(location), 1);
    }

    private record CosmeticAppearance(String hat, String shirt, String pants) {
    }
}

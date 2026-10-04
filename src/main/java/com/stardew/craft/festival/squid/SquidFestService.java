package com.stardew.craft.festival.squid;

import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.festival.FestivalRegistry;
import com.stardew.craft.festival.FestivalService;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.network.ItemPickupHudPacket;
import com.stardew.craft.network.payload.HoldUpItemPayload;
import com.stardew.craft.network.payload.OpenDesertFestivalQuestionPayload;
import com.stardew.craft.network.payload.OpenNpcDialogueScreenPayload;
import com.stardew.craft.npc.runtime.NpcScheduleRuntimeService;
import com.stardew.craft.npc.runtime.NpcSpawnManager;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class SquidFestService {
    public static final String FESTIVAL_ID = "SquidFest";
    public static final String WILLY_NPC_ID = "willy";
    public static final String WILLY_POINT_ID = "squid_fest_willy";
    public static final int WILLY_FACING = 2;

    private static final String CONTEXT_BOOTH = "squid_fest_booth";
    private static final String CHOICE_REWARDS = "SquidFestBooth_Rewards";
    private static final String CHOICE_EXPLANATION = "SquidFestBooth_Explanation";
    private static final String CHOICE_LEAVE = "Leave";
    private static final String SQUID_ID = "stardewcraft:squid";
    private static final String CRABBING_BOOK_FLAG = "GotCrabbingBook";

    private static final Vec3 WILLY_POSITION = new Vec3(32.5D, 60.0D, 99.5D);

    private static final int BOOTH_MIN_X = 29;
    private static final int BOOTH_MAX_X = 35;
    private static final int BOOTH_MIN_Y = 59;
    private static final int BOOTH_MAX_Y = 62;
    private static final int BOOTH_MIN_Z = 98;
    private static final int BOOTH_MAX_Z = 102;

    private SquidFestService() {
    }

    public static boolean isFestivalOpen() {
        return FestivalService.isPassiveFestivalOpen(FESTIVAL_ID);
    }

    public static boolean isWillyScheduleOverride(String npcId) {
        return WILLY_NPC_ID.equalsIgnoreCase(npcId) && isFestivalOpen();
    }

    public static Vec3 willyPosition() {
        return WILLY_POSITION;
    }

    public static int forceRefreshNpcSchedules(ServerLevel level) {
        if (level == null) {
            return 0;
        }
        NpcScheduleRuntimeService.invalidateCache();
        NpcScheduleRuntimeService.tick(level);
        return NpcSpawnManager.forceNpcToCurrentSchedule(level, WILLY_NPC_ID) ? 1 : 0;
    }

    public static void onFishCaught(ServerPlayer player, ItemStack fish, int numberCaught) {
        if (player == null || fish == null || fish.isEmpty() || numberCaught <= 0 || !isFestivalOpen()) {
            return;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(fish.getItem());
        if (!new ResourceLocation(SQUID_ID).equals(id)) {
            return;
        }
        int score = PlayerStardewDataAPI.incrementStat(player, scoreStatKey(activeFestivalDay()), numberCaught);
        player.displayClientMessage(Component.translatable("stardewcraft.squid_fest.score", score), true);
    }

    public static boolean isPlayerAtBooth(ServerPlayer player) {
        if (player == null || !isFestivalOpen()) {
            return false;
        }
        int px = (int) Math.floor(player.getX());
        int py = (int) Math.floor(player.getY());
        int pz = (int) Math.floor(player.getZ());
        return px >= BOOTH_MIN_X && px <= BOOTH_MAX_X
            && py >= BOOTH_MIN_Y && py <= BOOTH_MAX_Y
            && pz >= BOOTH_MIN_Z && pz <= BOOTH_MAX_Z;
    }

    public static InteractionResult handleWillyInteraction(ServerPlayer player, StardewNpcEntity willy) {
        float yaw = 0.0F;
        willy.setYRot(yaw);
        willy.setYHeadRot(yaw);
        willy.setYBodyRot(yaw);
        openBoothQuestion(player);
        return InteractionResult.SUCCESS;
    }

    public static void handleQuestionResponse(ServerPlayer player, String choiceId) {
        if (player == null || !isPlayerAtBooth(player)) {
            return;
        }
        if (CHOICE_REWARDS.equals(choiceId)) {
            tryClaimRewards(player);
        } else if (CHOICE_EXPLANATION.equals(choiceId)) {
            sendDialogue(player, "stardewcraft.squid_fest.booth.explanation");
        }
    }

    private static void openBoothQuestion(ServerPlayer player) {
        Component question = Component.translatable("stardewcraft.squid_fest.booth.intro");
        PacketDistributor.sendToPlayer(player, new OpenDesertFestivalQuestionPayload(
            CONTEXT_BOOTH,
            0,
            "",
            Component.Serializer.toJson(question),
            List.of(
                response(CHOICE_REWARDS, Component.translatable("stardewcraft.squid_fest.booth.get_rewards"), player),
                response(CHOICE_EXPLANATION, Component.translatable("stardewcraft.squid_fest.booth.explanation.choice"), player),
                response(CHOICE_LEAVE, Component.translatable("stardewcraft.squid_fest.booth.leave"), player)
            )
        ));
    }

    private static OpenDesertFestivalQuestionPayload.ResponseOption response(String id, Component label, ServerPlayer player) {
        return new OpenDesertFestivalQuestionPayload.ResponseOption(id, Component.Serializer.toJson(label));
    }

    private static void tryClaimRewards(ServerPlayer player) {
        StardewTimeManager time = StardewTimeManager.get();
        int year = time.getCurrentYear();
        int calendarDay = time.getCurrentDay();
        int festivalDay = activeFestivalDay();
        int[] targets = targetsForDay(festivalDay);
        int score = PlayerStardewDataAPI.getStat(player, scoreStatKey(year, festivalDay));
        if (calendarDay != festivalDay) {
            score = Math.max(score, PlayerStardewDataAPI.getStat(player, scoreStatKey(year, calendarDay)));
        }
        PlayerStardewData data = PlayerStardewDataAPI.getData(player);
        boolean alreadyGotCrabbingBook = data.hasMailFlag(CRABBING_BOOK_FLAG);

        if (data.hasMailFlag(rewardFlag(year, festivalDay, 3))) {
            sendDialogue(player, "stardewcraft.squid_fest.booth.got_all_rewards_today");
            return;
        }

        List<RewardKey> available = new ArrayList<>();
        boolean alreadyReceivedSomeAvailable = false;
        for (int tier = 0; tier < targets.length; tier++) {
            if (score < targets[tier]) {
                continue;
            }
            String flag = rewardFlag(year, festivalDay, tier);
            if (data.hasMailFlag(flag)) {
                alreadyReceivedSomeAvailable = true;
                continue;
            }
            available.add(new RewardKey(festivalDay, tier));
        }

        if (available.isEmpty()) {
            sendDialogue(player, alreadyReceivedSomeAvailable
                ? "stardewcraft.squid_fest.booth.already_got_available_rewards"
                : "stardewcraft.squid_fest.booth.no_rewards");
            return;
        }

        List<ItemStack> rewards = createRewards(player, available, alreadyGotCrabbingBook);
        if (rewards.isEmpty()) {
            sendDialogue(player, "stardewcraft.squid_fest.booth.no_rewards");
            return;
        }
        for (RewardKey key : available) {
            data.addMailFlag(rewardFlag(year, festivalDay, key.tier()));
            if (!alreadyGotCrabbingBook && key.tier() >= 3) {
                data.addMailFlag(CRABBING_BOOK_FLAG);
                alreadyGotCrabbingBook = true;
            }
        }
        grantRewards(player, rewards);
        PlayerDataEventHandler.syncPlayerData(player, data);
    }

    private static List<ItemStack> createRewards(ServerPlayer player, List<RewardKey> keys, boolean alreadyGotCrabbingBook) {
        Random random = dayRandom(player);
        List<ItemStack> rewards = new ArrayList<>();
        for (RewardKey key : keys) {
            switch (key.day() + "_" + key.tier()) {
                case "12_0" -> rewards.add(new ItemStack(ModItems.DELUXE_BAIT.get(), 20));
                case "12_1" -> {
                    rewards.add(random.nextBoolean() ? stackByPath("winter_seeds", 10) : new ItemStack(ModItems.MYSTERY_BOX.get(), 2));
                    rewards.add(stackByPath("dish_o_the_sea", 1));
                }
                case "12_2" -> {
                    rewards.add(new ItemStack(ModItems.PEARL.get()));
                    rewards.add(new ItemStack(ModItems.COFFEE.get(), 3));
                }
                case "12_3" -> {
                    rewards.add(new ItemStack(ModItems.SQUID_KID_PAINTING.get()));
                    addCrabbingBookOrFallback(rewards, alreadyGotCrabbingBook);
                }
                case "13_0" -> rewards.add(new ItemStack(ModItems.TRAP_BOBBER.get()));
                case "13_1" -> {
                    rewards.add(random.nextBoolean() ? stackByPath("winter_seeds", 15) : new ItemStack(ModItems.MYSTERY_BOX.get(), 3));
                    rewards.add(stackByPath("dish_o_the_sea", 1));
                }
                case "13_2" -> {
                    rewards.add(new ItemStack(ModItems.TREASURE_CHEST.get()));
                    rewards.add(stackByPath("triple_shot_espresso", 3));
                }
                case "13_3" -> {
                    rewards.add(new ItemStack(ModItems.SQUID_HAT.get()));
                    addCrabbingBookOrFallback(rewards, alreadyGotCrabbingBook);
                }
                default -> {
                }
            }
        }
        return rewards.stream().filter(stack -> !stack.isEmpty()).toList();
    }

    private static void addCrabbingBookOrFallback(List<ItemStack> rewards, boolean alreadyGotCrabbingBook) {
        if (!alreadyGotCrabbingBook) {
            rewards.add(new ItemStack(ModItems.BOOK_CRABBING.get()));
        } else {
            rewards.add(new ItemStack(ModItems.MYSTERY_BOX.get(), 3));
            rewards.add(stackByPath("seafoam_pudding", 1));
        }
    }

    private static void grantRewards(ServerPlayer player, List<ItemStack> rewards) {
        ItemStack first = ItemStack.EMPTY;
        for (ItemStack reward : rewards) {
            if (reward.isEmpty()) {
                continue;
            }
            if (first.isEmpty()) {
                first = reward.copy();
            }
            ItemStack toAdd = reward.copy();
            boolean added = player.getInventory().add(toAdd);
            if (!added || !toAdd.isEmpty()) {
                player.drop(toAdd.isEmpty() ? reward.copy() : toAdd.copy(), false);
            }
            ItemPickupHudPacket.sendTo(player, reward, reward.getCount(), false);
        }
        if (!first.isEmpty()) {
            HoldUpItemPayload.sendTo(player, first);
        }
    }

    private static ItemStack stackByPath(String path, int count) {
        ResourceLocation id = new ResourceLocation("stardewcraft", path);
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item, count);
    }

    private static int[] targetsForDay(int day) {
        return day == 13 ? new int[]{2, 5, 7, 10} : new int[]{1, 3, 5, 8};
    }

    private static int activeFestivalDay() {
        StardewTimeManager time = StardewTimeManager.get();
        return time.getCurrentSeason() == FestivalRegistry.WINTER && time.getCurrentDay() == 13 ? 13 : 12;
    }

    private static String scoreStatKey(int day) {
        StardewTimeManager time = StardewTimeManager.get();
        return scoreStatKey(time.getCurrentYear(), day);
    }

    private static String scoreStatKey(int year, int day) {
        return "SquidFestScore_" + year + "_" + day;
    }

    private static String rewardFlag(int year, int day, int tier) {
        return "GotSquidFestReward_" + year + "_" + day + "_" + tier;
    }

    private static Random dayRandom(ServerPlayer player) {
        StardewTimeManager time = StardewTimeManager.get();
        long seed = player.serverLevel().getSeed()
            ^ ((long) time.getCurrentYear() * 2000L)
            ^ ((long) activeFestivalDay() * 10L);
        return new Random(seed);
    }

    private static void sendDialogue(ServerPlayer player, String key) {
        com.stardew.craft.npc.runtime.NpcInteractionService.sendDialogue(player, new OpenNpcDialogueScreenPayload(WILLY_NPC_ID, key, 0));
    }

    private record RewardKey(int day, int tier) {
    }
}

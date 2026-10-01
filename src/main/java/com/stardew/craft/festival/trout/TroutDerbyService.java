package com.stardew.craft.festival.trout;

import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.festival.FestivalService;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.network.ItemPickupHudPacket;
import com.stardew.craft.network.payload.HoldUpItemPayload;
import com.stardew.craft.network.payload.OpenDesertFestivalQuestionPayload;
import com.stardew.craft.network.payload.OpenNpcDialogueScreenPayload;
import com.stardew.craft.npc.runtime.NpcScheduleRuntimeService;
import com.stardew.craft.npc.runtime.NpcSpawnManager;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public final class TroutDerbyService {
    public static final String FESTIVAL_ID = "TroutDerby";
    public static final String WILLY_NPC_ID = "willy";
    public static final String WILLY_POINT_ID = "trout_derby_willy";
    public static final int WILLY_FACING = 2;
    public static final String GOLDEN_TAGS_TURNED_IN_STAT = "GoldenTagsTurnedIn";

    private static final String CONTEXT_BOOTH = "trout_derby_booth";
    private static final String CHOICE_REWARDS = "TroutDerbyBooth_Rewards";
    private static final String CHOICE_EXPLANATION = "TroutDerbyBooth_Explanation";
    private static final String CHOICE_LEAVE = "Leave";

    private static final Vec3 WILLY_POSITION = new Vec3(-140.5D, 64.0D, 86.5D);

    private static final int BOOTH_MIN_X = -144;
    private static final int BOOTH_MAX_X = -139;
    private static final int BOOTH_MIN_Y = 63;
    private static final int BOOTH_MAX_Y = 66;
    private static final int BOOTH_MIN_Z = 87;
    private static final int BOOTH_MAX_Z = 90;

    private TroutDerbyService() {
    }

    public static boolean isFestivalOpen() {
        return FestivalService.isPassiveFestivalOpen(FESTIVAL_ID);
    }

    public static boolean shouldAwardGoldenTag(ServerPlayer player, ItemStack fish, int numberOfFishCaught) {
        if (player == null || fish == null || fish.isEmpty() || numberOfFishCaught <= 0) {
            return false;
        }
        StardewTimeManager time = StardewTimeManager.get();
        boolean sourceDate = time != null && time.getCurrentSeason() == 1 && time.getCurrentDay() >= 20 && time.getCurrentDay() <= 21;
        if (!sourceDate && !isFestivalOpen()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(fish.getItem());
        if (!new ResourceLocation("stardewcraft", "rainbow_trout").equals(id)) {
            return false;
        }
        return player.getRandom().nextDouble() < 0.33D * numberOfFishCaught;
    }

    public static ItemStack createGoldenTagStack() {
        return new ItemStack(ModItems.GOLDEN_TAG.get());
    }

    public static void awardGoldenTagWithoutTreasure(ServerPlayer player) {
        if (player == null) {
            return;
        }
        ItemStack tag = createGoldenTagStack();
        ItemStack toAdd = tag.copy();
        boolean added = player.getInventory().add(toAdd);
        if (!added || !toAdd.isEmpty()) {
            player.drop(toAdd.isEmpty() ? tag.copy() : toAdd.copy(), false);
        }
        HoldUpItemPayload.sendTo(player, tag);
        ItemPickupHudPacket.sendTo(player, tag, 1, false);
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
            tryClaimReward(player);
        } else if (CHOICE_EXPLANATION.equals(choiceId)) {
            sendDialogue(player, "stardewcraft.trout_derby.booth.explanation");
        }
    }

    private static void openBoothQuestion(ServerPlayer player) {
        Component question = Component.translatable("stardewcraft.trout_derby.booth.intro");
        PacketDistributor.sendToPlayer(player, new OpenDesertFestivalQuestionPayload(
            CONTEXT_BOOTH,
            0,
            "",
            Component.Serializer.toJson(question),
            List.of(
                response(CHOICE_REWARDS, Component.translatable("stardewcraft.trout_derby.booth.get_rewards"), player),
                response(CHOICE_EXPLANATION, Component.translatable("stardewcraft.trout_derby.booth.explanation.choice"), player),
                response(CHOICE_LEAVE, Component.translatable("stardewcraft.trout_derby.booth.leave"), player)
            )
        ));
    }

    private static OpenDesertFestivalQuestionPayload.ResponseOption response(String id, Component label, ServerPlayer player) {
        return new OpenDesertFestivalQuestionPayload.ResponseOption(id, Component.Serializer.toJson(label));
    }

    private static void tryClaimReward(ServerPlayer player) {
        int tags = player.getInventory().countItem(ModItems.GOLDEN_TAG.get());
        if (tags <= 0) {
            sendDialogue(player, "stardewcraft.trout_derby.booth.no_tags");
            return;
        }

        ItemStack reward = createReward(player);
        if (reward.isEmpty()) {
            sendDialogue(player, "stardewcraft.trout_derby.booth.bag_full");
            return;
        }
        if (!canFullyAddToInventory(player.getInventory(), reward) && tags != 1) {
            sendDialogue(player, "stardewcraft.trout_derby.booth.bag_full");
            return;
        }

        PlayerStardewDataAPI.incrementStat(player, GOLDEN_TAGS_TURNED_IN_STAT);
        removeOneGoldenTag(player);
        ItemStack granted = reward.copy();
        boolean added = player.getInventory().add(granted);
        if (!added || !granted.isEmpty()) {
            player.drop(granted.isEmpty() ? reward.copy() : granted.copy(), false);
        }
        HoldUpItemPayload.sendTo(player, reward);
        ItemPickupHudPacket.sendTo(player, reward, reward.getCount(), false);
    }

    private static ItemStack createReward(ServerPlayer player) {
        int turnedIn = PlayerStardewDataAPI.getStat(player, GOLDEN_TAGS_TURNED_IN_STAT);
        if (turnedIn == 0) {
            return new ItemStack(ModItems.CRAB_POT.get());
        }
        int rewardIndex = Math.floorMod(seededRewardOffset(player) + turnedIn, 10);
        return switch (rewardIndex) {
            case 0 -> new ItemStack(ModItems.CRAB_POT.get());
            case 1 -> new ItemStack(ModItems.CRAB_POT.get());
            case 2 -> new ItemStack(ModItems.MYSTERY_BOX.get(), 3);
            case 3 -> new ItemStack(ModItems.DIAMOND.get());
            case 4 -> new ItemStack(ModItems.MYSTERY_BOX.get(), 3);
            case 5 -> new ItemStack(ModItems.DELUXE_BAIT.get(), 20);
            case 6 -> stackByPath("triple_shot_espresso", 2);
            case 7 -> new ItemStack(ModItems.QUALITY_SPRINKLER.get());
            case 8 -> new ItemStack(ModItems.WARP_TOTEM_FARM.get(), 3);
            case 9 -> new ItemStack(ModItems.OMNI_GEODE.get(), 3);
            default -> ItemStack.EMPTY;
        };
    }

    private static int seededRewardOffset(ServerPlayer player) {
        long seed = player.serverLevel().getSeed();
        return new DotNetRandom(createRandomSeed(seed, 0L, 0L, 0L, 0L)).next(10);
    }

    private static ItemStack stackByPath(String path, int count) {
        ResourceLocation id = new ResourceLocation("stardewcraft", path);
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item, count);
    }

    private static void removeOneGoldenTag(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(ModItems.GOLDEN_TAG.get())) {
                stack.shrink(1);
                return;
            }
        }
    }

    private static boolean canFullyAddToInventory(Inventory inventory, ItemStack stack) {
        int remaining = stack.getCount();
        int inventoryMax = inventory.getMaxStackSize();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack existing = inventory.getItem(slot);
            if (existing.isEmpty()) {
                remaining -= Math.min(stack.getMaxStackSize(), inventoryMax);
            } else if (ItemStack.isSameItemSameTags(existing, stack)) {
                int slotLimit = Math.min(existing.getMaxStackSize(), inventoryMax);
                remaining -= Math.max(0, slotLimit - existing.getCount());
            }
            if (remaining <= 0) {
                return true;
            }
        }
        return false;
    }

    private static void sendDialogue(ServerPlayer player, String key) {
        com.stardew.craft.npc.runtime.NpcInteractionService.sendDialogue(player, new OpenNpcDialogueScreenPayload(WILLY_NPC_ID, key, 0));
    }

    private static final class DotNetRandom {
        private static final int MBIG = Integer.MAX_VALUE;
        private static final int MSEED = 161803398;
        private static final int PRIME32_1 = 0x9E3779B1;
        private static final int PRIME32_2 = 0x85EBCA77;
        private static final int PRIME32_3 = 0xC2B2AE3D;
        private static final int PRIME32_4 = 0x27D4EB2F;
        private static final int PRIME32_5 = 0x165667B1;

        private final int[] seedArray = new int[56];
        private int inext;
        private int inextp;

        private DotNetRandom(int seed) {
            int subtraction = seed == Integer.MIN_VALUE ? Integer.MAX_VALUE : Math.abs(seed);
            int mj = MSEED - subtraction;
            seedArray[55] = mj;
            int mk = 1;
            for (int i = 1; i < 55; i++) {
                int ii = (21 * i) % 55;
                seedArray[ii] = mk;
                mk = mj - mk;
                if (mk < 0) {
                    mk += MBIG;
                }
                mj = seedArray[ii];
            }
            for (int k = 1; k < 5; k++) {
                for (int i = 1; i < 56; i++) {
                    seedArray[i] -= seedArray[1 + (i + 30) % 55];
                    if (seedArray[i] < 0) {
                        seedArray[i] += MBIG;
                    }
                }
            }
            inext = 0;
            inextp = 21;
        }

        private int next(int maxExclusive) {
            return (int) (sample() * maxExclusive);
        }

        private double sample() {
            return internalSample() * (1.0D / MBIG);
        }

        private int internalSample() {
            int next = inext + 1;
            if (next >= 56) {
                next = 1;
            }
            int nextp = inextp + 1;
            if (nextp >= 56) {
                nextp = 1;
            }
            int value = seedArray[next] - seedArray[nextp];
            if (value == MBIG) {
                value--;
            }
            if (value < 0) {
                value += MBIG;
            }
            seedArray[next] = value;
            inext = next;
            inextp = nextp;
            return value;
        }
    }

    private static int createRandomSeed(long seedA, long seedB, long seedC, long seedD, long seedE) {
        byte[] data = new byte[20];
        writeLittleEndian(data, 0, sdvSeedPart(seedA));
        writeLittleEndian(data, 4, sdvSeedPart(seedB));
        writeLittleEndian(data, 8, sdvSeedPart(seedC));
        writeLittleEndian(data, 12, sdvSeedPart(seedD));
        writeLittleEndian(data, 16, sdvSeedPart(seedE));
        return xxHash32(data);
    }

    private static int sdvSeedPart(long seed) {
        return (int) Math.floorMod(seed, 2147483647L);
    }

    private static void writeLittleEndian(byte[] data, int offset, int value) {
        data[offset] = (byte) value;
        data[offset + 1] = (byte) (value >>> 8);
        data[offset + 2] = (byte) (value >>> 16);
        data[offset + 3] = (byte) (value >>> 24);
    }

    private static int xxHash32(byte[] data) {
        int index = 0;
        int hash;
        if (data.length >= 16) {
            int limit = data.length - 16;
            int v1 = DotNetRandom.PRIME32_1 + DotNetRandom.PRIME32_2;
            int v2 = DotNetRandom.PRIME32_2;
            int v3 = 0;
            int v4 = -DotNetRandom.PRIME32_1;
            while (index <= limit) {
                v1 = round(v1, readInt(data, index));
                index += 4;
                v2 = round(v2, readInt(data, index));
                index += 4;
                v3 = round(v3, readInt(data, index));
                index += 4;
                v4 = round(v4, readInt(data, index));
                index += 4;
            }
            hash = Integer.rotateLeft(v1, 1) + Integer.rotateLeft(v2, 7)
                + Integer.rotateLeft(v3, 12) + Integer.rotateLeft(v4, 18);
        } else {
            hash = DotNetRandom.PRIME32_5;
        }
        hash += data.length;
        while (index <= data.length - 4) {
            hash += readInt(data, index) * DotNetRandom.PRIME32_3;
            hash = Integer.rotateLeft(hash, 17) * DotNetRandom.PRIME32_4;
            index += 4;
        }
        while (index < data.length) {
            hash += (data[index] & 0xFF) * DotNetRandom.PRIME32_5;
            hash = Integer.rotateLeft(hash, 11) * DotNetRandom.PRIME32_1;
            index++;
        }
        hash ^= hash >>> 15;
        hash *= DotNetRandom.PRIME32_2;
        hash ^= hash >>> 13;
        hash *= DotNetRandom.PRIME32_3;
        hash ^= hash >>> 16;
        return hash;
    }

    private static int round(int acc, int input) {
        acc += input * DotNetRandom.PRIME32_2;
        acc = Integer.rotateLeft(acc, 13);
        acc *= DotNetRandom.PRIME32_1;
        return acc;
    }

    private static int readInt(byte[] data, int offset) {
        return (data[offset] & 0xFF)
            | ((data[offset + 1] & 0xFF) << 8)
            | ((data[offset + 2] & 0xFF) << 16)
            | (data[offset + 3] << 24);
    }
}
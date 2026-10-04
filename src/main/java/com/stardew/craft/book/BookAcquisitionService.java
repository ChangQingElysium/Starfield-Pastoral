package com.stardew.craft.book;

import com.stardew.craft.item.ModItems;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.player.SkillType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.List;
import java.util.Random;

public final class BookAcquisitionService {
    private static final String GOT_WOODCUTTING_BOOK = "GotWoodcuttingBook";
    private static final String ROE_BOOK_DROPPED = "roeBookDropped";
    private static final String VOID_BOOK_DROPPED = "voidBookDropped";
    private static final String DEFENSE_BOOK_DROPPED = "DefenseBookDropped";

    private BookAcquisitionService() {
    }

    public static void recordTreeChoppedAndMaybeAddBook(ServerPlayer player, List<ItemStack> drops, RandomSource random) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        int treesChopped = data.incrementStat("TreesChopped", 1);
        if (treesChopped <= 20) {
            PlayerDataEventHandler.syncPlayerData(player, data);
            return;
        }
        double chance = 0.0003D + (data.hasMailFlag(GOT_WOODCUTTING_BOOK) ? 0.0007D : treesChopped * 0.00001D);
        if (random.nextDouble() < chance) {
            data.addMailFlag(GOT_WOODCUTTING_BOOK);
            drops.add(new ItemStack(ModItems.BOOK_WOODCUTTING.get()));
        }
        PlayerDataEventHandler.syncPlayerData(player, data);
    }

    public static void recordFishingTreasureAndMaybeAddRoeBook(ServerPlayer player, List<ItemStack> treasures,
                                                               RandomSource random) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        int treasuresOpened = data.incrementStat("FishingTreasures", 1);
        if (PlayerStardewDataAPI.getSkillLevel(player, SkillType.FISHING) <= 4 || treasuresOpened <= 2) {
            PlayerDataEventHandler.syncPlayerData(player, data);
            return;
        }
        double chance = 0.02D + (data.hasMailFlag(ROE_BOOK_DROPPED) ? 0.001D : treasuresOpened * 0.001D);
        if (random.nextDouble() < chance) {
            data.addMailFlag(ROE_BOOK_DROPPED);
            treasures.add(new ItemStack(ModItems.BOOK_ROE.get()));
        }
        PlayerDataEventHandler.syncPlayerData(player, data);
    }

    public static void recordMonsterKilledAndMaybeAddVoidBook(ServerPlayer player, Collection<ItemEntity> drops,
                                                              LivingEntity entity, RandomSource random) {
        recordMonsterKilledAndMaybeAddVoidBook(player, drops, entity, random, true);
    }

    public static void recordMonsterKilledAndMaybeAddVoidBook(ServerPlayer player, Collection<ItemEntity> drops,
                                                              LivingEntity entity, RandomSource random, boolean allowDrop) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        int monstersKilled = data.incrementStat("MonstersKilled", 1);
        if (!allowDrop || monstersKilled <= 10) {
            PlayerDataEventHandler.syncPlayerData(player, data);
            return;
        }
        double chance = 0.0001D + (data.hasMailFlag(VOID_BOOK_DROPPED) ? 0.0004D : monstersKilled * 0.000015D);
        if (random.nextDouble() < chance) {
            data.addMailFlag(VOID_BOOK_DROPPED);
            drops.add(new ItemEntity(entity.level(), entity.getX(), entity.getY(), entity.getZ(),
                    new ItemStack(ModItems.BOOK_VOID.get())));
        }
        PlayerDataEventHandler.syncPlayerData(player, data);
    }

    public static void recordArtifactSpotDugAndMaybeAddDefenseBook(ServerPlayer player, List<ItemStack> drops,
                                                                   RandomSource random) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        int spotsDug = data.incrementStat("ArtifactSpotsDug", 1);
        if (spotsDug <= 2) {
            PlayerDataEventHandler.syncPlayerData(player, data);
            return;
        }
        double chance = 0.008D + (data.hasMailFlag(DEFENSE_BOOK_DROPPED) ? 0.005D : spotsDug * 0.002D);
        if (random.nextDouble() < chance) {
            data.addMailFlag(DEFENSE_BOOK_DROPPED);
            drops.add(new ItemStack(ModItems.BOOK_DEFENSE.get()));
        }
        PlayerDataEventHandler.syncPlayerData(player, data);
    }

}

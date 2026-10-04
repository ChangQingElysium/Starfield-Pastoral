package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.blockentity.CookingPlacedFoodBlockEntity;
import com.stardew.craft.blockentity.PlacedFishBlockEntity;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.cooking.PlacedFoodPlacement;
import com.stardew.craft.item.quality.QualityHelper;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder("stardewcraft_food_retrieval")
@PrefixGameTestTemplate(false)
public final class PlacedFoodRetrievalGameTests {
    private PlacedFoodRetrievalGameTests() {}

    @GameTest(templateNamespace = "stardewcraft_food_retrieval", template = "ring_utilities", timeoutTicks = 200)
    public static void everyPlaceableFoodUsesRealShiftClicksAndKeepsComponents(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(6, 2, 6));
        var player = player(helper, pos);
        var data = PlayerStardewDataAPI.getData(player);
        var items = BuiltInRegistries.ITEM.stream()
                .filter(item -> PlacedFoodPlacement.blockFor(new ItemStack(item)) != null).toList();
        helper.assertTrue(items.size() >= 100, "Placeable food coverage is unexpectedly small");
        int checks = 0;
        try {
            for (var item : items) for (int quality = 0; quality < 4; quality++) {
                for (int heldMode = 0; heldMode < 3; heldMode++) {
                    player.getInventory().clearContent();
                    data.setEnergy(10); data.setHealth(10);
                    var expected = serving(item, quality);
                    place(helper, player, pos, expected);
                    var entity = level.getBlockEntity(pos);
                    if (entity instanceof CookingPlacedFoodBlockEntity food) {
                        helper.assertTrue(ItemStack.matches(food.getStoredFood(), expected), "Placed food lost its components: " + item);
                        var reloaded = new CookingPlacedFoodBlockEntity(pos, food.getBlockState());
                        reloaded.loadWithComponents(food.getUpdateTag(level.registryAccess()), level.registryAccess());
                        helper.assertTrue(ItemStack.matches(reloaded.getStoredFood(), expected), "Saved serving lost components: " + item);
                    } else {
                        helper.assertTrue(entity instanceof PlacedFishBlockEntity fish && ItemStack.matches(fish.fish(), expected),
                                "Placed catch lost its components: " + item);
                    }
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                    var hand = heldMode == 2 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                    if (heldMode != 0) player.setItemInHand(hand, new ItemStack(Items.STONE, 2));
                    var hit = hit(pos);
                    helper.assertTrue(player.gameMode.useItemOn(player, level, player.getItemInHand(hand), hand, hit).consumesAction(),
                            "Real Shift click did not retrieve " + item + " with held mode " + heldMode);
                    helper.assertTrue(level.isEmptyBlock(pos), "Retrieved food stayed placed: " + item);
                    helper.assertTrue(countMatching(player, expected) == 1, "Recovery changed or duplicated the serving: " + item);
                    helper.assertTrue(data.getEnergy() == 10 && data.getHealth() == 10, "Shift recovery ate the serving: " + item);
                    helper.assertTrue(heldMode == 0 || player.getItemInHand(hand).is(Items.STONE)
                                    && player.getItemInHand(hand).getCount() == 2,
                            "Recovery used or replaced the held item: " + item);
                    helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(6)).isEmpty(),
                            "Recovery both inserted and dropped the serving: " + item);
                    // A repeated use packet after the first removal must never create another serving.
                    if (ItemStack.isSameItemSameComponents(player.getMainHandItem(), expected)) {
                        player.getInventory().setItem(8, player.getMainHandItem());
                    }
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                    int before = countMatching(player, expected);
                    player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
                    helper.assertTrue(countMatching(player, expected) == before,
                            "A second use packet duplicated the serving: " + item);
                    checks++;
                }
            }
        } finally {
            level.removeBlock(pos, false);
            player.getInventory().clearContent();
        }
        StardewCraft.LOGGER.info("[FOOD-RETRIEVAL-TEST] Verified {} Shift pickup combinations across {} foods", checks, items.size());
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_food_retrieval", template = "ring_utilities")
    public static void fullInventoriesDropExactlyOneOriginalServing(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(6, 2, 6));
        var player = player(helper, pos);
        try {
            for (var item : representativeFoods()) {
                player.getInventory().clearContent();
                var expected = serving(item, 3);
                place(helper, player, pos, expected);
                for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
                player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STONE, 64));
                helper.assertTrue(player.gameMode.useItemOn(player, level, player.getMainHandItem(),
                        InteractionHand.MAIN_HAND, hit(pos)).consumesAction(), "Full inventory prevented recovery");
                var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(6));
                helper.assertTrue(level.isEmptyBlock(pos) && drops.size() == 1
                                && ItemStack.matches(expected, drops.getFirst().getItem()),
                        "Full inventory lost, altered or duplicated the serving: " + item);
                helper.assertTrue(countMatching(player, expected) == 0, "Full inventory also received the dropped serving");
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit(pos));
                helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(6)).size() == 1,
                        "Repeated pickup created another dropped serving");
                drops.forEach(ItemEntity::discard);
            }
        } finally {
            level.removeBlock(pos, false);
            level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(6)).forEach(ItemEntity::discard);
            player.getInventory().clearContent();
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_food_retrieval", template = "ring_utilities")
    public static void playersWithoutBuildPermissionCannotRetrieveServings(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(6, 2, 6));
        var player = player(helper, pos);
        try {
            for (var item : representativeFoods()) {
                player.getInventory().clearContent();
                var expected = serving(item, 2);
                player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
                place(helper, player, pos, expected);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, 2));
                player.gameMode.changeGameModeForPlayer(GameType.ADVENTURE);
                player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit(pos));
                helper.assertTrue(!level.isEmptyBlock(pos) && countMatching(player, expected) == 0,
                        "Adventure player retrieved a serving without build permission");
                helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(6)).isEmpty(),
                        "Denied retrieval dropped a serving");
                level.removeBlock(pos, false);
                level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(6)).forEach(ItemEntity::discard);
            }
        } finally {
            level.removeBlock(pos, false);
            level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(6)).forEach(ItemEntity::discard);
            player.getInventory().clearContent();
        }
        helper.succeed();
    }

    private static List<Item> representativeFoods() {
        return List.of(ModItems.COOKING_DISHES.get("fried_egg").get(), ModItems.WINE.get(), ModItems.SALMON.get());
    }

    private static FakePlayer player(GameTestHelper helper, BlockPos pos) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "Food recovery"));
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        player.setPos(Vec3.atCenterOf(pos.offset(3, 0, 0)));
        return player;
    }

    private static ItemStack serving(Item item, int quality) {
        var stack = new ItemStack(item);
        QualityHelper.setQuality(stack, quality);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Original serving " + quality));
        var custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        custom.putString("FoodRecoveryMarker", "preserve all components");
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        return stack;
    }

    private static void place(GameTestHelper helper, FakePlayer player, BlockPos pos, ItemStack expected) {
        var level = helper.getLevel();
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        var held = expected.copyWithCount(3);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        var result = player.gameMode.useItemOn(player, level, held, InteractionHand.MAIN_HAND, hit(pos.below()));
        helper.assertTrue(result.consumesAction() && held.getCount() == 2
                        && level.getBlockState(pos).is(PlacedFoodPlacement.blockFor(expected)),
                "Real Shift placement failed or consumed the wrong count: " + expected.getItem());
    }

    private static BlockHitResult hit(BlockPos pos) {
        return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    }

    private static int countMatching(FakePlayer player, ItemStack expected) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var stack = player.getInventory().getItem(slot);
            if (ItemStack.isSameItemSameComponents(stack, expected)) count += stack.getCount();
        }
        return count;
    }
}

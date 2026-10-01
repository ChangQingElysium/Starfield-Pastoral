package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.block.cooking.CookingPlacedFoodBlock;
import com.stardew.craft.building.runtime.*;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.farm.FarmType;
import com.stardew.craft.festival.*;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.cooking.PlacedFoodPlacement;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;
import com.stardew.craft.port.PortBlockInteraction;

@GameTestHolder("stardewcraft_play_fixes")
@PrefixGameTestTemplate(false)
public final class GameplayAccessibilityGameTests {
    private GameplayAccessibilityGameTests() {}

    @GameTest(template = "construction_site")
    public static void protectedFoodCannotAwardEffectsOrItems(GameTestHelper h) {
        var level = h.getLevel();
        var registry = FarmInstanceRegistry.get(level.getServer());
        var owner = UUID.randomUUID();
        var farm = registry.createFarm(owner, "Food", "Food protection", FarmType.STANDARD);
        var buildings = BuildingWorldData.get(level.getServer());
        var family = PrefabDefinitions.get(PrefabDefinitions.COOP);
        var tier = family.tier(1);
        var anchor = h.absolutePos(new BlockPos(18, 1, 22));
        var rotation = PrefabDefinitions.rotation(Direction.SOUTH);
        var record = BuildingRecord.waiting(farm.getInstanceId(), farm.getSlotIndex(), family.id(), BuildingRecord.Mode.PREFAB,
                level.dimension().location(), anchor, PrefabDefinitions.world(tier.manager(), tier.anchor(), anchor, rotation),
                Direction.SOUTH, PrefabDefinitions.transform(family.reservation(), anchor, rotation));
        try {
            var permit = UUID.randomUUID();
            buildings.recordPurchase(permit, farm.getInstanceId(), true, family.id());
            h.assertTrue(buildings.beginPrefab(record, permit, 10) == BuildingWorldData.Result.SUCCESS, "Start food fixture");
            BuildingPlacementService.scaffold(level, buildings.find(record.id()));
            buildings.constructionDay(11, true);
            buildings.constructionDay(12, true);
            buildings.constructionDay(13, true);
            BuildingPlacementService.finish(level, buildings.find(record.id()));
            var pos = BuildingTransfer.nativeCells(level, record, 1).keySet().stream()
                    .filter(p -> !p.equals(record.manager())).findFirst().orElseThrow();
            var item = ModItems.COOKING_DISHES.get("fried_egg").get();
            var food = (CookingPlacedFoodBlock) PlacedFoodPlacement.blockFor(new ItemStack(item));
            var state = food.defaultBlockState();
            BuildingProtection.internal(() -> {
                level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(pos, state, 3);
            });
            var player = FakePlayerFactory.get(level, new GameProfile(owner, "Food"));
            var data = PlayerStardewDataAPI.getData(player);
            data.setEnergy(1);
            data.setHealth(1);
            var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
            h.assertTrue(BuildingProtection.protects(level, pos), "Native food belongs to the protected mask");
            for (boolean creative : new boolean[]{false, true}) {
                player.getAbilities().instabuild = creative;
                for (boolean shift : new boolean[]{false, true}) {
                    player.setShiftKeyDown(shift);
                    for (var hand : InteractionHand.values()) {
                        var event = new PlayerInteractEvent.RightClickBlock(player, hand, pos, hit);
                        MinecraftForge.EVENT_BUS.post(event);
                        h.assertTrue(event.isCanceled(), "Protected food use must stop before consumption");
                        // Exercise the block itself too: a vetoed removal must never award anything.
                        PortBlockInteraction.stateUseWithoutItem(state, level, player, hit);
                        h.assertTrue(data.getEnergy() == 1 && data.getHealth() == 1, "No repeatable food effects");
                        h.assertTrue(player.getInventory().countItem(item) == 0, "No shift-use item duplication");
                        h.assertTrue(level.getBlockState(pos).is(food), "Native food stays in place");
                    }
                }
            }
            for (var cake : new net.minecraft.world.level.block.Block[]{Blocks.CAKE, Blocks.CANDLE_CAKE}) {
                BuildingProtection.internal(() -> level.setBlock(pos, cake.defaultBlockState(), 3));
                var event = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit);
                MinecraftForge.EVENT_BUS.post(event);
                h.assertTrue(event.isCanceled(), "Native cake must not provide repeatable bites");
            }

            var free = h.absolutePos(new BlockPos(2, 2, 2));
            level.setBlock(free.below(), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(free, state, 3);
            var freeHit = new BlockHitResult(Vec3.atCenterOf(free), Direction.UP, free, false);
            player.getAbilities().instabuild = false;
            player.setShiftKeyDown(false);
            h.assertTrue(!BuildingProtection.deniesInteraction(level, free), "Player food remains usable");
            PortBlockInteraction.stateUseWithoutItem(state, level, player, freeHit);
            h.assertTrue(level.getBlockState(free).isAir() && data.getEnergy() > 1, "Player food is consumed and restores energy");
            float energy = data.getEnergy();
            PortBlockInteraction.stateUseWithoutItem(state, level, player, freeHit);
            h.assertTrue(data.getEnergy() == energy, "A consumed serving cannot award effects twice");
            level.setBlock(free, state, 3);
            player.setShiftKeyDown(true);
            PortBlockInteraction.stateUseWithoutItem(state, level, player, freeHit);
            PortBlockInteraction.stateUseWithoutItem(state, level, player, freeHit);
            h.assertTrue(level.getBlockState(free).isAir() && player.getInventory().countItem(item) == 1,
                    "Player food can be picked up exactly once");
        } finally {
            buildings.demolish(record.id());
            registry.deleteFarm(owner);
            BuildingProtection.clearMasks();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void eggPrizesTrackEachPlayersFirstVictory(GameTestHelper h) throws ReflectiveOperationException {
        var award = EggFestivalService.class.getDeclaredMethod("grantWinnerPrize", ServerPlayer.class);
        award.setAccessible(true);
        var first = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "FirstWinner"));
        var newcomer = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "NewWinner"));
        award.invoke(null, first);
        h.assertTrue(first.getInventory().countItem(ModItems.STRAW_HAT.get()) == 1, "First win grants the implemented straw hat");
        h.assertTrue(PlayerStardewDataAPI.getData(first).hasMailFlag("Egg Festival"), "First victory is persisted");
        award.invoke(null, first);
        award.invoke(null, first);
        h.assertTrue(first.getInventory().countItem(ModItems.STRAW_HAT.get()) == 1
                && first.getInventory().countItem(ModItems.PRIZE_TICKET.get()) == 2, "Later wins grant tickets");
        award.invoke(null, newcomer);
        h.assertTrue(newcomer.getInventory().countItem(ModItems.STRAW_HAT.get()) == 1
                && newcomer.getInventory().countItem(ModItems.PRIZE_TICKET.get()) == 0, "Another player's wins do not skip a newcomer's hat");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void festivalBoundsAllowSittingButStillDetectLeaving(GameTestHelper h) throws ReflectiveOperationException {
        Object[][] fixtures = {
                {EggFestivalService.class, "FESTIVAL_BOUNDS", 64},
                {SpiritEveFestivalService.class, "ENTRY_EXIT_BOUNDS", 64},
                {FairFestivalService.class, "ENTRY_EXIT_BOUNDS", 51},
                {LuauFestivalService.class, "ENTRY_EXIT_BOUNDS", 59},
                {MoonlightJelliesFestivalService.class, "ENTRY_EXIT_BOUNDS", 59},
                {WinterStarFestivalService.class, "ENTRY_EXIT_BOUNDS", 63},
                {FestivalOfIceService.class, "ENTRY_EXIT_BOUNDS", 61},
                {FlowerDanceService.class, "ENTRY_TRIGGER_BOUNDS", 58}
        };
        for (Object[] fixture : fixtures) {
            var service = (Class<?>) fixture[0];
            var field = service.getDeclaredField((String) fixture[1]);
            field.setAccessible(true);
            var bounds = (AABB) field.get(null);
            double standingY = (int) fixture[2];
            double x = bounds.getCenter().x;
            double z = bounds.getCenter().z;
            h.assertTrue(bounds.contains(x, standingY - .75, z), service.getSimpleName() + " must allow sitting below the floor boundary");
            h.assertTrue(!bounds.contains(x, standingY - 1.01, z), "Vertical tolerance is one block");
            h.assertTrue(!bounds.contains(x, standingY, bounds.minZ - .1), "Walking out still triggers the exit boundary");
            if (service != EggFestivalService.class && service != FlowerDanceService.class) {
                var arrival = service.getDeclaredField("SAFE_ENTRY_RETURN");
                arrival.setAccessible(true);
                var pos = (Vec3) arrival.get(null);
                h.assertTrue(bounds.contains(pos), "Fixed festival arrival is inside the venue bounds");
            }
        }
        h.succeed();
    }
}

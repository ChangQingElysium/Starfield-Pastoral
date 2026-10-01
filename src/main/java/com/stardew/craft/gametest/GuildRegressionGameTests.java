package com.stardew.craft.gametest;

import com.stardew.craft.port.PortItemData;
import com.mojang.authlib.GameProfile;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.core.ModGameRules;
import com.stardew.craft.event.FarmAreaProtectionEvents;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.shop.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestHolder("stardewcraft_guild_regressions")
@PrefixGameTestTemplate(false)
public final class GuildRegressionGameTests {
    @GameTest(templateNamespace = "stardewcraft_guild_regressions", template = "empty", timeoutTicks = 100)
    public static void guildBoardReplacesOnlyLegacyNoticesAndReadsBothHalves(GameTestHelper h) throws Exception {
        var level = h.getLevel();
        var dimension = net.minecraft.world.level.Level.class.getDeclaredField("dimension");
        dimension.setAccessible(true); var oldDimension = dimension.get(level);
        var base = GuildBoardPlacement.BOARD;
        var paper = com.stardew.craft.block.ModBlocks.PAPER_CHECKLIST.get().defaultBlockState()
                .setValue(com.stardew.craft.block.decor.MapDecorWallThinBlock.FACING, net.minecraft.core.Direction.SOUTH);
        var positions = java.util.List.of(base, base.above(), base.west(), base.north(), base.above().north());
        var original = new java.util.LinkedHashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        for (var pos : positions) original.put(pos, level.getBlockState(pos));
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "GuildBoard"));
        try {
            dimension.set(level, ModDimensions.STARDEW_VALLEY);
            var wall = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(new net.minecraft.resources.ResourceLocation("stardewcraft:wallpaper_87")).defaultBlockState();
            level.setBlock(base.north(), wall, 2); level.setBlock(base.above().north(), wall, 2);
            level.setBlock(base, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState(), 2);
            level.setBlock(base.above(), paper, 2); level.setBlock(base.west(), paper, 2);
            h.assertTrue(!GuildBoardPlacement.replaceLegacyNotices(level) && level.getBlockState(base).is(net.minecraft.world.level.block.Blocks.CHEST), "Board overwrote player furniture");
            level.setBlock(base, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
            h.assertTrue(GuildBoardPlacement.replaceLegacyNotices(level), "Shipped guild notices were not replaced");
            h.assertTrue(level.getBlockState(base.west()).isAir() && !GuildBoardPlacement.replaceLegacyNotices(level), "Legacy notice cleanup is not idempotent");
            player.setPos(109.5, 60, -148.5);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            for (var pos : java.util.List.of(base, base.above())) {
                var state = level.getBlockState(pos);
                h.assertTrue(state.is(com.stardew.craft.block.ModBlocks.GUILD_MONSTER_BOARD.get()) && !state.getShape(level, pos).isEmpty() && state.canSurvive(level, pos), "Board half is missing or untargetable");
                var data = PlayerDataManager.getPlayerData(player); data.removeMailFlag("checkedMonsterBoard");
                h.assertTrue(com.stardew.craft.world.interaction.MapInteractionService.acceptsHeldItems(player, pos), "Armed player cannot read board");
                var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), net.minecraft.core.Direction.SOUTH, pos, false);
                com.stardew.craft.world.interaction.MapInteractionService.interact(player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
                h.assertTrue(data.hasMailFlag("checkedMonsterBoard"), "Board half does not open progress");
            }
        } finally {
            com.stardew.craft.block.decor.MapDecorStaticBlock.runWithDropsSuppressed(() -> original.forEach((pos, state) -> level.setBlock(pos, state, 2)));
            player.getInventory().clearContent(); PlayerDataManager.get().removePlayerData(player.getUUID()); dimension.set(level, oldDimension);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_guild_regressions", template = "empty", timeoutTicks = 100)
    public static void rewardsOnlyClaimWhenTakenAndPublicRuleControlsInteraction(GameTestHelper h) throws Exception {
        var level = h.getLevel();
        var dimension = net.minecraft.world.level.Level.class.getDeclaredField("dimension");
        dimension.setAccessible(true);
        var old = dimension.get(level);
        var rule = level.getGameRules().getRule(ModGameRules.RULE_STARDEW_ALLOW_PUBLIC_BUILDING);
        boolean was = rule.get();
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "GuildRewards"));
        dimension.set(level, ModDimensions.STARDEW_VALLEY);
        try {
            player.setPos(110, 60, -146);
            var publicPos = new BlockPos(85, 60, -140);
            rule.set(false, level.getServer());
            h.assertTrue(!FarmAreaProtectionEvents.canModifyAt(player, publicPos), "Rule off still grants interaction");
            rule.set(true, level.getServer());
            h.assertTrue(FarmAreaProtectionEvents.canBuildAt(player, publicPos) && FarmAreaProtectionEvents.canModifyAt(player, publicPos), "Rule allows placement but blocks interaction");
            h.assertTrue(!FarmAreaProtectionEvents.isOnProtectedFarm(player, publicPos), "Public rule turns town into another player's farm");
            h.assertTrue(com.stardew.craft.npc.data.NpcDataRegistry.capabilities().containsKey("gil"), "Gil actor definition rejected");
            var data = PlayerDataManager.getPlayerData(player);
            h.assertTrue(MonsterSlayerGoalRegistry.getAllGoals().size() == 12, "Original guild goal missing");
            for (var goal : MonsterSlayerGoalRegistry.getAllGoals()) if (!goal.rewards().isEmpty()) {
                h.assertTrue(!GilService.rewardItem(goal).isEmpty(), "Unresolvable reward " + goal.goalKey());
                data.addMonsterKills(goal.goalKey(), goal.requiredKills());
            }
            var menu = new GilRewardMenu(1, player.getInventory());
            long rewards = menu.slots.subList(0, GilRewardMenu.REWARD_SLOTS).stream().filter(s -> s.hasItem()).count();
            h.assertTrue(rewards == 11, "Reward tray missing items");
            menu.removed(player);
            h.assertTrue(data.getClaimedSlayerRewards().isEmpty(), "Closing menu claimed an untaken reward");
            menu = new GilRewardMenu(2, player.getInventory());
            for (int i = 0; i < player.getInventory().items.size(); i++) player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            h.assertTrue(menu.quickMoveStack(player, 0).isEmpty() && !data.hasClaimedSlayerReward("Slimes"), "Full inventory consumes reward");
            player.getInventory().clearContent();
            h.assertTrue(!menu.quickMoveStack(player, 0).isEmpty() && data.hasClaimedSlayerReward("Slimes") && data.hasMailFlag("Gil_Slimes"), "Taking reward did not persist claim/shop unlock");
            h.assertTrue(menu.quickMoveStack(player, 0).isEmpty(), "Repeated click duplicates reward");
            var reopened = new GilRewardMenu(3, player.getInventory());
            h.assertTrue(reopened.slots.subList(0, GilRewardMenu.REWARD_SLOTS).stream().filter(s -> s.hasItem()).count() == 10, "Claimed reward returns on reopening");
            player.setPos(0, 60, 0);
            h.assertTrue(!reopened.stillValid(player) && reopened.quickMoveStack(player, 0).isEmpty(), "Remote reward claim accepted");
        } finally {
            player.getInventory().clearContent();
            PlayerDataManager.get().removePlayerData(player.getUUID());
            rule.set(was, level.getServer());
            dimension.set(level, old);
        }
        h.succeed();
    }
    @GameTest(templateNamespace = "stardewcraft_guild_regressions", template = "empty", timeoutTicks = 100)
    public static void recoveryWaitsForMailAndPreservesFullStack(GameTestHelper h) throws Exception {
        var level = h.getLevel();
        var dimension = net.minecraft.world.level.Level.class.getDeclaredField("dimension");
        dimension.setAccessible(true); var old = dimension.get(level);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "GuildRecovery"));
        int originalMoney = com.stardew.craft.player.PlayerStardewDataAPI.getMoney(player);
        dimension.set(level, ModDimensions.STARDEW_VALLEY);
        try {
            player.setPos(105, 60, -146);
            var data = PlayerDataManager.getPlayerData(player);
            var lost = new ItemStack(com.stardew.craft.item.ModItems.PARSNIP.get(), 7);
            com.stardew.craft.item.quality.QualityHelper.setQuality(lost, 4);
            PortItemData.set(lost, com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Recovery components"));
            data.setItemsLostLastDeath(java.util.List.of(lost)); com.stardew.craft.player.PlayerStardewDataAPI.setMoney(player, 10000);
            int expectedPrice = com.stardew.craft.api.v1.item.StardewItemDataApi.getSellPrice(lost) * lost.getCount();
            MarlonService.handleRecoveryPurchaseFromShop(player, 0);
            h.assertTrue(com.stardew.craft.player.PlayerStardewDataAPI.getMoney(player) == 10000 - expectedPrice, "Recovery price is not full stack sale value");
            h.assertTrue(data.getItemsLostLastDeath().isEmpty() && data.getMailForTomorrow().contains("MarlonRecovery"), "Recovery not queued for tomorrow");
            h.assertTrue(player.getInventory().isEmpty(), "Recovery delivered immediately");
            var loaded = com.stardew.craft.player.PlayerStardewData.fromNBT(data.toNBT(level.registryAccess()), player.getUUID(), level.registryAccess());
            h.assertTrue(ItemStack.matches(lost, loaded.getMarlonRecoveredItem()), "Recovery components/count lost on save/load");
            MarlonService.handleRecoveryPurchaseFromShop(player, 0);
            h.assertTrue(com.stardew.craft.player.PlayerStardewDataAPI.getMoney(player) == 10000 - expectedPrice, "Repeated purchase charged again");
            data.deliverTomorrowMail(1);
            com.stardew.craft.mail.MailService.openNextMail(player);
            h.assertTrue(data.getMarlonRecoveredItem().isEmpty(), "Pending recovered item not consumed by mail");
            h.assertTrue(player.getInventory().items.stream().anyMatch(stack -> ItemStack.matches(stack, lost)), "Mail did not return original component-bearing stack");
        } finally {
            com.stardew.craft.player.PlayerStardewDataAPI.setMoney(player, originalMoney);
            player.getInventory().clearContent(); PlayerDataManager.get().removePlayerData(player.getUUID()); dimension.set(level, old);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_guild_regressions", template = "empty", timeoutTicks = 100)
    public static void crystalariumFrontFacesPlacingPlayer(GameTestHelper h) {
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "CrystalFacing"));
        var pos = h.absolutePos(new BlockPos(1, 1, 1));
        var block = com.stardew.craft.block.ModBlocks.CRYSTALARIUM.get();
        for (var facing : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            player.setYRot(facing.toYRot());
            var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false);
            var context = new net.minecraft.world.item.context.BlockPlaceContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(block), hit);
            var state = block.getStateForPlacement(context);
            h.assertTrue(state != null && state.getValue(com.stardew.craft.block.utility.CrystalariumBlock.FACING) == facing.getOpposite(), "Crystalarium front reversed for " + facing);
        }
        h.succeed();
    }
}

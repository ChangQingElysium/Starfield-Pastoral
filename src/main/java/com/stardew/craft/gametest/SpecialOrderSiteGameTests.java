package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.specialorder.SpecialOrderBoardInstaller;
import com.stardew.craft.specialorder.SpecialOrderManager;
import com.stardew.craft.specialorder.SpecialOrderTicketService;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import com.stardew.craft.port.PortBlockInteraction;

@GameTestHolder("stardewcraft_special_orders")
@PrefixGameTestTemplate(false)
public final class SpecialOrderSiteGameTests {
    private static FakePlayer player(GameTestHelper h) {
        FakePlayer player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "TicketTest"));
        player.getInventory().clearContent();
        return player;
    }

    @GameTest(templateNamespace = "stardewcraft_special_orders", template = "ring_utilities")
    public static void ticketsArePersonalOneAtATimeAndRequireUnlock(GameTestHelper h) {
        try (var ignored = miningDataLevel(h)) {
            var p = player(h); var other = player(h);
            var data = PlayerStardewDataAPI.getData(p);
            data.setSpecialOrderPrizeTickets(2);
            PlayerStardewDataAPI.getData(other).setSpecialOrderPrizeTickets(7);
            h.assertTrue(!SpecialOrderTicketService.claimOne(p), "Locked board dispensed a ticket");
            data.addMailFlag(SpecialOrderManager.BOARD_UNLOCK_FLAG);
            h.assertTrue(SpecialOrderTicketService.claimOne(p), "First ticket was not collected");
            h.assertTrue(data.getSpecialOrderPrizeTickets() == 1 && p.getInventory().countItem(ModItems.PRIZE_TICKET.get()) == 1,
                    "A click must transfer exactly one ticket");
            h.assertTrue(SpecialOrderTicketService.claimOne(p) && !SpecialOrderTicketService.claimOne(p), "Empty box duplicated a ticket");
            h.assertTrue(p.getInventory().countItem(ModItems.PRIZE_TICKET.get()) == 2, "Wrong collected total");
            h.assertTrue(PlayerStardewDataAPI.getData(other).getSpecialOrderPrizeTickets() == 7, "Changed another player's rewards");
            h.succeed();
        }
    }

    @GameTest(templateNamespace = "stardewcraft_special_orders", template = "ring_utilities")
    public static void fullInventoryPreservesTicketsAndPartialStackAcceptsOne(GameTestHelper h) {
        try (var ignored = miningDataLevel(h)) {
            var p = player(h); var data = PlayerStardewDataAPI.getData(p);
            data.addMailFlag(SpecialOrderManager.BOARD_UNLOCK_FLAG); data.setSpecialOrderPrizeTickets(3);
            for (int i = 0; i < 36; i++) p.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
            p.getAbilities().instabuild = true;
            h.assertTrue(!SpecialOrderTicketService.claimOne(p) && data.getSpecialOrderPrizeTickets() == 3,
                    "Full creative inventory lost a pending ticket");
            ItemStack stack = new ItemStack(ModItems.PRIZE_TICKET.get()); stack.setCount(stack.getMaxStackSize() - 1);
            p.getInventory().setItem(0, stack);
            h.assertTrue(SpecialOrderTicketService.claimOne(p) && data.getSpecialOrderPrizeTickets() == 2, "Existing stack capacity ignored");
            h.assertTrue(stack.getCount() == stack.getMaxStackSize(), "Stack not filled exactly");
            h.assertTrue(!SpecialOrderTicketService.claimOne(p) && data.getSpecialOrderPrizeTickets() == 2, "Full stack lost a ticket");
            h.succeed();
        }
    }

    @GameTest(templateNamespace = "stardewcraft_special_orders", template = "ring_utilities")
    public static void installerUpgradesBoardAddsBoxAndSupportsUpperClick(GameTestHelper h) {
        try (var ignored = miningDataLevel(h)) {
            var level = h.getLevel(); BlockPos board = h.absolutePos(new BlockPos(7, 2, 7));
            var state = ModBlocks.SPECIAL_ORDERS_BOARD.get().defaultBlockState().setValue(MapDecorStaticBlock.FACING, Direction.SOUTH);
            level.setBlock(board, state, 18);
            h.assertTrue(SpecialOrderBoardInstaller.placeSite(level, board), "Old board was not upgraded");
            h.assertTrue(SpecialOrderBoardInstaller.placeSite(level, board), "Repeat installation was not idempotent");
            for (int x = -2; x <= 1; x++) for (int y = 0; y < 2; y++) {
                var expected = x == -2 ? ModBlocks.PRIZE_TICKET_BOX.get() : ModBlocks.SPECIAL_ORDERS_BOARD.get();
                h.assertTrue(level.getBlockState(board.offset(x, y, 0)).is(expected), "Missing reserved footprint cell");
            }
            var p = player(h);var data = PlayerStardewDataAPI.getData(p);
            data.addMailFlag(SpecialOrderManager.BOARD_UNLOCK_FLAG);data.setSpecialOrderPrizeTickets(1);
            BlockPos upper = board.west(2).above();
            PortBlockInteraction.stateUseWithoutItem(level.getBlockState(upper), level,p,new BlockHitResult(Vec3.atCenterOf(upper),Direction.SOUTH,upper,false));
            h.assertTrue(data.getSpecialOrderPrizeTickets() == 0 && p.getInventory().countItem(ModItems.PRIZE_TICKET.get()) == 1,
                    "Box upper half did not claim from its main part");
            h.succeed();
        }
    }

    @GameTest(templateNamespace = "stardewcraft_special_orders", template = "ring_utilities")
    public static void blockedInstallationDoesNotOverwriteOrPlaceHalfSite(GameTestHelper h) {
        var level = h.getLevel();BlockPos board = h.absolutePos(new BlockPos(7, 2, 7));
        BlockPos obstruction = board.west(2).above();level.setBlock(obstruction, Blocks.CHEST.defaultBlockState(), 18);
        h.assertTrue(!SpecialOrderBoardInstaller.placeSite(level,board), "Overwrote a chest");
        h.assertTrue(level.getBlockState(board).isAir() && level.getBlockState(board.west(2)).isAir(), "Placed half a site");
        h.assertTrue(level.getBlockState(obstruction).is(Blocks.CHEST), "Obstruction removed");
        level.removeBlock(obstruction,false);
        h.assertTrue(SpecialOrderBoardInstaller.placeSite(level,board), "Could not retry cleared site");
        h.succeed();
    }

    private interface LevelScope extends AutoCloseable { @Override void close(); }

    /** The headless fixture omits the mining world required by the existing full player-data sync. */
    @SuppressWarnings("unchecked")
    private static LevelScope miningDataLevel(GameTestHelper h) {
        try {
            var field = net.minecraft.server.MinecraftServer.class.getDeclaredField("levels");
            field.setAccessible(true);
            var levels = (java.util.Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>,
                    net.minecraft.server.level.ServerLevel>) field.get(h.getLevel().getServer());
            var key = com.stardew.craft.core.ModMiningDimensions.STARDEW_MINING;
            var previous = levels.put(key, h.getLevel());
            return () -> { if (previous == null) levels.remove(key); else levels.put(key, previous); };
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}

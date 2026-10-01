package com.stardew.craft.gametest;

import com.stardew.craft.port.PortItemStacks;
import com.mojang.authlib.GameProfile;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.blockentity.ShippingBinBlockEntity;
import com.stardew.craft.economy.sell.ProfessionSellPriceService;
import com.stardew.craft.economy.sell.SellSource;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.menu.ShippingBinMenu;
import com.stardew.craft.network.overnight.OvernightSettlementTracker;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.ProfessionType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(StardewCraft.MODID)
@PrefixGameTestTemplate(false)
public final class ShippingOwnershipGameTests {
    private ShippingOwnershipGameTests() {}

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void shippingKeepsEachDepositorAcrossOpenDepositAndReload(GameTestHelper helper) {
        ShippingBinBlockEntity bin = bin(helper);
        ServerPlayer alice = player(helper, "Shipper A");
        ServerPlayer bob = player(helper, "Shipper B");
        ItemStack goods = new ItemStack(ModItems.PARSNIP.get(), 3);
        PlayerDataManager.getPlayerData(alice).addProfession(ProfessionType.TILLER);
        int alicePrice = ProfessionSellPriceService.quoteItem(PlayerDataManager.getPlayerData(alice), goods,
                SellSource.SHIPPING_BIN).finalUnitPrice();
        helper.assertTrue(bin.depositFromPlayer(alice, goods), "first deposit failed");
        bin.startOpen(bob);
        bin.stopOpen(bob);
        helper.assertTrue(bin.depositFromPlayer(bob, goods.copyWithCount(7)), "second deposit failed");
        assertLedger(helper, alice, 3, alicePrice);
        assertLedger(helper, bob, 0, -1);
        CompoundTag saved = bin.saveWithoutMetadata();
        helper.assertTrue(saved.getUUID("bufferOwnerId").equals(bob.getUUID()), "new buffer has the wrong owner");
        bin.clearContent();
        bin.load(saved);
        bin.startOpen(alice);
        bin.flushBufferForOvernight();
        bin.flushBufferForOvernight();
        bin.stopOpen(alice);
        assertLedger(helper, alice, 3, alicePrice);
        assertLedger(helper, bob, 7, -1);
        helper.assertTrue(bin.isEmpty(), "flushed buffer was not cleared");
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void shippingMenuAttributesPickupShiftSwapAndDrag(GameTestHelper helper) {
        ShippingBinBlockEntity bin = bin(helper);
        ServerPlayer alice = player(helper, "Menu A");
        ServerPlayer bob = player(helper, "Menu B");
        ShippingBinMenu a = new ShippingBinMenu(1, alice.getInventory(), bin);
        ShippingBinMenu b = new ShippingBinMenu(2, bob.getInventory(), bin);
        a.setCarried(new ItemStack(ModItems.PARSNIP.get(), 3));
        a.clicked(0, 0, ClickType.PICKUP, alice);
        helper.assertTrue(a.getCarried().isEmpty(), "cursor was not consumed after deposit");
        bob.getInventory().setItem(9, new ItemStack(ModItems.PARSNIP.get(), 4));
        bob.getInventory().setItem(10, new ItemStack(ModItems.PARSNIP.get(), 6));
        b.quickMoveStack(bob, 1);
        helper.assertTrue(bob.getInventory().getItem(9).isEmpty() && bob.getInventory().getItem(10).getCount()==6,
                "A single shift deposit changed an adjacent inventory slot");
        alice.getInventory().setItem(0, new ItemStack(ModItems.PARSNIP.get(), 2));
        a.clicked(0, 0, ClickType.SWAP, alice);
        helper.assertTrue(alice.getInventory().getItem(0).isEmpty(), "number-key deposit left duplicates");
        // Exercise the actual vanilla quick-craft path: start, select bin, finish.
        b.setCarried(new ItemStack(ModItems.PARSNIP.get(), 5));
        b.clicked(-999, 0, ClickType.QUICK_CRAFT, bob);
        b.clicked(0, 1, ClickType.QUICK_CRAFT, bob);
        b.clicked(-999, 2, ClickType.QUICK_CRAFT, bob);
        helper.assertTrue(b.getCarried().isEmpty(), "drag deposit did not consume exactly its contribution");
        helper.assertTrue(bin.getItem(0).getCount() == 5, "drag merged someone else's already-shipped batch");
        bin.flushBufferForOvernight();
        assertLedger(helper, alice, 5, -1);
        assertLedger(helper, bob, 9, -1);
        // Multi-slot drag takes a different vanilla path, passing a combined stack to setByPlayer.
        bin.depositFromPlayer(alice, new ItemStack(ModItems.PARSNIP.get(), 4));
        b.setCarried(new ItemStack(ModItems.PARSNIP.get(), 10));
        b.clicked(-999, 0, ClickType.QUICK_CRAFT, bob);
        b.clicked(0, 1, ClickType.QUICK_CRAFT, bob);
        b.clicked(1, 1, ClickType.QUICK_CRAFT, bob);
        b.clicked(-999, 2, ClickType.QUICK_CRAFT, bob);
        helper.assertTrue(b.getCarried().isEmpty() && bob.getInventory().getItem(9).getCount() == 5,
                "multi-slot drag consumed the wrong quantity");
        helper.assertTrue(bin.getItem(0).getCount() == 5, "multi-slot drag included the previous batch");
        bin.flushBufferForOvernight();
        assertLedger(helper, alice, 9, -1);
        assertLedger(helper, bob, 14, -1);
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void thrownShippingUsesThrowerAndUnknownItemsStayRecoverable(GameTestHelper helper) {
        ShippingBinBlockEntity bin = bin(helper);
        ServerPlayer alice = player(helper, "Thrower");
        ServerPlayer bob = player(helper, "Observer");
        bin.startOpen(bob);
        ItemEntity thrown = new ItemEntity(helper.getLevel(), bin.getBlockPos().getX(),
                bin.getBlockPos().getY(), bin.getBlockPos().getZ(), new ItemStack(ModItems.PARSNIP.get(), 6));
        thrown.setThrower(alice.getUUID());
        // Saving/reloading drops the cached entity reference; UUID attribution must still work offline.
        CompoundTag itemTag = thrown.saveWithoutId(new CompoundTag());
        ItemEntity reloaded = new ItemEntity(helper.getLevel(), 0, 0, 0, ItemStack.EMPTY);
        reloaded.load(itemTag);
        bin.swallowItemEntity(reloaded);
        helper.assertTrue(reloaded.isRemoved(), "attributed thrown stack was not consumed");
        bin.flushBufferForOvernight();
        assertLedger(helper, alice, 6, -1);
        assertLedger(helper, bob, 0, -1);
        ItemEntity unknown = new ItemEntity(helper.getLevel(), 0, 0, 0, new ItemStack(ModItems.PARSNIP.get(), 2));
        bin.swallowItemEntity(unknown);
        helper.assertTrue(!unknown.isRemoved() && bin.isEmpty(), "unattributed stack was assigned to a bystander");
        bin.stopOpen(bob);
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void shippingWithdrawalDoesNotReassignRemainingBatch(GameTestHelper helper) {
        ShippingBinBlockEntity bin = bin(helper);
        ServerPlayer alice = player(helper, "Original owner");
        ServerPlayer bob = player(helper, "Withdrawer");
        bin.depositFromPlayer(alice, new ItemStack(ModItems.PARSNIP.get(), 10));
        ShippingBinMenu menu = new ShippingBinMenu(3, bob.getInventory(), bin);
        menu.clicked(0, 1, ClickType.PICKUP, bob);
        helper.assertTrue(menu.getCarried().getCount() == 5, "right-click withdrawal did not take half");
        bin.flushBufferForOvernight();
        assertLedger(helper, alice, 5, -1);
        assertLedger(helper, bob, 0, -1);
        menu.clicked(0, 0, ClickType.PICKUP, bob);
        bin.flushBufferForOvernight();
        assertLedger(helper, bob, 5, -1);
        helper.succeed();
    }

    private static ShippingBinBlockEntity bin(GameTestHelper helper) {
        BlockPos relative = new BlockPos(8, 1, 8);
        helper.setBlock(relative, ModBlocks.SHIPPING_BIN.get());
        return (ShippingBinBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(relative));
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities", timeoutTicks = 80)
    public static void shippableDropsKeepDifferentThrowersThroughVanillaMerging(GameTestHelper helper) {
        ServerPlayer alice = player(helper, "Drop A");
        ServerPlayer bob = player(helper, "Drop B");
        var pos = helper.absoluteVec(new net.minecraft.world.phys.Vec3(3, 3, 3));
        ItemEntity a = drop(helper, alice, pos, 3);
        ItemEntity b = drop(helper, bob, pos, 7);
        // Same-owner drops should still merge normally.
        ItemEntity a2 = drop(helper, alice, pos, 2);
        helper.runAtTickTime(45, () -> {
            helper.assertTrue(b.isAlive() && b.getItem().getCount() == 7, "another thrower's goods merged into Bob's batch");
            helper.assertTrue(a.isAlive() != a2.isAlive(), "same-owner drops stopped merging");
            ItemEntity remaining = a.isAlive() ? a : a2;
            helper.assertTrue(remaining.getItem().getCount() == 5, "Alice's merged batch has the wrong quantity");
            ShippingBinBlockEntity bin = bin(helper);
            bin.swallowItemEntity(remaining);
            bin.swallowItemEntity(b);
            bin.flushBufferForOvernight();
            assertLedger(helper, alice, 5, -1);
            assertLedger(helper, bob, 7, -1);
            helper.succeed();
        });
    }

    private static ItemEntity drop(GameTestHelper helper, ServerPlayer owner, net.minecraft.world.phys.Vec3 pos, int count) {
        ItemEntity entity = new ItemEntity(helper.getLevel(), pos.x, pos.y, pos.z, new ItemStack(ModItems.PARSNIP.get(), count));
        entity.setThrower(owner.getUUID());
        entity.setNoGravity(true);
        entity.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), name));
        PlayerDataManager.getPlayerData(player);
        return player;
    }

    private static void assertLedger(GameTestHelper helper, ServerPlayer player, int expectedCount, int expectedPrice) {
        CompoundTag ledger = OvernightSettlementTracker.get(helper.getLevel().getServer())
                .save(new CompoundTag(), helper.getLevel().registryAccess());
        int count = 0;
        for (Tag entry : ledger.getList("Players", Tag.TAG_COMPOUND)) {
            CompoundTag data = (CompoundTag) entry;
            if (!data.getUUID("Player").equals(player.getUUID())) continue;
            for (Tag item : data.getList("Items", Tag.TAG_COMPOUND)) {
                CompoundTag itemData = (CompoundTag) item;
                ItemStack stack = PortItemStacks.parse(helper.getLevel().registryAccess(), itemData.getCompound("Stack")).orElseThrow();
                count += stack.getCount();
                if (expectedPrice >= 0) helper.assertTrue(itemData.getInt("PricePerItem") == expectedPrice,
                        "sale price used another player's profession");
            }
        }
        helper.assertTrue(count == expectedCount, "wrong shipping owner/count for " + player.getName().getString()
                + ": expected " + expectedCount + ", got " + count);
    }
}

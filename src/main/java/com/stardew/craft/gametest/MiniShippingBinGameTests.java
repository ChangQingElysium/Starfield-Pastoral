package com.stardew.craft.gametest;

import com.stardew.craft.port.PortItemStacks;
import com.mojang.authlib.GameProfile;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.blockentity.MiniShippingBinBlockEntity;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.menu.MiniShippingBinMenu;
import com.stardew.craft.network.overnight.OvernightSettlementTracker;
import com.stardew.craft.player.PlayerDataManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestHolder("stardewcraft_mini_shipping")
@PrefixGameTestTemplate(false)
public final class MiniShippingBinGameTests {
    private MiniShippingBinGameTests() {}

    @GameTest(templateNamespace = "stardewcraft_mini_shipping", template = "ring_utilities")
    public static void nineSlotsRejectOverflowAndAllowWithdrawal(GameTestHelper h) {
        var bin = bin(h);
        var player = player(h);
        var inventory = bin.account(player.getUUID());
        var menu = new MiniShippingBinMenu(1, player.getInventory(), inventory);
        h.assertTrue(inventory.getContainerSize() == 9 && menu.slots.size() == 45, "Wrong capacity");
        menu.setCarried(new ItemStack(Items.BARRIER));
        menu.clicked(0, 0, ClickType.PICKUP, player);
        h.assertTrue(inventory.isEmpty() && !menu.getCarried().isEmpty(), "Accepted an unshippable item");
        menu.setCarried(ItemStack.EMPTY);
        for (int i = 0; i < 9; i++) inventory.setItem(i, new ItemStack(ModItems.PARSNIP.get(), inventory.getMaxStackSize()));
        player.getInventory().setItem(9, new ItemStack(ModItems.PARSNIP.get(), 4));
        menu.quickMoveStack(player, 9);
        h.assertTrue(player.getInventory().getItem(9).getCount() == 4, "Full bin consumed goods");
        menu.clicked(5, 0, ClickType.PICKUP, player);
        h.assertTrue(inventory.getItem(5).isEmpty() && !menu.getCarried().isEmpty(), "Could not reclaim a non-last slot");
        assertLedger(h, player, 0);
        menu.removed(player);
        bin.dropAllContents(h.getLevel(), bin.getBlockPos());
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_mini_shipping", template = "ring_utilities")
    public static void reloadPreservesSlotsOwnersAndExactlyOnceSettlement(GameTestHelper h) {
        var bin = bin(h);
        var alice = player(h);
        var bob = player(h);
        bin.account(alice.getUUID()).setItem(8, new ItemStack(ModItems.PARSNIP.get(), 3));
        bin.account(bob.getUUID()).setItem(2, new ItemStack(ModItems.PARSNIP.get(), 7));
        h.assertTrue(!bin.isEmpty(), "Chest movement protection cannot see stored shipments");
        CompoundTag saved = bin.saveWithoutMetadata();
        bin.load(saved);
        h.assertTrue(bin.account(alice.getUUID()).getItem(0).isEmpty()
                && bin.account(alice.getUUID()).getItem(8).getCount() == 3, "Reload changed slot positions");
        h.assertTrue(bin.account(bob.getUUID()).getItem(2).getCount() == 7, "Reload mixed owners");
        MiniShippingBinBlockEntity.flushAllForOvernight();
        MiniShippingBinBlockEntity.flushAllForOvernight();
        h.assertTrue(bin.isEmpty(), "Settled bin remains marked full");
        assertLedger(h, alice, 3);
        assertLedger(h, bob, 7);
        h.assertTrue(bin.account(alice.getUUID()).isEmpty() && bin.account(bob.getUUID()).isEmpty(), "Shipped goods remained claimable");
        h.succeed();
    }

    private static MiniShippingBinBlockEntity bin(GameTestHelper h) {
        var relative = new BlockPos(8, 1, 8);
        h.setBlock(relative, ModBlocks.MINI_SHIPPING_BIN.get());
        var bin = (MiniShippingBinBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(relative));
        bin.onLoad();
        return bin;
    }
    private static ServerPlayer player(GameTestHelper h) {
        var player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(),
                new GameProfile(UUID.randomUUID(), "Mini Shipper"));
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), player) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) { }
        };
        PlayerDataManager.getPlayerData(player);
        return player;
    }
    private static void assertLedger(GameTestHelper h, ServerPlayer player, int expected) {
        var ledger = OvernightSettlementTracker.get(h.getLevel().getServer()).save(new CompoundTag(), h.getLevel().registryAccess());
        int count = 0;
        for (Tag entry : ledger.getList("Players", 10)) {
            var data = (CompoundTag) entry;
            if (!data.getUUID("Player").equals(player.getUUID())) continue;
            for (Tag item : data.getList("Items", 10)) {
                count += PortItemStacks.parse(h.getLevel().registryAccess(), ((CompoundTag)item).getCompound("Stack")).orElseThrow().getCount();
            }
        }
        h.assertTrue(count == expected, "Wrong owner or duplicate settlement: expected " + expected + ", got " + count);
    }
}

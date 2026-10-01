package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.utility.ShippingBinBlock;
import com.stardew.craft.block.utility.WoodenChestBlock;
import com.stardew.craft.blockentity.MiniShippingBinBlockEntity;
import com.stardew.craft.blockentity.ShippingBinBlockEntity;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.model.ShippingBinLidMotion;
import com.stardew.craft.player.PlayerDataManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestHolder("stardewcraft_mini_shipping")
@PrefixGameTestTemplate(false)
public final class ShippingAnimationGameTests {
    private ShippingAnimationGameTests() {}

    @GameTest(templateNamespace = "stardewcraft_mini_shipping", template = "ring_utilities")
    public static void twoCellFootprintRotatesAndNeverOverwritesNeighbors(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(8, 1, 8));
        var block = (ShippingBinBlock) ModBlocks.SHIPPING_BIN.get();
        for (var facing : Direction.Plane.HORIZONTAL) {
            var state = block.defaultBlockState().setValue(ShippingBinBlock.FACING, facing);
            var side = pos.relative(facing.getClockWise());
            level.setBlock(pos, state, 3);
            level.setBlock(side, Blocks.STONE.defaultBlockState(), 3);
            h.assertTrue(!block.placeExtensions(level, pos, state) && level.getBlockState(side).is(Blocks.STONE), "Overwrote old farm neighbor");
            h.assertTrue(!((ShippingBinBlockEntity)level.getBlockEntity(pos)).hasFullFootprint(), "Blocked legacy bin must remain compact");
            level.removeBlock(side, false);
            h.assertTrue(block.placeExtensions(level, pos, state), "Failed to reserve second cell");
            h.assertTrue(level.getBlockState(side).getValue(ShippingBinBlock.PART) == ShippingBinBlock.Part.EXTENSION
                    && level.getBlockEntity(side) == null && pos.equals(block.findMainPos(level, side, level.getBlockState(side))), "Duplicate inventory or wrong facing");
            var area = ShippingBinBlock.proximityArea(pos, state);
            h.assertTrue(Math.abs(area.getXsize() * area.getZsize() - 12) < .001, "Proximity area is not four by three tiles");
            var shape = state.getCollisionShape(level, pos).bounds();
            h.assertTrue(shape.minX >= 0 && shape.maxX <= 1 && shape.minZ >= 0 && shape.maxZ <= 1, "Collision escaped reserved cell");
            level.destroyBlock(side, false);
            h.assertTrue(level.getBlockState(pos).isAir(), "Breaking companion left main behind");
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_mini_shipping", template = "ring_utilities")
    public static void lidsFollowAllNearbyPlayersRatherThanMenus(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(8, 1, 8));
        var level = h.getLevel();
        level.setBlock(pos, ModBlocks.SHIPPING_BIN.get().defaultBlockState(), 3);
        var bin = (ShippingBinBlockEntity) level.getBlockEntity(pos);
        var first = player(h, pos.offset(-1, 0, 0));
        var second = player(h, pos.offset(2, 0, 0));
        try {
            ShippingBinBlockEntity.serverTick(level, pos, bin.getBlockState(), bin);
            h.assertTrue(bin.getBlockState().getValue(ShippingBinBlock.OPEN), "Approaching without clicking did not open lid");
            first.setPos(pos.getX() + 8, pos.getY(), pos.getZ());
            ShippingBinBlockEntity.serverTick(level, pos, bin.getBlockState(), bin);
            h.assertTrue(bin.getBlockState().getValue(ShippingBinBlock.OPEN), "One departure closed lid on second player");
            second.setPos(pos.getX() + 8, pos.getY(), pos.getZ());
            bin.startOpen(first);
            ShippingBinBlockEntity.serverTick(level, pos, bin.getBlockState(), bin);
            h.assertTrue(!bin.getBlockState().getValue(ShippingBinBlock.OPEN), "Remote menu kept proximity lid open");
            bin.stopOpen(first);
            level.removeBlock(pos, false);
            level.setBlock(pos, ModBlocks.MINI_SHIPPING_BIN.get().defaultBlockState(), 3);
            var mini = (MiniShippingBinBlockEntity)level.getBlockEntity(pos);
            first.setPos(pos.getX() + 1.5, pos.getY(), pos.getZ() + 1.5);
            mini.tick();
            h.assertTrue(mini.getBlockState().getValue(WoodenChestBlock.OPEN), "Mini diagonal adjacent tile did not open");
            first.setPos(pos.getX() + 2.5, pos.getY(), pos.getZ() + .5);
            mini.tick();
            h.assertTrue(!mini.getBlockState().getValue(WoodenChestBlock.OPEN), "Mini exceeded original three by three range");
            var motion = new ShippingBinLidMotion();
            motion.tick(false, 4.8f);
            motion.tick(true, 4.8f);
            motion.tick(true, 4.8f);
            float before = motion.radians(1);
            motion.tick(false, 4.8f);
            h.assertTrue(Math.abs(motion.radians(0) - before) < .0001 && motion.radians(1) < before, "Reversal jumped to endpoint");
        } finally {
            first.remove(Entity.RemovalReason.DISCARDED);
            second.remove(Entity.RemovalReason.DISCARDED);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_mini_shipping", template = "ring_utilities")
    public static void shipmentEventsUseActualGoodsAndNeverReplayFromSaves(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(8, 1, 8));
        var level = h.getLevel();
        level.setBlock(pos, ModBlocks.SHIPPING_BIN.get().defaultBlockState(), 3);
        var bin = (ShippingBinBlockEntity)level.getBlockEntity(pos);
        var player = player(h, pos.north());
        try {
            h.assertTrue(!bin.depositFromPlayer(player, new ItemStack(Items.BARRIER)) && bin.shipmentItem().isEmpty(), "Rejected shipment animated");
            var incoming = new ItemStack(ModItems.PARSNIP.get(), 4);
            h.assertTrue(bin.depositFromPlayer(player, incoming), "Valid shipment rejected");
            var first = bin.getUpdateTag();
            h.assertTrue(bin.shipmentItem().is(ModItems.PARSNIP.get()) && bin.shipmentItem().getCount() == 1
                    && bin.getItem(0).getCount() == 4 && incoming.getCount() == 4, "Visual prop altered actual inventory");
            bin.depositFromPlayer(player, new ItemStack(ModItems.PARSNIP.get(), 2));
            long serial = bin.getUpdateTag().getLong("ShipmentSerial");
            h.assertTrue(serial > first.getLong("ShipmentSerial"), "Repeated same-item deposit did not restart motion");
            bin.removeItem(0, 1);
            h.assertTrue(serial == bin.getUpdateTag().getLong("ShipmentSerial"), "Withdrawal replayed shipment");
            var saved = bin.saveWithoutMetadata();
            h.assertTrue(!saved.contains("ShipmentItem") && !saved.contains("ShipmentTick"), "Visual event leaked into persistent save");
            bin.clearContent();
        } finally { player.remove(Entity.RemovalReason.DISCARDED); }
        h.succeed();
    }

    private static FakePlayer player(GameTestHelper h, BlockPos pos) {
        var player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "Ship Animator"));
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        PlayerDataManager.getPlayerData(player);
        h.getLevel().addNewPlayer(player);
        return player;
    }
}

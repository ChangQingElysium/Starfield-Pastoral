package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.utility.AutoGrabberBlock;
import com.stardew.craft.blockentity.AutoGrabberBlockEntity;
import com.stardew.craft.network.payload.BuildingLedgerActionPayload;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("stardewcraft_collector_ledger")
@PrefixGameTestTemplate(false)
public final class CollectorLedgerGameTests {
    private CollectorLedgerGameTests() {}

    @GameTest(templateNamespace = "stardewcraft_collector_ledger", template = "empty", timeoutTicks = 200)
    public static void ledgerEncodesAnimalUuidAndExistingNames(GameTestHelper helper) {
        UUID session = UUID.randomUUID();
        String animal = UUID.randomUUID().toString();
        helper.assertTrue(animal.length() == 36, "Animal selection must exercise the reported UUID length");
        for (var request : new BuildingLedgerActionPayload[]{
                new BuildingLedgerActionPayload(session, "animals", animal),
                new BuildingLedgerActionPayload(session, "animals", ""),
                new BuildingLedgerActionPayload(session, "rename", "牛".repeat(32)),
                new BuildingLedgerActionPayload(session, "refresh", "")}) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                BuildingLedgerActionPayload.CODEC.encode(buffer, request);
                helper.assertTrue(request.equals(BuildingLedgerActionPayload.CODEC.decode(buffer))
                                && !buffer.isReadable(),
                        "Building ledger action changed during network roundtrip");
            } finally {
                buffer.release();
            }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_collector_ledger", template = "empty", timeoutTicks = 200)
    public static void breakingEitherCollectorPartSettlesEachItemOnce(GameTestHelper helper) {
        var level = helper.getLevel();
        var block = ModBlocks.AUTO_GRABBER.get();
        BlockPos main = helper.absolutePos(new BlockPos(1, 1, 1));
        AABB area = new AABB(main).expandTowards(0, 1, 0).inflate(1);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "Collector break"));
        try {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                for (int slots : new int[]{0, 1, 9, 10, AutoGrabberBlockEntity.SLOT_COUNT}) {
                    for (boolean upper : new boolean[]{false, true}) {
                        for (GameType mode : new GameType[]{GameType.SURVIVAL, GameType.CREATIVE}) {
                            for (var item : level.getEntitiesOfClass(ItemEntity.class, area)) item.discard();
                            player.setGameMode(mode);
                            player.setPos(main.getX() + .5, main.getY(), main.getZ() + 2);
                            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
                            level.setBlock(main.below(), Blocks.STONE.defaultBlockState(), 3);
                            var state = block.defaultBlockState().setValue(AutoGrabberBlock.FACING, facing);
                            level.setBlock(main, state, 3);
                            block.setPlacedBy(level, main, state, player, new ItemStack(block));
                            var collector = (AutoGrabberBlockEntity) level.getBlockEntity(main);
                            helper.assertTrue(collector != null, "Collector placement has no storage");
                            int eggs = 0, wheat = 0;
                            for (int slot = 0; slot < slots; slot++) {
                                int amount = slot % 3 + 1;
                                boolean egg = slot % 2 == 0;
                                collector.setItem(slot, new ItemStack(egg ? Items.EGG : Items.WHEAT, amount));
                                if (egg) eggs += amount; else wheat += amount;
                            }
                            helper.assertTrue(level.getBlockState(main).getValue(AutoGrabberBlock.FULL) == (slots > 0)
                                            && level.getBlockState(main.above()).getValue(AutoGrabberBlock.FULL) == (slots > 0),
                                    "Collector fullness did not reach both parts");
                            String scenario = facing + "/slots=" + slots + "/upper=" + upper + "/" + mode;
                            helper.assertTrue(player.gameMode.destroyBlock(upper ? main.above() : main),
                                    "Player could not break collector: " + scenario);
                            helper.assertTrue(level.isEmptyBlock(main) && level.isEmptyBlock(main.above())
                                            && level.getBlockEntity(main) == null,
                                    "Removal resurrected a collector part or storage: " + scenario);
                            var drops = level.getEntitiesOfClass(ItemEntity.class, area);
                            int machines = 0, droppedEggs = 0, droppedWheat = 0;
                            for (var entity : drops) {
                                ItemStack stack = entity.getItem();
                                if (stack.is(block.asItem())) machines += stack.getCount();
                                else if (stack.is(Items.EGG)) droppedEggs += stack.getCount();
                                else if (stack.is(Items.WHEAT)) droppedWheat += stack.getCount();
                                else throw new AssertionError("Unexpected collector drop: " + stack);
                            }
                            helper.assertTrue(machines == (mode == GameType.CREATIVE ? 0 : 1),
                                    "Collector item settled more than once: " + scenario + ", drops=" + machines);
                            helper.assertTrue(droppedEggs == eggs && droppedWheat == wheat,
                                    "Stored produce was lost or duplicated: " + scenario);
                            // A stale UI/automation reference must not revive the removed machine or drop it again.
                            collector.refreshVisualState();
                            collector.dropAllContents(level, main);
                            helper.assertTrue(level.isEmptyBlock(main) && level.isEmptyBlock(main.above())
                                            && level.getEntitiesOfClass(ItemEntity.class, area).size() == drops.size(),
                                    "Removed collector settled or appeared again: " + scenario);
                        }
                    }
                }
            }
        } finally {
            player.discard();
        }
        helper.succeed();
    }
}

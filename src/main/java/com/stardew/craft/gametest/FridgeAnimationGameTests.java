package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.blockentity.FridgeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.OptionalInt;
import java.util.UUID;

@GameTestHolder("stardewcraft_fridge")
@PrefixGameTestTemplate(false)
public final class FridgeAnimationGameTests {
    private static FridgeBlockEntity place(GameTestHelper h, Direction facing) {
        var pos = h.absolutePos(new BlockPos(4, 1, 4));
        h.getLevel().removeBlock(pos, false);
        h.getLevel().removeBlock(pos.above(), false);
        h.getLevel().setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        var state = ModBlocks.FRIDGE.get().defaultBlockState().setValue(MapDecorStaticBlock.FACING, facing);
        h.getLevel().setBlock(pos, state, 3);
        h.getLevel().setBlock(pos.above(), state.setValue(MapDecorStaticBlock.PART, MapDecorStaticBlock.Part.EXTENSION), 3);
        return (FridgeBlockEntity) h.getLevel().getBlockEntity(pos);
    }

    private static FakePlayer player(GameTestHelper h, BlockPos pos) {
        var player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "Fridge test")) {
            @Override public OptionalInt openMenu(MenuProvider provider) {
                containerMenu = provider.createMenu(1, getInventory(), this);
                return OptionalInt.of(1);
            }
        };
        player.setPos(Vec3.atCenterOf(pos));
        return player;
    }

    private static boolean open(FridgeBlockEntity fridge) {
        return fridge.getUpdateTag(fridge.getLevel().registryAccess()).getBoolean("DoorOpen");
    }

    @GameTest(templateNamespace = "stardewcraft_fridge", template = "machine_test", timeoutTicks = 40)
    public static void bothHalvesAndLastViewer(GameTestHelper h) {
        var fridge = place(h, Direction.NORTH);
        var pos = fridge.getBlockPos();
        var level = h.getLevel();
        var first = player(h, pos);
        var second = player(h, pos);
        for (var pair : new Object[][]{{pos, first}, {pos.above(), second}}) {
            var cell = (BlockPos) pair[0];
            var user = (FakePlayer) pair[1];
            var result = level.getBlockState(cell).useWithoutItem(level, user,
                    new BlockHitResult(Vec3.atCenterOf(cell), Direction.NORTH, cell, false));
            h.assertTrue(result.consumesAction() && user.containerMenu instanceof ChestMenu menu
                    && menu.getContainer() == fridge, "Both halves must open the same fridge");
        }
        h.assertTrue(open(fridge), "Two viewers should open the door");
        first.containerMenu.removed(first);
        h.assertTrue(open(fridge), "First viewer closing must not close the door for the second");
        second.containerMenu.removed(second);
        h.assertTrue(!open(fridge), "Last viewer must close the door");
        fridge.startOpen(h.makeMockPlayer(GameType.SPECTATOR));
        h.assertTrue(!open(fridge), "Spectator must not hold the door open");
        fridge.startOpen(first);
        fridge.recheckOpeners(); // Neither mock is in the level: emulate a lost/disconnected viewer.
        h.assertTrue(!open(fridge), "Recheck must release stale viewers");
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_fridge", template = "machine_test", timeoutTicks = 40)
    public static void continuousReversibleMotionAndCollision(GameTestHelper h) {
        for (var facing : Direction.Plane.HORIZONTAL) {
            var fridge = place(h, facing);
            var pos = fridge.getBlockPos();
            var state = fridge.getBlockState();
            var mainShape = state.getCollisionShape(h.getLevel(), pos).toAabbs();
            var upperState = h.getLevel().getBlockState(pos.above());
            var upperShape = upperState.getCollisionShape(h.getLevel(), pos.above()).toAabbs();
            h.assertTrue(state.triggerEvent(h.getLevel(), pos, 1, 1), "Block event must reach fridge entity");
            float before = 0;
            for (int i = 0; i < 5; i++) {
                FridgeBlockEntity.clientTick(h.getLevel(), pos, state, fridge);
                float now = fridge.getDoorOpenness(1);
                h.assertTrue(now > before && fridge.getDoorOpenness(.5F) > before
                        && fridge.getDoorOpenness(.5F) < now, "Motion must interpolate between ticks");
                before = now;
            }
            state.triggerEvent(h.getLevel(), pos, 1, 0);
            h.assertTrue(fridge.getDoorOpenness(1) == before, "Changing direction must not jump");
            for (int i = 0; i < 10; i++) FridgeBlockEntity.clientTick(h.getLevel(), pos, state, fridge);
            h.assertTrue(fridge.getDoorOpenness(1) == 0, "Door must fully close");
            state.triggerEvent(h.getLevel(), pos, 1, 1);
            for (int i = 0; i < 10; i++) FridgeBlockEntity.clientTick(h.getLevel(), pos, state, fridge);
            h.assertTrue(fridge.getDoorOpenness(1) == 1, "Door must fully open");
            h.assertTrue(mainShape.equals(state.getCollisionShape(h.getLevel(), pos).toAabbs())
                    && upperShape.equals(upperState.getCollisionShape(h.getLevel(), pos.above()).toAabbs()),
                    "Opening must leave both collision shapes unchanged for " + facing);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_fridge", template = "machine_test", timeoutTicks = 40)
    public static void trackingSyncAndStoredItems(GameTestHelper h) {
        var fridge = place(h, Direction.NORTH);
        fridge.setItem(3, new ItemStack(Items.DIAMOND, 7));
        var user = player(h, fridge.getBlockPos());
        user.openMenu(fridge);
        var tag = fridge.getUpdateTag(h.getLevel().registryAccess());
        h.assertTrue(tag.getBoolean("DoorOpen") && !tag.contains("items"), "Tracking sync must carry pose, not inventory");
        var client = new FridgeBlockEntity(fridge.getBlockPos(), fridge.getBlockState());
        client.handleUpdateTag(tag, h.getLevel().registryAccess());
        h.assertTrue(client.getDoorOpenness(0) == 1, "New tracker must see an already open fridge");
        var restored = new FridgeBlockEntity(fridge.getBlockPos(), fridge.getBlockState());
        restored.loadWithComponents(fridge.saveWithoutMetadata(h.getLevel().registryAccess()), h.getLevel().registryAccess());
        h.assertTrue(restored.getItem(3).is(Items.DIAMOND) && restored.getItem(3).getCount() == 7,
                "Existing inventory save format must survive");
        h.assertTrue(restored.getDoorOpenness(1) == 0, "Viewer state must not persist across world reload");
        user.containerMenu.removed(user);
        h.succeed();
    }
}

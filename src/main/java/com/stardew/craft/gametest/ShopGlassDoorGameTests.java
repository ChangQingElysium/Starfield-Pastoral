package com.stardew.craft.gametest;

import com.stardew.craft.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Opt-in: -PgameTestNamespaces=stardewcraft_shop_doors. */
@GameTestHolder("stardewcraft_shop_doors")
@PrefixGameTestTemplate(false)
public final class ShopGlassDoorGameTests {
    @GameTest(templateNamespace = "stardewcraft_shop_doors", template = "house_details")
    public static void bothPlacementOrdersPairAllFacingsAndPreserveOpening(GameTestHelper h) {
        var level = h.getLevel();
        var door = ModBlocks.SHOP_GLASS_DOOR.get();
        var pos = h.absolutePos(new BlockPos(7, 2, 7));
        for (var facing : Direction.Plane.HORIZONTAL) {
            for (var side : new Direction[]{facing.getClockWise(), facing.getCounterClockWise()}) {
                for (double click : new double[]{.2, .8}) {
                    clear(h, pos);
                    place(h, pos, facing, click);
                    door.setOpen(null, level, level.getBlockState(pos), pos, true);
                    var neighbor = pos.relative(side);
                    place(h, neighbor, facing, .5);
                    var hinge = side == facing.getClockWise() ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT;
                    var opposite = hinge == DoorHingeSide.LEFT ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT;
                    for (int y = 0; y < 2; y++) {
                        var a = level.getBlockState(pos.above(y));
                        var b = level.getBlockState(neighbor.above(y));
                        h.assertTrue(a.getValue(DoorBlock.HINGE) == hinge && b.getValue(DoorBlock.HINGE) == opposite,
                                "Handles failed to meet: " + facing + "/" + side + "/" + click + "/" + y);
                        h.assertTrue(a.getValue(DoorBlock.FACING) == facing && b.getValue(DoorBlock.FACING) == facing,
                                "Pairing changed the facing");
                        h.assertTrue(a.getValue(DoorBlock.OPEN), "Pairing closed the first leaf");
                        h.assertTrue(!b.getValue(DoorBlock.OPEN), "Second leaf opened without interaction");
                        var vanilla = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing)
                                .setValue(DoorBlock.HINGE, hinge).setValue(DoorBlock.OPEN, true);
                        h.assertTrue(a.getCollisionShape(level, pos).bounds().equals(vanilla.getCollisionShape(level, pos).bounds()),
                                "Door collision differs from vanilla");
                    }
                    place(h, neighbor.relative(side), facing, .5);
                    h.assertTrue(level.getBlockState(neighbor).getValue(DoorBlock.HINGE) == opposite,
                            "Third leaf changed the existing pair");
                    level.destroyBlock(neighbor.above(), false);
                    h.assertTrue(level.getBlockState(neighbor).isAir(), "Upper-half removal left a lower half");
                }
            }
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_shop_doors", template = "house_details")
    public static void differentDoorsStayIndependentAndRedstoneStillWorks(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(7, 2, 7));
        clear(h, pos);
        var old = ModBlocks.RED_GLASS_DOOR.get().defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH)
                .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);
        level.setBlock(pos, old, 3);
        ModBlocks.RED_GLASS_DOOR.get().setPlacedBy(level, pos, old, null, ItemStack.EMPTY);
        place(h, pos.west(), Direction.SOUTH, .5);
        h.assertTrue(level.getBlockState(pos).equals(old), "Shop door changed another door type");
        clear(h, pos);
        place(h, pos, Direction.SOUTH, .2);
        var before = level.getBlockState(pos);
        place(h, pos.west(), Direction.NORTH, .5);
        h.assertTrue(level.getBlockState(pos).equals(before), "Oppositely facing doors paired");
        level.setBlock(pos.east(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        for (int y = 0; y < 2; y++) {
            h.assertTrue(level.getBlockState(pos.above(y)).getValue(DoorBlock.OPEN), "Redstone did not open both halves");
        }
        level.setBlock(pos.east(), Blocks.AIR.defaultBlockState(), 3);
        h.assertTrue(!level.getBlockState(pos).getValue(DoorBlock.OPEN), "Door stayed open after power removal");
        h.succeed();
    }

    private static void clear(GameTestHelper h, BlockPos pos) {
        for (var p : BlockPos.betweenClosed(pos.offset(-3, 0, -3), pos.offset(3, 2, 3))) {
            h.getLevel().setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        }
        for (var p : BlockPos.betweenClosed(pos.offset(-3, -1, -3), pos.offset(3, -1, 3))) {
            h.getLevel().setBlock(p, Blocks.STONE.defaultBlockState(), 3);
        }
    }

    private static void place(GameTestHelper h, BlockPos pos, Direction facing, double click) {
        var player = FakePlayerFactory.getMinecraft(h.getLevel());
        player.setYRot(facing.toYRot());
        player.setXRot(0);
        player.setPos(Vec3.atCenterOf(pos.relative(facing.getOpposite(), 2)));
        var stack = new ItemStack(ModBlocks.SHOP_GLASS_DOOR.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(new Vec3(pos.getX() + click, pos.getY(), pos.getZ() + click),
                        Direction.UP, pos.below(), false)));
        h.assertTrue(h.getLevel().getBlockState(pos).is(ModBlocks.SHOP_GLASS_DOOR.get()), "Item placement failed");
    }
}

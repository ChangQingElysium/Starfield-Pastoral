package com.stardew.craft.gametest;

import com.stardew.craft.port.PortGameTests;
import com.mojang.authlib.GameProfile;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.ParkBenchBlock;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import com.stardew.craft.port.PortBlockInteraction;

@GameTestHolder(StardewCraft.MODID)
@PrefixGameTestTemplate(false)
public final class ParkBenchGameTests {
    private ParkBenchGameTests() {}

    private static BlockPlaceContext context(net.minecraft.world.entity.player.Player player, BlockPos pos) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModBlocks.PARK_BENCH.get()));
        return new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0, .5, 0), Direction.UP, pos.below(), false)));
    }

    @GameTest(templateNamespace = "stardewcraft_bench", template = "ring_utilities")
    public static void benchSeatsPlayersIndependentlyAndRemovesOnlyBrokenSeat(GameTestHelper helper) {
        var level = helper.getLevel(); var block = ModBlocks.PARK_BENCH.get();
        BlockPos origin = helper.absolutePos(new BlockPos(6, 1, 6));
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            // MinecraftForge FakePlayer disables riding. These mock players retain vanilla riding behavior.
            var first = PortGameTests.makeMockPlayer(helper, net.minecraft.world.level.GameType.SURVIVAL);
            var second = PortGameTests.makeMockPlayer(helper, net.minecraft.world.level.GameType.SURVIVAL);
            first.setYRot(facing.getOpposite().toYRot());
            BlockPos neighbor = origin.relative(facing.getClockWise());
            for (BlockPos pos : new BlockPos[]{origin, neighbor}) {
                level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
                var ctx = context(first, pos);
                helper.assertTrue(((BlockItem) ctx.getItemInHand().getItem()).place(ctx).consumesAction(), "Seat setup failed");
            }
            var hit = new BlockHitResult(Vec3.atCenterOf(origin.above()), facing, origin.above(), false);
            first.setShiftKeyDown(true);
            PortBlockInteraction.stateUseWithoutItem(level.getBlockState(origin.above()), level, first, hit);
            helper.assertTrue(!first.isPassenger(), "Sneaking unexpectedly sat down");
            first.setShiftKeyDown(false);
            var sitResult = PortBlockInteraction.stateUseWithoutItem(level.getBlockState(origin.above()), level, first, hit);
            helper.assertTrue(first.getVehicle() instanceof com.stardew.craft.entity.seat.SofaSeatEntity,
                    "Backrest click did not sit down: " + facing + ", result=" + sitResult + ", shift=" + first.isShiftKeyDown()
                    + ", main=" + block.findMainPos(level, origin.above(), level.getBlockState(origin.above()))
                    + ", seats=" + level.getEntitiesOfClass(com.stardew.craft.entity.seat.SofaSeatEntity.class, new AABB(origin).inflate(2)).size());
            var seat = (com.stardew.craft.entity.seat.SofaSeatEntity) first.getVehicle();
            helper.assertTrue(seat.getSofaPos().equals(origin), "Backrest resolved to wrong seat");
            for (int i = 0; i < 3; i++) level.tickNonPassenger(seat);
            helper.assertTrue(first.getVehicle() == seat && seat.isAlive(), "Seat disappeared on its next tick");
            helper.assertTrue(Math.abs(first.getY() + .75 - origin.getY() - 10.0/16.0) < .0001,
                    "Rider's hips do not meet the bench surface");
            helper.assertTrue(first.getYRot() == facing.toYRot(), "Rider faces the backrest");
            PortBlockInteraction.stateUseWithoutItem(level.getBlockState(origin), level, second,
                    new BlockHitResult(Vec3.atCenterOf(origin), facing, origin, false));
            helper.assertTrue(!second.isPassenger() && seat.getFirstPassenger() == first, "Occupied seat was stolen");
            PortBlockInteraction.stateUseWithoutItem(level.getBlockState(neighbor), level, second,
                    new BlockHitResult(Vec3.atCenterOf(neighbor), facing, neighbor, false));
            helper.assertTrue(second.isPassenger() && second.getVehicle() != seat, "Connected seats cannot be occupied independently");
            level.destroyBlock(origin.above(), false);
            helper.assertTrue(!first.isPassenger() && seat.isRemoved(), "Broken bench retained its passenger/seat entity");
            helper.assertTrue(second.isPassenger() && level.getBlockState(neighbor).is(block), "Breaking a seat ejected its neighbor");
            var secondSeat = second.getVehicle();
            second.stopRiding(); level.tickNonPassenger(secondSeat); level.tickNonPassenger(secondSeat);
            helper.assertTrue(secondSeat.isRemoved(), "Dismount left a seat entity behind");
            ParkBenchBlock.runWithDropsSuppressed(() -> level.removeBlock(neighbor, false));
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_bench", template = "ring_utilities")
    public static void benchConnectsAndSeparatesInEveryDirection(GameTestHelper helper) {
        var level = helper.getLevel();
        var block = ModBlocks.PARK_BENCH.get();
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "BenchTest"));
        BlockPos origin = helper.absolutePos(new BlockPos(4, 3, 4));
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            player.setYRot(facing.getOpposite().toYRot());
            BlockPos[] seats = {origin, origin.relative(facing.getClockWise()), origin.relative(facing.getClockWise(), 2)};
            for (BlockPos pos : seats) {
                level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
                var ctx = context(player, pos);
                helper.assertTrue(((BlockItem) ctx.getItemInHand().getItem()).place(ctx).consumesAction(), "Bench placement failed " + facing);
            }
            for (int i = 0; i < 3; i++) {
                var state = level.getBlockState(seats[i]);
                helper.assertTrue(state.getValue(ParkBenchBlock.FACING) == facing, "Wrong facing");
                helper.assertTrue(state.getValue(ParkBenchBlock.LEFT) == (i > 0)
                        && state.getValue(ParkBenchBlock.RIGHT) == (i < 2), "Stale end or middle connections");
                helper.assertTrue(level.getBlockState(seats[i].above()).getValue(ParkBenchBlock.PART) == ParkBenchBlock.Part.EXTENSION,
                        "Backrest cell not reserved");
                helper.assertTrue(!level.getBlockState(seats[i].above()).getShape(level, seats[i].above()).isEmpty(), "Upper backrest not selectable");
                var mirror = block.mirror(state, Mirror.FRONT_BACK);
                helper.assertTrue(mirror.getValue(ParkBenchBlock.LEFT) == state.getValue(ParkBenchBlock.RIGHT), "Mirror did not exchange ends");
            }
            int variant = level.getBlockState(seats[0]).getValue(ParkBenchBlock.VARIANT);
            for (var item : level.getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(6))) item.discard();
            level.destroyBlock(seats[1].above(), true);
            helper.assertTrue(level.getBlockState(seats[1]).isAir() && level.getBlockState(seats[1].above()).isAir(), "Upper break left an orphan");
            for (int i : new int[]{0, 2}) {
                var state = level.getBlockState(seats[i]);
                helper.assertTrue(state.is(block) && !state.getValue(ParkBenchBlock.LEFT) && !state.getValue(ParkBenchBlock.RIGHT),
                        "Removing middle did not restore both armrests");
            }
            helper.assertTrue(level.getBlockState(seats[0]).getValue(ParkBenchBlock.VARIANT) == variant, "Connection update changed wood variant");
            var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(6));
            helper.assertTrue(drops.stream().mapToInt(e -> e.getItem().getCount()).sum() == 1
                    && drops.getFirst().getItem().is(block.asItem()), "Breaking upper cell did not drop exactly one bench");
            for (BlockPos pos : seats) ParkBenchBlock.runWithDropsSuppressed(() -> level.removeBlock(pos, false));
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_bench", template = "ring_utilities")
    public static void benchRejectsBlockedBackrestAndPerpendicularConnection(GameTestHelper helper) {
        var level = helper.getLevel(); var block = ModBlocks.PARK_BENCH.get();
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "BenchSpaceTest"));
        BlockPos pos = helper.absolutePos(new BlockPos(3, 3, 3));
        player.setYRot(Direction.SOUTH.toYRot());
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(pos.above(), Blocks.STONE.defaultBlockState(), 3);
        helper.assertTrue(block.getStateForPlacement(context(player, pos)) == null, "Bench placed inside a ceiling");
        level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(pos.east(), block.defaultBlockState().setValue(ParkBenchBlock.FACING, Direction.EAST), 3);
        var state = block.getStateForPlacement(context(player, pos));
        helper.assertTrue(state != null && !state.getValue(ParkBenchBlock.RIGHT), "Bench joined a perpendicular seat");
        helper.succeed();
    }
}

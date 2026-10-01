package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.ParkFountainBlock;
import com.stardew.craft.block.decor.ParkFountainMotion;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("stardewcraft_fountain")
@PrefixGameTestTemplate(false)
public final class ParkFountainGameTests {
    @GameTest(templateNamespace = "stardewcraft_fountain", template = "ring_utilities")
    public static void fountainSeasonsKeepStoneIdentityAndFreezeInWinter(GameTestHelper helper) throws Exception {
        for (int season = 0; season < 4; season++) {
            helper.assertTrue(com.stardew.craft.block.decor.ParkFountainSeasons.flows(season) == (season != 3),
                    "Water animation and sound must both stop in winter");
            String name = new String[]{"spring", "summer", "fall", "winter"}[season];
            try (var stream = ParkFountainGameTests.class.getResourceAsStream(
                    "/assets/stardewcraft/models/block/park_fountain/" + name + "/cell_12.json")) {
                helper.assertTrue(stream != null, "Missing seasonal native geometry");
                var model = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                helper.assertTrue(model.getAsJsonObject("textures").get("particle").getAsString().contains("/"+name+"/"),
                        "Particle did not follow the authored seasonal material");
            }
        }
        helper.succeed();
    }
    private static BlockPlaceContext context(FakePlayer player, BlockPos pos) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModBlocks.PARK_FOUNTAIN.get(), 3));
        return new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0, .5, 0), Direction.UP, pos.below(), false)));
    }

    private static FakePlayer prepare(GameTestHelper helper) {
        var level = helper.getLevel();
        for (int x = 1; x <= 12; x++) for (int z = 1; z <= 7; z++) for (int y = 0; y <= 4; y++)
            level.setBlock(helper.absolutePos(new BlockPos(x, y, z)),
                    y == 0 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "FountainTest"));
        var away = helper.absolutePos(new BlockPos(14, 1, 14));
        player.setPos(away.getX(), away.getY(), away.getZ());
        return player;
    }

    private static int cells(GameTestHelper helper, BlockPos origin) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-2, 0, -2), origin.offset(2, 3, 2)))
            if (helper.getLevel().getBlockState(pos).is(ModBlocks.PARK_FOUNTAIN.get())) count++;
        return count;
    }

    @GameTest(templateNamespace = "stardewcraft_fountain", template = "ring_utilities", timeoutTicks = 200)
    public static void fountainPlacementCollisionAndAdjacentRemoval(GameTestHelper helper) {
        var player = prepare(helper); var level = helper.getLevel(); var block = ModBlocks.PARK_FOUNTAIN.get();
        BlockPos origin = helper.absolutePos(new BlockPos(4, 1, 4)), neighbor = origin.east(5);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            player.setYRot(facing.getOpposite().toYRot());
            for (BlockPos pos : new BlockPos[]{origin, neighbor}) {
                var ctx = context(player, pos);
                helper.assertTrue(((BlockItem) ctx.getItemInHand().getItem()).place(ctx).consumesAction(), "Placement failed: " + facing);
                helper.assertTrue(cells(helper, pos) == 56, "Incomplete footprint");
                helper.assertTrue(level.getBlockEntity(pos) != null, "Missing animation controller");
                for (BlockPos cell : BlockPos.betweenClosed(pos.offset(-2, 0, -2), pos.offset(2, 3, 2))) {
                    var state = level.getBlockState(cell);
                    if (!state.is(block)) continue;
                    helper.assertTrue(pos.equals(block.findMainPos(level, cell, state)), "Cell points at wrong fountain");
                    if (!cell.equals(pos)) helper.assertTrue(level.getBlockEntity(cell) == null, "Extra animation controller");
                }
            }
            BlockPos air = origin.offset(1, 1, 1);
            helper.assertTrue(!Shapes.joinIsNotEmpty(level.getBlockState(air).getCollisionShape(level, air),
                    Shapes.box(.25, .25, .25, .75, .75, .75), BooleanOp.AND), "Pool air space became a solid box");
            helper.assertTrue(!level.getBlockState(origin).getCollisionShape(level, origin).isEmpty(), "Missing stone collision");
            for (var item : level.getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(8))) item.discard();
            level.destroyBlock(origin.east(2), true);
            helper.assertTrue(cells(helper, origin) == 0 && level.getBlockEntity(origin) == null, "Removal left parts/controller behind");
            helper.assertTrue(cells(helper, neighbor) == 56, "Removal damaged the neighboring fountain");
            var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(8));
            helper.assertTrue(drops.stream().mapToInt(e -> e.getItem().getCount()).sum() == 1
                    && com.stardew.craft.port.PortJava.getFirst(drops).getItem().is(block.asItem()), "Expected exactly one fountain item");
            ParkFountainBlock.runWithDropsSuppressed(() -> level.removeBlock(neighbor, false));
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_fountain", template = "ring_utilities")
    public static void fountainRejectsObstructionsWithoutConsumingItem(GameTestHelper helper) {
        var player = prepare(helper); var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(4, 1, 4));
        for (BlockPos obstacle : new BlockPos[]{origin.above(3), origin.offset(2, 0, 2)}) {
            level.setBlock(obstacle, Blocks.STONE.defaultBlockState(), 3);
            var ctx = context(player, origin);
            helper.assertTrue(!((BlockItem) ctx.getItemInHand().getItem()).place(ctx).consumesAction(), "Fountain overwrote obstruction");
            helper.assertTrue(ctx.getItemInHand().getCount() == 3 && cells(helper, origin) == 0, "Failed placement consumed an item or left a fragment");
            level.setBlock(obstacle, Blocks.AIR.defaultBlockState(), 3);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_fountain", template = "ring_utilities")
    public static void fountainContinuousMotionLoopsAndKeepsRipplesInsidePool(GameTestHelper helper) {
        for (String kind : new String[]{"drop", "ripple", "splash"}) for (int side = 0; side < 4; side++)
            for (int index = 0; index < (kind.equals("drop") ? 4 : kind.equals("ripple") ? 2 : 3); index++)
                for (int frame = 0; frame < 108; frame++) {
                    var a = ParkFountainMotion.sample(kind, side, index, frame / 30.0);
                    var b = ParkFountainMotion.sample(kind, side, index, frame / 30.0 + 3.6);
                    helper.assertTrue(Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y()) + Math.abs(a.z() - b.z()) < .0001
                            && Math.abs(a.alpha() - b.alpha()) < .0001, "Motion loop jumps");
                    if (kind.equals("ripple")) helper.assertTrue(a.x() - 7 * a.sx() >= 7 && a.x() + 7 * a.sx() <= 73
                            && a.z() - 7 * a.sz() >= 7 && a.z() + 7 * a.sz() <= 73, "Ripple enters pool wall");
                }
        helper.succeed();
    }
}

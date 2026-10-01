package com.stardew.craft.gametest;

import com.stardew.craft.port.PortGameTests;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.entity.bomb.BombBlastPattern;
import com.stardew.craft.entity.bomb.StardewBombEntity;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.item.bomb.BombType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("stardewcraft_bombs")
@PrefixGameTestTemplate(false)
public final class BombPlaneGameTests {
    @GameTest(templateNamespace = "stardewcraft_bombs", template = "ring_utilities", timeoutTicks = 100)
    public static void originalRadiiDestroyOnlyTheCurrentPlane(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(8, 4, 8));
        var explode = StardewBombEntity.class.getDeclaredMethod("explode");
        explode.setAccessible(true);
        for (BombType type : BombType.values()) {
            var pattern = BombBlastPattern.circle(type.getRadius());
            for (var tile : pattern) {
                BlockPos pos = center.offset(tile.x(), 0, tile.z());
                level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(pos, Blocks.COPPER_BLOCK.defaultBlockState());
                level.setBlockAndUpdate(pos.above(), Blocks.COPPER_BLOCK.defaultBlockState());
            }
            BlockPos outside = center.offset(type.getRadius(), 0, type.getRadius());
            level.setBlockAndUpdate(outside, Blocks.COPPER_BLOCK.defaultBlockState());
            var bomb = new StardewBombEntity(ModEntities.STARDEW_BOMB.get(), level);
            bomb.setBombType(type);
            bomb.setPos(center.getX() + 0.5D, center.getY(), center.getZ() + 0.5D);
            explode.invoke(bomb);
            for (var tile : pattern) {
                BlockPos pos = center.offset(tile.x(), 0, tile.z());
                helper.assertTrue(level.isEmptyBlock(pos), type + " missed tile " + tile);
                helper.assertTrue(level.getBlockState(pos.below()).is(Blocks.STONE), "Bomb dug through the floor");
                helper.assertTrue(level.getBlockState(pos.above()).is(Blocks.COPPER_BLOCK), "Bomb hit the upper floor");
            }
            helper.assertTrue(level.getBlockState(outside).is(Blocks.COPPER_BLOCK), "Bomb destroyed square corner");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_bombs", template = "ring_utilities", timeoutTicks = 100)
    public static void blastTillsDirtAndMineSoilButNeverGrass(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(4, 4, 4));
        var till = StardewBombEntity.class.getDeclaredMethod("tillSoilInCircle",
                net.minecraft.server.level.ServerLevel.class, BlockPos.class, int.class,
                net.minecraft.world.entity.LivingEntity.class);
        till.setAccessible(true);
        BlockPos dirt = center.below(), grass = dirt.east(), dark = dirt.west(), mine = dirt.north();
        for (var pos : java.util.List.of(dirt, grass, dark, mine)) level.setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(dirt, ModBlocks.DIRT.get().defaultBlockState());
        level.setBlockAndUpdate(grass, ModBlocks.GRASS_BLOCK.get().defaultBlockState());
        level.setBlockAndUpdate(dark, ModBlocks.DARK_GRASS_BLOCK.get().defaultBlockState());
        level.setBlockAndUpdate(mine, ModBlocks.MINE_EARTH_SOIL.get().defaultBlockState());
        var bomb = new StardewBombEntity(ModEntities.STARDEW_BOMB.get(), level);
        // Fixed seed + repeated attempts cover the intentional 10% no-op without timing.
        bomb.getRandom().setSeed(147L);
        for (int i = 0; i < 12; i++) till.invoke(bomb, level, center, 1, null);
        helper.assertTrue(level.getBlockState(dirt).is(ModBlocks.FARMLAND.get()), "Ordinary mod dirt was not tilled");
        helper.assertTrue(level.getBlockState(grass).is(ModBlocks.GRASS_BLOCK.get()), "Grass was tilled");
        helper.assertTrue(level.getBlockState(dark).is(ModBlocks.DARK_GRASS_BLOCK.get()), "Dark grass was tilled");
        helper.assertTrue(level.getBlockState(mine).is(ModBlocks.MINE_EARTH_LOOSE_SOIL.get()), "Mine soil lost its identity or stayed untouched");
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_bombs", template = "ring_utilities")
    public static void halfStairsFollowClickSideFacingAndUpsideDownPlacement(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = PortGameTests.makeMockPlayer(helper, net.minecraft.world.level.GameType.SURVIVAL);
        var block = com.stardew.craft.templates.TemplateContent.TEMPLATE_BLOCKS
                .get(com.stardew.craft.templates.TemplateShape.HALF_STAIRS).get();
        BlockPos pos = helper.absolutePos(new BlockPos(4, 3, 4));
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(pos.above(), Blocks.STONE.defaultBlockState());
        for (var facing : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            player.setYRot(facing.toYRot());
            var left = facing.getCounterClockWise();
            for (boolean mirrored : new boolean[]{false, true}) for (boolean flipped : new boolean[]{false, true}) {
                double offset = mirrored ? 0.25D : -0.25D;
                var face = flipped ? net.minecraft.core.Direction.DOWN : net.minecraft.core.Direction.UP;
                BlockPos support = flipped ? pos.above() : pos.below();
                var hit = new net.minecraft.world.phys.BlockHitResult(new net.minecraft.world.phys.Vec3(
                        pos.getX() + .5D + left.getStepX() * offset, pos.getY() + (flipped ? 1D : 0D),
                        pos.getZ() + .5D + left.getStepZ() * offset), face, support, false);
                var context = new net.minecraft.world.item.context.BlockPlaceContext(player,
                        net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(block), hit);
                var state = block.getStateForPlacement(context);
                helper.assertTrue(state.getValue(com.stardew.craft.templates.MaterialTemplateBlock.FACING) == facing, "Half stair turned unexpectedly");
                helper.assertTrue(state.getValue(com.stardew.craft.templates.MaterialTemplateBlock.FLIPPED) == flipped, "Wrong vertical half");
                helper.assertTrue(state.getValue(com.stardew.craft.templates.HalfStairsTemplateBlock.MIRRORED) == mirrored, "Wrong horizontal half");
                var bounds = state.getShape(level, pos).bounds();
                double occupiedX = .5D + left.getStepX() * offset;
                double occupiedZ = .5D + left.getStepZ() * offset;
                helper.assertTrue(occupiedX > bounds.minX && occupiedX < bounds.maxX
                        && occupiedZ > bounds.minZ && occupiedZ < bounds.maxZ, "Collision did not follow clicked side");
            }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_bombs", template = "ring_utilities")
    public static void damageUsesTheOriginalSquareButCannotReachOtherFloors(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(8, 4, 8));
        var bomb = new StardewBombEntity(ModEntities.STARDEW_BOMB.get(), level);
        bomb.setBombType(BombType.MEGA_BOMB);
        bomb.setPos(center.getX() + .5D, center.getY(), center.getZ() + .5D);
        var mobs = new java.util.ArrayList<net.minecraft.world.entity.animal.Cow>();
        try {
            for (int dy : new int[]{0, 1, -1}) {
                var mob = net.minecraft.world.entity.EntityType.COW.create(level);
                mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100D);
                mob.setHealth(100F);
                mob.setNoAi(true);
                mob.setNoGravity(true);
                mob.setPos(center.getX() + 7.5D, center.getY() + dy, center.getZ() + 7.5D);
                level.addFreshEntity(mob);
                mobs.add(mob);
            }
            var damage = StardewBombEntity.class.getDeclaredMethod("damageEntitiesInRadius",
                    net.minecraft.server.level.ServerLevel.class, int.class,
                    net.minecraft.world.entity.LivingEntity.class);
            damage.setAccessible(true);
            damage.invoke(bomb, level, BombType.MEGA_BOMB.getRadius(), null);
            helper.assertTrue(mobs.get(0).getHealth() >= 44F && mobs.get(0).getHealth() <= 58F, "Same-floor square corner missed or incorrect damage");
            helper.assertTrue(mobs.get(1).getHealth() == 100F, "Bomb hurt upper floor");
            helper.assertTrue(mobs.get(2).getHealth() == 100F, "Bomb hurt lower floor");
        } finally {
            mobs.forEach(net.minecraft.world.entity.Entity::discard);
        }
        helper.succeed();
    }

    private BombPlaneGameTests() {}
}

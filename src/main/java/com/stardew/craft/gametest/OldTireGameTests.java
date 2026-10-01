package com.stardew.craft.gametest;

import com.stardew.craft.port.PortGameTests;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.time.StardewTimeManager;
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
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("stardewcraft_old_tire")
@PrefixGameTestTemplate(false)
public final class OldTireGameTests {
    @GameTest(templateNamespace = "stardewcraft_old_tire", template = "tire_test")
    public static void placeRotateSeasonAndDropWithOpenCenter(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(10, 1, 10));
        for (var at : BlockPos.betweenClosed(pos.offset(-2, -1, -2), pos.offset(2, 2, 2)))
            level.setBlock(at, at.getY() == pos.getY() - 1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
        var block = ModBlocks.OLD_TIRE.get(); var player = PortGameTests.makeMockPlayer(h, GameType.SURVIVAL);
        player.setPos(Vec3.atCenterOf(pos.offset(3, 0, 3)));
        var time = StardewTimeManager.get(); int previous = time.getCurrentSeason();
        try {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                player.setYRot(facing.getOpposite().toYRot());
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(block, 3));
                var ctx = new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atBottomCenterOf(pos), Direction.UP, pos.below(), false)));
                h.assertTrue(((BlockItem) block.asItem()).place(ctx).consumesAction(), "Placement failed");
                var state = level.getBlockState(pos);
                h.assertTrue(state.getValue(MapDecorStaticBlock.FACING) == facing && ctx.getItemInHand().getCount() == 2, "Wrong facing or count");
                for (int season = 0; season < 4; season++) {
                    time.setCurrentSeason(season);
                    h.assertTrue(level.getBlockState(pos) == state, "Season replaced placed block");
                    var shape = state.getCollisionShape(level, pos);
                    h.assertTrue(Math.abs(shape.bounds().maxY - .25) < 1e-7, "Wrong tire height");
                    h.assertTrue(!Shapes.joinIsNotEmpty(shape, Shapes.box(.4, 0, .4, .6, .5, .6), BooleanOp.AND), "Center was filled");
                    h.assertTrue(level.getBlockState(pos.above()).isAir() && level.getBlockEntity(pos) == null, "Unexpected extra cell or entity");
                }
                for (var item : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3))) item.discard();
                level.destroyBlock(pos, true);
                var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3));
                h.assertTrue(drops.size() == 1 && drops.getFirst().getItem().is(block.asItem()) && drops.getFirst().getItem().getCount() == 1, "Expected one tire drop");
            }
            level.removeBlock(pos.below(), false);
            h.assertTrue(!block.defaultBlockState().canSurvive(level, pos), "Tire can float without ground support");
        } finally { time.setCurrentSeason(previous); }
        h.succeed();
    }
}

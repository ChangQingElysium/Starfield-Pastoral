package com.stardew.craft.gametest;

import com.stardew.craft.port.PortGameTests;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.DogHouseBlock;
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
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("stardewcraft_dog_house")
@PrefixGameTestTemplate(false)
public final class DogHouseGameTests {
    @GameTest(templateNamespace = "stardewcraft_dog_house", template = "dog_house_test")
    public static void localCollisionPlacementSeasonAndSingleDrop(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(10, 1, 10));
        for (var at : BlockPos.betweenClosed(pos.offset(-3, -1, -3), pos.offset(3, 2, 3)))
            level.setBlock(at, at.getY() == pos.getY()-1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
        var block = ModBlocks.DOG_HOUSE.get(); var player = PortGameTests.makeMockPlayer(h, GameType.SURVIVAL);
        player.setPos(Vec3.atCenterOf(pos.offset(4, 0, 4)));
        var time = StardewTimeManager.get(); int previous = time.getCurrentSeason();
        try {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                player.setYRot(facing.getOpposite().toYRot());
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(block, 5));
                var ctx = new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atBottomCenterOf(pos), Direction.UP, pos.below(), false)));
                BlockPos blocked = pos.offset(DogHouseBlock.rotateOffset(DogHouseBlock.localOffset(2), facing));
                level.setBlock(blocked, Blocks.STONE.defaultBlockState(), 3);
                h.assertTrue(!((BlockItem) block.asItem()).place(ctx).consumesAction(), "Occupied footprint accepted");
                h.assertTrue(level.getBlockState(pos).isAir() && ctx.getItemInHand().getCount() == 5, "Failed placement changed origin or consumed item");
                level.removeBlock(blocked, false);
                for (int removed = 0; removed < 3; removed++) {
                    int count = ctx.getItemInHand().getCount();
                    h.assertTrue(((BlockItem) block.asItem()).place(ctx).consumesAction(), "Placement failed: " + facing);
                    h.assertTrue(ctx.getItemInHand().getCount() == count-1, "Expected one item consumed");
                    double volume = 0;
                    for (int cell = 0; cell < 3; cell++) {
                        BlockPos target = pos.offset(DogHouseBlock.rotateOffset(DogHouseBlock.localOffset(cell), facing));
                        var state = level.getBlockState(target);
                        h.assertTrue(state.is(block) && state.getValue(DogHouseBlock.CELL) == cell
                                && state.getValue(MapDecorStaticBlock.FACING) == facing, "Wrong cell or orientation");
                        h.assertTrue(pos.equals(block.findMainPos(level, target, state)), "Wrong multipart owner");
                        var shape = state.getCollisionShape(level, target);
                        for (AABB box : shape.toAabbs()) {
                            h.assertTrue(box.minX >= 0 && box.minY >= 0 && box.minZ >= 0
                                    && box.maxX <= 1 && box.maxY <= 1 && box.maxZ <= 1, "Collision escaped local cell");
                            volume += box.getXsize() * box.getYsize() * box.getZsize();
                        }
                        if (cell == 0) h.assertTrue(shape.bounds().maxY == 3.0/16, "Air wall above bowl");
                        for (int season = 0; season < 4; season++) {
                            time.setCurrentSeason(season);
                            h.assertTrue(level.getBlockState(target) == state, "Season mutated block state");
                        }
                        h.assertTrue(level.getBlockState(target.above()).isAir(), "Unexpected upper extension");
                    }
                    h.assertTrue(Math.abs(volume - (16*16*20+8*3*8)/4096.0) < 1e-7, "Missing or overlapping collision volume");
                    BlockPos emptyCorner = pos.offset(DogHouseBlock.rotateOffset(new BlockPos(0, 0, 1), facing));
                    h.assertTrue(level.getBlockState(emptyCorner).isAir(), "Unused footprint corner occupied");
                    for (var item : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(4))) item.discard();
                    BlockPos target = pos.offset(DogHouseBlock.rotateOffset(DogHouseBlock.localOffset(removed), facing));
                    level.destroyBlock(target, true);
                    for (int cell = 0; cell < 3; cell++)
                        h.assertTrue(level.getBlockState(pos.offset(DogHouseBlock.rotateOffset(DogHouseBlock.localOffset(cell), facing))).isAir(), "Orphaned part");
                    var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(4));
                    h.assertTrue(drops.size() == 1 && drops.getFirst().getItem().is(block.asItem())
                            && drops.getFirst().getItem().getCount() == 1, "Expected exactly one doghouse drop");
                }
            }
        } finally { time.setCurrentSeason(previous); }
        h.succeed();
    }
}

package com.stardew.craft.gametest;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.CauldronModelMigration;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.blockentity.LuauFestivalDecorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("stardewcraft_cauldrons")
@PrefixGameTestTemplate(false)
public final class CauldronIntegrationGameTests {
    private static BlockPos prepare(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(8, 1, 8));
        for (var cell : BlockPos.betweenClosed(pos.offset(-4, 0, -4), pos.offset(4, 3, 4))) {
            helper.getLevel().setBlock(cell, Blocks.AIR.defaultBlockState(), 2);
        }
        return pos;
    }

    @GameTest(templateNamespace = "stardewcraft_cauldrons", template = "machine_test", timeoutTicks = 100)
    public static void fourFacingsUseOneAnimatedOwner(GameTestHelper helper) {
        var pos = prepare(helper);
        var level = helper.getLevel();
        for (var entry : java.util.List.of(ModBlocks.WIZARD_CAULDRON, ModBlocks.LUAU_SOUP_POT)) {
            var block = (MapDecorStaticBlock) entry.get();
            for (var facing : Direction.Plane.HORIZONTAL) {
                var state = block.defaultBlockState().setValue(MapDecorStaticBlock.FACING, facing);
                level.setBlock(pos, state, 2);
                helper.assertTrue(block.placeExtensions(level, pos, state), "Footprint placement failed: " + facing);
                helper.assertTrue(level.getBlockEntity(pos) instanceof LuauFestivalDecorBlockEntity, "Missing animated owner");
                for (var cell : BlockPos.betweenClosed(pos.offset(-4, 0, -4), pos.offset(4, 2, 4))) {
                    var part = level.getBlockState(cell);
                    if (!part.is(block) || cell.equals(pos)) continue;
                    helper.assertTrue(pos.equals(block.findMainPos(level, cell, part)), "Unreachable extension owner");
                    helper.assertTrue(level.getBlockEntity(cell) == null, "Duplicate animated entity");
                }
                level.destroyBlock(pos, false);
                for (var cell : BlockPos.betweenClosed(pos.offset(-4, 0, -4), pos.offset(4, 2, 4))) {
                    helper.assertTrue(!level.getBlockState(cell).is(block), "Removal left a cauldron fragment");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_cauldrons", template = "machine_test", timeoutTicks = 100)
    public static void stairsKeepTwoDistinctWalkableHeights(GameTestHelper helper) {
        var pos = prepare(helper);
        var state = ModBlocks.LUAU_SOUP_POT.get().defaultBlockState();
        helper.getLevel().setBlock(pos, state, 2);
        var shape = state.getCollisionShape(helper.getLevel(), pos);
        // Rays from above the two tread centers, in block-local coordinates.
        var first = shape.clip(new Vec3(.5, 3, -1.875).add(Vec3.atLowerCornerOf(pos)),
                new Vec3(.5, -1, -1.875).add(Vec3.atLowerCornerOf(pos)), pos);
        var second = shape.clip(new Vec3(.5, 3, -1.25).add(Vec3.atLowerCornerOf(pos)),
                new Vec3(.5, -1, -1.25).add(Vec3.atLowerCornerOf(pos)), pos);
        helper.assertTrue(first != null && Math.abs(first.getLocation().y - pos.getY() - .375) < .001, "Lower tread blocked by full-box collision");
        helper.assertTrue(second != null && Math.abs(second.getLocation().y - pos.getY() - .75) < .001, "Upper tread height changed");
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_cauldrons", template = "machine_test", timeoutTicks = 100)
    public static void oldStaticCauldronGainsEntityWithoutReplacingNeighbor(GameTestHelper helper) {
        var pos = prepare(helper);
        var level = helper.getLevel();
        var state = ModBlocks.WIZARD_CAULDRON.get().defaultBlockState();
        level.setBlock(pos, state, 2);
        level.removeBlockEntity(pos);
        var chunk = level.getChunkAt(pos);
        CauldronModelMigration.loaded(new ChunkEvent.Load(chunk, false));
        helper.assertTrue(chunk.getBlockEntities().get(pos) instanceof LuauFestivalDecorBlockEntity, "Old static cauldron has no entity");
        level.setBlock(pos.east(), Blocks.STONE.defaultBlockState(), 2);
        ((LuauFestivalDecorBlockEntity) level.getBlockEntity(pos)).repairCauldronFootprint();
        helper.assertTrue(level.getBlockState(pos.east()).is(Blocks.STONE), "Migration replaced an existing building");
        helper.succeed();
    }
}

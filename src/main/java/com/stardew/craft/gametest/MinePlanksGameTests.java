package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.mine.MinePlankConnections;
import com.stardew.craft.block.mine.MinePlanksBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import com.stardew.craft.port.net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestHolder(StardewCraft.MODID)
@PrefixGameTestTemplate(false)
public final class MinePlanksGameTests {
    private MinePlanksGameTests() {}

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities", timeoutTicks = 100)
    public static void minePlanksExtendOnlyOntoExposedSoilAndKeepVariants(GameTestHelper helper) {
        var level = helper.getLevel();
        var center = helper.absolutePos(new BlockPos(8, 2, 8));
        var wood = ModBlocks.MINE_PLANKS.get();
        int[][] offsets = {{0,-1},{1,0},{0,1},{-1,0},{1,-1},{1,1},{-1,1},{-1,-1}};
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            for (int y = -1; y <= 2; y++) level.setBlock(center.offset(x,y,z),
                    y == -1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
        }
        for (var soil : new net.minecraft.world.level.block.Block[]{ModBlocks.MINE_EARTH_SOIL.get(), ModBlocks.DIRT.get()}) {
            level.setBlock(center, soil.defaultBlockState(), 3);
            for (int i = 0; i < offsets.length; i++) {
                var neighbor = center.offset(offsets[i][0],0,offsets[i][1]);
                for (int variant = 0; variant < 2; variant++) {
                    level.setBlock(neighbor, wood.defaultBlockState().setValue(MinePlanksBlock.VARIANT, variant), 3);
                    helper.assertTrue(MinePlankConnections.mask(level, center, level.getBlockState(center)) == 1 << i,
                            "Wrong edge/corner direction or a variant fails to connect");
                    helper.assertTrue(MinePlankConnections.mask(level, neighbor, level.getBlockState(neighbor)) == 0,
                            "Soil must never extend over wood");
                    level.setBlock(neighbor.above(), Blocks.STONE.defaultBlockState(), 3);
                    helper.assertTrue(MinePlankConnections.mask(level, center, level.getBlockState(center)) == 0, "Covered wood leaks an edge");
                    level.setBlock(neighbor.above(), Blocks.AIR.defaultBlockState(), 3);
                }
                level.setBlock(neighbor, Blocks.AIR.defaultBlockState(), 3);
                helper.assertTrue(MinePlankConnections.mask(level, center, level.getBlockState(center)) == 0, "Removed wood leaves a stale edge");
            }
            level.setBlock(center.north(), wood.defaultBlockState(), 3);
            level.setBlock(center.east(), wood.defaultBlockState(), 3);
            level.setBlock(center.north().east(), wood.defaultBlockState(), 3);
            helper.assertTrue(MinePlankConnections.mask(level, center, level.getBlockState(center)) == 3, "Concave corner duplicates diagonal");
            for (var cover : new net.minecraft.world.level.block.Block[]{Blocks.STONE, Blocks.SNOW, Blocks.WATER}) {
                level.setBlock(center.above(), cover.defaultBlockState(), 3);
                helper.assertTrue(MinePlankConnections.mask(level, center, level.getBlockState(center)) == 0, "Covered soil receives overlay");
            }
            level.setBlock(center.above(), Blocks.AIR.defaultBlockState(), 3);
            for (int[] offset : offsets) level.setBlock(center.offset(offset[0],0,offset[1]), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(center.north().above(), wood.defaultBlockState(), 3);
            helper.assertTrue(MinePlankConnections.mask(level, center, level.getBlockState(center)) == 0, "Height difference must not connect");
            level.setBlock(center.north().above(), Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(level.getBlockState(center).equals(soil.defaultBlockState()), "Connection mutated soil identity/state");
        }
        var player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "Plank test"), ClientInformation.createDefault());
        player.getAbilities().instabuild = true;
        for (int variant = 0; variant < 2; variant++) {
            level.setBlock(center, Blocks.AIR.defaultBlockState(), 3);
            var expected = wood.defaultBlockState().setValue(MinePlanksBlock.VARIANT, variant);
            var stack = wood.getCloneItemStack(level, center, expected);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            var context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack,
                    new BlockHitResult(Vec3.atCenterOf(center), Direction.UP, center, false));
            helper.assertTrue(((BlockItem) stack.getItem()).place(context).consumesAction(), "Picked variant failed placement");
            var placed = level.getBlockState(center);
            helper.assertTrue(placed.equals(expected), "Middle-pick did not preserve the variant");
            helper.assertTrue(!Shapes.joinIsNotEmpty(placed.getCollisionShape(level, center), Shapes.block(), BooleanOp.NOT_SAME),
                    "Planks must remain a full cube");
        }
        helper.succeed();
    }
}

package com.stardew.craft.gametest;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.block.mine.MineRailBlock;
import com.stardew.craft.block.mine.MineRailCurveBlock;
import com.stardew.craft.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import com.stardew.craft.item.ModItems;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(StardewCraft.MODID)
@PrefixGameTestTemplate(false)
public final class MineRailCartGameTests {
    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities", timeoutTicks = 100)
    public static void mineBendImportAcceptsSectionsBeforeAnchor(GameTestHelper helper) {
        var level = helper.getLevel(); var block = ModBlocks.MINE_RAIL_CURVE.get();
        var pos = helper.absolutePos(new BlockPos(8,3,8));
        var main = block.defaultBlockState().setValue(MapDecorStaticBlock.FACING, Direction.SOUTH);
        for (int x = 1; x >= 0; x--) for (int z = 1; z >= 0; z--) {
            var cell = pos.offset(-x,0,-z);
            level.setBlock(cell.below(), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(cell, main.setValue(MineRailCurveBlock.SECTION, x*2+z)
                    .setValue(MapDecorStaticBlock.PART, x == 0 && z == 0
                            ? MapDecorStaticBlock.Part.MAIN : MapDecorStaticBlock.Part.EXTENSION), 3);
        }
        helper.runAfterDelay(5, () -> {
            for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++)
                helper.assertTrue(level.getBlockState(pos.offset(-x,0,-z)).is(block), "Imported bend section disappeared");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities", timeoutTicks = 100)
    public static void mineCoalCartClaimsOnceAndStaysFixed(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(8, 3, 8)));
        var cart = ModEntities.COAL_MINECART.get().create(level);
        cart.setYRot(90); cart.setPos(pos); level.addFreshEntity(cart);
        cart.push(1, 2, 3); cart.setDeltaMovement(1, 1, 1); cart.move(MoverType.PISTON, new Vec3(1, 0, 0)); cart.tick();
        helper.assertTrue(cart.position().equals(pos) && cart.getYRot() == 90, "Cart moved or rotated");
        var player = FakePlayerFactory.getMinecraft(level);
        cart.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(!cart.isLoaded(), "Coal cart did not become empty");
        cart.interact(player, InteractionHand.MAIN_HAND);
        var saved = new CompoundTag(); cart.save(saved);
        var restored = ModEntities.COAL_MINECART.get().create(level); restored.load(saved);
        helper.assertTrue(!restored.isLoaded() && restored.getYRot() == 90 && restored.position().equals(pos), "Save lost cart state");
        restored.interact(player, InteractionHand.MAIN_HAND);
        int coal = level.getEntitiesOfClass(ItemEntity.class, cart.getBoundingBox().inflate(3)).stream()
                .filter(e -> e.getItem().is(ModItems.COAL.get())).mapToInt(e -> e.getItem().getCount()).sum();
        helper.assertTrue(coal == 6, "Coal duplicated or default loot did not yield six: " + coal);
        cart.discard(); restored.discard(); helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities", timeoutTicks = 100)
    public static void mineBendPlacesAndCleansAllOrientations(GameTestHelper helper) {
        var level = helper.getLevel(); var block = ModBlocks.MINE_RAIL_CURVE.get();
        var pos = helper.absolutePos(new BlockPos(8, 3, 8));
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
                level.setBlock(pos.offset(x,-1,z), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(pos.offset(x,0,z), Blocks.AIR.defaultBlockState(), 3);
            }
            var state = block.defaultBlockState().setValue(MapDecorStaticBlock.FACING, facing);
            level.setBlock(pos, state, 3);
            helper.assertTrue(block.placeExtensions(level, pos, state), "Bend placement failed");
            int parts = 0, openings = 0; BlockPos extension = null;
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                var cell = pos.offset(x,0,z); var current = level.getBlockState(cell);
                if (!current.is(block)) continue;
                parts++;
                helper.assertTrue(pos.equals(block.findMainPos(level, cell, current)), "Bend section lost its anchor");
                helper.assertTrue(current.getCollisionShape(level, cell).isEmpty(), "Bend blocks walking");
                for (Direction edge : Direction.Plane.HORIZONTAL) if (MineRailCurveBlock.opens(current, edge)) openings++;
                if (!cell.equals(pos)) extension = cell;
            }
            helper.assertTrue(parts == 4 && openings == 2, "Bend footprint/openings invalid");
            MapDecorStaticBlock.runWithDropsSuppressed(() -> level.removeBlock(pos, false));
            helper.assertTrue(level.getBlockState(extension).isAir(), "Bend left an orphan section");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities", timeoutTicks = 100)
    public static void mineRailEndsMeetBendAndRecoverAfterRemoval(GameTestHelper helper) {
        var level = helper.getLevel(); var pos = helper.absolutePos(new BlockPos(8,3,8));
        for (int x = -2; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            level.setBlock(pos.offset(x,-1,z), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(pos.offset(x,0,z), Blocks.AIR.defaultBlockState(), 3);
        }
        var bend = ModBlocks.MINE_RAIL_CURVE.get(); var state = bend.defaultBlockState();
        level.setBlock(pos, state, 3); helper.assertTrue(bend.placeExtensions(level,pos,state), "Failed to place bend");
        level.setBlock(pos.north(), ModBlocks.MINE_RAIL.get().defaultBlockState(), 3);
        level.setBlock(pos.offset(2,0,1), ModBlocks.MINE_RAIL.get().defaultBlockState().setValue(MineRailBlock.SHAPE, RailShape.EAST_WEST), 3);
        helper.runAfterDelay(10, () -> {
            var north = level.getBlockState(pos.north()); var east = level.getBlockState(pos.offset(2,0,1));
            helper.assertTrue(!north.getValue(MineRailBlock.END_SOUTH) && north.getValue(MineRailBlock.END_NORTH), "North rail cap faces into bend");
            helper.assertTrue(!east.getValue(MineRailBlock.END_SOUTH) && east.getValue(MineRailBlock.END_NORTH), "East rail cap faces into bend");
            MapDecorStaticBlock.runWithDropsSuppressed(() -> level.removeBlock(pos, false));
        });
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(level.getBlockState(pos.north()).getValue(MineRailBlock.END_SOUTH), "Missing recovered end stop");
            helper.assertTrue(level.getBlockState(pos.offset(2,0,1)).getValue(MineRailBlock.END_SOUTH), "Missing recovered east end stop");
            helper.succeed();
        });
    }
}

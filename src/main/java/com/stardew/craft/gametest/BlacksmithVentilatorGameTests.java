package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.BlacksmithVentilatorBlock;
import com.stardew.craft.time.StardewTimeManager;
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
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("stardewcraft_blacksmith_ventilator")
@PrefixGameTestTemplate(false)
public final class BlacksmithVentilatorGameTests {
    private static FakePlayer prepare(GameTestHelper helper) {
        var level = helper.getLevel();
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 0; y <= 5; y++)
            level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), y == 0 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "VentilatorTest"));
        var away = helper.absolutePos(new BlockPos(20, 1, 20)); player.setPos(away.getX(), away.getY(), away.getZ()); return player;
    }
    private static BlockPlaceContext context(FakePlayer player, BlockPos pos) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModBlocks.BLACKSMITH_VENTILATOR.get(), 3));
        return new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0, .5, 0), Direction.UP, pos.below(), false)));
    }
    private static int cells(GameTestHelper h, BlockPos origin) {
        int count = 0; var block = ModBlocks.BLACKSMITH_VENTILATOR.get();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-6, 0, -6), origin.offset(6, 3, 6))) {
            var state = h.getLevel().getBlockState(pos);
            if (state.is(block) && origin.equals(block.findMainPos(h.getLevel(), pos, state))) count++;
        }
        return count;
    }
    @GameTest(batch = "blacksmith_ventilator", templateNamespace = "stardewcraft_blacksmith_ventilator", template = "ventilator_test", timeoutTicks = 200)
    public static void facingSeasonOwnershipClearanceAndOneDrop(GameTestHelper h) {
        var player = prepare(h); var level = h.getLevel(); var block = ModBlocks.BLACKSMITH_VENTILATOR.get();
        var origin = h.absolutePos(new BlockPos(8, 1, 8)); var time = StardewTimeManager.get(); int previous = time.getCurrentSeason();
        try {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                player.setYRot(facing.getOpposite().toYRot());
                BlockPos neighbor = origin.offset(BlacksmithVentilatorBlock.rotateOffset(new BlockPos(0, 0, 3), facing));
                var wall = origin.offset(BlacksmithVentilatorBlock.rotateOffset(new BlockPos(-5, 2, 0), facing));
                level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
                for (BlockPos main : new BlockPos[]{origin, neighbor}) {
                    var ctx = context(player, main);
                    h.assertTrue(((BlockItem) ctx.getItemInHand().getItem()).place(ctx).consumesAction(), "Placement failed " + facing);
                    h.assertTrue(cells(h, main) == 18 && ctx.getItemInHand().getCount() == 2, "Wrong footprint or item consumption");
                }
                var originalState = level.getBlockState(origin); var entity = level.getBlockEntity(origin);
                h.assertTrue(entity != null, "Missing main block entity");
                for (int season = 0; season < 4; season++) {
                    time.setCurrentSeason(season);
                    h.assertTrue(level.getBlockState(origin) == originalState && level.getBlockEntity(origin) == entity,
                            "Season replaced saved structure");
                    for (int cell = 0; cell < BlacksmithVentilatorBlock.cellCount(); cell++) {
                        BlockPos pos = origin.offset(BlacksmithVentilatorBlock.rotateOffset(BlacksmithVentilatorBlock.localOffset(cell), facing));
                        var state = level.getBlockState(pos);
                        h.assertTrue(state.getValue(BlacksmithVentilatorBlock.CELL) == cell && origin.equals(block.findMainPos(level, pos, state)), "Incorrect owner cell");
                        if (cell > 0) h.assertTrue(level.getBlockEntity(pos) == null, "Extension created duplicate renderer");
                        var shape = state.getCollisionShape(level, pos);
                        h.assertTrue(!shape.isEmpty(), "An occupied component has no collision");
                        var b = shape.bounds();
                        h.assertTrue(b.minX >= -1e-7 && b.minY >= -1e-7 && b.minZ >= -1e-7 && b.maxX <= 1.0000001 && b.maxY <= 1.0000001 && b.maxZ <= 1.0000001,
                                "Collision escaped its cell");
                    }
                    for (int x = -4; x < 0; x++) for (int y = 0; y < 2; y++) {
                        var air = origin.offset(BlacksmithVentilatorBlock.rotateOffset(new BlockPos(x, y, 0), facing));
                        h.assertTrue(level.getBlockState(air).isAir(), "Duct blocked the two-high passage");
                    }
                }
                for (var item : level.getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(8))) item.discard();
                var extension = origin.offset(BlacksmithVentilatorBlock.rotateOffset(new BlockPos(-4, 2, 0), facing));
                level.destroyBlock(extension, true);
                h.assertTrue(cells(h, origin) == 0 && level.getBlockEntity(origin) == null, "Removal left fragments");
                h.assertTrue(cells(h, neighbor) == 18 && level.getBlockState(wall).is(Blocks.STONE), "Removal damaged neighbor or attachment wall");
                var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(8));
                h.assertTrue(drops.size() == 1 && drops.getFirst().getItem().is(block.asItem()) && drops.getFirst().getItem().getCount() == 1, "Expected exactly one whole-machine drop");
                BlacksmithVentilatorBlock.runWithDropsSuppressed(() -> level.removeBlock(neighbor, false));
                level.removeBlock(wall, false);
            }
        } finally { time.setCurrentSeason(previous); }
        h.succeed();
    }
    @GameTest(batch = "blacksmith_ventilator", templateNamespace = "stardewcraft_blacksmith_ventilator", template = "ventilator_test")
    public static void obstructionSupportAndSpaceUnderDuct(GameTestHelper h) {
        var player = prepare(h); var level = h.getLevel(); var origin = h.absolutePos(new BlockPos(8, 1, 8));
        player.setYRot(Direction.SOUTH.toYRot());
        for (BlockPos local : new BlockPos[]{new BlockPos(-4, 2, 0), new BlockPos(2, 2, 1)}) {
            var obstruction = origin.offset(local); level.setBlock(obstruction, Blocks.STONE.defaultBlockState(), 3);
            var ctx = context(player, origin);
            h.assertTrue(!((BlockItem) ctx.getItemInHand().getItem()).place(ctx).consumesAction(), "Overwrote pipe/side-vent space");
            h.assertTrue(ctx.getItemInHand().getCount() == 3 && cells(h, origin) == 0, "Rejected placement changed world/item");
            level.removeBlock(obstruction, false);
        }
        var floor = origin.offset(1, -1, 1); level.removeBlock(floor, false);
        var ctx = context(player, origin);
        h.assertTrue(!((BlockItem) ctx.getItemInHand().getItem()).place(ctx).consumesAction(), "Placed unsupported machine");
        level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
        var under = origin.offset(-3, 0, 0); level.setBlock(under, Blocks.STONE.defaultBlockState(), 3);
        ctx = context(player, origin);
        h.assertTrue(((BlockItem) ctx.getItemInHand().getItem()).place(ctx).consumesAction() && level.getBlockState(under).is(Blocks.STONE),
                "Incorrectly reserved/overwrote clear space under duct");
        h.succeed();
    }
}

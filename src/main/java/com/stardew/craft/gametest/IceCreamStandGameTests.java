package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.IceCreamStandBlock;
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

@GameTestHolder("stardewcraft_ice_cream_stand")
@PrefixGameTestTemplate(false)
public final class IceCreamStandGameTests {
    private static FakePlayer prepare(GameTestHelper helper) {
        var level = helper.getLevel();
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 0; y <= 5; y++)
            level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), y == 0 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "IceCreamTest"));
        var away = helper.absolutePos(new BlockPos(20, 1, 20)); player.setPos(away.getX(), away.getY(), away.getZ()); return player;
    }
    private static BlockPlaceContext context(FakePlayer player, BlockPos pos) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModBlocks.ICE_CREAM_STAND.get(), 3));
        return new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0, .5, 0), Direction.UP, pos.below(), false)));
    }
    private static int cells(GameTestHelper h, BlockPos origin) {
        int count = 0; var block = ModBlocks.ICE_CREAM_STAND.get();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-4, 0, -4), origin.offset(4, 3, 4))) {
            var state = h.getLevel().getBlockState(pos);
            if (state.is(block) && origin.equals(block.findMainPos(h.getLevel(), pos, state))) count++;
        }
        return count;
    }
    @GameTest(batch = "ice_cream_stand", templateNamespace = "stardewcraft_ice_cream_stand", template = "ice_cream_test", timeoutTicks = 200)
    public static void seasonalIdentityFacingOwnershipAndDrop(GameTestHelper h) {
        var player = prepare(h); var level = h.getLevel(); var block = ModBlocks.ICE_CREAM_STAND.get();
        var origin = h.absolutePos(new BlockPos(8, 1, 8)); var time = StardewTimeManager.get(); int previous = time.getCurrentSeason();
        try {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                time.setCurrentSeason(0); player.setYRot(facing.getOpposite().toYRot());
                BlockPos neighbor = origin.offset(IceCreamStandBlock.rotateOffset(new BlockPos(4, 0, 0), facing));
                for (BlockPos main : new BlockPos[]{origin, neighbor}) {
                    var ctx = context(player, main);
                    h.assertTrue(((BlockItem) ctx.getItemInHand().getItem()).place(ctx).consumesAction(), "Placement failed " + facing);
                    h.assertTrue(cells(h, main) == 40 && ctx.getItemInHand().getCount() == 2, "Wrong footprint or consumption");
                }
                var originalState = level.getBlockState(origin); var entity = level.getBlockEntity(origin);
                h.assertTrue(entity != null, "Missing main block entity");
                for (int season = 0; season < 4; season++) {
                    time.setCurrentSeason(season);
                    h.assertTrue(level.getBlockState(origin) == originalState && level.getBlockEntity(origin) == entity && cells(h, origin) == 40,
                            "Season replaced the saved structure");
                    for (int cell = 0; cell < 48; cell++) {
                        if (!IceCreamStandBlock.ownsCell(cell)) continue;
                        BlockPos pos = origin.offset(IceCreamStandBlock.rotateOffset(IceCreamStandBlock.localOffset(cell), facing));
                        var state = level.getBlockState(pos);
                        h.assertTrue(state.getValue(IceCreamStandBlock.CELL) == cell && origin.equals(block.findMainPos(level, pos, state)), "Incorrect owner cell");
                        if (cell > 0) h.assertTrue(level.getBlockEntity(pos) == null, "Extension created another renderer");
                        var shape = state.getCollisionShape(level, pos);
                        if (!shape.isEmpty()) {
                            var b = shape.bounds();
                            h.assertTrue(b.minX >= -1e-7 && b.minY >= -1e-7 && b.minZ >= -1e-7 && b.maxX <= 1.0000001 && b.maxY <= 1.0000001 && b.maxZ <= 1.0000001,
                                    "Collision escaped its cell");
                        }
                    }
                    for (int y = 0; y < 2; y++) for (int z = 0; z < 2; z++) {
                        var aisle = origin.offset(IceCreamStandBlock.rotateOffset(new BlockPos(2, y, z), facing));
                        h.assertTrue(level.getBlockState(aisle).isAir(), "Vendor cell or rear entrance was filled");
                    }
                    BlockPos corner = origin.offset(IceCreamStandBlock.rotateOffset(new BlockPos(3, 1, 2), facing));
                    double top = level.getBlockState(corner).getCollisionShape(level, corner).bounds().maxY;
                    double expected = season == 3 ? 5.0 / 16 : season == 1 ? 4.0 / 16 : 2.0 / 16;
                    h.assertTrue(Math.abs(top - expected) < 1e-7, "Season collision does not match countertop/display height");
                }
                for (var item : level.getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(8))) item.discard();
                var extension = origin.offset(IceCreamStandBlock.rotateOffset(new BlockPos(1, 2, 1), facing));
                level.destroyBlock(extension, true);
                h.assertTrue(cells(h, origin) == 0 && level.getBlockEntity(origin) == null, "Removal left fragments");
                h.assertTrue(cells(h, neighbor) == 40, "Removal damaged the neighboring arrangement");
                var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(8));
                h.assertTrue(drops.size() == 1 && drops.getFirst().getItem().is(block.asItem()) && drops.getFirst().getItem().getCount() == 1, "Expected one complete display item");
                IceCreamStandBlock.runWithDropsSuppressed(() -> level.removeBlock(neighbor, false));
            }
        } finally { time.setCurrentSeason(previous); }
        h.succeed();
    }
    @GameTest(batch = "ice_cream_stand", templateNamespace = "stardewcraft_ice_cream_stand", template = "ice_cream_test")
    public static void requiresClearSeasonalEnvelopeAndSupportedBase(GameTestHelper h) {
        var player = prepare(h); var level = h.getLevel(); var origin = h.absolutePos(new BlockPos(8, 1, 8));
        player.setYRot(Direction.SOUTH.toYRot());
        // Even when the spring countertop leaves this cell visually empty, reserve room for the summer parasol.
        var obstruction = origin.offset(1, 3, 2); level.setBlock(obstruction, Blocks.STONE.defaultBlockState(), 3);
        var ctx = context(player, origin);
        h.assertTrue(!((BlockItem) ctx.getItemInHand().getItem()).place(ctx).consumesAction(), "Overwrote future seasonal space");
        h.assertTrue(ctx.getItemInHand().getCount() == 3 && cells(h, origin) == 0, "Rejected placement changed the world/item");
        level.setBlock(obstruction, Blocks.AIR.defaultBlockState(), 3);
        var floor = origin.offset(1, -1, 2); level.setBlock(floor, Blocks.AIR.defaultBlockState(), 3);
        ctx = context(player, origin);
        h.assertTrue(!((BlockItem) ctx.getItemInHand().getItem()).place(ctx).consumesAction(), "Placed without full base support");
        h.succeed();
    }
}

package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.block.decor.RuralFenceBlock;
import com.stardew.craft.item.catalog.StardewCatalogTab;
import com.stardew.craft.item.catalog.StardewItemCatalog;
import java.util.HashSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(StardewCraft.MODID)
@PrefixGameTestTemplate(false)
public final class RuralFenceGameTests {
    private RuralFenceGameTests() {}

    private static BlockPlaceContext context(ServerLevel level, FakePlayer player, BlockPos pos) {
        return context(level, player, pos, ModBlocks.RURAL_FENCE.get());
    }

    private static BlockPlaceContext context(ServerLevel level, FakePlayer player, BlockPos pos, RuralFenceBlock block) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(block));
        return new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0, .5, 0), Direction.UP, pos.below(), false)));
    }

    private static void clear(ServerLevel level, BlockPos pos) {
        MapDecorStaticBlock.runWithDropsSuppressed(() -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3));
    }

    private static void place(GameTestHelper helper, FakePlayer player, BlockPos pos) {
        var ctx = context(helper.getLevel(), player, pos);
        helper.assertTrue(((BlockItem) ctx.getItemInHand().getItem()).place(ctx).consumesAction(), "Fence placement failed");
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void fenceMaterialsConnectAndKeepTheirOwnItems(GameTestHelper helper) {
        var level = helper.getLevel(); var pos = helper.absolutePos(new BlockPos(8, 3, 8));
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "Fence materials"));
        var materials = new RuralFenceBlock[]{ModBlocks.RURAL_FENCE.get(), ModBlocks.WOOD_FENCE.get(), ModBlocks.HARDWOOD_FENCE.get()};
        for (var left : materials) for (var right : materials) {
            player.setGameMode(GameType.CREATIVE);
            for (int i = 0; i < 2; i++) {
                var at = pos.east(i); var block = i == 0 ? left : right;
                level.setBlock(at.below(), Blocks.STONE.defaultBlockState(), 3);
                var ctx = context(level, player, at, block);
                helper.assertTrue(((BlockItem) ctx.getItemInHand().getItem()).place(ctx).consumesAction(), "Material placement failed");
                var state = level.getBlockState(at); var upper = level.getBlockState(at.above());
                helper.assertTrue(state.is(block) && upper.is(block), "Material identity changed");
                helper.assertTrue(upper.getValue(RuralFenceBlock.VARIANT).equals(state.getValue(RuralFenceBlock.VARIANT)), "Upper grain mismatch");
                helper.assertTrue(state.is(BlockTags.MINEABLE_WITH_AXE), "Material missing axe tag");
                helper.assertTrue(StardewItemCatalog.tabForItem(block.asItem()) == StardewCatalogTab.BUILDING, "Material missing from building catalog");
            }
            helper.assertTrue(level.getBlockState(pos).getValue(RuralFenceBlock.CONNECTIONS.get(Direction.EAST))
                    && level.getBlockState(pos.east()).getValue(RuralFenceBlock.CONNECTIONS.get(Direction.WEST)), "Materials do not connect reciprocally");
            player.setGameMode(GameType.SURVIVAL);
            level.destroyBlock(pos.east().above(), true);
            helper.assertTrue(level.getBlockState(pos.east()).isAir(), "Material left lower cell behind");
            helper.assertTrue(!level.getBlockState(pos).getValue(RuralFenceBlock.CONNECTIONS.get(Direction.EAST)), "Material removal left rail behind");
            var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3));
            helper.assertTrue(drops.stream().mapToInt(e -> e.getItem().getCount()).sum() == 1
                    && drops.getFirst().getItem().is(right.asItem()), "Material dropped wrong or duplicate item");
            drops.forEach(Entity::discard);
            clear(level, pos);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void fencePlacementReservesUpperCellAndKeepsGrain(GameTestHelper helper) {
        var level = helper.getLevel(); var block = ModBlocks.RURAL_FENCE.get();
        var pos = helper.absolutePos(new BlockPos(8, 3, 8));
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "Fence placement"));
        player.setGameMode(GameType.CREATIVE);
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos.above(), Blocks.STONE.defaultBlockState(), 3);
        var blocked = context(level, player, pos);
        helper.assertTrue(!((BlockItem) blocked.getItemInHand().getItem()).place(blocked).consumesAction(), "Overwrote upper obstruction");
        helper.assertTrue(level.getBlockState(pos).isAir(), "Blocked placement left base behind");
        level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
        var seen = new HashSet<Integer>();
        for (int i = 0; i < 60; i++) {
            place(helper, player, pos);
            var state = level.getBlockState(pos); var upper = level.getBlockState(pos.above());
            int variant = state.getValue(RuralFenceBlock.VARIANT); seen.add(variant);
            helper.assertTrue(upper.is(block) && upper.getValue(MapDecorStaticBlock.PART) == MapDecorStaticBlock.Part.EXTENSION
                    && upper.getValue(RuralFenceBlock.VARIANT) == variant, "Upper cell lost grain or owner");
            var encoded = BlockState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();
            helper.assertTrue(BlockState.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow().equals(state), "State did not persist");
            var shape = state.getShape(level, pos);
            helper.assertTrue(Math.abs(shape.bounds().maxY - 31.0 / 16) < 1E-8, "Wrong fence height");
            helper.assertTrue(!Shapes.joinIsNotEmpty(shape, upper.getShape(level, pos.above()).move(0, 1, 0), BooleanOp.NOT_SAME), "Upper outline diverges");
            helper.assertTrue(!state.isPathfindable(level, pos, PathComputationType.LAND), "AI can walk through fence");
            clear(level, pos);
            helper.assertTrue(level.getBlockState(pos.above()).isAir(), "Upper cell was orphaned");
        }
        helper.assertTrue(seen.size() == 3, "Placement did not use all three grain patterns");
        helper.assertTrue(block.defaultBlockState().is(BlockTags.MINEABLE_WITH_AXE), "Missing axe tag");
        helper.assertTrue(StardewItemCatalog.tabForItem(block.asItem()) == StardewCatalogTab.BUILDING, "Missing building catalog entry");
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void fenceAllConnectionsRotateAndRemoveWithoutChangingGrain(GameTestHelper helper) {
        var level = helper.getLevel(); var block = ModBlocks.RURAL_FENCE.get();
        var pos = helper.absolutePos(new BlockPos(8, 3, 8));
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "Fence connections"));
        player.setGameMode(GameType.CREATIVE);
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        place(helper, player, pos);
        int originalGrain = level.getBlockState(pos).getValue(RuralFenceBlock.VARIANT);
        for (int mask = 0; mask < 16; mask++) {
            for (Direction d : Direction.Plane.HORIZONTAL) clear(level, pos.relative(d));
            for (Direction d : Direction.Plane.HORIZONTAL) if ((mask & (1 << d.get2DDataValue())) != 0) {
                level.setBlock(pos.relative(d).below(), Blocks.STONE.defaultBlockState(), 3);
                place(helper, player, pos.relative(d));
            }
            var state = level.getBlockState(pos);
            helper.assertTrue(state.getValue(RuralFenceBlock.VARIANT) == originalGrain, "Neighbor change rerolled grain");
            for (Direction d : Direction.Plane.HORIZONTAL) {
                boolean expected = (mask & (1 << d.get2DDataValue())) != 0;
                helper.assertTrue(state.getValue(RuralFenceBlock.CONNECTIONS.get(d)) == expected, "Wrong connection mask " + mask);
                if (expected) helper.assertTrue(level.getBlockState(pos.relative(d)).getValue(RuralFenceBlock.CONNECTIONS.get(d.getOpposite())), "One-way connection");
                var rotated = state.rotate(Rotation.CLOCKWISE_90);
                helper.assertTrue(rotated.getValue(RuralFenceBlock.CONNECTIONS.get(d.getClockWise())) == expected, "Rotation lost rail");
                var probe = Shapes.box(d == Direction.WEST ? 0 : d == Direction.EAST ? .9 : .45, .6,
                        d == Direction.NORTH ? 0 : d == Direction.SOUTH ? .9 : .45,
                        d == Direction.WEST ? .1 : d == Direction.EAST ? 1 : .55, .7,
                        d == Direction.NORTH ? .1 : d == Direction.SOUTH ? 1 : .55);
                helper.assertTrue(Shapes.joinIsNotEmpty(state.getCollisionShape(level, pos), probe, BooleanOp.AND) == expected, "Collision disagrees with rail mask");
            }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void fenceEitherCellDropsOnceAndCreativeDropsNothing(GameTestHelper helper) {
        var level = helper.getLevel(); var block = ModBlocks.RURAL_FENCE.get();
        var pos = helper.absolutePos(new BlockPos(8, 3, 8));
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "Fence drops"));
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        for (boolean creative : new boolean[]{false, true}) for (boolean upper : new boolean[]{false, true}) {
            player.setGameMode(GameType.CREATIVE); place(helper, player, pos);
            player.setGameMode(creative ? GameType.CREATIVE : GameType.SURVIVAL);
            BlockPos target = upper ? pos.above() : pos;
            if (creative) {
                block.playerWillDestroy(level, target, level.getBlockState(target), player);
                level.removeBlock(target, false);
            } else level.destroyBlock(target, true);
            helper.assertTrue(level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir(), "Removal orphaned half");
            var items = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3), e -> e.getItem().is(block.asItem()));
            helper.assertTrue(items.stream().mapToInt(e -> e.getItem().getCount()).sum() == (creative ? 0 : 1), "Duplicate or missing fence drop");
            items.forEach(Entity::discard);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void fenceTracksUpperWallChanges(GameTestHelper helper) {
        var level = helper.getLevel(); var pos = helper.absolutePos(new BlockPos(8, 3, 8));
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "Fence wall"));
        player.setGameMode(GameType.CREATIVE);
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3); place(helper, player, pos);
        level.setBlock(pos.east(), Blocks.STONE.defaultBlockState(), 3);
        helper.assertTrue(!level.getBlockState(pos).getValue(RuralFenceBlock.CONNECTIONS.get(Direction.EAST)), "Connected to wall below upper rail");
        level.setBlock(pos.east().above(), Blocks.STONE.defaultBlockState(), 3);
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(level.getBlockState(pos).getValue(RuralFenceBlock.CONNECTIONS.get(Direction.EAST)), "Upper wall placement did not connect");
            level.setBlock(pos.east().above(), Blocks.AIR.defaultBlockState(), 3);
        });
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(!level.getBlockState(pos).getValue(RuralFenceBlock.CONNECTIONS.get(Direction.EAST)), "Removed upper wall left rail behind");
            helper.succeed();
        });
    }
}

package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.ParkedVehicleBlock;
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

@GameTestHolder("stardewcraft_vehicles")
@PrefixGameTestTemplate(false)
public final class ParkedVehicleGameTests {
    private static FakePlayer prepare(GameTestHelper helper) {
        var level = helper.getLevel();
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 0; y <= 5; y++)
            level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), y == 0 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "VehicleTest"));
        var away = helper.absolutePos(new BlockPos(20, 1, 20)); player.setPos(away.getX(), away.getY(), away.getZ()); return player;
    }
    private static BlockPlaceContext context(FakePlayer player, ParkedVehicleBlock block, BlockPos pos) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(block, 3));
        return new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0, .5, 0), Direction.UP, pos.below(), false)));
    }
    private static int cells(GameTestHelper helper, ParkedVehicleBlock block, BlockPos origin) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-5, 0, -5), origin.offset(5, 3, 5))) {
            var state = helper.getLevel().getBlockState(pos);
            if (state.is(block) && origin.equals(block.findMainPos(helper.getLevel(), pos, state))) count++;
        }
        return count;
    }
    @GameTest(templateNamespace = "stardewcraft_vehicles", template = "vehicle_test", timeoutTicks = 200)
    public static void placementFacingCollisionAndSingleDrop(GameTestHelper helper) {
        var player = prepare(helper); var level = helper.getLevel(); var origin = helper.absolutePos(new BlockPos(8, 1, 8));
        for (var holder : java.util.List.of(ModBlocks.BUS, ModBlocks.MAYOR_PICKUP, ModBlocks.JOJA_TRUCK)) {
            var block = holder.get(); int expected = block.isBus() ? 108 : block.isJojaTruck() ? 160 : 54;
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                player.setYRot(facing.getOpposite().toYRot());
                BlockPos neighbor = origin.offset(ParkedVehicleBlock.rotateOffset(new BlockPos(block.isJojaTruck() ? 5 : 3, 0, 0), facing));
                for (BlockPos main : new BlockPos[]{origin, neighbor}) {
                    var ctx = context(player, block, main);
                    helper.assertTrue(((BlockItem) ctx.getItemInHand().getItem()).place(ctx).consumesAction(), "Vehicle placement failed: " + block.assetId() + facing);
                    helper.assertTrue(cells(helper, block, main) == expected, "Incomplete vehicle envelope");
                    helper.assertTrue(level.getBlockEntity(main) != null, "Missing main renderer");
                    for (BlockPos cell : BlockPos.betweenClosed(main.offset(-5, 0, -5), main.offset(5, 3, 5))) {
                        var state = level.getBlockState(cell);
                        if (!state.is(block) || !main.equals(block.findMainPos(level, cell, state))) continue;
                        if (!cell.equals(main)) helper.assertTrue(level.getBlockEntity(cell) == null, "Extra block entity");
                        var shape = state.getCollisionShape(level, cell);
                        if (!shape.isEmpty()) {
                            var b = shape.bounds();
                            helper.assertTrue(b.minX >= -1e-7 && b.minY >= -1e-7 && b.minZ >= -1e-7 && b.maxX <= 1.0000001 && b.maxY <= 1.0000001 && b.maxZ <= 1.0000001, "Collision leaked into another cell");
                        }
                    }
                }
                for (var item : level.getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(12))) item.discard();
                var extension = origin.offset(ParkedVehicleBlock.rotateOffset(new BlockPos(1, 0, 0), facing));
                level.destroyBlock(extension, true);
                helper.assertTrue(cells(helper, block, origin) == 0 && level.getBlockEntity(origin) == null, "Removal left vehicle fragments");
                helper.assertTrue(cells(helper, block, neighbor) == expected, "Removal damaged adjacent vehicle");
                var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(12));
                helper.assertTrue(drops.stream().mapToInt(e -> e.getItem().getCount()).sum() == 1 && com.stardew.craft.port.PortJava.getFirst(drops).getItem().is(block.asItem()), "Expected one whole vehicle item");
                ParkedVehicleBlock.runWithDropsSuppressed(() -> level.removeBlock(neighbor, false));
            }
        }
        helper.succeed();
    }
    @GameTest(templateNamespace = "stardewcraft_vehicles", template = "vehicle_test")
    public static void rejectsObstructionsAndMissingSupport(GameTestHelper helper) {
        var player = prepare(helper); var level = helper.getLevel(); var origin = helper.absolutePos(new BlockPos(8, 1, 8));
        player.setYRot(Direction.SOUTH.toYRot());
        for (var holder : java.util.List.of(ModBlocks.BUS, ModBlocks.MAYOR_PICKUP, ModBlocks.JOJA_TRUCK)) {
            var block = holder.get(); var obstruction = origin.offset(0, block.isBus() || block.isJojaTruck() ? 3 : 2, 1);
            level.setBlock(obstruction, Blocks.STONE.defaultBlockState(), 3);
            var ctx = context(player, block, origin);
            helper.assertTrue(!((BlockItem) ctx.getItemInHand().getItem()).place(ctx).consumesAction(), "Overwrote obstruction");
            helper.assertTrue(ctx.getItemInHand().getCount() == 3 && cells(helper, block, origin) == 0, "Rejected placement consumed item/left cells");
            level.setBlock(obstruction, Blocks.AIR.defaultBlockState(), 3);
            var floor = origin.offset(1, -1, block.isBus() || block.isJojaTruck() ? -2 : -1); level.setBlock(floor, Blocks.AIR.defaultBlockState(), 3);
            ctx = context(player, block, origin);
            helper.assertTrue(!((BlockItem) ctx.getItemInHand().getItem()).place(ctx).consumesAction(), "Placed with unsupported axle");
            level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
        }
        helper.succeed();
    }
    private static com.google.gson.JsonObject json(String path) throws Exception {
        try (var stream = ParkedVehicleGameTests.class.getResourceAsStream("/assets/stardewcraft/" + path)) {
            if (stream == null) throw new IllegalStateException("Missing " + path);
            return com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    @GameTest(templateNamespace = "stardewcraft_vehicles", template = "vehicle_test")
    public static void seasonalAssembliesRetainGlassWipersAndInvertedLamps(GameTestHelper helper) throws Exception {
        for (String vehicle : new String[]{"bus", "mayor_pickup", "joja_truck"}) for (String season : vehicle.equals("bus") ? new String[]{"spring"} : new String[]{"spring", "summer", "fall", "winter"}) {
            int hulls = 0, rotations = 0; boolean glass = false, snow = false;
            for (var entry : json("vehicles/" + vehicle + "/" + season + ".json").getAsJsonArray("parts")) {
                var part = entry.getAsJsonObject(); String material = part.get("material").getAsString();
                glass |= material.equals("glass"); snow |= material.equals("snow");
                var model = json("models/" + part.get("model").getAsString().split(":")[1] + ".json");
                String particle = model.getAsJsonObject("textures").get("particle").getAsString().split(":")[1];
                try (var stream = ParkedVehicleGameTests.class.getResourceAsStream("/assets/stardewcraft/textures/" + particle + ".png")) {
                    helper.assertTrue(stream != null, "Missing material particle");
                }
                for (var value : model.getAsJsonArray("elements")) {
                    var e = value.getAsJsonObject(); if (e.has("rotation")) rotations++;
                    var a = e.getAsJsonArray("from"); var b = e.getAsJsonArray("to");
                    if (a.get(0).getAsDouble() > b.get(0).getAsDouble()) {
                        hulls++; helper.assertTrue((material.equals("outline") || material.equals("tail_outline")) && a.get(1).getAsDouble() > b.get(1).getAsDouble() && a.get(2).getAsDouble() > b.get(2).getAsDouble(), "Inverted hull was normalized");
                    }
                }
            }
            helper.assertTrue(hulls == (vehicle.equals("joja_truck") ? 4 : 2) && rotations == 4 && glass, "Lost lamp shells, wiper rotations or translucent glass");
            helper.assertTrue(snow == (!vehicle.equals("bus") && season.equals("winter")), "Wrong seasonal snow geometry");
        }
        helper.succeed();
    }
}

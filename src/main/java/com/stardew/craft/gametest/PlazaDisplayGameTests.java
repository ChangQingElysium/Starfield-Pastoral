package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.PlazaDisplayBlock;
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

@GameTestHolder("stardewcraft_plaza")
@PrefixGameTestTemplate(false)
public final class PlazaDisplayGameTests {
    private static FakePlayer prepare(GameTestHelper helper) {
        var level = helper.getLevel();
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 0; y <= 5; y++)
            level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), y == 0 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "PlazaTest"));
        var away = helper.absolutePos(new BlockPos(20, 1, 20)); player.setPos(away.getX(), away.getY(), away.getZ()); return player;
    }
    private static BlockPlaceContext context(FakePlayer player, BlockPos pos) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModBlocks.PLAZA_DISPLAY.get(), 3));
        return new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0, .5, 0), Direction.UP, pos.below(), false)));
    }
    private static int cells(GameTestHelper h, BlockPos origin) {
        int count = 0; var block = ModBlocks.PLAZA_DISPLAY.get();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-3, 0, -3), origin.offset(3, 3, 3))) {
            var state = h.getLevel().getBlockState(pos);
            if (state.is(block) && origin.equals(block.findMainPos(h.getLevel(), pos, state))) count++;
        }
        return count;
    }
    @GameTest(batch = "plaza_display", templateNamespace = "stardewcraft_plaza", template = "plaza_test", timeoutTicks = 200)
    public static void seasonalIdentityFacingOwnershipAndDrop(GameTestHelper h) {
        var player = prepare(h); var level = h.getLevel(); var block = ModBlocks.PLAZA_DISPLAY.get();
        var origin = h.absolutePos(new BlockPos(8, 1, 8)); var time = StardewTimeManager.get(); int previous = time.getCurrentSeason();
        try {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                time.setCurrentSeason(0); player.setYRot(facing.getOpposite().toYRot());
                BlockPos neighbor = origin.offset(PlazaDisplayBlock.rotateOffset(new BlockPos(2, 0, 0), facing));
                for (BlockPos main : new BlockPos[]{origin, neighbor}) {
                    var ctx = context(player, main);
                    h.assertTrue(((BlockItem) ctx.getItemInHand().getItem()).place(ctx).consumesAction(), "Placement failed " + facing);
                    h.assertTrue(cells(h, main) == 24 && ctx.getItemInHand().getCount() == 2, "Wrong footprint or consumption");
                }
                var originalState = level.getBlockState(origin); var entity = level.getBlockEntity(origin);
                h.assertTrue(entity != null, "Missing main block entity");
                for (int season = 0; season < 4; season++) {
                    time.setCurrentSeason(season);
                    h.assertTrue(level.getBlockState(origin) == originalState && level.getBlockEntity(origin) == entity && cells(h, origin) == 24,
                            "Season replaced the saved structure");
                    for (int cell = 0; cell < 24; cell++) {
                        BlockPos pos = origin.offset(PlazaDisplayBlock.rotateOffset(PlazaDisplayBlock.localOffset(cell), facing));
                        var state = level.getBlockState(pos);
                        h.assertTrue(state.getValue(PlazaDisplayBlock.CELL) == cell && origin.equals(block.findMainPos(level, pos, state)), "Incorrect owner cell");
                        if (cell > 0) h.assertTrue(level.getBlockEntity(pos) == null, "Extension created another renderer");
                        var shape = state.getCollisionShape(level, pos);
                        if (!shape.isEmpty()) {
                            var b = shape.bounds();
                            h.assertTrue(b.minX >= -1e-7 && b.minY >= -1e-7 && b.minZ >= -1e-7 && b.maxX <= 1.0000001 && b.maxY <= 1.0000001 && b.maxZ <= 1.0000001,
                                    "Collision escaped its cell");
                        }
                    }
                    double top = level.getBlockState(origin).getCollisionShape(level, origin).bounds().maxY;
                    h.assertTrue(Math.abs(top - (season == 1 ? .75 : 1)) < 1e-7, "Season did not select the matching pot/tree collision");
                }
                for (var item : level.getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(8))) item.discard();
                var extension = origin.offset(PlazaDisplayBlock.rotateOffset(new BlockPos(1, 2, 1), facing));
                level.destroyBlock(extension, true);
                h.assertTrue(cells(h, origin) == 0 && level.getBlockEntity(origin) == null, "Removal left fragments");
                h.assertTrue(cells(h, neighbor) == 24, "Removal damaged the neighboring arrangement");
                var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(8));
                h.assertTrue(drops.size() == 1 && drops.getFirst().getItem().is(block.asItem()) && drops.getFirst().getItem().getCount() == 1, "Expected one complete display item");
                PlazaDisplayBlock.runWithDropsSuppressed(() -> level.removeBlock(neighbor, false));
            }
        } finally { time.setCurrentSeason(previous); }
        h.succeed();
    }
    @GameTest(batch = "plaza_display", templateNamespace = "stardewcraft_plaza", template = "plaza_test")
    public static void requiresClearSeasonalEnvelopeAndSupportedBase(GameTestHelper h) {
        var player = prepare(h); var level = h.getLevel(); var origin = h.absolutePos(new BlockPos(8, 1, 8));
        player.setYRot(Direction.SOUTH.toYRot());
        // Even when the spring flowers leave this cell visually empty, reserve room for winter.
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
    private static com.google.gson.JsonObject json(String path) throws Exception {
        try (var stream = PlazaDisplayGameTests.class.getResourceAsStream("/assets/stardewcraft/" + path)) {
            if (stream == null) throw new IllegalStateException("Missing " + path);
            return com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    @GameTest(batch = "plaza_display", templateNamespace = "stardewcraft_plaza", template = "plaza_test")
    public static void allFourAssembliesHaveNativeModelsAndMaterialParticles(GameTestHelper h) throws Exception {
        var expected = java.util.Map.of("spring", 36, "summer", 36, "fall", 32, "winter", 118);
        for (String season : expected.keySet()) {
            var parts = json("plaza_display/" + season + ".json").getAsJsonArray("parts");
            h.assertTrue(parts.size() == expected.get(season), "Incomplete seasonal model");
            int invertedParts = 0;
            for (var entry : parts) {
                var part = entry.getAsJsonObject(); String path = part.get("model").getAsString().split(":")[1];
                var model = json("models/" + path + ".json");
                h.assertTrue(!model.has("loader") && model.get("render_type").getAsString().equals("minecraft:cutout"), "Changed native/cutout format");
                for (var texture : model.getAsJsonObject("textures").entrySet()) {
                    String png = texture.getValue().getAsString().split(":")[1];
                    try (var stream = PlazaDisplayGameTests.class.getResourceAsStream("/assets/stardewcraft/textures/" + png + ".png")) {
                        h.assertTrue(stream != null, "Missing material texture");
                    }
                }
                for (var value : model.getAsJsonArray("elements")) for (String bound : new String[]{"from", "to"})
                    for (var coordinate : value.getAsJsonObject().getAsJsonArray(bound)) h.assertTrue(coordinate.getAsDouble() >= -16 && coordinate.getAsDouble() <= 32, "Invalid native model bounds");
                for (var value : model.getAsJsonArray("elements")) {
                    var element = value.getAsJsonObject();
                    int reversedAxes = 0;
                    for (int axis = 0; axis < 3; axis++)
                        if (element.getAsJsonArray("from").get(axis).getAsDouble() > element.getAsJsonArray("to").get(axis).getAsDouble()) reversedAxes++;
                    h.assertTrue(reversedAxes == 0 || reversedAxes == 3, "Partially reversed outline winding");
                    if (reversedAxes == 3) invertedParts++;
                }
            }
            h.assertTrue(invertedParts == (season.equals("winter") ? 45 : 0), "Winter ornament outline winding was lost during export");
        }
        h.succeed();
    }
}

package com.stardew.craft.gametest;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.FertilizerType;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.manager.FertilizerManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(StardewCraft.MODID)
@PrefixGameTestTemplate(false)
public final class FertilizedSoilGameTests {
    private FertilizedSoilGameTests() {}

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void everyFertilizerSurvivesWateringAndSaveWithoutAnOverlayBlock(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(6, 1, 6));
        var manager = new FertilizerManager();
        for (Block soil : new Block[]{ModBlocks.FARMLAND.get(), Blocks.FARMLAND, ModBlocks.GARDEN_POT.get()}) {
            level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
            for (FertilizerType type : FertilizerType.values()) {
                var dry = soil.defaultBlockState().setValue(FarmBlock.MOISTURE, 0);
                level.setBlock(pos, dry, 3);
                helper.assertTrue(manager.tryApplyFertilizer(level, pos, type), "Application rejected " + type);
                helper.assertTrue(level.getBlockState(pos).equals(dry) && level.getBlockState(pos.above()).isAir(),
                        "Applying fertilizer changed soil identity or occupied the crop cell");
                helper.assertTrue(!manager.tryApplyFertilizer(level, pos, type), "Applied fertilizer twice");
                level.setBlock(pos, dry.setValue(FarmBlock.MOISTURE, 7), 3);
                var restored = FertilizerManager.load(manager.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
                helper.assertTrue(restored.getFertilizer(level, pos) == type, "Watering/save lost fertilizer " + type);
                helper.assertTrue(restored.removeFertilizer(level, pos) && restored.getFertilizer(level, pos) == null, "Removal left fertilizer data");
                manager.removeFertilizer(level, pos);
            }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void packagedFertilizersMatchTypesAndPreserveTerrainSeams(GameTestHelper helper) throws java.io.IOException {
        String assets = "/assets/stardewcraft/";
        try (var stream = FertilizedSoilGameTests.class.getResourceAsStream(assets + "fertilized_farmland_manifest.json")) {
            helper.assertTrue(stream != null, "Missing fertilizer manifest");
            var manifest = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var names = manifest.getAsJsonArray("names");
            helper.assertTrue(names.size() == FertilizerType.values().length, "Fertilizer texture count differs from gameplay types");
            for (FertilizerType type : FertilizerType.values()) helper.assertTrue(names.get(type.ordinal()).getAsString().equals(type.getSerializedName()), "Wrong texture selected for " + type);
        }
        for (String season : new String[]{"spring", "summer", "fall", "winter"}) {
            var atlas = readImage(assets + "textures/block/terrain/fertilized/" + season + ".png");
            helper.assertTrue(atlas.getWidth() == 144 && atlas.getHeight() == 320, "Wrong fertilizer atlas dimensions");
            for (boolean wet : new boolean[]{false,true}) {
                String state = wet ? "wet" : "dry";
                var base = readImage(assets + "textures/block/terrain/farmland/" + season + "/" + state + ".png");
                for (FertilizerType type : FertilizerType.values()) {
                    var painted = readImage(assets + "textures/block/terrain/fertilized/" + season + "/" + type.getSerializedName() + "_" + state + ".png");
                    int changed = 0;
                    for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                        int pixel = painted.getRGB(x, y);
                        if (x < 2 || x >= 14 || y < 2 || y >= 14)
                            helper.assertTrue(pixel == base.getRGB(x, y), "Fertilizer crossed a tiling seam");
                        if (pixel != base.getRGB(x, y)) changed++;
                        helper.assertTrue(pixel >>> 24 == 255, "Transparent fertilizer surface");
                        helper.assertTrue(pixel == atlas.getRGB(type.ordinal() * 16 + x, (wet ? 256 : 0) + y), "Wrong atlas wet/dry mapping");
                    }
                    helper.assertTrue(changed > 0 && changed < 64, "Missing fertilizer or excessive coverage");
                }
            }
        }
        helper.succeed();
    }

    private static java.awt.image.BufferedImage readImage(String path) throws java.io.IOException {
        try (var stream = FertilizedSoilGameTests.class.getResourceAsStream(path)) {
            if (stream == null) throw new java.io.IOException("Missing packaged resource " + path);
            return javax.imageio.ImageIO.read(stream);
        }
    }
}

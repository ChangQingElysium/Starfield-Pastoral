package com.stardew.craft.gametest;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.world.StardewRegion;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.manager.ArtifactSpotCandidatePool;
import com.stardew.craft.manager.ArtifactSpotSpawnService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.List;

@GameTestHolder(StardewCraft.MODID)
@PrefixGameTestTemplate(false)
public final class ArtifactCandidateGameTests {
    private static StardewRegion.Box grassPatch(GameTestHelper helper) {
        var min = helper.absolutePos(new BlockPos(2, 1, 2));
        var max = min.offset(5, 0, 5);
        for (var p : BlockPos.betweenClosed(min, max)) {
            helper.getLevel().setBlock(p, ModBlocks.GRASS_BLOCK.get().defaultBlockState(), 3);
            helper.getLevel().setBlock(p.above(), Blocks.AIR.defaultBlockState(), 3);
        }
        return new StardewRegion.Box(min, max);
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void isolatedSoilAlwaysGetsFoundWithoutSpawningOnGrass(GameTestHelper helper) {
        var box = grassPatch(helper);
        var ground = box.min().offset(3, 0, 4);
        helper.getLevel().setBlock(ground, ModBlocks.DIRT.get().defaultBlockState(), 3);
        for (int seed = 0; seed < 100; seed++) {
            var pool = new ArtifactSpotCandidatePool(List.of(box), RandomSource.create(seed));
            var marker = pool.next(helper.getLevel(), box::contains);
            helper.assertTrue(ground.above().equals(marker), "A valid isolated soil patch was missed: seed=" + seed);
            helper.assertTrue(ArtifactSpotSpawnService.place(helper.getLevel(), marker, false), "Selected soil was not placeable");
            helper.assertTrue(pool.next(helper.getLevel(), box::contains) == null, "Grass or occupied marker was selected");
            helper.assertTrue(pool.inspected() == 36, "Search repeated or skipped a column");
            helper.getLevel().removeBlock(marker, false);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void noGroundTerminatesAndLaterTerrainEditsAreRespected(GameTestHelper helper) {
        var box = grassPatch(helper);
        var pool = new ArtifactSpotCandidatePool(List.of(box), RandomSource.create(53));
        helper.assertTrue(pool.next(helper.getLevel(), box::contains) == null, "All-grass region spawned a marker");
        helper.assertTrue(pool.inspected() == 36, "Empty region did not finish a bounded search");
        helper.assertTrue(pool.next(helper.getLevel(), box::contains) == null && pool.inspected() == 36, "Exhausted region was scanned again");
        helper.getLevel().setBlock(box.min(), Blocks.SAND.defaultBlockState(), 3);
        var nextPass = new ArtifactSpotCandidatePool(List.of(box), RandomSource.create(54));
        helper.assertTrue(box.min().above().equals(nextPass.next(helper.getLevel(), box::contains)), "New sand was hidden by a stale candidate cache");
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void exclusionsAndOccupiedGroundRemainUnavailable(GameTestHelper helper) {
        var box = grassPatch(helper);
        var excluded = box.min();
        var occupied = box.max();
        var allowed = box.min().offset(2, 0, 2);
        for (var p : List.of(excluded, occupied, allowed)) helper.getLevel().setBlock(p, ModBlocks.DIRT.get().defaultBlockState(), 3);
        helper.getLevel().setBlock(occupied.above(), Blocks.CHEST.defaultBlockState(), 3);
        var pool = new ArtifactSpotCandidatePool(List.of(box), RandomSource.create(97));
        var marker = pool.next(helper.getLevel(), p -> box.contains(p) && !p.equals(excluded));
        helper.assertTrue(allowed.above().equals(marker), "Candidate search bypassed region exclusion or occupancy");
        helper.assertTrue(ArtifactSpotSpawnService.place(helper.getLevel(), marker, true), "Could not place seed marker");
        helper.assertTrue(pool.next(helper.getLevel(), p -> box.contains(p) && !p.equals(excluded)) == null, "Invalid remaining candidate was accepted");
        helper.succeed();
    }
}

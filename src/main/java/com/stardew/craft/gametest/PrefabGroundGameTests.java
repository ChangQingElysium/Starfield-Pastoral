package com.stardew.craft.gametest;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.building.runtime.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/** Targeted regression suite: embedded ground is support, not building contents. */
@GameTestHolder("stardewcraft_prefab_ground")
@PrefixGameTestTemplate(false)
public final class PrefabGroundGameTests {
    @GameTest(templateNamespace = "stardewcraft_buildings", template = "construction_site")
    public static void solidFloorIsAcceptedInEveryRotationAndAirStartsAboveIt(GameTestHelper h) {
        var level = h.getLevel();
        var anchor = h.absolutePos(new BlockPos(20, 1, 20));
        for (var rotation : Rotation.values()) {
            var claim = PrefabDefinitions.transform(new BuildingBounds(BlockPos.ZERO, new BlockPos(3, 4, 4)), anchor, rotation);
            for (var pos : BlockPos.betweenClosed(claim.min(), claim.maxInclusive()))
                level.setBlock(pos, (pos.getY() == anchor.getY() ? ModBlocks.GRASS_BLOCK.get() : Blocks.AIR).defaultBlockState(), 3);
            h.assertTrue(BuildingPlacementService.checkSpace(level, claim) == null, "Embedded grass floor rejected: " + rotation);
            var passableCover = claim.min().above();
            level.setBlock(passableCover, ModBlocks.PASTURE_GRASS.get().defaultBlockState(), 3);
            h.assertTrue(BuildingPlacementService.checkSpace(level, claim) == null,
                    "Passable pasture cover rejected: " + rotation);
            level.removeBlock(passableCover, false);
            var obstacle = claim.min().above();
            level.setBlock(obstacle, Blocks.STONE.defaultBlockState(), 3);
            var secondObstacle = obstacle.east();
            level.setBlock(secondObstacle, Blocks.OAK_LOG.defaultBlockState(), 3);
            var issue = BuildingPlacementService.checkSpace(level, claim);
            h.assertTrue(issue != null && issue.issue().equals("air") && issue.pos().equals(obstacle)
                            && issue.positions().contains(obstacle) && issue.positions().contains(secondObstacle),
                    "Above-ground obstructions were not all located");
            level.removeBlock(obstacle, false);
            level.removeBlock(secondObstacle, false);
            level.removeBlock(claim.min(), false);
            issue = BuildingPlacementService.checkSpace(level, claim);
            h.assertTrue(issue != null && issue.issue().equals("ground"), "Missing support accepted");
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_buildings", template = "empty")
    public static void onlyBottomSoilIsOmittedIncludingBuildingGrassVariants(GameTestHelper h) {
        var authored = new java.util.ArrayList<PrefabDefinitions.Cell>();
        var soils = List.of(Blocks.GRASS_BLOCK, Blocks.DIRT, ModBlocks.GRASS_BLOCK.get(),
                ModBlocks.DARK_GRASS_BLOCK.get(), ModBlocks.DIRT.get());
        for (int i = 0; i < soils.size(); i++) {
            authored.add(new PrefabDefinitions.Cell(new BlockPos(i, 0, 0), soils.get(i).defaultBlockState(), null));
            authored.add(new PrefabDefinitions.Cell(new BlockPos(i, 1, 0), soils.get(i).defaultBlockState(), null));
        }
        authored.add(new PrefabDefinitions.Cell(new BlockPos(0, 0, 1), Blocks.OAK_PLANKS.defaultBlockState(), null));
        authored.add(new PrefabDefinitions.Cell(new BlockPos(1, 0, 1), Blocks.STONE_BRICKS.defaultBlockState(), null));
        var template = new PrefabDefinitions.Template(new BlockPos(5, 2, 2), authored);
        h.assertTrue(template.retainedGround().size() == 5 && template.cells().size() == 7, "Soil filtering removed floors or upper blocks");
        h.assertTrue(template.cells().stream().filter(c -> c.pos().getY() == 0).allMatch(c -> c.state().is(Blocks.OAK_PLANKS) || c.state().is(Blocks.STONE_BRICKS)), "Bottom soil remained in projection");
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_buildings", template = "construction_site")
    public static void shippedTemplatesPreviewAndMoveLeaveTerrainBehind(GameTestHelper h) {
        var level = h.getLevel();
        var anchor = h.absolutePos(new BlockPos(18, 1, 22));
        for (var familyId : List.of(PrefabDefinitions.COOP, PrefabDefinitions.BARN)) {
            PrefabDefinitions.validateAssets(level,familyId);
            var family = PrefabDefinitions.get(familyId);
            for (var tier : family.tiers()) {
                var template = PrefabDefinitions.template(level, tier);
                h.assertTrue(!template.retainedGround().isEmpty(), "Expected authored bottom terrain: " + tier.structure());
                var preview = PrefabDefinitions.previewTag(level, familyId, tier.level());
                for (var raw : preview.getList("Blocks", 10)) {
                    int[] xyz = ((CompoundTag) raw).getIntArray("Pos");
                    h.assertTrue(!template.retainedGround().contains(new BlockPos(xyz[0], xyz[1], xyz[2])), "Preview renders omitted terrain");
                }
                var record = new BuildingRecord(UUID.randomUUID(), UUID.randomUUID(), 0, familyId, BuildingRecord.Mode.PREFAB,
                        level.dimension().location(), anchor, PrefabDefinitions.world(tier.manager(), tier.anchor(), anchor, Rotation.NONE),
                        Direction.SOUTH, PrefabDefinitions.transform(family.reservation(), anchor, Rotation.NONE),
                        BuildingRecord.Phase.READY, tier.level(), BuildingRecord.Residence.VALID, 0, "Ground test");
                var nativeCells = BuildingTransfer.nativeCells(level, record, tier.level());
                var terrain = PrefabDefinitions.retainedGround(level, record);
                for (var pos : terrain) level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
                var source = BuildingTransfer.sourcePositions(level, record);
                h.assertTrue(terrain.stream().noneMatch(nativeCells::containsKey), "Retained terrain counted as native structure");
                h.assertTrue(terrain.stream().noneMatch(source::contains), "Move would remove existing terrain");
            }
        }
        h.succeed();
    }
}

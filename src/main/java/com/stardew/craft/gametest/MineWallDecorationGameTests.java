package com.stardew.craft.gametest;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.mine.MineBuildingTheme;
import com.stardew.craft.block.mine.MineGroundConnections;
import com.stardew.craft.block.mine.MineWallDecorationBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(StardewCraft.MODID)
@PrefixGameTestTemplate(false)
public final class MineWallDecorationGameTests {
    private MineWallDecorationGameTests() {}

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities", timeoutTicks = 100)
    public static void attachedSectionsUpdateOnAllWalls(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 3, 8));
        var decorations = new MineWallDecorationBlock[]{ModBlocks.MINE_VINES.get(), ModBlocks.FROST_WALL_ICE.get(),
                ModBlocks.LAVA_MINE_VINES.get(), ModBlocks.DESERT_WALL_CRUST.get()};
        for (var block : decorations) for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (int y = -1; y < 4; y++) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
                level.setBlock(base.offset(x,y,z), Blocks.AIR.defaultBlockState(), 3);
            for (int y = 0; y < 3; y++) level.setBlock(base.above(y).relative(facing.getOpposite()), Blocks.STONE.defaultBlockState(), 3);
            for (int y = 0; y < 3; y++) level.setBlock(base.above(y), block.defaultBlockState()
                    .setValue(MineWallDecorationBlock.FACING, facing).setValue(MineWallDecorationBlock.VARIANT, y)
                    .setValue(MineWallDecorationBlock.BELOW, y > 0), 3);
            for (int y = 0; y < 3; y++) {
                BlockPos pos = base.above(y);
                var state = level.getBlockState(pos);
                helper.assertTrue(state.getValue(MineWallDecorationBlock.ABOVE) == (y < 2), "Wrong upper connection");
                helper.assertTrue(state.getValue(MineWallDecorationBlock.BELOW) == (y > 0), "Wrong lower connection");
                helper.assertTrue(state.getValue(MineWallDecorationBlock.VARIANT) == y, "Variant rerolled on update");
                helper.assertTrue(state.canSurvive(level, pos), "Decoration lost wall support");
                helper.assertTrue(state.getCollisionShape(level, pos).isEmpty(), "Decoration blocks movement");
            }
            level.removeBlock(base.above().relative(facing.getOpposite()), false);
            helper.assertTrue(level.getBlockState(base.above()).isAir(), "Unsupported middle section floated");
            helper.assertTrue(!level.getBlockState(base).getValue(MineWallDecorationBlock.ABOVE), "Lower cap did not update");
            helper.assertTrue(!level.getBlockState(base.above(2)).getValue(MineWallDecorationBlock.BELOW), "Upper cap did not update");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities", timeoutTicks = 100)
    public static void allThemeGroundBordersKeepTheirMaterial(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(8, 2, 8));
        for (MineBuildingTheme theme : MineBuildingTheme.values()) {
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                level.setBlock(pos.offset(x,0,z), theme.wall().defaultBlockState(), 3);
                level.setBlock(pos.offset(x,1,z), Blocks.AIR.defaultBlockState(), 3);
            }
            level.setBlock(pos.north(), theme.soil().defaultBlockState(), 3);
            level.setBlock(pos.east(), theme.looseSoil().defaultBlockState(), 3);
            helper.assertTrue(MineGroundConnections.mask(level,pos,level.getBlockState(pos),1)==1, "Theme soil did not cover wall");
            helper.assertTrue(MineGroundConnections.mask(level,pos,level.getBlockState(pos),2)==2, "Theme loose soil did not cover wall");
            level.setBlock(pos, theme.soil().defaultBlockState(), 3);
            helper.assertTrue(MineGroundConnections.mask(level,pos,level.getBlockState(pos),2)==2, "Theme loose soil did not cover soil");
            level.setBlock(pos.above(), ModBlocks.MINE_STEP_STONE.get().defaultBlockState(), 3);
            helper.assertTrue(MineGroundConnections.mask(level,pos,level.getBlockState(pos),2)==0, "Soil border covered stone paving");
            level.removeBlock(pos.above(), false);
            int[][] offsets = {{0,-1},{1,0},{0,1},{-1,0},{1,-1},{1,1},{-1,1},{-1,-1}};
            for (int mask = 0; mask < 256; mask++) {
                for (int i = 0; i < offsets.length; i++) {
                    level.setBlock(pos.offset(offsets[i][0], 0, offsets[i][1]),
                            ((mask & (1 << i)) != 0 ? theme.looseSoil() : theme.wall()).defaultBlockState(), 2);
                }
                helper.assertTrue(MineGroundConnections.mask(level, pos, level.getBlockState(pos), 2) == mask,
                        "Eight-neighbor border mask changed for " + theme.id() + ": " + mask);
            }
            for (int[] offset : offsets) level.setBlock(pos.offset(offset[0], 0, offset[1]), theme.wall().defaultBlockState(), 2);
            var other = MineBuildingTheme.values()[(theme.ordinal()+1)%MineBuildingTheme.values().length];
            level.setBlock(pos.east(), other.looseSoil().defaultBlockState(), 3);
            helper.assertTrue(MineGroundConnections.mask(level,pos,level.getBlockState(pos),2)==0, "Foreign theme applied the wrong texture");
        }
        helper.succeed();
    }
    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities", timeoutTicks = 100)
    public static void mineSoilWrapsOntoWallFeet(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(8, 2, 8));
        for (MineBuildingTheme theme : MineBuildingTheme.values()) for (Direction face : Direction.Plane.HORIZONTAL) {
            for (int variant = 0; variant < 4; variant++) {
                for (int x = -2; x <= 2; x++) for (int y = -1; y <= 2; y++) for (int z = -2; z <= 2; z++)
                    level.setBlock(pos.offset(x,y,z), Blocks.AIR.defaultBlockState(), 2);
                var wall = theme.wall().defaultBlockState();
                var soil = variant == 3 ? theme.looseSoil().defaultBlockState() : theme.soil().defaultBlockState()
                        .setValue(com.stardew.craft.block.mine.MineSoilBlock.VARIANT, variant);
                BlockPos donor = pos.below().relative(face);
                level.setBlock(pos, wall, 2);
                level.setBlock(pos.below(), wall, 2);
                level.setBlock(donor, soil, 2);
                var connections = MineGroundConnections.faceConnections(level, pos, wall, face);
                helper.assertTrue(connections.size() == 1, "Missing wall-foot connection: " + theme.id() + " " + face);
                var edge = connections.getFirst();
                helper.assertTrue(edge.folded() && edge.edge() == 2 && edge.face() == Direction.UP,
                        "Wall must sample the floor top around its lower edge");
                helper.assertTrue(edge.state().equals(soil), "Wall-foot border lost soil variant");
                helper.assertTrue(MineGroundConnections.mask(level, pos.below(), wall, variant == 3 ? 2 : 1) == 0,
                        "Buried wall top must remain hidden");
                for (int x = 0; x < 16; x++) {
                    var sample = edge.sample(face, (x + .5) / 16., 15.5 / 16.);
                    helper.assertTrue(sample.x >= 0 && sample.x <= 1 && sample.y >= 0 && sample.y <= 1,
                            "Folded texture left the donor face");
                }
                level.setBlock(donor.above(), ModBlocks.MINE_STEP_STONE.get().defaultBlockState(), 2);
                helper.assertTrue(MineGroundConnections.faceConnections(level, pos, wall, face).isEmpty(),
                        "Soil wrapped through stone paving");
                level.setBlock(donor.above(), Blocks.WATER.defaultBlockState(), 2);
                helper.assertTrue(MineGroundConnections.faceConnections(level, pos, wall, face).isEmpty(),
                        "Soil wrapped through water");
                level.setBlock(donor.above(), Blocks.AIR.defaultBlockState(), 2);
                var other = MineBuildingTheme.values()[(theme.ordinal()+1)%MineBuildingTheme.values().length];
                level.setBlock(donor, other.soil().defaultBlockState(), 2);
                helper.assertTrue(MineGroundConnections.faceConnections(level, pos, wall, face).isEmpty(),
                        "Wall borrowed soil from another theme");
                level.setBlock(donor, Blocks.AIR.defaultBlockState(), 2);
                helper.assertTrue(MineGroundConnections.faceConnections(level, pos, wall, face).isEmpty(),
                        "Removed ground left a stale border");
            }
        }
        helper.succeed();
    }

}

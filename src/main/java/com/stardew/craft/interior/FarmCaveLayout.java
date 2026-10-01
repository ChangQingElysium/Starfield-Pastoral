package com.stardew.craft.interior;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** FarmCave.tmx: one tile per block, with a two-block rock shell. */
public final class FarmCaveLayout {
    public static final int VERSION = 1;
    public static final int WIDTH = 16, HEIGHT = 13, LENGTH = 18, FLOOR = 3;
    public static final BlockPos BASE = new BlockPos(18976, 70, 19392);
    public static final BlockPos SPAWN = new BlockPos(10, 3, 13);
    public static final BlockPos EXIT = new BlockPos(10, 3, 14);
    public static final BlockPos DEHYDRATOR = new BlockPos(12, 3, 7);
    public static final ResourceLocation TEMPLATE = new ResourceLocation("stardewcraft", "farm_layouts/cave");
    public static final List<BlockPos> BOXES = List.of(
            new BlockPos(6,3,7), new BlockPos(8,3,7), new BlockPos(10,3,7),
            new BlockPos(6,3,9), new BlockPos(8,3,9), new BlockPos(10,3,9));
    public static final List<BlockPos> LEGACY_BOXES = List.of(
            new BlockPos(3,1,3), new BlockPos(3,1,5), new BlockPos(3,1,7),
            new BlockPos(5,1,3), new BlockPos(5,1,5), new BlockPos(5,1,7));
    private static final String[] FLOOR_ROWS = {
            "############", "############", "############", "############",
            "####.....###", "##.........#", "##.........#", "##.........#",
            "##.........#", "##.........#", "###.......##", "########.###",
            "########.###", "############"};
    private FarmCaveLayout() {}
    public static boolean floor(int x, int z) {
        x -= 2; z -= 2;
        return z >= 0 && z < FLOOR_ROWS.length && x >= 0 && x < 12 && FLOOR_ROWS[z].charAt(x)=='.';
    }
    public static boolean reserved(BlockPos local) {
        return local.getY() >= FLOOR && local.getY() <= FLOOR+1
                && local.getX()==10 && local.getZ()>=13 && local.getZ()<=14;
    }
    public static boolean contains(BlockPos origin, BlockPos pos) {
        return pos.getX()>=origin.getX() && pos.getX()<origin.getX()+WIDTH
                && pos.getY()>=origin.getY() && pos.getY()<origin.getY()+HEIGHT
                && pos.getZ()>=origin.getZ() && pos.getZ()<origin.getZ()+LENGTH;
    }
}

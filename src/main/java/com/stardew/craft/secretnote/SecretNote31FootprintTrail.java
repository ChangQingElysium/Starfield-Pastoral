package com.stardew.craft.secretnote;

import com.stardew.craft.block.nature.ShadowFootprintBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.List;

/** The authored Shadow Guy footprint trail for event 520702. */
public final class SecretNote31FootprintTrail {
    public static final String BUS_STOP_EVENT_ID = "520702";

    public static final List<Footprint> FOOTPRINTS = List.of(
            footprint(-8, 64, -18, Direction.NORTH, ShadowFootprintBlock.Foot.RIGHT),
            footprint(-9, 64, -16, Direction.NORTH, ShadowFootprintBlock.Foot.LEFT),
            footprint(-13, 64, -14, Direction.EAST, ShadowFootprintBlock.Foot.RIGHT),
            footprint(-17, 64, -13, Direction.EAST, ShadowFootprintBlock.Foot.LEFT),
            footprint(-20, 64, -12, Direction.EAST, ShadowFootprintBlock.Foot.RIGHT),
            footprint(-25, 64, -11, Direction.EAST, ShadowFootprintBlock.Foot.RIGHT),
            footprint(-24, 64, -11, Direction.EAST, ShadowFootprintBlock.Foot.LEFT),
            footprint(-21, 64, -11, Direction.EAST, ShadowFootprintBlock.Foot.LEFT),
            footprint(-2, 66, -63, Direction.NORTH, ShadowFootprintBlock.Foot.RIGHT),
            footprint(-2, 66, -62, Direction.NORTH, ShadowFootprintBlock.Foot.LEFT),
            footprint(-1, 66, -61, Direction.NORTH, ShadowFootprintBlock.Foot.RIGHT),
            footprint(0, 66, -59, Direction.NORTH, ShadowFootprintBlock.Foot.LEFT),
            footprint(0, 66, -58, Direction.NORTH, ShadowFootprintBlock.Foot.RIGHT),
            footprint(2, 66, -50, Direction.NORTH, ShadowFootprintBlock.Foot.LEFT),
            footprint(3, 66, -49, Direction.NORTH, ShadowFootprintBlock.Foot.RIGHT),
            footprint(3, 66, -48, Direction.NORTH, ShadowFootprintBlock.Foot.LEFT),
            footprint(-3, 66, -40, Direction.NORTH, ShadowFootprintBlock.Foot.RIGHT),
            footprint(-4, 66, -39, Direction.NORTH, ShadowFootprintBlock.Foot.LEFT),
            footprint(-4, 66, -38, Direction.NORTH, ShadowFootprintBlock.Foot.RIGHT),
            footprint(-5, 66, -37, Direction.NORTH, ShadowFootprintBlock.Foot.LEFT),
            footprint(-4, 66, -36, Direction.NORTH, ShadowFootprintBlock.Foot.RIGHT),
            footprint(-5, 66, -34, Direction.NORTH, ShadowFootprintBlock.Foot.LEFT),
            footprint(-5, 66, -32, Direction.NORTH, ShadowFootprintBlock.Foot.RIGHT),
            footprint(-4, 66, -31, Direction.NORTH, ShadowFootprintBlock.Foot.LEFT)
    );

    private SecretNote31FootprintTrail() {}

    private static Footprint footprint(int x, int y, int z, Direction direction,
                                       ShadowFootprintBlock.Foot foot) {
        return new Footprint(new BlockPos(x, y, z), direction, foot);
    }

    public record Footprint(BlockPos pos, Direction direction, ShadowFootprintBlock.Foot foot) {}
}

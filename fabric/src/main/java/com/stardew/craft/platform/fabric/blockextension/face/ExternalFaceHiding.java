package com.stardew.craft.platform.fabric.blockextension.face;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/** Neighbor-first evaluation mirrors the original shouldRenderFace extension. */
public final class ExternalFaceHiding {
    private ExternalFaceHiding() {}
    public static boolean hides(BlockState rendered, BlockState covering, BlockGetter level, BlockPos coveringPos, Direction renderedFace) {
        return covering.getBlock() instanceof ExternalFaceHidingProvider cover
                && cover.hidesNeighborFace(level, coveringPos, covering, rendered, renderedFace.getOpposite())
                && rendered.getBlock() instanceof ExternalFaceHidingProvider source
                && source.supportsExternalFaceHiding(rendered);
    }
}

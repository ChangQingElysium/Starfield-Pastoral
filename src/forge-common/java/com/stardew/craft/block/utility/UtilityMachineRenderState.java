package com.stardew.craft.block.utility;

import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Keeps idle machine bodies in the chunk mesh and reserves block-entity rendering for animation.
 */
public final class UtilityMachineRenderState {
    private UtilityMachineRenderState() {
    }

    public static RenderShape forWorkingState(boolean working, boolean extension) {
        if (extension) {
            return RenderShape.INVISIBLE;
        }
        return working ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.MODEL;
    }

    public static RenderShape forWorkingState(boolean working) {
        return forWorkingState(working, false);
    }

    public static boolean rendersDynamicBody(BlockState state) {
        return state.getRenderShape() == RenderShape.ENTITYBLOCK_ANIMATED;
    }
}

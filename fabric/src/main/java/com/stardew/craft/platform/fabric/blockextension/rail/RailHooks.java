package com.stardew.craft.platform.fabric.blockextension.rail;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

public final class RailHooks {
    private RailHooks() {}
    /** Current callers use the native shape-property default; no contextual direction override exists in this mod. */
    public static RailShape getRailDirection(BaseRailBlock block, BlockState state, BlockGetter level, BlockPos pos, AbstractMinecart cart) {
        return state.getValue(block.getShapeProperty());
    }
    public static boolean isValid(BaseRailBlock block, RailShape shape) {
        return shape != null && (!(block instanceof RailBehaviorProvider provider) || provider.isValidRailShape(shape));
    }
    public static RailShape placementShape(BaseRailBlock block, RailShape shape, RailShape fallback) {
        return isValid(block, shape) ? shape : fallback;
    }
}

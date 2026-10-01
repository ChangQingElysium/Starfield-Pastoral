package com.stardew.craft.templates;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Front timber occupies z=0..3; the optional wall backing occupies z=3..16. */
public class WallCompositeTemplateBlock extends CompositeTemplateBlock {
    public WallCompositeTemplateBlock(TemplateShape shape, Properties properties) { super(shape,properties); }

    @Override
    public boolean targetsFill(BlockState state, BlockHitResult hit) {
        double x=hit.getLocation().x-hit.getBlockPos().getX(),z=hit.getLocation().z-hit.getBlockPos().getZ();
        for(int i=0,n=TemplateShapeCache.turnsFrom(templateShape().baseFacing(),state.getValue(FACING));i<n;i++) {
            double old=x;x=z;z=1-old;
        }
        if (templateShape().isWindow()) {
            double y = hit.getLocation().y - hit.getBlockPos().getY();
            if (state.getValue(FLIPPED)) y = 1-y;
            if (hit.getDirection() != state.getValue(FACING) && hit.getDirection() != state.getValue(FACING).getOpposite()) return false;
            for (var pane : FacadeTemplateGeometry.fill(templateShape(), ConnectedFacadeTemplateBlock.connections(state))) {
                if (x > pane.minX()/16D && x < pane.maxX()/16D && y > pane.minY()/16D && y < pane.maxY()/16D
                        && z >= pane.minZ()/16D-1E-5 && z <= pane.maxZ()/16D+1E-5) return true;
            }
            return false;
        }
        return z >= 3/16D-1E-5 || hit.getDirection() == state.getValue(FACING).getOpposite();
    }

    @Override
    public BlockState defaultFillMaterial() {
        return templateShape().isWindow()?com.stardew.craft.block.ModBlocks.PALE_BLUE_WINDOW_GLASS.get().defaultBlockState():null;
    }
}

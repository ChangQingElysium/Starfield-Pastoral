package com.stardew.craft.block.decor;

import com.stardew.craft.blockentity.BookshelfGeoBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.Set;

@SuppressWarnings("null")
public class BookshelfGeoDecorBlock extends IntegratedAabbDecorBlock implements EntityBlock {
    public BookshelfGeoDecorBlock(Properties properties, String modelId,
                                  double minX, double minY, double minZ,
                                  double maxX, double maxY, double maxZ) {
        super(properties, modelId, minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    protected Set<CellOffset> localOccupiedOffsets() {
        Set<CellOffset> cells = new LinkedHashSet<>();
        for (int x = -1; x <= 0; x++) {
            for (int y = 0; y < 3; y++) {
                cells.add(new CellOffset(x, y, 0));
            }
        }
        return cells;
    }

    @Override
    public RenderShape getRenderShape(@Nonnull BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        if (state.getValue(PART) != Part.MAIN) {
            return null;
        }
        return new BookshelfGeoBlockEntity(pos, state);
    }
}

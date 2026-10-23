package com.stardew.craft.platform.fabric.blockextension;

import io.github.fabricators_of_create.porting_lib.models.data.ModelData;
import io.github.fabricators_of_create.porting_lib.models.data.ModelProperty;
import java.util.Objects;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.blockview.v2.FabricBlockView;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Reads the same immutable material attachment that Fabric copied into a render region. */
public final class BlockMaterialSnapshots {
    private BlockMaterialSnapshots() {}

    public static BlockState material(FabricBlockView view, BlockPos pos, ModelProperty<BlockState> property,
            Supplier<BlockState> fallback) {
        Object attachment = view.getBlockEntityRenderData(pos);
        if (attachment instanceof ModelData data) {
            BlockState material = data.get(property);
            if (material != null) return material;
        }
        return Objects.requireNonNull(fallback.get(), "default template material");
    }
}

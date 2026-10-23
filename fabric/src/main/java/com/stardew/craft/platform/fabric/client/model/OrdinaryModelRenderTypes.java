package com.stardew.craft.platform.fabric.client.model;

import io.github.fabricators_of_create.porting_lib.models.data.ModelData;
import io.github.fabricators_of_create.porting_lib.models.geometry.extensions.BlockModelExtension;
import io.github.fabricators_of_create.porting_lib.render_types.RenderTypeGroup;
import java.util.List;
import java.util.Objects;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Retains native ordinary-model geometry while exposing its JSON layer hint to FRAPI. */
public final class OrdinaryModelRenderTypes {
    private OrdinaryModelRenderTypes() {}

    public static BakedModel apply(BlockModel model, BakedModel baked) {
        Objects.requireNonNull(baked, "baked model");
        if (model.getRootModel() == ModelBakery.BLOCK_ENTITY_MARKER
                || model.getRootModel() == ModelBakery.GENERATION_MARKER) return baked;
        var context = ((BlockModelExtension) (Object) model).port_lib$getCustomData();
        // A custom loader owns its native mesh/material and must not be wrapped a second time.
        if (context.hasCustomGeometry()) return baked;
        var hint = context.getRenderTypeHint();
        if (hint == null) return baked;
        RenderTypeGroup group = context.getRenderType(hint);
        return group.isEmpty() ? baked : new HintedModel(baked, group);
    }

    static boolean hasHintedGeometry(BakedModel model) {
        if (model instanceof HintedModel) return true;
        return model instanceof BakedModelWrapper<?> wrapper && hasHintedGeometry(wrapper.originalModel);
    }

    static RenderMaterial materialFor(BakedModel model, RenderType layer) {
        if (model instanceof HintedModel hinted) {
            var group = hinted.types;
            return layer == group.block() || layer == group.entity() || layer == group.entityFabulous()
                    ? group.material() : null;
        }
        return model instanceof BakedModelWrapper<?> wrapper ? materialFor(wrapper.originalModel, layer) : null;
    }

    private static final class HintedModel extends BakedModelWrapper<BakedModel> implements IDynamicBakedModel {
        private final RenderTypeGroup types;
        HintedModel(BakedModel original, RenderTypeGroup types) { super(original); this.types = types; }
        @Override public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
            return ChunkRenderTypeSet.of(types.block());
        }
        @Override public List<RenderType> getRenderTypes(ItemStack stack, boolean direct) {
            return List.of(direct ? types.entity() : types.entityFabulous());
        }
    }
}

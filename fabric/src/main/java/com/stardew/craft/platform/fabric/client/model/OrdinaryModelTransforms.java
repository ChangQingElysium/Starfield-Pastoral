package com.stardew.craft.platform.fabric.client.model;

import com.mojang.math.Transformation;
import io.github.fabricators_of_create.porting_lib.models.geometry.extensions.BlockModelExtension;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelState;

/** Root transforms for the ordinary-element path that Porting Lib leaves to vanilla. */
public final class OrdinaryModelTransforms {
    private OrdinaryModelTransforms() {}

    public static ModelState forBake(BlockModel model, ModelState state) {
        // PL owns custom geometries, including inherited geometry. Builtin/generated models
        // retain their existing native paths; this bridge only handles ordinary elements.
        if (model.getRootModel() == ModelBakery.BLOCK_ENTITY_MARKER
                || model.getRootModel() == ModelBakery.GENERATION_MARKER) return state;
        var context = ((BlockModelExtension) (Object) model).port_lib$getCustomData();
        if (context.hasCustomGeometry()) return state;
        Transformation root = context.getRootTransform();
        if (root.equals(Transformation.identity())) return state;
        // Neo 21.1.217 ElementsModel composes before FaceBakery, including UV-lock and cull rotation.
        return CompositeGeometry.composeRootTransform(state, root);
    }

    /** Neo treats a state with a composed root transform as potentially non-axis-aligned. */
    public record RootTransformState(Transformation rotation, boolean uvLocked) implements ModelState {
        @Override public Transformation getRotation() { return rotation; }
        @Override public boolean isUvLocked() { return uvLocked; }
    }
}

package com.stardew.craft.platform.fabric.client.model;

import com.google.gson.JsonObject;
import io.github.fabricators_of_create.porting_lib.models.ExtraFaceData;
import io.github.fabricators_of_create.porting_lib.models.IModelBuilder;
import io.github.fabricators_of_create.porting_lib.models.geometry.IGeometryBakingContext;
import io.github.fabricators_of_create.porting_lib.models.geometry.extensions.BlockElementExtension;
import io.github.fabricators_of_create.porting_lib.models.geometry.extensions.BlockElementFaceExtension;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import org.apache.commons.lang3.mutable.MutableObject;

/** Uses Porting Lib's public LGPL-2.1-or-later APIs; supplies the per-face AO its FaceBakery TODO does not encode. */
public final class GeometryQuads {
    private GeometryQuads() {}

    public static BlockElementFace faceData(BlockElementFace face, JsonObject json) {
        if (json.has("neoforge_data")) ((BlockElementFaceExtension) (Object) face).port_lib$setFaceData(
                ExtraFaceData.read(json.get("neoforge_data"), null), new MutableObject<>());
        return face;
    }

    public static BlockElement elementData(BlockElement element, ExtraFaceData data) {
        ((BlockElementExtension) (Object) element).port_lib$setFaceData(data);
        return element;
    }

    public static boolean ambientOcclusion(BlockElementFace face) {
        return ((BlockElementFaceExtension) (Object) face).port_lib$faceData().ambientOcclusion();
    }

    public static void addUnculled(IGeometryBakingContext context, IModelBuilder<?> builder, BakedQuad quad) {
        var renderer = java.util.Objects.requireNonNull(RendererAccess.INSTANCE.getRenderer(), "Fabric renderer");
        var finder = renderer.materialFinder();
        var hint = context.getRenderTypeHint();
        if (hint != null) finder.copyFrom(context.getRenderType(hint).material());
        var material = finder.ambientOcclusion(TriState.of(context.useAmbientOcclusion() && ModelQuad.ambientOcclusion(quad)))
                .disableDiffuse(!quad.isShade()).find();
        var emitter = renderer.meshBuilder().getEmitter();
        emitter.fromVanilla(quad, material, null);
        builder.addFace(emitter);
    }
}

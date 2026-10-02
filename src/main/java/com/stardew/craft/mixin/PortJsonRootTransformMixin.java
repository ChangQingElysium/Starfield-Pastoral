package com.stardew.craft.mixin;

import com.mojang.math.Transformation;
import com.stardew.craft.port.PortFaceBakery;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.ElementsModel;
import net.minecraftforge.client.model.IModelBuilder;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import net.minecraftforge.client.model.geometry.UnbakedGeometryHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NeoForge composes root transforms before face baking, not into already packed vertices/normals. */
@Mixin(value = ElementsModel.class, remap = false)
public abstract class PortJsonRootTransformMixin {
    @Shadow @Final private List<BlockElement> elements;

    @Inject(method = "addQuads", at = @At("HEAD"), cancellable = true, require = 1)
    private void stardewcraft$bakeRootTransform(IGeometryBakingContext context, IModelBuilder<?> builder,
            ModelBaker baker, Function<Material, TextureAtlasSprite> sprites, ModelState state,
            ResourceLocation modelLocation, CallbackInfo ci) {
        if (modelLocation == null || !"stardewcraft".equals(modelLocation.getNamespace())) {
            return;
        }
        Transformation root = context.getRootTransform();
        if (root.isIdentity()) {
            return;
        }
        // Both versions expose this composition helper with identical non-identity arithmetic.
        ModelState composed = UnbakedGeometryHelper.composeRootTransformIntoModelState(state, root);
        for (BlockElement element : elements) {
            for (Direction direction : element.faces.keySet()) {
                BlockElementFace face = element.faces.get(direction);
                TextureAtlasSprite sprite = sprites.apply(context.getMaterial(face.texture));
                BakedQuad quad = PortFaceBakery.bakeFace(element, face, sprite, direction, composed);
                if (face.cullForDirection == null) {
                    builder.addUnculledFace(quad);
                } else {
                    builder.addCulledFace(composed.getRotation().rotateTransform(face.cullForDirection), quad);
                }
            }
        }
        ci.cancel();
    }
}

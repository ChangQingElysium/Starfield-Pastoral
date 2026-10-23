package com.stardew.craft.platform.fabric.client.model;

import com.stardew.craft.platform.fabric.event.ModBusEvent;
import com.stardew.craft.platform.fabric.event.Event;
import io.github.fabricators_of_create.porting_lib.models.geometry.IGeometryLoader;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;

/** Client MOD events at the native model loading and baking boundaries. */
@Environment(EnvType.CLIENT)
public abstract class ModelEvent extends Event {
    private ModelEvent() {}

    /** Changes this reload's actual baked table before blockstate models are cached, on the reload worker. */
    public static final class ModifyBakingResult extends ModelEvent implements ModBusEvent {
        private final Map<ModelResourceLocation, BakedModel> models;
        private final Function<Material, TextureAtlasSprite> textureGetter;
        private final ModelBakery modelBakery;

        public ModifyBakingResult(Map<ModelResourceLocation, BakedModel> models,
                Function<Material, TextureAtlasSprite> textureGetter, ModelBakery modelBakery) {
            this.models = Objects.requireNonNull(models, "models");
            this.textureGetter = Objects.requireNonNull(textureGetter, "textureGetter");
            this.modelBakery = Objects.requireNonNull(modelBakery, "modelBakery");
        }

        public Map<ModelResourceLocation, BakedModel> getModels() { return models; }
        public Function<Material, TextureAtlasSprite> getTextureGetter() { return textureGetter; }
        public ModelBakery getModelBakery() { return modelBakery; }
    }

    /** Published on the apply thread after the manager has adopted the new models and atlases. */
    public static final class BakingCompleted extends ModelEvent implements ModBusEvent {
        private final ModelManager modelManager;
        private final Map<ModelResourceLocation, BakedModel> models;
        private final ModelBakery modelBakery;

        public BakingCompleted(ModelManager modelManager, Map<ModelResourceLocation, BakedModel> models,
                ModelBakery modelBakery) {
            this.modelManager = Objects.requireNonNull(modelManager, "modelManager");
            this.models = Collections.unmodifiableMap(Objects.requireNonNull(models, "models"));
            this.modelBakery = Objects.requireNonNull(modelBakery, "modelBakery");
        }

        public ModelManager getModelManager() { return modelManager; }
        public Map<ModelResourceLocation, BakedModel> getModels() { return models; }
        public ModelBakery getModelBakery() { return modelBakery; }
    }

    /** Extra JSON models are loaded, resolved and baked under their original standalone keys. */
    public static final class RegisterAdditional extends ModelEvent implements ModBusEvent {
        private final Set<ModelResourceLocation> models;

        public RegisterAdditional(Set<ModelResourceLocation> models) {
            this.models = Objects.requireNonNull(models, "models");
        }

        public void register(ModelResourceLocation model) {
            Objects.requireNonNull(model, "model");
            if (!model.getVariant().equals("standalone")) {
                throw new IllegalArgumentException("Additional model must use the standalone variant: " + model);
            }
            models.add(model);
        }
    }

    /** Writes directly into Porting Lib's geometry-loader registration callback. */
    public static final class RegisterGeometryLoaders extends ModelEvent implements ModBusEvent {
        private final Map<ResourceLocation, IGeometryLoader<?>> loaders;

        public RegisterGeometryLoaders(Map<ResourceLocation, IGeometryLoader<?>> loaders) {
            this.loaders = Objects.requireNonNull(loaders, "loaders");
        }

        public void register(ResourceLocation key, IGeometryLoader<?> loader) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(loader, "loader");
            if (loaders.containsKey(key)) throw new IllegalArgumentException("Duplicate geometry loader: " + key);
            loaders.put(key, loader);
        }
    }
}

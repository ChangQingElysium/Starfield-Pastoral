package com.stardew.craft.platform.fabric.client.model;

import com.stardew.craft.platform.fabric.event.IEventBus;
import io.github.fabricators_of_create.porting_lib.models.geometry.IGeometryLoader;
import io.github.fabricators_of_create.porting_lib.models.geometry.RegisterGeometryLoadersCallback;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.AtlasSet;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Install after subscriber registration and before any model JSON is deserialized. */
@Environment(EnvType.CLIENT)
public final class FabricModelEvents {
    private static final Logger LOGGER = LoggerFactory.getLogger(FabricModelEvents.class);
    private static volatile FabricModelEvents installed;
    private final IEventBus bus;

    FabricModelEvents(IEventBus bus) {
        this.bus = Objects.requireNonNull(bus, "bus");
    }

    public static synchronized void install(IEventBus bus) {
        Objects.requireNonNull(bus, "bus");
        if (installed != null) {
            if (installed.bus == bus) return;
            throw new IllegalStateException("Model events already have an installed mod bus");
        }
        var bridge = new FabricModelEvents(bus);
        // Do not initialize GeometryLoaderManager here: its own init callback collects all PL loaders.
        RegisterGeometryLoadersCallback.EVENT.register(bridge::registerGeometryLoaders);
        installed = bridge;
    }

    public static void onRegisterAdditional(Consumer<ModelResourceLocation> loadModelAndDependencies) {
        requireInstalled().registerAdditional(loadModelAndDependencies);
    }

    public static void onModifyBakingResult(Map<ModelResourceLocation, BakedModel> models,
            Map<ResourceLocation, AtlasSet.StitchResult> atlases, ModelBakery bakery) {
        requireInstalled().modifyBakingResult(models, textureGetter(atlases), bakery);
    }

    public static void onBakingCompleted(ModelManager manager, Map<ModelResourceLocation, BakedModel> models,
            ModelBakery bakery) {
        requireInstalled().bakingCompleted(manager, models, bakery);
    }

    private static FabricModelEvents requireInstalled() {
        FabricModelEvents bridge = installed;
        if (bridge == null) throw new IllegalStateException("Model event bus must be installed before model loading");
        return bridge;
    }

    void registerAdditional(Consumer<ModelResourceLocation> loadModelAndDependencies) {
        Objects.requireNonNull(loadModelAndDependencies, "loadModelAndDependencies");
        var additional = new LinkedHashSet<ModelResourceLocation>();
        bus.post(new ModelEvent.RegisterAdditional(additional));
        additional.forEach(loadModelAndDependencies);
    }

    void registerGeometryLoaders(Map<ResourceLocation, IGeometryLoader<?>> loaders) {
        new ModelEvent.RegisterGeometryLoaders(loaders).register(CompositeGeometry.LOADER_ID, CompositeGeometry.LOADER);
        bus.post(new ModelEvent.RegisterGeometryLoaders(loaders));
    }

    void modifyBakingResult(Map<ModelResourceLocation, BakedModel> models,
            Function<Material, TextureAtlasSprite> textureGetter, ModelBakery bakery) {
        bus.post(new ModelEvent.ModifyBakingResult(models, textureGetter, bakery));
    }

    void bakingCompleted(ModelManager manager, Map<ModelResourceLocation, BakedModel> models, ModelBakery bakery) {
        bus.post(new ModelEvent.BakingCompleted(manager, models, bakery));
    }

    static Function<Material, TextureAtlasSprite> textureGetter(Map<ResourceLocation, AtlasSet.StitchResult> atlases) {
        Objects.requireNonNull(atlases, "atlases");
        return material -> {
            var atlas = Objects.requireNonNull(atlases.get(material.atlasLocation()),
                    () -> "Unstitched atlas in baked model material: " + material.atlasLocation());
            TextureAtlasSprite sprite = atlas.getSprite(material.texture());
            if (sprite != null) return sprite;
            LOGGER.warn("Failed to retrieve texture '{}' from atlas '{}'", material.texture(), material.atlasLocation());
            return atlas.missing();
        };
    }
}

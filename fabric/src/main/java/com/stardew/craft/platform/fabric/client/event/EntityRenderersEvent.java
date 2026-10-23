package com.stardew.craft.platform.fabric.client.event;

import com.stardew.craft.platform.fabric.event.ModBusEvent;
import com.stardew.craft.platform.fabric.event.Event;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public abstract class EntityRenderersEvent extends Event implements ModBusEvent {
    private EntityRenderersEvent() {}

    public static final class RegisterLayerDefinitions extends EntityRenderersEvent {
        public void registerLayerDefinition(ModelLayerLocation location, Supplier<LayerDefinition> supplier) {
            Objects.requireNonNull(supplier, "supplier");
            EntityModelLayerRegistry.registerModelLayer(location, supplier::get);
        }
    }

    public static final class RegisterRenderers extends EntityRenderersEvent {
        public <T extends Entity> void registerEntityRenderer(EntityType<? extends T> type, EntityRendererProvider<T> provider) {
            EntityRendererRegistry.register(type, provider);
        }

        @SuppressWarnings("deprecation")
        public <T extends BlockEntity> void registerBlockEntityRenderer(
                BlockEntityType<? extends T> type, BlockEntityRendererProvider<T> provider) {
            BlockEntityRendererRegistry.register(type, provider);
        }
    }

    /** Actual renderer instances from one completed EntityRenderDispatcher reload. */
    public static final class AddLayers extends EntityRenderersEvent {
        private final Map<EntityType<?>, EntityRenderer<?>> renderers;
        private final Map<PlayerSkin.Model, EntityRenderer<? extends Player>> skins;
        private final EntityRendererProvider.Context context;

        public AddLayers(Map<EntityType<?>, EntityRenderer<?>> renderers,
                Map<PlayerSkin.Model, EntityRenderer<? extends Player>> skins,
                EntityRendererProvider.Context context) {
            this.renderers = Collections.unmodifiableMap(Objects.requireNonNull(renderers, "renderers"));
            this.skins = Collections.unmodifiableMap(Objects.requireNonNull(skins, "skins"));
            this.context = Objects.requireNonNull(context, "context");
        }

        public Set<EntityType<?>> getEntityTypes() {
            return renderers.keySet();
        }

        @Nullable
        @SuppressWarnings("unchecked")
        public <T extends Entity, R extends EntityRenderer<T>> R getRenderer(EntityType<? extends T> type) {
            return (R) renderers.get(type);
        }

        public Set<PlayerSkin.Model> getSkins() {
            return skins.keySet();
        }

        @Nullable
        @SuppressWarnings("unchecked")
        public <R extends EntityRenderer<? extends Player>> R getSkin(PlayerSkin.Model skin) {
            return (R) skins.get(skin);
        }

        public EntityModelSet getEntityModels() {
            return context.getModelSet();
        }

        public EntityRendererProvider.Context getContext() {
            return context;
        }
    }
}

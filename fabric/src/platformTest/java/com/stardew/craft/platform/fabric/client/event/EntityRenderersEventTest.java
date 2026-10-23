package com.stardew.craft.platform.fabric.client.event;

import static org.junit.jupiter.api.Assertions.*;

import com.stardew.craft.platform.fabric.event.EventBus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EntityRenderersEventTest {
    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void addLayersExposesAllRealEntityAndSkinInstancesAndTheReloadContext() {
        var models = new EntityModelSet();
        var context = context(models);
        var pig = new FixtureRenderer<Entity>(context);
        var arrow = new FixtureRenderer<Entity>(context);
        var wide = new FixtureRenderer<Player>(context);
        var slim = new FixtureRenderer<Player>(context);
        Map<EntityType<?>, EntityRenderer<?>> entities = Map.of(EntityType.PIG, pig, EntityType.ARROW, arrow);
        Map<PlayerSkin.Model, EntityRenderer<? extends Player>> skins = Map.of(PlayerSkin.Model.WIDE, wide, PlayerSkin.Model.SLIM, slim);
        var event = new EntityRenderersEvent.AddLayers(entities, skins, context);
        assertEquals(entities.keySet(), event.getEntityTypes());
        assertEquals(skins.keySet(), event.getSkins());
        assertSame(pig, event.getRenderer(EntityType.PIG));
        assertSame(arrow, event.getRenderer(EntityType.ARROW));
        assertSame(wide, event.getSkin(PlayerSkin.Model.WIDE));
        assertSame(slim, event.getSkin(PlayerSkin.Model.SLIM));
        assertNull(event.getRenderer(EntityType.COW));
        assertSame(context, event.getContext());
        assertSame(models, event.getEntityModels());
        assertFalse(event.isCancelable());
    }

    @Test
    void eachReloadPublishesItsNewInstancesOnceAndInstallationIsIdempotent() throws ReflectiveOperationException {
        var installedBus = FabricClientRegistrationEvents.class.getDeclaredField("installedBus");
        installedBus.setAccessible(true);
        Object previous = installedBus.get(null);
        installedBus.set(null, null);
        try {
            var bus = new EventBus();
            List<EntityRenderersEvent.AddLayers> seen = new ArrayList<>();
            bus.addListener(EntityRenderersEvent.AddLayers.class, seen::add);
            FabricClientRegistrationEvents.install(bus);
            FabricClientRegistrationEvents.install(bus);
            assertThrows(IllegalStateException.class, () -> FabricClientRegistrationEvents.install(new EventBus()));
            var firstContext = context(new EntityModelSet());
            var first = new FixtureRenderer<Entity>(firstContext);
            FabricClientRegistrationEvents.onRenderersReloaded(Map.of(EntityType.PIG, first), Map.of(), firstContext);
            var nextContext = context(new EntityModelSet());
            var next = new FixtureRenderer<Entity>(nextContext);
            FabricClientRegistrationEvents.onRenderersReloaded(Map.of(EntityType.PIG, next), Map.of(), nextContext);
            assertEquals(2, seen.size());
            assertSame(first, seen.getFirst().getRenderer(EntityType.PIG));
            assertSame(next, seen.getLast().getRenderer(EntityType.PIG));
            assertSame(firstContext, seen.getFirst().getContext());
            assertSame(nextContext, seen.getLast().getContext());
        } finally {
            installedBus.set(null, previous);
        }
    }

    private static EntityRendererProvider.Context context(EntityModelSet models) {
        return new EntityRendererProvider.Context(null, null, null, null, null, models, null);
    }

    private static final class FixtureRenderer<T extends Entity> extends EntityRenderer<T> {
        FixtureRenderer(EntityRendererProvider.Context context) { super(context); }
        @Override public ResourceLocation getTextureLocation(T entity) { return ResourceLocation.parse("fixture:texture"); }
    }
}

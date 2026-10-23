package com.stardew.craft.platform.fabric.client.event;

import com.stardew.craft.platform.fabric.event.IEventBus;
import java.util.Map;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;

/** Install with the client mod event bus before the first resource reload. */
@Environment(EnvType.CLIENT)
public final class FabricClientRegistrationEvents {
    private static volatile IEventBus installedBus;

    private FabricClientRegistrationEvents() {}

    public static synchronized void install(IEventBus bus) {
        Objects.requireNonNull(bus, "bus");
        if (installedBus == bus) return;
        if (installedBus != null) throw new IllegalStateException("Client registration events already have an installed bus");
        installedBus = bus;
    }

    public static void onRenderersReloaded(Map<EntityType<?>, EntityRenderer<?>> renderers,
            Map<PlayerSkin.Model, EntityRenderer<? extends Player>> skins, EntityRendererProvider.Context context) {
        IEventBus bus = installedBus;
        if (bus == null) throw new IllegalStateException("Client registration event bus must be installed before resource reload");
        bus.post(new EntityRenderersEvent.AddLayers(renderers, skins, context));
    }

    public static void onAtlasUploaded(TextureAtlas atlas) {
        IEventBus bus = installedBus;
        if (bus == null) throw new IllegalStateException("Client registration event bus must be installed before atlas upload");
        bus.post(new TextureAtlasStitchedEvent(atlas));
    }
}

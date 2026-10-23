package com.stardew.craft.platform.fabric.client.event.entityrender;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.platform.fabric.client.event.RenderLivingEvent;
import com.stardew.craft.platform.fabric.client.event.RenderPlayerEvent;
import com.stardew.craft.platform.fabric.event.IEventBus;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

@Environment(EnvType.CLIENT)
public final class FabricEntityRenderEvents {
    private static IEventBus installedBus;
    private FabricEntityRenderEvents() {}
    public static synchronized void install(IEventBus bus) {
        Objects.requireNonNull(bus, "bus"); if (installedBus == bus) return;
        if (installedBus != null) throw new IllegalStateException("Entity render event bus already installed"); installedBus = bus;
    }
    public static IEventBus bus() {
        if (installedBus == null) throw new IllegalStateException("Entity render event bus is not installed"); return installedBus;
    }
    public static void player(IEventBus bus, Player entity, PlayerRenderer renderer, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, Runnable original) {
        if (bus.post(new RenderPlayerEvent.Pre(entity, renderer, partialTick, pose, buffers, light)).isCanceled()) return;
        original.run(); bus.post(new RenderPlayerEvent.Post(entity, renderer, partialTick, pose, buffers, light));
    }
    public static <T extends LivingEntity, M extends EntityModel<T>> void living(IEventBus bus, T entity, LivingEntityRenderer<T, M> renderer,
            float partialTick, PoseStack pose, MultiBufferSource buffers, int light, Runnable original) {
        if (bus.post(new RenderLivingEvent.Pre<>(entity, renderer, partialTick, pose, buffers, light)).isCanceled()) return;
        original.run(); bus.post(new RenderLivingEvent.Post<>(entity, renderer, partialTick, pose, buffers, light));
    }
}

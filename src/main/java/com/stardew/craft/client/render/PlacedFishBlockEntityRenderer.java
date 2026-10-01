package com.stardew.craft.client.render;

import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.decor.PlacedFishBlock;
import com.stardew.craft.blockentity.PlacedFishBlockEntity;
import com.stardew.craft.client.fishpond.ClientFishPondFishRenderer;
import com.stardew.craft.client.npcnative.NativeNpcModel;
import com.stardew.craft.client.npcnative.NativeNpcPose;
import com.stardew.craft.client.npcnative.NativeNpcPoseRenderer;
import com.stardew.craft.fishing.FishMarketCrateLayout;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;

@EventBusSubscriber(modid=StardewCraft.MODID, bus=EventBusSubscriber.Bus.MOD, value=Dist.CLIENT)
public final class PlacedFishBlockEntityRenderer implements BlockEntityRenderer<PlacedFishBlockEntity> {
    private record Mount(NativeNpcModel model, NativeNpcPose pose) {}
    private static volatile Mount mount;
    private final NativeNpcPoseRenderer renderer = new NativeNpcPoseRenderer();
    public PlacedFishBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}

    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resources -> {
            var id = new ResourceLocation(StardewCraft.MODID, "fishing_native/fish_wall_mount.json");
            try (var reader = resources.openAsReader(id)) {
                var model = new Gson().fromJson(reader, NativeNpcModel.class);
                if (model.version()!=1 || model.quads().isEmpty()) throw new IllegalArgumentException("Empty fish mount");
                resources.getResourceOrThrow(new ResourceLocation(model.texture()));
                mount = new Mount(model, new NativeNpcPose(model));
            } catch (Exception e) { throw new IllegalStateException("Cannot load fish wall mount", e); }
        });
    }
    @Override public void render(PlacedFishBlockEntity entity, float partialTick, PoseStack stack,
                                 MultiBufferSource buffers, int light, int overlay) {
        var fish = entity.fish();
        boolean wall = entity.getBlockState().getValue(PlacedFishBlock.WALL);
        var fit = ClientFishPondFishRenderer.placedPose(fish, wall);
        if (fit == null) return;
        stack.pushPose();
        stack.translate(.5, 0, .5);
        stack.mulPose(Axis.YP.rotationDegrees(-90 * FishMarketCrateLayout.quarterTurns(entity.getBlockState().getValue(PlacedFishBlock.FACING))));
        stack.translate(-.5, 0, -.5);
        ClientFishPondFishRenderer.renderPlacedFish(fish, stack, buffers, light, fit);
        var current = mount;
        if (wall && current != null) {
            var pose = current.pose(); pose.reset();
            stack.scale(1/16F, 1/16F, 1/16F);
            renderer.renderGeometry(stack, buffers, light, current.model(), pose);
        }
        stack.popPose();
    }
}

package com.stardew.craft.client.aquarium;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import com.stardew.craft.port.net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(modid=StardewCraft.MODID, bus=EventBusSubscriber.Bus.MOD, value=Dist.CLIENT)
public final class AquariumItemRendering {
    @SubscribeEvent public static void register(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            private final BlockEntityWithoutLevelRenderer renderer = new BlockEntityWithoutLevelRenderer(
                    Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels()) {
                @Override public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
                        MultiBufferSource buffers, int light, int overlay) {
                    pose.pushPose(); pose.translate(.5, -.75, .5); pose.scale(1/16f, 1/16f, 1/16f);
                    AquariumModels.render("large_fish_tank", false, pose, buffers, light);
                    AquariumModels.render("large_fish_tank", true, pose, buffers, light);
                    pose.popPose();
                }
            };
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() { return renderer; }
        }, ModItems.LARGE_FISH_TANK.get());
    }
}

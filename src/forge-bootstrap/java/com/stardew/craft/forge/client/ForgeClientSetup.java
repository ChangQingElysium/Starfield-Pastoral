package com.stardew.craft.forge.client;

import com.stardew.craft.forge.ForgeBootstrap;
import com.stardew.craft.forge.registry.ForgeBlocks;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client-only render-layer registrations for the Forge tree-core slice. */
@Mod.EventBusSubscriber(modid = ForgeBootstrap.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ForgeClientSetup {
    private ForgeClientSetup() {
    }

    @SubscribeEvent
    @SuppressWarnings("deprecation")
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(ForgeBlocks.OAK_ROOT.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ForgeBlocks.OAK_BRANCH.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ForgeBlocks.OAK_LEAVES.get(), RenderType.cutoutMipped());
            ItemBlockRenderTypes.setRenderLayer(ForgeBlocks.OAK_LEAVES_QUESTION.get(), RenderType.cutoutMipped());
            ItemBlockRenderTypes.setRenderLayer(ForgeBlocks.MAPLE_ROOT.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ForgeBlocks.MAPLE_BRANCH.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ForgeBlocks.MAPLE_LEAVES.get(), RenderType.cutoutMipped());
            ItemBlockRenderTypes.setRenderLayer(ForgeBlocks.PINE_ROOT.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ForgeBlocks.PINE_BRANCH.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ForgeBlocks.PINE_LEAVES.get(), RenderType.cutoutMipped());
            ItemBlockRenderTypes.setRenderLayer(ForgeBlocks.MAHOGANY_ROOT.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ForgeBlocks.MAHOGANY_BRANCH.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ForgeBlocks.MAHOGANY_LEAVES.get(), RenderType.cutoutMipped());
            ItemBlockRenderTypes.setRenderLayer(ForgeBlocks.MYSTIC_TREE_ROOT.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ForgeBlocks.MYSTIC_TREE_BRANCH.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ForgeBlocks.MYSTIC_TREE_LEAVES.get(), RenderType.cutoutMipped());
        });
    }
}

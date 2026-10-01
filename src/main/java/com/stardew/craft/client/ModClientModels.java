package com.stardew.craft.client;

import com.stardew.craft.StardewCraft;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.event.ModelEvent;

@SuppressWarnings("removal")
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ModClientModels {
    private ModClientModels() {}

    @SubscribeEvent
    public static void onRegisterGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        // PORT(1.20.1): Forge takes the loader path and prefixes the active mod namespace (stardewcraft).
        event.register("crop_geometry",
            com.stardew.craft.client.model.CropModelGeometry.LOADER);
        event.register("geometry",
            com.stardew.craft.client.model.ImportedModelGeometry.LOADER);
    }

    @SubscribeEvent
    public static void onRegisterAdditional(ModelEvent.RegisterAdditional event) {
        @SuppressWarnings("null")
        ModelResourceLocation model = new ModelResourceLocation(
            new ResourceLocation(StardewCraft.MODID, "entity/special_effect/ice_spine"),
            "standalone"
        );
        event.register(model);

        registerStandalone(event, "block/utility/incubator_egg");
        registerStandalone(event, "block/utility/incubator_straw_front");
        registerStandalone(event, "block/utility/incubator_straw_back");

        registerStandalone(event, "entity/minecart/empty");
        registerStandalone(event, "entity/minecart/loaded");

        // 炸弹实体 3D 模型
        registerStandalone(event, "entity/bomb/cherry_bomb");
        registerStandalone(event, "entity/bomb/bomb");
        registerStandalone(event, "entity/bomb/mega_bomb");

        for (var id : BuiltInRegistries.ITEM.keySet()) {
            if (!StardewCraft.MODID.equals(id.getNamespace())) {
                continue;
            }
            String path = id.getPath();
            if (!path.startsWith("smoked_")) {
                continue;
            }
            registerSmokedBase(event, path + "_base");
            registerSmokedBase(event, path + "_base_silver");
            registerSmokedBase(event, path + "_base_gold");
            registerSmokedBase(event, path + "_base_iridium");
        }
    }

    private static void registerSmokedBase(ModelEvent.RegisterAdditional event, String path) {
        registerStandalone(event, "item/" + path);
    }

    @SuppressWarnings("null")
    private static void registerStandalone(ModelEvent.RegisterAdditional event, String path) {
        ModelResourceLocation model = new ModelResourceLocation(
            new ResourceLocation(StardewCraft.MODID, path),
            "standalone"
        );
        event.register(model);
    }
}

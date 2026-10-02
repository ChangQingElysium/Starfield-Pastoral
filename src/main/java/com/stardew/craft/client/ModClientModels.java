package com.stardew.craft.client;

import com.stardew.craft.StardewCraft;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

@SuppressWarnings("removal")
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ModClientModels {
    private ModClientModels() {}

    @SubscribeEvent
    public static void onRegisterGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "crop_geometry"),
            com.stardew.craft.client.model.CropModelGeometry.LOADER);
        event.register(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "geometry"),
            com.stardew.craft.client.model.ImportedModelGeometry.LOADER);
    }

    @SubscribeEvent
    public static void onRegisterAdditional(ModelEvent.RegisterAdditional event) {
        @SuppressWarnings("null")
        ModelResourceLocation model = new ModelResourceLocation(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "entity/special_effect/ice_spine"),
            "standalone"
        );
        event.register(model);

        registerStandalone(event, "block/utility/incubator_egg");
        registerStandalone(event, "block/ginger_island/ostrich_incubator_empty");
        registerStandalone(event, "block/ginger_island/ostrich_incubator_loaded");
        registerStandalone(event, "block/utility/fridge_door");
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
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, path),
            "standalone"
        );
        event.register(model);
    }
}

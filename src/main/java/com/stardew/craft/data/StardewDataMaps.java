package com.stardew.craft.data;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.item.StardewItemData;
import com.stardew.craft.api.v1.equipment.StardewEquipmentData;
import com.stardew.craft.api.v1.agriculture.StardewAnimalData;
import com.stardew.craft.api.v1.agriculture.StardewBuildingData;
import com.stardew.craft.api.v1.agriculture.StardewCropData;
import com.stardew.craft.api.v1.agriculture.StardewTreeData;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/** Data Maps owned by the public StardewCraft content API. */
public final class StardewDataMaps {
    public static final DataMapType<Item, StardewItemData> ITEM_DATA = DataMapType.builder(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "stardew_item_data"),
            Registries.ITEM,
            StardewItemData.CODEC
    ).synced(StardewItemData.CODEC, false).build();

    public static final DataMapType<Item, StardewEquipmentData> EQUIPMENT_DATA = DataMapType.builder(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "stardew_equipment_data"),
            Registries.ITEM,
            StardewEquipmentData.CODEC
    ).synced(StardewEquipmentData.CODEC, false).build();

    public static final DataMapType<Block, StardewCropData> CROP_DATA = DataMapType.builder(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "stardew_crop_data"),
            Registries.BLOCK,
            StardewCropData.CODEC
    ).synced(StardewCropData.CODEC, false).build();

    public static final DataMapType<Block, StardewTreeData> TREE_DATA = DataMapType.builder(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "stardew_tree_data"),
            Registries.BLOCK,
            StardewTreeData.CODEC
    ).synced(StardewTreeData.CODEC, false).build();

    public static final DataMapType<EntityType<?>, StardewAnimalData> ANIMAL_DATA = DataMapType.builder(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "stardew_animal_data"),
            Registries.ENTITY_TYPE,
            StardewAnimalData.CODEC
    ).synced(StardewAnimalData.CODEC, false).build();

    public static final DataMapType<Block, StardewBuildingData> BUILDING_DATA = DataMapType.builder(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "stardew_building_data"),
            Registries.BLOCK,
            StardewBuildingData.CODEC
    ).synced(StardewBuildingData.CODEC, false).build();

    private StardewDataMaps() {
    }

    public static void registerDataMaps(RegisterDataMapTypesEvent event) {
        event.register(ITEM_DATA);
        event.register(EQUIPMENT_DATA);
        event.register(CROP_DATA);
        event.register(TREE_DATA);
        event.register(ANIMAL_DATA);
        event.register(BUILDING_DATA);
    }
}

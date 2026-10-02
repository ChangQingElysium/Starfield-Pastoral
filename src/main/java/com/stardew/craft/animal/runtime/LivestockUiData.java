package com.stardew.craft.animal.runtime;

import com.stardew.craft.animal.model.FarmAnimalDefinitions;

import net.minecraft.nbt.CompoundTag;

/** Display metadata travels with server snapshots; clients never infer image dimensions. */
public final class LivestockUiData {
    private LivestockUiData() {}

    public static void describe(CompoundTag row, LivestockSpecies species) {
        row.putString("Species", species.id());
        row.putString("Family", species.family().toString());
        row.putInt("MinTier", species.minimumTier());
        describe(row, species.id());
    }

    public static void describe(CompoundTag row, String id) {
        row.putString("Species", id);
        var definition = FarmAnimalDefinitions.find(id.equals("white_cow") ? "cow" : id);
        if (definition != null) {
            row.putString("NameKey", definition.displayNameKey());
            row.putString("Description", definition.shopDescriptionKey());
            row.putString("DefaultName", definition.defaultName());
            if (definition.shopTextureId() != null) {
                row.putString("Texture", definition.shopTextureId().toString());
                row.putInt("TextureWidth", definition.shopTextureWidth());
                row.putInt("TextureHeight", definition.shopTextureHeight());
            }
        }
        if(definition==null){
            var entry=com.stardew.craft.api.v1.agriculture.StardewAnimalShopEntries.entry(id);
            if(entry!=null){row.putString("NameKey",entry.displayNameKey());row.putString("Description",entry.descriptionKey());row.putString("DefaultName",entry.defaultName());}
        }
        if (!row.contains("Texture")) {
            var fallback = BUILTIN_PORTRAITS.get(id);
            if (fallback != null) {
                row.putString("Texture", fallback.texture());
                row.putInt("TextureWidth", fallback.width());
                row.putInt("TextureHeight", fallback.height());
            }
        }
    }

    private record Portrait(String texture, int width, int height) {}

    private static final java.util.Map<String, Portrait> BUILTIN_PORTRAITS =
            java.util.Map.ofEntries(
                    java.util.Map.entry(
                            "cow",
                            new Portrait(
                                    "stardewcraft:textures/gui/animal_query/icon_cow.png", 32, 32)),
                    java.util.Map.entry(
                            "dinosaur",
                            new Portrait(
                                    "stardewcraft:textures/gui/animal_query/icon_dinosaur.png",
                                    32,
                                    32)),
                    java.util.Map.entry(
                            "duck",
                            new Portrait(
                                    "stardewcraft:textures/gui/animal_query/icon_duck.png",
                                    32,
                                    32)),
                    java.util.Map.entry(
                            "goat",
                            new Portrait(
                                    "stardewcraft:textures/gui/animal_query/icon_goat.png",
                                    32,
                                    32)),
                    java.util.Map.entry(
                            "golden_chicken",
                            new Portrait(
                                    "stardewcraft:textures/gui/animal_query/icon_golden_chicken.png",
                                    32,
                                    32)),
                    java.util.Map.entry(
                            "ostrich",
                            new Portrait(
                                    "stardewcraft:textures/gui/animal_query/icon_ostrich.png",
                                    32,
                                    32)),
                    java.util.Map.entry(
                            "pig",
                            new Portrait(
                                    "stardewcraft:textures/gui/animal_query/icon_pig.png", 32, 32)),
                    java.util.Map.entry(
                            "rabbit",
                            new Portrait(
                                    "stardewcraft:textures/gui/animal_query/icon_rabbit.png",
                                    32,
                                    32)),
                    java.util.Map.entry(
                            "sheep",
                            new Portrait(
                                    "stardewcraft:textures/gui/animal_query/icon_sheep.png",
                                    32,
                                    32)),
                    java.util.Map.entry(
                            "void_chicken",
                            new Portrait(
                                    "stardewcraft:textures/gui/animal_query/icon_void_chicken.png",
                                    32,
                                    32)),
                    java.util.Map.entry(
                            "white_chicken",
                            new Portrait(
                                    "stardewcraft:textures/gui/animal_query/icon_white_chicken.png",
                                    32,
                                    32)));

    public static void care(CompoundTag row, LivestockRecord animal) {
        describe(row, animal.species());
        row.putInt("Age", animal.care().age());
        row.putInt("Friendship", animal.care().friendship());
        row.putInt("Mood", animal.care().happiness());
        row.putInt("MoodMessage", animal.extra().getInt("MoodMessage"));
        row.putInt("Fullness", animal.care().fullness());
        row.putBoolean("Petted", animal.care().petted() || animal.care().autoPetted());
        row.putBoolean("Baby", animal.baby());
    }
}

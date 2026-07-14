package com.stardew.craft.specialorder;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.item.StardewItemDataApi;
import com.stardew.craft.item.quality.QualityHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class SpecialOrderContextTagService {
    private SpecialOrderContextTagService() {
    }

    public static boolean matches(ItemStack stack, String acceptedTags, SpecialOrderInstance order) {
        if (stack.isEmpty()) {
            return false;
        }
        String resolved = SpecialOrderText.resolveRaw(acceptedTags, order);
        if (resolved == null || resolved.isBlank()) {
            return false;
        }
        Set<String> tags = tagsFor(stack);
        for (String andPart : resolved.split(",")) {
            String part = andPart.trim();
            if (part.isEmpty()) {
                continue;
            }
            boolean negated = part.startsWith("!");
            if (negated) {
                part = part.substring(1).trim();
            }
            boolean any = false;
            for (String option : part.split("/")) {
                String tag = option.trim().toLowerCase(Locale.ROOT);
                if (!tag.isBlank() && tags.contains(tag)) {
                    any = true;
                    break;
                }
            }
            if (negated == any) {
                return false;
            }
        }
        return true;
    }

    public static boolean hasWorldTag(String tag, int season, Set<String> mailFlags) {
        if (tag == null || tag.isBlank()) {
            return true;
        }
        boolean negated = tag.startsWith("!");
        String normalized = (negated ? tag.substring(1) : tag).trim();
        boolean result = switch (normalized) {
            case "season_spring" -> season == 0;
            case "season_summer" -> season == 1;
            case "season_fall" -> season == 2;
            case "season_winter" -> season == 3;
            case "event_992559" -> true;
            default -> normalized.startsWith("mail_") && mailFlags.contains(normalized.substring("mail_".length()));
        };
        return negated != result;
    }

    public static String itemId(ItemStack stack) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }

    public static Set<String> tagsFor(ItemStack stack) {
        Set<String> tags = new HashSet<>();
        if (stack.isEmpty()) {
            return tags;
        }
        Item item = stack.getItem();
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
        if (key == null) {
            return tags;
        }
        String namespace = key.getNamespace();
        String path = key.getPath();
        tags.add("item_" + path);
        tags.add(namespace + ":" + path);
        if (StardewCraft.MODID.equals(namespace)) {
            tags.add(path);
        }
        int quality = QualityHelper.getQuality(stack);
        if (quality >= QualityHelper.GOLD) {
            tags.add("quality_gold");
        }
        if (quality >= QualityHelper.SILVER) {
            tags.add("quality_silver");
        }
        if (quality >= QualityHelper.IRIDIUM) {
            tags.add("quality_iridium");
        }
        String type = StardewItemDataApi.getTypeKey(stack);
        if (!type.isBlank()) {
            tags.add(type);
            if (type.endsWith(".crop")) tags.add("category_vegetable");
            if (type.contains("fish")) tags.add("fish_item");
        }
        addAliases(path, tags);
        return tags;
    }

    private static void addAliases(String path, Set<String> tags) {
        switch (path) {
            case "bug_meat" -> tags.add("item_bug_meat");
            case "wood_hard", "hardwood" -> tags.add("item_hardwood");
            case "wood_normal", "wood" -> tags.add("item_wood_normal");
            case "stone" -> tags.add("item_stone");
            case "leek" -> tags.add("item_leek");
            case "ectoplasm" -> tags.add("item_ectoplasm");
            case "prismatic_jelly" -> tags.add("item_prismatic_jelly");
            case "ruby" -> tags.add("item_ruby");
            case "topaz" -> tags.add("item_topaz");
            case "emerald" -> tags.add("item_emerald");
            case "jade" -> tags.add("item_jade");
            case "amethyst" -> tags.add("item_amethyst");
            case "potato_juice" -> {
                tags.add("juice_item");
                tags.add("preserve_sheet_index_192");
            }
            case "egg_white", "egg_brown", "large_egg_white", "large_egg_brown", "duck_egg", "void_egg", "golden_egg", "ostrich_egg" -> tags.add("egg_item");
            case "trash", "driftwood", "broken_glasses", "broken_cd", "soggy_newspaper", "joja_cola" -> tags.add("trash_item");
            case "bone_fragment", "prehistoric_scapula", "prehistoric_tibia", "prehistoric_skull", "skeletal_hand", "skeletal_tail", "nautilus_fossil", "amphibian_fossil", "palm_fossil", "trilobite", "dinosaur_egg", "bone_flute" -> tags.add("bone_item");
            case "sunfish", "catfish", "shad", "tiger_trout", "salmon", "smallmouth_bass" -> tags.add("fish_river");
            case "sardine", "flounder", "halibut", "tilapia", "tuna", "red_mullet", "albacore", "squid" -> tags.add("fish_ocean");
            case "largemouth_bass", "rainbow_trout", "dorado", "midnight_carp", "perch", "lingcod" -> tags.add("fish_lake");
            default -> {
            }
        }
    }
}

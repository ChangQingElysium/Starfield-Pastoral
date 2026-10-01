package com.stardew.craft.item.artisan;

import com.google.gson.JsonParser;
import com.stardew.craft.economy.sell.ProfessionSellPriceService;
import com.stardew.craft.economy.sell.SellSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Original single-output crafting recipes, independent of Minecraft conversion recipes. */
public final class DeconstructorRecipes {
    public record Material(ResourceLocation item, int count, int unitPrice) {}
    private static final Map<ResourceLocation, List<Material>> RECIPES = load();
    private DeconstructorRecipes() {}

    public static java.util.Set<ResourceLocation> inputs() { return RECIPES.keySet(); }

    public static ItemStack output(ItemStack input) {
        return output(input, null);
    }

    public static ItemStack output(ItemStack input, ServerPlayer player) {
        var materials = RECIPES.get(BuiltInRegistries.ITEM.getKey(input.getItem()));
        if (input.isEmpty() || materials == null) return ItemStack.EMPTY;
        Material best = null;
        long bestValue = -1;
        for (var material : materials) {
            int unit = material.unitPrice();
            var item = BuiltInRegistries.ITEM.get(material.item());
            // Apply the same profession multiplier as ordinary sales, to the original base value.
            if (player != null && item != Items.AIR) {
                var quote = ProfessionSellPriceService.quoteItem(player, new ItemStack(item), SellSource.SHOP_COUNTER);
                if (quote.sellable()) unit = (int) (unit * quote.multiplier());
            }
            long value = (long) unit * material.count();
            if (value > bestValue) { best = material; bestValue = value; }
        }
        if (best == null) return ItemStack.EMPTY;
        var item = BuiltInRegistries.ITEM.get(best.item());
        // Never substitute a cheaper material when the actual original output is unavailable.
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, best.count());
    }

    private static Map<ResourceLocation, List<Material>> load() {
        String path = "/data/stardewcraft/machines/deconstruction.json";
        try (var stream = DeconstructorRecipes.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing " + path);
            var root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            var result = new LinkedHashMap<ResourceLocation, List<Material>>();
            for (var element : root.getAsJsonArray("recipes")) {
                var recipe = element.getAsJsonObject();
                var materials = new java.util.ArrayList<Material>();
                for (var entry : recipe.getAsJsonArray("ingredients")) {
                    var material = entry.getAsJsonObject();
                    materials.add(new Material(new ResourceLocation(material.get("item").getAsString()),
                            material.get("count").getAsInt(), material.get("unit_price").getAsInt()));
                }
                result.put(new ResourceLocation(recipe.get("input").getAsString()), List.copyOf(materials));
            }
            return Map.copyOf(result);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Cannot read " + path, exception);
        }
    }
}

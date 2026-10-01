package com.stardew.craft.monster;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import java.util.ArrayList;
import java.util.List;

/** Immutable source data. Constructor-specific transformations belong to the species. */
public record MonsterDefinition(ResourceLocation id, String sourceName, String family,
        int health, int damage, int resilience, float missChance, int experience,
        boolean mineMonster, List<Drop> drops) {
    public MonsterDefinition {
        drops = List.copyOf(drops);
        if (sourceName.isBlank() || family.isBlank() || health < 1 || damage < 0 || resilience < 0
                || !Float.isFinite(missChance) || missChance < 0 || missChance > 1 || experience < 0) {
            throw new IllegalArgumentException("Invalid monster definition: " + id);
        }
    }
    public record Drop(String item, double chance) {
        public Drop(ResourceLocation item, double chance) { this(item.toString(), chance); }
        public Drop {
            if (!item.equals("-4") && !item.equals("-6")) new ResourceLocation(item);
            if (!Double.isFinite(chance) || chance < 0 || chance > 1) throw new IllegalArgumentException("Invalid drop chance");
        }
    }
    public List<String> rollDrops(RandomSource random) {
        var result = new ArrayList<String>();
        for (var drop : drops) if (random.nextDouble() < drop.chance()) result.add(drop.item());
        return List.copyOf(result);
    }
    public static MonsterDefinition parse(ResourceLocation id, JsonObject json) {
        if (GsonHelper.getAsInt(json, "version") != 1) throw new IllegalArgumentException("Unsupported monster schema: " + id);
        var drops = new ArrayList<Drop>();
        for (var element : GsonHelper.getAsJsonArray(json, "drops")) {
            var drop = element.getAsJsonObject();
            drops.add(new Drop(GsonHelper.getAsString(drop, "item"),
                    GsonHelper.getAsDouble(drop, "chance")));
        }
        return new MonsterDefinition(id, GsonHelper.getAsString(json, "source_name"),
                GsonHelper.getAsString(json, "family"), GsonHelper.getAsInt(json, "health"),
                GsonHelper.getAsInt(json, "damage"), GsonHelper.getAsInt(json, "resilience"),
                GsonHelper.getAsFloat(json, "miss_chance"), GsonHelper.getAsInt(json, "experience"),
                GsonHelper.getAsBoolean(json, "mine_monster"), drops);
    }
}

package com.stardew.craft.shop;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.condition.StardewCondition;
import com.stardew.craft.api.v1.condition.StardewConditions;

import java.util.ArrayList;
import java.util.List;

/** Converts authoritative shop conditions into language-neutral client display tokens. */
public final class ShopConditionDisplayData {
    private static final String UNKNOWN = "unknown";

    private ShopConditionDisplayData() {
    }

    public static List<String> tokens(List<StardewCondition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (StardewCondition condition : conditions) {
            String token = token(condition);
            if (token != null && !result.contains(token)) {
                result.add(token);
            }
        }
        return List.copyOf(result);
    }

    private static String token(StardewCondition condition) {
        if (!StardewCraft.MODID.equals(condition.type().getNamespace())) {
            return UNKNOWN;
        }
        JsonElement encoded = StardewConditions.encodeData(condition).result().orElse(null);
        if (encoded == null || !encoded.isJsonObject()) {
            return UNKNOWN;
        }
        JsonObject data = encoded.getAsJsonObject();
        try {
            return switch (condition.type().getPath()) {
                case "always" -> getBoolean(data, "value", true) ? null : "never";
                case "has_item" -> "has_item|" + data.get("item").getAsString()
                        + '|' + getInt(data, "count", 1);
                case "lacks_item" -> "lacks_item|" + data.get("item").getAsString()
                        + '|' + getInt(data, "count", 1);
                case "money" -> "money|" + getInt(data, "min", Integer.MIN_VALUE)
                        + '|' + getInt(data, "max", Integer.MAX_VALUE);
                case "flag" -> "flag|" + getBoolean(data, "present", true);
                case "skill" -> "skill|" + data.get("skill").getAsString()
                        + '|' + data.get("level").getAsInt();
                case "season" -> "season|" + joinStrings(data.getAsJsonArray("seasons"));
                default -> UNKNOWN;
            };
        } catch (RuntimeException ignored) {
            return UNKNOWN;
        }
    }

    private static int getInt(JsonObject data, String key, int fallback) {
        return data.has(key) ? data.get(key).getAsInt() : fallback;
    }

    private static boolean getBoolean(JsonObject data, String key, boolean fallback) {
        return data.has(key) ? data.get(key).getAsBoolean() : fallback;
    }

    private static String joinStrings(JsonArray values) {
        List<String> result = new ArrayList<>();
        for (JsonElement value : values) {
            result.add(value.getAsString());
        }
        return String.join(",", result);
    }
}

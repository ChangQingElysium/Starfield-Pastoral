package com.stardew.craft.cutscene.data;

import com.google.gson.JsonObject;

/**
 * Describes how an event is triggered: enter_area, interact_npc, or time_check.
 */
public record EventTrigger(String type, String location, String npc,
                            double[] areaMin, double[] areaMax, JsonObject raw) {

    public static EventTrigger fromJson(JsonObject obj) {
        String type = obj.get("type").getAsString();
        String location = obj.has("location") ? obj.get("location").getAsString() : null;
        String npc = obj.has("npc") ? obj.get("npc").getAsString() : null;

        double[] areaMin = null;
        double[] areaMax = null;
        // Format A (flat): "area_min": [...], "area_max": [...]
        if (obj.has("area_min") && obj.has("area_max")) {
            areaMin = jsonArrayToDoubles(obj, "area_min");
            areaMax = jsonArrayToDoubles(obj, "area_max");
        }
        // Format B (nested): "area": { "min": [...], "max": [...] }
        else if (obj.has("area")) {
            JsonObject area = obj.getAsJsonObject("area");
            areaMin = jsonArrayToDoubles(area, "min");
            areaMax = jsonArrayToDoubles(area, "max");
        }

        JsonObject raw = obj.deepCopy();
        raw.remove("type");
        return new EventTrigger(type, location, npc, areaMin, areaMax, raw);
    }

    private static double[] jsonArrayToDoubles(JsonObject parent, String key) {
        var arr = parent.getAsJsonArray(key);
        double[] result = new double[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            result[i] = arr.get(i).getAsDouble();
        }
        return result;
    }
}

package com.stardew.craft.npc.runtime;

import com.google.gson.JsonObject;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/** Ordered data-pack rules, evaluated against an explicit shared-world context. */
public final class NpcScheduleRules {
    public record Context(String season,int day,int year,String weather,long seed,int absoluteDay,
                          ToIntFunction<String> hearts,Predicate<String> eventSeen) {}
    private NpcScheduleRules() {}

    public static String select(JsonObject root, Context context) {
        if (!root.has("_selection_rules")) return null;
        for (var entry:root.getAsJsonArray("_selection_rules")) {
            var rule=entry.getAsJsonObject();
            if (!matches(rule,context)) continue;
            String key;
            if (rule.has("choose")) {
                var choices=rule.getAsJsonArray("choose");
                if (choices.isEmpty()) throw new IllegalArgumentException("Empty schedule choices");
                var random=new java.util.Random(context.seed() ^ (context.absoluteDay()*0x9E3779B97F4A7C15L));
                key=choices.get(random.nextInt(choices.size())).getAsString();
            } else key=rule.get("key").getAsString();
            return key.replace("{season}",context.season());
        }
        return null;
    }

    public static boolean matches(JsonObject rule,Context c) {
        if (rule.has("season") && !rule.get("season").getAsString().equals(c.season())) return false;
        if (rule.has("year") && rule.get("year").getAsInt()!=c.year()) return false;
        if (rule.has("days") && rule.getAsJsonArray("days").asList().stream().noneMatch(v->v.getAsInt()==c.day())) return false;
        if (rule.has("weekdays") && rule.getAsJsonArray("weekdays").asList().stream().noneMatch(v->v.getAsInt()==(c.day()-1)%7)) return false;
        if (rule.has("weather_contains") && rule.getAsJsonArray("weather_contains").asList().stream().noneMatch(v->c.weather().contains(v.getAsString()))) return false;
        if (rule.has("any_hearts_min") && rule.getAsJsonObject("any_hearts_min").entrySet().stream()
                .noneMatch(v->c.hearts().applyAsInt(v.getKey())>=v.getValue().getAsInt())) return false;
        return !rule.has("event_seen") || c.eventSeen().test(rule.get("event_seen").getAsString());
    }

    public static void validate(JsonObject root) {
        if (root.has("_point_replacements")) for (var entry : root.getAsJsonArray("_point_replacements")) {
            var rule=entry.getAsJsonObject();
            for (String field : java.util.List.of("from","to","location"))
                if (!rule.has(field) || !rule.get(field).isJsonPrimitive() || rule.get(field).getAsString().isBlank())
                    throw new IllegalArgumentException("Point replacement requires " + field);
            if (!rule.has("facing")) throw new IllegalArgumentException("Point replacement requires facing");
            int facing=rule.get("facing").getAsBigDecimal().intValueExact();
            if (facing<0 || facing>3) throw new IllegalArgumentException("Invalid replacement facing");
            validateConditions(rule,java.util.Set.of("from","to","location","facing"));
        }
        if (!root.has("_selection_rules")) return;
        for (var entry:root.getAsJsonArray("_selection_rules")) {
            var rule=entry.getAsJsonObject();
            validateConditions(rule,java.util.Set.of("key","choose"));
            for (String field:rule.keySet()) if (!java.util.Set.of("season","year","days","weekdays","weather_contains",
                    "any_hearts_min","event_seen","key","choose").contains(field))
                throw new IllegalArgumentException("Unknown schedule rule field: "+field);
            var keys=rule.has("choose") ? rule.getAsJsonArray("choose").asList()
                    : java.util.List.of(rule.get("key"));
            if (keys.isEmpty()) throw new IllegalArgumentException("Empty schedule rule");
            for (var key:keys) {
                String name=key.getAsString();
                if (name.equals("{season}")) {
                    for (String season:java.util.List.of("spring","summer","fall","winter"))
                        requireKey(root,season);
                } else requireKey(root,name);
            }
        }
    }
    private static void validateConditions(JsonObject rule,java.util.Set<String> extra) {
        for (String field : rule.keySet()) {
            if (extra.contains(field)) continue;
            var value=rule.get(field);
            switch(field) {
                case "season" -> {
                    if (!java.util.Set.of("spring","summer","fall","winter").contains(value.getAsString()))
                        throw new IllegalArgumentException("Invalid rule season");
                }
                case "year" -> { if(value.getAsBigDecimal().intValueExact()<1) throw new IllegalArgumentException("Invalid rule year"); }
                case "days", "weekdays" -> {
                    int min=field.equals("days")?1:0,max=field.equals("days")?28:6;
                    if (value.getAsJsonArray().isEmpty()) throw new IllegalArgumentException("Empty rule " + field);
                    for(var item:value.getAsJsonArray()) {
                        int number=item.getAsBigDecimal().intValueExact();
                        if(number<min || number>max) throw new IllegalArgumentException("Invalid rule " + field);
                    }
                }
                case "weather_contains" -> {
                    if(value.getAsJsonArray().isEmpty()) throw new IllegalArgumentException("Empty weather rule");
                    for(var item:value.getAsJsonArray()) if(item.getAsString().isBlank()) throw new IllegalArgumentException("Empty weather token");
                }
                case "any_hearts_min" -> {
                    if(value.getAsJsonObject().size() == 0) throw new IllegalArgumentException("Empty friendship rule");
                    for(var item:value.getAsJsonObject().entrySet())
                        if(item.getKey().isBlank() || item.getValue().getAsBigDecimal().intValueExact()<0)
                            throw new IllegalArgumentException("Invalid friendship rule");
                }
                case "event_seen" -> { if(value.getAsString().isBlank()) throw new IllegalArgumentException("Empty event rule"); }
                default -> throw new IllegalArgumentException("Unknown schedule rule field: " + field);
            }
        }
    }
    private static void requireKey(JsonObject root,String key) {
        if (!root.has(key) || !root.get(key).isJsonObject()) throw new IllegalArgumentException("Missing schedule rule destination: "+key);
    }
}

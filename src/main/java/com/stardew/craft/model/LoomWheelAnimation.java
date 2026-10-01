package com.stardew.craft.model;

import com.google.gson.JsonObject;
import java.util.Comparator;
import java.util.List;

/** Linear rotation track exported from the loom's Blockbench work clip. Units: seconds/degrees. */
public record LoomWheelAnimation(double x, double y, double z, double length, List<Key> keys) {
    public record Key(double time, double degrees) {}

    public static LoomWheelAnimation read(JsonObject wheel, JsonObject animation) {
        var pivot = wheel.getAsJsonArray("pivot");
        var clip = animation.getAsJsonObject("animations").getAsJsonObject("animation.loom.work");
        double length = clip.get("animation_length").getAsDouble();
        var track = clip.getAsJsonObject("bones").getAsJsonObject("rotating_wheel").getAsJsonObject("rotation");
        var keys = track.entrySet().stream().map(entry -> {
            var value = entry.getValue().getAsJsonArray();
            if (value.get(0).getAsDouble() != 0 || value.get(1).getAsDouble() != 0)
                throw new IllegalArgumentException("Loom wheel rotates around Z only");
            return new Key(Double.parseDouble(entry.getKey()), value.get(2).getAsDouble());
        }).sorted(Comparator.comparingDouble(Key::time)).toList();
        if (!Double.isFinite(length) || length <= 0 || keys.size() < 2
                || com.stardew.craft.port.PortJava.getFirst(keys).time() != 0 || com.stardew.craft.port.PortJava.getLast(keys).time() < length - 0.0001
                || keys.stream().anyMatch(k -> !Double.isFinite(k.time()) || !Double.isFinite(k.degrees())))
            throw new IllegalArgumentException("Invalid loom work animation");
        var origin = ModelGeometry.vector(pivot);
        return new LoomWheelAnimation(origin.x / 16.0, origin.y / 16.0, origin.z / 16.0, length, keys);
    }

    public float angle(double seconds) {
        double time = ((seconds % length) + length) % length;
        for (int i = 1; i < keys.size(); i++) {
            Key a = keys.get(i - 1), b = keys.get(i);
            if (time <= b.time()) return (float) (a.degrees()
                + (b.degrees() - a.degrees()) * (time - a.time()) / (b.time() - a.time()));
        }
        return (float) com.stardew.craft.port.PortJava.getLast(keys).degrees();
    }
}

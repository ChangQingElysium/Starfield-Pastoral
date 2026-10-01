package com.stardew.craft.model;

import com.google.gson.JsonObject;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.joml.Vector3f;

/** Linear Blockbench tracks, parsed once on resource reload instead of every rendered frame. */
public final class ReclamationMachineAnimation {
    private record Key(double time, Vector3f value) {}
    private record Clip(double length, boolean loop, Map<String, List<Key>> tracks) {}
    private final Map<String, Vector3f> pivots = new HashMap<>();
    private final Map<String, Clip> clips = new HashMap<>();

    public ReclamationMachineAnimation(JsonObject data) {
        data.getAsJsonObject("pivots").entrySet().forEach(entry -> {
            var p = vector(entry.getValue().getAsJsonArray());
            pivots.put(entry.getKey(), p.add(8, 0, 8).div(16));
        });
        data.getAsJsonObject("animations").entrySet().forEach(entry -> {
            var clip = entry.getValue().getAsJsonObject();
            double length = clip.get("length").getAsDouble();
            if (!Double.isFinite(length) || length <= 0) throw new IllegalArgumentException("Invalid clip duration");
            Map<String, List<Key>> tracks = new HashMap<>();
            clip.getAsJsonObject("bones").entrySet().forEach(bone ->
                bone.getValue().getAsJsonObject().entrySet().forEach(channel -> {
                    var keys = channel.getValue().getAsJsonObject().entrySet().stream()
                        .map(k -> new Key(Double.parseDouble(k.getKey()), vector(k.getValue().getAsJsonArray())))
                        .sorted(Comparator.comparingDouble(Key::time)).toList();
                    if (keys.isEmpty() || keys.stream().anyMatch(k -> !Double.isFinite(k.time()) || !k.value().isFinite()))
                        throw new IllegalArgumentException("Invalid animation keys");
                    tracks.put(bone.getKey() + "/" + channel.getKey(), keys);
                }));
            clips.put(entry.getKey(), new Clip(length, clip.get("loop").getAsBoolean(), Map.copyOf(tracks)));
        });
    }

    private static Vector3f vector(com.google.gson.JsonArray a) {
        return new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
    }

    public Vector3f pivot(String bone) { return new Vector3f(pivots.get(bone)); }

    public Vector3f sample(String clipName, String bone, String channel, double seconds) {
        Clip clip = clips.get(clipName);
        List<Key> keys = clip.tracks().get(bone + "/" + channel);
        if (keys == null) return new Vector3f(channel.equals("scale") ? 1 : 0);
        double time = clip.loop() ? ((seconds % clip.length()) + clip.length()) % clip.length()
                : com.stardew.craft.port.PortJava.clamp(seconds, 0, clip.length());
        if (time <= com.stardew.craft.port.PortJava.getFirst(keys).time()) return new Vector3f(com.stardew.craft.port.PortJava.getFirst(keys).value());
        for (int i = 1; i < keys.size(); i++) {
            Key a = keys.get(i - 1), b = keys.get(i);
            if (time <= b.time()) return new Vector3f(a.value()).lerp(b.value(), (float) ((time - a.time()) / (b.time() - a.time())));
        }
        return new Vector3f(com.stardew.craft.port.PortJava.getLast(keys).value());
    }
}

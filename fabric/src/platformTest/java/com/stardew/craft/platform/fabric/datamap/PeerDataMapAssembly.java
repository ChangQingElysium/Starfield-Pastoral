package com.stardew.craft.platform.fabric.datamap;

import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.Map;

/** Development peer reuses production ordering/bounds/digest; it does not pretend to publish client holders. */
public final class PeerDataMapAssembly {
    private DataMapTransfer.Assembly pending;
    public record Complete(DataMapTransfer.Ack ack, int bytes, Map<String, Integer> mapEntryCounts) {}

    public Complete accept(DataMapTransfer.Chunk chunk) {
        if (pending == null) pending = new DataMapTransfer.Assembly(chunk);
        byte[] bytes = pending.accept(chunk);
        if (bytes == null) return null;
        pending = null;
        var root = JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        if (root.get("version").getAsInt() != 1) throw new IllegalArgumentException("Unknown data map snapshot version");
        Map<String, Integer> maps = new LinkedHashMap<>();
        for (var raw : root.getAsJsonArray("maps")) {
            var map = raw.getAsJsonObject();
            String key = map.get("registry").getAsString() + "/" + map.get("id").getAsString();
            if (maps.putIfAbsent(key, map.getAsJsonObject("values").size()) != null)
                throw new IllegalArgumentException("Duplicate data map " + key);
        }
        return new Complete(new DataMapTransfer.Ack(chunk.nonce(), DataMapSync.digest(bytes)), bytes.length, Map.copyOf(maps));
    }
}

package com.stardew.craft.mining;

import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Approved architecture and original 2D tile semantics; never infer spawn tiles from wall blocks. */
public final class OrdinaryMineLayout {
    public record Cell(int x, int z, boolean reachable, boolean candidate, boolean solid,
                       boolean diggable, int back, String type) {}
    public final String name;
    public final JsonObject metadata;
    public final Vec3i size;
    public final int width, depth, tileX, tileY, tileZ;
    public final Vec3 spawn;
    public final List<Cell> cells;
    private final Set<Long> sightWalls = new HashSet<>();
    private final Map<Long, Cell> byTile = new HashMap<>();

    public static String nameForFloor(int floor) {
        if(floor==SkullCavernRuntime.LOBBY) return "skull_lobby";
        if(floor>120) return SkullCavernRuntime.isForcedTreasure(floor)?"desert_reward_"+(floor-120):"desert_01";
        if (floor < 0) throw new IllegalArgumentException("Ordinary mine floor: " + floor);
        return floor == 0 ? "earth_lobby" : "earth_" + String.format(Locale.ROOT, "%02d", switch (floor) {
            case 30 -> 10; case 50 -> 40; case 90 -> 80; default -> floor;
        });
    }

    private static final Map<ServerLevel,Map<String,OrdinaryMineLayout>> CACHE=new WeakHashMap<>();
    public static void clearCache() { CACHE.clear(); }
    public static OrdinaryMineLayout load(ServerLevel level, int floor) {
        var data=MineFloorDataManager.get(level).getFloorData(floor);
        return loadNamed(level,data!=null && !data.getLayoutName().isEmpty()?data.getLayoutName():nameForFloor(floor));
    }
    public static OrdinaryMineLayout loadNamed(ServerLevel level, String name) {
        var cache=CACHE.computeIfAbsent(level,k->new HashMap<>());
        if(cache.containsKey(name)) return cache.get(name);
        var id = new ResourceLocation("stardewcraft", "mine_layouts/" + name + ".json");
        try (var reader = new InputStreamReader(level.getServer().getResourceManager().getResourceOrThrow(id).open(), StandardCharsets.UTF_8)) {
            var layout=new OrdinaryMineLayout(name, JsonParser.parseReader(reader).getAsJsonObject());
            cache.put(name,layout);return layout;
        } catch (IOException e) { throw new IllegalStateException("Missing approved mine layout " + id, e); }
    }

    public OrdinaryMineLayout(String name, JsonObject data) {
        this.name = name; metadata = data;
        var s = data.getAsJsonArray("size"); size = new Vec3i(s.get(0).getAsInt(), s.get(1).getAsInt(), s.get(2).getAsInt());
        var bounds = data.getAsJsonArray("source_size"); width = bounds.get(0).getAsInt(); depth = bounds.get(1).getAsInt();
        var o = data.getAsJsonArray("tile_origin"); tileX = o.get(0).getAsInt(); tileY = o.get(1).getAsInt(); tileZ = o.get(2).getAsInt();
        var p = data.getAsJsonArray("spawn_relative"); spawn = new Vec3(p.get(0).getAsDouble(), p.get(1).getAsDouble(), p.get(2).getAsDouble());
        var list = new ArrayList<Cell>();
        for (var entry : data.getAsJsonArray("cells")) {
            var c = entry.getAsJsonObject(); var t = c.getAsJsonArray("tile"); var layers = c.getAsJsonObject("source_tiles");
            String type = c.has("back_type") && !c.get("back_type").isJsonNull() ? c.get("back_type").getAsString() : "";
            Cell cell = new Cell(t.get(0).getAsInt(), t.get(1).getAsInt(), bool(c,"reachable"), bool(c,"static_object_candidate"),
                    bool(c,"ground_open"), bool(c,"diggable") || type.equals("Dirt"), layers.get("Back").getAsInt(), type);
            list.add(cell); byTile.put(key(cell.x,cell.z),cell);
            if (layers.has("Buildings") && !layers.get("Buildings").isJsonNull() && layers.get("Buildings").getAsInt() >= 0) sightWalls.add(key(cell.x,cell.z));
        }
        // MineShaft.populateLevel iterates X outside, Y inside.
        list.sort(Comparator.comparingInt(Cell::x).thenComparingInt(Cell::z)); cells = List.copyOf(list);
    }
    private static boolean bool(JsonObject o, String key) { return o.has(key) && o.get(key).getAsBoolean(); }
    private static long key(int x, int z) { return ((long)x << 32) ^ (z & 0xffffffffL); }
    public boolean blocksSight(int x,int z) { return sightWalls.contains(key(x,z)); }
    public Cell cell(int x, int z) { return byTile.get(key(x,z)); }
    public BlockPos origin(int floor) {
        int z = floor == 0 ? 0 : floor * MiningCoordinates.FLOOR_SPACING + 14;
        return new BlockPos(-(int)Math.floor(spawn.x), MiningCoordinates.FIXED_Y - tileY, z - (int)Math.floor(spawn.z));
    }
    public BlockPos position(int floor, int x, int z) { return origin(floor).offset(tileX+x,tileY,tileZ+z); }
    public Vec3 spawn(int floor) { return Vec3.atLowerCornerOf(origin(floor)).add(spawn); }
    public double distanceFromEntry(int x, int z) {
        var t = metadata.getAsJsonArray("entry_tile");
        double distance = Math.hypot(x-t.get(0).getAsInt(),z-t.get(1).getAsInt());
        if (metadata.has("elevators")) for (var e : metadata.getAsJsonArray("elevators")) {
            var p = e.getAsJsonObject().getAsJsonArray("anchor_tile");
            distance = Math.min(distance,Math.hypot(x-p.get(0).getAsInt(),z-p.get(1).getAsInt()-1));
        }
        return distance;
    }
}

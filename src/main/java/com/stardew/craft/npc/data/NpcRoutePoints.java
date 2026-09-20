package com.stardew.craft.npc.data;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import java.util.LinkedHashMap;
import java.util.Map;

/** Validated, namespaced point documents. Callers receive private copies of individual points. */
public final class NpcRoutePoints {
    private static long revision=-1;
    private static Map<String,JsonObject> points=Map.of();
    private NpcRoutePoints() {}

    public static String canonical(String id) {
        if(id==null || id.isBlank()) return "";
        String value=id.trim().toLowerCase(java.util.Locale.ROOT);
        return value.contains(":")?value:"stardewcraft:"+value;
    }
    public static JsonObject get(String id) {
        if(revision!=NpcDataRegistry.revision()) {
            points=compile(NpcDataRegistry.events()); revision=NpcDataRegistry.revision();
        }
        var point=points.get(canonical(id));
        return point==null?null:point.deepCopy();
    }
    public static Map<String,JsonObject> compile(Map<String,JsonObject> events) {
        Map<String,JsonObject> result=new LinkedHashMap<>();
        for(var event:events.entrySet()) {
            if(!event.getKey().equals("npc_route_points") && !event.getKey().endsWith(":npc_route_points")) continue;
            String namespace=event.getKey().contains(":")?event.getKey().split(":",2)[0]:"stardewcraft";
            var root=event.getValue().getAsJsonObject("points");
            if(root==null) throw new IllegalArgumentException(event.getKey()+": missing points");
            for(var entry:root.entrySet()) {
                if(entry.getKey().startsWith("_comment")) continue;
                String id=canonical(entry.getKey().contains(":")?entry.getKey():namespace+":"+entry.getKey());
                try {
                    if(ResourceLocation.tryParse(id)==null) throw new IllegalArgumentException("Invalid point id");
                    var p=entry.getValue().getAsJsonObject().deepCopy();
                    // Existing Sam point data names the chair model rather than the support role.
                    if(p.has("furniture") && p.get("furniture").getAsString().equals("wooden_dining_chair")) p.addProperty("furniture","chair");
                    int axes=0;
                    for(String axis:java.util.List.of("x","y","z")) if(p.has(axis)) {finite(p,axis);axes++;}
                    if(axes!=0 && axes!=3) throw new IllegalArgumentException("Coordinates require x/y/z together");
                    if (p.has("arrival") && (!p.get("arrival").isJsonPrimitive()
                            || !p.getAsJsonPrimitive("arrival").isString()
                            || !java.util.Set.of("nearest_reachable", "exact_work").contains(p.get("arrival").getAsString())))
                        throw new IllegalArgumentException("Invalid arrival policy");
                    if(p.has("yaw")) finite(p,"yaw");
                    if(p.has("square_area"))com.stardew.craft.npc.runtime.NpcSquareArea.decode(p.getAsJsonObject("square_area"));
                    if(p.has("approach_yaw_offset")) finite(p,"approach_yaw_offset");
                    if(p.has("dimension") && ResourceLocation.tryParse(p.get("dimension").getAsString())==null)
                        throw new IllegalArgumentException("Invalid dimension");
                    for(String flag:java.util.List.of("indoor","use_ground_height","preserve_bed_side")) if(p.has(flag)
                            && (!p.get(flag).isJsonPrimitive() || !p.getAsJsonPrimitive(flag).isBoolean()))
                        throw new IllegalArgumentException("Invalid boolean "+flag);
                    for(String offset:java.util.List.of("origin_offset","approach_offset","seat_span")) if(p.has(offset)) {
                        var v=p.getAsJsonArray(offset);
                        if(v.size()!=3) throw new IllegalArgumentException(offset+" requires three coordinates");
                        double length=0;
                        for(var coordinate:v) {double n=coordinate.getAsDouble();length+=n*n;}
                        if(!Double.isFinite(length) || length>16) throw new IllegalArgumentException("Invalid "+offset);
                        if(offset.equals("seat_span")) {
                            for(var coordinate:v)coordinate.getAsBigDecimal().intValueExact();
                            if(v.get(1).getAsInt()!=0 || Math.abs(v.get(0).getAsInt())+Math.abs(v.get(2).getAsInt())!=1)
                                throw new IllegalArgumentException("seat_span requires one horizontal neighboring block");
                        }
                    }
                    // Addons can supply their own support kinds through the support provider API.
                    if(p.has("furniture") && ResourceLocation.tryParse(p.get("furniture").getAsString())==null)
                        throw new IllegalArgumentException("Invalid furniture kind");
                    if(p.has("workstation")) {
                        var w=p.getAsJsonObject("workstation");
                        for(String axis:java.util.List.of("x","y","z")) w.get(axis).getAsBigDecimal().intValueExact();
                        if(ResourceLocation.tryParse(w.get("block").getAsString())==null || w.get("clip").getAsString().isBlank())
                            throw new IllegalArgumentException("Invalid workstation identity");
                    }
                    if(p.has("activity_target")) {
                        var target=p.getAsJsonObject("activity_target");
                        for(String axis:java.util.List.of("x","y","z"))target.get(axis).getAsBigDecimal().intValueExact();
                        finite(target,"surface_y");
                        if(target.get("surface_y").getAsDouble()<0 || target.get("surface_y").getAsDouble()>2)
                            throw new IllegalArgumentException("Invalid activity target height");
                    }
                    if(result.putIfAbsent(id,p.deepCopy())!=null) throw new IllegalArgumentException("Duplicate canonical point");
                } catch(RuntimeException error) {throw new IllegalArgumentException(id+": "+error.getMessage(),error);}
            }
        }
        return java.util.Collections.unmodifiableMap(result);
    }
    private static void finite(JsonObject p,String field) {
        if(!Double.isFinite(p.get(field).getAsDouble())) throw new IllegalArgumentException(field+" must be finite");
    }
}

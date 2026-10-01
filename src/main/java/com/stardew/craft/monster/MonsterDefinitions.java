package com.stardew.craft.monster;

import com.google.gson.JsonParser;
import com.stardew.craft.StardewCraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.event.AddReloadListenerEvent;
import java.util.LinkedHashMap;
import java.util.Map;

/** A failed reload retains the last complete snapshot; existing instances retain their own rolls. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class MonsterDefinitions {
    public static final ResourceLocation GREEN_SLIME = new ResourceLocation(StardewCraft.MODID, "green_slime");
    public static final ResourceLocation FROST_JELLY = new ResourceLocation(StardewCraft.MODID, "frost_jelly");
    public static final ResourceLocation SLUDGE = new ResourceLocation(StardewCraft.MODID, "sludge");
    public static final ResourceLocation BAT = new ResourceLocation(StardewCraft.MODID, "bat");
    private static volatile Map<ResourceLocation, MonsterDefinition> definitions = Map.of();
    private MonsterDefinitions() {}
    public static MonsterDefinition require(ResourceLocation id) {
        var result = definitions.get(id);
        if (result == null) throw new IllegalStateException("Monster definition is not loaded: " + id);
        return result;
    }
    @SubscribeEvent public static void reload(AddReloadListenerEvent event) { event.addListener(new Loader()); event.addListener(new MonsterSourceLoot.Reload()); }
    private record Prepared(Map<ResourceLocation, MonsterDefinition> entries, RuntimeException failure) {}
    private static final class Loader extends SimplePreparableReloadListener<Prepared> {
        @Override protected Prepared prepare(ResourceManager manager, ProfilerFiller profiler) {
            try {
                var converter = FileToIdConverter.json("monsters");
                var result = new LinkedHashMap<ResourceLocation, MonsterDefinition>();
                for (var entry : converter.listMatchingResources(manager).entrySet()) {
                    try (var reader = entry.getValue().openAsReader()) {
                        var id = converter.fileToId(entry.getKey());
                        result.put(id, MonsterDefinition.parse(id, JsonParser.parseReader(reader).getAsJsonObject()));
                    } catch (java.io.IOException exception) { throw new IllegalArgumentException(entry.getKey().toString(), exception); }
                }
                for (var id : java.util.List.of(GREEN_SLIME, FROST_JELLY, SLUDGE))
                    if (!result.containsKey(id) || !result.get(id).family().equals("slime"))
                        throw new IllegalArgumentException("Missing/invalid slime definition: " + id);
                for (String name : java.util.List.of("bat", "frost_bat", "lava_bat", "iridium_bat")) {
                    var id = new ResourceLocation(StardewCraft.MODID, name);
                    if (!result.containsKey(id) || !result.get(id).family().equals("bat"))
                        throw new IllegalArgumentException("Missing/invalid bat definition: " + id);
                }
                for (String family : java.util.List.of("grub", "fly", "duggy", "dust_sprite", "ghost", "carbon_ghost", "skeleton", "rock_golem", "metal_head", "shadow_brute", "shadow_shaman", "squid_kid", "big_slime", "mummy", "serpent", "pepper_rex")) {
                    var id=new ResourceLocation(StardewCraft.MODID,family);
                    if(!result.containsKey(id)||!result.get(id).family().equals(family))
                        throw new IllegalArgumentException("Missing/invalid monster definition: "+id);
                }
                for(String farm:java.util.List.of("wilderness_golem","iridium_golem")) {
                    var id=new ResourceLocation(StardewCraft.MODID,farm);
                    if(!result.containsKey(id)||!result.get(id).family().equals("rock_golem"))throw new IllegalArgumentException("Missing/invalid farm golem: "+id);
                }
                for(String bug:java.util.List.of("bug","armored_bug")) {
                    var id=new ResourceLocation(StardewCraft.MODID,bug);
                    if(!result.containsKey(id)||!result.get(id).family().equals("bug"))throw new IllegalArgumentException("Missing/invalid bug definition: "+id);
                }
                for(String crab:java.util.List.of("rock_crab","lava_crab","iridium_crab")) {
                    var id=new ResourceLocation(StardewCraft.MODID,crab);
                    if(!result.containsKey(id)||!result.get(id).family().equals("rock_crab"))
                        throw new IllegalArgumentException("Missing/invalid crab definition: "+id);
                }
                for (var definition : result.values()) for (var drop : definition.drops()) {
                    if (!java.util.Set.of("-4", "-6").contains(drop.item()) && !BuiltInRegistries.ITEM.containsKey(new ResourceLocation(drop.item()))) throw new IllegalArgumentException("Unknown monster drop: " + drop.item());
                }
                return new Prepared(Map.copyOf(result), null);
            } catch (RuntimeException failure) { return new Prepared(Map.of(), failure); }
        }
        @Override protected void apply(Prepared prepared, ResourceManager manager, ProfilerFiller profiler) {
            if (prepared.failure() != null) {
                if (definitions.isEmpty()) throw prepared.failure();
                StardewCraft.LOGGER.error("Monster data reload rejected; retaining previous snapshot", prepared.failure());
            } else definitions = prepared.entries();
        }
    }
}

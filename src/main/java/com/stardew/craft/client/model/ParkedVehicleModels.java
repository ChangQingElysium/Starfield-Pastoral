package com.stardew.craft.client.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.ParkedVehicleBlock;
import com.stardew.craft.client.model.terrain.TerrainSeasonTextures;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;

/** Native JSON assemblies baked on reload; season swaps never change the placed structure. */
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ParkedVehicleModels {
    private static final String[] SEASONS = {"spring", "summer", "fall", "winter"};
    private static final String[] VEHICLES = {"bus", "mayor_pickup", "joja_truck"};
    private static volatile Map<String, List<List<Part>>> vehicles = Map.of();
    private ParkedVehicleModels() {}
    public record Part(String group, boolean glass, double x, double y, double z, List<BakedQuad> quads) {}
    public static List<Part> parts(String vehicle, int season) {
        var variants = vehicles.get(vehicle);
        return variants == null ? List.of() : variants.get(Math.clamp(season, 0, variants.size() - 1));
    }
    private static JsonObject description(String vehicle, String season) {
        var id = new ResourceLocation(StardewCraft.MODID, "vehicles/" + vehicle + "/" + season + ".json");
        try (var reader = new InputStreamReader(Minecraft.getInstance().getResourceManager().getResourceOrThrow(id).open(), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException exception) { throw new IllegalStateException("Cannot load parked vehicle " + id, exception); }
    }
    private static ModelResourceLocation id(String path) { return new ModelResourceLocation(new ResourceLocation(path), "standalone"); }
    @SubscribeEvent public static void register(ModelEvent.RegisterAdditional event) {
        var ids = new HashSet<ModelResourceLocation>();
        for (String vehicle : VEHICLES) for (String season : vehicle.equals("bus") ? new String[]{"spring"} : SEASONS) {
            ids.add(id("stardewcraft:block/vehicles/" + vehicle + "/" + season + "/empty"));
            for (var e : description(vehicle, season).getAsJsonArray("parts")) ids.add(id(e.getAsJsonObject().get("model").getAsString()));
        }
        ids.forEach(event::register);
    }
    @SubscribeEvent public static void bake(ModelEvent.ModifyBakingResult event) {
        var result = new HashMap<String, List<List<Part>>>();
        var particleMap = new HashMap<String, TextureAtlasSprite[]>();
        for (String vehicle : VEHICLES) {
            int count = vehicle.equals("bus") ? 1 : 4;
            var seasons = new ArrayList<List<Part>>(); var particles = new TextureAtlasSprite[count];
            for (int s = 0; s < count; s++) {
                var parts = new ArrayList<Part>();
                particles[s] = Objects.requireNonNull(event.getModels().get(id("stardewcraft:block/vehicles/" + vehicle + "/" + SEASONS[s] + "/empty"))).getParticleIcon();
                for (var entry : description(vehicle, SEASONS[s]).getAsJsonArray("parts")) {
                    var part = entry.getAsJsonObject(); var offset = part.getAsJsonArray("scene_offset");
                    var model = Objects.requireNonNull(event.getModels().get(id(part.get("model").getAsString())));
                    parts.add(new Part(part.get("group").getAsString(), part.get("material").getAsString().equals("glass"),
                            offset.get(0).getAsDouble(), offset.get(1).getAsDouble(), offset.get(2).getAsDouble(),
                            List.copyOf(model.getQuads(null, null, RandomSource.create(0)))));
                }
                seasons.add(List.copyOf(parts));
            }
            result.put(vehicle, List.copyOf(seasons)); particleMap.put(vehicle, particles);
        }
        vehicles = Map.copyOf(result);
        for (var holder : List.of(ModBlocks.BUS, ModBlocks.MAYOR_PICKUP, ModBlocks.JOJA_TRUCK)) {
            var block = (ParkedVehicleBlock) holder.get(); var particles = particleMap.get(block.assetId());
            for (var state : block.getStateDefinition().getPossibleStates()) {
                var key = BlockModelShaper.stateToModelLocation(state);
                event.getModels().put(key, new ParticleModel(Objects.requireNonNull(event.getModels().get(key)), particles));
            }
        }
    }
    private static final class ParticleModel extends BakedModelWrapper<BakedModel> {
        private final TextureAtlasSprite[] particles;
        ParticleModel(BakedModel original, TextureAtlasSprite[] particles) { super(original); this.particles = particles; }
        @Override public TextureAtlasSprite getParticleIcon() { return particles[particles.length == 1 ? 0 : TerrainSeasonTextures.currentTextureSet()]; }
        @Override public TextureAtlasSprite getParticleIcon(ModelData data) { return getParticleIcon(); }
    }
}

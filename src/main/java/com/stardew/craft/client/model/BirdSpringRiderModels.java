package com.stardew.craft.client.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.model.terrain.TerrainSeasonTextures;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.ModelEvent;

/** Native Java cuboids; seasonal materials and runtime transforms remain independent. */
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BirdSpringRiderModels {
    private static final String[] SEASONS = {"spring", "summer", "fall", "winter"};
    private static volatile List<List<Part>> assemblies = List.of();
    private BirdSpringRiderModels() {}
    public record Part(double x, double y, double z, double flex, List<BakedQuad> quads) {}
    public static List<Part> parts() {
        return assemblies.isEmpty() ? List.of() : assemblies.get(TerrainSeasonTextures.currentTextureSet());
    }
    private static JsonObject description() {
        var id = new ResourceLocation(StardewCraft.MODID, "playground/bird_spring_rider.json");
        try (var reader = new InputStreamReader(Minecraft.getInstance().getResourceManager().getResourceOrThrow(id).open(), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) { throw new IllegalStateException("Cannot load bird spring rider", e); }
    }
    private static ModelResourceLocation id(String season, String part) {
        return new ModelResourceLocation(new ResourceLocation(StardewCraft.MODID,
                "block/bird_spring_rider/" + season + "/" + part), "standalone");
    }
    @SubscribeEvent public static void register(ModelEvent.RegisterAdditional event) {
        for (String season : SEASONS) for (var entry : description().getAsJsonArray("parts"))
            event.register(id(season, entry.getAsJsonObject().get("part").getAsString()));
    }
    @SubscribeEvent public static void bake(ModelEvent.ModifyBakingResult event) {
        var result = new ArrayList<List<Part>>();
        for (String season : SEASONS) {
            var parts = new ArrayList<Part>();
            for (var entry : description().getAsJsonArray("parts")) {
                var p = entry.getAsJsonObject(); var offset = p.getAsJsonArray("offset");
                var model = Objects.requireNonNull(event.getModels().get(id(season, p.get("part").getAsString())));
                parts.add(new Part(offset.get(0).getAsDouble(), offset.get(1).getAsDouble(), offset.get(2).getAsDouble(),
                        p.get("flex").getAsDouble(), List.copyOf(model.getQuads(null, null, RandomSource.create(0)))));
            }
            result.add(List.copyOf(parts));
        }
        assemblies = List.copyOf(result);
    }
}

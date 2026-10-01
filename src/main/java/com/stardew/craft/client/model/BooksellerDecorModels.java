package com.stardew.craft.client.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stardew.craft.StardewCraft;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

/** Native Java model parts, baked once per resource reload. Original art is season independent. */
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BooksellerDecorModels {
    private static final String[] ASSETS = {"bookseller_stall", "bookseller_balloon"};
    private static volatile Map<String, List<Part>> models = Map.of();
    private BooksellerDecorModels() {}
    public record Part(double x, double y, double z, boolean emissive, List<BakedQuad> quads) {}
    public static List<Part> parts(String asset) { return models.getOrDefault(asset, List.of()); }
    private static JsonObject description(String asset) {
        var id = new ResourceLocation(StardewCraft.MODID, "bookseller/" + asset + ".json");
        try (var reader = new InputStreamReader(Minecraft.getInstance().getResourceManager().getResourceOrThrow(id).open(), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException exception) { throw new IllegalStateException("Cannot load bookseller decoration " + id, exception); }
    }
    private static ModelResourceLocation id(String path) { return new ModelResourceLocation(new ResourceLocation(path), "standalone"); }
    @SubscribeEvent public static void register(ModelEvent.RegisterAdditional event) {
        for (String asset : ASSETS)
            for (var entry : description(asset).getAsJsonArray("parts")) event.register(id(entry.getAsJsonObject().get("model").getAsString()));
    }
    @SubscribeEvent public static void bake(ModelEvent.ModifyBakingResult event) {
        var result = new HashMap<String, List<Part>>();
        for (String asset : ASSETS) {
            var parts = new ArrayList<Part>();
            for (var entry : description(asset).getAsJsonArray("parts")) {
                var part = entry.getAsJsonObject(); var offset = part.getAsJsonArray("scene_offset");
                var model = Objects.requireNonNull(event.getModels().get(id(part.get("model").getAsString())));
                parts.add(new Part(offset.get(0).getAsDouble(), offset.get(1).getAsDouble(), offset.get(2).getAsDouble(),
                        part.get("emissive").getAsBoolean(), List.copyOf(model.getQuads(null, null, RandomSource.create(0)))));
            }
            result.put(asset, List.copyOf(parts));
        }
        models = Map.copyOf(result);
    }
}

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
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.event.ModelEvent;

/** The approved Java JSON parts are baked normally; no custom geometry format. */
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PlaygroundModels {
    private static final String[] NAMES = {"playground_slide", "climbing_frame"};
    private static volatile Map<String, List<Part>> assemblies = Map.of();
    private PlaygroundModels() {}
    public record Part(double x, double y, double z, List<BakedQuad> quads) {}
    public static List<Part> parts(String name) { return assemblies.getOrDefault(name, List.of()); }

    private static JsonObject description(String name) {
        var id = new ResourceLocation(StardewCraft.MODID, "playground/" + name + ".json");
        try (var reader = new InputStreamReader(Minecraft.getInstance().getResourceManager().getResourceOrThrow(id).open(), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException exception) { throw new IllegalStateException("Cannot load playground assembly " + name, exception); }
    }
    private static ModelResourceLocation id(String path) {
        return new ModelResourceLocation(new ResourceLocation(path), "standalone");
    }
    @SubscribeEvent public static void register(ModelEvent.RegisterAdditional event) {
        for (String name : NAMES) for (var entry : description(name).getAsJsonArray("parts"))
            event.register(id(entry.getAsJsonObject().get("model").getAsString()));
    }
    @SubscribeEvent public static void bake(ModelEvent.ModifyBakingResult event) {
        var result = new HashMap<String, List<Part>>();
        for (String name : NAMES) {
            var parts = new ArrayList<Part>();
            for (var entry : description(name).getAsJsonArray("parts")) {
                var part = entry.getAsJsonObject();
                var model = Objects.requireNonNull(event.getModels().get(id(part.get("model").getAsString())));
                var offset = part.getAsJsonArray("offset");
                parts.add(new Part(offset.get(0).getAsDouble(), offset.get(1).getAsDouble(), offset.get(2).getAsDouble(),
                        List.copyOf(model.getQuads(null, null, RandomSource.create(0)))));
            }
            result.put(name, List.copyOf(parts));
        }
        assemblies = Map.copyOf(result);
    }
}

package com.stardew.craft.client.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.client.model.terrain.TerrainSeasonTextures;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;

@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class JojaBillboardModels {
    private static final String[] SEASONS = {"spring", "summer", "fall", "winter"};
    private static volatile List<List<Part>> seasons = List.of();
    private JojaBillboardModels() {}
    public record Part(double x, double y, double z, List<BakedQuad> quads) {}
    public static List<Part> parts(int season) { return seasons.isEmpty() ? List.of() : seasons.get(com.stardew.craft.port.PortJava.clamp(season, 0, 3)); }
    private static JsonObject description(String season) {
        var id = new ResourceLocation(StardewCraft.MODID, "joja_billboard/" + season + ".json");
        try (var reader = new InputStreamReader(Minecraft.getInstance().getResourceManager().getResourceOrThrow(id).open(), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException exception) { throw new IllegalStateException("Cannot load Joja billboard " + id, exception); }
    }
    private static ModelResourceLocation id(String path) { return new ModelResourceLocation(new ResourceLocation(path), "standalone"); }
    @SubscribeEvent public static void register(ModelEvent.RegisterAdditional event) {
        for (String season : SEASONS) {
            event.register(id("stardewcraft:block/joja_billboard/" + season + "/empty"));
            for (var entry : description(season).getAsJsonArray("parts")) event.register(id(entry.getAsJsonObject().get("model").getAsString()));
        }
    }
    @SubscribeEvent public static void bake(ModelEvent.ModifyBakingResult event) {
        var result = new ArrayList<List<Part>>(); var particles = new TextureAtlasSprite[4];
        for (int s = 0; s < 4; s++) {
            var parts = new ArrayList<Part>();
            particles[s] = Objects.requireNonNull(event.getModels().get(id("stardewcraft:block/joja_billboard/" + SEASONS[s] + "/empty"))).getParticleIcon();
            for (var entry : description(SEASONS[s]).getAsJsonArray("parts")) {
                var part = entry.getAsJsonObject(); var offset = part.getAsJsonArray("scene_offset");
                var model = Objects.requireNonNull(event.getModels().get(id(part.get("model").getAsString())));
                parts.add(new Part(offset.get(0).getAsDouble(), offset.get(1).getAsDouble(), offset.get(2).getAsDouble(),
                        List.copyOf(model.getQuads(null, null, RandomSource.create(0)))));
            }
            result.add(List.copyOf(parts));
        }
        seasons = List.copyOf(result);
        for (var state : ModBlocks.JOJA_BILLBOARD.get().getStateDefinition().getPossibleStates()) {
            var key = BlockModelShaper.stateToModelLocation(state);
            event.getModels().put(key, new ParticleModel(Objects.requireNonNull(event.getModels().get(key)), particles));
        }
    }
    private static final class ParticleModel extends BakedModelWrapper<BakedModel> {
        private final TextureAtlasSprite[] particles;
        ParticleModel(BakedModel original, TextureAtlasSprite[] particles) { super(original); this.particles = particles; }
        @Override public TextureAtlasSprite getParticleIcon() { return particles[TerrainSeasonTextures.currentTextureSet()]; }
        @Override public TextureAtlasSprite getParticleIcon(ModelData data) { return getParticleIcon(); }
    }
}

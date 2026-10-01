package com.stardew.craft.client.model;

import com.google.gson.JsonParser;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.model.ReclamationMachineAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.ModelEvent;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ReclamationMachineModels {
    private static final Map<String, ModelResourceLocation> PARTS = java.util.stream.Stream.of(
        "deconstructor", "wood_chipper_stationary_housing", "wood_chipper_feed_apron", "wood_chipper_rotating_blades")
        .collect(java.util.stream.Collectors.toUnmodifiableMap(n -> n, n -> new ModelResourceLocation(
            new ResourceLocation(StardewCraft.MODID, "block/utility/" + n), "standalone")));
    private static final Map<String, ReclamationMachineAnimation> ANIMATIONS = new java.util.HashMap<>();
    private ReclamationMachineModels() {}

    @SubscribeEvent
    public static void register(ModelEvent.RegisterAdditional event) {
        PARTS.values().forEach(event::register);
        for (String name : new String[]{"deconstructor", "wood_chipper"}) {
            var id = new ResourceLocation(StardewCraft.MODID, "animations/utility/" + name + ".json");
            try (var reader = new InputStreamReader(Minecraft.getInstance().getResourceManager().getResourceOrThrow(id).open(), StandardCharsets.UTF_8)) {
                ANIMATIONS.put(name, new ReclamationMachineAnimation(JsonParser.parseReader(reader).getAsJsonObject()));
            } catch (java.io.IOException exception) { throw new IllegalStateException("Cannot load " + id, exception); }
        }
    }

    public static BakedModel part(String name) { return Minecraft.getInstance().getModelManager().getModel(PARTS.get(name)); }
    public static ReclamationMachineAnimation motion(String name) { return ANIMATIONS.get(name); }
}

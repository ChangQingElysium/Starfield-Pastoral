package com.stardew.craft.client.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.model.LoomWheelAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.event.ModelEvent;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Approved generic loom, baked with the existing imported geometry loader. */
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LoomModels {
    private static final ModelResourceLocation FRAME = id("loom_working_frame");
    private static final ModelResourceLocation WHEEL = id("loom_working_wheel");
    private static volatile LoomWheelAnimation motion;

    private LoomModels() {}

    private static ModelResourceLocation id(String name) {
        return new ModelResourceLocation(new ResourceLocation(StardewCraft.MODID,
            "block/utility/" + name), "standalone");
    }

    @SubscribeEvent
    public static void register(ModelEvent.RegisterAdditional event) {
        event.register(FRAME);
        event.register(WHEEL);
        var wheel = read(new ResourceLocation(StardewCraft.MODID,
            "models/block/utility/loom_working_wheel.json")).getAsJsonObject("stardewcraft:wheel");
        motion = LoomWheelAnimation.read(wheel, read(new ResourceLocation(wheel.get("animation").getAsString())));
    }

    private static JsonObject read(ResourceLocation id) {
        try (var reader = new InputStreamReader(Minecraft.getInstance().getResourceManager()
                .getResourceOrThrow(id).open(), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load loom animation resource " + id, exception);
        }
    }

    public static BakedModel frame() { return Minecraft.getInstance().getModelManager().getModel(FRAME); }
    public static BakedModel wheel() { return Minecraft.getInstance().getModelManager().getModel(WHEEL); }
    public static LoomWheelAnimation motion() { return motion; }
}

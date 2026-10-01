package com.stardew.craft.client.model.nativebb;

import com.google.gson.JsonParser;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.npcnative.NativeNpcModel;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import java.util.*;

@EventBusSubscriber(modid=StardewCraft.MODID, bus=EventBusSubscriber.Bus.MOD, value=Dist.CLIENT)
public final class BlockbenchAssets {
    private record Key(ResourceLocation geometry, ResourceLocation animation) {}
    private static final Map<Key,NativeNpcModel> MODELS = new HashMap<>();
    private BlockbenchAssets() {}
    @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resources -> MODELS.clear());
    }
    public static NativeNpcModel get(ResourceLocation geometry,ResourceLocation animation) {
        return MODELS.computeIfAbsent(new Key(geometry,animation),key -> {
            var resources=Minecraft.getInstance().getResourceManager();
            try(var reader=resources.openAsReader(key.geometry)) {
                var json=JsonParser.parseReader(reader).getAsJsonObject();
                com.google.gson.JsonObject clips=null;
                if(key.animation!=null)try(var animationReader=resources.openAsReader(key.animation)) {
                    clips=JsonParser.parseReader(animationReader).getAsJsonObject();
                }
                return BlockbenchDecoder.decode(json,clips);
            } catch(Exception ex) {throw new IllegalStateException("Cannot load Blockbench model "+key,ex);}
        });
    }
}

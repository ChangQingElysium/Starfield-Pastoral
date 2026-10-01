package com.stardew.craft.client.model;

import com.google.gson.Gson;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.npcnative.NativeNpcModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;

@SuppressWarnings("removal")
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SebastianComputerModel implements ResourceManagerReloadListener {
    private static volatile NativeNpcModel model;
    public static NativeNpcModel get() { return model; }

    @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new SebastianComputerModel());
    }

    @Override public void onResourceManagerReload(ResourceManager resources) {
        model = null;
        var id = new ResourceLocation(StardewCraft.MODID, "furniture_native/sebastian_computer.json");
        try (var reader = resources.openAsReader(id)) {
            var next = new Gson().fromJson(reader, NativeNpcModel.class);
            if (next.version() != 1 || next.bones().isEmpty() || next.quads().isEmpty())
                throw new IllegalArgumentException("Invalid Generic furniture model");
            for (int i = 0; i < next.bones().size(); i++) {
                var bone = next.bones().get(i);
                if (bone.parent() < -1 || bone.parent() >= i) throw new IllegalArgumentException("Invalid parent");
                finite(bone.origin(), 3); finite(bone.rotation(), 3);
            }
            for (var quad : next.quads()) {
                if (quad.bone() < -1 || quad.bone() >= next.bones().size() || quad.vertices().length != 4)
                    throw new IllegalArgumentException("Invalid quad");
                finite(quad.normal(), 3);
                for (var vertex : quad.vertices()) finite(vertex, 5);
            }
            for (var clip : next.clips().values()) {
                if (!Double.isFinite(clip.length()) || clip.length() <= 0) throw new IllegalArgumentException("Invalid duration");
                for (var track : clip.tracks()) {
                    if (track.bone() < 0 || track.bone() >= next.bones().size() || track.keys().isEmpty()
                            || !java.util.Set.of("position", "rotation", "scale").contains(track.channel()))
                        throw new IllegalArgumentException("Invalid track");
                    double previous = -1;
                    for (var key : track.keys()) {
                        if (!Double.isFinite(key.time()) || key.time() <= previous || key.time() < 0 || key.time() > clip.length())
                            throw new IllegalArgumentException("Invalid key time");
                        finite(key.before(), 3); finite(key.after(), 3); previous = key.time();
                        if (track.channel().equals("scale")) for (int axis = 0; axis < 3; axis++)
                            if (key.before()[axis] <= 0 || key.after()[axis] <= 0) throw new IllegalArgumentException("Invalid scale");
                    }
                }
            }
            if (resources.getResource(new ResourceLocation(next.texture())).isEmpty())
                throw new IllegalArgumentException("Missing furniture texture");
            model = next;
        } catch (Exception error) {
            StardewCraft.LOGGER.error("Cannot load Sebastian's Generic computer", error);
        }
    }

    private static void finite(float[] values, int count) {
        if (values == null || values.length != count) throw new IllegalArgumentException("Invalid vector");
        for (float value : values) if (!Float.isFinite(value)) throw new IllegalArgumentException("Nonfinite vector");
    }
}

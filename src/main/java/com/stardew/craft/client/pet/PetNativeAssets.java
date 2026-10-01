package com.stardew.craft.client.pet;

import com.google.gson.Gson;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.fishing.FishingRigAssets;
import com.stardew.craft.client.npcnative.NativeNpcModel;
import com.stardew.craft.pet.PetVariant;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;

/** The editor's actual cuboids, continuous meshes and complete armature weights. */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PetNativeAssets {
    public record Hat(int bone, float[] position, float scale) {}
    public record Asset(FishingRigAssets.Rig rig, Map<String, NativeNpcModel.Clip> clips,
                        String texture, Hat hat, String species) {}
    private static volatile Map<PetVariant, Asset> assets = Map.of();
    private PetNativeAssets() {}
    public static Asset get(PetVariant variant) { return assets.get(variant); }

    @SubscribeEvent
    public static void register(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resources -> {
            var next = new HashMap<PetVariant, Asset>();
            var models = new HashMap<ResourceLocation, Asset>();
            for (var variant : PetVariant.values()) {
                var path = variant.breed().model();
                try {
                    Asset asset = models.get(path);
                    if (asset == null) {
                        try (var reader = new InputStreamReader(new GZIPInputStream(resources.open(path)), StandardCharsets.UTF_8)) {
                            asset = new Gson().fromJson(reader, Asset.class);
                        }
                        models.put(path, asset);
                    }
                    validate(asset, variant);
                    resources.getResourceOrThrow(variant.breed().texture());
                    try (var stream = resources.open(variant.breed().icon()); var icon = com.mojang.blaze3d.platform.NativeImage.read(stream)) {
                        if (icon.getWidth() != 16 || icon.getHeight() != 16) throw new IllegalArgumentException("Pet icons must be native 16x16 pixels");
                    }
                    next.put(variant, asset);
                } catch (Exception error) {
                    throw new IllegalStateException("Cannot load pet asset " + path, error);
                }
            }
            assets = Map.copyOf(next);
        });
    }

    public static void validate(Asset asset, PetVariant variant) {
        if (asset == null || asset.rig() == null || asset.clips() == null)
            throw new IllegalArgumentException("Pet header");
        var rig = asset.rig();
        if (rig.version() != 1 || rig.bones().isEmpty() || rig.faces().isEmpty()) throw new IllegalArgumentException("Pet rig");
        for (int i = 0; i < rig.bones().size(); i++) {
            var bone = rig.bones().get(i);
            if (bone.parent() < -1 || bone.parent() >= i) throw new IllegalArgumentException("Pet hierarchy");
            finite(bone.position(), 3); finite(bone.rotation(), 3); finite(bone.inverse(), 16);
        }
        for (var face : rig.faces()) {
            if (face.vertices().size() != 4 || face.texture() != 0) throw new IllegalArgumentException("Pet face");
            for (var vertex : face.vertices()) {
                finite(vertex.point(), 3); finite(vertex.uv(), 2);
                if (vertex.bones().length == 0 || vertex.bones().length != vertex.weights().length)
                    throw new IllegalArgumentException("Pet skin binding");
                float sum = 0;
                for (int i = 0; i < vertex.bones().length; i++) {
                    if (vertex.bones()[i] < 0 || vertex.bones()[i] >= rig.bones().size()
                            || !Float.isFinite(vertex.weights()[i]) || vertex.weights()[i] <= 0)
                        throw new IllegalArgumentException("Pet skin weight");
                    sum += vertex.weights()[i];
                }
                if (Math.abs(sum - 1) > 1e-5) throw new IllegalArgumentException("Pet skin normalization");
            }
        }
        for (var clip : asset.clips().values()) {
            if (!Double.isFinite(clip.length()) || clip.length() <= 0) throw new IllegalArgumentException("Pet clip duration");
            for (var track : clip.tracks()) {
                if (track.bone() < 0 || track.bone() >= rig.bones().size() || track.keys().isEmpty()
                        || !Set.of("position", "rotation", "scale").contains(track.channel()))
                    throw new IllegalArgumentException("Pet track");
                double previous = -1;
                for (var key : track.keys()) {
                    if (!Double.isFinite(key.time()) || key.time() <= previous || key.time() > clip.length() + 1e-5)
                        throw new IllegalArgumentException("Pet key time");
                    finite(key.before(), 3); finite(key.after(), 3);
                    // A zero Y scale deliberately hides a lid; the renderer rejects degenerate faces before normalizing.
                    if (track.channel().equals("scale")) for (int axis = 0; axis < 3; axis++)
                        if (key.before()[axis] < 0 || key.after()[axis] < 0) throw new IllegalArgumentException("Negative pet scale");
                    previous = key.time();
                }
            }
        }
        if (!asset.clips().keySet().containsAll(Set.of("idle", "walk", "blink", "sleep_enter", "sleep", "sleep_exit")))
            throw new IllegalArgumentException("Missing pet clips");
        for (var expected : variant.species().behavior().clips().entrySet()) {
            var clip = asset.clips().get(expected.getKey());
            if (clip == null || Math.abs(clip.length() - expected.getValue()) > 1e-5) throw new IllegalArgumentException("Pet behavior/model clip mismatch: " + expected.getKey());
        }
        if (variant.wearsHat() && asset.hat() == null) throw new IllegalArgumentException("Pet hat support");
        if (asset.hat() != null) {
            finite(asset.hat().position(), 3);
            if (asset.hat().bone() < 0 || asset.hat().bone() >= rig.bones().size()
                    || !Float.isFinite(asset.hat().scale()) || asset.hat().scale() <= 0)
                throw new IllegalArgumentException("Pet hat anchor");
        }
    }

    private static void finite(float[] values, int size) {
        if (values == null || values.length != size) throw new IllegalArgumentException("Pet vector size");
        for (float value : values) if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite pet value");
    }
}

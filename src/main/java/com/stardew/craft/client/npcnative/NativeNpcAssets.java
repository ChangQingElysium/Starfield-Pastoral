package com.stardew.craft.client.npcnative;

import com.google.gson.Gson;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.npc.data.NpcModelOwnership;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;

@SuppressWarnings("removal")
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class NativeNpcAssets implements ResourceManagerReloadListener {
    private static volatile java.util.Map<String,NativeNpcModel> models = java.util.Map.of();

    public static NativeNpcModel model(String id) {
        String key = NpcModelOwnership.normalize(id);
        var model = models.get(key);
        if (model == null && NpcModelOwnership.requiresNative(key))
            throw new IllegalStateException("Required native NPC model is unavailable: " + key);
        return model;
    }

    /** Unknown IDs retain the existing Lewis placeholder, using his current native model. */
    public static String renderId(String id) {
        String key = NpcModelOwnership.normalize(id);
        if (NpcModelOwnership.requiresNative(key) || models.containsKey(key)) return key;
        var resources = net.minecraft.client.Minecraft.getInstance().getResourceManager();
        if (!key.isEmpty() && key.matches("[a-z0-9_./-]+")
                && resources.getResource(new ResourceLocation(StardewCraft.MODID,
                        "geo/entity/npc/" + key + ".geo.json")).isPresent()) return key;
        return "lewis";
    }

    public static String legacyId(String id) {
        String key = NpcModelOwnership.requireLegacy(id);
        if (models.containsKey(key)) throw new IllegalStateException("Native NPC cannot use a legacy renderer: " + key);
        return key;
    }
    public static NativeNpcModel activity(com.stardew.craft.npc.animation.SamActivity action) {
        return action == null ? null : model(action.asset());
    }
    public static NativeNpcModel robinConstruction() { return model("robin_construction"); }
    public static NativeNpcModel sam() { return model("sam"); }
    public static NativeNpcModel samGuitar() { return model("sam_guitar"); }

    @SubscribeEvent
    public static void register(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new NativeNpcAssets());
    }

    @Override
    public void onResourceManagerReload(ResourceManager resources) {
        var next = new java.util.LinkedHashMap<String,NativeNpcModel>();
        var textures = new java.util.LinkedHashMap<ResourceLocation,com.mojang.blaze3d.platform.NativeImage>();
        try {
            resources.listResources("npc_native", path -> path.getPath().endsWith(".json"))
                    .keySet().stream().sorted().forEach(location -> {
                String path = location.getPath();
                String name = path.substring("npc_native/".length(),path.length()-5);
                String id = location.getNamespace().equals(StardewCraft.MODID) ? name : location.getNamespace()+":"+name;
                try (var reader = resources.openAsReader(location)) {
                    var loaded = new Gson().fromJson(reader,NativeNpcModel.class);
                    boolean legacyActivity = com.stardew.craft.npc.animation.SamActivity.fromAnimation(id) != null;
                    validate(loaded,legacyActivity,id);
                    var texture = new ResourceLocation(loaded.texture());
                    if (!textures.containsKey(texture)) {
                        try (var input = resources.open(texture)) {
                            textures.put(texture,com.mojang.blaze3d.platform.NativeImage.read(input));
                        }
                    }
                    next.put(id,loaded);
                } catch (Exception error) {
                    throw new IllegalStateException("Cannot load native NPC asset " + id + "; legacy fallback is forbidden", error);
                }
            });
            NpcModelOwnership.requireComplete(next.keySet());
            // Publish the decoded textures with their models, rather than letting the first
            // visible actor lazily load a changed file or reuse a cached missing texture.
            var manager = net.minecraft.client.Minecraft.getInstance().getTextureManager();
            var iterator = textures.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                var texture = new net.minecraft.client.renderer.texture.DynamicTexture(entry.getValue());
                iterator.remove(); // DynamicTexture now owns the native image.
                try {
                    manager.register(entry.getKey(),texture);
                } catch (RuntimeException error) {
                    texture.close();
                    throw error;
                }
            }
            models = java.util.Map.copyOf(next);
            StardewCraft.LOGGER.info("[NPC_ASSETS] Loaded {} native models with decoded textures",models.size());
        } finally {
            textures.values().forEach(com.mojang.blaze3d.platform.NativeImage::close);
        }
    }

    static void validate(NativeNpcModel model) {
        validate(model, false, "sam");
    }

    private static void validate(NativeNpcModel model, boolean guitar, String id) {
        if (model == null || model.version() != 1 || model.bones().isEmpty())
            throw new IllegalArgumentException("Unsupported native model");
        new ResourceLocation(model.texture());
        var names=model.bones().stream().map(NativeNpcModel.Bone::name).collect(java.util.stream.Collectors.toSet());
        if (!names.containsAll(java.util.Set.of("root","body","head","arm_left","arm_right",
                "leg_left","leg_right")))
            throw new IllegalArgumentException("Missing attention rig");
        for (int i = 0; i < model.bones().size(); i++) {
            var bone = model.bones().get(i);
            if (bone.parent() < -1 || bone.parent() >= i) throw new IllegalArgumentException("Invalid hierarchy");
            finite(bone.origin(), 3); finite(bone.rotation(), 3);
        }
        for (var quad : model.quads()) {
            if (quad.bone() < -1 || quad.bone() >= model.bones().size() || quad.vertices().length != 4)
                throw new IllegalArgumentException("Invalid quad");
            finite(quad.normal(), 3);
            for (var vertex : quad.vertices()) finite(vertex, 5);
            if (quad.skin() != null) {
                var skin=quad.skin();
                if (skin.upper()<0 || skin.upper()>=model.bones().size()
                        || skin.lower()<0 || skin.lower()>=model.bones().size()
                        || skin.upper()==skin.lower()) throw new IllegalArgumentException("Joint skin bones");
                finite(skin.weights(),4);
                for (float weight:skin.weights()) if (weight<0 || weight>1)
                    throw new IllegalArgumentException("Joint skin weight");
            }
        }
        for (var clip : model.clips().values()) {
            if (!Double.isFinite(clip.length()) || clip.length() <= 0) throw new IllegalArgumentException("Clip length");
            for (var track : clip.tracks()) {
                if (track.bone() < 0 || track.bone() >= model.bones().size() || track.keys().isEmpty()
                        || !java.util.Set.of("position", "rotation", "scale").contains(track.channel()))
                    throw new IllegalArgumentException("Invalid track");
                double previous = -1;
                for (var key : track.keys()) {
                    if (!Double.isFinite(key.time()) || key.time() < 0 || key.time() <= previous || key.time() > clip.length())
                        throw new IllegalArgumentException("Invalid key time");
                    finite(key.before(), 3); finite(key.after(), 3);
                    if (track.channel().equals("scale")) for (int axis = 0; axis < 3; axis++)
                        if (key.before()[axis] <= 0 || key.after()[axis] <= 0) throw new IllegalArgumentException("Scale");
                    previous = key.time();
                }
            }
        }
        var action = com.stardew.craft.npc.animation.SamActivity.fromAnimation(id);
        var required = "gil".equals(id)
                ? java.util.Set.of("animation.gil.idle")
                : "robin_construction".equals(id)
                ? java.util.Set.of("animation.robin.construction", "animation.robin.construction_low")
                : guitar && action!=null ? java.util.Set.of(action.playClip(),action.holdClip(),"animation.sam.idle")
                : model.profile()!=null && !model.profile().visibleBlink()
                ? java.util.Set.of("animation."+id+".idle","animation."+id+".walk")
                : java.util.Set.of("animation."+id+".idle","animation."+id+".blink","animation."+id+".walk");
        if (guitar && action != null && action.supported()
                && (!model.clips().containsKey(action.enterClip()) || !model.clips().containsKey(action.exitClip())))
            throw new IllegalArgumentException("Missing support transitions");
        boolean actorModel = model.clips().containsKey("animation."+id+".idle");
        if (model.clips().isEmpty() || ((actorModel || guitar || "robin_construction".equals(id))
                && !model.clips().keySet().containsAll(required)))
            throw new IllegalArgumentException("Missing character clips");
        var p = model.profile();
        if (p!=null && p.blinkMode()!=null && !java.util.Set.of("occluded", "closed").contains(p.blinkMode()))
            throw new IllegalArgumentException("Unsupported blink visibility mode");
        if (p!=null && !p.visibleBlink() && model.clips().keySet().stream().anyMatch(n->n.endsWith(".blink")))
            throw new IllegalArgumentException("Closed or occluded eyes must not have a blink clip");
        if (p == null || !(p.intervalMin() > 0 && p.intervalMax() > p.intervalMin()
                && p.durationMin() > 0 && p.durationMax() > p.durationMin()
                && p.doubleChance() >= 0 && p.doubleChance() <= 1) || !Double.isFinite(p.intervalMax())
                || !Double.isFinite(p.durationMax()) || !Float.isFinite(p.groundOffset())
                || !(p.walkStride()>0 && p.walkStride()<4))
            throw new IllegalArgumentException("Invalid motion profile");
        if (p.gait()!=null && (!Double.isFinite(p.gait().previewSpeed()) || p.gait().previewSpeed()<=0))
            throw new IllegalArgumentException("Invalid authored walk speed");
        var look=p.lookLimits();
        if(look!=null && !(Float.isFinite(look.yaw()) && Float.isFinite(look.pitch())
                && look.yaw()>0 && look.yaw()<=48 && look.pitch()>0 && look.pitch()<=18))
            throw new IllegalArgumentException("Invalid relative neck limits");
        var rig=p.attentionRig();
        if(p.cloth()!=null) {
            var c=p.cloth();
            if(!java.util.Set.of("skirt","cape","apron","mantle").contains(c.kind()) || !names.contains(c.bone()) || !names.contains("cloth_motion")
                    || !Float.isFinite(c.anchorY()) || !Float.isFinite(c.hemY()) || !Float.isFinite(c.margin())
                    || !(c.anchorY()>c.hemY() && c.anchorY()-c.hemY()<40 && c.margin()>0 && c.margin()<2))
                throw new IllegalArgumentException("Invalid garment profile");
            if(c.contactBlend()!=null && !(Float.isFinite(c.contactBlend())
                    && c.contactBlend()>0 && c.contactBlend()<=2))
                throw new IllegalArgumentException("Invalid garment contact blend");
            if(c.standingDepthMargin()!=null && !(Float.isFinite(c.standingDepthMargin())
                    && c.standingDepthMargin()>0 && c.standingDepthMargin()<=c.margin()))
                throw new IllegalArgumentException("Invalid standing garment margin");
            if(c.clearancePart()!=null && model.quads().stream().noneMatch(q -> c.clearancePart().equals(q.sourcePart())
                    && q.bone()>=0 && model.bones().get(q.bone()).name().equals(c.bone())))
                throw new IllegalArgumentException("Missing garment clearance surface");
        }
        for(double value:new double[]{rig.hipHeight(),rig.soleY(),rig.toeDepth(),rig.halfStance(),
                rig.rightZ(),rig.leftZ(),rig.rightYaw(),rig.leftYaw(),rig.lift(),rig.lean(),rig.armSwing()})
            if(!Double.isFinite(value))throw new IllegalArgumentException("Invalid attention rig");
        if(rig.hipHeight()<=rig.soleY() || rig.toeDepth()<=0 || rig.halfStance()<=0 || rig.lift()<0)
            throw new IllegalArgumentException("Invalid contact dimensions");
    }

    private static void finite(float[] values, int size) {
        if (values == null || values.length != size) throw new IllegalArgumentException("Vector size");
        for (float value : values) if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite vector");
    }
}

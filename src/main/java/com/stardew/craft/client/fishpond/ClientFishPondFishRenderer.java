package com.stardew.craft.client.fishpond;

import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.npcnative.NativeNpcModel;
import com.stardew.craft.client.npcnative.NativeNpcPose;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import java.util.Map;

import com.stardew.craft.port.PortVertex;
/** Rigid authored Cube models; swimming and jumps move the whole fish without deforming it. */
@EventBusSubscriber(modid=StardewCraft.MODID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class ClientFishPondFishRenderer {
    private record FishModel(NativeNpcModel model, NativeNpcPose pose, Vector3f center, float radius, String locomotion, Vector3f catchPoint, boolean verticalGrip, Vector3f size, com.stardew.craft.fishing.PlacedFishLayout.Pose wallPose) {}
    private record CatchAnchor(float[] point, String axis, String surface) {}
    private static volatile Map<ResourceLocation,FishModel> models=Map.of();
    private ClientFishPondFishRenderer() {}
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resources -> {
            var next=new java.util.HashMap<ResourceLocation,FishModel>();
            Map<String,CatchAnchor> catchAnchors;
            try(var reader=resources.openAsReader(new ResourceLocation(StardewCraft.MODID,"fishing_native/catch_anchors.json"))) {
                catchAnchors=new Gson().fromJson(reader,new com.google.gson.reflect.TypeToken<Map<String,CatchAnchor>>(){}.getType());
            } catch(Exception e){throw new IllegalStateException("Cannot load fish attachment surfaces",e);}
            resources.listResources("pond_fish",id->id.getPath().endsWith(".json")&&!id.getPath().endsWith("/manifest.json")).forEach((id,resource)->{
                try(var reader=resource.openAsReader()) {
                    var json=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                    var model=new Gson().fromJson(json,NativeNpcModel.class);
                    String locomotion=json.has("locomotion")?json.get("locomotion").getAsString():"swim";
                    if(model.version()!=1 || model.quads().isEmpty())throw new IllegalArgumentException("Invalid fish geometry");
                    var min=new Vector3f(Float.POSITIVE_INFINITY);var max=new Vector3f(Float.NEGATIVE_INFINITY);
                    var pose=new NativeNpcPose(model);var matrices=pose.matrices();
                    for(var q:model.quads())for(var v:q.vertices()) {
                        var p=new Vector3f(v[0],v[1],v[2]);if(q.bone()>=0)matrices[q.bone()].transformPosition(p);min.min(p);max.max(p);
                    }
                    var center=new Vector3f(min).add(max).mul(.5F);float radius=max.distance(min)*.5F;
                    resources.getResourceOrThrow(new ResourceLocation(model.texture()));
                    String name=id.getPath().substring("pond_fish/".length(),id.getPath().length()-5);
                    var anchor=catchAnchors.get(name);
                    var mouth=anchor==null?new Vector3f(max.x-.15F,center.y,center.z):new Vector3f(anchor.point());
                    var size=new Vector3f(max).sub(min);
                    var wallPose=com.stardew.craft.fishing.PlacedFishLayout.hanging(
                            new double[]{min.x,min.y,min.z},new double[]{max.x,max.y,max.z},
                            new double[]{mouth.x,mouth.y,mouth.z},anchor!=null&&anchor.axis().equals("y"));
                    next.put(new ResourceLocation(id.getNamespace(),name),new FishModel(model,pose,center,radius,locomotion,mouth,anchor!=null&&anchor.axis().equals("y"),size,wallPose));
                } catch(Exception e) {throw new IllegalStateException("Cannot load pond fish "+id,e);}
            });
            models=Map.copyOf(next);ClientFishPondSwimVisuals.clear();
        });
    }
    public static boolean available(ItemStack stack) {return models.containsKey(BuiltInRegistries.ITEM.getKey(stack.getItem()));}
    /** Dimensions of the complete authored model, including fins and inverted outlines. */
    public static Vector3f modelSize(ItemStack stack) {
        var model=models.get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        return model==null?new Vector3f():new Vector3f(model.size());
    }
    private static String locomotion(ItemStack stack) {
        var model=models.get(BuiltInRegistries.ITEM.getKey(stack.getItem()));return model==null?"still":model.locomotion();
    }
    public static boolean stationary(ItemStack stack) {return locomotion(stack).equals("still");}
    public static boolean bottomDweller(ItemStack stack) {return !locomotion(stack).equals("swim");}
    /** Mouth-side attachment for the player's catch presentation, keeping authored species geometry. */
    public static void renderCaughtFish(ItemStack fish, PoseStack stack, MultiBufferSource buffers, int light) {
        var entry=models.get(BuiltInRegistries.ITEM.getKey(fish.getItem()));if(entry==null)return;
        float scale=Math.min(.65F/16F, 1.35F/(entry.radius()*2));
        stack.pushPose();
        if(entry.verticalGrip())stack.mulPose(Axis.ZP.rotationDegrees(-90));
        var offset=new Vector3f(entry.center()).sub(entry.catchPoint()).mul(scale);
        stack.translate(offset.x,offset.y,offset.z);
        renderFish(fish,stack,buffers,null,light,0,0,0,entry.radius()*2*scale);
        stack.popPose();
    }
    /** Display the approved fish rigidly, with its complete outline resting on the crushed ice. */
    public static void renderMarketFish(ItemStack fish, PoseStack stack, MultiBufferSource buffers, int light, int slot) {
        var entry=models.get(BuiltInRegistries.ITEM.getKey(fish.getItem()));if(entry==null)return;
        var size=entry.size();
        var fit=com.stardew.craft.fishing.FishMarketCrateLayout.fit(size.x,size.y,size.z,
                entry.verticalGrip()||!entry.locomotion().equals("swim"),slot);
        stack.pushPose();
        stack.translate(0,fit.centerY(),0);
        renderFish(fish,stack,buffers,null,light,-fit.yaw(),0,fit.roll(),(float)(entry.radius()*2*fit.unitScale()));
        stack.popPose();
    }
    public static com.stardew.craft.fishing.PlacedFishLayout.Pose placedPose(ItemStack fish, boolean wall) {
        var entry=models.get(BuiltInRegistries.ITEM.getKey(fish.getItem()));if(entry==null)return null;
        if(wall)return entry.wallPose();
        var size=entry.size();
        return com.stardew.craft.fishing.PlacedFishLayout.floor(size.x,size.y,size.z,
                entry.verticalGrip()||!entry.locomotion().equals("swim"));
    }
    public static void renderPlacedFish(ItemStack fish, PoseStack stack, MultiBufferSource buffers, int light,
                                        com.stardew.craft.fishing.PlacedFishLayout.Pose fit) {
        var entry=models.get(BuiltInRegistries.ITEM.getKey(fish.getItem()));if(entry==null)return;
        stack.pushPose();stack.translate(fit.centerX()/16,fit.centerY()/16,fit.centerZ()/16);
        renderFish(fish,stack,buffers,null,light,0,fit.pitch(),fit.roll(),(float)(entry.radius()*2*fit.scale()/16));
        stack.popPose();
    }
    public static void renderFish(ItemStack fish,PoseStack stack,MultiBufferSource buffers,ClientLevel level,int light,
                                  float yaw,float pitch,float roll,float scale) {
        var entry=models.get(BuiltInRegistries.ITEM.getKey(fish.getItem()));if(entry==null)return;
        var model=entry.model();var pose=entry.pose();pose.reset();
        var matrices=pose.matrices();var normalMatrix=new Matrix3f();var normal=new Vector3f();var vertex=new Vector3f();
        stack.pushPose();
        // Approved fish face +X in authoring space. Heading zero swims east, positive heading turns south.
        stack.mulPose(Axis.YP.rotationDegrees(-yaw));stack.mulPose(Axis.ZP.rotationDegrees(-pitch));stack.mulPose(Axis.XP.rotationDegrees(roll));
        float factor=scale/(2*entry.radius());stack.scale(factor,factor,factor);stack.translate(-entry.center().x,-entry.center().y,-entry.center().z);
        var texture=new ResourceLocation(model.texture());
        for(int pass=0;pass<3;pass++) {
            var consumer=buffers.getBuffer(pass==2?RenderType.entityTranslucent(texture):pass==1?RenderType.entityCutout(texture):RenderType.entityCutoutNoCull(texture));
            for(var q:model.quads()) {
                if(q.translucent()!=(pass==2)||pass<2&&q.cull()!=(pass==1))continue;
                normal.set(q.normal());if(q.bone()>=0)matrices[q.bone()].normal(normalMatrix).transform(normal).normalize();
                for(var v:q.vertices()) {
                    vertex.set(v[0],v[1],v[2]);if(q.bone()>=0)matrices[q.bone()].transformPosition(vertex);
                    PortVertex.of(consumer).addVertex(stack.last().pose(),vertex.x,vertex.y,vertex.z).setColor(255,255,255,255)
                        .setUv(v[3],v[4]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(q.sourcePart().equals("lure_bulb")?net.minecraft.client.renderer.LightTexture.FULL_BRIGHT:light).setNormal(stack.last(),normal.x,normal.y,normal.z).endVertex();
                }
            }
        }
        stack.popPose();
    }
}

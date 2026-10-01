package com.stardew.craft.client.fishing;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.npcnative.NativeNpcModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/** Compiled from the editor's actual bind mesh, including its native armature weights. */
@EventBusSubscriber(modid=StardewCraft.MODID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class FishingRigAssets {
    public record Bone(String name,int parent,float[] position,float[] rotation,float[] inverse) {}
    public record Vertex(float[] point,float[] uv,int[] bones,float[] weights) {}
    public record Face(String part,int texture,boolean cull,List<Vertex> vertices) {}
    public record Rig(int version,List<Bone> bones,List<Face> faces) {}
    public static volatile Rig rig;
    public static volatile Map<String,NativeNpcModel.Clip> clips=Map.of();
    public static ResourceLocation resource(String path) {return new ResourceLocation(StardewCraft.MODID,path);}
    @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resources->{
            try(var reader=resources.openAsReader(resource("fishing_native/rig.json"));
                var motion=new InputStreamReader(new GZIPInputStream(resources.open(resource("fishing_native/motion.json.gz"))),StandardCharsets.UTF_8)) {
                var next=new Gson().fromJson(reader,Rig.class);
                Map<String,NativeNpcModel.Clip> animations=new Gson().fromJson(motion,new TypeToken<Map<String,NativeNpcModel.Clip>>(){}.getType());
                validate(next,animations);
                for(int i=0;i<4;i++)resources.getResourceOrThrow(resource("textures/entity/fishing_native/"+i+".png"));
                var bobbers=FishingBobberModels.load(resources);
                var baits=FishingBaitModels.load(resources);
                var rods=FishingRodModels.load(resources,next);
                var tackles=FishingTackleModels.load(resources,bobbers);
                FishingInteractionState.cancel(true);FishingBobberModels.models=bobbers;FishingBaitModels.models=baits;FishingRodModels.models=rods;FishingTackleModels.assets=tackles;rig=next;clips=Map.copyOf(animations);FishingPresentationClient.clear();
            } catch(Exception error) {throw new IllegalStateException("Cannot load native fishing presentation",error);}
        });
    }
    public static void validate(Rig rig,Map<String,NativeNpcModel.Clip> clips) {
        if(rig==null||rig.version()!=1||rig.bones().isEmpty()||rig.faces().isEmpty())throw new IllegalArgumentException("Fishing rig");
        for(int i=0;i<rig.bones().size();i++) {
            var b=rig.bones().get(i);if(b.parent()>=i||b.parent()< -1)throw new IllegalArgumentException("Fishing hierarchy");
            finite(b.position(),3);finite(b.rotation(),3);finite(b.inverse(),16);
        }
        for(var face:rig.faces())for(var v:face.vertices()) {
            finite(v.point(),3);finite(v.uv(),2);if(v.bones().length!=v.weights().length||v.bones().length==0)throw new IllegalArgumentException("Skin binding");
            float sum=0;for(int i=0;i<v.bones().length;i++){if(v.bones()[i]<0||v.bones()[i]>=rig.bones().size()||!Float.isFinite(v.weights()[i])||v.weights()[i]<=0)throw new IllegalArgumentException("Skin weight");sum+=v.weights()[i];}
            if(Math.abs(sum-1)>1e-5)throw new IllegalArgumentException("Skin normalization");
        }
        for(String required:List.of("equip_raise","ready_one_hand","ready_idle","charge_enter","charge_hold","cast_release","wait_idle","bite_notice","hook_set","reel_grab","reel_enter","reel_loop","fish_run","slack_takeup","catch_lift","catch_item","catch_block","failed_retrieve","cancel_retrieve","success_to_ready"))
            if(!clips.containsKey(required))throw new IllegalArgumentException("Missing fishing clip "+required);
        for(var clip:clips.values()) {
            if(!Double.isFinite(clip.length())||clip.length()<=0)throw new IllegalArgumentException("Clip duration");
            for(var track:clip.tracks()) {
                if(track.bone()<0||track.bone()>=rig.bones().size()||!List.of("position","rotation","scale").contains(track.channel()))throw new IllegalArgumentException("Track");
                double previous=-1;
                for(var key:track.keys()){if(key.time()<=previous||key.time()>clip.length()+1e-5)throw new IllegalArgumentException("Key time");previous=key.time();finite(key.before(),3);finite(key.after(),3);}
            }
        }
    }
    private static void finite(float[] v,int count){if(v.length!=count)throw new IllegalArgumentException("Vector");for(float f:v)if(!Float.isFinite(f))throw new IllegalArgumentException("Finite vector");}
}

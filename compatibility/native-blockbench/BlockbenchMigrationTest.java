package com.stardew.craft.model.nativebb;

import com.google.gson.*;
import com.stardew.craft.client.model.nativebb.*;
import com.stardew.craft.client.npcnative.*;
import com.stardew.craft.model.ModelAnimation;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BlockbenchMigrationTest {
    private static final Path ASSETS=Path.of(System.getProperty("stardewcraft.projectDir")).resolve("src/main/resources/assets/stardewcraft");
    private static JsonObject json(String text) {return JsonParser.parseString(text).getAsJsonObject();}
    private static JsonObject read(Path path) throws Exception {return json(Files.readString(path));}
    private static JsonObject rig(String bones) {return json("{\"minecraft:geometry\":[{\"description\":{\"texture_width\":32,\"texture_height\":32},\"bones\":"+bones+"}]}");}
    private static JsonObject simpleRig() {return rig("[{\"name\":\"root\",\"pivot\":[0,0,0]}]");}
    private static NativeNpcModel simpleModel() {return BlockbenchDecoder.decode(simpleRig(),json("""
        {"animations":{
          "walk":{"loop":true,"animation_length":1,"bones":{"root":{"position":{"0":[0,0,0],"1":[0,10,0]}}}},
          "close":{"animation_length":1,"bones":{"root":{"rotation":{"0":[-90,0,0],"1":[0,0,0]}}}}
        }}
        """));}

    @Test void noAnimationLibraryOnRuntimeClasspath() {
        assertThrows(ClassNotFoundException.class,()->Class.forName("software.bernie.geckolib.GeckoLib"));
    }

    @Test void allShippedGeometryAndAnimationsDecodeAndProduceFinitePoses() throws Exception {
        int geometryCount=0, animationCount=0, quadCount=0;
        try(var paths=Files.walk(ASSETS.resolve("geo"))) {
            for(var path:paths.filter(p->p.toString().endsWith(".json")).sorted().toList()) {
                var model=BlockbenchDecoder.decode(read(path),null);
                assertFalse(model.bones().isEmpty(),path.toString());
                assertFalse(model.quads().isEmpty(),path.toString());
                for(var q:model.quads())for(var v:q.vertices())for(float value:v)assertTrue(Float.isFinite(value),path.toString());
                for(var matrix:new NativeNpcPose(model).matrices())assertTrue(matrix.isFinite(),path.toString());
                quadCount+=model.quads().size();geometryCount++;
            }
        }
        try(var paths=Files.walk(ASSETS.resolve("animations"))) {
            for(var path:paths.filter(p->p.toString().endsWith(".json")).sorted().toList()) {
                var animations=read(path);var names=new TreeSet<String>();
                for(var clip:animations.getAsJsonObject("animations").entrySet()) {
                    var bones=clip.getValue().getAsJsonObject().getAsJsonObject("bones");
                    if(bones!=null)names.addAll(bones.keySet());
                }
                var bones=new JsonArray();
                for(String name:names) {var bone=new JsonObject();bone.addProperty("name",name);bones.add(bone);}
                var model=BlockbenchDecoder.decode(rig(bones.toString()),animations);
                var pose=new NativeNpcPose(model);
                for(var clip:model.clips().entrySet())for(int i=0;i<=20;i++) {
                    pose.reset();pose.applyAt(clip.getKey(),clip.getValue().length()*i/20.0);
                    for(var matrix:pose.matrices())assertTrue(matrix.isFinite(),path+" "+clip.getKey());
                }
                animationCount++;
            }
        }
        assertTrue(geometryCount>=80);assertTrue(animationCount>=58);assertTrue(quadCount>20000);
        System.out.printf("[NATIVE-BLOCKBENCH] %d geometries, %d quads, %d animation files validated%n",geometryCount,quadCount,animationCount);
    }

    @Test void mirroredCoordinatesRotatedUvsAndParentPivotsRemainConsistent() {
        var model=BlockbenchDecoder.decode(rig("""
            [{"name":"child","parent":"root","pivot":[2,3,4],"cubes":[{
              "origin":[1,2,3],"size":[2,4,6],"uv":{"north":{"uv":[8,4],"uv_size":[2,4],"uv_rotation":90}}}]},
             {"name":"root","pivot":[0,0,0],"rotation":[0,0,90]}]
            """),null);
        assertEquals("root",model.bones().get(0).name());assertEquals(0,model.bones().get(1).parent());
        var quad=model.quads().get(0);
        assertArrayEquals(new float[]{-3,6,3,8F/32,4F/32},quad.vertices()[0],1E-6F);
        assertArrayEquals(new float[]{0,0,-1},quad.normal(),1E-6F);
        var position=new NativeNpcPose(model).matrices()[1].transformPosition(new Vector3f(-3,6,3));
        assertEquals(-6,position.x,1E-5);assertEquals(-3,position.y,1E-5);assertEquals(3,position.z,1E-5);
    }

    @Test void catmullRomAndDiscontinuousKeysSurviveImport() {
        var model=BlockbenchDecoder.decode(simpleRig(),json("""
            {"animations":{"a":{"animation_length":3,"bones":{"root":{"position":{
                "0":{"post":{"vector":[0,0,0]},"lerp_mode":"catmullrom"},
                "1":{"post":{"vector":[0,10,0]},"lerp_mode":"catmullrom"},
                "2":{"post":{"vector":[0,10,0]},"lerp_mode":"catmullrom"},
                "3":{"pre":{"vector":[0,0,0]},"post":{"vector":[0,4,0]}}
            }}}}}}
            """));
        var pose=new NativeNpcPose(model);pose.applyAt("a",1.5);
        assertEquals(11.25,pose.boneMatrix("root").m31(),1E-4);
        pose.reset();pose.applyAt("a",3);assertEquals(4,pose.boneMatrix("root").m31(),1E-5);
    }

    @Test void clocksArePerInstanceAndHoldLoopStopAndTransitionsAreDistinct() {
        var model=simpleModel();var first=new BlockbenchPlayback(model);var second=new BlockbenchPlayback(model);
        first.sample(ModelAnimation.loop("walk"),10,0);
        assertEquals(5,first.sample(ModelAnimation.loop("walk"),10.5,0).boneMatrix("root").m31(),1E-5);
        assertEquals(0,second.sample(ModelAnimation.loop("walk"),10.5,0).boneMatrix("root").m31(),1E-5);
        assertEquals(2.5,first.sample(ModelAnimation.loop("walk"),11.25,0).boneMatrix("root").m31(),1E-5);
        assertEquals(2.5,first.sample(null,11.25,5).boneMatrix("root").m31(),1E-5);
        assertEquals(1.25,first.sample(null,11.375,5).boneMatrix("root").m31(),1E-5);
        assertEquals(0,first.sample(null,11.5,5).boneMatrix("root").m31(),1E-5);
        var chest=new BlockbenchPlayback(model);
        assertTrue(chest.sample(ModelAnimation.state("close"),0,0).boneMatrix("root").equals(new org.joml.Matrix4f(),1E-5F),"Initially closed chest must not visibly close again");
        var once=new BlockbenchPlayback(model);
        once.sample(ModelAnimation.play("walk"),0,0);
        assertEquals(0,once.sample(ModelAnimation.play("walk"),2,0).boneMatrix("root").m31(),1E-5);
        assertEquals(7,once.sample(ModelAnimation.at("walk",.7),2,0).boneMatrix("root").m31(),1E-5);
    }

    @Test void requiredClipsAndAttachmentBonesExist() throws Exception {
        Map<String,List<String>> cases=Map.of(
            "block/utility/shipping_bin",List.of("open","close","ship"),
            "block/utility/mini_shipping_bin",List.of("OPEN","CLOSE"),
            "entity/junimo/junimo",List.of("idle","walk","hold_walk"),
            "entity/festival/moonlight_jelly",List.of("animation"),
            "entity/npc/traveling_cart",List.of("animation.traveling_cart.idle"));
        for(var entry:cases.entrySet()) {
            var model=BlockbenchDecoder.decode(read(ASSETS.resolve("geo/"+entry.getKey()+".geo.json")),
                    read(ASSETS.resolve("animations/"+entry.getKey()+".animation.json")));
            for(String clip:entry.getValue())assertTrue(model.clips().containsKey(clip));
            if(entry.getKey().endsWith("shipping_bin"))assertTrue(model.bones().stream().anyMatch(b->b.name().equals("lid")));
            if(entry.getKey().equals("block/utility/shipping_bin"))assertTrue(model.bones().stream().anyMatch(b->b.name().equals("shipment_item")));
            if(entry.getKey().equals("entity/junimo/junimo"))assertTrue(model.bones().stream().anyMatch(b->b.name().equals("right_item")));
        }
    }

    @Test void closedChestDoesNotReplayWhenRenderTimeMovesBackwards() throws Exception {
        for (String id : List.of("wooden_chest_default", "stone_chest", "big_chest", "big_stone_chest", "junimo_chest")) {
            var model = BlockbenchDecoder.decode(read(ASSETS.resolve("geo/block/utility/" + id + ".geo.json")),
                    read(ASSETS.resolve("animations/block/utility/" + id + ".animation.json")));
            var playback = new BlockbenchPlayback(model);
            var expected = new NativeNpcPose(model); expected.applyAt("CLOSE", model.clips().get("CLOSE").length());
            for (double time : new double[]{10, 10.05, 10.04, 10.06, 0, .01, 100}) {
                assertTrue(playback.sample(ModelAnimation.state("CLOSE"), time, 0).boneMatrix("lid")
                                .equals(expected.boneMatrix("lid"), 1E-5F),
                        id + " replayed closing from a fully open lid at render time " + time);
            }
        }
    }

    @Test void clockRewindsPreserveActiveAnimationAndTransitionProgress() {
        var playback = new BlockbenchPlayback(simpleModel());
        playback.sample(ModelAnimation.loop("walk"), 10, 0);
        assertEquals(5, playback.sample(ModelAnimation.loop("walk"), 10.5, 0).boneMatrix("root").m31(), 1E-5);
        assertEquals(5, playback.sample(ModelAnimation.loop("walk"), 10.49, 0).boneMatrix("root").m31(), 1E-5);
        assertEquals(7.5, playback.sample(ModelAnimation.loop("walk"), 10.74, 0).boneMatrix("root").m31(), 1E-5);
        assertEquals(7.5, playback.sample(null, 10.74, 5).boneMatrix("root").m31(), 1E-5);
        assertEquals(3.75, playback.sample(null, 10.865, 5).boneMatrix("root").m31(), 1E-5);
        assertEquals(3.75, playback.sample(null, 0, 5).boneMatrix("root").m31(), 1E-5);
        assertEquals(0, playback.sample(null, .125, 5).boneMatrix("root").m31(), 1E-5);
    }

    @Test void realChestStateChangesStillPlayOpenAndClose() throws Exception {
        for (String id : List.of("wooden_chest_default", "stone_chest", "big_chest", "big_stone_chest", "junimo_chest")) {
            var model = BlockbenchDecoder.decode(read(ASSETS.resolve("geo/block/utility/" + id + ".geo.json")),
                    read(ASSETS.resolve("animations/block/utility/" + id + ".animation.json")));
            var playback = new BlockbenchPlayback(model);
            var closed = new NativeNpcPose(model); closed.applyAt("CLOSE", model.clips().get("CLOSE").length());
            var opened = new NativeNpcPose(model); opened.applyAt("OPEN", model.clips().get("OPEN").length());
            playback.sample(ModelAnimation.state("CLOSE"), 10, 0);
            assertTrue(playback.sample(ModelAnimation.state("OPEN"), 11, 0).boneMatrix("lid").equals(closed.boneMatrix("lid"), 1E-5F), id);
            assertTrue(playback.sample(ModelAnimation.state("OPEN"), 12, 0).boneMatrix("lid").equals(opened.boneMatrix("lid"), 1E-5F), id);
            assertTrue(playback.sample(ModelAnimation.state("CLOSE"), 13, 0).boneMatrix("lid").equals(opened.boneMatrix("lid"), 1E-5F), id);
            assertTrue(playback.sample(ModelAnimation.state("CLOSE"), 14, 0).boneMatrix("lid").equals(closed.boneMatrix("lid"), 1E-5F), id);
        }
    }
}

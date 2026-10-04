package com.stardew.craft.model.nativebb;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stardew.craft.client.model.nativebb.BlockbenchDecoder;
import com.stardew.craft.client.model.nativebb.BlockbenchPlayback;
import com.stardew.craft.client.npcnative.NativeActorAnimation;
import com.stardew.craft.client.npcnative.NativeNpcModel;
import com.stardew.craft.client.npcnative.NativeNpcPose;
import com.stardew.craft.model.ModelAnimation;
import com.stardew.craft.npc.data.NpcModelOwnership;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Rendering-independent checks against the resources actually shipped to Minecraft. */
class NpcCutscenePlaybackTest {
    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    private static JsonObject resource(String path) throws Exception {
        try (var stream = NpcModelOwnership.class.getClassLoader().getResourceAsStream("assets/stardewcraft/" + path)) {
            assertNotNull(stream, "Missing packaged NPC resource: " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static NativeNpcModel legacy(String id) throws Exception {
        return BlockbenchDecoder.decode(resource("geo/entity/npc/" + id + ".geo.json"),
                resource("animations/entity/npc/" + id + ".animation.json"));
    }

    private static NativeNpcModel simpleModel(String clips) {
        return BlockbenchDecoder.decode(json("""
                {"minecraft:geometry":[{"description":{"texture_width":16,"texture_height":16},
                  "bones":[{"name":"root","pivot":[0,0,0]}]}]}
                """), json("{\"animations\":" + clips + "}"));
    }

    private static Matrix4f[] snapshot(NativeNpcPose pose) {
        return java.util.Arrays.stream(pose.matrices()).map(Matrix4f::new).toArray(Matrix4f[]::new);
    }

    private static void assertPose(Matrix4f[] expected, NativeNpcPose actual, String context) {
        var matrices = actual.matrices();
        assertEquals(expected.length, matrices.length, context);
        for (int bone = 0; bone < matrices.length; bone++) {
            assertTrue(matrices[bone].isFinite(), context + " bone " + bone);
            assertTrue(expected[bone].equals(matrices[bone], 1E-5F), context + " bone " + bone);
        }
    }

    @Test void governorAndAllRemainingLegacyActorsCanWalkWithoutMissingClipCrashes() throws Exception {
        for (String id : List.of("bear", "bouncer", "club_seller", "governor", "henchman",
                "joja_cashier", "morris", "traveling_cart")) {
            var model = legacy(id);
            String walk = NativeActorAnimation.resolve(model, id, "walk");
            String idle = NativeActorAnimation.resolve(model, id, "idle");
            assertNotNull(idle, id + " has no authored idle");
            if (id.equals("governor")) {
                assertNull(walk, "The reported luau_main_event_6 actor has no authored walk");
                var error = assertThrows(IllegalArgumentException.class,
                        () -> new BlockbenchPlayback(model).sample(ModelAnimation.loop("walk"), 0, 0));
                assertEquals("Missing Blockbench animation: walk", error.getMessage());
            }
            var playback = new BlockbenchPlayback(model);
            var expected = new BlockbenchPlayback(model);
            for (double time : new double[]{20, 20.05, 20.25, 21.25, 100}) {
                assertPose(snapshot(expected.sample(ModelAnimation.loop(walk == null ? idle : walk), time, 0)),
                        playback.sampleNpc(id, ModelAnimation.loop("walk"), time, 0), id + " at " + time);
            }
            // Existing activities such as the Governor's eat clip keep their original clock and state semantics.
            for (String clip : model.clips().keySet()) for (ModelAnimation request : List.of(
                    ModelAnimation.loop(clip), ModelAnimation.play(clip), ModelAnimation.hold(clip),
                    ModelAnimation.at(clip, .23), ModelAnimation.state(clip))) {
                var direct = new BlockbenchPlayback(model);
                var npc = new BlockbenchPlayback(model);
                for (double time : new double[]{3, 3.125, 4.25, 9}) {
                    assertPose(snapshot(direct.sample(request, time, 0)), npc.sampleNpc(id, request, time, 0),
                            id + " " + request + " at " + time);
                }
            }
        }
    }

    @Test void qualifiedAliasesPreserveLoopHoldFixedTimeAndInitiallyComplete() {
        var model = simpleModel("""
                {"animation.example.idle":{"animation_length":1,"bones":{"root":{"position":{"0":[0,0,0],"1":[0,10,0]}}}}}
                """);
        for (ModelAnimation alias : List.of(ModelAnimation.loop("idle"), ModelAnimation.play("idle"),
                ModelAnimation.hold("idle"), ModelAnimation.at("idle", .4), ModelAnimation.state("idle"))) {
            var qualified = new ModelAnimation("animation.example.idle", alias.loop(), alias.hold(),
                    alias.time(), alias.initiallyComplete());
            var direct = new BlockbenchPlayback(model);
            var npc = new BlockbenchPlayback(model);
            for (double time : new double[]{7, 7.25, 8.25, 15}) {
                assertPose(snapshot(direct.sample(qualified, time, 0)), npc.sampleNpc("example", alias, time, 0),
                        alias + " at " + time);
            }
        }
    }

    @Test void missingScriptActionUsesUnrestrictedLoopingIdleAndTransitionsToIt() {
        var model = simpleModel("""
                {"idle":{"animation_length":1,"bones":{"root":{"position":{"0":[0,0,0],"1":[0,10,0]}}}},
                 "eat":{"animation_length":1,"bones":{"root":{"position":[0,20,0]}}}}
                """);
        var playback = new BlockbenchPlayback(model);
        var idle = new BlockbenchPlayback(model);
        for (double time : new double[]{10, 10.25, 11.25, 15.75}) {
            assertPose(snapshot(idle.sample(ModelAnimation.loop("idle"), time, 0)),
                    playback.sampleNpc("example", ModelAnimation.at("missing_action", .9), time, 0),
                    "fallback idle ignores the missing script's fixed sample time at " + time);
        }
        var transition = new BlockbenchPlayback(model);
        transition.sampleNpc("example", ModelAnimation.hold("eat"), 0, 0);
        assertEquals(20, transition.sampleNpc("example", ModelAnimation.play("walk"), 1, 5)
                .boneMatrix("root").m31(), 1E-5);
        assertEquals(10.625, transition.sampleNpc("example", ModelAnimation.play("walk"), 1.125, 5)
                .boneMatrix("root").m31(), 1E-5);
        assertEquals(2.5, transition.sampleNpc("example", ModelAnimation.play("walk"), 1.25, 5)
                .boneMatrix("root").m31(), 1E-5);
    }

    @Test void npcWithoutIdleRetainsItsStaticModelWhileMachinePlaybackRemainsStrict() {
        var model = simpleModel("""
                {"eat":{"animation_length":1,"bones":{"root":{"position":[0,20,0]}}}}
                """);
        var playback = new BlockbenchPlayback(model);
        playback.sampleNpc("example", ModelAnimation.hold("eat"), 0, 0);
        assertEquals(20, playback.sampleNpc("example", ModelAnimation.play("walk"), 1, 5)
                .boneMatrix("root").m31(), 1E-5);
        assertEquals(10, playback.sampleNpc("example", ModelAnimation.play("walk"), 1.125, 5)
                .boneMatrix("root").m31(), 1E-5);
        assertPose(snapshot(new NativeNpcPose(model)),
                playback.sampleNpc("example", ModelAnimation.play("walk"), 1.25, 5), "static fallback");
        var error = assertThrows(IllegalArgumentException.class,
                () -> new BlockbenchPlayback(model).sample(ModelAnimation.loop("walk"), 0, 0));
        assertEquals("Missing Blockbench animation: walk", error.getMessage());
    }

    @Test void warningsAreEmittedOnlyOncePerMissingClipAndPlaybackInstance() {
        var model = simpleModel("{\"idle\":{\"animation_length\":1,\"bones\":{}}}");
        var logger = (org.apache.logging.log4j.core.Logger) LogManager.getLogger(BlockbenchPlayback.class);
        var messages = new CopyOnWriteArrayList<String>();
        var appender = new AbstractAppender("npc-missing-clip-test", null, PatternLayout.createDefaultLayout(),
                false, Property.EMPTY_ARRAY) {
            @Override public void append(LogEvent event) {
                if (event.getMessage().getFormattedMessage().startsWith("Missing NPC Blockbench animation"))
                    messages.add(event.getMessage().getFormattedMessage());
            }
        };
        appender.start();
        var oldLevel = logger.getLevel();
        logger.setLevel(org.apache.logging.log4j.Level.WARN);
        logger.addAppender(appender);
        try {
            var playback = new BlockbenchPlayback(model);
            for (int frame = 0; frame < 20; frame++)
                playback.sampleNpc("example", ModelAnimation.loop("walk"), frame / 20.0, 0);
            playback.sampleNpc("example", ModelAnimation.loop("missing_action"), 2, 0);
            assertEquals(2, messages.size(), messages.toString());
            assertTrue(messages.get(0).contains("walk for example"));
            assertTrue(messages.get(0).contains("idle animation"));
            new BlockbenchPlayback(model).sampleNpc("example", ModelAnimation.loop("walk"), 0, 0);
            assertEquals(3, messages.size(), "A freshly reloaded playback instance can report its own missing content");
        } finally {
            logger.removeAppender(appender);
            logger.setLevel(oldLevel);
            appender.stop();
        }
    }

    @Test void guntherIsRegisteredWithItsNativeAuthoredIdleBlinkAndWalk() throws Exception {
        assertTrue(NpcModelOwnership.requiresNative("gunther"));
        assertTrue(NpcModelOwnership.requiresNative("stardewcraft:gunther"));
        assertThrows(IllegalStateException.class, () -> NpcModelOwnership.requireLegacy("gunther"));
        var data = resource("npc_native/gunther.json");
        var model = new Gson().fromJson(data, NativeNpcModel.class);
        assertEquals("stardewcraft:textures/entity/npc_native/gunther.png", model.texture());
        assertEquals(28, model.bones().size(), "The newly registered Gunther rig must survive native compilation");
        assertEquals(8.8, model.clips().get("animation.gunther.idle").length(), 1E-5);
        assertEquals(.23, model.clips().get("animation.gunther.blink").length(), 1E-5);
        assertEquals(1, model.clips().get("animation.gunther.walk").length(), 1E-5);
        for (String clip : List.of("idle", "blink", "walk")) {
            String qualified = NativeActorAnimation.resolve(model, "gunther", clip);
            assertEquals("animation.gunther." + clip, qualified);
            assertFalse(model.clips().get(qualified).tracks().isEmpty(), clip + " must contain authored motion");
            var playback = new BlockbenchPlayback(model);
            for (double time : new double[]{0, .05, .25, .75, 8.9})
                for (var matrix : playback.sampleNpc("gunther", ModelAnimation.loop(clip), time, 0).matrices())
                    assertTrue(matrix.isFinite(), "Gunther " + clip + " at " + time);
        }
        try (var texture = NpcModelOwnership.class.getClassLoader()
                .getResourceAsStream("assets/stardewcraft/textures/entity/npc_native/gunther.png")) {
            assertNotNull(texture, "Gunther's native texture must be included with the compiled model");
        }
    }
}

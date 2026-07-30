package com.stardew.craft.cutscene.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.core.ModMiningDimensions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatRescueCutsceneCoordinatorTest {
    @Test
    void authoringTableHasExactlyTheRequiredPointIdsAndHonestStatuses() {
        assertEquals(
                Set.of("M01", "M02", "M03", "M04",
                        "H01", "H02", "H03",
                        "I01", "I02", "I03", "D01"),
                CombatRescuePoints.ALL.keySet());

        assertTrue(CombatRescuePoints.allAuthorConfirmed(CombatRescuePoints.MINE));
        assertTrue(CombatRescuePoints.allAuthorConfirmed(CombatRescuePoints.HOSPITAL));
        assertTrue(CombatRescuePoints.allAuthorConfirmed(CombatRescuePoints.DESERT));
        assertFalse(CombatRescuePoints.allAuthorConfirmed(CombatRescuePoints.ISLAND));
        assertEquals(
                List.of("I01", "I02", "I03"),
                CombatRescuePoints.pendingPointIds(CombatRescuePoints.ISLAND));
        assertTrue(CombatRescuePoints.ALL.values().stream()
                .filter(point -> point.status() == CombatRescuePoints.Status.PENDING_AUTHORING)
                .allMatch(point -> point.id().startsWith("I")));
    }

    @Test
    void destinationsUseCapturedDimensionsAndPoints() {
        var mine = CombatRescueCutsceneCoordinator.MINE_PLAYER;
        assertEquals(ModMiningDimensions.STARDEW_MINING, mine.dimension());
        assertEquals(1.0D, mine.x());
        assertEquals(66.0D, mine.y());
        assertEquals(-10.0D, mine.z());
        assertEquals(90.0F, mine.yaw());

        var hospital = CombatRescueCutsceneCoordinator.HOSPITAL_PLAYER;
        assertEquals(ModDimensions.STARDEW_VALLEY, hospital.dimension());
        assertEquals(23.0D, hospital.x());
        assertEquals(43.0D, hospital.y());
        assertEquals(-16.0D, hospital.z());
        assertEquals(-90.0F, hospital.yaw());

        var desert = CombatRescueCutsceneCoordinator.DESERT_FESTIVAL_RECOVERY;
        assertEquals(ModDimensions.STARDEW_VALLEY, desert.dimension());
        assertEquals(-221.0D, desert.x());
        assertEquals(64.0D, desert.y());
        assertEquals(-193.0D, desert.z());
        assertEquals(0.0F, desert.yaw());
    }

    @Test
    void vanillaMineRollUsesSevenSlotsAndEligibleSpouseOverride() {
        assertEquals("robin", choice(0, 1.0D, null, false).npcId());
        assertEquals("clint", choice(1, 1.0D, null, false).npcId());

        var maruSpouse = choice(2, 1.0D, "Maru", false);
        assertEquals("maru", maruSpouse.npcId());
        assertEquals(
                CombatRescueCutsceneCoordinator.MineDialogue.MARU_SPOUSE,
                maruSpouse.dialogue());

        assertEquals("linus", choice(6, 1.0D, null, false).npcId());

        var spouse = choice(4, 0.05D, "Abigail", false);
        assertEquals("abigail", spouse.npcId());
        assertEquals(
                CombatRescueCutsceneCoordinator.MineDialogue.SPOUSE_PLAYER_MALE,
                spouse.dialogue());

        assertEquals("linus", choice(4, 0.05D, "Abigail", true).npcId());
    }

    @Test
    void vanillaIslandRollUsesWillyUntilLeoIsKnownThenFiftyPercentLeo() {
        assertEquals(
                "willy",
                CombatRescueCutsceneCoordinator
                        .selectVanillaIslandRescuer(new FixedBooleanRandom(true), false)
                        .npcId());
        assertEquals(
                "leo",
                CombatRescueCutsceneCoordinator
                        .selectVanillaIslandRescuer(new FixedBooleanRandom(true), true)
                        .npcId());
        assertEquals(
                "willy",
                CombatRescueCutsceneCoordinator
                        .selectVanillaIslandRescuer(new FixedBooleanRandom(false), true)
                        .npcId());
    }

    @Test
    void mineEventUsesEveryCapturedPointAndDynamicRescuer() throws Exception {
        JsonObject event = readEvent("combat_rescue_mine.json");
        JsonArray commands = event.getAsJsonArray("commands");

        assertTrue(hasActor(commands, "fake_player", "player",
                CombatRescuePoints.M01.x(), CombatRescuePoints.M01.y(),
                CombatRescuePoints.M01.z(), CombatRescuePoints.M01.yaw()));
        assertTrue(hasActor(commands, "rescuer", "$combat_rescuer",
                CombatRescuePoints.M02.x(), CombatRescuePoints.M02.y(),
                CombatRescuePoints.M02.z(), CombatRescuePoints.M02.yaw()));
        assertTrue(hasMove(commands, "rescuer",
                CombatRescuePoints.M03.x(), CombatRescuePoints.M03.y(),
                CombatRescuePoints.M03.z()));
        assertTrue(hasCamera(commands,
                CombatRescuePoints.M04.x(), CombatRescuePoints.M04.y(),
                CombatRescuePoints.M04.z(), CombatRescuePoints.M04.yaw(),
                CombatRescuePoints.M04.pitch()));
    }

    @Test
    void hospitalEventUsesEveryCapturedPoint() throws Exception {
        JsonObject event = readEvent("combat_rescue_hospital.json");
        JsonArray commands = event.getAsJsonArray("commands");

        assertTrue(hasActor(commands, "fake_player", "player",
                CombatRescuePoints.H01.x(), CombatRescuePoints.H01.y(),
                CombatRescuePoints.H01.z(), CombatRescuePoints.H01.yaw()));
        assertTrue(hasActor(commands, "harvey", "harvey",
                CombatRescuePoints.H02.x(), CombatRescuePoints.H02.y(),
                CombatRescuePoints.H02.z(), CombatRescuePoints.H02.yaw()));
        assertTrue(hasCamera(commands,
                CombatRescuePoints.H03.x(), CombatRescuePoints.H03.y(),
                CombatRescuePoints.H03.z(), CombatRescuePoints.H03.yaw(),
                CombatRescuePoints.H03.pitch()));
        assertFalse(commands.asList().stream().anyMatch(element ->
                "move_actor".equals(element.getAsJsonObject().get("cmd").getAsString())));
    }

    @Test
    void islandEventUsesOnlyTheExplicitPendingFallbackTable() throws Exception {
        JsonObject event = readEvent("combat_rescue_island.json");
        JsonArray commands = event.getAsJsonArray("commands");

        assertTrue(hasActor(commands, "fake_player", "player",
                CombatRescuePoints.I01.x(),
                CombatRescuePoints.I01.y(),
                CombatRescuePoints.I01.z(),
                CombatRescuePoints.I01.yaw()));
        assertTrue(hasActor(commands, "rescuer", "$combat_rescuer",
                CombatRescuePoints.I02.x(),
                CombatRescuePoints.I02.y(),
                CombatRescuePoints.I02.z(),
                CombatRescuePoints.I02.yaw()));
        assertTrue(hasCamera(commands,
                CombatRescuePoints.I03.x(),
                CombatRescuePoints.I03.y(),
                CombatRescuePoints.I03.z(),
                CombatRescuePoints.I03.yaw(),
                CombatRescuePoints.I03.pitch()));
        assertTrue(commands.asList().stream()
                .map(element -> element.getAsJsonObject())
                .filter(command -> "comment".equals(string(command, "cmd")))
                .map(command -> string(command, "text"))
                .anyMatch(text -> text.contains("PENDING AUTHORING")));
        assertFalse(commands.asList().stream().anyMatch(element ->
                "move_actor".equals(element.getAsJsonObject().get("cmd").getAsString())));
    }

    private static CombatRescueCutsceneCoordinator.MineRescueChoice choice(
            int slot,
            double spouseRoll,
            String spouse,
            boolean engaged
    ) {
        return CombatRescueCutsceneCoordinator.selectVanillaMineRescuer(
                new FixedRandom(slot, spouseRoll), spouse, engaged, true);
    }

    private static boolean hasActor(
            JsonArray commands,
            String actor,
            String npcId,
            double x,
            double y,
            double z,
            float facing
    ) {
        return commands.asList().stream()
                .map(element -> element.getAsJsonObject())
                .anyMatch(command -> "spawn_actor".equals(string(command, "cmd"))
                        && actor.equals(string(command, "actor"))
                        && npcId.equals(string(command, "npc_id"))
                        && x == number(command, "x")
                        && y == number(command, "y")
                        && z == number(command, "z")
                        && facing == number(command, "facing"));
    }

    private static boolean hasMove(
            JsonArray commands,
            String actor,
            double x,
            double y,
            double z
    ) {
        return commands.asList().stream()
                .map(element -> element.getAsJsonObject())
                .anyMatch(command -> "move_actor".equals(string(command, "cmd"))
                        && actor.equals(string(command, "actor"))
                        && x == number(command, "x")
                        && y == number(command, "y")
                        && z == number(command, "z"));
    }

    private static boolean hasCamera(
            JsonArray commands,
            double x,
            double y,
            double z,
            double yaw,
            double pitch
    ) {
        return commands.asList().stream()
                .map(element -> element.getAsJsonObject())
                .anyMatch(command -> "camera".equals(string(command, "cmd"))
                        && x == number(command, "x")
                        && y == number(command, "y")
                        && z == number(command, "z")
                        && Float.compare((float) yaw, (float) number(command, "yaw")) == 0
                        && Float.compare((float) pitch, (float) number(command, "pitch")) == 0);
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsString() : "";
    }

    private static double number(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsDouble() : Double.NaN;
    }

    private static JsonObject readEvent(String fileName) throws Exception {
        Path root = Path.of(System.getProperty("stardewcraft.projectDir"));
        Path path = root.resolve("src/main/resources/data/stardewcraft/cutscene_events")
                .resolve(fileName);
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private record FixedRandom(int slot, double spouseRoll) implements RandomGenerator {
        @Override
        public long nextLong() {
            return 0L;
        }

        @Override
        public int nextInt(int bound) {
            return Math.floorMod(slot, bound);
        }

        @Override
        public double nextDouble() {
            return spouseRoll;
        }
    }

    private record FixedBooleanRandom(boolean value) implements RandomGenerator {
        @Override
        public long nextLong() {
            return value ? 1L : 0L;
        }

        @Override
        public boolean nextBoolean() {
            return value;
        }
    }
}

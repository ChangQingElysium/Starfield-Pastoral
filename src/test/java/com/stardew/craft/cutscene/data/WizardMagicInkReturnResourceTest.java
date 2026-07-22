package com.stardew.craft.cutscene.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stardew.craft.world.WizardBuildingCatalogService;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WizardMagicInkReturnResourceTest {
    private static final Path PROJECT = Path.of(System.getProperty("stardewcraft.projectDir"));
    private static final List<String> LANGUAGES = List.of(
            "de_de", "en_us", "es_es", "fr_fr", "hu_hu", "it_it",
            "ja_jp", "ko_kr", "pt_br", "ru_ru", "tr_tr", "zh_cn");
    private static final List<String> DIALOGUE_KEYS = List.of(
            "found", "hesitation", "exwife", "dont_tell", "reward", "book", "thanks");

    @Test
    void usesConfirmedCatalogCameraAndActorPoints() throws Exception {
        EventData event = event();

        assertEquals("wizardhouse", event.trigger().location());
        assertArrayEquals(new double[] {-186, 33, 43}, event.trigger().areaMin());
        assertArrayEquals(new double[] {-172, 40, 65}, event.trigger().areaMax());
        assertTrue(event.preconditions().stream().anyMatch(condition ->
                "flag".equals(condition.type())
                        && "hasPickedUpMagicInk".equals(condition.getString("id"))));

        assertPoint(event, "camera", null, -185.736, 35.076, 52.262);
        JsonObject camera = event.rawCommands().stream()
                .filter(command -> "camera".equals(command.get("cmd").getAsString()) && command.has("x"))
                .findFirst()
                .orElseThrow();
        assertEquals(-23.6D, camera.get("yaw").getAsDouble());
        assertEquals(21.7D, camera.get("pitch").getAsDouble());
        assertPoint(event, "spawn_actor", "wizard", -186, 34, 55);
        assertPoint(event, "move_actor", "wizard", -185, 34, 55);
        assertPoint(event, "spawn_actor", "fake_player", -184, 34, 55);
        assertPoint(event, "place_player", null, -184, 34, 55);
        assertEquals(-185, WizardBuildingCatalogService.CATALOG_POS.getX());
        assertEquals(34, WizardBuildingCatalogService.CATALOG_POS.getY());
        assertEquals(53, WizardBuildingCatalogService.CATALOG_POS.getZ());
    }

    @Test
    void unlocksThePerPlayerCatalogAtTheVanillaSummoningBeat() throws Exception {
        EventData event = event();
        int fireball = commandIndex(event, "play_sound", "sound", "stardewcraft:fireball");
        int unlock = commandIndex(event, "set_flag", "flag", WizardBuildingCatalogService.UNLOCK_FLAG);
        int explanation = commandIndex(event, "speak", "text", "event.wizard_magic_ink.book");

        assertTrue(fireball >= 0);
        assertTrue(fireball < unlock);
        assertTrue(unlock < explanation);
        assertTrue(event.rawCommands().stream().anyMatch(command ->
                "music".equals(command.get("cmd").getAsString())
                        && "music_wizard_tower".equals(command.get("track").getAsString())));
    }

    @Test
    void bundlesBothStaticGeckoModelsAndTheirOriginalTextureSizes() throws Exception {
        JsonObject inactive = json("src/main/resources/assets/stardewcraft/geo/block/decor/"
                + "wizard_building_catalog_inactive.geo.json");
        JsonObject active = json("src/main/resources/assets/stardewcraft/geo/block/decor/"
                + "wizard_building_catalog_active.geo.json");
        assertEquals("geometry.stardewcraft.wizard_building_catalog.inactive", identifier(inactive));
        assertEquals("geometry.stardewcraft.wizard_building_catalog.active", identifier(active));

        var inactiveTexture = ImageIO.read(PROJECT.resolve(
                "src/main/resources/assets/stardewcraft/textures/block/decor/"
                        + "wizard_building_catalog_inactive.png").toFile());
        var activeTexture = ImageIO.read(PROJECT.resolve(
                "src/main/resources/assets/stardewcraft/textures/block/decor/"
                        + "wizard_building_catalog_active.png").toFile());
        assertNotNull(inactiveTexture);
        assertNotNull(activeTexture);
        assertEquals(128, inactiveTexture.getWidth());
        assertEquals(128, inactiveTexture.getHeight());
        assertEquals(64, activeTexture.getWidth());
        assertEquals(64, activeTexture.getHeight());
    }

    @Test
    void catalogHasARegisteredNameButNoRegisteredItem() throws Exception {
        String blocks = Files.readString(PROJECT.resolve(
                "src/main/java/com/stardew/craft/block/ModBlocks.java"));
        String items = Files.readString(PROJECT.resolve(
                "src/main/java/com/stardew/craft/item/ModItems.java"));
        assertTrue(blocks.contains("BLOCKS.register(\"wizard_building_catalog\""));
        assertFalse(items.contains("wizard_building_catalog"));

        for (String language : LANGUAGES) {
            JsonObject translations = json("src/main/resources/assets/stardewcraft/lang/" + language + ".json");
            assertTrue(translations.has("block.stardewcraft.wizard_building_catalog"), language);
            for (String suffix : DIALOGUE_KEYS) {
                assertTrue(translations.has("event.wizard_magic_ink." + suffix),
                        language + " is missing " + suffix);
            }
        }
    }

    private static String identifier(JsonObject geo) {
        return geo.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
                .getAsJsonObject("description").get("identifier").getAsString();
    }

    private static void assertPoint(EventData event, String type, String actor,
                                    double x, double y, double z) {
        assertTrue(event.rawCommands().stream().anyMatch(command ->
                type.equals(command.get("cmd").getAsString())
                        && (actor == null || actor.equals(command.get("actor").getAsString()))
                        && command.get("x").getAsDouble() == x
                        && command.get("y").getAsDouble() == y
                        && command.get("z").getAsDouble() == z));
    }

    private static int commandIndex(EventData event, String type, String key, String value) {
        for (int i = 0; i < event.rawCommands().size(); i++) {
            JsonObject command = event.rawCommands().get(i);
            if (type.equals(command.get("cmd").getAsString())
                    && command.has(key)
                    && value.equals(command.get(key).getAsString())) {
                return i;
            }
        }
        return -1;
    }

    private static EventData event() throws Exception {
        return EventData.fromJson(json(
                "src/main/resources/data/stardewcraft/cutscene_events/wizard_magic_ink_return.json"));
    }

    private static JsonObject json(String relativePath) throws Exception {
        return JsonParser.parseString(Files.readString(PROJECT.resolve(relativePath))).getAsJsonObject();
    }
}

package com.stardew.craft.casino;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CasinoResourceContractTest {
    private static final List<String> LOCALES = List.of(
            "de_de", "en_us", "es_es", "fr_fr", "hu_hu", "it_it",
            "ja_jp", "ko_kr", "pt_br", "ru_ru", "tr_tr", "zh_cn"
    );

    private static final List<String> REQUIRED_KEYS = List.of(
            "block.stardewcraft.club_computer",
            "block.stardewcraft.calico_jack_table",
            "block.stardewcraft.slot_machine",
            "stardewcraft.casino.farmer_file.1",
            "stardewcraft.casino.farmer_file.2",
            "stardewcraft.casino.not_enough_club_coins",
            "stardewcraft.casino.not_enough_club_coins_high_stakes",
            "stardewcraft.casino.calico_jack.prompt",
            "stardewcraft.casino.calico_jack.prompt_high_stakes",
            "stardewcraft.casino.calico_jack.play",
            "stardewcraft.casino.calico_jack.leave",
            "stardewcraft.casino.calico_jack.rules",
            "stardewcraft.casino.calico_jack.rules_1",
            "stardewcraft.casino.calico_jack.rules_2",
            "stardewcraft.casino.calico_jack.hit",
            "stardewcraft.casino.calico_jack.stand",
            "stardewcraft.casino.calico_jack.win",
            "stardewcraft.casino.calico_jack.lose",
            "stardewcraft.casino.calico_jack.tie",
            "stardewcraft.casino.calico_jack.blackjack",
            "stardewcraft.casino.calico_jack.player_bust",
            "stardewcraft.casino.calico_jack.dealer_bust",
            "stardewcraft.casino.calico_jack.push",
            "stardewcraft.casino.calico_jack.closest",
            "stardewcraft.casino.calico_jack.dealer_closest",
            "stardewcraft.casino.calico_jack.double_or_nothing",
            "stardewcraft.casino.calico_jack.play_again",
            "stardewcraft.casino.calico_jack.quit",
            "stardewcraft.casino.calico_jack.dealer",
            "stardewcraft.casino.calico_jack.player",
            "stardewcraft.casino.calico_jack.wager",
            "stardewcraft.casino.calico_jack.result",
            "stardewcraft.casino.slots.bet_10",
            "stardewcraft.casino.slots.bet_100",
            "stardewcraft.casino.slots.done",
            "stardewcraft.casino.slots.jackpot",
            "entity.stardewcraft.npc.bouncer",
            "stardewcraft.npc.bouncer.members_only",
            "stardewcraft.npc.bouncer.club_card_question",
            "stardewcraft.npc.bouncer.club_card_yes",
            "stardewcraft.npc.bouncer.club_card_insult",
            "stardewcraft.npc.bouncer.may_enter",
            "stardewcraft.npc.bouncer.nice_try",
            "stardewcraft.npc.bouncer.angry_1",
            "stardewcraft.npc.bouncer.angry_2",
            "entity.stardewcraft.npc.mister_qi",
            "entity.stardewcraft.npc.club_seller",
            "stardewcraft.npc.mister_qi.casino_member",
            "stardewcraft.npc.club_seller.offer",
            "stardewcraft.npc.club_seller.yes",
            "stardewcraft.npc.club_seller.no",
            "stardewcraft.npc.club_seller.not_enough_money",
            "stardewcraft.casino.buy_qi_coins.question",
            "stardewcraft.casino.not_enough_money",
            "stardewcraft.shop.currency.qi_coins",
            "block.stardewcraft.statue_of_endless_fortune",
            "block.stardewcraft.statue_of_endless_fortune.desc",
            "block.stardewcraft.statue_of_perfection",
            "block.stardewcraft.statue_of_perfection.desc",
            "stardewcraft.location.casino"
    );

    @Test
    void everyShippedLocaleContainsTheFullCasinoTextSurface() throws IOException {
        for (String locale : LOCALES) {
            JsonObject language = json(resource("assets/stardewcraft/lang/" + locale + ".json"));
            for (String key : REQUIRED_KEYS) {
                assertTrue(language.has(key), locale + " is missing " + key);
                assertFalse(language.get(key).getAsString().isBlank(), locale + " has blank " + key);
            }
        }
    }

    @Test
    void misterQiUsesTheCanonicalSourcePortrait() throws IOException {
        var portrait = ImageIO.read(resource(
                "assets/stardewcraft/textures/portraits/mrqi.png").toFile());
        assertNotNull(portrait);
        assertEquals(128, portrait.getWidth());
        assertEquals(64, portrait.getHeight());

        String registry = Files.readString(project().resolve(
                "src/main/java/com/stardew/craft/api/v1/internal/npc/StardewNpcDisplayRegistry.java"));
        assertTrue(registry.contains("\"mister_qi\".equals(path)"));
        assertTrue(registry.contains("? \"mrqi\""));
    }

    @Test
    void tallCasinoStatueIconsUseSquareTransparentCanvases() throws IOException {
        for (String id : List.of("statue_of_endless_fortune", "statue_of_perfection")) {
            var icon = ImageIO.read(resource(
                    "assets/stardewcraft/textures/item/" + id + ".png").toFile());
            assertNotNull(icon, id);
            assertEquals(32, icon.getWidth(), id);
            assertEquals(32, icon.getHeight(), id);
            for (int y = 0; y < icon.getHeight(); y++) {
                for (int x = 0; x < 8; x++) {
                    assertEquals(0, icon.getRGB(x, y) >>> 24, id + " left padding");
                    assertEquals(0, icon.getRGB(24 + x, y) >>> 24, id + " right padding");
                }
            }
        }
    }

    @Test
    void casinoNpcSpawnsMatchTheApprovedLayout() throws IOException {
        JsonObject spawns = json(resource(
                "data/stardewcraft/npc/events/default_spawns.json"))
                .getAsJsonObject("spawns");
        JsonObject seller = spawns.getAsJsonObject("club_seller");
        assertEquals(-232.0D, seller.get("x").getAsDouble(), 0.0001D);
        assertEquals(36.0D, seller.get("y").getAsDouble(), 0.0001D);
        assertEquals(-169.0D, seller.get("z").getAsDouble(), 0.0001D);
    }

    @Test
    void authoredModelsKeepTheirExpectedVerticalPlacementAndLocalTextures() throws IOException {
        assertModelBounds("calico_jack_table", 0.0D, 15.0D);
        assertModelBounds("calico_jack_table_high_stakes", 0.0D, 15.0D);
        assertModelBounds("club_computer", 0.0D, 28.0D);
        assertModelBounds("club_computer_upper", 5.0D, 23.0D);
        assertModelBounds("club_computer_item", -7.0D, 32.0D);
        assertModelBounds("slot_machine", 0.0D, 26.5D);

        for (String id : List.of(
                "calico_jack_table",
                "calico_jack_table_high_stakes",
                "club_computer",
                "club_computer_upper",
                "club_computer_item",
                "slot_machine"
        )) {
            JsonObject model = json(resource("assets/stardewcraft/models/casino/" + id + ".json"));
            assertVanillaElementBounds(id, model.getAsJsonArray("elements"));
            for (var texture : model.getAsJsonObject("textures").entrySet()) {
                String reference = texture.getValue().getAsString();
                assertTrue(reference.startsWith("stardewcraft:block/casino/"), reference);
                String relative = reference.substring("stardewcraft:".length()) + ".png";
                assertTrue(Files.isRegularFile(resource("assets/stardewcraft/textures/" + relative)),
                        "Missing texture " + relative);
            }
        }
    }

    @Test
    void groundedClubComputerSlicesReassembleTheAuthoredModelExactly() throws IOException {
        JsonArray authored = json(resource(
                "assets/stardewcraft/models/casino/club_computer_item.json"))
                .getAsJsonArray("elements");
        JsonArray lower = json(resource(
                "assets/stardewcraft/models/casino/club_computer.json"))
                .getAsJsonArray("elements");
        JsonArray upper = json(resource(
                "assets/stardewcraft/models/casino/club_computer_upper.json"))
                .getAsJsonArray("elements");

        List<String> expected = translatedEndpoints(authored, 7.0D);
        List<String> assembled = new ArrayList<>(translatedEndpoints(lower, 0.0D));
        assembled.addAll(translatedEndpoints(upper, 16.0D));
        Collections.sort(assembled);

        assertEquals(expected, assembled);
    }

    @Test
    void blockstatesCoverEveryIntegratedPartFacingAndHighStakesVariant() throws IOException {
        assertEquals(24, variants("club_computer").size());
        assertEquals(
                "stardewcraft:casino/club_computer_upper",
                variants("club_computer")
                        .getAsJsonObject("part=extension,facing=north,model_slice=1")
                        .get("model").getAsString()
        );
        assertEquals(8, variants("slot_machine").size());
        JsonObject calico = variants("calico_jack_table");
        assertEquals(16, calico.size());
        assertTrue(calico.has("part=main,facing=north,high_stakes=false"));
        assertTrue(calico.has("part=main,facing=north,high_stakes=true"));
        assertTrue(calico.has("part=extension,facing=west,high_stakes=true"));
        assertEquals(
                "stardewcraft:casino/calico_jack_table_high_stakes",
                calico.getAsJsonObject("part=main,facing=north,high_stakes=true")
                        .get("model").getAsString()
        );
    }

    @Test
    void blockItemsExistWithoutJoiningTheFurnitureCatalogue() throws IOException {
        Path project = project();
        String items = Files.readString(project.resolve(
                "src/main/java/com/stardew/craft/item/ModItems.java"));
        for (String id : List.of("club_computer", "calico_jack_table", "slot_machine")) {
            assertTrue(items.contains("ITEMS.register(\"" + id + "\""));
            JsonObject itemModel = json(resource("assets/stardewcraft/models/item/" + id + ".json"));
            assertEquals(
                    "stardewcraft:casino/" + (id.equals("club_computer") ? "club_computer_item" : id),
                    itemModel.get("parent").getAsString());
        }
        assertTrue(items.contains(
                "ModBlocks.CLUB_COMPUTER.get(), \"stardewcraft.type.hidden\""));
        assertTrue(items.contains(
                "ModBlocks.CALICO_JACK_TABLE.get(), \"stardewcraft.type.hidden\""));
        assertTrue(items.contains(
                "ModBlocks.SLOT_MACHINE.get(), \"stardewcraft.type.hidden\""));
        assertFalse(items.contains(
                "ModBlocks.CLUB_COMPUTER.get(), \"stardewcraft.type.furniture\""));
        assertFalse(items.contains(
                "ModBlocks.CALICO_JACK_TABLE.get(), \"stardewcraft.type.furniture\""));
        assertFalse(items.contains(
                "ModBlocks.SLOT_MACHINE.get(), \"stardewcraft.type.furniture\""));
    }

    @Test
    void casinoGuiUsesOnlyCroppedStandaloneTextures() throws IOException {
        Path casino = resource("assets/stardewcraft/textures/gui/casino");
        for (int icon = 0; icon < 8; icon++) {
            var image = ImageIO.read(casino.resolve("slot_icon_" + icon + ".png").toFile());
            assertEquals(16, image.getWidth());
            assertEquals(16, image.getHeight());
        }
        for (String locale : LOCALES) {
            var title = ImageIO.read(casino.resolve("slot_title_" + locale + ".png").toFile());
            assertEquals(66, title.getWidth(), locale);
            assertEquals(13, title.getHeight(), locale);

            int extraWidth = switch (locale) {
                case "de_de" -> 3;
                case "fr_fr" -> 6;
                case "hu_hu" -> 4;
                case "it_it" -> 2;
                case "pt_br" -> 10;
                case "ru_ru" -> 9;
                default -> 0;
            };
            assertImageSize(casino.resolve("slot_button_10_" + locale + ".png"),
                    32 + extraWidth, 13);
            assertImageSize(casino.resolve("slot_button_100_" + locale + ".png"),
                    37 + extraWidth, 13);
            assertImageSize(casino.resolve("slot_button_done_" + locale + ".png"),
                    30 + extraWidth, 13);
        }
        assertImageSize(casino.resolve("slot_backdrop_box.png"), 3, 3);
        assertImageSize(casino.resolve("slot_gradient_line.png"), 3, 3);
        try (var files = Files.list(casino)) {
            assertTrue(files.noneMatch(path -> {
                String name = path.getFileName().toString().toLowerCase();
                return name.contains("cursors") || name.contains("springobjects");
            }));
        }
    }

    @Test
    void qiCoinAndBouncerUseLocalStandaloneResources() throws IOException {
        assertImageSize(resource(
                "assets/stardewcraft/textures/gui/common/qi_coin.png"), 9, 10);
        assertImageSize(resource(
                "assets/stardewcraft/textures/entity/npc/bouncer.png"), 128, 128);
        assertTrue(Files.isRegularFile(resource(
                "assets/stardewcraft/geo/entity/npc/bouncer.geo.json")));
        assertTrue(Files.isRegularFile(resource(
                "assets/stardewcraft/animations/entity/npc/bouncer.animation.json")));

        String calicoJack = Files.readString(project().resolve(
                "src/main/java/com/stardew/craft/client/gui/casino/CalicoJackScreen.java"));
        String slots = Files.readString(project().resolve(
                "src/main/java/com/stardew/craft/client/gui/casino/SlotsScreen.java"));
        assertTrue(calicoJack.contains("drawQiCoin"));
        assertTrue(slots.contains("drawQiCoin"));
        assertFalse(calicoJack.contains("drawShopCoin"));
        assertFalse(slots.contains("drawShopCoin"));
    }

    @Test
    void casinoShopAndStatueResourcesMatchTheOriginalInventory() throws IOException {
        JsonObject shop = json(resource("data/stardewcraft/shops/casino.json"));
        JsonArray entries = shop.getAsJsonArray("entries");
        assertEquals(10, entries.size());
        assertEquals("stardewcraft:top_hat",
                entries.get(0).getAsJsonObject().get("item").getAsString());
        assertEquals(8000, entries.get(0).getAsJsonObject().get("price").getAsInt());
        assertEquals("stardewcraft:scarecrow_3",
                entries.get(1).getAsJsonObject().get("item").getAsString());
        assertEquals(10000, entries.get(1).getAsJsonObject().get("price").getAsInt());
        for (int i = 0; i < 3; i++) {
            String color = List.of("red", "purple", "green").get(i);
            JsonObject firework = entries.get(4 + i).getAsJsonObject();
            assertEquals("stardewcraft:casino_firework_" + color,
                    firework.get("item").getAsString());
            assertEquals(200, firework.get("price").getAsInt());
            assertEquals(20, firework.get("stock").getAsInt());

            assertImageSize(resource(
                    "assets/stardewcraft/textures/item/casino_firework_" + color + ".png"),
                    16, 16);
            JsonObject model = json(resource(
                    "assets/stardewcraft/models/item/casino_firework_" + color + ".json"));
            assertEquals("minecraft:item/generated", model.get("parent").getAsString());
            assertEquals("stardewcraft:item/casino_firework_" + color,
                    model.getAsJsonObject("textures").get("layer0").getAsString());
        }
        assertEquals("wallpaper:MoreWalls:24",
                entries.get(7).getAsJsonObject().get("item").getAsString());
        assertEquals("wallpaper:MoreWalls:25",
                entries.get(8).getAsJsonObject().get("item").getAsString());
        assertEquals("flooring:MoreFloors:8",
                entries.get(9).getAsJsonObject().get("item").getAsString());

        assertImageSize(resource(
                "assets/stardewcraft/textures/item/statue_of_endless_fortune.png"), 32, 32);
        assertImageSize(resource(
                "assets/stardewcraft/textures/item/statue_of_perfection.png"), 32, 32);
        for (String id : List.of("statue_of_endless_fortune", "statue_of_perfection")) {
            JsonObject itemModel = json(resource("assets/stardewcraft/models/item/" + id + ".json"));
            assertEquals("minecraft:item/generated", itemModel.get("parent").getAsString());
            assertEquals("stardewcraft:item/" + id,
                    itemModel.getAsJsonObject("textures").get("layer0").getAsString());
        }
    }

    @Test
    void rareBatCardUsesTheExtractedOriginalBatScreech() throws IOException {
        JsonObject sounds = json(resource("assets/stardewcraft/sounds.json"));
        assertTrue(sounds.has("bat_screech"));
        assertTrue(Files.isRegularFile(resource(
                "assets/stardewcraft/sounds/bat_screech.ogg")));

        String screen = Files.readString(project().resolve(
                "src/main/java/com/stardew/craft/client/gui/casino/CalicoJackScreen.java"));
        assertTrue(screen.contains("ModSounds.BAT_SCREECH.get()"));
        assertFalse(screen.contains("ModSounds.SHADOW_DIE.get()"));
    }

    @Test
    void farmerFileTranslationsUseMinecraftArgumentPlaceholders() throws IOException {
        for (String locale : LOCALES) {
            JsonObject language = json(resource("assets/stardewcraft/lang/" + locale + ".json"));
            String pageOne = language.get("stardewcraft.casino.farmer_file.1").getAsString();
            String pageTwo = language.get("stardewcraft.casino.farmer_file.2").getAsString();
            assertTrue(pageOne.contains("%1$s"), locale);
            assertTrue(pageOne.contains("%8$s"), locale);
            assertTrue(pageTwo.contains("%1$s"), locale);
            assertTrue(pageTwo.contains("%5$s"), locale);
            assertFalse(pageOne.matches(".*\\{[0-7]}.*"), locale);
            assertFalse(pageTwo.matches(".*\\{[0-4]}.*"), locale);
        }
    }

    @Test
    void everyParameterizedCasinoTranslationUsesMinecraftPlaceholders() throws IOException {
        for (String locale : LOCALES) {
            JsonObject language = json(resource("assets/stardewcraft/lang/" + locale + ".json"));
            for (var entry : language.entrySet()) {
                if (!entry.getKey().startsWith("stardewcraft.casino.")) {
                    continue;
                }
                String value = entry.getValue().getAsString();
                assertFalse(value.matches("(?s).*\\{[0-9]+}.*"),
                        locale + " still contains a C# placeholder in " + entry.getKey());
            }
            assertTrue(language.get("stardewcraft.casino.calico_jack.player")
                    .getAsString().contains("%2$s"), locale);
            assertTrue(language.get("stardewcraft.casino.calico_jack.result")
                    .getAsString().contains("%1$s"), locale);
            assertTrue(language.get("stardewcraft.casino.slots.jackpot")
                    .getAsString().contains("%1$s"), locale);
        }
    }

    private static void assertImageSize(Path path, int width, int height) throws IOException {
        var image = ImageIO.read(path.toFile());
        assertEquals(width, image.getWidth(), path.getFileName().toString());
        assertEquals(height, image.getHeight(), path.getFileName().toString());
    }

    private static List<String> translatedEndpoints(JsonArray elements, double translateY) {
        List<String> endpoints = new ArrayList<>();
        for (var element : elements) {
            JsonObject object = element.getAsJsonObject();
            JsonArray from = object.getAsJsonArray("from");
            JsonArray to = object.getAsJsonArray("to");
            endpoints.add(
                    from.get(0).getAsDouble() + ","
                            + (from.get(1).getAsDouble() + translateY) + ","
                            + from.get(2).getAsDouble() + "->"
                            + to.get(0).getAsDouble() + ","
                            + (to.get(1).getAsDouble() + translateY) + ","
                            + to.get(2).getAsDouble());
        }
        Collections.sort(endpoints);
        return endpoints;
    }

    private static void assertModelBounds(String id, double expectedMin, double expectedMax)
            throws IOException {
        JsonArray elements = json(resource("assets/stardewcraft/models/casino/" + id + ".json"))
                .getAsJsonArray("elements");
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (var element : elements) {
            JsonObject object = element.getAsJsonObject();
            min = Math.min(min, object.getAsJsonArray("from").get(1).getAsDouble());
            max = Math.max(max, object.getAsJsonArray("to").get(1).getAsDouble());
        }
        assertEquals(expectedMin, min, 0.0001D, id);
        assertEquals(expectedMax, max, 0.0001D, id);
    }

    private static void assertVanillaElementBounds(String id, JsonArray elements) {
        for (var element : elements) {
            JsonObject object = element.getAsJsonObject();
            for (String endpoint : List.of("from", "to")) {
                JsonArray coordinates = object.getAsJsonArray(endpoint);
                for (int axis = 0; axis < coordinates.size(); axis++) {
                    double value = coordinates.get(axis).getAsDouble();
                    assertTrue(value >= -16.0D && value <= 32.0D,
                            id + " " + endpoint + "[" + axis + "]=" + value
                                    + " exceeds Minecraft's [-16, 32] model limit");
                }
            }
        }
    }

    private static JsonObject variants(String id) throws IOException {
        return json(resource("assets/stardewcraft/blockstates/" + id + ".json"))
                .getAsJsonObject("variants");
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static Path resource(String relative) {
        return project().resolve("src/main/resources").resolve(relative);
    }

    private static Path project() {
        return Path.of(System.getProperty("stardewcraft.projectDir", "."));
    }
}

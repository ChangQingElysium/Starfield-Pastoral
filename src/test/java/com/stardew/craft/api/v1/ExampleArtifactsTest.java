package com.stardew.craft.api.v1;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.api.v1.item.StardewItemData;
import com.stardew.craft.api.v1.agriculture.StardewAnimalData;
import com.stardew.craft.api.v1.agriculture.StardewBuildingData;
import com.stardew.craft.api.v1.agriculture.StardewCropData;
import com.stardew.craft.api.v1.agriculture.StardewTreeData;
import com.stardew.craft.api.v1.equipment.StardewEquipmentData;
import com.stardew.craft.api.v1.internal.BuiltinApiTypes;
import com.stardew.craft.api.v1.mail.StardewMailDefinition;
import com.stardew.craft.api.v1.production.StardewCookingRecipeDefinition;
import com.stardew.craft.api.v1.production.StardewCraftingRecipeDefinition;
import com.stardew.craft.api.v1.fishing.StardewFishingTreasurePoolDefinition;
import com.stardew.craft.api.v1.festival.StardewFestivalDefinition;
import com.stardew.craft.api.v1.fishpond.StardewFishPondDefinition;
import com.stardew.craft.api.v1.guild.StardewMonsterSlayerGoalDefinition;
import com.stardew.craft.api.v1.museum.StardewLostBookDefinition;
import com.stardew.craft.api.v1.museum.StardewMuseumRewardDefinition;
import com.stardew.craft.api.v1.mastery.StardewMasteryRewardDefinition;
import com.stardew.craft.api.v1.mining.StardewMineThemeDefinition;
import com.stardew.craft.api.v1.npc.StardewNpcInteractions;
import com.stardew.craft.api.v1.loot.StardewGeodeDropDefinition;
import com.stardew.craft.api.v1.loot.StardewMineChestRewardDefinition;
import com.stardew.craft.api.v1.loot.StardewPrizeTicketRewardDefinition;
import com.stardew.craft.api.v1.quest.StardewQuestDefinition;
import com.stardew.craft.api.v1.profession.StardewProfessionDefinition;
import com.stardew.craft.api.v1.shop.StardewShopDefinition;
import com.stardew.craft.api.v1.world.StardewForageZoneDefinition;
import com.stardew.craft.api.v1.world.StardewLocationDefinition;
import com.stardew.craft.api.v1.world.StardewPortalDefinition;
import com.stardew.craft.api.v1.world.StardewWorldLootPoolDefinition;
import com.stardew.craft.player.UnlockSourceData;
import com.stardew.craft.quest.data.DailyQuestPoolDefinition;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExampleArtifactsTest {
    @Test
    void exampleDatapackMetadataMatchesThePublicCodec() throws Exception {
        Path root = Path.of(System.getProperty("stardewcraft.projectDir"))
                .resolve("examples/stardewcraft-data-pack");
        var pack = JsonParser.parseString(Files.readString(root.resolve("pack.mcmeta"))).getAsJsonObject();
        var dataMap = JsonParser.parseString(Files.readString(root.resolve(
                "data/stardewcraft/data_maps/item/stardew_item_data.json"))).getAsJsonObject();
        var sample = dataMap.getAsJsonObject("values")
                .getAsJsonObject("#example_stardew_addon:sample_crops");

        var decoded = StardewItemData.CODEC.parse(JsonOps.INSTANCE, sample).result();

        assertTrue(pack.getAsJsonObject("pack").get("pack_format").getAsInt() > 0);
        assertTrue(decoded.isPresent());
        assertEquals(50, decoded.get().baseSellPrice());
        assertEquals("example_stardew_addon:sample_crop", decoded.get().category().toString());
    }

    @Test
    void exampleQuestChainMatchesThePublicDefinitionCodec() throws Exception {
        BuiltinApiTypes.bootstrap();
        Path quests = Path.of(System.getProperty("stardewcraft.projectDir"))
                .resolve("examples/stardewcraft-data-pack/data/example_stardew_addon/quests/sample_chain");
        var startJson = JsonParser.parseString(Files.readString(quests.resolve("start.json")));
        var finishJson = JsonParser.parseString(Files.readString(quests.resolve("finish.json")));

        StardewQuestDefinition start = StardewQuestDefinition.CODEC
                .parse(JsonOps.INSTANCE, startJson).result().orElseThrow();
        StardewQuestDefinition finish = StardewQuestDefinition.CODEC
                .parse(JsonOps.INSTANCE, finishJson).result().orElseThrow();

        assertEquals("stardewcraft:item_harvest", start.objective().type().toString());
        assertEquals(2, start.onComplete().size());
        assertEquals("stardewcraft:crafting", finish.objective().type().toString());
        assertEquals(100, finish.moneyReward());
        assertEquals(1, finish.availableWhen().size());
    }

    @Test
    void bundledDailyQuestPoolMatchesItsCodec() throws Exception {
        Path file = Path.of(System.getProperty("stardewcraft.projectDir"))
                .resolve("src/main/resources/data/stardewcraft/daily_quest_pools/default.json");
        var json = JsonParser.parseString(Files.readString(file));

        DailyQuestPoolDefinition pool = DailyQuestPoolDefinition.CODEC
                .parse(JsonOps.INSTANCE, json).result().orElseThrow();

        assertEquals(4, pool.deliveryItemsBySeason().size());
        assertEquals(4, pool.fishBySeason().size());
        assertTrue(pool.deliveryNpcs().size() > 10);
        assertTrue(!pool.resources().isEmpty());
        assertTrue(!pool.monsters().isEmpty());
    }

    @Test
    void phaseThreeExampleDefinitionsMatchTheirPublicSchemas() throws Exception {
        BuiltinApiTypes.bootstrap();
        Path data = Path.of(System.getProperty("stardewcraft.projectDir"))
                .resolve("examples/stardewcraft-data-pack/data/example_stardew_addon");

        var shopJson = JsonParser.parseString(Files.readString(data.resolve("shops/apple_stand.json")));
        var mailJson = JsonParser.parseString(Files.readString(data.resolve("mail/apple_club.json")));
        var eventJson = JsonParser.parseString(Files.readString(
                data.resolve("cutscene_events/apple_club_intro.json"))).getAsJsonObject();
        var orderJson = JsonParser.parseString(Files.readString(
                data.resolve("special_orders/apple_hunt.json"))).getAsJsonObject();
        var machineJson = JsonParser.parseString(Files.readString(
                data.resolve("artisan/apple_keg.json"))).getAsJsonObject();
        var cookingJson = JsonParser.parseString(Files.readString(
                data.resolve("cooking/recipes/apple_snack.json")));
        var craftingJson = JsonParser.parseString(Files.readString(
                data.resolve("player/crafting_recipes/apple_crate.json")));
        var unlockJson = JsonParser.parseString(Files.readString(
                data.resolve("player/unlock_sources/apple_club.json")));

        StardewShopDefinition shop = StardewShopDefinition.CODEC
                .parse(JsonOps.INSTANCE, shopJson).result().orElseThrow();
        StardewMailDefinition mail = StardewMailDefinition.CODEC
                .parse(JsonOps.INSTANCE, mailJson).result().orElseThrow();

        assertEquals(1, shop.entries().size());
        assertEquals("example_stardew_addon:daily_apples", shop.inventoryProviders().getFirst().toString());
        assertEquals(2, mail.onRead().size());
        assertEquals("example_stardew_addon:apple_club_intro", eventJson.get("id").getAsString());
        assertEquals("collect", orderJson.getAsJsonArray("objectives")
                .get(0).getAsJsonObject().get("type").getAsString());
        assertEquals("stardewcraft:keg", machineJson.get("machine").getAsString());
        assertEquals("minecraft:apple", machineJson.get("input").getAsString());
        assertTrue(machineJson.get("minutes").getAsInt() > 0);
        StardewCookingRecipeDefinition cooking = StardewCookingRecipeDefinition.CODEC
                .parse(JsonOps.INSTANCE, cookingJson).result().orElseThrow();
        assertEquals("minecraft:golden_apple", cooking.output().toString());
        assertEquals("minecraft:apple", cooking.ingredients().getFirst().item().orElseThrow().toString());
        StardewCraftingRecipeDefinition crafting = StardewCraftingRecipeDefinition.CODEC
                .parse(JsonOps.INSTANCE, craftingJson).result().orElseThrow();
        UnlockSourceData.UnlockBundle unlocks = UnlockSourceData.UnlockBundle.CODEC
                .parse(JsonOps.INSTANCE, unlockJson).result().orElseThrow();
        assertEquals("minecraft:chest", crafting.output().toString());
        assertEquals(4, crafting.ingredients().getFirst().count());
        assertTrue(unlocks.recipes().contains("example_stardew_addon:apple_crate"));
        assertEquals("stardewcraft:skill/farming/2",
                UnlockSourceData.normalizeSourceId("skill:farming:2").toString());
        assertEquals("example_stardew_addon:apple_club",
                UnlockSourceData.normalizeSourceId("example_stardew_addon:apple_club").toString());
    }

    @Test
    void collectionSystemExamplesMatchTheirPublicSchemas() throws Exception {
        BuiltinApiTypes.bootstrap();
        Path data = Path.of(System.getProperty("stardewcraft.projectDir"))
                .resolve("examples/stardewcraft-data-pack/data/example_stardew_addon");

        var treasureJson = JsonParser.parseString(Files.readString(
                data.resolve("fishing/treasure_pools/apple_bonus.json")));
        var pondJson = JsonParser.parseString(Files.readString(
                data.resolve("fishpond/pond_data/apple_fish.json")));
        var goalJson = JsonParser.parseString(Files.readString(
                data.resolve("adventurers_guild/monster_slayer_goals/apple_hunter.json")));
        var museumJson = JsonParser.parseString(Files.readString(
                data.resolve("museum_rewards/rewards/apple_collection.json")));
        var lostBookJson = JsonParser.parseString(Files.readString(
                data.resolve("museum/lost_books/apple_archive.json")));
        var geodeJson = JsonParser.parseString(Files.readString(
                data.resolve("geode/drops/apple_crystal.json")));
        var prizeJson = JsonParser.parseString(Files.readString(
                data.resolve("prize_ticket/rewards/golden_apple.json")));
        var mineJson = JsonParser.parseString(Files.readString(
                data.resolve("mine_chest/rewards/floor_30.json")));

        var treasure = StardewFishingTreasurePoolDefinition.CODEC
                .parse(JsonOps.INSTANCE, treasureJson).result().orElseThrow();
        var pond = StardewFishPondDefinition.CODEC
                .parse(JsonOps.INSTANCE, pondJson).result().orElseThrow();
        var goal = StardewMonsterSlayerGoalDefinition.CODEC
                .parse(JsonOps.INSTANCE, goalJson).result().orElseThrow();
        var museum = StardewMuseumRewardDefinition.CODEC
                .parse(JsonOps.INSTANCE, museumJson).result().orElseThrow();
        var lostBook = StardewLostBookDefinition.CODEC
                .parse(JsonOps.INSTANCE, lostBookJson).result().orElseThrow();
        var geode = StardewGeodeDropDefinition.CODEC
                .parse(JsonOps.INSTANCE, geodeJson).result().orElseThrow();
        var prize = StardewPrizeTicketRewardDefinition.CODEC
                .parse(JsonOps.INSTANCE, prizeJson).result().orElseThrow();
        var mine = StardewMineChestRewardDefinition.CODEC
                .parse(JsonOps.INSTANCE, mineJson).result().orElseThrow();

        assertEquals(2, treasure.entries().size());
        assertEquals("minecraft:cod", pond.fish().toString());
        assertEquals(5, goal.requiredKills());
        assertEquals("specific_items", museum.condition());
        assertEquals(22, lostBook.unlockAt());
        assertEquals(1, lostBook.interactions().size());
        assertEquals("minecraft:amethyst_cluster", geode.inputs().getFirst().toString());
        assertEquals(2, geode.entries().size());
        assertEquals(5, prize.level());
        assertTrue(prize.matches(5));
        assertEquals(30, mine.floor());
    }

    @Test
    void bundledPrizeAndMineRewardTablesMatchTheirPublicSchemas() throws Exception {
        BuiltinApiTypes.bootstrap();
        Path data = Path.of(System.getProperty("stardewcraft.projectDir"))
                .resolve("src/main/resources/data/stardewcraft");
        var prizeEntries = JsonParser.parseString(Files.readString(
                data.resolve("prize_ticket/rewards.json"))).getAsJsonArray();
        var mineEntries = JsonParser.parseString(Files.readString(
                data.resolve("mine_chest/rewards.json"))).getAsJsonArray();

        for (var raw : prizeEntries) {
            var definition = raw.getAsJsonObject().deepCopy();
            definition.remove("id");
            StardewPrizeTicketRewardDefinition.CODEC.parse(JsonOps.INSTANCE, definition)
                    .result().orElseThrow();
        }
        for (var raw : mineEntries) {
            var definition = raw.getAsJsonObject().deepCopy();
            definition.remove("id");
            StardewMineChestRewardDefinition.CODEC.parse(JsonOps.INSTANCE, definition)
                    .result().orElseThrow();
        }

        assertEquals(28, prizeEntries.size());
        assertEquals(11, mineEntries.size());
        var repeating = prizeEntries.get(21).getAsJsonObject().deepCopy();
        repeating.remove("id");
        var decoded = StardewPrizeTicketRewardDefinition.CODEC
                .parse(JsonOps.INSTANCE, repeating).result().orElseThrow();
        assertTrue(decoded.matches(decoded.level()));
        assertTrue(decoded.matches(decoded.level() + decoded.repeatEvery()));
    }

    @Test
    void festivalDefinitionsMatchTheirPublicSchema() throws Exception {
        BuiltinApiTypes.bootstrap();
        Path root = Path.of(System.getProperty("stardewcraft.projectDir"));
        Path bundled = root.resolve("src/main/resources/data/stardewcraft/festivals");
        try (var files = Files.list(bundled)) {
            var definitions = files.filter(path -> path.toString().endsWith(".json")).toList();
            assertEquals(12, definitions.size());
            for (Path file : definitions) {
                StardewFestivalDefinition.CODEC.parse(JsonOps.INSTANCE,
                                JsonParser.parseString(Files.readString(file)))
                        .result().orElseThrow();
            }
        }

        Path example = root.resolve(
                "examples/stardewcraft-data-pack/data/example_stardew_addon/festivals/apple_day.json");
        StardewFestivalDefinition appleDay = StardewFestivalDefinition.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(example)))
                .result().orElseThrow();
        assertEquals(StardewFestivalDefinition.FestivalKind.PASSIVE, appleDay.type());
        assertEquals("Town", appleDay.world().location());
        assertEquals(5, appleDay.startDay());

        var invalid = JsonParser.parseString(Files.readString(example)).getAsJsonObject();
        invalid.addProperty("type", "unknown");
        assertTrue(StardewFestivalDefinition.CODEC.parse(JsonOps.INSTANCE, invalid).error().isPresent());
    }

    @Test
    void npcIdsHaveStableLegacyAndAddonNamespaces() {
        assertEquals("stardewcraft:lewis", StardewNpcInteractions.normalizeNpcId("Lewis").toString());
        assertEquals("example_stardew_addon:apple_keeper",
                StardewNpcInteractions.normalizeNpcId("example_stardew_addon:Apple_Keeper").toString());
    }

    @Test
    void advancedWorldAndProgressionExamplesMatchTheirPublicSchemas() throws Exception {
        BuiltinApiTypes.bootstrap();
        Path root = Path.of(System.getProperty("stardewcraft.projectDir"))
                .resolve("examples/stardewcraft-data-pack/data");
        Path addon = root.resolve("example_stardew_addon");

        assertTrue(StardewWorldLootPoolDefinition.CODEC.parse(JsonOps.INSTANCE, read(addon,
                "world_loot/apple_artifact.json")).result().isPresent());
        assertTrue(StardewForageZoneDefinition.CODEC.parse(JsonOps.INSTANCE, read(addon,
                "forage_zones/apple_grove.json")).result().isPresent());
        assertTrue(StardewMineThemeDefinition.CODEC.parse(JsonOps.INSTANCE, read(addon,
                "mine_themes/apple_floor.json")).result().isPresent());
        assertTrue(StardewLocationDefinition.CODEC.parse(JsonOps.INSTANCE, read(addon,
                "locations/apple_shed.json")).result().isPresent());
        assertTrue(StardewPortalDefinition.CODEC.parse(JsonOps.INSTANCE, read(addon,
                "interior_portals/apple_shed_exit.json")).result().isPresent());
        assertTrue(StardewMasteryRewardDefinition.CODEC.parse(JsonOps.INSTANCE, read(addon,
                "mastery_rewards/apple_farming.json")).result().isPresent());
        assertTrue(StardewProfessionDefinition.CODEC.parse(JsonOps.INSTANCE, read(root,
                "stardewcraft/professions/tiller.json")).result().isPresent());
    }

    @Test
    void advancedDataMapExamplesMatchTheirPublicSchemas() throws Exception {
        Path root = Path.of(System.getProperty("stardewcraft.projectDir"))
                .resolve("examples/stardewcraft-data-pack/data/stardewcraft/data_maps");

        assertTrue(StardewCropData.CODEC.parse(JsonOps.INSTANCE, dataMapValue(root,
                "block/stardew_crop_data.json", "minecraft:wheat")).result().isPresent());
        assertTrue(StardewTreeData.CODEC.parse(JsonOps.INSTANCE, dataMapValue(root,
                "block/stardew_tree_data.json", "minecraft:oak_log")).result().isPresent());
        assertTrue(StardewBuildingData.CODEC.parse(JsonOps.INSTANCE, dataMapValue(root,
                "block/stardew_building_data.json", "minecraft:crafting_table")).result().isPresent());
        assertTrue(StardewAnimalData.CODEC.parse(JsonOps.INSTANCE, dataMapValue(root,
                "entity_type/stardew_animal_data.json", "minecraft:cow")).result().isPresent());
        assertTrue(StardewEquipmentData.CODEC.parse(JsonOps.INSTANCE, dataMapValue(root,
                "item/stardew_equipment_data.json", "minecraft:diamond_sword")).result().isPresent());
    }

    private static com.google.gson.JsonElement read(Path root, String relativePath) throws Exception {
        return JsonParser.parseString(Files.readString(root.resolve(relativePath)));
    }

    private static com.google.gson.JsonElement dataMapValue(Path root, String relativePath, String key)
            throws Exception {
        return read(root, relativePath).getAsJsonObject()
                .getAsJsonObject("values").get(key);
    }
}

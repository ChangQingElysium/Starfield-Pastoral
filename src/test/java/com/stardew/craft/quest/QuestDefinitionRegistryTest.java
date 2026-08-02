package com.stardew.craft.quest;

import com.stardew.craft.api.v1.internal.BuiltinApiTypes;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestDefinitionRegistryTest {
    @BeforeAll
    static void loadDefinitions() {
        BuiltinApiTypes.bootstrap();
        QuestDataLoader.load();
    }

    @Test
    void legacyNumericIdsResolveToNamespacedDefinitions() {
        StardewQuest legacy = QuestDataLoader.createQuest("6");
        StardewQuest namespaced = QuestDataLoader.createQuest("stardewcraft:6");

        assertNotNull(legacy);
        assertNotNull(namespaced);
        assertInstanceOf(DataDrivenQuest.class, legacy);
        assertEquals("stardewcraft:6", legacy.getDefinitionId().toString());
        assertEquals(legacy.getDefinitionId(), namespaced.getDefinitionId());
        assertTrue(QuestDataLoader.idsEqual("6", "stardewcraft:6"));
    }

    @Test
    void definitionBackedPersistenceStoresOnlyMutableState() {
        StardewQuest quest = QuestDataLoader.createQuest("6");
        assertNotNull(quest);
        quest.setAccepted(true);
        quest.setShowNew(true);

        var state = quest.saveState();
        StardewQuest restored = StardewQuest.load(state);

        assertTrue(state.getBoolean("StateOnly"));
        assertTrue(!state.contains("Title"));
        assertTrue(restored.isAccepted());
        assertEquals("stardewcraft:6", restored.getDefinitionId().toString());
        assertEquals(quest.getTitleKey(), restored.getTitleKey());
    }

    @Test
    void ornateNecklaceReturnQuestsAreHiddenRuntimeQuests() {
        StardewQuest abigail = QuestDataLoader.createQuest("128");
        StardewQuest caroline = QuestDataLoader.createQuest("129");

        assertNotNull(abigail);
        assertNotNull(caroline);
        assertTrue(abigail.isSecretQuest());
        assertTrue(caroline.isSecretQuest());
    }

    @Test
    void ornateNecklaceInHandCanRepairAStaleItemFoundFlag() {
        StardewQuest abigail = QuestDataLoader.createQuest("128");
        StardewQuest caroline = QuestDataLoader.createQuest("129");

        assertNotNull(abigail);
        assertNotNull(caroline);
        abigail.setAccepted(true);
        caroline.setAccepted(true);

        assertTrue(abigail.matchesItemDelivery("abigail", "stardewcraft:ornate_necklace"));
        assertTrue(caroline.matchesItemDelivery("caroline", "stardewcraft:ornate_necklace"));
    }

    @Test
    void vanillaStoryQuests100101And103UseTheirSourceObjectiveTypes() {
        DataDrivenQuest lostAxe = assertInstanceOf(
                DataDrivenQuest.class, QuestDataLoader.createQuest("100"));
        DataDrivenQuest cauliflower = assertInstanceOf(
                DataDrivenQuest.class, QuestDataLoader.createQuest("101"));
        DataDrivenQuest paleAle = assertInstanceOf(
                DataDrivenQuest.class, QuestDataLoader.createQuest("103"));

        assertEquals("stardewcraft:lost_item", lostAxe.getObjectiveType().toString());
        assertEquals("stardewcraft:item_delivery", cauliflower.getObjectiveType().toString());
        assertEquals("stardewcraft:item_delivery", paleAle.getObjectiveType().toString());
        assertEquals(250, lostAxe.getMoneyReward());
        assertEquals(350, cauliflower.getMoneyReward());
        assertEquals(350, paleAle.getMoneyReward());

        lostAxe.setAccepted(true);
        cauliflower.setAccepted(true);
        paleAle.setAccepted(true);
        assertTrue(lostAxe.matchesItemDelivery("robin", "stardewcraft:lost_axe"));
        assertTrue(cauliflower.matchesItemDelivery("jodi", "stardewcraft:cauliflower"));
        assertTrue(paleAle.matchesItemDelivery("pam", "stardewcraft:pale_ale"));
    }

    @Test
    void vanillaStoryQuests104Through106UseItemDeliveryObjectives() {
        DataDrivenQuest cropResearch = assertInstanceOf(
                DataDrivenQuest.class, QuestDataLoader.createQuest("104"));
        DataDrivenQuest kneeTherapy = assertInstanceOf(
                DataDrivenQuest.class, QuestDataLoader.createQuest("105"));
        DataDrivenQuest cowsDelight = assertInstanceOf(
                DataDrivenQuest.class, QuestDataLoader.createQuest("106"));

        assertEquals("stardewcraft:item_delivery", cropResearch.getObjectiveType().toString());
        assertEquals("stardewcraft:item_delivery", kneeTherapy.getObjectiveType().toString());
        assertEquals("stardewcraft:item_delivery", cowsDelight.getObjectiveType().toString());
        assertEquals(550, cropResearch.getMoneyReward());
        assertEquals(200, kneeTherapy.getMoneyReward());
        assertEquals(500, cowsDelight.getMoneyReward());

        cropResearch.setAccepted(true);
        kneeTherapy.setAccepted(true);
        cowsDelight.setAccepted(true);
        assertTrue(cropResearch.matchesItemDelivery("demetrius", "stardewcraft:melon"));
        assertTrue(kneeTherapy.matchesItemDelivery("george", "stardewcraft:hot_pepper"));
        assertTrue(cowsDelight.matchesItemDelivery("marnie", "stardewcraft:amaranth"));
    }

    @Test
    void vanillaStoryQuests107Through109UseTheirSourceObjectiveTypes() {
        DataDrivenQuest blackberryBasket = assertInstanceOf(
                DataDrivenQuest.class, QuestDataLoader.createQuest("107"));
        DataDrivenQuest carvingPumpkins = assertInstanceOf(
                DataDrivenQuest.class, QuestDataLoader.createQuest("108"));
        DataDrivenQuest catchASquid = assertInstanceOf(
                DataDrivenQuest.class, QuestDataLoader.createQuest("109"));

        assertEquals("stardewcraft:lost_item", blackberryBasket.getObjectiveType().toString());
        assertEquals("stardewcraft:item_delivery", carvingPumpkins.getObjectiveType().toString());
        assertEquals("stardewcraft:item_delivery", catchASquid.getObjectiveType().toString());
        assertEquals(0, blackberryBasket.getMoneyReward());
        assertEquals(500, carvingPumpkins.getMoneyReward());
        assertEquals(800, catchASquid.getMoneyReward());

        blackberryBasket.setAccepted(true);
        carvingPumpkins.setAccepted(true);
        catchASquid.setAccepted(true);
        assertTrue(blackberryBasket.matchesItemDelivery("linus", "stardewcraft:blackberry_basket"));
        assertTrue(carvingPumpkins.matchesItemDelivery("caroline", "stardewcraft:pumpkin"));
        assertTrue(catchASquid.matchesItemDelivery("willy", "stardewcraft:squid"));
    }

    @Test
    void vanillaStoryQuests110Through112UseItemDeliveryObjectives() {
        DataDrivenQuest clintsAttempt = assertInstanceOf(
                DataDrivenQuest.class, QuestDataLoader.createQuest("110"));
        DataDrivenQuest darkReagent = assertInstanceOf(
                DataDrivenQuest.class, QuestDataLoader.createQuest("111"));
        DataDrivenQuest favorForClint = assertInstanceOf(
                DataDrivenQuest.class, QuestDataLoader.createQuest("112"));

        assertEquals("stardewcraft:item_delivery", clintsAttempt.getObjectiveType().toString());
        assertEquals("stardewcraft:item_delivery", darkReagent.getObjectiveType().toString());
        assertEquals("stardewcraft:item_delivery", favorForClint.getObjectiveType().toString());
        assertEquals(0, clintsAttempt.getMoneyReward());
        assertEquals(1000, darkReagent.getMoneyReward());
        assertEquals(500, favorForClint.getMoneyReward());

        clintsAttempt.setAccepted(true);
        darkReagent.setAccepted(true);
        favorForClint.setAccepted(true);
        assertTrue(clintsAttempt.matchesItemDelivery("emily", "stardewcraft:amethyst"));
        assertTrue(darkReagent.matchesItemDelivery("wizard", "stardewcraft:void_essence"));
        assertTrue(favorForClint.matchesItemDelivery("clint", "stardewcraft:iron_bar"));
    }

    @Test
    void vanillaStoryQuests113Through117UseItemDeliveryObjectives() {
        DataDrivenQuest robinsRequest = assertInstanceOf(
                DataDrivenQuest.class, QuestDataLoader.createQuest("113"));
        DataDrivenQuest fishStew = assertInstanceOf(
                DataDrivenQuest.class, QuestDataLoader.createQuest("114"));
        DataDrivenQuest freshFruit = assertInstanceOf(
                DataDrivenQuest.class, QuestDataLoader.createQuest("115"));
        DataDrivenQuest grannysGift = assertInstanceOf(
                DataDrivenQuest.class, QuestDataLoader.createQuest("116"));
        DataDrivenQuest pierresNotice = assertInstanceOf(
                DataDrivenQuest.class, QuestDataLoader.createQuest("117"));

        for (DataDrivenQuest quest : List.of(
                robinsRequest, fishStew, freshFruit, grannysGift, pierresNotice)) {
            assertEquals("stardewcraft:item_delivery", quest.getObjectiveType().toString());
            quest.setAccepted(true);
        }

        assertEquals(500, robinsRequest.getMoneyReward());
        assertEquals(400, fishStew.getMoneyReward());
        assertEquals(600, freshFruit.getMoneyReward());
        assertEquals(500, grannysGift.getMoneyReward());
        assertEquals(1000, pierresNotice.getMoneyReward());
        assertEquals(0, robinsRequest.getCurrentObjectiveCount());
        assertEquals(10, robinsRequest.getTotalObjectiveCount());

        assertTrue(robinsRequest.matchesItemDelivery("robin", "stardewcraft:wood_hard"));
        assertTrue(fishStew.matchesItemDelivery("gus", "stardewcraft:albacore"));
        assertTrue(freshFruit.matchesItemDelivery("emily", "stardewcraft:apricot"));
        assertTrue(grannysGift.matchesItemDelivery("evelyn", "stardewcraft:leek"));
        assertTrue(pierresNotice.matchesItemDelivery("pierre", "stardewcraft:sashimi"));
    }

    @Test
    void vanillaStoryQuests118Through122UseItemDeliveryObjectives() {
        DataDrivenQuest aquaticResearch = assertInstanceOf(
                DataDrivenQuest.class, QuestDataLoader.createQuest("118"));
        DataDrivenQuest soldiersStar = assertInstanceOf(
                DataDrivenQuest.class, QuestDataLoader.createQuest("119"));
        DataDrivenQuest mayorsNeed = assertInstanceOf(
                DataDrivenQuest.class, QuestDataLoader.createQuest("120"));
        DataDrivenQuest wantedLobster = assertInstanceOf(
                DataDrivenQuest.class, QuestDataLoader.createQuest("121"));
        DataDrivenQuest pamNeedsJuice = assertInstanceOf(
                DataDrivenQuest.class, QuestDataLoader.createQuest("122"));

        for (DataDrivenQuest quest : List.of(
                aquaticResearch, soldiersStar, mayorsNeed, wantedLobster, pamNeedsJuice)) {
            assertEquals("stardewcraft:item_delivery", quest.getObjectiveType().toString());
            quest.setAccepted(true);
        }

        assertEquals(1000, aquaticResearch.getMoneyReward());
        assertEquals(500, soldiersStar.getMoneyReward());
        assertEquals(750, mayorsNeed.getMoneyReward());
        assertEquals(800, wantedLobster.getMoneyReward());
        assertEquals(400, pamNeedsJuice.getMoneyReward());

        assertTrue(aquaticResearch.matchesItemDelivery("demetrius", "stardewcraft:pufferfish"));
        assertTrue(soldiersStar.matchesItemDelivery("kent", "stardewcraft:starfruit"));
        assertTrue(mayorsNeed.matchesItemDelivery("lewis", "stardewcraft:truffle_oil"));
        assertTrue(wantedLobster.matchesItemDelivery("gus", "stardewcraft:lobster"));
        assertTrue(pamNeedsJuice.matchesItemDelivery("pam", "stardewcraft:battery_pack"));
    }

    @Test
    void vanillaStoryQuests123Through125UseItemDeliveryObjectives() {
        DataDrivenQuest staffOfPower = assertInstanceOf(
                DataDrivenQuest.class, QuestDataLoader.createQuest("123"));
        DataDrivenQuest catchALingcod = assertInstanceOf(
                DataDrivenQuest.class, QuestDataLoader.createQuest("124"));
        DataDrivenQuest exoticSpirits = assertInstanceOf(
                DataDrivenQuest.class, QuestDataLoader.createQuest("125"));

        for (DataDrivenQuest quest : List.of(staffOfPower, catchALingcod, exoticSpirits)) {
            assertEquals("stardewcraft:item_delivery", quest.getObjectiveType().toString());
            quest.setAccepted(true);
        }

        assertEquals(5000, staffOfPower.getMoneyReward());
        assertEquals(550, catchALingcod.getMoneyReward());
        assertEquals(600, exoticSpirits.getMoneyReward());

        assertTrue(staffOfPower.matchesItemDelivery("wizard", "stardewcraft:iridium_bar"));
        assertTrue(catchALingcod.matchesItemDelivery("willy", "stardewcraft:lingcod"));
        assertTrue(exoticSpirits.matchesItemDelivery("gus", "stardewcraft:coconut"));
    }

    @Test
    void introductionsUsesLocalizedDynamicProgressInsteadOfVanillaSentinel() {
        StardewQuest quest = QuestDataLoader.createQuest("9");

        assertNotNull(quest);
        var objective = quest.getObjectiveComponents();
        assertEquals(1, objective.size());
        var translated = assertInstanceOf(
                TranslatableContents.class, objective.getFirst().getContents());
        assertEquals("stardewcraft.quest.socialize.progress", translated.getKey());
        assertEquals(2, translated.getArgs().length);
    }

    @Test
    void darkTalismanCutsceneQuestIsRegistered() {
        StardewQuest quest = QuestDataLoader.createQuest("28");

        assertNotNull(quest);
        assertEquals("stardewcraft.quest.28.title", quest.getTitleKey());
        assertEquals("stardewcraft:28", quest.getDefinitionId().toString());
    }
}

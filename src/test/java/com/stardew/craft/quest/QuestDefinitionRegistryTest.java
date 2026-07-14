package com.stardew.craft.quest;

import com.stardew.craft.api.v1.internal.BuiltinApiTypes;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
}

package com.stardew.craft.item;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WizardBuildingItemTest {
    private static final Path PROJECT = Path.of(System.getProperty("stardewcraft.projectDir"));
    private static final List<String> LANGUAGES = List.of(
            "de_de", "en_us", "es_es", "fr_fr", "hu_hu", "it_it",
            "ja_jp", "ko_kr", "pt_br", "ru_ru", "tr_tr", "zh_cn");
    private static final List<String> OWNERSHIP_KEYS = List.of(
            "tooltip.stardewcraft.wizard_building.owner",
            "tooltip.stardewcraft.wizard_building.owner_unassigned",
            "tooltip.stardewcraft.wizard_building.bound",
            "tooltip.stardewcraft.wizard_building.farm_only",
            "message.stardewcraft.wizard_building.owner_only");

    @Test
    void ownershipBindsOnceAndCannotBeTransferred() {
        ItemStack stack = new ItemStack(Items.BRICK);
        UUID firstOwner = UUID.randomUUID();
        UUID secondOwner = UUID.randomUUID();

        assertNull(WizardBuildingItem.getOwner(stack));
        WizardBuildingItem.bindTo(stack, firstOwner, "First Farmer");
        WizardBuildingItem.bindTo(stack, secondOwner, "Second Farmer");

        assertEquals(firstOwner, WizardBuildingItem.getOwner(stack));
        assertEquals("First Farmer", WizardBuildingItem.getOwnerName(stack));
        assertTrue(WizardBuildingItem.isOwnedBy(stack, firstOwner));
        assertFalse(WizardBuildingItem.isOwnedBy(stack, secondOwner));
    }

    @Test
    void everyWizardBuildingUsesTheOwnedItemClassAndPurchasesBindImmediately() throws Exception {
        String items = Files.readString(PROJECT.resolve(
                "src/main/java/com/stardew/craft/item/ModItems.java"));
        int wizardSection = items.indexOf("// ── 法师魔法建筑 ──");
        assertTrue(wizardSection >= 0);
        for (String id : List.of(
                "JUNIMO_HUT", "EARTH_OBELISK", "WATER_OBELISK",
                "DESERT_OBELISK", "ISLAND_OBELISK", "GOLD_CLOCK")) {
            int registration = items.indexOf("DeferredItem<Item> " + id, wizardSection);
            assertTrue(registration >= 0, id);
            assertTrue(items.substring(registration, Math.min(items.length(), registration + 300))
                    .contains("new WizardBuildingItem"), id);
        }

        String purchase = Files.readString(PROJECT.resolve(
                "src/main/java/com/stardew/craft/network/payload/CarpenterPurchasePayload.java"));
        assertTrue(purchase.contains("WizardBuildingItem.bindTo(resultStack, player)"));
    }

    @Test
    void everyBundledLanguageExplainsOwnershipAndTransferRules() throws Exception {
        for (String language : LANGUAGES) {
            JsonObject translations = JsonParser.parseString(Files.readString(PROJECT.resolve(
                    "src/main/resources/assets/stardewcraft/lang/" + language + ".json"))).getAsJsonObject();
            for (String key : OWNERSHIP_KEYS) {
                assertTrue(translations.has(key), language + " is missing " + key);
                assertFalse(translations.get(key).getAsString().isBlank(), language + " has blank " + key);
            }
        }
    }

    @Test
    void sharedFarmMembersKeepWizardBuildingRuntimeEffects() throws Exception {
        String blockEntity = Files.readString(PROJECT.resolve(
                "src/main/java/com/stardew/craft/blockentity/WizardBuildingBlockEntity.java"));
        assertTrue(blockEntity.contains(".getFarmForPlayer(owner)"),
                "a member-owned Gold Clock must update the shared farm");
        assertTrue(blockEntity.contains(".areFarmmates(owner, cropOwner)"),
                "a member-owned Junimo Hut must harvest crops on the shared farm");
    }
}

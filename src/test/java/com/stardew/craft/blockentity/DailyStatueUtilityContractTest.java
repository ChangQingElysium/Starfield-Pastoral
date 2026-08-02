package com.stardew.craft.blockentity;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DailyStatueUtilityContractTest {
    private static final Path PROJECT = Path.of(
            System.getProperty("stardewcraft.projectDir"));
    private static final List<String> LOCALES = List.of(
            "de_de", "en_us", "es_es", "fr_fr", "hu_hu", "it_it",
            "ja_jp", "ko_kr", "pt_br", "ru_ru", "tr_tr", "zh_cn");

    @Test
    void statuesImplementTheSameRuntimeContractsAsDailyUtilityFacilities() {
        assertTrue(UtilityAutomationAccess.class.isAssignableFrom(DailyStatueBlockEntity.class));
        assertTrue(AdvanceableUtility.class.isAssignableFrom(DailyStatueBlockEntity.class));
        assertTrue(UtilityMachineInfo.class.isAssignableFrom(DailyStatueBlockEntity.class));
    }

    @Test
    void statuesKeepTheirAutomationBubbleAndUtilityRegistrations() throws IOException {
        String capabilities = source(
                "src/main/java/com/stardew/craft/capability/UtilityAutomationCapabilities.java");
        String renderers = source(
                "src/main/java/com/stardew/craft/client/ModClientSetup.java");
        assertTrue(capabilities.contains(
                "registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.DAILY_STATUE.get()"));
        assertTrue(renderers.contains(
                "registerBlockEntityRenderer(ModBlockEntities.DAILY_STATUE.get()"));

        JsonObject utilityTag = JsonParser.parseString(source(
                "src/main/resources/data/stardewcraft/tags/block/machines/utility.json"))
                .getAsJsonObject();
        List<String> values = utilityTag.getAsJsonArray("values").asList().stream()
                .map(element -> element.getAsString())
                .toList();
        assertTrue(values.contains("stardewcraft:statue_of_endless_fortune"));
        assertTrue(values.contains("stardewcraft:statue_of_perfection"));
    }

    @Test
    void everySupportedLanguageContainsDailyStatueFacilityStatusText() throws IOException {
        for (String locale : LOCALES) {
            JsonObject language = JsonParser.parseString(source(
                    "src/main/resources/assets/stardewcraft/lang/" + locale + ".json"))
                    .getAsJsonObject();
            assertTrue(language.has("stardewcraft.tooltip.daily_statue.ready"), locale);
            assertTrue(language.has("stardewcraft.tooltip.daily_statue.waiting"), locale);
        }
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(PROJECT.resolve(relativePath));
    }
}

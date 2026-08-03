package com.stardew.craft.client.gui.common;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StardewQuestionDialogSoundContractTest {
    @Test
    void standaloneQuestionUsesVanillaDialogueBoxCues() throws IOException {
        String screen = source("client/gui/common/StardewConfirmDialogScreen.java");

        assertTrue(screen.contains("ModSounds.BREATHIN"));
        assertTrue(screen.contains("ModSounds.COWBOY_GUNSHOT"));
        assertTrue(screen.contains("ModSounds.SHINY4"));
        assertTrue(screen.contains("ModSounds.SMALL_SELECT"));
        assertTrue(screen.contains("ModSounds.BREATHOUT"));
        assertFalse(screen.contains("ModSounds.BUTTON_TAP"));
        assertFalse(screen.contains("ModSounds.BOOK_READ"));
        assertFalse(screen.contains("ModSounds.COIN"));
        assertFalse(screen.contains("ModSounds.CANCEL"));
        assertFalse(screen.contains("ModSounds.DIALOGUE_CHARACTER"));
    }

    @Test
    void lewisMenusDoNotOverrideQuestionDialogSounds() throws IOException {
        String spec = source("client/gui/common/StardewQuestionDialogSpec.java");
        String menu = source("network/payload/OpenLewisMenuPayload.java");
        String confirm = source("network/payload/OpenLewisConfirmPayload.java");

        assertFalse(spec.contains("SoundTheme"));
        assertFalse(menu.contains("withSoundTheme"));
        assertFalse(confirm.contains("withSoundTheme"));
    }

    private static String source(String relativePath) throws IOException {
        Path projectDir = Path.of(System.getProperty("stardewcraft.projectDir"));
        return Files.readString(projectDir.resolve("src/main/java/com/stardew/craft").resolve(relativePath));
    }
}

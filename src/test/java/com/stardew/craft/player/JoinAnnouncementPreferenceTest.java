package com.stardew.craft.player;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JoinAnnouncementPreferenceTest {

    @Test
    void dismissalDefaultsToFalseAndSurvivesSaveRoundTrip() {
        UUID playerId = UUID.randomUUID();
        PlayerStardewData data = new PlayerStardewData(playerId);
        assertFalse(data.isJoinAnnouncementDismissed());

        data.setJoinAnnouncementDismissed(true);
        CompoundTag saved = data.toNBT();

        assertTrue(saved.getBoolean("JoinAnnouncementDismissed"));
        assertTrue(PlayerStardewData.fromNBT(saved, playerId).isJoinAnnouncementDismissed());
    }

    @Test
    void everySupportedLanguageHasDismissalTextAndNoRewardPrompt() throws Exception {
        String[] versionKeys = {
            "stardewcraft.join_announcement.current_version",
            "stardewcraft.join_announcement.latest_version",
            "stardewcraft.join_announcement.up_to_date",
            "stardewcraft.join_announcement.update_available",
            "stardewcraft.join_announcement.update_recommended",
            "stardewcraft.join_announcement.ahead_of_public",
            "stardewcraft.join_announcement.latest_unavailable",
            "stardewcraft.join_announcement.modrinth"
        };
        String[] languages = {
            "de_de", "en_us", "es_es", "fr_fr", "hu_hu", "it_it",
            "ja_jp", "ko_kr", "pt_br", "ru_ru", "tr_tr", "zh_cn"
        };
        for (String language : languages) {
            try (var stream = getClass().getResourceAsStream(
                    "/assets/stardewcraft/lang/" + language + ".json")) {
                assertNotNull(stream, language);
                JsonObject translations = JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                assertTrue(translations.has("stardewcraft.join_announcement.dismiss"), language);
                assertTrue(translations.has("stardewcraft.join_announcement.dismissed"), language);
                for (String key : versionKeys) {
                    assertTrue(translations.has(key), language + ": " + key);
                }
                assertFalse(translations.has("stardewcraft.join_announcement.reward"), language);
                assertFalse(translations.has("stardewcraft.bilibili.gift_hint"), language);
            }
        }
    }

    @Test
    void dismissedAnnouncementStillShowsOutdatedVersionWarning() {
        var outdated = new ModUpdateChecker.VersionStatus(
                "0.5.2", "0.5.3", ModUpdateChecker.State.OUTDATED);
        var current = new ModUpdateChecker.VersionStatus(
                "0.5.3", "0.5.3", ModUpdateChecker.State.UP_TO_DATE);

        assertTrue(JoinAnnouncementService.shouldSendUpdateNotice(true, outdated));
        assertFalse(JoinAnnouncementService.shouldSendUpdateNotice(true, current));
        assertFalse(JoinAnnouncementService.shouldSendUpdateNotice(false, outdated));
    }
}

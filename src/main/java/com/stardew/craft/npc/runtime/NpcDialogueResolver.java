package com.stardew.craft.npc.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Selects NPC dialogue keys using Stardew Valley's {@code NPC.cs} precedence.
 *
 * <p>The relevant vanilla paths are {@code loadCurrentDialogue},
 * {@code checkForNewCurrentDialogue}, and {@code tryToRetrieveDialogue}. This
 * class intentionally contains no Minecraft state access so the complete key
 * matrix can be regression-tested without starting a game server.</p>
 */
final class NpcDialogueResolver {
    private NpcDialogueResolver() {
    }

    static Selection select(JsonObject dialogueRoot, JsonObject rainyRoot, Context context) {
        JsonObject entries = dialogueScope(dialogueRoot);
        if (entries == null || entries.isEmpty()) {
            return Selection.NONE;
        }

        // NPC.loadCurrentDialogue: green rain suppresses ordinary/new-location dialogue.
        if (context.greenRain()) {
            if (context.year() >= 2) {
                Selection yearTwo = selection(entries, "GreenRain_2", context.dayKey(), Source.GREEN_RAIN);
                if (yearTwo.present()) {
                    return yearTwo;
                }
            }
            Selection greenRain = selection(entries, "GreenRain", context.dayKey(), Source.GREEN_RAIN);
            if (greenRain.present()) {
                return greenRain;
            }
        }

        if (!context.greenRain()) {
            // Farmer.activeDialogueEvents, including Introduction and event memories.
            for (String activeKey : context.activeDialogueKeys()) {
                Selection active = selection(entries, activeKey, context.dayKey(), Source.ACTIVE_EVENT);
                if (active.present()) {
                    return active;
                }
            }
            if (context.firstMeeting()) {
                Selection introduction = selection(entries, "Introduction", context.dayKey(), Source.INTRODUCTION);
                if (introduction.present()) {
                    return introduction;
                }
            }

            // NPC.checkForNewCurrentDialogue: seasonal location keys first, then unprefixed keys.
            Selection location = locationDialogue(entries, context, false);
            if (location.present()) {
                return location;
            }
            location = locationDialogue(entries, context, true);
            if (location.present()) {
                return location;
            }
        }

        // NPC.loadCurrentDialogue: ordinary rain dialogue is a 50% daily choice from
        // Characters/Dialogue/rainy, not a key named "Rain" or "Sun" in the NPC sheet.
        if (context.raining() && context.useRainDialogue()) {
            JsonObject rainyEntries = dialogueScope(rainyRoot);
            if (rainyEntries != null) {
                Selection rainy = selection(rainyEntries, context.npcId(), context.dayKey(), Source.RAINY);
                if (rainy.present()) {
                    return rainy;
                }
            }
        }

        // NPC.tryToRetrieveDialogue: season-specific dialogue, then generic dialogue.
        Selection daily = retrieveDaily(entries, context, context.seasonLower() + "_", "");
        if (daily.present()) {
            return daily;
        }
        return retrieveDaily(entries, context, "", "");
    }

    private static Selection locationDialogue(JsonObject entries, Context context, boolean noPreface) {
        if (context.locationName().isBlank()) {
            return Selection.NONE;
        }
        String preface = !noPreface && !"spring".equals(context.seasonLower())
                ? context.seasonLower() + "_"
                : "";
        String location = context.locationName();

        Selection result = selection(entries,
                preface + location + "_" + context.tileX() + "_" + context.tileY(),
                context.dayKey(), Source.LOCATION);
        if (result.present()) {
            return result;
        }
        result = selection(entries, preface + location + "_" + context.weekdayShort(),
                context.dayKey(), Source.LOCATION);
        if (result.present()) {
            return result;
        }
        for (int hearts = 10; hearts >= 2; hearts -= 2) {
            if (context.hearts() >= hearts) {
                result = selection(entries, preface + location + hearts,
                        context.dayKey(), Source.LOCATION);
                if (result.present()) {
                    return result;
                }
            }
        }
        return selection(entries, preface + location, context.dayKey(), Source.LOCATION);
    }

    private static Selection retrieveDaily(
            JsonObject entries,
            Context context,
            String preface,
            String appendToEnd
    ) {
        int year = Math.min(Math.max(1, context.year()), 2);
        String day = Integer.toString(context.dayInSeason());
        String weekday = context.weekdayShort();

        if (year == 1) {
            Selection exactDay = selection(entries, preface + day + appendToEnd,
                    context.dayKey(), Source.DAILY);
            if (exactDay.present()) {
                return exactDay;
            }
        }

        Selection dated = selection(entries, preface + day + "_" + year + appendToEnd,
                context.dayKey(), Source.DAILY);
        if (dated.present()) {
            return dated;
        }
        Selection everyYear = selection(entries, preface + day + "_*" + appendToEnd,
                context.dayKey(), Source.DAILY);
        if (everyYear.present()) {
            return everyYear;
        }

        for (int hearts = 10; hearts >= 2; hearts -= 2) {
            if (context.hearts() < hearts) {
                continue;
            }
            Selection heartYear = selection(entries,
                    preface + weekday + hearts + "_" + year + appendToEnd,
                    context.dayKey(), Source.DAILY);
            Selection heart = selection(entries, preface + weekday + hearts + appendToEnd,
                    context.dayKey(), Source.DAILY);
            Selection heartDialogue = heartYear.present() ? heartYear : heart;
            if (heartDialogue.present()) {
                // Exact vanilla exception after Pam's house upgrade.
                if (hearts == 4
                        && "fall_".equals(preface)
                        && "Mon".equals(weekday)
                        && "penny".equals(context.npcId())
                        && context.pamHouseUpgrade()) {
                    Selection replacement = selection(entries,
                            preface + weekday + "_" + year + appendToEnd,
                            context.dayKey(), Source.DAILY);
                    if (!replacement.present()) {
                        replacement = selection(entries, "fall_Mon",
                                context.dayKey(), Source.DAILY);
                    }
                    return replacement;
                }
                return heartDialogue;
            }
        }

        Selection weekdayDialogue = selection(entries, preface + weekday + appendToEnd,
                context.dayKey(), Source.DAILY);
        if (weekdayDialogue.present()) {
            Selection yearSpecific = selection(entries,
                    preface + weekday + "_" + year + appendToEnd,
                    context.dayKey(), Source.DAILY);
            if (yearSpecific.present()) {
                weekdayDialogue = yearSpecific;
            }
        }

        // Exact vanilla exception in NPC.tryToRetrieveDialogue.
        if (weekdayDialogue.present()
                && "caroline".equals(context.npcId())
                && context.communityCenterAccessible()
                && "summer_".equals(preface)
                && "Mon".equals(weekday)) {
            Selection replacement = selection(entries, "summer_Wed", context.dayKey(), Source.DAILY);
            if (replacement.present()) {
                return replacement;
            }
        }
        return weekdayDialogue;
    }

    private static Selection selection(JsonObject entries, String requestedKey, int dayKey, Source source) {
        String actualKey = findKeyCaseInsensitive(entries, requestedKey);
        if (actualKey == null) {
            return Selection.NONE;
        }
        String text = pickText(entries.get(actualKey), dayKey);
        return text == null || text.isBlank()
                ? Selection.NONE
                : new Selection(actualKey, text, source);
    }

    private static JsonObject dialogueScope(JsonObject root) {
        if (root == null) {
            return null;
        }
        if (root.has("entries") && root.get("entries").isJsonObject()) {
            return root.getAsJsonObject("entries");
        }
        return root;
    }

    private static String findKeyCaseInsensitive(JsonObject entries, String requestedKey) {
        if (entries == null || requestedKey == null || requestedKey.isBlank()) {
            return null;
        }
        if (entries.has(requestedKey)) {
            return requestedKey;
        }
        for (String key : entries.keySet()) {
            if (key.equalsIgnoreCase(requestedKey)) {
                return key;
            }
        }
        return null;
    }

    private static String pickText(JsonElement entry, int dayKey) {
        if (entry == null || entry.isJsonNull()) {
            return null;
        }
        if (entry.isJsonPrimitive()) {
            return entry.getAsString();
        }
        if (entry.isJsonObject()) {
            JsonObject object = entry.getAsJsonObject();
            if (object.has("translate") && object.get("translate").isJsonPrimitive()) {
                return object.get("translate").getAsString();
            }
            return null;
        }
        if (entry.isJsonArray()) {
            JsonArray array = entry.getAsJsonArray();
            if (array.isEmpty()) {
                return null;
            }
            return pickText(array.get(Math.floorMod(dayKey, array.size())), dayKey);
        }
        return null;
    }

    record Context(
            String npcId,
            int dayInSeason,
            int dayKey,
            int year,
            String season,
            String weekdayShort,
            int hearts,
            boolean firstMeeting,
            boolean greenRain,
            boolean raining,
            boolean useRainDialogue,
            String locationName,
            int tileX,
            int tileY,
            boolean pamHouseUpgrade,
            boolean communityCenterAccessible,
            List<String> activeDialogueKeys
    ) {
        Context {
            npcId = normalize(npcId);
            season = normalize(season);
            weekdayShort = weekdayShort == null ? "Mon" : weekdayShort;
            locationName = locationName == null ? "" : locationName.trim();
            activeDialogueKeys = activeDialogueKeys == null
                    ? List.of()
                    : List.copyOf(new ArrayList<>(activeDialogueKeys));
            hearts = Math.max(0, Math.min(14, hearts));
        }

        String seasonLower() {
            return season;
        }

        private static String normalize(String value) {
            return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        }
    }

    record Selection(String key, String text, Source source) {
        static final Selection NONE = new Selection("", "", Source.NONE);

        boolean present() {
            return !key.isBlank() && !text.isBlank();
        }
    }

    enum Source {
        NONE,
        GREEN_RAIN,
        ACTIVE_EVENT,
        INTRODUCTION,
        LOCATION,
        RAINY,
        DAILY
    }
}

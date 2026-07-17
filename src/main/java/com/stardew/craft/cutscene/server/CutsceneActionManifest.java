package com.stardew.craft.cutscene.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.stardew.craft.cutscene.data.EventData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Server-derived allowlist of state actions embedded in a cutscene definition. */
final class CutsceneActionManifest {
    private final Map<Integer, List<ExpectedAction>> byCommandToken;

    private CutsceneActionManifest(Map<Integer, List<ExpectedAction>> byCommandToken) {
        this.byCommandToken = byCommandToken;
    }

    static CutsceneActionManifest from(EventData event) {
        Map<Integer, List<ExpectedAction>> result = new HashMap<>();
        for (int token = 0; token < event.rawCommands().size(); token++) {
            collect(event.rawCommands().get(token), token, -1, result);
        }
        result.replaceAll((ignored, actions) -> List.copyOf(actions));
        return new CutsceneActionManifest(Map.copyOf(result));
    }

    AuthorizationState newState() {
        return new AuthorizationState();
    }

    boolean authorize(
            AuthorizationState state,
            int commandToken,
            String action,
            String value
    ) {
        List<ExpectedAction> expected = byCommandToken.get(commandToken);
        if (expected == null) {
            return false;
        }
        Integer selectedBranch = state.selectedBranches.get(commandToken);
        for (int i = 0; i < expected.size(); i++) {
            ExpectedAction candidate = expected.get(i);
            ActionKey key = new ActionKey(commandToken, i);
            if (state.consumed.contains(key)
                    || !candidate.action.equals(action)
                    || !candidate.value.equals(value)) {
                continue;
            }
            if (candidate.branch >= 0 && selectedBranch != null && candidate.branch != selectedBranch) {
                continue;
            }
            if (candidate.branch >= 0 && selectedBranch == null) {
                state.selectedBranches.put(commandToken, candidate.branch);
            }
            state.consumed.add(key);
            return true;
        }
        return false;
    }

    private static void collect(
            JsonObject command,
            int token,
            int branch,
            Map<Integer, List<ExpectedAction>> result
    ) {
        String type = string(command, "cmd", "");
        if ("simultaneous".equals(type)) {
            collectArray(command.getAsJsonArray("commands"), token, branch, result);
            return;
        }
        if ("question".equals(type)) {
            JsonArray choices = command.getAsJsonArray("choices");
            for (int choice = 0; choice < choices.size(); choice++) {
                JsonObject choiceObject = choices.get(choice).getAsJsonObject();
                collectArray(choiceObject.getAsJsonArray("commands"), token, choice, result);
            }
            return;
        }
        ExpectedAction action = expectedAction(type, command, branch);
        if (action != null) {
            result.computeIfAbsent(token, ignored -> new ArrayList<>()).add(action);
        }
    }

    private static void collectArray(
            JsonArray commands,
            int token,
            int branch,
            Map<Integer, List<ExpectedAction>> result
    ) {
        if (commands == null) {
            return;
        }
        for (var element : commands) {
            collect(element.getAsJsonObject(), token, branch, result);
        }
    }

    private static ExpectedAction expectedAction(String type, JsonObject command, int branch) {
        return switch (type) {
            case "add_quest" -> action("add_quest", string(command, "quest_id", ""), branch);
            case "remove_quest" -> action("remove_quest", string(command, "quest_id", ""), branch);
            case "set_flag", "add_mail" -> action(
                    "set_flag",
                    string(command, "set_flag".equals(type) ? "flag" : "id", ""),
                    branch);
            case "grant_rusty_key" -> action("grant_rusty_key", "", branch);
            case "grant_magnifying_glass" -> action("grant_magnifying_glass", "", branch);
            case "grant_bear_knowledge" -> action("grant_bear_knowledge", "", branch);
            case "mark_opened_sewer" -> action("mark_opened_sewer", "", branch);
            case "add_recipe" -> action("add_recipe", string(command, "recipe", ""), branch);
            case "add_mail_now" -> action("add_mail_now", string(command, "id", ""), branch);
            case "add_mail_for_tomorrow" -> action(
                    "add_mail_for_tomorrow", string(command, "id", ""), branch);
            case "apply_unlock_source" -> action(
                    "apply_unlock_source", string(command, "source", ""), branch);
            case "set_cave_choice" -> action(
                    "set_cave_choice", string(command, "choice", ""), branch);
            case "add_friendship" -> action(
                    "add_friendship",
                    string(command, "npc", "") + ":" + integer(command, "points", 250),
                    branch);
            case "add_item", "remove_item" -> action(
                    type,
                    string(command, "item", "") + ":" + integer(command, "count", 1),
                    branch);
            case "door" -> action(
                    "door",
                    integer(command, "x", 0) + "," + integer(command, "y", 0) + ","
                            + integer(command, "z", 0) + ":" + bool(command, "open", true),
                    branch);
            case "teleport_cc" -> action("teleport_cc", "", branch);
            case "egg_festival_stage" -> action(
                    "egg_festival_blackout", string(command, "stage", "main"), branch);
            case "egg_festival_finish" -> action("egg_festival_award_complete", "", branch);
            case "flower_dance_stage" -> action(
                    "flower_dance_stage", string(command, "stage", "main"), branch);
            case "moonlight_jellies_stage" -> action(
                    "moonlight_jellies_stage", string(command, "stage", "release"), branch);
            case "winter_star_stage" -> "open_gift".equals(string(command, "action", ""))
                    ? action("winter_star_open_gift", "", branch)
                    : null;
            default -> null;
        };
    }

    private static ExpectedAction action(String action, String value, int branch) {
        return new ExpectedAction(action, value, branch);
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) ? object.get(key).getAsString() : fallback;
    }

    private static int integer(JsonObject object, String key, int fallback) {
        return object.has(key) ? object.get(key).getAsInt() : fallback;
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        return object.has(key) ? object.get(key).getAsBoolean() : fallback;
    }

    static final class AuthorizationState {
        private final Set<ActionKey> consumed = new HashSet<>();
        private final Map<Integer, Integer> selectedBranches = new HashMap<>();
    }

    private record ExpectedAction(String action, String value, int branch) {
    }

    private record ActionKey(int token, int occurrence) {
    }
}

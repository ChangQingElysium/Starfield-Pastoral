package com.stardew.craft.cutscene.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parsed event data from a JSON file.
 * Contains metadata, trigger info, preconditions, and the raw command list.
 */
public final class EventData {

    private final String id;
    private final boolean skippable;
    private final EventTrigger trigger;
    private final List<EventPrecondition> preconditions;
    private final List<JsonObject> rawCommands;

    private EventData(String id, boolean skippable, EventTrigger trigger,
                      List<EventPrecondition> preconditions, List<JsonObject> rawCommands) {
        this.id = id;
        this.skippable = skippable;
        this.trigger = trigger;
        this.preconditions = Collections.unmodifiableList(preconditions);
        this.rawCommands = Collections.unmodifiableList(rawCommands);
    }

    public String id() { return id; }
    public boolean skippable() { return skippable; }

    /** A command-level gate delays skipping even when the event metadata allows it. */
    public boolean skippableAtStart() {
        if (!skippable) {
            return false;
        }
        return rawCommands.stream().noneMatch(command ->
                command.has("cmd") && "skippable".equals(command.get("cmd").getAsString()));
    }
    public EventTrigger trigger() { return trigger; }
    public List<EventPrecondition> preconditions() { return preconditions; }
    public List<JsonObject> rawCommands() { return rawCommands; }

    public static EventData fromJson(JsonObject root) {
        String id = root.get("id").getAsString();
        boolean skippable = root.has("skippable") && root.get("skippable").getAsBoolean();

        EventTrigger trigger = EventTrigger.fromJson(root.getAsJsonObject("trigger"));

        List<EventPrecondition> preconditions = new ArrayList<>();
        if (root.has("preconditions")) {
            JsonArray arr = root.getAsJsonArray("preconditions");
            for (JsonElement e : arr) {
                preconditions.add(EventPrecondition.fromJson(e.getAsJsonObject()));
            }
        }

        List<JsonObject> rawCommands = new ArrayList<>();
        if (root.has("commands")) {
            JsonArray arr = root.getAsJsonArray("commands");
            for (JsonElement e : arr) {
                rawCommands.add(e.getAsJsonObject());
            }
        }

        return new EventData(id, skippable, trigger, preconditions, rawCommands);
    }
}

package com.stardew.craft.api.v1.action;

/** Outcome of a server-authoritative action. */
public record StardewActionResult(boolean success, String message) {
    public StardewActionResult {
        message = message == null ? "" : message;
    }

    public static StardewActionResult ok() {
        return new StardewActionResult(true, "");
    }

    public static StardewActionResult failure(String message) {
        return new StardewActionResult(false, message);
    }
}

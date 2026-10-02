package com.stardew.craft.port.net.neoforged.neoforge.common.util;

import net.minecraftforge.eventbus.api.Event;

/** PORT(1.20.1): NeoForge's TriState; Forge 1.20.1 events use {@link Event.Result} for the same three states. */
public enum TriState {
    TRUE,
    DEFAULT,
    FALSE;

    public boolean isTrue() {
        return this == TRUE;
    }

    public boolean isDefault() {
        return this == DEFAULT;
    }

    public boolean isFalse() {
        return this == FALSE;
    }

    /** TRUE -> ALLOW, DEFAULT -> DEFAULT, FALSE -> DENY. */
    public Event.Result toResult() {
        return switch (this) {
            case TRUE -> Event.Result.ALLOW;
            case DEFAULT -> Event.Result.DEFAULT;
            case FALSE -> Event.Result.DENY;
        };
    }

    public static TriState fromResult(Event.Result result) {
        return switch (result) {
            case ALLOW -> TRUE;
            case DEFAULT -> DEFAULT;
            case DENY -> FALSE;
        };
    }
}

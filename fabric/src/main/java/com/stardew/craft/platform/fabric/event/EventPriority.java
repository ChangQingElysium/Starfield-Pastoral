package com.stardew.craft.platform.fabric.event;

/** Dispatch order; registration order breaks ties within each priority. */
public enum EventPriority {
    HIGHEST, HIGH, NORMAL, LOW, LOWEST
}

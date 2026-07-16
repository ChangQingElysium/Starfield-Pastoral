package com.stardew.craft.client.gui;

/**
 * Marker for screens which are themselves real-time gameplay and must not request a collective
 * Stardew simulation pause. Ordinary menus intentionally do not implement this interface.
 */
public interface StardewRealtimeScreen {
}

package com.stardew.craft.client.gui;

/**
 * Marks a screen which participates in StardewCraft's scoped collective pause even when its
 * vanilla {@code isPauseScreen()} contract stays false to avoid pausing the integrated server.
 */
public interface StardewCollectivePauseScreen {
}

package com.stardew.craft.platform.fabric.client.render;

/** The native health flash update, including computing this frame's flash before changing its timer. */
public record GuiHealthFrame(int health, int displayHealth, long lastHealthTime, long healthBlinkTime, boolean blinking) {
    public static GuiHealthFrame advance(int health, int previousHealth, int displayHealth,
            long lastHealthTime, long healthBlinkTime, int tick, int invulnerableTime, long now) {
        boolean blinking = healthBlinkTime > tick && (healthBlinkTime - tick) / 3L % 2L == 1L;
        if (health < previousHealth && invulnerableTime > 0) {
            lastHealthTime = now;
            healthBlinkTime = (long) (tick + 20);
        } else if (health > previousHealth && invulnerableTime > 0) {
            lastHealthTime = now;
            healthBlinkTime = (long) (tick + 10);
        }
        if (now - lastHealthTime > 1000L) {
            displayHealth = health;
            lastHealthTime = now;
        }
        return new GuiHealthFrame(health, displayHealth, lastHealthTime, healthBlinkTime, blinking);
    }
}

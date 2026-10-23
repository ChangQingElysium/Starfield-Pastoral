package com.stardew.craft.platform.fabric.client.keys;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.glfw.GLFW;

/** Neo's binding capture commits after both the ordinary key and modifier are released. */
@Environment(EnvType.CLIENT)
public final class KeyBindingCapture {
    private InputConstants.Key lastKey = InputConstants.UNKNOWN;
    private InputConstants.Key lastModifier = InputConstants.UNKNOWN;
    private boolean keyHeld;
    private boolean modifierHeld;
    public void press(InputConstants.Key key, boolean mac) {
        if (lastModifier.equals(InputConstants.UNKNOWN) && KeyModifier.getKeyModifier(key, mac) != KeyModifier.NONE) {
            lastModifier = key; modifierHeld = true;
        } else { lastKey = key; keyHeld = true; }
    }
    public Optional<Binding> release(int keyCode, int scanCode, boolean mac) {
        if (mac && scanCode == 63) return Optional.empty(); // macOS Fn emits a release with no matching press.
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { clear(); return Optional.of(new Binding(KeyModifier.NONE, InputConstants.UNKNOWN)); }
        var key = InputConstants.getKey(keyCode, scanCode);
        if (lastKey.equals(key)) keyHeld = false;
        else if (lastModifier.equals(key)) modifierHeld = false;
        if (keyHeld || modifierHeld) return Optional.empty();
        var result = lastKey.equals(InputConstants.UNKNOWN) ? new Binding(KeyModifier.NONE, lastModifier)
                : new Binding(KeyModifier.getKeyModifier(lastModifier, mac), lastKey);
        clear(); return Optional.of(result);
    }
    public void clear() { lastKey = lastModifier = InputConstants.UNKNOWN; keyHeld = modifierHeld = false; }
    public record Binding(KeyModifier modifier, InputConstants.Key key) {}
}

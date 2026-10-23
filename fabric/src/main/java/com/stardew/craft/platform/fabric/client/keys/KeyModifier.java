package com.stardew.craft.platform.fabric.client.keys;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public enum KeyModifier {
    CONTROL, SHIFT, ALT, NONE;
    public static final KeyModifier[] MODIFIER_VALUES = { SHIFT, CONTROL, ALT };

    public boolean matches(InputConstants.Key key) { return matches(key, KeyInputState.PHYSICAL.mac()); }
    boolean matches(InputConstants.Key key, boolean mac) {
        int value = key.getValue();
        return switch (this) {
            case CONTROL -> mac ? value == GLFW.GLFW_KEY_LEFT_SUPER || value == GLFW.GLFW_KEY_RIGHT_SUPER
                    : value == GLFW.GLFW_KEY_LEFT_CONTROL || value == GLFW.GLFW_KEY_RIGHT_CONTROL;
            case SHIFT -> value == GLFW.GLFW_KEY_LEFT_SHIFT || value == GLFW.GLFW_KEY_RIGHT_SHIFT;
            case ALT -> value == GLFW.GLFW_KEY_LEFT_ALT || value == GLFW.GLFW_KEY_RIGHT_ALT;
            case NONE -> false;
        };
    }
    public boolean isActive(@Nullable IKeyConflictContext context) { return isActive(context, KeyInputState.PHYSICAL); }
    boolean isActive(@Nullable IKeyConflictContext context, KeyInputState input) {
        return switch (this) {
            case CONTROL -> input.control();
            case SHIFT -> input.shift();
            case ALT -> input.alt();
            // Neo deliberately permits ordinary in-game bindings while crouch/control is held.
            // This does not check context.isActive(): the emote wheel reads physical input inside its GUI.
            case NONE -> context == null || context.conflicts(KeyConflictContext.IN_GAME)
                    || !(input.control() || input.shift() || input.alt());
        };
    }
    public InputConstants.Key[] codes() { return codes(KeyInputState.PHYSICAL.mac()); }
    InputConstants.Key[] codes(boolean mac) {
        int[] values = switch (this) {
            case CONTROL -> mac ? new int[] { GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER }
                    : new int[] { GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL };
            case SHIFT -> new int[] { GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT };
            case ALT -> new int[] { GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT };
            case NONE -> new int[0];
        };
        return java.util.Arrays.stream(values).mapToObj(InputConstants.Type.KEYSYM::getOrCreate).toArray(InputConstants.Key[]::new);
    }
    public Component getCombinedName(InputConstants.Key key, Supplier<Component> original) {
        if (this == NONE) return original.get();
        // Use Minecraft's already localized physical key names; no NeoForge language assets are required.
        return Component.empty().append(codes()[0].getDisplayName()).append(" + ").append(original.get());
    }
    public static KeyModifier getKeyModifier(InputConstants.Key key) { return getKeyModifier(key, KeyInputState.PHYSICAL.mac()); }
    static KeyModifier getKeyModifier(InputConstants.Key key, boolean mac) {
        for (var modifier : MODIFIER_VALUES) if (modifier.matches(key, mac)) return modifier;
        return NONE;
    }
    public static boolean isKeyCodeModifier(InputConstants.Key key) { return getKeyModifier(key) != NONE; }
    public static List<KeyModifier> getActiveModifiers() {
        return java.util.Arrays.stream(MODIFIER_VALUES).filter(modifier -> modifier.isActive(null)).toList();
    }
    public static KeyModifier valueFromString(String value) {
        try { return valueOf(value); } catch (NullPointerException | IllegalArgumentException invalid) { return NONE; }
    }
}

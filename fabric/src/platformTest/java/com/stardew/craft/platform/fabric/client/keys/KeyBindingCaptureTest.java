package com.stardew.craft.platform.fabric.client.keys;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.platform.InputConstants;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

class KeyBindingCaptureTest {
    @Test
    void chordCommitsOnlyAfterBothKeysAreReleasedInEitherOrder() {
        for (boolean modifierFirst : new boolean[] { false, true }) {
            var capture = new KeyBindingCapture();
            capture.press(key(GLFW.GLFW_KEY_LEFT_SHIFT), false); capture.press(key(GLFW.GLFW_KEY_R), false);
            assertTrue(capture.release(modifierFirst ? GLFW.GLFW_KEY_LEFT_SHIFT : GLFW.GLFW_KEY_R, 0, false).isEmpty());
            var binding = capture.release(modifierFirst ? GLFW.GLFW_KEY_R : GLFW.GLFW_KEY_LEFT_SHIFT, 0, false).orElseThrow();
            assertEquals(key(GLFW.GLFW_KEY_R), binding.key()); assertSame(KeyModifier.SHIFT, binding.modifier());
        }
    }

    @Test
    void plainModifiersEscapeScanCodesAndMacFnRetainTheirNativeMeanings() {
        var capture = new KeyBindingCapture(); capture.press(key(GLFW.GLFW_KEY_LEFT_SHIFT), false);
        var modifierKey = capture.release(GLFW.GLFW_KEY_LEFT_SHIFT, 0, false).orElseThrow();
        assertSame(KeyModifier.NONE, modifierKey.modifier()); assertEquals(key(GLFW.GLFW_KEY_LEFT_SHIFT), modifierKey.key());
        capture.press(key(GLFW.GLFW_KEY_R), true);
        assertTrue(capture.release(GLFW.GLFW_KEY_UNKNOWN, 63, true).isEmpty());
        var canceled = capture.release(GLFW.GLFW_KEY_ESCAPE, 0, true).orElseThrow();
        assertEquals(InputConstants.UNKNOWN, canceled.key()); assertSame(KeyModifier.NONE, canceled.modifier());
        var scan = InputConstants.Type.SCANCODE.getOrCreate(72);
        capture.press(scan, false);
        assertEquals(scan, capture.release(GLFW.GLFW_KEY_UNKNOWN, 72, false).orElseThrow().key());
    }
    private static InputConstants.Key key(int code) { return InputConstants.Type.KEYSYM.getOrCreate(code); }
}

package com.stardew.craft.platform.fabric.client.widget;

import static org.junit.jupiter.api.Assertions.*;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.stardew.craft.platform.mixin.client.widget.mixin.EditBoxTextShadowMixin;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class EditBoxTextShadowTest {
    @Test
    void shadowIsPerWidgetAndTheThreeNativeTextTypesKeepTheirFontCoordinatesColorAndAdvance() throws Exception {
        var first = new EditBoxTextShadowMixin() {}; var second = new EditBoxTextShadowMixin() {};
        assertTrue(first.stardewcraft$getTextShadow()); assertTrue(second.stardewcraft$getTextShadow());
        first.stardewcraft$setTextShadow(false);
        assertFalse(first.stardewcraft$getTextShadow()); assertTrue(second.stardewcraft$getTextShadow());
        var graphics = recordingGraphics(); var font = new Font(ignored -> null, false);
        Component hint = Component.literal("hint");
        FormattedCharSequence formatted = hint.getVisualOrderText();
        for (var text : new Object[] { formatted, hint, "suggestion" }) {
            Class<?> type = text instanceof String ? String.class : text instanceof Component ? Component.class : FormattedCharSequence.class;
            String name = type == String.class ? "stardewcraft$stringShadow" : type == Component.class ? "stardewcraft$hintShadow" : "stardewcraft$formattedShadow";
            var method = EditBoxTextShadowMixin.class.getDeclaredMethod(name, GuiGraphics.class, Font.class, type, int.class, int.class, int.class, Operation.class);
            method.setAccessible(true);
            assertEquals(47, method.invoke(first, graphics, font, text, 13, 19, 0xA1B2C3D4, null));
            assertSame(font, graphics.font); assertSame(text, graphics.text);
            assertEquals(13, graphics.x); assertEquals(19, graphics.y); assertEquals(0xA1B2C3D4, graphics.color); assertFalse(graphics.shadow);
            method.invoke(second, graphics, font, text, 13, 19, 0xA1B2C3D4, null);
            assertTrue(graphics.shadow);
        }
    }

    @Test
    void nativeRenderHasExactlyTheFiveNeoPatchedDrawCallsAndRetainsSelectionAndCursorFills() throws IOException {
        try (var bytes = getClass().getClassLoader().getResourceAsStream("net/minecraft/client/gui/components/EditBox.class")) {
            assertNotNull(bytes); var node = new ClassNode(); new ClassReader(bytes).accept(node, ClassReader.SKIP_FRAMES);
            var method = node.methods.stream().filter(value -> value.name.equals("renderWidget")).findFirst().orElseThrow();
            Map<String, Integer> calls = new HashMap<>(); boolean fill = false; boolean selection = false;
            for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call) {
                if (call.owner.equals("net/minecraft/client/gui/GuiGraphics") && call.name.equals("drawString")) calls.merge(call.desc, 1, Integer::sum);
                if (call.name.equals("fill")) fill = true;
                if (call.name.equals("renderHighlight")) selection = true;
            }
            assertEquals(Map.of("(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)I", 2,
                    "(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I", 1,
                    "(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)I", 2), calls);
            assertTrue(fill); assertTrue(selection);
        }
    }

    // GuiGraphics' real constructor reads Minecraft's sprite manager. Only its overridden recorders
    // are exercised here, so no Minecraft singleton, texture atlas, or OpenGL context is created.
    private static RecordingGraphics recordingGraphics() throws ReflectiveOperationException {
        var unsafe = Class.forName("sun.misc.Unsafe");
        var field = unsafe.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return (RecordingGraphics) unsafe.getMethod("allocateInstance", Class.class).invoke(field.get(null), RecordingGraphics.class);
    }

    private static final class RecordingGraphics extends GuiGraphics {
        Font font; Object text; int x, y, color; boolean shadow;
        RecordingGraphics() { super(null, (MultiBufferSource.BufferSource) null); }
        private int record(Font font, Object text, int x, int y, int color, boolean shadow) {
            this.font = font; this.text = text; this.x = x; this.y = y; this.color = color; this.shadow = shadow; return 47;
        }
        @Override public int drawString(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) { return record(font, text, x, y, color, shadow); }
        @Override public int drawString(Font font, Component text, int x, int y, int color, boolean shadow) { return record(font, text, x, y, color, shadow); }
        @Override public int drawString(Font font, String text, int x, int y, int color, boolean shadow) { return record(font, text, x, y, color, shadow); }
    }
}

package com.stardew.craft.platform.fabric.client.event.tooltip;

import static org.junit.jupiter.api.Assertions.*;
import com.stardew.craft.platform.fabric.client.event.RenderTooltipEvent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TooltipBackgroundTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test
    void editedFourColorPaletteReachesTheNativePaddedBackgroundAndInnerBorder() throws Exception {
        var graphics = PaintRecorder.create();
        var color = new RenderTooltipEvent.Color(ItemStack.EMPTY, graphics, 10, 20, null, 0, 0, 0, List.of());
        color.setBackgroundStart(1); color.setBackgroundEnd(2); color.setBorderStart(3); color.setBorderEnd(4);
        TooltipBackground.draw(graphics, 10, 20, 40, 30, 400, color);
        assertEquals(9, graphics.paint.size());
        assertEquals(new Paint(7, 16, 53, 17, 400, 1, 1), graphics.paint.getFirst());
        assertEquals(new Paint(7, 53, 53, 54, 400, 2, 2), graphics.paint.get(1));
        assertEquals(new Paint(7, 17, 53, 53, 400, 1, 2), graphics.paint.get(2));
        assertEquals(new Paint(7, 18, 8, 52, 400, 3, 4), graphics.paint.get(5));
        assertEquals(new Paint(7, 52, 53, 53, 400, 4, 4), graphics.paint.getLast());
        assertTrue(graphics.paint.stream().allMatch(paint -> paint.z == 400));
    }

    record Paint(int left, int top, int right, int bottom, int z, int start, int end) {}
    static final class PaintRecorder extends GuiGraphics {
        List<Paint> paint;
        private PaintRecorder() { super(null, (MultiBufferSource.BufferSource) null); }
        @Override public void fill(int left, int top, int right, int bottom, int z, int color) {
            paint.add(new Paint(left, top, right, bottom, z, color, color));
        }
        @Override public void fillGradient(int left, int top, int right, int bottom, int z, int start, int end) {
            paint.add(new Paint(left, top, right, bottom, z, start, end));
        }
        static PaintRecorder create() throws Exception {
            Class<?> type = Class.forName("sun.misc.Unsafe");
            var field = type.getDeclaredField("theUnsafe");
            if (!field.trySetAccessible()) throw new IllegalStateException("Paint fixture allocator unavailable");
            var recorder = (PaintRecorder) type.getMethod("allocateInstance", Class.class).invoke(field.get(null), PaintRecorder.class);
            recorder.paint = new ArrayList<>();
            return recorder;
        }
    }
}

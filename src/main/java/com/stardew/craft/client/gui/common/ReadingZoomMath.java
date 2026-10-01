package com.stardew.craft.client.gui.common;

/** Physical-pixel zoom/scroll geometry. Drawing and input consume the same result. */
public final class ReadingZoomMath {
    public static final int RAIL_PIXELS = 20;
    private ReadingZoomMath() {}

    public record Scrollbar(double x, double y, double width, double height,
                            double thumbStart, double thumbLength, boolean horizontal) {
        public boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
        public double fractionAt(double pointer, double grabOffset) {
            double start = horizontal ? x : y;
            double length = horizontal ? width : height;
            return com.stardew.craft.port.PortJava.clamp((pointer - start - grabOffset) / Math.max(1, length - thumbLength), 0, 1);
        }
    }

    public record Zoom(GuiLayoutMath.Viewport viewport, int viewWidth, int viewHeight,
                       double overflowX, double overflowY, Scrollbar horizontal, Scrollbar vertical) {}

    public static Zoom calculate(GuiLayoutMath.Viewport base, int pixelsWide, int pixelsHigh,
                                 float multiplier, double scrollX, double scrollY) {
        double pixelsPerUnit = base.scale() * base.windowScale() * multiplier;
        double contentW = base.width() * pixelsPerUnit, contentH = base.height() * pixelsPerUnit;
        int viewW = pixelsWide, viewH = pixelsHigh;
        // One scrollbar can create overflow on the other axis.
        for (int i = 0; i < 3; i++) {
            viewW = Math.max(1, pixelsWide - (contentH > viewH + .001 ? RAIL_PIXELS : 0));
            viewH = Math.max(1, pixelsHigh - (contentW > viewW + .001 ? RAIL_PIXELS : 0));
        }
        double overX = Math.max(0, contentW - viewW), overY = Math.max(0, contentH - viewH);
        double fx = com.stardew.craft.port.PortJava.clamp(scrollX, 0, 1), fy = com.stardew.craft.port.PortJava.clamp(scrollY, 0, 1);
        double x = overX > 0 ? -overX * fx : (viewW - contentW) / 2;
        double y = overY > 0 ? -overY * fy : (viewH - contentH) / 2;
        var viewport = new GuiLayoutMath.Viewport(base.width(), base.height(),
                pixelsPerUnit / base.windowScale(), x / base.windowScale(), y / base.windowScale(), base.windowScale(),
                pixelsWide / base.windowScale(), pixelsHigh / base.windowScale());
        double thumbW = Math.min(viewW, Math.max(32, viewW * viewW / Math.max(1, contentW)));
        double thumbH = Math.min(viewH, Math.max(32, viewH * viewH / Math.max(1, contentH)));
        var horizontal = overX > .001 ? new Scrollbar(0, viewH, viewW, pixelsHigh - viewH,
                fx * (viewW - thumbW), thumbW, true) : null;
        var vertical = overY > .001 ? new Scrollbar(viewW, 0, pixelsWide - viewW, viewH,
                fy * (viewH - thumbH), thumbH, false) : null;
        return new Zoom(viewport, viewW, viewH, overX, overY, horizontal, vertical);
    }
}

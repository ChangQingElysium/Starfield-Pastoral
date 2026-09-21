package com.stardew.craft.templates;

import java.util.List;

/** One-block round window. Frame and corner infill are separate material slots; panes are built in. */
public final class RoundWindowProfile {
    private static final List<TemplateBox> FRAME = List.of(
            new TemplateBox(4, 0, 0, 12, 2, 16),
            new TemplateBox(2, 2, 0, 6, 4, 16),
            new TemplateBox(10, 2, 0, 14, 4, 16),
            new TemplateBox(0, 4, 0, 4, 6, 16),
            new TemplateBox(12, 4, 0, 16, 6, 16),
            new TemplateBox(0, 6, 0, 2, 12, 16),
            new TemplateBox(14, 6, 0, 16, 12, 16),
            new TemplateBox(2, 10, 0, 4, 14, 16),
            new TemplateBox(12, 10, 0, 14, 14, 16),
            new TemplateBox(4, 12, 0, 6, 16, 16),
            new TemplateBox(10, 12, 0, 12, 16, 16),
            new TemplateBox(6, 14, 0, 10, 16, 16),
            new TemplateBox(7, 2, 3, 9, 14, 6),
            new TemplateBox(2, 7, 3, 7, 9, 6),
            new TemplateBox(9, 7, 3, 14, 9, 6));

    private static final List<TemplateBox> FILL = List.of(
            new TemplateBox(0, 0, 0, 4, 2, 16),
            new TemplateBox(12, 0, 0, 16, 2, 16),
            new TemplateBox(0, 2, 0, 2, 4, 16),
            new TemplateBox(14, 2, 0, 16, 4, 16),
            new TemplateBox(0, 12, 0, 2, 16, 16),
            new TemplateBox(14, 12, 0, 16, 16, 16),
            new TemplateBox(2, 14, 0, 4, 16, 16),
            new TemplateBox(12, 14, 0, 14, 16, 16));

    private static final List<TemplateBox> GLASS = List.of(
            new TemplateBox(6, 2, 5, 7, 7, 6),
            new TemplateBox(9, 2, 5, 10, 7, 6),
            new TemplateBox(4, 4, 5, 6, 7, 6),
            new TemplateBox(10, 4, 5, 12, 7, 6),
            new TemplateBox(2, 6, 5, 4, 7, 6),
            new TemplateBox(12, 6, 5, 14, 7, 6),
            new TemplateBox(2, 9, 5, 7, 10, 6),
            new TemplateBox(9, 9, 5, 14, 10, 6),
            new TemplateBox(4, 10, 5, 7, 12, 6),
            new TemplateBox(9, 10, 5, 12, 12, 6),
            new TemplateBox(6, 12, 5, 7, 14, 6),
            new TemplateBox(9, 12, 5, 10, 14, 6));

    public static List<TemplateBox> frame() { return FRAME; }
    public static List<TemplateBox> fill() { return FILL; }
    public static List<TemplateBox> glass() { return GLASS; }

    private RoundWindowProfile() {}
}

package com.stardew.craft.port.net.minecraft.world.item.component;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.Style;

/** 1.21 {@code minecraft:lore}; stored as the vanilla 1.20.1 {@code display.Lore} list of JSON strings. */
public record ItemLore(List<Component> lines, List<Component> styledLines) {
    public static final int MAX_LINES = 256;
    public static final ItemLore EMPTY = new ItemLore(List.of());
    private static final Style LORE_STYLE = Style.EMPTY.withColor(ChatFormatting.DARK_PURPLE).withItalic(true);

    public ItemLore(List<Component> lines) {
        this(lines, styled(lines));
    }

    public ItemLore {
        if (lines.size() > MAX_LINES) {
            throw new IllegalArgumentException("Got " + lines.size() + " lines, but maximum is " + MAX_LINES);
        }
        lines = List.copyOf(lines);
        styledLines = List.copyOf(styledLines);
    }

    private static List<Component> styled(List<Component> lines) {
        List<Component> styled = new ArrayList<>(lines.size());
        for (Component line : lines) {
            styled.add(ComponentUtils.mergeStyles(line.copy(), LORE_STYLE));
        }
        return styled;
    }

    public ItemLore withLineAdded(Component line) {
        List<Component> updated = new ArrayList<>(lines);
        updated.add(line);
        return new ItemLore(updated);
    }
}

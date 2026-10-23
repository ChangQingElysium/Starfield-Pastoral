package com.stardew.craft.platform.fabric.client.event;

import com.mojang.datafixers.util.Either;
import com.stardew.craft.platform.fabric.event.Event;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

@Environment(EnvType.CLIENT)
public abstract class RenderTooltipEvent extends Event {
    private final ItemStack stack;
    private final GuiGraphics graphics;
    private final List<ClientTooltipComponent> components;
    protected int x;
    protected int y;
    protected Font font;
    protected RenderTooltipEvent(boolean cancelable, ItemStack stack, GuiGraphics graphics, int x, int y, Font font,
                                 List<ClientTooltipComponent> components) {
        super(cancelable);
        this.stack = stack; this.graphics = graphics; this.x = x; this.y = y; this.font = font;
        this.components = Collections.unmodifiableList(components);
    }
    public ItemStack getItemStack() { return stack; }
    public GuiGraphics getGraphics() { return graphics; }
    public List<ClientTooltipComponent> getComponents() { return components; }
    public int getX() { return x; }
    public int getY() { return y; }
    public Font getFont() { return font; }

    public static final class GatherComponents extends Event {
        private final ItemStack stack;
        private final int screenWidth;
        private final int screenHeight;
        private final List<Either<FormattedText, TooltipComponent>> elements;
        private int maxWidth;
        public GatherComponents(ItemStack stack, int screenWidth, int screenHeight,
                                List<Either<FormattedText, TooltipComponent>> elements, int maxWidth) {
            super(true);
            this.stack = stack; this.screenWidth = screenWidth; this.screenHeight = screenHeight;
            this.elements = elements instanceof ArrayList<?> ? elements : new ArrayList<>(elements);
            this.maxWidth = maxWidth;
        }
        public ItemStack getItemStack() { return stack; }
        public int getScreenWidth() { return screenWidth; }
        public int getScreenHeight() { return screenHeight; }
        public List<Either<FormattedText, TooltipComponent>> getTooltipElements() { return elements; }
        public int getMaxWidth() { return maxWidth; }
        public void setMaxWidth(int value) { maxWidth = value; }
    }

    public static final class Pre extends RenderTooltipEvent {
        private final int screenWidth;
        private final int screenHeight;
        private final ClientTooltipPositioner positioner;
        public Pre(ItemStack stack, GuiGraphics graphics, int x, int y, int screenWidth, int screenHeight, Font font,
                   List<ClientTooltipComponent> components, ClientTooltipPositioner positioner) {
            super(true, stack, graphics, x, y, font, components);
            this.screenWidth = screenWidth; this.screenHeight = screenHeight; this.positioner = positioner;
        }
        public int getScreenWidth() { return screenWidth; }
        public int getScreenHeight() { return screenHeight; }
        public ClientTooltipPositioner getTooltipPositioner() { return positioner; }
        public void setFont(Font value) { font = value; }
        public void setX(int value) { x = value; }
        public void setY(int value) { y = value; }
    }

    public static final class Color extends RenderTooltipEvent {
        private final int originalBackground;
        private final int originalBorderStart;
        private final int originalBorderEnd;
        private int backgroundStart;
        private int backgroundEnd;
        private int borderStart;
        private int borderEnd;
        public Color(ItemStack stack, GuiGraphics graphics, int x, int y, Font font, int background,
                     int borderStart, int borderEnd, List<ClientTooltipComponent> components) {
            super(false, stack, graphics, x, y, font, components);
            this.originalBackground = background; this.originalBorderStart = borderStart; this.originalBorderEnd = borderEnd;
            this.backgroundStart = background; this.backgroundEnd = background; this.borderStart = borderStart; this.borderEnd = borderEnd;
        }
        public int getBackgroundStart() { return backgroundStart; }
        public int getBackgroundEnd() { return backgroundEnd; }
        public int getBorderStart() { return borderStart; }
        public int getBorderEnd() { return borderEnd; }
        public int getOriginalBackgroundStart() { return originalBackground; }
        public int getOriginalBackgroundEnd() { return originalBackground; }
        public int getOriginalBorderStart() { return originalBorderStart; }
        public int getOriginalBorderEnd() { return originalBorderEnd; }
        public void setBackground(int value) { backgroundStart = value; backgroundEnd = value; }
        public void setBackgroundStart(int value) { backgroundStart = value; }
        public void setBackgroundEnd(int value) { backgroundEnd = value; }
        public void setBorderStart(int value) { borderStart = value; }
        public void setBorderEnd(int value) { borderEnd = value; }
    }
}

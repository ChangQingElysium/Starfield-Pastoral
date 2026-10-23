package com.stardew.craft.platform.fabric.client.event;

import com.stardew.craft.platform.fabric.event.Event;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public abstract class ScreenEvent extends Event {
    private final Screen screen;
    protected ScreenEvent(Screen screen, boolean cancelable) { super(cancelable); this.screen = screen; }
    public Screen getScreen() { return screen; }

    public abstract static class Render extends ScreenEvent {
        private final GuiGraphics graphics;
        private final int mouseX, mouseY;
        private final float partialTick;
        protected Render(Screen screen, GuiGraphics graphics, int x, int y, float partialTick, boolean cancelable) {
            super(screen, cancelable); this.graphics = graphics; mouseX = x; mouseY = y; this.partialTick = partialTick;
        }
        public GuiGraphics getGuiGraphics() { return graphics; }
        public int getMouseX() { return mouseX; }
        public int getMouseY() { return mouseY; }
        public float getPartialTick() { return partialTick; }
        public static final class Pre extends Render {
            public Pre(Screen screen, GuiGraphics graphics, int x, int y, float partialTick) { super(screen, graphics, x, y, partialTick, true); }
        }
        public static final class Post extends Render {
            public Post(Screen screen, GuiGraphics graphics, int x, int y, float partialTick) { super(screen, graphics, x, y, partialTick, false); }
        }
    }
    public static final class Opening extends ScreenEvent {
        @Nullable private final Screen currentScreen;
        @Nullable private Screen newScreen;
        public Opening(@Nullable Screen currentScreen, Screen newScreen) { super(newScreen, true); this.currentScreen = currentScreen; this.newScreen = newScreen; }
        @Nullable public Screen getCurrentScreen() { return currentScreen; }
        @Nullable public Screen getNewScreen() { return newScreen; }
        public void setNewScreen(@Nullable Screen screen) { newScreen = screen; }
    }
    public static final class Closing extends ScreenEvent {
        public Closing(Screen screen) { super(screen, false); }
    }
    public abstract static class MouseInput extends ScreenEvent {
        private final double x, y;
        protected MouseInput(Screen screen, double x, double y, boolean cancelable) { super(screen, cancelable); this.x = x; this.y = y; }
        public double getMouseX() { return x; }
        public double getMouseY() { return y; }
    }
    public abstract static class MouseButtonPressed extends MouseInput {
        private final int button;
        protected MouseButtonPressed(Screen screen, double x, double y, int button, boolean cancelable) { super(screen, x, y, cancelable); this.button = button; }
        public int getButton() { return button; }
        public static final class Pre extends MouseButtonPressed {
            public Pre(Screen screen, double x, double y, int button) { super(screen, x, y, button, true); }
        }
        public static final class Post extends MouseButtonPressed {
            private final boolean handled;
            private Result result = Result.DEFAULT;
            public Post(Screen screen, double x, double y, int button, boolean handled) { super(screen, x, y, button, false); this.handled = handled; }
            public boolean wasClickHandled() { return handled; }
            public boolean getClickResult() { return result == Result.FORCE_HANDLED || result == Result.DEFAULT && handled; }
            public void setResult(Result result) { this.result = java.util.Objects.requireNonNull(result); }
            public Result getResult() { return result; }
            public enum Result { FORCE_HANDLED, DEFAULT, FORCE_UNHANDLED }
        }
    }
    public abstract static class MouseButtonReleased extends MouseInput {
        private final int button;
        protected MouseButtonReleased(Screen screen, double x, double y, int button, boolean cancelable) { super(screen, x, y, cancelable); this.button = button; }
        public int getButton() { return button; }
        public static final class Pre extends MouseButtonReleased {
            public Pre(Screen screen, double x, double y, int button) { super(screen, x, y, button, true); }
        }
        public static final class Post extends MouseButtonReleased {
            private final boolean handled;
            private Result result = Result.DEFAULT;
            public Post(Screen screen, double x, double y, int button, boolean handled) { super(screen, x, y, button, false); this.handled = handled; }
            public boolean wasReleaseHandled() { return handled; }
            public boolean getReleaseResult() { return result == Result.FORCE_HANDLED || result == Result.DEFAULT && handled; }
            public void setResult(Result result) { this.result = java.util.Objects.requireNonNull(result); }
            public Result getResult() { return result; }
            public enum Result { FORCE_HANDLED, DEFAULT, FORCE_UNHANDLED }
        }
    }
    public abstract static class MouseDragged extends MouseInput {
        private final int button;
        private final double dragX, dragY;
        protected MouseDragged(Screen screen, double x, double y, int button, double dx, double dy, boolean cancelable) {
            super(screen, x, y, cancelable); this.button = button; dragX = dx; dragY = dy;
        }
        public int getMouseButton() { return button; }
        public double getDragX() { return dragX; }
        public double getDragY() { return dragY; }
        public static final class Pre extends MouseDragged {
            public Pre(Screen screen, double x, double y, int button, double dx, double dy) { super(screen, x, y, button, dx, dy, true); }
        }
        public static final class Post extends MouseDragged {
            public Post(Screen screen, double x, double y, int button, double dx, double dy) { super(screen, x, y, button, dx, dy, false); }
        }
    }
    public abstract static class MouseScrolled extends MouseInput {
        private final double dx, dy;
        protected MouseScrolled(Screen screen, double x, double y, double dx, double dy, boolean cancelable) {
            super(screen, x, y, cancelable); this.dx = dx; this.dy = dy;
        }
        public double getScrollDeltaX() { return dx; }
        public double getScrollDeltaY() { return dy; }
        public static final class Pre extends MouseScrolled {
            public Pre(Screen screen, double x, double y, double dx, double dy) { super(screen, x, y, dx, dy, true); }
        }
        public static final class Post extends MouseScrolled {
            public Post(Screen screen, double x, double y, double dx, double dy) { super(screen, x, y, dx, dy, false); }
        }
    }
    public abstract static class KeyInput extends ScreenEvent {
        private final int key, scan, modifiers;
        protected KeyInput(Screen screen, int key, int scan, int modifiers, boolean cancelable) { super(screen, cancelable); this.key = key; this.scan = scan; this.modifiers = modifiers; }
        public int getKeyCode() { return key; }
        public int getScanCode() { return scan; }
        public int getModifiers() { return modifiers; }
    }
    public abstract static class KeyPressed extends KeyInput {
        protected KeyPressed(Screen screen, int key, int scan, int modifiers) { super(screen, key, scan, modifiers, true); }
        public static final class Pre extends KeyPressed { public Pre(Screen screen, int key, int scan, int modifiers) { super(screen, key, scan, modifiers); } }
        public static final class Post extends KeyPressed { public Post(Screen screen, int key, int scan, int modifiers) { super(screen, key, scan, modifiers); } }
    }
    public abstract static class KeyReleased extends KeyInput {
        protected KeyReleased(Screen screen, int key, int scan, int modifiers) { super(screen, key, scan, modifiers, true); }
        public static final class Pre extends KeyReleased { public Pre(Screen screen, int key, int scan, int modifiers) { super(screen, key, scan, modifiers); } }
        public static final class Post extends KeyReleased { public Post(Screen screen, int key, int scan, int modifiers) { super(screen, key, scan, modifiers); } }
    }
}

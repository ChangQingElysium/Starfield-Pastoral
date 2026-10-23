package com.stardew.craft.platform.fabric.client.event;

import com.stardew.craft.platform.fabric.event.Event;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.InteractionHand;

/** Client input notifications at the corresponding native processing boundaries. */
@Environment(EnvType.CLIENT)
public abstract class InputEvent extends Event {
    protected InputEvent(boolean cancelable) { super(cancelable); }

    public abstract static class MouseButton extends InputEvent {
        private final int button;
        private final int action;
        private final int modifiers;

        protected MouseButton(boolean cancelable, int button, int action, int modifiers) {
            super(cancelable);
            this.button = button;
            this.action = action;
            this.modifiers = modifiers;
        }

        public int getButton() { return button; }
        public int getAction() { return action; }
        public int getModifiers() { return modifiers; }

        public static class Pre extends MouseButton {
            public Pre(int button, int action, int modifiers) { super(true, button, action, modifiers); }
        }

        public static class Post extends MouseButton {
            public Post(int button, int action, int modifiers) { super(false, button, action, modifiers); }
        }
    }

    public static class MouseScrollingEvent extends InputEvent {
        private final double scrollDeltaX;
        private final double scrollDeltaY;
        private final boolean leftDown;
        private final boolean middleDown;
        private final boolean rightDown;
        private final double mouseX;
        private final double mouseY;

        public MouseScrollingEvent(double scrollDeltaX, double scrollDeltaY, boolean leftDown, boolean middleDown,
                                   boolean rightDown, double mouseX, double mouseY) {
            super(true);
            this.scrollDeltaX = scrollDeltaX;
            this.scrollDeltaY = scrollDeltaY;
            this.leftDown = leftDown;
            this.middleDown = middleDown;
            this.rightDown = rightDown;
            this.mouseX = mouseX;
            this.mouseY = mouseY;
        }

        public double getScrollDeltaX() { return scrollDeltaX; }
        public double getScrollDeltaY() { return scrollDeltaY; }
        public boolean isLeftDown() { return leftDown; }
        public boolean isMiddleDown() { return middleDown; }
        public boolean isRightDown() { return rightDown; }
        public double getMouseX() { return mouseX; }
        public double getMouseY() { return mouseY; }
    }

    public static class Key extends InputEvent {
        private final int key;
        private final int scanCode;
        private final int action;
        private final int modifiers;

        public Key(int key, int scanCode, int action, int modifiers) {
            super(false);
            this.key = key;
            this.scanCode = scanCode;
            this.action = action;
            this.modifiers = modifiers;
        }

        public int getKey() { return key; }
        public int getScanCode() { return scanCode; }
        public int getAction() { return action; }
        public int getModifiers() { return modifiers; }
    }

    public static class InteractionKeyMappingTriggered extends InputEvent {
        private final int button;
        private final KeyMapping keyMapping;
        private final InteractionHand hand;
        private boolean handSwing = true;

        public InteractionKeyMappingTriggered(int button, KeyMapping keyMapping, InteractionHand hand) {
            super(true);
            this.button = button;
            this.keyMapping = keyMapping;
            this.hand = hand;
        }

        public void setSwingHand(boolean value) { handSwing = value; }
        public boolean shouldSwingHand() { return handSwing; }
        public InteractionHand getHand() { return hand; }
        public boolean isAttack() { return button == 0; }
        public boolean isUseItem() { return button == 1; }
        public boolean isPickBlock() { return button == 2; }
        public KeyMapping getKeyMapping() { return keyMapping; }
    }
}

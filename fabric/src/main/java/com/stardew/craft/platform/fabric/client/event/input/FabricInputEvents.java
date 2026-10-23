package com.stardew.craft.platform.fabric.client.event.input;

import com.stardew.craft.platform.fabric.client.event.InputEvent;
import com.stardew.craft.platform.fabric.event.IEventBus;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.InteractionHand;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public final class FabricInputEvents {
    private static IEventBus installedBus;
    private FabricInputEvents() {}

    public static synchronized void install(IEventBus bus) {
        Objects.requireNonNull(bus, "bus");
        if (installedBus == bus) return;
        if (installedBus != null) throw new IllegalStateException("Client input events already have an installed bus");
        installedBus = bus;
    }

    public static void key(int key, int scanCode, int action, int modifiers) {
        bus().post(new InputEvent.Key(key, scanCode, action, modifiers));
    }

    public static boolean mousePre(int button, int action, int modifiers) {
        return bus().post(new InputEvent.MouseButton.Pre(button, action, modifiers)).isCanceled();
    }

    public static void mousePost(int button, int action, int modifiers) {
        bus().post(new InputEvent.MouseButton.Post(button, action, modifiers));
    }

    public static boolean scroll(double scrollX, double scrollY, boolean left, boolean middle, boolean right,
                                 double mouseX, double mouseY) {
        return bus().post(new InputEvent.MouseScrollingEvent(scrollX, scrollY, left, middle, right, mouseX, mouseY)).isCanceled();
    }

    public static InputEvent.InteractionKeyMappingTriggered interaction(int button, KeyMapping mapping, InteractionHand hand) {
        return bus().post(new InputEvent.InteractionKeyMappingTriggered(button, mapping, hand));
    }

    /** A canceled attack/use still swings by default. Held attack also creates its normal crack particles. */
    public static boolean consumeCanceled(InputEvent.InteractionKeyMappingTriggered event, Runnable beforeSwing, Runnable swing) {
        if (!event.isCanceled()) return false;
        if (event.shouldSwingHand()) {
            beforeSwing.run();
            swing.run();
        }
        return true;
    }

    public static boolean shouldSwing(@Nullable InputEvent.InteractionKeyMappingTriggered event) {
        return event == null || event.shouldSwingHand();
    }

    private static IEventBus bus() {
        if (installedBus == null) throw new IllegalStateException("Client input event bus is not installed");
        return installedBus;
    }
}

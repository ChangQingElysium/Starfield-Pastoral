package com.stardew.craft.port;

import com.stardew.craft.client.gui.common.GuiLayoutMath;
import com.stardew.craft.mixin.PortContainerEventHandlerMixin;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Optional;
import net.minecraft.client.gui.components.events.GuiEventListener;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Executes production input bodies and checks the native bytecode targets without creating a Minecraft client. */
public final class InheritanceClientInputCheck {
    private static final class Child implements GuiEventListener {
        private int releases;
        private boolean focused;
        @Override public boolean mouseReleased(double x, double y, int button) { releases++; return true; }
        @Override public void setFocused(boolean value) { focused = value; }
        @Override public boolean isFocused() { return focused; }
    }

    private static final class ModContainer implements PortContainerEventHandlerMixin {
        private boolean dragging = true;
        private final Child focused = new Child();
        private final Child hovered = new Child();
        @Override public boolean isDragging() { return dragging; }
        @Override public void setDragging(boolean value) { dragging = value; }
        @Override public GuiEventListener getFocused() { return focused; }
        @Override public Optional<GuiEventListener> getChildAt(double x, double y) { return Optional.of(hovered); }
    }

    public static void main(String[] args) throws Exception {
        int layouts = 0;
        for (int width : new int[]{854, 1365, 1920, 2561}) {
            for (int height : new int[]{481, 768, 1080}) {
                for (double guiScale : new double[]{1, 2, 3, 4, 5}) {
                    var layout = GuiLayoutMath.viewport(width, height, guiScale);
                    var movement = new PortMouseMovement();
                    movement.accumulate(0, 0, 90, 80, true, true);
                    check(!movement.hasDelta(), "Ignored first movement accumulated");
                    movement.accumulate(90, 80, 100, 90, false, false);
                    check(!movement.hasDelta(), "Inactive window accumulated movement");
                    // Two GLFW events must produce one frame's total delta, not the most recent event's delta.
                    movement.accumulate(100, 90, 120, 97, false, true);
                    movement.accumulate(120, 97, 155, 110, false, true);
                    var frame = movement.frame(layout, 155, 110, width, height, layout.width(), layout.height());
                    near(frame.mouseX(), layout.rawMouseX(155, width), "hover X");
                    near(frame.mouseY(), layout.rawMouseY(110, height), "hover Y");
                    near(frame.dragX(), layout.rawMouseX(155, width) - layout.rawMouseX(100, width), "drag X");
                    near(frame.dragY(), layout.rawMouseY(110, height) - layout.rawMouseY(90, height), "drag Y");
                    movement.reset();
                    check(!movement.hasDelta(), "Movement repeated in the following frame");
                    movement.accumulate(155, 110, 160, 112, false, true);
                    var next = movement.frame(layout, 160, 112, width, height, layout.width(), layout.height());
                    near(next.dragX(), layout.rawMouseX(160, width) - layout.rawMouseX(155, width), "next frame X");
                    layouts++;
                }
            }
        }
        var nativeMovement = new PortMouseMovement();
        nativeMovement.accumulate(1, 2, 5, 8, false, true);
        var nativeFrame = nativeMovement.frame(null, 5, 8, 800, 600, 400, 300);
        near(nativeFrame.mouseX(), 2.5, "unmapped X");
        near(nativeFrame.dragY(), 3.0, "unmapped delta");

        var mod = new ModContainer();
        check(mod.mouseReleased(1000, 1000, 0), "Dragged child did not consume release");
        check(!mod.dragging && mod.focused.releases == 1 && mod.hovered.releases == 0,
                "Left drag release was not delivered once to the focused child");
        mod.dragging = true;
        mod.mouseReleased(0, 0, 1);
        check(mod.dragging && mod.hovered.releases == 1, "Right release ended a left drag");
        var vanillaHovered = new Child();
        boolean[] vanillaDragging = {true};
        PortContainerEventHandlerMixin vanilla = (PortContainerEventHandlerMixin) Proxy.newProxyInstance(
                InheritanceClientInputCheck.class.getClassLoader(), new Class<?>[]{PortContainerEventHandlerMixin.class},
                (proxy, method, arguments) -> {
                    if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, arguments);
                    return switch (method.getName()) {
                        case "isDragging" -> vanillaDragging[0];
                        case "setDragging" -> { vanillaDragging[0] = (boolean) arguments[0]; yield null; }
                        case "getFocused" -> mod.focused;
                        case "getChildAt" -> Optional.of(vanillaHovered);
                        default -> throw new AssertionError(method);
                    };
                });
        vanilla.mouseReleased(0, 0, 1);
        check(!vanillaDragging[0] && vanillaHovered.releases == 1 && mod.focused.releases == 1,
                "Vanilla container no longer follows its 1.20.1 release path");

        // Client-only mixins are not loaded by a dedicated GameTest server; check these exact targets separately.
        targetInvocation("net/minecraft/client/MouseHandler", "onMove", "net/minecraft/client/gui/screens/Screen",
                "wrapScreenError", "(Ljava/lang/Runnable;Ljava/lang/String;Ljava/lang/String;)V", 2);
        targetInvocation("net/minecraft/client/MouseHandler", "onMove", "net/minecraft/client/gui/screens/Screen",
                "afterMouseMove", "()V", 1);
        targetInvocation("net/minecraft/client/Minecraft", "runTick", "net/minecraft/client/MouseHandler",
                "turnPlayer", "()V", 1);
        targetInvocation("net/minecraft/client/multiplayer/MultiPlayerGameMode", "destroyBlock", "net/minecraft/world/level/Level",
                "getFluidState", "(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;", 1);
        var mouse = classNode("net/minecraft/client/MouseHandler");
        long osxReads = mouse.methods.stream().filter(method -> method.name.equals("onScroll"))
                .flatMap(method -> java.util.stream.StreamSupport.stream(method.instructions.spliterator(), false))
                .filter(instruction -> instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC
                        && field.owner.equals("net/minecraft/client/Minecraft") && field.name.equals("ON_OSX")).count();
        check(osxReads == 1, "Scroll redirect's ON_OSX target changed: " + osxReads);
        check(classNode("net/minecraft/client/particle/Particle").methods.stream()
                .anyMatch(method -> method.name.equals("setAlpha") && method.desc.equals("(F)V")), "Particle alpha invoker target missing");
        check(classNode("net/minecraft/client/gui/components/events/ContainerEventHandler").methods.stream()
                .anyMatch(method -> method.name.equals("mouseReleased") && method.desc.equals("(DDI)Z")), "Container release overwrite target missing");
        System.out.println("PASS: " + layouts + " viewport/GUI-scale layouts, accumulated movement/one-frame reset, "
                + "focused drag release, vanilla fallback and all client input/particle bytecode targets");
    }

    private static ClassNode classNode(String name) throws Exception {
        try (InputStream stream = InheritanceClientInputCheck.class.getClassLoader().getResourceAsStream(name + ".class")) {
            check(stream != null, "Missing native class " + name);
            var node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }

    private static void targetInvocation(String target, String method, String owner, String name, String descriptor,
            long expected) throws Exception {
        long matches = classNode(target).methods.stream().filter(candidate -> candidate.name.equals(method))
                .flatMap(candidate -> java.util.stream.StreamSupport.stream(candidate.instructions.spliterator(), false))
                .filter(instruction -> instruction instanceof MethodInsnNode call && call.owner.equals(owner)
                        && call.name.equals(name) && call.desc.equals(descriptor)).count();
        check(matches == expected, target + "#" + method + " -> " + owner + "#" + name + ": " + matches + " != " + expected);
    }

    private static void near(double actual, double expected, String label) {
        check(Math.abs(actual - expected) < 1.0E-8, label + ": " + actual + " != " + expected);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

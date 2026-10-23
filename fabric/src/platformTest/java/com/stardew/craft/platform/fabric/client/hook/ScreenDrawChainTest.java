package com.stardew.craft.platform.fabric.client.hook;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.platform.fabric.client.event.ScreenEvent;
import com.stardew.craft.platform.fabric.client.event.screen.FabricScreenEvents;
import com.stardew.craft.platform.fabric.event.EventBus;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

class ScreenDrawChainTest {
    @BeforeAll
    static void install() {
        var bus = new EventBus();
        bus.addListener(ScreenEvent.Render.Pre.class, event -> {
            if (event.getScreen() instanceof TestScreen screen) {
                screen.order.add("pre");
                if (screen.cancel) event.setCanceled(true);
            }
        });
        bus.addListener(ScreenEvent.Render.Post.class, event -> {
            if (event.getScreen() instanceof TestScreen screen) screen.order.add("post");
        });
        FabricScreenEvents.install(bus);
    }

    @Test
    void keepsTheOriginalScreenOperationBetweenNeoPreAndPost() throws Exception {
        var screen = new TestScreen();
        var graphics = graphics();
        ClientHooks.drawScreen(screen, graphics, -7, 31, .375F, arguments -> {
            assertSame(screen, arguments[0]);
            assertSame(graphics, arguments[1]);
            assertEquals(-7, arguments[2]);
            assertEquals(31, arguments[3]);
            assertEquals(.375F, arguments[4]);
            screen.order.add("fabric-before");
            ((Screen) arguments[0]).renderWithTooltip((GuiGraphics) arguments[1],
                    (int) arguments[2], (int) arguments[3], (float) arguments[4]);
            screen.order.add("fabric-after");
            return null;
        });
        assertEquals(List.of("pre", "fabric-before", "native", "fabric-after", "post"), screen.order);
        assertEquals(new org.joml.Matrix4f(), graphics.pose().last().pose());
        assertThrows(IllegalStateException.class, () -> internal(screen, graphics, 0, 0));
    }

    @Test
    void canceledPreKeepsNeoPostAndDoesNotRunTheWrappedScreenOperation() throws Exception {
        var screen = new TestScreen();
        screen.cancel = true;
        ClientHooks.drawScreen(screen, graphics(), 0, 0, 0, arguments -> {
            fail("A canceled screen draw must not enter the Fabric/native chain");
            return null;
        });
        assertEquals(List.of("pre", "post"), screen.order);
    }

    @Test
    void aNestedDrawRestoresTheOuterOperationAndForwardsTheViewportAdjustedArguments() throws Exception {
        nested(false);
    }

    @Test
    void aFailedNestedDrawRestoresBothTheOuterOperationAndPoseBeforeTheOuterContinues() throws Exception {
        nested(true);
    }

    private static void nested(boolean failInner) throws Exception {
        var outer = new TestScreen();
        var inner = new TestScreen();
        var graphics = graphics();
        var outerCalls = new AtomicInteger();
        var innerCalls = new AtomicInteger();
        var failure = new IllegalArgumentException("nested screen failure");
        ClientHooks.drawScreen(outer, graphics, 5, 6, 0, arguments -> {
            assertSame(outer, arguments[0]);
            if (outerCalls.incrementAndGet() == 1) {
                graphics.pose().translate(3, 4, 0);
                Runnable nested = () -> ClientHooks.drawScreen(inner, graphics, 1, 2, 0, nestedArguments -> {
                    assertSame(inner, nestedArguments[0]);
                    innerCalls.incrementAndGet();
                    graphics.pose().translate(9, 9, 0);
                    if (failInner) throw failure;
                    return null;
                });
                if (failInner) assertSame(failure, assertThrows(IllegalArgumentException.class, nested::run));
                else nested.run();
                assertEquals(3, graphics.pose().last().pose().m30());
                assertEquals(4, graphics.pose().last().pose().m31());
                // This is the stable five-argument viewport target. Its current arguments,
                // rather than those captured at public drawScreen entry, reach the operation.
                internal(outer, graphics, 17, -8);
            } else {
                assertEquals(17, arguments[2]);
                assertEquals(-8, arguments[3]);
            }
            return null;
        });
        assertEquals(2, outerCalls.get());
        assertEquals(1, innerCalls.get());
        assertEquals(new org.joml.Matrix4f(), graphics.pose().last().pose());
        assertThrows(IllegalStateException.class, () -> internal(outer, graphics, 0, 0));
    }

    @Test
    void theGameRendererMixinForwardsItsActualOperationAndKeepsTheViewportTargetDescriptor() throws Exception {
        var node = new ClassNode();
        try (var bytes = getClass().getResourceAsStream(
                "/com/stardew/craft/platform/mixin/client/event/screen/mixin/ScreenDrawingMixin.class")) {
            assertNotNull(bytes);
            new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        var method = node.methods.stream().filter(value -> value.name.equals("stardewcraft$screenFrame")).findFirst().orElseThrow();
        var calls = java.util.stream.StreamSupport.stream(method.instructions.spliterator(), false)
                .filter(value -> value instanceof MethodInsnNode call && call.owner.equals(
                        "com/stardew/craft/platform/fabric/client/hook/ClientHooks") && call.name.equals("drawScreen"))
                .map(value -> (MethodInsnNode) value).toList();
        assertEquals(1, calls.size());
        assertTrue(calls.getFirst().desc.endsWith("FLcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)V"));
        var forwarded = assertInstanceOf(VarInsnNode.class, calls.getFirst().getPrevious());
        assertEquals(Opcodes.ALOAD, forwarded.getOpcode());
        assertEquals(6, forwarded.var);
        assertNotNull(ClientHooks.class.getDeclaredMethod("drawScreenInternal",
                Screen.class, GuiGraphics.class, int.class, int.class, float.class));
    }

    private static void internal(Screen screen, GuiGraphics graphics, int x, int y) {
        try {
            var method = ClientHooks.class.getDeclaredMethod("drawScreenInternal",
                    Screen.class, GuiGraphics.class, int.class, int.class, float.class);
            method.setAccessible(true);
            method.invoke(null, screen, graphics, x, y, 0F);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            if (failure.getCause() instanceof Error error) throw error;
            throw new AssertionError(failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    /** Only native PoseStack storage is needed; no Minecraft/window/renderer is constructed. */
    private static RecordingGraphics graphics() throws ReflectiveOperationException {
        var unsafe = Class.forName("sun.misc.Unsafe");
        var field = unsafe.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        var graphics = (RecordingGraphics) unsafe.getMethod("allocateInstance", Class.class)
                .invoke(field.get(null), RecordingGraphics.class);
        graphics.stack = new PoseStack();
        return graphics;
    }

    private static final class RecordingGraphics extends GuiGraphics {
        private PoseStack stack;
        private RecordingGraphics() { super(null, null); }
        @Override public PoseStack pose() { return stack; }
    }

    private static final class TestScreen extends Screen {
        final List<String> order = new ArrayList<>();
        boolean cancel;
        TestScreen() { super(Component.literal("test")); }
        @Override public void render(GuiGraphics graphics, int x, int y, float partialTick) {
            order.add("native");
        }
    }
}

package com.stardew.craft.platform.fabric.client.event.tooltip;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.datafixers.util.Either;
import com.stardew.craft.platform.fabric.client.event.RegisterClientTooltipComponentFactoriesEvent;
import com.stardew.craft.platform.fabric.client.event.RenderTooltipEvent;
import com.stardew.craft.platform.fabric.client.extension.FabricClientExtensions;
import com.stardew.craft.platform.fabric.client.extension.IClientItemExtensions;
import com.stardew.craft.platform.fabric.event.EventBus;
import com.stardew.craft.platform.fabric.event.PlayerEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.client.rendering.v1.TooltipComponentCallback;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TooltipEventsTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test
    void gatherAllowsMixedComponentsBeforeSplittingAndKeepsEmptyStyledLines() {
        var bus = new EventBus();
        var font = new MeasureFont();
        var image = new Image();
        var clientImage = new ClientImage();
        List<Either<FormattedText, TooltipComponent>> original = List.of(Either.left(Component.literal("original")));
        bus.addListener(RenderTooltipEvent.GatherComponents.class, event -> {
            assertSame(Items.APPLE, event.getItemStack().getItem());
            assertEquals(120, event.getScreenWidth());
            event.getTooltipElements().clear();
            event.getTooltipElements().add(Either.left(Component.literal("abcdefghij")));
            event.getTooltipElements().add(Either.left(Component.empty()));
            event.getTooltipElements().add(Either.right(image));
            event.setMaxWidth(12);
        });
        var result = FabricTooltipEvents.gatherElements(bus, new ItemStack(Items.APPLE), original, 20, 120, 100, font, component -> {
            assertSame(image, component); return clientImage;
        });
        assertEquals(List.of(12), font.splitWidths);
        assertEquals(3, result.size());
        assertSame(clientImage, result.getLast());
        assertEquals("original", original.getFirst().left().orElseThrow().getString());
    }

    @Test
    void canceledGatherNeverMeasuresSplitsOrCallsAnyImageFactory() {
        var bus = new EventBus();
        bus.addListener(RenderTooltipEvent.GatherComponents.class, event -> event.setCanceled(true));
        var font = new MeasureFont();
        var result = FabricTooltipEvents.gatherElements(bus, ItemStack.EMPTY,
                List.of(Either.left(Component.literal("some text")), Either.right(new Image())), 30, 40, 50, font,
                component -> { fail("factory called after cancellation"); return null; });
        assertTrue(result.isEmpty());
        assertEquals(0, font.measures);
        assertTrue(font.splitWidths.isEmpty());
    }

    @Test
    void wrappingUsesTheMouseSideAndRetainsUnlimitedMaximumWidthWhenItFits() {
        var font = new MeasureFont();
        var bus = new EventBus();
        FabricTooltipEvents.gatherElements(bus, ItemStack.EMPTY, List.of(Either.left(Component.literal("a".repeat(30)))),
                80, 100, 100, font);
        assertEquals(List.of(60), font.splitWidths); // right side: mouseX - 12 - 8
        font.splitWidths.clear();
        FabricTooltipEvents.gatherElements(bus, ItemStack.EMPTY, List.of(Either.left(Component.literal("a".repeat(30)))),
                20, 100, 100, font);
        assertEquals(List.of(64), font.splitWidths); // left side: screenWidth - 16 - mouseX
        font.splitWidths.clear();
        FabricTooltipEvents.gatherElements(bus, ItemStack.EMPTY, List.of(Either.left(Component.literal("short"))),
                20, 100, 100, font);
        assertTrue(font.splitWidths.isEmpty());
    }

    @Test
    void preCanChangeFontAndCoordinatesWhileColorKeepsItsOriginalPalette() {
        var first = new MeasureFont();
        var replacement = new MeasureFont();
        List<ClientTooltipComponent> components = new ArrayList<>(List.of(new ClientImage()));
        var pre = new RenderTooltipEvent.Pre(ItemStack.EMPTY, null, 3, 4, 123, 77, first, components, DefaultTooltipPositioner.INSTANCE);
        pre.setFont(replacement); pre.setX(9); pre.setY(16);
        assertSame(replacement, pre.getFont()); assertEquals(9, pre.getX()); assertEquals(16, pre.getY());
        assertSame(DefaultTooltipPositioner.INSTANCE, pre.getTooltipPositioner());
        assertEquals(123, pre.getScreenWidth()); assertEquals(77, pre.getScreenHeight());
        assertThrows(UnsupportedOperationException.class, () -> pre.getComponents().clear());
        var color = new RenderTooltipEvent.Color(ItemStack.EMPTY, null, 11, 12, replacement, 1, 2, 3, components);
        color.setBackground(4); color.setBackgroundEnd(5); color.setBorderStart(6); color.setBorderEnd(7);
        assertEquals(4, color.getBackgroundStart()); assertEquals(5, color.getBackgroundEnd());
        assertEquals(6, color.getBorderStart()); assertEquals(7, color.getBorderEnd());
        assertEquals(1, color.getOriginalBackgroundStart()); assertEquals(1, color.getOriginalBackgroundEnd());
        assertEquals(2, color.getOriginalBorderStart()); assertEquals(3, color.getOriginalBorderEnd());
        assertFalse(color.isCancelable()); assertThrows(IllegalStateException.class, () -> color.setCanceled(true));
    }

    @Test
    void renderDispatchesPreBeforeMeasuringAndHandsItsModifiedFontAndOriginToNativeRendering() {
        var bus = new EventBus();
        var first = new MeasureFont();
        var selected = new MeasureFont();
        List<String> order = new ArrayList<>();
        bus.addListener(RenderTooltipEvent.Pre.class, event -> {
            assertEquals(0, first.measures); assertEquals(0, selected.measures);
            assertEquals(200, event.getScreenWidth()); assertEquals(100, event.getScreenHeight());
            event.setFont(selected); event.setX(13); event.setY(16); order.add("pre");
        });
        FabricTooltipEvents.render(bus, ItemStack.EMPTY, null, 200, 100, 2, 3, first, List.of(new ClientImage()),
                DefaultTooltipPositioner.INSTANCE, (font, x, y, positioner) -> {
                    assertSame(selected, font); assertEquals(13, x); assertEquals(16, y);
                    assertSame(DefaultTooltipPositioner.INSTANCE, positioner);
                    font.width(Component.literal("measurement")); order.add("native");
                });
        assertEquals(List.of("pre", "native"), order);
        assertEquals(0, first.measures); assertEquals(1, selected.measures);
        var canceled = new EventBus();
        canceled.addListener(RenderTooltipEvent.Pre.class, event -> event.setCanceled(true));
        FabricTooltipEvents.render(canceled, ItemStack.EMPTY, null, 200, 100, 2, 3, first, List.of(new ClientImage()),
                DefaultTooltipPositioner.INSTANCE, (font, x, y, positioner) -> fail("canceled native/color pass executed"));
        var empty = new EventBus();
        empty.addListener(RenderTooltipEvent.Pre.class, event -> fail("Pre for empty Gather"));
        FabricTooltipEvents.render(empty, ItemStack.EMPTY, null, 200, 100, 2, 3, first, List.of(),
                DefaultTooltipPositioner.INSTANCE, (font, x, y, positioner) -> fail("empty tooltip rendered"));
    }

    @Test
    void itemExtensionFontIsResolvedBeforeGatherAndPreCanStillReplaceIt() {
        var fallback = new MeasureFont(); var custom = new MeasureFont(); var selected = new MeasureFont();
        var item = Items.CHORUS_FRUIT; var stack = new ItemStack(item);
        var resolutions = new AtomicInteger();
        FabricClientExtensions.registry().registerItem(new IClientItemExtensions() {
            @Override public Font getFont(ItemStack actual, FontContext context) {
                assertSame(stack, actual); assertSame(FontContext.TOOLTIP, context);
                resolutions.incrementAndGet(); return custom;
            }
        }, item);
        var bus = new EventBus();
        bus.addListener(RenderTooltipEvent.GatherComponents.class, event -> {
            assertEquals(1, resolutions.get()); event.setMaxWidth(12);
        });
        var gathered = FabricTooltipEvents.gatherElements(bus, stack, List.of(Either.left(Component.literal("long text"))),
                20, 120, 100, fallback);
        assertEquals(0, fallback.measures); assertEquals(List.of(12), custom.splitWidths);
        bus.addListener(RenderTooltipEvent.Pre.class, event -> {
            assertEquals(2, resolutions.get()); assertSame(custom, event.getFont()); event.setFont(selected);
        });
        FabricTooltipEvents.render(bus, stack, null, 120, 100, 20, 30, fallback, gathered,
                DefaultTooltipPositioner.INSTANCE, (font, x, y, positioner) -> assertSame(selected, font));
        assertSame(fallback, FabricClientExtensions.font(ItemStack.EMPTY, fallback, IClientItemExtensions.FontContext.TOOLTIP));
    }

    @Test
    void nullableSearchTreePlayerKeepsPlayerEventInheritanceAndTheOriginalMutableLines() {
        var stack = new ItemStack(Items.APPLE);
        List<Component> lines = new ArrayList<>(List.of(Component.literal("title")));
        var event = new ItemTooltipEvent(stack, null, lines, TooltipFlag.ADVANCED, Item.TooltipContext.EMPTY);
        assertInstanceOf(PlayerEvent.class, event);
        assertNull(event.getEntity()); assertSame(stack, event.getItemStack());
        assertSame(TooltipFlag.ADVANCED, event.getFlags()); assertSame(Item.TooltipContext.EMPTY, event.getContext());
        event.getToolTip().add(Component.literal("extra"));
        assertEquals(2, lines.size()); assertFalse(event.isCancelable());
    }

    @Test
    void nestedItemStackContextsRestoreAfterErrors() {
        var scope = new TooltipScope();
        var outer = new ItemStack(Items.APPLE);
        var nested = new ItemStack(Items.STONE);
        scope.with(outer, () -> {
            assertSame(outer, scope.stack());
            assertThrows(IllegalArgumentException.class, () -> scope.with(nested, () -> {
                assertSame(nested, scope.stack()); throw new IllegalArgumentException("component failed");
            }));
            assertSame(outer, scope.stack());
            return null;
        });
        assertSame(ItemStack.EMPTY, scope.stack());
    }

    @Test
    void componentFactoriesUseTheRealFabricCallbackAndNeoExactClassLastRegistrationRules() {
        var bus = new EventBus();
        var calls = new AtomicInteger();
        var expected = new ClientImage();
        bus.addListener(RegisterClientTooltipComponentFactoriesEvent.class, event -> {
            calls.incrementAndGet();
            event.register(Image.class, component -> { fail("replaced factory called"); return null; });
            event.register(Image.class, component -> expected);
        });
        FabricTooltipEvents.registerFactories(bus);
        FabricTooltipEvents.registerFactories(bus);
        assertEquals(1, calls.get());
        assertSame(expected, TooltipComponentCallback.EVENT.invoker().getComponent(new Image()));
        assertNull(TooltipComponentCallback.EVENT.invoker().getComponent(new UnknownImage()));
        assertThrows(IllegalStateException.class, () -> FabricTooltipEvents.registerFactories(new EventBus()));
    }

    static final class MeasureFont extends Font {
        int measures;
        final List<Integer> splitWidths = new ArrayList<>();
        MeasureFont() { super(ignored -> null, false); }
        @Override public int width(FormattedText text) { measures++; return text.getString().length() * 6; }
        @Override public List<FormattedCharSequence> split(FormattedText text, int width) {
            splitWidths.add(width);
            return List.of(Component.literal(text.getString()).getVisualOrderText());
        }
    }
    record Image() implements TooltipComponent {}
    record UnknownImage() implements TooltipComponent {}
    static final class ClientImage implements ClientTooltipComponent {
        @Override public int getHeight() { return 20; }
        @Override public int getWidth(Font font) { return 40; }
    }
}

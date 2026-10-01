package com.stardew.craft.client.tooltip;

import com.mojang.datafixers.util.Either;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.item.weapon.IStardewWeapon;
import com.stardew.craft.item.weapon.WeaponTooltipBuilder;
import com.stardew.craft.tooltip.WeaponTooltipComponent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class WeaponTooltipEvents {
    private static ItemStack previousStack = ItemStack.EMPTY;
    private static boolean previousExpanded;
    private static String previousLanguage;
    private static Screen previousScreen;
    private static long lastRendered;
    private static int scroll, maximumScroll, lastMouseX, lastMouseY;
    private WeaponTooltipEvents() {}

    @SubscribeEvent
    public static void gather(RenderTooltipEvent.GatherComponents event) {
        if (!(event.getItemStack().getItem() instanceof IStardewWeapon weapon) || weapon.getWeaponData() == null) return;
        var elements = event.getTooltipElements();
        // Leave third-party image components in their normal rendering pipeline.
        if (elements.isEmpty() || elements.stream().anyMatch(element -> element.right().isPresent())) return;
        boolean expanded = Screen.hasShiftDown();
        List<String> owned = new WeaponTooltipBuilder(event.getItemStack(), weapon.getWeaponData(), expanded)
                .build().stream().map(Component::getString).toList();
        List<String> text = elements.stream().map(element -> element.left().orElseThrow().getString()).toList();
        int start = WeaponTooltipLayout.ownedStart(text, owned);
        if (start < 0 && !owned.isEmpty()) return;
        List<Component> extras = new ArrayList<>();
        for (int index = 1; index < elements.size(); index++) {
            if (start >= 0 && index >= start && index < start + owned.size()) continue;
            extras.add(asComponent(elements.get(index).left().orElseThrow()));
        }
        Component title = asComponent(elements.get(0).left().orElseThrow());
        elements.clear();
        elements.add(Either.right(new WeaponTooltipComponent(event.getItemStack(), weapon.getWeaponData(), title,
                extras, expanded, event.getScreenWidth(), event.getScreenHeight())));
    }

    private static Component asComponent(FormattedText text) {
        return text instanceof Component component ? component.copy() : Component.literal(text.getString());
    }

    @SubscribeEvent
    public static void render(RenderTooltipEvent.Pre event) {
        if (event.getComponents().size() != 1 || !(event.getComponents().getFirst() instanceof WeaponClientTooltipComponent panel)) return;
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        var font = event.getFont();
        String language = minecraft.getLanguageManager().getSelected();
        panel.measure(font);
        var model = panel.model();
        if (!ItemStack.isSameItemSameTags(previousStack, model.stack())
                || previousExpanded != model.expanded() || !language.equals(previousLanguage)
                || minecraft.screen != previousScreen || Util.getMillis() - lastRendered > 250
                || Math.abs(event.getX() - lastMouseX) > 3 || Math.abs(event.getY() - lastMouseY) > 3) {
            scroll = 0;
        }
        previousStack = model.stack().copy();
        previousExpanded = model.expanded();
        previousLanguage = language;
        previousScreen = minecraft.screen;
        lastMouseX = event.getX(); lastMouseY = event.getY();
        lastRendered = Util.getMillis();
        maximumScroll = panel.maximumScroll();
        scroll = Math.min(scroll, maximumScroll);
        panel.setScroll(scroll);
        // Keep vanilla's positioning, background, border/color event and render pass.
        // DefaultTooltipPositioner offsets Y by -12; avoid crossing the top edge.
        if (event.getTooltipPositioner() instanceof net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner) {
            event.setY(Math.max(16, event.getY()));
        }
    }

    @SubscribeEvent
    public static void scroll(ScreenEvent.MouseScrolled.Pre event) {
        if (!Screen.hasShiftDown() || maximumScroll <= 0 || event.getScreen() != previousScreen
                // PORT(1.20.1): Forge exposes the vertical scroll as getScrollDelta().
                || Util.getMillis() - lastRendered > 250 || event.getScrollDelta() == 0
                || Math.abs(event.getMouseX() - lastMouseX) > 3 || Math.abs(event.getMouseY() - lastMouseY) > 3) return;
        scroll = Math.max(0, Math.min(maximumScroll, scroll - (int) Math.round(event.getScrollDelta() * 24)));
        event.setCanceled(true);
    }
}

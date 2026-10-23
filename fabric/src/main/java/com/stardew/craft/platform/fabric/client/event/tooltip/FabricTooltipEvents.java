package com.stardew.craft.platform.fabric.client.event.tooltip;

import com.mojang.datafixers.util.Either;
import com.stardew.craft.platform.fabric.client.event.RegisterClientTooltipComponentFactoriesEvent;
import com.stardew.craft.platform.fabric.client.event.RenderTooltipEvent;
import com.stardew.craft.platform.fabric.client.extension.FabricClientExtensions;
import com.stardew.craft.platform.fabric.client.extension.IClientItemExtensions;
import com.stardew.craft.platform.fabric.event.IEventBus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.TooltipComponentCallback;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

@Environment(EnvType.CLIENT)
public final class FabricTooltipEvents {
    private static IEventBus installedBus;
    private static IEventBus factoriesBus;
    private FabricTooltipEvents() {}
    public static synchronized void install(IEventBus bus) {
        Objects.requireNonNull(bus, "bus");
        if (installedBus == bus) return;
        if (installedBus != null) throw new IllegalStateException("Tooltip events already have an installed bus");
        installedBus = bus;
    }

    public static synchronized void registerFactories(IEventBus modBus) {
        Objects.requireNonNull(modBus, "modBus");
        if (factoriesBus == modBus) return;
        if (factoriesBus != null) throw new IllegalStateException("Tooltip factories already registered");
        Map<Class<? extends TooltipComponent>, Function<TooltipComponent, ClientTooltipComponent>> factories = new HashMap<>();
        modBus.post(new RegisterClientTooltipComponentFactoriesEvent(factories));
        var frozen = Map.copyOf(factories);
        TooltipComponentCallback.EVENT.register(component -> {
            var factory = frozen.get(component.getClass());
            return factory == null ? null : factory.apply(component);
        });
        factoriesBus = modBus;
    }

    public static void itemLines(ItemStack stack, Player player, List<Component> lines, TooltipFlag flag, Item.TooltipContext context) {
        bus().post(new ItemTooltipEvent(stack, player, lines, flag, context));
    }

    public static List<ClientTooltipComponent> gather(ItemStack stack, List<? extends FormattedText> text,
            Optional<TooltipComponent> image, int mouseX, int screenWidth, int screenHeight, Font font) {
        List<Either<FormattedText, TooltipComponent>> elements = new ArrayList<>();
        text.forEach(line -> elements.add(Either.left(line)));
        image.ifPresent(component -> elements.add(1, Either.right(component)));
        return gatherElements(bus(), stack, elements, mouseX, screenWidth, screenHeight, font);
    }

    static List<ClientTooltipComponent> gatherElements(IEventBus bus, ItemStack stack,
            List<Either<FormattedText, TooltipComponent>> elements, int mouseX, int screenWidth, int screenHeight, Font font) {
        return gatherElements(bus, stack, elements, mouseX, screenWidth, screenHeight, font, ClientTooltipComponent::create);
    }

    static List<ClientTooltipComponent> gatherElements(IEventBus bus, ItemStack stack,
            List<Either<FormattedText, TooltipComponent>> elements, int mouseX, int screenWidth, int screenHeight, Font font,
            Function<TooltipComponent, ClientTooltipComponent> imageFactory) {
        Font tooltipFont = FabricClientExtensions.font(stack, font, IClientItemExtensions.FontContext.TOOLTIP);
        var event = bus.post(new RenderTooltipEvent.GatherComponents(stack, screenWidth, screenHeight, elements, -1));
        if (event.isCanceled()) return List.of();
        int textWidth = event.getTooltipElements().stream().mapToInt(element -> element.map(tooltipFont::width, component -> 0)).max().orElse(0);
        boolean wrap = false;
        if (mouseX + 12 + textWidth + 4 > screenWidth && mouseX - 16 - textWidth < 4) {
            textWidth = mouseX > screenWidth / 2 ? mouseX - 20 : screenWidth - 16 - mouseX;
            wrap = true;
        }
        if (event.getMaxWidth() > 0 && textWidth > event.getMaxWidth()) { textWidth = event.getMaxWidth(); wrap = true; }
        List<ClientTooltipComponent> result = new ArrayList<>();
        for (var element : event.getTooltipElements()) {
            if (element.right().isPresent()) { result.add(imageFactory.apply(element.right().orElseThrow())); continue; }
            var text = element.left().orElseThrow();
            if (wrap && !(text instanceof Component component && component.getString().isEmpty()))
                tooltipFont.split(text, textWidth).forEach(line -> result.add(ClientTooltipComponent.create(line)));
            else result.add(ClientTooltipComponent.create(text instanceof Component component ? component.getVisualOrderText() : Language.getInstance().getVisualOrder(text)));
        }
        return result;
    }

    @FunctionalInterface public interface RenderBody { void render(Font font, int x, int y, ClientTooltipPositioner positioner); }

    public static void render(ItemStack stack, GuiGraphics graphics, int x, int y, Font font,
            List<ClientTooltipComponent> components, ClientTooltipPositioner positioner, RenderBody body) {
        render(bus(), stack, graphics, graphics.guiWidth(), graphics.guiHeight(), x, y, font, components, positioner, body);
    }

    static void render(IEventBus bus, ItemStack stack, GuiGraphics graphics, int width, int height, int x, int y,
            Font font, List<ClientTooltipComponent> components, ClientTooltipPositioner positioner, RenderBody body) {
        if (components.isEmpty()) return;
        var pre = bus.post(new RenderTooltipEvent.Pre(stack, graphics, x, y, width, height,
                FabricClientExtensions.font(stack, font, IClientItemExtensions.FontContext.TOOLTIP), components, positioner));
        if (!pre.isCanceled()) body.render(pre.getFont(), pre.getX(), pre.getY(), positioner);
    }

    public static RenderTooltipEvent.Color color(ItemStack stack, GuiGraphics graphics, int x, int y, Font font,
                                                 List<ClientTooltipComponent> components) {
        return bus().post(new RenderTooltipEvent.Color(stack, graphics, x, y, font, 0xF0100010, 0x505000FF, 0x5028007F, components));
    }

    private static IEventBus bus() {
        if (installedBus == null) throw new IllegalStateException("Tooltip event bus is not installed");
        return installedBus;
    }
}

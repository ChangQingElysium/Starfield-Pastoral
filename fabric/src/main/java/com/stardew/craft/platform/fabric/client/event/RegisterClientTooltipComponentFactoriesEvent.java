package com.stardew.craft.platform.fabric.client.event;

import com.stardew.craft.platform.fabric.event.ModBusEvent;
import com.stardew.craft.platform.fabric.event.Event;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

@Environment(EnvType.CLIENT)
public final class RegisterClientTooltipComponentFactoriesEvent extends Event implements ModBusEvent {
    private final Map<Class<? extends TooltipComponent>, Function<TooltipComponent, ClientTooltipComponent>> factories;
    public RegisterClientTooltipComponentFactoriesEvent(Map<Class<? extends TooltipComponent>, Function<TooltipComponent, ClientTooltipComponent>> factories) {
        this.factories = factories;
    }
    public <T extends TooltipComponent> void register(Class<T> type, Function<? super T, ? extends ClientTooltipComponent> factory) {
        factories.put(type, component -> factory.apply(type.cast(component)));
    }
}

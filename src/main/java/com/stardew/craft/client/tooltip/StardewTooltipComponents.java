package com.stardew.craft.client.tooltip;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.tooltip.MaxChargeRangeTooltipComponent;
import com.stardew.craft.tooltip.WaterAmountTooltipComponent;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;

@SuppressWarnings("removal")
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class StardewTooltipComponents {
    private StardewTooltipComponents() {
    }

    @SubscribeEvent
    public static void registerTooltipComponents(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(WaterAmountTooltipComponent.class, WaterAmountClientTooltipComponent::new);
        event.register(MaxChargeRangeTooltipComponent.class, MaxChargeRangeClientTooltipComponent::new);
    }
}

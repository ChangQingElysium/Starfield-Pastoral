package com.stardew.craft.client.tooltip;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.tooltip.BookTooltipComponent;
import com.stardew.craft.tooltip.FishingRodSlotRowTooltipComponent;
import com.stardew.craft.tooltip.FishingRodSlotsTooltipComponent;
import com.stardew.craft.tooltip.MaxChargeRangeTooltipComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;

@SuppressWarnings("removal")
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientTooltipFactoryRegistrar {
	private ClientTooltipFactoryRegistrar() {
	}

	@SubscribeEvent
	public static void onRegisterTooltipFactories(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(com.stardew.craft.tooltip.SlingshotAmmoTooltip.class, SlingshotAmmoClientTooltip::new);
        event.register(PlaceableFoodTooltip.class, component -> component);
		event.register(BookTooltipComponent.class, BookClientTooltipComponent::new);
        event.register(com.stardew.craft.tooltip.WeaponTooltipComponent.class, WeaponClientTooltipComponent::create);
		event.register(MaxChargeRangeTooltipComponent.class, MaxChargeRangeClientTooltipComponent::new);
		event.register(FishingRodSlotsTooltipComponent.class, FishingRodSlotsClientTooltipComponent::new);
		event.register(FishingRodSlotRowTooltipComponent.class, FishingRodSlotRowClientTooltipComponent::new);
	}
}

package com.stardew.craft.client;

import com.stardew.craft.network.AdvanceUtilitiesPayload;
import com.stardew.craft.network.GrowCropsPayload;
import com.stardew.craft.network.GrowTreesPayload;
import net.minecraft.client.Minecraft;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraftforge.network.PacketDistributor;

public final class DebugKeybindsTick {
	private DebugKeybindsTick() {
	}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.player == null || mc.level == null) {
			return;
		}
		// Avoid firing while typing in a screen.
		if (mc.screen != null) {
			return;
		}

		while (DebugKeybinds.GROW_DEBUG_KEY.consumeClick() || DebugKeybinds.GROW_DEBUG_KEY_F8.consumeClick()) {
			PacketDistributor.sendToServer(new GrowCropsPayload());
			PacketDistributor.sendToServer(new GrowTreesPayload());
			PacketDistributor.sendToServer(new AdvanceUtilitiesPayload());
		}
	}
}

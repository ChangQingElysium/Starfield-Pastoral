package com.stardew.craft.client.emote;

import com.stardew.craft.client.ModKeyMappings;

import net.minecraft.client.Minecraft;

@SuppressWarnings("null")
public final class EmoteWheelClient {

	private static boolean wasDown;

	private EmoteWheelClient() {
	}

	public static void onClientTick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			wasDown = false;
			if (mc.screen instanceof EmoteWheelScreen) {
				mc.setScreen(null);
			}
			return;
		}

		boolean down = isWheelKeyHeld();
		if (down && !wasDown && mc.screen == null) {
			mc.setScreen(new EmoteWheelScreen());
		}
		if (!down && wasDown && mc.screen instanceof EmoteWheelScreen screen) {
			screen.confirmAndClose();
		}

		wasDown = down;
	}

	public static boolean isWheelKeyHeld() {
		return ModKeyMappings.isDown(ModKeyMappings.EMOTE_WHEEL);
	}

	public static void render(net.minecraft.client.gui.GuiGraphics guiGraphics) {
	}
}

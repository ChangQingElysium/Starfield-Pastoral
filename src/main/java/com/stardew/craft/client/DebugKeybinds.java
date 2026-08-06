package com.stardew.craft.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.stardew.craft.StardewCraft;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

@SuppressWarnings("removal")
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class DebugKeybinds {
	private DebugKeybinds() {
	}

	public static final KeyMapping GROW_DEBUG_KEY = new KeyMapping(
			"key.stardewcraft.grow_crops",
			KeyConflictContext.IN_GAME,
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_KP_7,
			"key.categories.stardewcraft"
	);

	public static final KeyMapping GROW_DEBUG_KEY_F8 = new KeyMapping(
			"key.stardewcraft.grow_crops_f8",
			KeyConflictContext.IN_GAME,
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_F8,
			"key.categories.stardewcraft"
	);

	@SuppressWarnings("null")
	@SubscribeEvent
	public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
		if (FMLLoader.isProduction()) {
			return;
		}
		event.register(GROW_DEBUG_KEY);
		event.register(GROW_DEBUG_KEY_F8);
	}
}

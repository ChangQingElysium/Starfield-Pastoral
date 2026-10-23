package com.stardew.craft.platform.fabric.client.event;

import com.stardew.craft.platform.fabric.event.ModBusEvent;
import com.stardew.craft.platform.fabric.event.Event;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;

/** Publish during client initialization, before Options loads the saved bindings. */
@Environment(EnvType.CLIENT)
public final class RegisterKeyMappingsEvent extends Event implements ModBusEvent {
    public void register(KeyMapping key) {
        KeyBindingHelper.registerKeyBinding(key);
    }
}

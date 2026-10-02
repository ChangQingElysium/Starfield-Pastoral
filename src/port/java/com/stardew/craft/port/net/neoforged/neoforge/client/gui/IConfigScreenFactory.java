package com.stardew.craft.port.net.neoforged.neoforge.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;

/**
 * PORT(1.20.1): NeoForge config screen extension point. Forge's equivalent is
 * {@link ConfigScreenHandler.ConfigScreenFactory}; use {@link #register(IConfigScreenFactory)} (or
 * {@link #toForge()}) instead of {@code ModContainer#registerExtensionPoint}.
 */
@FunctionalInterface
public interface IConfigScreenFactory {
    Screen createScreen(Minecraft minecraft, Screen modListScreen);

    default ConfigScreenHandler.ConfigScreenFactory toForge() {
        return new ConfigScreenHandler.ConfigScreenFactory(this::createScreen);
    }

    /** Registers the factory for the mod currently being constructed. */
    static void register(IConfigScreenFactory factory) {
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class, factory::toForge);
    }
}

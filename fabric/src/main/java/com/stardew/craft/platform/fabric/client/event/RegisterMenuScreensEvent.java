package com.stardew.craft.platform.fabric.client.event;

import com.stardew.craft.platform.fabric.event.ModBusEvent;
import com.stardew.craft.platform.fabric.event.Event;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

/** MenuScreens and ScreenConstructor are exposed by Fabric's transitive access widener. */
@Environment(EnvType.CLIENT)
public final class RegisterMenuScreensEvent extends Event implements ModBusEvent {
    public <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void register(
            MenuType<? extends M> type, MenuScreens.ScreenConstructor<M, U> constructor) {
        MenuScreens.register(type, constructor);
    }
}

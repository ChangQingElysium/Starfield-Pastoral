package com.stardew.craft.port.net.neoforged.neoforge.client.event;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.event.IModBusEvent;

/**
 * PORT(1.20.1): NeoForge 21.1 menu-screen registration event (mod bus). Forge 1.20.1 registers screens through the
 * public static {@link MenuScreens#register}; the event is posted on the mod bus on the main thread during
 * {@code FMLClientSetupEvent} (see {@code PortClientEventBridges}).
 */
public class RegisterMenuScreensEvent extends Event implements IModBusEvent {
    public RegisterMenuScreensEvent() {}

    public <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void register(
            MenuType<? extends M> menuType, MenuScreens.ScreenConstructor<M, U> screenConstructor) {
        MenuScreens.register(menuType, screenConstructor);
    }
}

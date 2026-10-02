package com.stardew.craft.port.net.neoforged.neoforge.registries.datamaps;

import com.stardew.craft.port.PortDataMaps;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.event.IModBusEvent;

/** PORT(1.20.1): posted on the mod bus during common setup by {@code PortBootstrap}. */
public class RegisterDataMapTypesEvent extends Event implements IModBusEvent {
    public RegisterDataMapTypesEvent() {}

    public <T, R> void register(DataMapType<R, T> type) {
        PortDataMaps.register(type);
    }
}

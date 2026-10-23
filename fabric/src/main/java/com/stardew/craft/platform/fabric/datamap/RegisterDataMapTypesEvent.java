package com.stardew.craft.platform.fabric.datamap;

import com.stardew.craft.platform.fabric.event.ModBusEvent;
import com.stardew.craft.platform.fabric.event.Event;

/** Publish once on the mod bus before resource loading begins. */
public final class RegisterDataMapTypesEvent extends Event implements ModBusEvent {
    public <T, R> void register(DataMapType<R, T> type) { DataMaps.register(type); }
}

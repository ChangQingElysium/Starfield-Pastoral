package com.stardew.craft.platform.fabric.chunk;

import com.stardew.craft.platform.fabric.event.ModBusEvent;
import com.stardew.craft.platform.fabric.event.Event;
import java.util.Objects;
import java.util.function.Consumer;

public final class RegisterTicketControllersEvent extends Event implements ModBusEvent {
    private final Consumer<TicketController> registrar;
    private boolean open = true;
    RegisterTicketControllersEvent(Consumer<TicketController> registrar) { this.registrar = Objects.requireNonNull(registrar); }
    public void register(TicketController controller) {
        if (!open) throw new IllegalStateException("Ticket controller registration has closed");
        registrar.accept(Objects.requireNonNull(controller));
    }
    void close() { open = false; }
}

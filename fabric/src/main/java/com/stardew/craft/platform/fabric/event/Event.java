package com.stardew.craft.platform.fabric.event;

/** Base for the project's explicitly bridged events. */
public class Event {
    private final boolean cancelable;
    private boolean canceled;

    public Event() {
        this(false);
    }

    protected Event(boolean cancelable) {
        this.cancelable = cancelable;
    }

    public final boolean isCancelable() {
        return cancelable;
    }

    public final boolean isCanceled() {
        return canceled;
    }

    public final void setCanceled(boolean canceled) {
        if (!cancelable) throw new IllegalStateException("Event is not cancelable: " + getClass().getName());
        this.canceled = canceled;
        onCancellationChanged(canceled);
    }

    protected void onCancellationChanged(boolean canceled) {}
}

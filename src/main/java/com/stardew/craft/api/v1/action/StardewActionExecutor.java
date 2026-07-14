package com.stardew.craft.api.v1.action;

/** Executes one decoded action payload on the logical server. */
@FunctionalInterface
public interface StardewActionExecutor<T> {
    StardewActionResult execute(StardewActionContext context, T data);
}

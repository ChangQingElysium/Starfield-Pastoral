package com.stardew.craft.platform.fabric.client.keys;

import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class KeyBindingState {
    private IKeyConflictContext context = KeyConflictContext.UNIVERSAL;
    private KeyModifier modifier = KeyModifier.NONE;
    private KeyModifier defaultModifier = KeyModifier.NONE;
    public IKeyConflictContext context() { return context; }
    public void context(IKeyConflictContext context) { this.context = Objects.requireNonNull(context, "context"); }
    public KeyModifier modifier() { return modifier; }
    public void modifier(KeyModifier modifier) { this.modifier = Objects.requireNonNull(modifier, "modifier"); }
    public KeyModifier defaultModifier() { return defaultModifier; }
    public void defaultModifier(KeyModifier modifier) { this.defaultModifier = Objects.requireNonNull(modifier, "defaultModifier"); }
    public boolean active(KeyInputState input) { return context.isActive() && modifier.isActive(context, input); }
}

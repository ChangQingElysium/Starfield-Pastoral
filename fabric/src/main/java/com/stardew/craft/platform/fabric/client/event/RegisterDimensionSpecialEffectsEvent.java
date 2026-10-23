package com.stardew.craft.platform.fabric.client.event;

import com.stardew.craft.platform.fabric.event.ModBusEvent;
import com.stardew.craft.platform.fabric.event.Event;
import java.util.Map;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.resources.ResourceLocation;

@Environment(EnvType.CLIENT)
public final class RegisterDimensionSpecialEffectsEvent extends Event implements ModBusEvent {
    private final Map<ResourceLocation, DimensionSpecialEffects> effects;
    public RegisterDimensionSpecialEffectsEvent(Map<ResourceLocation, DimensionSpecialEffects> effects) { this.effects = Objects.requireNonNull(effects); }
    public void register(ResourceLocation id, DimensionSpecialEffects effect) { effects.put(Objects.requireNonNull(id), Objects.requireNonNull(effect)); }
}

package com.stardew.craft.api.v1.equipment;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class StardewWeaponSkillHandlers {
    private static final Map<ResourceLocation, StardewWeaponSkillHandler> HANDLERS = new LinkedHashMap<>();

    private StardewWeaponSkillHandlers() {
    }

    public static synchronized void register(ResourceLocation id, StardewWeaponSkillHandler handler) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(handler, "handler");
        if (HANDLERS.putIfAbsent(id, handler) != null) {
            throw new IllegalStateException("Weapon skill handler already registered: " + id);
        }
    }

    public static synchronized Optional<StardewWeaponSkillHandler> get(ResourceLocation id) {
        return Optional.ofNullable(HANDLERS.get(id));
    }
}

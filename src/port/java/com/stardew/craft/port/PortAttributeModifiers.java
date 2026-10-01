package com.stardew.craft.port;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * 1.21 identifies attribute modifiers by {@link ResourceLocation}; 1.20.1 uses a UUID plus a name.
 * The UUID is derived deterministically from the id, so the same 1.21 id always maps to the same
 * 1.20.1 modifier (replacing/removing by id keeps working, also across saves).
 */
public final class PortAttributeModifiers {
    private PortAttributeModifiers() {
    }

    public static UUID uuid(ResourceLocation id) {
        return UUID.nameUUIDFromBytes(id.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** 1.21 {@code new AttributeModifier(id, amount, operation)}. */
    public static AttributeModifier create(ResourceLocation id, double amount, AttributeModifier.Operation operation) {
        return new AttributeModifier(uuid(id), id.toString(), amount, operation);
    }
}

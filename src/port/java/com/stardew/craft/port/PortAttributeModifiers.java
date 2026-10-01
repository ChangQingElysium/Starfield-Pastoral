package com.stardew.craft.port;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
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

    /** 1.21 {@code MobEffect#addAttributeModifier(attribute, id, ...)}: 1.20.1 takes the UUID as a string. */
    public static String uuidString(ResourceLocation id) {
        return uuid(id).toString();
    }

    /** 1.21 {@code AttributeInstance#getModifier(ResourceLocation)}. */
    @javax.annotation.Nullable
    public static AttributeModifier getModifier(AttributeInstance instance, ResourceLocation id) {
        return instance.getModifier(uuid(id));
    }

    /** 1.21 {@code AttributeInstance#hasModifier(ResourceLocation)}. */
    public static boolean hasModifier(AttributeInstance instance, ResourceLocation id) {
        return instance.getModifier(uuid(id)) != null;
    }

    /** 1.21 {@code AttributeInstance#removeModifier(ResourceLocation)}: removes (also from the permanent set). */
    public static boolean removeModifier(AttributeInstance instance, ResourceLocation id) {
        AttributeModifier existing = instance.getModifier(uuid(id));
        if (existing == null) {
            return false;
        }
        instance.removeModifier(existing);
        return true;
    }

    /**
     * 1.21 {@code AttributeInstance#addOrUpdateTransientModifier}: replaces the modifier with the same id (no-op
     * when the very same instance is already applied). 1.20.1 only has the throwing {@code addTransientModifier},
     * so an existing modifier is removed first (one extra dirty notification, same resulting value).
     */
    public static void addOrUpdateTransientModifier(AttributeInstance instance, AttributeModifier modifier) {
        AttributeModifier existing = instance.getModifier(modifier.getId());
        if (existing == modifier) {
            return;
        }
        if (existing != null) {
            instance.removeModifier(existing);
        }
        instance.addTransientModifier(modifier);
    }
}

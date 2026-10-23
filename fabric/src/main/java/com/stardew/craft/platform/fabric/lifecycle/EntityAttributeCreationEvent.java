package com.stardew.craft.platform.fabric.lifecycle;

import com.stardew.craft.platform.fabric.event.ModBusEvent;
import com.stardew.craft.platform.fabric.event.Event;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;

/** Published after entity types are registered, before worlds can construct their living entities. */
public final class EntityAttributeCreationEvent extends Event implements ModBusEvent {
    private final Set<EntityType<?>> registered = new HashSet<>();

    public void put(EntityType<? extends LivingEntity> type, AttributeSupplier attributes) {
        java.util.Objects.requireNonNull(type, "type");
        java.util.Objects.requireNonNull(attributes, "attributes");
        if (!registered.add(type)) throw new IllegalArgumentException("Entity attributes registered twice: " + type);
        FabricDefaultAttributeRegistry.register(type, attributes);
    }
}

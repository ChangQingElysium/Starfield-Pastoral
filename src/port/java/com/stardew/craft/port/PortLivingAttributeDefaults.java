package com.stardew.craft.port;

import com.stardew.craft.StardewCraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * PORT(1.20.1): 1.21.1's {@code LivingEntity#createLivingAttributes} also adds ATTACK_KNOCKBACK
 * (base 0), so every StardewCraft living entity built from it has that attribute; 1.20.1's does not,
 * and reading a missing attribute throws. Restore the 1.21.1 default set for the mod's entity types
 * only. (The other 1.21-only defaults have no 1.20.1 attribute and are covered by PortAttributes.)
 */
public final class PortLivingAttributeDefaults {
    private PortLivingAttributeDefaults() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(EventPriority.LOWEST, PortLivingAttributeDefaults::onModify);
    }

    @SuppressWarnings("unchecked")
    private static void onModify(EntityAttributeModificationEvent event) {
        for (EntityType<? extends LivingEntity> type : event.getTypes()) {
            if (!StardewCraft.MODID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace())) {
                continue;
            }
            if (!event.has(type, Attributes.ATTACK_KNOCKBACK)) {
                event.add(type, Attributes.ATTACK_KNOCKBACK);
            }
        }
    }
}

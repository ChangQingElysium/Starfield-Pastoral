package com.stardew.craft.mixin;

import com.stardew.craft.StardewCraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mixin(EntityType.class)
public abstract class ManagedAnimalEntityLoadMixin {
    private static final String MANAGED_ID_TAG = "stardewManagedAnimalId";
    private static final String MANAGED_TYPE_TAG = "stardewManagedAnimalType";
    private static final int MAX_STARDEW_ANIMALS_PER_ENTITY_CHUNK = 128;

    @ModifyVariable(method = "loadEntitiesRecursive", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static List<? extends Tag> stardewcraft$sanitizeManagedAnimalEntities(List<? extends Tag> tags) {
        if (tags.size() <= 1) {
            return tags;
        }

        Set<Long> managedIds = new HashSet<>();
        ListTag sanitized = new ListTag();
        int keptStardewAnimals = 0;
        int removed = 0;

        for (Tag tag : tags) {
            if (!(tag instanceof CompoundTag entityTag) || !isStardewAnimal(entityTag)) {
                sanitized.add(tag);
                continue;
            }

            long managedId = entityTag.getLong(MANAGED_ID_TAG);
            boolean duplicate = managedId > 0L && !managedIds.add(managedId);
            if (duplicate || keptStardewAnimals >= MAX_STARDEW_ANIMALS_PER_ENTITY_CHUNK) {
                removed++;
                continue;
            }

            sanitized.add(tag);
            keptStardewAnimals++;
        }

        if (removed == 0) {
            return tags;
        }

        StardewCraft.LOGGER.warn(
            "[ANIMAL_RECOVERY] Removed {} duplicate/excess animal entities before loading (kept {}, original entities {})",
            removed, keptStardewAnimals, tags.size());
        return sanitized;
    }

    private static boolean isStardewAnimal(CompoundTag tag) {
        return tag.getString("id").startsWith(StardewCraft.MODID + ":")
            && tag.contains(MANAGED_ID_TAG, Tag.TAG_LONG)
            && tag.contains(MANAGED_TYPE_TAG, Tag.TAG_STRING);
    }
}

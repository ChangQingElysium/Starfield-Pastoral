package com.stardew.craft.animal.service;

import com.stardew.craft.entity.animal.BaseCoopAnimalEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Index of currently loaded managed animals, scoped to a server level.
 */
public final class ManagedAnimalRuntimeIndex {
    private static final Map<ServerLevel, Map<Long, UUID>> BY_LEVEL =
        Collections.synchronizedMap(new WeakHashMap<>());

    private ManagedAnimalRuntimeIndex() {
    }

    public static BaseCoopAnimalEntity register(ServerLevel level, BaseCoopAnimalEntity animal) {
        long managedId = animal.getManagedAnimalId();
        if (managedId <= 0L) {
            return null;
        }

        synchronized (BY_LEVEL) {
            Map<Long, UUID> index = BY_LEVEL.computeIfAbsent(level, ignored -> new HashMap<>());
            UUID existingUuid = index.get(managedId);
            if (existingUuid != null && !existingUuid.equals(animal.getUUID())) {
                Entity existing = level.getEntity(existingUuid);
                if (existing instanceof BaseCoopAnimalEntity existingAnimal
                    && !existingAnimal.isRemoved()
                    && existingAnimal.getManagedAnimalId() == managedId) {
                    return existingAnimal;
                }
            }

            index.put(managedId, animal.getUUID());
            return null;
        }
    }

    public static BaseCoopAnimalEntity find(ServerLevel level, long managedId) {
        if (managedId <= 0L) {
            return null;
        }

        synchronized (BY_LEVEL) {
            Map<Long, UUID> index = BY_LEVEL.get(level);
            if (index == null) {
                return null;
            }
            UUID uuid = index.get(managedId);
            Entity entity = uuid == null ? null : level.getEntity(uuid);
            if (entity instanceof BaseCoopAnimalEntity animal
                && !animal.isRemoved()
                && animal.getManagedAnimalId() == managedId) {
                return animal;
            }
            index.remove(managedId);
            return null;
        }
    }

    public static List<BaseCoopAnimalEntity> snapshot(ServerLevel level) {
        synchronized (BY_LEVEL) {
            Map<Long, UUID> index = BY_LEVEL.get(level);
            if (index == null || index.isEmpty()) {
                return List.of();
            }

            List<BaseCoopAnimalEntity> animals = new ArrayList<>(index.size());
            Iterator<Map.Entry<Long, UUID>> iterator = index.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Long, UUID> entry = iterator.next();
                Entity entity = level.getEntity(entry.getValue());
                if (entity instanceof BaseCoopAnimalEntity animal
                    && !animal.isRemoved()
                    && animal.getManagedAnimalId() == entry.getKey()) {
                    animals.add(animal);
                } else {
                    iterator.remove();
                }
            }
            return List.copyOf(animals);
        }
    }

    public static void remove(ServerLevel level, BaseCoopAnimalEntity animal) {
        long managedId = animal.getManagedAnimalId();
        synchronized (BY_LEVEL) {
            Map<Long, UUID> index = BY_LEVEL.get(level);
            if (index != null) {
                index.remove(managedId, animal.getUUID());
            }
        }
    }

    public static void clear(ServerLevel level) {
        BY_LEVEL.remove(level);
    }
}

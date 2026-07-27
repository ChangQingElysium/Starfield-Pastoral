package com.stardew.craft.animal.model;

import net.minecraft.nbt.CompoundTag;

import java.util.Objects;

/** Durable replacement for Stardew Valley's overnight {@code QuestionEvent(2)} naming step. */
public record AnimalPendingBirth(
        long eventId,
        String ownerPlayerUuid,
        String buildingId,
        long parentAnimalId,
        String animalTypeId,
        int createdAbsDay
) {
    public AnimalPendingBirth {
        if (eventId <= 0L) {
            throw new IllegalArgumentException("eventId must be positive");
        }
        ownerPlayerUuid = requireText(ownerPlayerUuid, "ownerPlayerUuid");
        buildingId = requireText(buildingId, "buildingId");
        if (parentAnimalId <= 0L) {
            throw new IllegalArgumentException("parentAnimalId must be positive");
        }
        animalTypeId = requireText(animalTypeId, "animalTypeId");
        createdAbsDay = Math.max(0, createdAbsDay);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("eventId", eventId);
        tag.putString("ownerPlayerUuid", ownerPlayerUuid);
        tag.putString("buildingId", buildingId);
        tag.putLong("parentAnimalId", parentAnimalId);
        tag.putString("animalTypeId", animalTypeId);
        tag.putInt("createdAbsDay", createdAbsDay);
        return tag;
    }

    public static AnimalPendingBirth load(CompoundTag tag) {
        return new AnimalPendingBirth(
                tag.getLong("eventId"),
                tag.getString("ownerPlayerUuid"),
                tag.getString("buildingId"),
                tag.getLong("parentAnimalId"),
                tag.getString("animalTypeId"),
                tag.getInt("createdAbsDay")
        );
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}

package com.stardew.craft.animal.model;

public enum AnimalAcquisitionSource {
    PURCHASE,
    PREGNANCY,
    INCUBATION;

    public static AnimalAcquisitionSource fromId(String id) {
        for (AnimalAcquisitionSource value : values()) {
            if (value.name().equalsIgnoreCase(id)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown acquisition source: " + id);
    }
}

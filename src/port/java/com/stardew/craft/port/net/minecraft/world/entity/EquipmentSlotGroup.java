package com.stardew.craft.port.net.minecraft.world.entity;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EquipmentSlot;

/** 1.21 slot group used by item attribute modifiers. 1.20.1 has no BODY slot, so BODY matches nothing. */
public enum EquipmentSlotGroup implements StringRepresentable {
    ANY(0, "any", slot -> true),
    MAINHAND(1, "mainhand", EquipmentSlot.MAINHAND),
    OFFHAND(2, "offhand", EquipmentSlot.OFFHAND),
    HAND(3, "hand", slot -> slot.getType() == EquipmentSlot.Type.HAND),
    FEET(4, "feet", EquipmentSlot.FEET),
    LEGS(5, "legs", EquipmentSlot.LEGS),
    CHEST(6, "chest", EquipmentSlot.CHEST),
    HEAD(7, "head", EquipmentSlot.HEAD),
    ARMOR(8, "armor", slot -> slot.getType() == EquipmentSlot.Type.ARMOR),
    BODY(9, "body", slot -> false);

    private final int id;
    private final String key;
    private final Predicate<EquipmentSlot> predicate;

    EquipmentSlotGroup(int id, String key, Predicate<EquipmentSlot> predicate) {
        this.id = id;
        this.key = key;
        this.predicate = predicate;
    }

    EquipmentSlotGroup(int id, String key, EquipmentSlot slot) {
        this(id, key, candidate -> candidate == slot);
    }

    public static EquipmentSlotGroup bySlot(EquipmentSlot slot) {
        return switch (slot) {
            case MAINHAND -> MAINHAND;
            case OFFHAND -> OFFHAND;
            case FEET -> FEET;
            case LEGS -> LEGS;
            case CHEST -> CHEST;
            case HEAD -> HEAD;
        };
    }

    public boolean test(EquipmentSlot slot) {
        return predicate.test(slot);
    }

    /** PORT(1.20.1): concrete slots of this group, used to write per-slot 1.20.1 attribute modifier NBT. */
    public List<EquipmentSlot> portSlots() {
        return java.util.Arrays.stream(EquipmentSlot.values()).filter(this::test).toList();
    }

    public int id() {
        return id;
    }

    @Override
    public String getSerializedName() {
        return key;
    }
}

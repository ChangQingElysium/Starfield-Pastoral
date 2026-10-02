package com.stardew.craft.port.net.minecraft.world.item.component;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.FireworkRocketItem;

/** 1.21 {@code minecraft:fireworks}; stored as the vanilla 1.20.1 {@code Fireworks{Flight, Explosions}} compound. */
public record Fireworks(int flightDuration, List<FireworkExplosion> explosions) {
    public static final int MAX_EXPLOSIONS = 256;

    public Fireworks {
        if (explosions.size() > MAX_EXPLOSIONS) {
            throw new IllegalArgumentException("Got " + explosions.size() + " explosions, but maximum is " + MAX_EXPLOSIONS);
        }
        explosions = List.copyOf(explosions);
    }

    /** PORT(1.20.1): vanilla Fireworks compound. */
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putByte(FireworkRocketItem.TAG_FLIGHT, (byte) flightDuration);
        ListTag list = new ListTag();
        for (FireworkExplosion explosion : explosions) {
            list.add(explosion.toTag());
        }
        if (!list.isEmpty()) {
            tag.put(FireworkRocketItem.TAG_EXPLOSIONS, list);
        }
        return tag;
    }

    /** PORT(1.20.1): inverse of {@link #toTag()}. */
    public static Fireworks fromTag(CompoundTag tag) {
        ListTag list = tag.getList(FireworkRocketItem.TAG_EXPLOSIONS, Tag.TAG_COMPOUND);
        List<FireworkExplosion> explosions = new ArrayList<>(list.size());
        for (int i = 0; i < list.size() && i < MAX_EXPLOSIONS; i++) {
            explosions.add(FireworkExplosion.fromTag(list.getCompound(i)));
        }
        return new Fireworks(tag.getByte(FireworkRocketItem.TAG_FLIGHT), explosions);
    }
}

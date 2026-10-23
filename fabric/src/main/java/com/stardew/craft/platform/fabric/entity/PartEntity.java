package com.stardew.craft.platform.fabric.entity;

import net.minecraft.world.entity.Entity;

/** Parent identity for the existing multipart-target queries. */
public interface PartEntity<T extends Entity> {
    T getParent();
}

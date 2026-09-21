package com.stardew.craft.blockentity;

import net.minecraft.world.item.Item;

public record MissingItemRequirement(Item item, int requiredCount) {}

package com.stardew.craft.block.decor;

import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/** Painted doors use vanilla placement, hinges, collision and interaction rules. */
public final class PaintedDoorBlock extends DoorBlock {
    public PaintedDoorBlock(Properties properties) { super(properties, BlockSetType.OAK); } // PORT(1.20.1): 1.20.1 constructor takes Properties first
}

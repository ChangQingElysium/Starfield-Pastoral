package com.stardew.craft.blockentity;

import com.stardew.craft.forge.registry.ForgeBlockEntities;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Compatibility aliases for block-entity registrations on the Forge line. */
public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = ForgeBlockEntities.BLOCK_ENTITIES;
    public static final RegistryObject<BlockEntityType<NewTreePartBlockEntity>> NEW_TREE_PART =
            ForgeBlockEntities.NEW_TREE_PART;
    public static final RegistryObject<BlockEntityType<DecorBlockEntity>> DECOR_BLOCK =
            ForgeBlockEntities.DECOR_BLOCK;

    private ModBlockEntities() {
    }
}

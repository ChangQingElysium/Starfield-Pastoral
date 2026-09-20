package com.stardew.craft.forge.registry;

import com.stardew.craft.blockentity.NewTreePartBlockEntity;
import com.stardew.craft.forge.ForgeBootstrap;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Forge 1.20.1 block-entity registrations for the tree-generation slice. */
public final class ForgeBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ForgeBootstrap.MOD_ID);

    /**
     * The valid-block set deliberately mirrors the NeoForge registration:
     * every generated root, log and branch block for all five tree species.
     * ForgeBlocks supplies these entries in the tree-core registration slice.
     */
    public static final RegistryObject<BlockEntityType<NewTreePartBlockEntity>> NEW_TREE_PART =
            BLOCK_ENTITIES.register("new_tree_part", () -> BlockEntityType.Builder.of(
                    NewTreePartBlockEntity::new,
                    ForgeBlocks.OAK_ROOT.get(), ForgeBlocks.OAK_LOG.get(), ForgeBlocks.OAK_BRANCH.get(),
                    ForgeBlocks.MAPLE_ROOT.get(), ForgeBlocks.MAPLE_LOG.get(), ForgeBlocks.MAPLE_BRANCH.get(),
                    ForgeBlocks.PINE_ROOT.get(), ForgeBlocks.PINE_LOG.get(), ForgeBlocks.PINE_BRANCH.get(),
                    ForgeBlocks.MAHOGANY_ROOT.get(), ForgeBlocks.MAHOGANY_LOG.get(), ForgeBlocks.MAHOGANY_BRANCH.get(),
                    ForgeBlocks.MYSTIC_TREE_ROOT.get(), ForgeBlocks.MYSTIC_TREE_LOG.get(), ForgeBlocks.MYSTIC_TREE_BRANCH.get()
            ).build(null));

    private ForgeBlockEntities() {
    }
}

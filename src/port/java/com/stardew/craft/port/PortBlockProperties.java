package com.stardew.craft.port;

import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;

/**
 * 1.20.5+ {@code BlockBehaviour.Properties.ofFullCopy}. 1.20.1 {@code Properties.copy} is the 1.21
 * {@code ofLegacyCopy}; the full copy additionally carries jump factor, the redstone-conductor / valid-spawn /
 * post-process / suffocating / view-blocking predicates and the explicit loot table. Those fields are package-private
 * in 1.20.1, so they are read and written through their SRG names.
 */
public final class PortBlockProperties {
    private static final Field PROPERTIES = field(BlockBehaviour.class, "f_60439_");
    private static final Field[] FULL_COPY_FIELDS = {
            field(BlockBehaviour.Properties.class, "f_60893_"), // jumpFactor
            field(BlockBehaviour.Properties.class, "f_60898_"), // isRedstoneConductor
            field(BlockBehaviour.Properties.class, "f_60897_"), // isValidSpawn
            field(BlockBehaviour.Properties.class, "f_60901_"), // hasPostProcess
            field(BlockBehaviour.Properties.class, "f_60899_"), // isSuffocating
            field(BlockBehaviour.Properties.class, "f_60900_"), // isViewBlocking
            field(BlockBehaviour.Properties.class, "f_60894_"), // drops
    };

    private PortBlockProperties() {}

    public static BlockBehaviour.Properties ofFullCopy(BlockBehaviour block) {
        BlockBehaviour.Properties copy = BlockBehaviour.Properties.copy(block);
        try {
            BlockBehaviour.Properties source = (BlockBehaviour.Properties) PROPERTIES.get(block);
            for (Field f : FULL_COPY_FIELDS) {
                f.set(copy, f.get(source));
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot copy block properties of " + block, e);
        }
        return copy;
    }

    /** 1.20.5+ {@code Properties.ofLegacyCopy}: identical to 1.20.1 {@code Properties.copy}. */
    public static BlockBehaviour.Properties ofLegacyCopy(BlockBehaviour block) {
        return BlockBehaviour.Properties.copy(block);
    }

    private static Field field(Class<?> owner, String srgName) {
        return ObfuscationReflectionHelper.findField(owner, srgName);
    }
}

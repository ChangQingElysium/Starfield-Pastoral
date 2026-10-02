package com.stardew.craft.port.net.neoforged.neoforge.capabilities;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import org.jetbrains.annotations.Nullable;

/**
 * PORT(1.20.1): NeoForge block capability. Each instance is bound to the Forge {@link Capability} that block
 * entities expose to other mods (see {@code com.stardew.craft.port.PortCapabilities}).
 */
public final class BlockCapability<T, C> {
    private final ResourceLocation name;
    private final Class<T> typeClass;
    private final Class<C> contextClass;
    private final Capability<T> forgeCapability;

    BlockCapability(ResourceLocation name, Class<T> typeClass, Class<C> contextClass, Capability<T> forgeCapability) {
        this.name = Objects.requireNonNull(name);
        this.typeClass = typeClass;
        this.contextClass = contextClass;
        this.forgeCapability = forgeCapability;
    }

    public ResourceLocation name() {
        return name;
    }

    public Class<T> typeClass() {
        return typeClass;
    }

    public Class<C> contextClass() {
        return contextClass;
    }

    public Capability<T> forgeCapability() {
        return forgeCapability;
    }

    @Nullable
    public T getCapability(Level level, BlockPos pos, @Nullable BlockState state, @Nullable BlockEntity blockEntity, C context) {
        return com.stardew.craft.port.PortCapabilities.getCapability(this, level, pos, state, blockEntity, context);
    }

    @Override
    public String toString() {
        return "BlockCapability[" + name + "]";
    }
}

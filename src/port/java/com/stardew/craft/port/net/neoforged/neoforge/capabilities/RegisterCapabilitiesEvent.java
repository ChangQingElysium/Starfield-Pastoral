package com.stardew.craft.port.net.neoforged.neoforge.capabilities;

import com.stardew.craft.port.PortCapabilities;
import java.util.Objects;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.event.IModBusEvent;

/**
 * PORT(1.20.1): NeoForge's capability registration event, posted on the mod bus during common setup by
 * {@code PortBootstrap}. Registrations are resolved in order, like NeoForge: first non-null provider wins.
 */
public class RegisterCapabilitiesEvent extends Event implements IModBusEvent {
    public RegisterCapabilitiesEvent() {}

    public <T, C> void registerBlock(BlockCapability<T, C> capability, IBlockCapabilityProvider<T, C> provider, Block... blocks) {
        Objects.requireNonNull(provider);
        if (blocks.length == 0) throw new IllegalArgumentException("Must register at least one block");
        PortCapabilities.registerBlock(capability, provider, blocks);
    }

    public <T, C, BE extends BlockEntity> void registerBlockEntity(BlockCapability<T, C> capability, BlockEntityType<BE> blockEntityType,
            ICapabilityProvider<? super BE, C, T> provider) {
        Objects.requireNonNull(provider);
        PortCapabilities.registerBlockEntity(capability, blockEntityType, provider);
    }

    public boolean isBlockRegistered(BlockCapability<?, ?> capability, Block block) {
        return PortCapabilities.isBlockRegistered(capability, block);
    }
}

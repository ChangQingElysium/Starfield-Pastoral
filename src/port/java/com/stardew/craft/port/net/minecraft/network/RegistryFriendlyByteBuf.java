package com.stardew.craft.port.net.minecraft.network;

import com.stardew.craft.port.PortCodecs;
import com.stardew.craft.port.net.neoforged.neoforge.network.connection.ConnectionType;
import io.netty.buffer.ByteBuf;
import java.util.function.Function;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

/** 1.21.1 {@code RegistryFriendlyByteBuf} (with NeoForge's connection type) on Forge 1.20.1. */
public class RegistryFriendlyByteBuf extends FriendlyByteBuf {
    private final RegistryAccess registryAccess;
    private final ConnectionType connectionType;

    public RegistryFriendlyByteBuf(ByteBuf source, RegistryAccess registryAccess, ConnectionType connectionType) {
        super(source);
        this.registryAccess = registryAccess;
        this.connectionType = connectionType;
    }

    @Deprecated
    public RegistryFriendlyByteBuf(ByteBuf source, RegistryAccess registryAccess) {
        this(source, registryAccess, ConnectionType.OTHER);
    }

    public RegistryAccess registryAccess() {
        return this.registryAccess;
    }

    public ConnectionType getConnectionType() {
        return this.connectionType;
    }

    /** 1.21.1 FriendlyByteBuf API missing from 1.20.1. */
    public void writeVec3(Vec3 value) {
        PortCodecs.writeVec3(this, value);
    }

    public Vec3 readVec3() {
        return PortCodecs.readVec3(this);
    }

    public static Function<ByteBuf, RegistryFriendlyByteBuf> decorator(RegistryAccess registryAccess, ConnectionType connectionType) {
        return buffer -> new RegistryFriendlyByteBuf(buffer, registryAccess, connectionType);
    }
}

package com.stardew.craft.platform.fabric.network;

import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.ConfigurationTask;

/** A real Fabric configuration task, adapted to vanilla by the registration event. */
public interface ICustomConfigurationTask {
    void run(Consumer<CustomPacketPayload> sender);

    Type type();

    /** Vanilla accepts a string; the ResourceLocation constructor was a NeoForge extension. */
    record Type(String id) {
        public Type {
            Objects.requireNonNull(id, "Configuration task id");
            ResourceLocation.parse(id);
        }

        public Type(ResourceLocation id) {
            this(id.toString());
        }

        public ConfigurationTask.Type vanillaType() {
            return new ConfigurationTask.Type(id);
        }
    }
}

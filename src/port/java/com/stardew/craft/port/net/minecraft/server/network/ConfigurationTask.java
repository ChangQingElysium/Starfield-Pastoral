package com.stardew.craft.port.net.minecraft.server.network;

import net.minecraft.resources.ResourceLocation;

/**
 * 1.21.1 {@code ConfigurationTask}. Forge 1.20.1 has no configuration phase; Stardew tasks run
 * during the Forge login handshake instead (see {@code PortLoginTasks}).
 */
public interface ConfigurationTask {
    Type type();

    record Type(String id) {
        /** NeoForge convenience constructor. */
        public Type(ResourceLocation id) {
            this(id.toString());
        }

        @Override
        public String toString() {
            return this.id;
        }
    }
}

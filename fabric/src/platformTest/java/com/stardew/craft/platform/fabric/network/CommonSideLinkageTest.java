package com.stardew.craft.platform.fabric.network;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommonSideLinkageTest {
    @Test
    void commonNetworkTypesLoadAndExposeTheirSignaturesWithAllClientTypesDenied() throws Exception {
        URL location = FabricNetwork.class.getProtectionDomain().getCodeSource().getLocation();
        String root = "com.stardew.craft.platform.fabric.network.";
        try (URLClassLoader serverOnly = new URLClassLoader(new URL[] {location}, getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("net.minecraft.client.")
                        || name.startsWith("net.fabricmc.fabric.api.client.")
                        || name.startsWith(root + "client.")) {
                    throw new ClassNotFoundException("Dedicated-server test forbids " + name);
                }
                if (name.startsWith(root)) {
                    synchronized (getClassLoadingLock(name)) {
                        Class<?> type = findLoadedClass(name);
                        if (type == null) type = findClass(name);
                        if (resolve) resolveClass(type);
                        return type;
                    }
                }
                return super.loadClass(name, resolve);
            }
        }) {
            for (String name : List.of("FabricNetwork", "FabricPayloadContext", "IPayloadContext",
                    "IPayloadHandler", "PacketDistributor", "PayloadRegistrar", "RegisterPayloadHandlersEvent",
                    "RegisterConfigurationTasksEvent", "ICustomConfigurationTask", "ProtocolManifest",
                    "FragmentPayload", "FragmentAssembler", "FabricFragmentation")) {
                Class<?> type = Class.forName(root + name, true, serverOnly);
                assertSame(serverOnly, type.getClassLoader());
                assertNotNull(type.getDeclaredMethods());
                assertNotNull(type.getDeclaredConstructors());
            }
        }
    }
}

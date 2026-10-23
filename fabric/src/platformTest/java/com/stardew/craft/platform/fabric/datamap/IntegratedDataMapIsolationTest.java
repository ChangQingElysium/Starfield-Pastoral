package com.stardew.craft.platform.fabric.datamap;

import static com.stardew.craft.platform.fabric.datamap.DataMapTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import com.mojang.serialization.Codec;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;

class IntegratedDataMapIsolationTest {
    @Test
    void differentAccessContainersReallyShareTheStaticMinecraftRegistryAndHolderOwner() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var serverAccess = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var clientAccess = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        assertNotSame(serverAccess, clientAccess);
        assertSame(serverAccess.registryOrThrow(Registries.ITEM), clientAccess.registryOrThrow(Registries.ITEM));
        assertSame(serverAccess.registryOrThrow(Registries.ITEM).holderOwner(), clientAccess.registryOrThrow(Registries.ITEM).holderOwner());
    }

    @Test
    void sharedOwnerClientApplyAndDisconnectCannotEraseAuthorityOrUnsyncedData() {
        var sharedRegistry = registry();
        var serverAccess = access(sharedRegistry);
        var clientAccess = access(sharedRegistry);
        assertSame(serverAccess.registryOrThrow(REGISTRY).holderOwner(), clientAccess.registryOrThrow(REGISTRY).holderOwner());
        var fullCodec = Codec.unboundedMap(Codec.STRING, Codec.INT);
        Codec<Map<String, Integer>> publicCodec = Codec.INT.xmap(value -> Map.of("public", value), value -> value.get("public"));
        var synced = DataMapType.builder(ResourceLocation.parse("fixture:synced_view"), REGISTRY, fullCodec).synced(publicCodec, false).build();
        var serverOnly = DataMapType.builder(ResourceLocation.parse("fixture:server_only"), REGISTRY, Codec.INT).build();
        Map<String, Integer> authority = Map.of("public", 12, "private", 99);
        DataMaps.publishServer(Map.of(sharedRegistry.holderOwner(), Map.of(synced, Map.of(A, authority), serverOnly, Map.of(B, 777))));
        byte[] wire = DataMapSnapshot.encode(serverAccess, List.of(synced, serverOnly));
        DataMaps.publishClient(DataMapSnapshot.decode(wire, clientAccess, List.of(synced, serverOnly)));
        // Holder-only lookups prefer authority on both logical sides when an
        // integrated server shares its owner. Distinct per-thread codec views
        // are not promised by the current seven-map API contract.
        assertEquals(authority, DataMaps.getDataMap(sharedRegistry, synced).get(A));
        assertEquals(777, DataMaps.getDataMap(sharedRegistry, serverOnly).get(B));
        assertArrayEquals(wire, DataMapSnapshot.encode(serverAccess, List.of(synced, serverOnly)));
        DataMaps.clearClient(sharedRegistry.holderOwner());
        assertEquals(authority, DataMaps.getDataMap(sharedRegistry, synced).get(A));
        assertEquals(777, DataMaps.getDataMap(sharedRegistry, serverOnly).get(B));

        DataMaps.publishClient(DataMapSnapshot.decode(wire, clientAccess, List.of(synced, serverOnly)));
        DataMaps.clearServer(sharedRegistry.holderOwner());
        assertEquals(Map.of("public", 12), DataMaps.getDataMap(sharedRegistry, synced).get(A));
        assertTrue(DataMaps.getDataMap(sharedRegistry, serverOnly).isEmpty());
        // A server configuration encoder must not fall back to a previous
        // remote client's values after authority has been released.
        assertTrue(new String(DataMapSnapshot.encode(serverAccess, List.of(synced)), StandardCharsets.UTF_8).contains("\"values\":{}"));
        DataMaps.clearClient(sharedRegistry.holderOwner());
        assertTrue(DataMaps.getDataMap(sharedRegistry, synced).isEmpty());
    }
}

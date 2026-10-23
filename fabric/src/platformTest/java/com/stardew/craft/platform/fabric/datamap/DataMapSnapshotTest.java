package com.stardew.craft.platform.fabric.datamap;

import static com.stardew.craft.platform.fabric.datamap.DataMapTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import com.mojang.serialization.Codec;
import com.stardew.craft.platform.mixin.datamap.mixin.HolderOwnerAccessor;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class DataMapSnapshotTest {
    @Test
    void networkCodecRoundTripPublishesActualValuesAndKeepsSameKeyRegistriesIsolated() {
        var server = registry();
        var client = registry();
        var type = DataMapType.builder(ResourceLocation.parse("fixture:snapshot"), REGISTRY, Codec.INT).synced(Codec.INT, false).build();
        DataMaps.publishServer(Map.of(server.holderOwner(), Map.of(type, Map.of(A, 12))));
        var bytes = DataMapSnapshot.encode(access(server), List.of(type));
        var decoded = DataMapSnapshot.decode(bytes, access(client), List.of(type));
        assertTrue(DataMaps.getDataMap(client, type).isEmpty());
        DataMaps.publishClient(decoded);
        var holder = new FixtureHolder(client.holderOwner(), A);
        assertEquals(12, DataMaps.get(holder, type));
        DataMaps.publishClient(Map.of(client.holderOwner(), Map.of(type, Map.of(A, 99))));
        assertEquals(12, DataMaps.getDataMap(server, type).get(A));
        assertEquals(99, DataMaps.get(holder, type));
        assertNull(DataMaps.get(Holder.direct("unregistered"), type));
        assertThrows(IllegalStateException.class, () -> DataMaps.get(client.getHolder(A).orElseThrow(), type));
    }

    @Test
    void unsupportedOptionalMapsAreSkippedMandatoryMapsFailAndAbsentMapsClearOldValues() {
        var registry = registry();
        var type = DataMapType.builder(ResourceLocation.parse("fixture:known"), REGISTRY, Codec.INT).synced(Codec.INT, false).build();
        DataMaps.publishClient(Map.of(registry.holderOwner(), Map.of(type, Map.of(A, 5))));
        var optional = snapshot("[{\"registry\":\"fixture:objects\",\"id\":\"fixture:unknown\",\"mandatory\":false,\"values\":{}}]");
        DataMaps.publishClient(DataMapSnapshot.decode(optional, access(registry), List.of(type)));
        assertTrue(DataMaps.getDataMap(registry, type).isEmpty());
        var mandatory = snapshot("[{\"registry\":\"fixture:objects\",\"id\":\"fixture:unknown\",\"mandatory\":true,\"values\":{}}]");
        assertThrows(IllegalArgumentException.class, () -> DataMapSnapshot.decode(mandatory, access(registry), List.of(type)));
        var unknownValue = snapshot("[{\"registry\":\"fixture:objects\",\"id\":\"fixture:known\",\"mandatory\":false,\"values\":{\"fixture:missing\":1}}]");
        assertThrows(IllegalArgumentException.class, () -> DataMapSnapshot.decode(unknownValue, access(registry), List.of(type)));
    }

    private static byte[] snapshot(String maps) { return ("{\"version\":1,\"maps\":" + maps + "}").getBytes(StandardCharsets.UTF_8); }

    private static final class FixtureHolder extends Holder.Reference<String> implements HolderOwnerAccessor {
        private final HolderOwner<?> fixtureOwner;
        FixtureHolder(HolderOwner<String> owner, ResourceKey<String> key) {
            super(Type.STAND_ALONE, owner, key, null);
            fixtureOwner = owner;
        }
        @Override public HolderOwner<?> stardewcraft$dataMapOwner() { return fixtureOwner; }
    }
}

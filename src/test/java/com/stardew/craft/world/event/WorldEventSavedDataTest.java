package com.stardew.craft.world.event;

import com.stardew.craft.api.v1.world.StardewWorldEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldEventSavedDataTest {
    private static final HolderLookup.Provider REGISTRIES =
            VanillaRegistries.createLookup();

    @Test
    void unknownAddonEventRoundTripsWithExactInverseAndState() {
        UUID instanceId = UUID.randomUUID();
        ResourceLocation eventType =
                ResourceLocation.fromNamespaceAndPath(
                        "absent_addon", "orchard_blossom");
        ResourceKey<Level> dimension = Level.OVERWORLD;
        CompoundTag ownerState = new CompoundTag();
        ownerState.putString("Season", "spring");
        WorldEventSavedData original =
                new WorldEventSavedData();
        original.put(new WorldEventSavedData.ActiveEvent(
                instanceId,
                eventType,
                dimension,
                new BlockPos(4, 70, 9),
                List.of(new StardewWorldEvents.BlockChange(
                        new BlockPos(4, 70, 9),
                        Blocks.GRASS_BLOCK.defaultBlockState(),
                        Blocks.PINK_WOOL.defaultBlockState())),
                ownerState,
                1_234L,
                false));

        CompoundTag saved = original.save(
                new CompoundTag(), REGISTRIES);
        WorldEventSavedData restored =
                WorldEventSavedData.load(saved, REGISTRIES);
        WorldEventSavedData.ActiveEvent event =
                restored.find(instanceId).orElseThrow();

        assertEquals(eventType, event.eventType());
        assertEquals(dimension, event.dimension());
        assertEquals(Blocks.GRASS_BLOCK.defaultBlockState(),
                event.changes().getFirst().expected());
        assertEquals(Blocks.PINK_WOOL.defaultBlockState(),
                event.changes().getFirst().replacement());
        assertEquals("spring",
                event.persistentData().getString("Season"));
        assertTrue(restored.snapshot().stream()
                .anyMatch(entry -> entry.instanceId()
                        .equals(instanceId)));
    }
}

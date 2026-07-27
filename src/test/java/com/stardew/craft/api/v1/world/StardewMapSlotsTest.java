package com.stardew.craft.api.v1.world;

import com.stardew.craft.api.v1.farm.StardewFarmLayoutAttachment;
import com.stardew.craft.api.v1.farm.StardewFarmLayoutConfiguration;
import com.stardew.craft.api.v1.farm.StardewFarmSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StardewMapSlotsTest {
    @Test
    void farmAttachmentProjectsToScopedResolvedWorldSlot() {
        UUID owner = UUID.randomUUID();
        ResourceLocation layout = id("orchard_layout");
        ResourceLocation role = id("festival");
        StardewFarmLayoutAttachment attachment =
                new StardewFarmLayoutAttachment(
                        id("stage"),
                        new BlockPos(7, 3, 11),
                        180.0F,
                        Set.of(role));
        StardewFarmSnapshot farm = new StardewFarmSnapshot(
                owner,
                "Owner",
                "Orchard",
                2,
                new BlockPos(100, 20, -50),
                layout,
                1,
                StardewFarmLayoutConfiguration.empty(),
                List.of(attachment),
                true,
                0L,
                1,
                0,
                0,
                "",
                false,
                false,
                List.of());

        StardewMapSlot slot = StardewMapSlots.fromFarmAttachment(
                farm, attachment);

        assertEquals(id("stage"), slot.id());
        assertEquals(StardewMapSlotScopes.FARM, slot.scopeType());
        assertEquals(owner.toString(), slot.scopeId());
        assertEquals(layout, slot.containerId());
        assertEquals(new BlockPos(107, 23, -39),
                slot.blockPosition());
        assertEquals(180.0F, slot.yaw());
        assertTrue(slot.hasRole(role));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                "map_slot_test", path);
    }
}

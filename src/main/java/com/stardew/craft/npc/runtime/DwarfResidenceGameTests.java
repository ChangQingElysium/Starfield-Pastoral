package com.stardew.craft.npc.runtime;

import com.stardew.craft.mining.OrdinaryMineLayout;
import com.stardew.craft.mining.OrdinaryMineRuntime;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("stardewcraft_dwarf_residence")
@PrefixGameTestTemplate(false)
public final class DwarfResidenceGameTests {
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void dwarfUsesSourceTileAndRepositionsExistingResident(GameTestHelper h) {
        // GameTestServer owns only its fixture dimension; exercise the residency core there.
        var level = h.getLevel();
        OrdinaryMineRuntime.ensure(level, 0);
        var layout = OrdinaryMineLayout.load(level, 0);
        var tile = layout.metadata.getAsJsonArray("dwarf_spawn_reserved");
        h.assertTrue(tile.get(0).getAsInt() == 43 && tile.get(1).getAsInt() == 6,
                "Dwarf must occupy the original Mine tile 43,6");
        Vec3 home = Vec3.atLowerCornerOf(layout.position(0, 43, 6)).add(.5, 0, .5);
        for (int i = 0; i < 12; i++) NpcSpawnManager.tickMiningResidents(level);
        var dwarf = NpcSpawnManager.getTrackedNpc(level, "dwarf");
        h.assertTrue(dwarf != null, "Dwarf did not spawn in the approved lobby");
        h.assertTrue(dwarf.position().distanceTo(home) < .26, "New Dwarf uses obsolete coordinates");
        h.assertTrue(level.noCollision(dwarf, dwarf.getBoundingBox().deflate(.001)), "Dwarf overlaps the shop architecture");
        var uuid = dwarf.getUUID();
        dwarf.moveTo(22, 66, -16, 180, 0);
        dwarf.setDeltaMovement(1, 0, 1);
        NpcSpawnManager.tickMiningResidents(level);
        h.assertTrue(dwarf.position().distanceTo(home) < .26, "Existing Dwarf was left in the old mine");
        h.assertTrue(dwarf.getUUID().equals(uuid) && NpcSpawnManager.getTrackedNpc(level, "dwarf") == dwarf,
                "Relocation replaced the resident instead of moving it");
        h.assertTrue(Math.abs(dwarf.getYRot()) < .01, "Original Dwarf faces south");
        h.assertTrue(dwarf.getDeltaMovement().lengthSqr() < 1e-8, "Relocated Dwarf retained old movement");
        h.succeed();
    }
}

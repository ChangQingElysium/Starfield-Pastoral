package com.stardew.craft.gametest;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.interior.InteriorSubspaceManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.Map;

@GameTestHolder("stardewcraft_npc_runtime")
@PrefixGameTestTemplate(false)
public final class PortalDeferredPlacementGameTests {
    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities")
    public static void portalPlacementPreservesVanillaForceload(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(3,2,3));
        int cx=pos.getX()>>4,cz=pos.getZ()>>4;long key=net.minecraft.world.level.ChunkPos.asLong(cx,cz);
        boolean wasForced=level.getForcedChunks().contains(key);var old=level.getBlockState(pos);
        level.setChunkForced(cx,cz,true);
        try {
            InteriorSubspaceManager.placePortalTriggerArea(level,pos,1,1,1,"repair_forced","repair_target");
            h.assertTrue(level.getForcedChunks().contains(key),"Portal placement removed a pre-existing vanilla forceload");
        } finally {level.setBlockAndUpdate(pos,old);if(!wasForced)level.setChunkForced(cx,cz,false);}
        h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities",timeoutTicks=100)
    public static void unloadedPortalRetriesOnLevelTicksWithoutTaskRecursion(GameTestHelper h) throws ReflectiveOperationException {
        var level=h.getLevel();
        var pos=h.absolutePos(new BlockPos(1024,2,1024));
        int cx=pos.getX()>>4,cz=pos.getZ()>>4;
        h.assertTrue(level.getChunkSource().getChunkNow(cx,cz)==null,"Fixture requires an initially unloaded chunk");
        var pendingField=InteriorSubspaceManager.class.getDeclaredField("PENDING_PORTALS");pendingField.setAccessible(true);
        InteriorSubspaceManager.placePortalTriggerArea(level,pos,1,1,1,"audit_deferred","audit_target");
        var pending=(Map<?,?>)((Map<?,?>)pendingField.get(null)).get(level);
        h.assertTrue(pending!=null && pending.size()==1,"Unloaded placement must enter the level-tick queue");
        for(int i=0;i<2;i++) InteriorSubspaceManager.placePortalTriggerArea(level,pos,1,1,1,"audit_deferred","audit_target");
        h.assertTrue(pending.size()<=1,"Repeated requests must not multiply pending placements");
        h.onEachTick(()->{
            // Called by the real LevelTickEvent; also safe if test harness ordering differs.
            InteriorSubspaceManager.tickPendingPortals(level);
            if(level.getChunkSource().getChunkNow(cx,cz)==null) return;
            if(!level.getBlockState(pos).is(ModBlocks.PORTAL_TRIGGER.get())) return;
            h.assertTrue(pending.isEmpty(),"Successful placement remains queued");
            level.removeBlock(pos,false);
            level.setChunkForced(cx,cz,false);
            h.succeed();
        });
    }
}

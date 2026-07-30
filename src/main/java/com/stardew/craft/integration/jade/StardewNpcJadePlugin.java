package com.stardew.craft.integration.jade;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.casino.CasinoAccessService;
import com.stardew.craft.client.ClientPlayerDataCache;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.network.payload.ClientNpcVisibilityState;
import net.minecraft.world.phys.EntityHitResult;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin(StardewCraft.MODID)
public class StardewNpcJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerEntityDataProvider(StardewNpcJadeProvider.INSTANCE, StardewNpcEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerEntityComponent(StardewNpcJadeProvider.INSTANCE, StardewNpcEntity.class);
        // Jade performs its own entity ray trace and deliberately inflates very
        // small hit boxes, so collapsing a locally hidden NPC AABB cannot suppress
        // Jade's built-in entity header. Remove the accessor only for a client
        // whose per-player story state hides this shared NPC.
        registration.addRayTraceCallback(Integer.MAX_VALUE, (hit, accessor, originalAccessor) -> {
            if (hit instanceof EntityHitResult entityHit
                    && entityHit.getEntity() instanceof StardewNpcEntity npc
                    && isNpcHiddenForLocalPlayer(npc)) {
                return null;
            }
            if (accessor instanceof EntityAccessor entityAccessor
                    && entityAccessor.getEntity() instanceof StardewNpcEntity npc
                    && isNpcHiddenForLocalPlayer(npc)) {
                return null;
            }
            return accessor;
        });
    }

    private static boolean isNpcHiddenForLocalPlayer(StardewNpcEntity npc) {
        String npcId = npc.getNpcId();
        return npcId != null && (ClientNpcVisibilityState.isHidden(npcId)
                || (CasinoAccessService.BOUNCER_NPC_ID.equals(npcId)
                && ClientPlayerDataCache.hasMailFlag(CasinoAccessService.BOUNCER_GONE_FLAG))
                || ("henchman".equals(npcId)
                && ClientPlayerDataCache.hasMailFlag("henchmanGone")));
    }
}

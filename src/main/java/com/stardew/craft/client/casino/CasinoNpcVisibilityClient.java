package com.stardew.craft.client.casino;

import com.stardew.craft.casino.CasinoAccessService;
import com.stardew.craft.client.ClientPlayerDataCache;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Removes the local hit/collision box together with the per-player Bouncer render.
 * The server entity stays intact for other players whose story flag isn't set.
 */
public final class CasinoNpcVisibilityClient {
    private static final Set<UUID> LOCALLY_COLLAPSED = new HashSet<>();

    private CasinoNpcVisibilityClient() {
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            LOCALLY_COLLAPSED.clear();
            return;
        }
        boolean hideBouncer = ClientPlayerDataCache.hasMailFlag(
                CasinoAccessService.BOUNCER_GONE_FLAG);
        for (StardewNpcEntity npc : minecraft.level.getEntitiesOfClass(
                StardewNpcEntity.class,
                minecraft.player.getBoundingBox().inflate(64.0D))) {
            if (!CasinoAccessService.BOUNCER_NPC_ID.equals(npc.getNpcId())) {
                continue;
            }
            if (hideBouncer) {
                npc.setBoundingBox(new AABB(
                        npc.getX(), npc.getY(), npc.getZ(),
                        npc.getX(), npc.getY(), npc.getZ()));
                LOCALLY_COLLAPSED.add(npc.getUUID());
            } else if (LOCALLY_COLLAPSED.remove(npc.getUUID())) {
                npc.refreshDimensions();
            }
        }
        // Entity picking uses isPickable(), not canBeCollidedWith(). A per-player
        // story state can't safely change that server-side on the shared NPC, so
        // replace a stale local Bouncer hit with the block ray behind him.
        if (hideBouncer
                && minecraft.hitResult instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof StardewNpcEntity npc
                && CasinoAccessService.BOUNCER_NPC_ID.equals(npc.getNpcId())) {
            minecraft.hitResult = minecraft.player.pick(
                    minecraft.player.blockInteractionRange(), 1.0F, false);
        }
    }
}

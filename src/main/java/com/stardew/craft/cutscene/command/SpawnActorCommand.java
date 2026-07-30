package com.stardew.craft.cutscene.command;

import com.stardew.craft.cutscene.runtime.EventActorEntity;
import com.stardew.craft.cutscene.runtime.EventPlayerActorEntity;
import com.stardew.craft.cutscene.runtime.EventPlayer;
import com.stardew.craft.client.renderer.entity.EventPlayerActorRenderer;
import com.stardew.craft.entity.ModEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Mob;

/**
 * spawn_actor: creates a client-only actor entity at the given position.
 * JSON: { "cmd": "spawn_actor", "actor": "alice", "npc_id": "abigail", "x": 0, "y": 0, "z": 0, "relative": true }
 */
public class SpawnActorCommand implements EventCommand {

    private final String actorTag;
    private final String npcId;
    private final double x, y, z;
    private final boolean relative;
    private final float facing; // yaw
    private final String anchor;

    public SpawnActorCommand(String actorTag, String npcId, double x, double y, double z, boolean relative, float facing) {
        this(actorTag, npcId, x, y, z, relative, facing, null);
    }

    public SpawnActorCommand(String actorTag, String npcId, double x, double y, double z,
                              boolean relative, float facing, String anchor) {
        this.actorTag = actorTag;
        this.npcId = npcId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.relative = relative;
        this.facing = facing;
        this.anchor = anchor;
    }

    @SuppressWarnings("null")
    @Override
    public void start(EventPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;

        String resolvedNpcId = "$winter_star_giver".equals(npcId)
            ? com.stardew.craft.client.festival.WinterStarCutsceneContext.giverId()
            : com.stardew.craft.cutscene.runtime.CombatRescueCutsceneContext.resolveNpcId(npcId);

        // Choose entity type based on npc_id
        Mob actor;
        if ("player".equals(resolvedNpcId)) {
            EventPlayerActorEntity playerActor = new EventPlayerActorEntity(ModEntities.EVENT_PLAYER_ACTOR.get(), level);
            playerActor.setSkinSourcePlayerId(mc.player.getUUID());
            playerActor.setSlimSkinModel(EventPlayerActorRenderer.isSlimSkin(mc.player.getUUID()));
            // Copy equipment from local player
            for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                playerActor.setItemSlot(slot, mc.player.getItemBySlot(slot).copy());
            }
            actor = playerActor;
        } else {
            EventActorEntity npcActor = new EventActorEntity(ModEntities.EVENT_ACTOR.get(), level);
            npcActor.setNpcId(resolvedNpcId);
            actor = npcActor;
        }

        double px, py, pz;
        if (relative) {
            px = mc.player.getX() + x;
            py = mc.player.getY() + y;
            pz = mc.player.getZ() + z;
        } else {
            px = x + com.stardew.craft.cutscene.runtime.CutsceneAnchorRegistry.offsetX(anchor);
            py = y + com.stardew.craft.cutscene.runtime.CutsceneAnchorRegistry.offsetY(anchor);
            pz = z + com.stardew.craft.cutscene.runtime.CutsceneAnchorRegistry.offsetZ(anchor);
        }
        actor.setPos(px, py, pz);
        actor.setYRot(facing);
        actor.setYHeadRot(facing);
        actor.setYBodyRot(facing);

        // Use a unique negative entity ID to avoid conflicts
        int fakeId = -(actorTag.hashCode() & 0x7FFFFFFF) - 1;
        actor.setId(fakeId);

        level.addEntity(actor);
        player.registerActor(actorTag, actor);
    }

    @Override
    public void tick(EventPlayer player) {}

    @Override
    public boolean isComplete() { return true; }
}

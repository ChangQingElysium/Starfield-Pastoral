package com.stardew.craft.cutscene.command;

import com.stardew.craft.cutscene.runtime.EventPlayer;
import com.stardew.craft.cutscene.runtime.EventActorEntity;
import com.stardew.craft.cutscene.runtime.EventPlayerActorEntity;
import com.stardew.craft.client.renderer.entity.EventPlayerActorRenderer;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.festival.client.EggFestivalCutsceneClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EggFestivalPlayersCommand implements EventCommand {
    private static final String ACTION_SPAWN_LINEUP = "spawn_lineup";
    private static final String ACTION_AWARD_WALK = "award_walk";
    private static final String ACTION_NPC_WIN_REACT = "npc_win_react";
    private static final String ACTION_NPC_WIN_READY_PRIZE = "npc_win_ready_prize";
    private static final String ACTION_NPC_WIN_RETURN = "npc_win_return";
    private static final int NPC_CONTESTANT_COUNT = 5;
    private static final float NORTH_YAW = 180.0F;
    private static final float SOUTH_YAW = 0.0F;
    private static final float WEST_YAW = 90.0F;
    private static final Vec3 AWARD_POS = new Vec3(9.5D, 64.0D, 4.5D);

    private final String action;
    private final int totalTicks;
    private final List<MovingActor> movingActors = new ArrayList<>();
    private int ticksElapsed;
    private boolean done;

    public EggFestivalPlayersCommand(String action, int ticks) {
        this.action = action == null || action.isBlank() ? ACTION_SPAWN_LINEUP : action;
        this.totalTicks = Math.max(1, ticks);
    }

    @Override
    public void start(EventPlayer player) {
        movingActors.clear();
        ticksElapsed = 0;
        done = false;
        if (ACTION_AWARD_WALK.equals(action)) {
            startAwardWalk(player);
            return;
        }
        if (ACTION_NPC_WIN_REACT.equals(action)) {
            reactToNpcWin(player);
            done = true;
            return;
        }
        if (ACTION_NPC_WIN_READY_PRIZE.equals(action)) {
            readyNpcPrize(player);
            done = true;
            return;
        }
        if (ACTION_NPC_WIN_RETURN.equals(action)) {
            startNpcWinReturn(player);
            return;
        }
        spawnLineupActors(player);
        done = true;
    }

    @Override
    public void tick(EventPlayer player) {
        if (done) {
            return;
        }
        ticksElapsed++;
        double progress = Math.min(1.0D, (double) ticksElapsed / totalTicks);
        for (MovingActor moving : movingActors) {
            double x = moving.startX() + (moving.endX() - moving.startX()) * progress;
            double z = moving.startZ() + (moving.endZ() - moving.startZ()) * progress;
            moving.actor().setPos(x, moving.endY(), z);
            float yaw = progress >= 1.0D ? moving.finalYaw() : moving.walkYaw();
            moving.actor().setYRot(yaw);
            moving.actor().setYHeadRot(yaw);
            moving.actor().setYBodyRot(yaw);
            if (moving.actor() instanceof EventPlayerActorEntity playerActor) {
                playerActor.setWalking(progress < 1.0D);
            } else if (moving.actor() instanceof EventActorEntity npcActor) {
                npcActor.setWalking(progress < 1.0D);
            }
        }
        if (ticksElapsed >= totalTicks) {
            done = true;
        }
    }

    @Override
    public boolean isComplete() {
        return done;
    }

    private void spawnLineupActors(EventPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        var localPlayer = mc.player;
        if (level == null || localPlayer == null) {
            return;
        }
        int count = Math.min(EggFestivalCutsceneClientState.participantCount(), 24);
        for (int index = 0; index < count; index++) {
            EventPlayerActorEntity actor = new EventPlayerActorEntity(ModEntities.EVENT_PLAYER_ACTOR.get(), level);
            UUID participantId = EggFestivalCutsceneClientState.participantId(index);
            AbstractClientPlayer sourcePlayer = findClientPlayer(level, participantId, localPlayer);
            actor.setSkinSourcePlayerId(participantId == null ? sourcePlayer.getUUID() : participantId);
            actor.setSlimSkinModel(EventPlayerActorRenderer.isSlimSkin(actor.getSkinSourcePlayerId()));
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                actor.setItemSlot(slot, sourcePlayer.getItemBySlot(slot).copy());
            }
            Vec3 position = lineupPosition(NPC_CONTESTANT_COUNT + index);
            actor.setPos(position.x, position.y, position.z);
            actor.setYRot(NORTH_YAW);
            actor.setYHeadRot(NORTH_YAW);
            actor.setYBodyRot(NORTH_YAW);
            actor.setId(-((actorTag(index).hashCode() & 0x7FFFFFFF) + 2000));
            level.putNonPlayerEntity(actor.getId(), actor); // PORT(1.20.1): 1.21 ClientLevel#addEntity(entity)
            player.registerActor(actorTag(index), actor);
        }
    }

    private void startAwardWalk(EventPlayer player) {
        if (EggFestivalCutsceneClientState.playerWon()) {
            for (int index = 0; index < Math.min(EggFestivalCutsceneClientState.participantCount(), 24); index++) {
                if (EggFestivalCutsceneClientState.isWinnerIndex(index)) {
                    Mob actor = player.getActor(actorTag(index));
                    if (actor != null) {
                        movingActors.add(new MovingActor(actor, actor.getX(), actor.getZ(), AWARD_POS.x, AWARD_POS.y, AWARD_POS.z, walkYaw(actor.getX(), actor.getZ()), SOUTH_YAW));
                    }
                }
            }
        } else {
            Mob abigail = player.getActor("abigail");
            if (abigail != null) {
                movingActors.add(new MovingActor(abigail, abigail.getX(), abigail.getZ(), AWARD_POS.x, AWARD_POS.y, AWARD_POS.z, walkYaw(abigail.getX(), abigail.getZ()), SOUTH_YAW));
            }
        }
        for (MovingActor moving : movingActors) {
            moving.actor().setYRot(moving.walkYaw());
            moving.actor().setYHeadRot(moving.walkYaw());
            moving.actor().setYBodyRot(moving.walkYaw());
            if (moving.actor() instanceof EventPlayerActorEntity playerActor) {
                playerActor.setWalking(true);
            } else if (moving.actor() instanceof EventActorEntity npcActor) {
                npcActor.setWalking(true);
            }
        }
        done = movingActors.isEmpty();
    }

    private void reactToNpcWin(EventPlayer player) {
        if (EggFestivalCutsceneClientState.playerWon()) {
            return;
        }
        Mob abigail = player.getActor("abigail");
        if (abigail != null) {
            Vec3 motion = abigail.getDeltaMovement();
            abigail.setDeltaMovement(motion.x, 0.5D, motion.z);
            abigail.hasImpulse = true;
        }
        face(player.getActor("vincent"), WEST_YAW);
    }

    private void readyNpcPrize(EventPlayer player) {
        if (!EggFestivalCutsceneClientState.playerWon()) {
            face(player.getActor("vincent"), NORTH_YAW);
        }
    }

    private void startNpcWinReturn(EventPlayer player) {
        if (EggFestivalCutsceneClientState.playerWon()) {
            done = true;
            return;
        }
        Mob abigail = player.getActor("abigail");
        if (abigail == null) {
            done = true;
            return;
        }
        Vec3 target = lineupPosition(0);
        movingActors.add(new MovingActor(abigail, abigail.getX(), abigail.getZ(), target.x, target.y, target.z, walkYawTo(abigail.getX(), abigail.getZ(), target.x, target.z), NORTH_YAW));
        face(abigail, movingActors.get(0).walkYaw());
        if (abigail instanceof EventActorEntity npcActor) {
            npcActor.setWalking(true);
        }
        done = false;
    }

    private static Vec3 lineupPosition(int index) {
        int column = Math.floorMod(index, 6);
        int row = Math.floorDiv(index, 6);
        return new Vec3(1.5D + column * 2.0D, 64.0D, 7.5D + row * 2.0D);
    }

    private static float walkYaw(double startX, double startZ) {
        return walkYawTo(startX, startZ, AWARD_POS.x, AWARD_POS.z);
    }

    private static float walkYawTo(double startX, double startZ, double endX, double endZ) {
        double dirX = endX - startX;
        double dirZ = endZ - startZ;
        if (dirX == 0.0D && dirZ == 0.0D) {
            return SOUTH_YAW;
        }
        return (float) (Mth.atan2(dirZ, dirX) * Mth.RAD_TO_DEG) - 90.0F;
    }

    private static AbstractClientPlayer findClientPlayer(ClientLevel level, UUID playerId, AbstractClientPlayer fallback) {
        if (playerId == null) {
            return fallback;
        }
        for (AbstractClientPlayer player : level.players()) {
            if (player.getUUID().equals(playerId)) {
                return player;
            }
        }
        return fallback;
    }

    private static void face(Mob actor, float yaw) {
        if (actor == null) {
            return;
        }
        actor.setYRot(yaw);
        actor.setYHeadRot(yaw);
        actor.setYBodyRot(yaw);
    }

    private static String actorTag(int index) {
        return "festival_player_" + index;
    }

    private record MovingActor(Mob actor, double startX, double startZ, double endX, double endY, double endZ, float walkYaw, float finalYaw) {
    }
}
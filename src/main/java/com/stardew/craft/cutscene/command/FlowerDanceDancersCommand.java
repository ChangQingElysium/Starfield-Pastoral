package com.stardew.craft.cutscene.command;

import com.stardew.craft.client.renderer.entity.EventPlayerActorRenderer;
import com.stardew.craft.cutscene.runtime.EventActorEntity;
import com.stardew.craft.cutscene.runtime.EventPlayer;
import com.stardew.craft.cutscene.runtime.EventPlayerActorEntity;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.festival.client.FlowerDanceCutsceneClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class FlowerDanceDancersCommand implements EventCommand {
    private static final String ACTION_SPAWN = "spawn";
    private static final String ACTION_DANCE = "dance";
    private static final float SOUTH_YAW = 0.0F;
    private static final float NORTH_YAW = 180.0F;
    private static final float EAST_YAW = -90.0F;
    private static final float WEST_YAW = 90.0F;
    private static final double Y = 60.0D;
    private static final double[] PAIR_X = {-240.5D, -238.5D, -236.5D, -234.5D, -232.5D, -230.5D};
    private static final double[][] PAIR_Z = {{114.5D, 116.5D}, {119.5D, 121.5D}, {124.5D, 126.5D}};
    private static final Vec3[] SPECTATOR_SPOTS = {
        new Vec3(-238.5D, Y, 132.5D), new Vec3(-236.5D, Y, 132.5D), new Vec3(-234.5D, Y, 132.5D),
        new Vec3(-232.5D, Y, 132.5D), new Vec3(-230.5D, Y, 132.5D), new Vec3(-238.5D, Y, 134.5D),
        new Vec3(-236.5D, Y, 134.5D), new Vec3(-234.5D, Y, 134.5D), new Vec3(-232.5D, Y, 134.5D),
        new Vec3(-230.5D, Y, 134.5D)
    };
    private static final String[] EXTRA_AUDIENCE_NPCS = {
        "wizard", "vincent", "demetrius", "robin", "jodi"
    };
    private static final AudienceActor[] AUDIENCE = {
        new AudienceActor("lewis", -234.5D, Y, 109.5D, SOUTH_YAW),
        new AudienceActor("marnie", -233.5D, Y, 109.5D, SOUTH_YAW),
        new AudienceActor("marlon", -227.5D, Y, 109.5D, SOUTH_YAW),
        new AudienceActor("jas", -230.5D, Y, 110.5D, SOUTH_YAW),
        new AudienceActor("pierre", -232.5D, Y, 110.5D, SOUTH_YAW),
        new AudienceActor("caroline", -233.5D, Y, 110.5D, SOUTH_YAW),
        new AudienceActor("clint", -238.5D, Y, 108.5D, SOUTH_YAW),
        new AudienceActor("linus", -246.5D, Y, 111.5D, EAST_YAW),
        new AudienceActor("evelyn", -244.5D, Y, 116.5D, EAST_YAW),
        new AudienceActor("george", -245.5D, Y, 117.5D, EAST_YAW),
        new AudienceActor("willy", -226.5D, Y, 114.5D, WEST_YAW),
        new AudienceActor("gus", -226.5D, Y, 117.5D, WEST_YAW),
        new AudienceActor("pam", -224.5D, Y, 123.5D, WEST_YAW),
        new AudienceActor("penny", -226.5D, Y, 125.5D, WEST_YAW)
    };

    private final String action;
    private final int totalTicks;
    private final List<DanceActor> danceActors = new ArrayList<>();
    private int ticksElapsed;
    private boolean done;

    public FlowerDanceDancersCommand(String action, int ticks) {
        this.action = action == null || action.isBlank() ? ACTION_SPAWN : action;
        this.totalTicks = Math.max(1, ticks);
    }

    @Override
    public void start(EventPlayer player) {
        danceActors.clear();
        ticksElapsed = 0;
        done = false;
        if (ACTION_DANCE.equals(action)) {
            collectDanceActors(player);
            return;
        }
        spawnScene(player);
        done = true;
    }

    @Override
    public void tick(EventPlayer player) {
        if (done) {
            return;
        }
        ticksElapsed++;
        double beat = Math.sin((ticksElapsed / 12.0D) * Math.PI);
        double side = Math.sin((ticksElapsed / 24.0D) * Math.PI) * 0.18D;
        double bob = Math.max(0.0D, beat) * 0.08D;
        boolean walking = ticksElapsed % 24 < 18;
        for (DanceActor dancer : danceActors) {
            double signedSide = dancer.maleSide() ? -side : side;
            dancer.actor().setPos(dancer.base().x + signedSide, dancer.base().y + bob, dancer.base().z);
            float yaw = dancer.maleSide() ? NORTH_YAW : SOUTH_YAW;
            if (ticksElapsed % 96 >= 72) {
                yaw = dancer.maleSide() ? WEST_YAW : EAST_YAW;
            }
            setYaw(dancer.actor(), yaw);
            setWalking(dancer.actor(), walking);
        }
        if (ticksElapsed >= totalTicks) {
            for (DanceActor dancer : danceActors) {
                dancer.actor().setPos(dancer.base());
                setYaw(dancer.actor(), dancer.maleSide() ? NORTH_YAW : SOUTH_YAW);
                setWalking(dancer.actor(), false);
            }
            done = true;
        }
    }

    @Override
    public boolean isComplete() {
        return done;
    }

    private void spawnScene(EventPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            return;
        }
        Set<String> occupiedNpcIds = new LinkedHashSet<>();
        int pairCount = Math.min(FlowerDanceCutsceneClientState.pairs().size(), 18);
        for (int index = 0; index < pairCount; index++) {
            FlowerDanceCutsceneClientState.DancePair pair = FlowerDanceCutsceneClientState.pairs().get(index);
            addOccupiedNpc(occupiedNpcIds, pair.femaleSide());
            addOccupiedNpc(occupiedNpcIds, pair.maleSide());
            int row = index / 6;
            int column = index % 6;
            Vec3 femalePosition = new Vec3(PAIR_X[column], Y, PAIR_Z[row][0]);
            Vec3 malePosition = new Vec3(PAIR_X[column], Y, PAIR_Z[row][1]);
            spawnPartner(player, level, pair.femaleSide(), tag(index, false), femalePosition, SOUTH_YAW);
            spawnPartner(player, level, pair.maleSide(), tag(index, true), malePosition, NORTH_YAW);
        }
        List<FlowerDanceCutsceneClientState.Partner> spectators = FlowerDanceCutsceneClientState.spectators();
        int spectatorCount = Math.min(spectators.size(), SPECTATOR_SPOTS.length);
        for (int index = 0; index < spectatorCount; index++) {
            addOccupiedNpc(occupiedNpcIds, spectators.get(index));
            spawnPartner(player, level, spectators.get(index), "flower_dance_spectator_" + index, SPECTATOR_SPOTS[index], NORTH_YAW);
        }
        int nextSpectatorSpot = spectatorCount;
        for (String npcId : EXTRA_AUDIENCE_NPCS) {
            if (nextSpectatorSpot >= SPECTATOR_SPOTS.length) {
                break;
            }
            String canonicalNpcId = canonical(npcId);
            if (occupiedNpcIds.contains(canonicalNpcId)) {
                continue;
            }
            occupiedNpcIds.add(canonicalNpcId);
            spawnNpc(player, level, npcId, "flower_dance_extra_audience_" + npcId, SPECTATOR_SPOTS[nextSpectatorSpot], NORTH_YAW);
            nextSpectatorSpot++;
        }
        for (AudienceActor audience : AUDIENCE) {
            if (occupiedNpcIds.contains(canonical(audience.npcId()))) {
                continue;
            }
            spawnNpc(player, level, audience.npcId(), "flower_dance_audience_" + audience.npcId(), audience.position(), audience.yaw());
        }
    }

    private static void addOccupiedNpc(Set<String> occupiedNpcIds, FlowerDanceCutsceneClientState.Partner partner) {
        if (partner != null && partner.kind() == FlowerDanceCutsceneClientState.Partner.Kind.NPC) {
            String npcId = canonical(partner.npcId());
            if (!npcId.isBlank()) {
                occupiedNpcIds.add(npcId);
            }
        }
    }

    private static String canonical(String npcId) {
        return npcId == null ? "" : npcId.trim().toLowerCase(Locale.ROOT);
    }

    private void collectDanceActors(EventPlayer player) {
        int pairCount = Math.min(FlowerDanceCutsceneClientState.pairs().size(), 18);
        for (int index = 0; index < pairCount; index++) {
            Mob female = player.getActor(tag(index, false));
            Mob male = player.getActor(tag(index, true));
            int row = index / 6;
            int column = index % 6;
            if (female != null) {
                danceActors.add(new DanceActor(female, new Vec3(PAIR_X[column], Y, PAIR_Z[row][0]), false));
            }
            if (male != null) {
                danceActors.add(new DanceActor(male, new Vec3(PAIR_X[column], Y, PAIR_Z[row][1]), true));
            }
        }
    }

    private void spawnPartner(EventPlayer player, ClientLevel level, FlowerDanceCutsceneClientState.Partner partner,
                              String actorTag, Vec3 position, float yaw) {
        if (partner == null) {
            return;
        }
        if (partner.kind() == FlowerDanceCutsceneClientState.Partner.Kind.PLAYER) {
            spawnPlayer(player, level, partner.playerId(), actorTag, position, yaw);
        } else {
            spawnNpc(player, level, partner.npcId(), actorTag, position, yaw);
        }
    }

    private void spawnNpc(EventPlayer player, ClientLevel level, String npcId, String actorTag, Vec3 position, float yaw) {
        if (npcId == null || npcId.isBlank()) {
            return;
        }
        EventActorEntity actor = new EventActorEntity(ModEntities.EVENT_ACTOR.get(), level);
        actor.setNpcId(npcId);
        actor.setPos(position);
        setYaw(actor, yaw);
        actor.setId(-((actorTag.hashCode() & 0x7FFFFFFF) + 3000));
        level.putNonPlayerEntity(actor.getId(), actor); // PORT(1.20.1): 1.21 ClientLevel#addEntity(entity)
        player.registerActor(actorTag, actor);
    }

    private void spawnPlayer(EventPlayer player, ClientLevel level, UUID playerId, String actorTag, Vec3 position, float yaw) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        AbstractClientPlayer sourcePlayer = findClientPlayer(level, playerId, mc.player);
        EventPlayerActorEntity actor = new EventPlayerActorEntity(ModEntities.EVENT_PLAYER_ACTOR.get(), level);
        actor.setSkinSourcePlayerId(playerId == null ? sourcePlayer.getUUID() : playerId);
        actor.setSlimSkinModel(EventPlayerActorRenderer.isSlimSkin(actor.getSkinSourcePlayerId()));
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            actor.setItemSlot(slot, sourcePlayer.getItemBySlot(slot).copy());
        }
        actor.setPos(position);
        setYaw(actor, yaw);
        actor.setId(-((actorTag.hashCode() & 0x7FFFFFFF) + 4000));
        level.putNonPlayerEntity(actor.getId(), actor); // PORT(1.20.1): 1.21 ClientLevel#addEntity(entity)
        player.registerActor(actorTag, actor);
    }

    private static AbstractClientPlayer findClientPlayer(ClientLevel level, UUID playerId, AbstractClientPlayer fallback) {
        if (playerId == null) {
            return fallback;
        }
        for (AbstractClientPlayer candidate : level.players()) {
            if (playerId.equals(candidate.getUUID())) {
                return candidate;
            }
        }
        return fallback;
    }

    private static void setYaw(Mob actor, float yaw) {
        actor.setYRot(yaw);
        actor.setYHeadRot(yaw);
        actor.setYBodyRot(yaw);
    }

    private static void setWalking(Mob actor, boolean walking) {
        if (actor instanceof EventPlayerActorEntity playerActor) {
            playerActor.setWalking(walking);
        } else if (actor instanceof EventActorEntity npcActor) {
            npcActor.setWalking(walking);
        }
    }

    private static String tag(int pairIndex, boolean maleSide) {
        return "flower_dance_pair_" + pairIndex + (maleSide ? "_male" : "_female");
    }

    private record DanceActor(Mob actor, Vec3 base, boolean maleSide) {
    }

    private record AudienceActor(String npcId, Vec3 position, float yaw) {
        private AudienceActor(String npcId, double x, double y, double z, float yaw) {
            this(npcId, new Vec3(x, y, z), yaw);
        }
    }
}

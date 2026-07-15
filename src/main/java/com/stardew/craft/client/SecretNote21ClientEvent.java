package com.stardew.craft.client;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.cutscene.runtime.EventActorEntity;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Lightweight in-world animation for vanilla secret note 21; it never locks camera or input. */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class SecretNote21ClientEvent {
    static final int JUMP_TICKS = 20;
    static final int LANDING_PAUSE_TICKS = 40;
    static final int RUN_TICKS = 64;
    static final double SIDE_LANDING_DISTANCE = 3.0D;
    static final double JUMP_HEIGHT = 2.75D;
    static final double RUN_BLOCKS_PER_TICK = 0.45D;

    private static final List<Playback> ACTIVE = new ArrayList<>();
    private static int nextEntityId = -2_000_000_000;

    private SecretNote21ClientEvent() {
    }

    public static void start(BlockPos actorOrigin) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || actorOrigin == null) {
            return;
        }
        ACTIVE.add(new Playback(minecraft.level, actorOrigin));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel currentLevel = Minecraft.getInstance().level;
        Iterator<Playback> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            Playback playback = iterator.next();
            if (currentLevel == null || playback.level != currentLevel || playback.tick()) {
                playback.removeActors();
                iterator.remove();
            }
        }
    }

    private static final class Playback {
        private final ClientLevel level;
        private final double centerX;
        private final double baseY;
        private final double centerZ;
        private final EventActorEntity marnie;
        private final EventActorEntity lewis;
        private int age;

        private Playback(ClientLevel level, BlockPos origin) {
            this.level = level;
            this.centerX = origin.getX() + 0.5D;
            this.baseY = origin.getY();
            this.centerZ = origin.getZ() + 0.5D;
            this.marnie = spawn(level, "marnie", centerX, baseY, centerZ, 0.0F);
            this.lewis = spawn(level, "lewis", centerX, baseY, centerZ, 0.0F);
            level.playLocalSound(centerX, baseY, centerZ, ModSounds.DWOP.get(),
                    SoundSource.NEUTRAL, 1.0F, 1.0F, false);
        }

        private boolean tick() {
            age++;
            if (marnie == null || lewis == null) {
                return true;
            }

            if (age <= JUMP_TICKS) {
                double t = age / (double) JUMP_TICKS;
                double arc = 4.0D * JUMP_HEIGHT * t * (1.0D - t);
                place(marnie, centerX - SIDE_LANDING_DISTANCE * t, baseY + arc, centerZ + t, 0.0F, true);
                place(lewis, centerX + SIDE_LANDING_DISTANCE * t, baseY + arc, centerZ + t, 0.0F, true);
                return false;
            }

            int runAge = age - JUMP_TICKS - LANDING_PAUSE_TICKS;
            if (runAge <= 0) {
                place(marnie, centerX - SIDE_LANDING_DISTANCE, baseY, centerZ + 1.0D, 0.0F, false);
                place(lewis, centerX + SIDE_LANDING_DISTANCE, baseY, centerZ + 1.0D, 0.0F, false);
                return false;
            }

            double runDistance = RUN_BLOCKS_PER_TICK * runAge;
            place(marnie, centerX - SIDE_LANDING_DISTANCE - runDistance, baseY, centerZ + 1.0D, 90.0F, true);
            place(lewis, centerX + SIDE_LANDING_DISTANCE + runDistance, baseY, centerZ + 1.0D, -90.0F, true);
            if ((runAge - 1) % 4 == 0) {
                level.playLocalSound(centerX, baseY, centerZ, SoundEvents.GRASS_STEP,
                        SoundSource.NEUTRAL, 0.35F, 1.0F, false);
            }
            return runAge >= RUN_TICKS;
        }

        private void removeActors() {
            if (marnie != null) marnie.discard();
            if (lewis != null) lewis.discard();
        }
    }

    private static EventActorEntity spawn(ClientLevel level, String npcId,
                                           double x, double y, double z, float yaw) {
        EventActorEntity actor = new EventActorEntity(ModEntities.EVENT_ACTOR.get(), level);
        actor.setNpcId(npcId);
        actor.setId(nextEntityId--);
        place(actor, x, y, z, yaw, true);
        level.addEntity(actor);
        return actor;
    }

    private static void place(EventActorEntity actor, double x, double y, double z,
                              float yaw, boolean walking) {
        actor.setPos(x, y, z);
        actor.setYRot(yaw);
        actor.setYHeadRot(yaw);
        actor.setYBodyRot(yaw);
        actor.setWalking(walking);
    }
}

package com.stardew.craft.client.sound;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.decor.ParkFountainSeasons;
import com.stardew.craft.blockentity.ParkFountainBlockEntity;
import com.stardew.craft.client.model.terrain.TerrainSeasonTextures;
import com.stardew.craft.sound.ModSounds;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;

/** Audible even when looking away. Only inspect already loaded nearby chunks. */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class ParkFountainAmbientSound {
    private static final Map<BlockPos, WaterLoop> ACTIVE = new HashMap<>();
    private static ClientLevel level;
    private static int ticks;
    private ParkFountainAmbientSound() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (level != mc.level || mc.player == null || !ParkFountainSeasons.flows(TerrainSeasonTextures.currentTextureSet())) {
            ACTIVE.values().forEach(mc.getSoundManager()::stop);
            ACTIVE.clear();
            level = mc.level;
            ticks = 0;
        }
        if (level == null || mc.player == null || mc.isPaused()
                || !ParkFountainSeasons.flows(TerrainSeasonTextures.currentTextureSet())) return;
        ACTIVE.entrySet().removeIf(entry -> {
            var loop = entry.getValue();
            if (loop.entity.isRemoved() || level.getBlockEntity(entry.getKey()) != loop.entity
                    || mc.player.distanceToSqr(loop.x(), loop.y(), loop.z()) > 16*16) {
                mc.getSoundManager().stop(loop);
                return true;
            }
            return false;
        });
        if (ticks++ % 10 != 0) return;
        var candidates = new ArrayList<ParkFountainBlockEntity>();
        BlockPos player = mc.player.blockPosition();
        for (int x = (player.getX()-14)>>4; x <= (player.getX()+14)>>4; x++)
            for (int z = (player.getZ()-14)>>4; z <= (player.getZ()+14)>>4; z++) {
                var chunk = level.getChunkSource().getChunk(x, z, false);
                if (chunk == null) continue;
                for (var be : chunk.getBlockEntities().values())
                    if (be instanceof ParkFountainBlockEntity fountain && !be.isRemoved()
                            && be.getBlockPos().distToCenterSqr(mc.player.position()) < 14*14) candidates.add(fountain);
            }
        candidates.sort(Comparator.comparingDouble(be -> be.getBlockPos().distToCenterSqr(mc.player.position())));
        var nearest = candidates.stream().limit(3).map(ParkFountainBlockEntity::getBlockPos).toList();
        ACTIVE.entrySet().removeIf(entry -> {
            if (nearest.contains(entry.getKey())) return false;
            mc.getSoundManager().stop(entry.getValue());
            return true;
        });
        for (var entity : candidates.stream().limit(3).toList()) {
            var old = ACTIVE.get(entity.getBlockPos());
            if (old == null || old.isStopped() || !mc.getSoundManager().isActive(old)) {
                var loop = new WaterLoop(entity);
                ACTIVE.put(entity.getBlockPos(), loop);
                mc.getSoundManager().play(loop);
            }
        }
    }

    private static final class WaterLoop extends AbstractTickableSoundInstance {
        private final ParkFountainBlockEntity entity;
        WaterLoop(ParkFountainBlockEntity entity) {
            super(ModSounds.PARK_FOUNTAIN_WATER.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.entity = entity;
            looping = true;
            delay = 0;
            volume = .01F;
            pitch = 1.0F;
            relative = false;
            attenuation = Attenuation.LINEAR;
            x = entity.getBlockPos().getX()+.5;
            y = entity.getBlockPos().getY()+.8;
            z = entity.getBlockPos().getZ()+.5;
        }
        double x() { return x; }
        double y() { return y; }
        double z() { return z; }
        @Override public void tick() {
            if (entity.isRemoved() || entity.getLevel() != Minecraft.getInstance().level
                    || !ParkFountainSeasons.flows(TerrainSeasonTextures.currentTextureSet())) { stop(); return; }
            float target = .45F / (float) Math.sqrt(Math.max(1, ACTIVE.size()));
            volume += (target-volume)*.12F;
        }
    }
}

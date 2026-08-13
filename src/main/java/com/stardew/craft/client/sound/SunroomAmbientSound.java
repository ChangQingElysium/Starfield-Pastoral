package com.stardew.craft.client.sound;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.interior.SunroomService;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Original Sunroom babblingBrook loop, positioned at the authored water source. */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class SunroomAmbientSound {
    private static BrookLoop active;

    private SunroomAmbientSound() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean inside = minecraft.level != null
                && minecraft.player != null
                && SunroomService.isInside(minecraft.level, minecraft.player.blockPosition());
        if (!inside) {
            stop();
            return;
        }
        if (active == null || active.isStopped()
                || !minecraft.getSoundManager().isActive(active)) {
            active = new BrookLoop();
            minecraft.getSoundManager().play(active);
        }
    }

    private static void stop() {
        if (active != null) {
            active.stopNow();
            active = null;
        }
    }

    private static final class BrookLoop extends AbstractSoundInstance implements TickableSoundInstance {
        private boolean stopped;

        private BrookLoop() {
            super(ModSounds.BABBLING_BROOK.get(), SoundSource.AMBIENT,
                    SoundInstance.createUnseededRandom());
            looping = true;
            delay = 0;
            volume = 1.0F;
            pitch = 1.0F;
            relative = false;
            x = SunroomService.BROOK_SOUND_CENTER.getX() + 0.5D;
            y = SunroomService.BROOK_SOUND_CENTER.getY() + 0.5D;
            z = SunroomService.BROOK_SOUND_CENTER.getZ() + 0.5D;
            attenuation = Attenuation.LINEAR;
        }

        private void stopNow() {
            stopped = true;
        }

        @Override
        public boolean isStopped() {
            return stopped;
        }

        @Override
        public void tick() {
        }
    }
}

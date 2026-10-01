package com.stardew.craft.client.pet;

import com.stardew.craft.pet.PetSoundPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class PetSoundClient {
    private PetSoundClient() {}
    public static void play(PetSoundPayload cue) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || cue.footstep() && com.stardew.craft.client.sound.StardewMusicManager.hasActiveMusic()) return;
        var source = cue.voice() ? SoundSource.NEUTRAL : SoundSource.BLOCKS;
        if (mc.options.getSoundSourceVolume(source) <= 0) return;
        var point = cue.position();
        if (cue.range() > 0) {
            if (Math.abs(mc.player.getX() - point.x) > cue.range() || Math.abs(mc.player.getZ() - point.z) > cue.range()
                    || Math.abs(mc.player.getY() - point.y) > cue.range()) return;
        } else if (cue.border() > 0 && mc.levelRenderer.getFrustum() != null && !mc.levelRenderer.getFrustum().isVisible(AABB.ofSize(point.add(0, .5, 0), 1, 1, 1).inflate(cue.border()))) return;
        var sound = new SimpleSoundInstance(cue.sound(), source, cue.volume(), cue.pitch(), SoundInstance.createUnseededRandom(),
                false, 0, SoundInstance.Attenuation.LINEAR, point.x, point.y, point.z, false);
        if (cue.delay() > 0) mc.getSoundManager().playDelayed(sound, cue.delay()); else mc.getSoundManager().play(sound);
    }
}

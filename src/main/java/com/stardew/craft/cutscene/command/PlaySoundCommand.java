package com.stardew.craft.cutscene.command;

import com.stardew.craft.cutscene.runtime.EventPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * play_sound: play a sound effect.
 * JSON: {"cmd":"play_sound", "sound":"stardewcraft:some_sound"}
 * Optional: "volume": 1.0, "pitch": 1.0
 */
public class PlaySoundCommand implements EventCommand {
    private final String soundId;
    private final float volume;
    private final float pitch;

    public PlaySoundCommand(String soundId, float volume, float pitch) {
        this.soundId = soundId;
        this.volume = volume;
        this.pitch = pitch;
    }

    @Override
    public void start(EventPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        ResourceLocation loc = new ResourceLocation(soundId);
        SoundEvent event = SoundEvent.createVariableRangeEvent(loc);
        mc.getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
    }

    @Override public void tick(EventPlayer player) {}
    @Override public boolean isComplete() { return true; }
}

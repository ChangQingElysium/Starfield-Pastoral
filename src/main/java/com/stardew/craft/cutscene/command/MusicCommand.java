package com.stardew.craft.cutscene.command;

import com.stardew.craft.client.sound.StardewMusicManager;
import com.stardew.craft.cutscene.runtime.EventPlayer;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.lang.reflect.Field;
import java.util.Locale;

/**
 * music: plays a named music track via StardewMusicManager.
 * JSON: {"cmd":"music", "track":"music_wizard_tower"}
 *
 * The track name maps to a ModSounds constant (case-insensitive, upper-cased).
 * Stops existing background music first, then plays the specified track
 * as a looping sound through Minecraft's sound manager.
 */
@OnlyIn(Dist.CLIENT)
public class MusicCommand implements EventCommand {

    private final String trackName;

    public MusicCommand(String trackName) {
        this.trackName = trackName;
    }

    @Override
    public void start(EventPlayer player) {
        // Resolve the SoundEvent from ModSounds by reflection lookup
        SoundEvent event = resolveTrack(trackName);
        if (event != null) {
            StardewMusicManager.playForCutscene(event);
        }
    }

    @Override public void tick(EventPlayer player) {}
    @Override public boolean isComplete() { return true; }

    private static SoundEvent resolveTrack(String name) {
        // Try to find the field "MUSIC_xxx" in ModSounds
        String fieldName = name.toUpperCase(Locale.ROOT);
        try {
            Field f = ModSounds.class.getDeclaredField(fieldName);
            Object holder = f.get(null);
            if (holder instanceof com.stardew.craft.port.net.neoforged.neoforge.registries.DeferredHolder<?, ?> dh) {
                return (SoundEvent) dh.get();
            }
        } catch (NoSuchFieldException | IllegalAccessException | ClassCastException e) {
            // Try with MUSIC_ prefix
            try {
                Field f = ModSounds.class.getDeclaredField("MUSIC_" + fieldName);
                Object holder = f.get(null);
                if (holder instanceof com.stardew.craft.port.net.neoforged.neoforge.registries.DeferredHolder<?, ?> dh) {
                    return (SoundEvent) dh.get();
                }
            } catch (Exception ignored) {}
        }
        return null;
    }
}

package com.stardew.craft.cutscene.command;

import com.stardew.craft.client.sound.StardewMusicManager;
import com.stardew.craft.cutscene.runtime.EventPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * stop_music: stops all currently playing music.
 * JSON: {"cmd":"stop_music"}
 */
@OnlyIn(Dist.CLIENT)
public class StopMusicCommand implements EventCommand {

    @Override
    public void start(EventPlayer player) {
        StardewMusicManager.stopForCutsceneSilence();
    }

    @Override public void tick(EventPlayer player) {}
    @Override public boolean isComplete() { return true; }
}

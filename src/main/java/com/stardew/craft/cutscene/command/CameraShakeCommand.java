package com.stardew.craft.cutscene.command;

import com.stardew.craft.cutscene.runtime.EventPlayer;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * camera_shake: shakes the camera for a duration.
 * JSON: {"cmd":"camera_shake", "intensity":0.5, "ticks":20}
 */
@OnlyIn(Dist.CLIENT)
public class CameraShakeCommand implements EventCommand {

    private final float intensity;
    private final int durationTicks;
    private int elapsed = 0;

    public CameraShakeCommand(float intensity, int durationTicks) {
        this.intensity = intensity;
        this.durationTicks = durationTicks;
    }

    @Override
    public void start(EventPlayer player) {
        elapsed = 0;
    }

    @SuppressWarnings("null")
    @Override
    public void tick(EventPlayer player) {
        elapsed++;
        if (elapsed <= durationTicks) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                // Apply random camera offset via hurt-cam style shake
                float progress = (float) elapsed / durationTicks;
                float decay = 1.0f - progress; // decay over time
                float shake = intensity * decay;
                float rx = (float) (Math.random() - 0.5) * 2 * shake;
                float ry = (float) (Math.random() - 0.5) * 2 * shake;
                mc.player.setXRot(mc.player.getXRot() + rx);
                mc.player.setYRot(mc.player.getYRot() + ry);
            }
        }
    }

    @Override
    public boolean isComplete() {
        return elapsed >= durationTicks;
    }
}

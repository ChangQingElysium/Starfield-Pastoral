package com.example.stardewaddon;

import com.stardew.craft.api.v1.npc.StardewNpcExecution;
import net.minecraft.resources.ResourceLocation;
import java.util.Optional;

/** Addons register during construction; unknown expressions pass to other providers. */
public final class ExampleNpcExecution {
    private ExampleNpcExecution() {}
    public static void register() {
        StardewNpcExecution.registerCondition(new ResourceLocation("example_stardew_addon:archivist_hours"),100,
                context -> context.expression().equals("example_stardew_addon:daylight")
                        ? Optional.of(context.level().isDay()) : Optional.empty());
    }
}

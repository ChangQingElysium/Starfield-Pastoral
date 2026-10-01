package com.stardew.craft.client.hud;

import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Client-side: plays a "hold up item" animation (SDV holdUpItemThenMessage parity).
 * SDV original: freezePause=4000ms, sprite frame 57, sound "getNewSpecialItem" at 750ms,
 * item floats above player at 2500ms.
 * MC adaptation: uses displayItemActivation (totem-like) + totem particles
 * (same approach as FishingCatchVisuals), with SDV-like "getNewSpecialItem" sound
 * mapped to ModSounds.NEW_ARTIFACT.
 */
public final class HoldUpItemHandler {

    private HoldUpItemHandler() {}

    @SuppressWarnings("null")
    public static void play(String itemId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        try {
            ResourceLocation rl = new ResourceLocation(itemId);
            Item item = BuiltInRegistries.ITEM.get(rl);
            if (item == null || item == Items.AIR) return;
            ItemStack stack = new ItemStack(item);

            // Totem-like item activation animation (same as FishingCatchVisuals.start)
            mc.gameRenderer.displayItemActivation(stack);

            // Totem particles (same as fishing catch)
            if (mc.level != null) {
                var r = mc.level.getRandom();
                for (int i = 0; i < 28; i++) {
                    double dx = (r.nextDouble() - 0.5) * 0.6;
                    double dy = r.nextDouble() * 0.8 + 0.1;
                    double dz = (r.nextDouble() - 0.5) * 0.6;
                    mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING,
                            mc.player.getX(), mc.player.getY() + 1.0, mc.player.getZ(),
                            dx, dy, dz);
                }
            }

            // SDV: "getNewSpecialItem" at 750ms delay → we use NEW_ARTIFACT (closest equivalent)
            var sound = ModSounds.NEW_ARTIFACT.get();
            if (sound != null) {
                mc.player.playSound(sound, 1.0f, 1.0f);
            }
        } catch (Exception ignored) {}
    }
}

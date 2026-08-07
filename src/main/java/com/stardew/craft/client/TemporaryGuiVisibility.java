package com.stardew.craft.client;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.gui.AnimalMoveHomeSelectScreen;
import com.stardew.craft.client.gui.AnimalPurchaseBuildingScreen;
import com.stardew.craft.client.gui.AnimalPurchaseScreen;
import com.stardew.craft.client.gui.AnimalQueryScreen;
import com.stardew.craft.cutscene.runtime.EventPlayer;
import com.stardew.craft.cutscene.runtime.EventScreenFade;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.EnumSet;

/** Coordinates temporary HUD hiding so overlapping client effects restore the player's setting. */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class TemporaryGuiVisibility {
    public enum Owner {
        CUTSCENE,
        SCREEN_FADE,
        ANIMAL_PURCHASE,
        ANIMAL_PURCHASE_BUILDING,
        ANIMAL_QUERY,
        ANIMAL_MOVE_HOME
    }

    private static final EnumSet<Owner> OWNERS = EnumSet.noneOf(Owner.class);
    private static boolean previousHideGui;

    private TemporaryGuiVisibility() {
    }

    public static void acquire(Owner owner) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options == null) {
            return;
        }
        if (OWNERS.isEmpty()) {
            previousHideGui = minecraft.options.hideGui;
        }
        OWNERS.add(owner);
        minecraft.options.hideGui = true;
    }

    public static void release(Owner owner) {
        if (!OWNERS.remove(owner)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options == null) {
            if (OWNERS.isEmpty()) {
                previousHideGui = false;
            }
            return;
        }
        if (OWNERS.isEmpty()) {
            minecraft.options.hideGui = previousHideGui;
            previousHideGui = false;
        } else {
            minecraft.options.hideGui = true;
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            EventScreenFade.clear();
            releaseAll();
            return;
        }
        for (Owner owner : EnumSet.copyOf(OWNERS)) {
            if (!isStillActive(owner, minecraft)) {
                release(owner);
            }
        }
        if (!OWNERS.isEmpty() && minecraft.options != null) {
            minecraft.options.hideGui = true;
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        EventScreenFade.clear();
        releaseAll();
    }

    private static boolean isStillActive(Owner owner, Minecraft minecraft) {
        return switch (owner) {
            case CUTSCENE -> EventPlayer.get().isRunning();
            case SCREEN_FADE -> EventScreenFade.isHidingGui();
            case ANIMAL_PURCHASE -> minecraft.screen instanceof AnimalPurchaseScreen;
            case ANIMAL_PURCHASE_BUILDING -> minecraft.screen instanceof AnimalPurchaseBuildingScreen;
            case ANIMAL_QUERY -> minecraft.screen instanceof AnimalQueryScreen;
            case ANIMAL_MOVE_HOME -> minecraft.screen instanceof AnimalMoveHomeSelectScreen;
        };
    }

    private static void releaseAll() {
        if (OWNERS.isEmpty()) {
            return;
        }
        OWNERS.clear();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options != null) {
            minecraft.options.hideGui = previousHideGui;
        }
        previousHideGui = false;
    }
}

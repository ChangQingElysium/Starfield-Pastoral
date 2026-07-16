package com.stardew.craft.client;

import com.stardew.craft.StardewCraft;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

@SuppressWarnings("removal") // NeoForge 21.1 弃用 bus 参数，但功能仍正常
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ModKeyMappings {

    public static final String CATEGORY = "key.categories.stardewcraft";

    public static final KeyMapping SKILL_MINOR = new KeyMapping(
            "key.stardewcraft.skill_minor",
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_RIGHT,
            CATEGORY
    );

    public static final KeyMapping SKILL_MAJOR = new KeyMapping(
            "key.stardewcraft.skill_major",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            CATEGORY
    );

    public static final KeyMapping EMOTE_WHEEL = new KeyMapping(
            "key.stardewcraft.emote_wheel",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_TAB,
            CATEGORY
    );

    public static final KeyMapping GAME_MENU = new KeyMapping(
            "key.stardewcraft.game_menu",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CATEGORY
    );

    public static final KeyMapping QUEST_LOG = new KeyMapping(
            "key.stardewcraft.quest_log",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_U,
            CATEGORY
    );

    public static final KeyMapping CUTSCENE_SKIP = new KeyMapping(
            "key.stardewcraft.cutscene_skip",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            CATEGORY
    );

    private ModKeyMappings() {}

    /**
     * Reads a configurable key without forwarding GLFW's invalid -1 value
     * when the player has explicitly left the mapping unbound.
     */
    public static boolean isDown(KeyMapping mapping) {
        return mapping != null && !mapping.isUnbound() && mapping.isDown();
    }

    @SuppressWarnings("null")
    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(SKILL_MINOR);
        event.register(SKILL_MAJOR);
        event.register(EMOTE_WHEEL);
        event.register(GAME_MENU);
        event.register(QUEST_LOG);
        event.register(CUTSCENE_SKIP);
    }
}

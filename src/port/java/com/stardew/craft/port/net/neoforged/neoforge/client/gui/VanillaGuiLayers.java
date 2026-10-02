package com.stardew.craft.port.net.neoforged.neoforge.client.gui;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;

/**
 * PORT(1.20.1): NeoForge 21.1 vanilla HUD layer ids, mapped onto the equivalent Forge 1.20.1
 * {@link VanillaGuiOverlay} ids so they compare equal to {@code RenderGuiLayerEvent#getName()}.
 * NeoForge layers without a Forge overlay counterpart (camera overlays, spectator tooltip, experience level,
 * demo overlay, saving indicator) are omitted.
 */
public final class VanillaGuiLayers {
    public static final ResourceLocation CROSSHAIR = VanillaGuiOverlay.CROSSHAIR.id();
    public static final ResourceLocation HOTBAR = VanillaGuiOverlay.HOTBAR.id();
    public static final ResourceLocation JUMP_METER = VanillaGuiOverlay.JUMP_BAR.id();
    public static final ResourceLocation EXPERIENCE_BAR = VanillaGuiOverlay.EXPERIENCE_BAR.id();
    public static final ResourceLocation PLAYER_HEALTH = VanillaGuiOverlay.PLAYER_HEALTH.id();
    public static final ResourceLocation ARMOR_LEVEL = VanillaGuiOverlay.ARMOR_LEVEL.id();
    public static final ResourceLocation FOOD_LEVEL = VanillaGuiOverlay.FOOD_LEVEL.id();
    public static final ResourceLocation VEHICLE_HEALTH = VanillaGuiOverlay.MOUNT_HEALTH.id();
    public static final ResourceLocation AIR_LEVEL = VanillaGuiOverlay.AIR_LEVEL.id();
    public static final ResourceLocation SELECTED_ITEM_NAME = VanillaGuiOverlay.ITEM_NAME.id();
    public static final ResourceLocation EFFECTS = VanillaGuiOverlay.POTION_ICONS.id();
    public static final ResourceLocation BOSS_OVERLAY = VanillaGuiOverlay.BOSS_EVENT_PROGRESS.id();
    public static final ResourceLocation SLEEP_OVERLAY = VanillaGuiOverlay.SLEEP_FADE.id();
    public static final ResourceLocation DEBUG_OVERLAY = VanillaGuiOverlay.DEBUG_TEXT.id();
    public static final ResourceLocation SCOREBOARD_SIDEBAR = VanillaGuiOverlay.SCOREBOARD.id();
    public static final ResourceLocation OVERLAY_MESSAGE = VanillaGuiOverlay.RECORD_OVERLAY.id();
    public static final ResourceLocation TITLE = VanillaGuiOverlay.TITLE_TEXT.id();
    public static final ResourceLocation CHAT = VanillaGuiOverlay.CHAT_PANEL.id();
    public static final ResourceLocation TAB_LIST = VanillaGuiOverlay.PLAYER_LIST.id();
    public static final ResourceLocation SUBTITLE_OVERLAY = VanillaGuiOverlay.SUBTITLES.id();

    private VanillaGuiLayers() {}
}

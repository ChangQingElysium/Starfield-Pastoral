package com.stardew.craft.client.gui.common;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.gui.overnight.StardewGuiUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class CommonGuiTextures {
    private static final SdvTexture BACK_ARROW = SdvTexture.full(common("back_arrow"), 12, 11);
    private static final SdvTexture FORWARD_ARROW = SdvTexture.full(common("forward_arrow"), 12, 11);
    private static final SdvTexture CLOSE_BUTTON = SdvTexture.full(common("close_button"), 12, 12);
    private static final SdvTexture CANCEL_BUTTON_SMALL = SdvTexture.full(common("cancel_button_small"), 12, 12);
    private static final SdvTexture CANCEL_BUTTON_LARGE = SdvTexture.full(common("cancel_button_large"), 64, 64);
    private static final SdvTexture OK_CHECK_SMALL = SdvTexture.full(common("ok_check_small"), 16, 15);
    private static final SdvTexture OK_CHECK_GREEN = SdvTexture.full(common("ok_check_green"), 16, 16);
    private static final SdvTexture SCROLL_ARROW_UP = SdvTexture.full(common("scroll_arrow_up"), 11, 12);
    private static final SdvTexture SCROLL_ARROW_DOWN = SdvTexture.full(common("scroll_arrow_down"), 11, 12);
    private static final SdvTexture SCROLL_BAR_THUMB = SdvTexture.full(common("scroll_bar_thumb"), 6, 10);
    private static final SdvTexture SHOP_COIN = SdvTexture.full(common("shop_coin"), 9, 10);
    private static final SdvTexture QI_COIN = SdvTexture.full(common("qi_coin"), 9, 10);
    private static final SdvTexture FAIR_STAR_TOKEN = SdvTexture.full(fairTargetGame("star_token"), 8, 8);
    private static final SdvTexture SHOP_PORTRAIT_FRAME = SdvTexture.full(common("shop_portrait_frame"), 74, 74);
    private static final SdvTexture DAY_TIME_MONEY_BOX = SdvTexture.full(common("day_time_money_box"), 71, 43);
    private static final SdvTexture MONEY_BOX = SdvTexture.full(common("money_box"), 65, 17);
    private static final SdvTexture[] MONEY_DIGITS = frames("money_digit_", 10, 5, 8);
    private static final SdvTexture[] NUMBER_DIGITS = frames("number_digit_", 10, 8, 8);
    private static final SdvTexture REWARD_SLOT = SdvTexture.full(common("reward_slot"), 24, 24);
    private static final SdvTexture QUEST_COIN = SdvTexture.full(common("quest_coin"), 16, 16);
    private static final SdvTexture QUEST_NEW = SdvTexture.full(common("quest_new"), 23, 9);
    private static final SdvTexture QUEST_DONE = SdvTexture.full(common("quest_done"), 23, 9);
    private static final SdvTexture QUEST_DOT = SdvTexture.full(common("quest_dot"), 3, 8);
    private static final SdvTexture QUEST_TIMED = SdvTexture.full(common("quest_timed"), 9, 9);
    private static final SdvTexture QUEST_OBJECTIVE_ARROW = SdvTexture.full(common("quest_objective_arrow"), 5, 4);
    private static final ResourceLocation QUEST_PROGRESS_BAR = common("quest_progress_bar");
    private static final SdvTexture QUEST_PROGRESS_NOTCH = SdvTexture.full(common("quest_progress_notch"), 1, 12);
    private static final SdvTexture QUEST_HUD_BUTTON = SdvTexture.full(common("quest_hud_button"), 11, 14);
    private static final SdvTexture[] QUEST_HUD_PING = frames("quest_hud_ping_", 2, 16, 16);
    private static final ResourceLocation TEXTURE_BOX_18 = common("texture_box_18");
    private static final ResourceLocation ENTRY_BOX_15 = common("entry_box_15");
    private static final ResourceLocation SCROLL_TRACK_BOX = common("scroll_track_box");
    private static final ResourceLocation OPTION_HIGHLIGHT_BOX = common("option_highlight_box_3");
    private static final ResourceLocation CALENDAR_TODAY_BOX = common("calendar_today_box_3");
    private static final ResourceLocation BILLBOARD_ACCEPT_BOX = common("billboard_accept_box_9");
    private static final ResourceLocation MENU_TEXTURE_BOX_60 = animalQuery("menu_texture_box_60");
    private static final SdvTexture ITEM_SLOT_18 = SdvTexture.full(common("item_slot_18"), 18, 18);
    private static final SdvTexture SCROLL_BANNER_LEFT = SdvTexture.full(common("scroll_banner_left"), 12, 18);
    private static final ResourceLocation SCROLL_BANNER_MID = common("scroll_banner_mid");
    private static final SdvTexture SCROLL_BANNER_RIGHT = SdvTexture.full(common("scroll_banner_right"), 12, 18);
    private static final SdvTexture CARPENTER_UPGRADE = SdvTexture.full(common("carpenter_upgrade"), 9, 13);
    private static final SdvTexture GOLD_COIN_1_6 = SdvTexture.full(common("gold_coin_1_6"), 14, 13);
    private static final SdvTexture SOCIAL_GIFT_ICON = SdvTexture.full(common("social_gift_icon"), 14, 12);
    private static final SdvTexture SOCIAL_TALK_ICON = SdvTexture.full(common("social_talk_icon"), 13, 11);
    private static final SdvTexture SOCIAL_BOX_EMPTY = SdvTexture.full(common("social_box_empty"), 9, 9);
    private static final SdvTexture SOCIAL_BOX_FILLED = SdvTexture.full(common("social_box_filled"), 9, 9);
    private static final SdvTexture ANIMAL_PAGE_PET_ICON = SdvTexture.full(common("animal_page_pet_icon"), 10, 10);
    private static final SdvTexture[] ANIMAL_PAGE_PET_STATUS = new SdvTexture[] {
        SdvTexture.full(common("animal_page_pet_none"), 9, 9),
        SdvTexture.full(common("animal_page_pet_auto"), 9, 9),
        SdvTexture.full(common("animal_page_pet_hand"), 9, 9)
    };
    private static final SdvTexture ANIMAL_PAGE_CRACKER = SdvTexture.full(common("animal_page_cracker"), 15, 11);
    private static final SdvTexture CATEGORY_FRAME_LEFT = SdvTexture.full(common("category_frame_left"), 16, 16);
    private static final SdvTexture CATEGORY_FRAME_RIGHT = SdvTexture.full(common("category_frame_right"), 16, 16);
    private static final SdvTexture CATALOGUE_STAR = SdvTexture.full(common("catalogue_star"), 16, 16);
    private static final SdvTexture[] CATALOGUE_TAB_ICONS = new SdvTexture[] {
        CATALOGUE_STAR,
        SdvTexture.full(common("catalogue_tab_seats"), 16, 16),
        SdvTexture.full(common("catalogue_tab_tables"), 16, 16),
        SdvTexture.full(common("catalogue_tab_lamps"), 16, 16),
        SdvTexture.full(common("catalogue_tab_wall_decor"), 16, 16),
        SdvTexture.full(common("catalogue_tab_carpets"), 16, 16),
        SdvTexture.full(common("catalogue_tab_wallpaper"), 16, 16),
        SdvTexture.full(common("catalogue_tab_flooring"), 16, 16),
        CATALOGUE_STAR
    };
    private static final SdvTexture[] WARDROBE_TAB_ICONS = new SdvTexture[] {
        SdvTexture.full(common("wardrobe_tab_all"), 16, 16),
        SdvTexture.full(common("wardrobe_tab_hats"), 16, 16),
        SdvTexture.full(common("wardrobe_tab_shirts"), 16, 16),
        SdvTexture.full(common("wardrobe_tab_pants"), 16, 16),
        SdvTexture.full(common("wardrobe_tab_shoes"), 16, 16),
        SdvTexture.full(common("wardrobe_tab_rings"), 16, 16)
    };
    private static final SdvTexture[] DIALOGUE_NEXT_PAGE = frames("dialogue_next_page_", 6, 9, 9);
    private static final SdvTexture[] DIALOGUE_END = frames("dialogue_end_", 11, 11, 12);
    private static final SdvTexture DIALOGUE_QUESTION_CORNER_TL = SdvTexture.full(common("dialogue_question_corner_tl"), 14, 13);
    private static final SdvTexture DIALOGUE_QUESTION_CORNER_TR = SdvTexture.full(common("dialogue_question_corner_tr"), 12, 11);
    private static final SdvTexture DIALOGUE_QUESTION_CORNER_BR = SdvTexture.full(common("dialogue_question_corner_br"), 12, 12);
    private static final SdvTexture DIALOGUE_QUESTION_CORNER_BL = SdvTexture.full(common("dialogue_question_corner_bl"), 14, 11);
    private static final SdvTexture DIALOGUE_QUESTION_FILL = SdvTexture.full(common("dialogue_question_fill"), 16, 16);
    private static final SdvTexture DIALOGUE_QUESTION_TOP = SdvTexture.full(common("dialogue_question_top"), 1, 6);
    private static final SdvTexture DIALOGUE_QUESTION_BOTTOM = SdvTexture.full(common("dialogue_question_bottom"), 1, 8);
    private static final SdvTexture DIALOGUE_QUESTION_LEFT = SdvTexture.full(common("dialogue_question_left"), 8, 1);
    private static final SdvTexture DIALOGUE_QUESTION_RIGHT = SdvTexture.full(common("dialogue_question_right"), 7, 1);
    private static final SdvTexture DIALOGUE_PORTRAIT_DIVIDER_TOP = SdvTexture.full(common("dialogue_portrait_divider_top"), 10, 7);
    private static final SdvTexture DIALOGUE_PORTRAIT_DIVIDER_BOTTOM = SdvTexture.full(common("dialogue_portrait_divider_bottom"), 10, 8);
    private static final SdvTexture DIALOGUE_PORTRAIT_VERTICAL_STRIP = SdvTexture.full(common("dialogue_portrait_vertical_strip"), 9, 1);
    private static final SdvTexture DIALOGUE_PORTRAIT_FRAME = SdvTexture.full(common("dialogue_portrait_frame"), 115, 97);
    private static final SdvTexture DIALOGUE_BOX_FILL = SdvTexture.full(common("dialogue_box_fill"), 16, 16);
    private static final SdvTexture DIALOGUE_BOX_TOP = SdvTexture.full(common("dialogue_box_top"), 1, 6);
    private static final SdvTexture DIALOGUE_BOX_BOTTOM = SdvTexture.full(common("dialogue_box_bottom"), 1, 8);
    private static final SdvTexture DIALOGUE_BOX_LEFT = SdvTexture.full(common("dialogue_box_left"), 8, 1);
    private static final SdvTexture DIALOGUE_BOX_RIGHT = SdvTexture.full(common("dialogue_box_right"), 7, 1);
    private static final SdvTexture[] FRIENDSHIP_JEWELS = frames("friendship_jewel_", 20, 11, 11);
    private static final SdvTexture FRIENDSHIP_JEWEL_MAX = SdvTexture.full(common("friendship_jewel_max"), 11, 11);
    private static final SdvTexture QUESTION_EXCLAMATION_1_6 = SdvTexture.full(common("question_exclamation_1_6"), 17, 19);
    private static final SdvTexture[] QUESTION_ARROW_1_6 = frames("question_arrow_1_6_", 6, 7, 12);
    private static final SdvTexture MASTERY_ICON_1_6 = SdvTexture.full(common("mastery_icon_1_6"), 11, 11);
    private static final SdvTexture SKILLS_MASTERY_EMPTY_1_6 = SdvTexture.full(common("skills_mastery_empty_1_6"), 142, 12);
    private static final SdvTexture SKILLS_CC_ROOM_INCOMPLETE_1_6 = SdvTexture.full(common("skills_cc_room_incomplete_1_6"), 11, 11);
    private static final SdvTexture SKILLS_CC_ROOM_COMPLETE_1_6 = SdvTexture.full(common("skills_cc_room_complete_1_6"), 11, 11);
    private static final SdvTexture SKILLS_JOJA_ROOM_INCOMPLETE_1_6 = SdvTexture.full(common("skills_joja_room_incomplete_1_6"), 11, 11);
    private static final SdvTexture SKILLS_JOJA_ROOM_COMPLETE_1_6 = SdvTexture.full(common("skills_joja_room_complete_1_6"), 11, 11);
    private static final SdvTexture SKILLS_JOJA_LOGO_1_6 = SdvTexture.full(common("skills_joja_logo_1_6"), 51, 48);
    private static final SdvTexture SKILLS_CC_UNKNOWN_1_6 = SdvTexture.full(common("skills_cc_unknown_1_6"), 52, 47);
    private static final SdvTexture SKILLS_HOUSE_ICON = SdvTexture.full(common("skills_house_icon"), 10, 10);
    private static final SdvTexture SKILLS_MINE_EMPTY_1_6 = SdvTexture.full(common("skills_mine_empty_1_6"), 13, 13);
    private static final SdvTexture SKILLS_MINE_ICON_1_6 = SdvTexture.full(common("skills_mine_icon_1_6"), 13, 13);
    private static final SdvTexture SKILLS_SKULL_ICON_1_6 = SdvTexture.full(common("skills_skull_icon_1_6"), 8, 9);
    private static final SdvTexture SKILLS_STARDROP_ICON_1_6 = SdvTexture.full(common("skills_stardrop_icon_1_6"), 12, 14);
    private static final SdvTexture SKILLS_STARDROP_EMPTY_1_6 = SdvTexture.full(common("skills_stardrop_empty_1_6"), 12, 14);
    private static final SdvTexture[] SKILLS_DOODLES_1_6 = new SdvTexture[] {
        SdvTexture.full(common("skills_doodle_spring_1_6"), 33, 23),
        SdvTexture.full(common("skills_doodle_summer_1_6"), 33, 23),
        SdvTexture.full(common("skills_doodle_fall_1_6"), 33, 23),
        SdvTexture.full(common("skills_doodle_winter_1_6"), 33, 23)
    };
    private static final SdvTexture[] GAME_MENU_TABS = frames("game_menu_tab_", 10, 16, 16);
    private static final SdvTexture GAME_MENU_ORGANIZE = SdvTexture.full(common("game_menu_organize"), 16, 16);
    private static final SdvTexture GAME_MENU_TRASH_BODY_0 = SdvTexture.full(common("game_menu_trash_body_0"), 18, 26);
    private static final SdvTexture GAME_MENU_TRASH_LID_0 = SdvTexture.full(common("game_menu_trash_lid_0"), 18, 10);
    private static final SdvTexture[] SKILL_ICONS = new SdvTexture[] {
        SdvTexture.full(common("skill_icon_farming"), 10, 10),
        SdvTexture.full(common("skill_icon_mining"), 10, 10),
        SdvTexture.full(common("skill_icon_foraging"), 10, 10),
        SdvTexture.full(common("skill_icon_fishing"), 10, 10),
        SdvTexture.full(common("skill_icon_combat"), 10, 10)
    };
    private static final SdvTexture SKILL_BAR_SMALL_EMPTY = SdvTexture.full(common("skill_bar_small_empty"), 8, 9);
    private static final SdvTexture SKILL_BAR_SMALL_FILLED = SdvTexture.full(common("skill_bar_small_filled"), 8, 9);
    private static final SdvTexture SKILL_BAR_BIG_EMPTY = SdvTexture.full(common("skill_bar_big_empty"), 14, 9);
    private static final SdvTexture SKILL_BAR_BIG_FILLED = SdvTexture.full(common("skill_bar_big_filled"), 14, 9);
    private static final SdvTexture SOCIAL_HEART_FILLED = SdvTexture.full(common("social_heart_filled"), 7, 6);
    private static final SdvTexture SOCIAL_HEART_EMPTY = SdvTexture.full(common("social_heart_empty"), 7, 6);
    private static final SdvTexture[] POWER_ICONS = new SdvTexture[] {
        SdvTexture.full(power("forest_magic"), 16, 16),
        SdvTexture.full(power("dwarvish_translation_guide"), 16, 16),
        SdvTexture.full(power("rusty_key"), 16, 16),
        SdvTexture.full(power("club_card"), 16, 16),
        SdvTexture.full(power("special_charm"), 16, 16),
        SdvTexture.full(power("skull_key"), 16, 16),
        SdvTexture.full(power("magnifying_glass"), 16, 16),
        SdvTexture.full(power("dark_talisman"), 16, 16),
        SdvTexture.full(power("magic_ink"), 16, 16),
        SdvTexture.full(power("bear_paw"), 16, 16),
        SdvTexture.full(power("spring_onion_mastery"), 16, 16),
        SdvTexture.full(power("key_to_the_town"), 16, 16)
    };

    private CommonGuiTextures() {
    }

    public static void drawBackArrow(GuiGraphics graphics, int x, int y, float scale) {
        BACK_ARROW.drawPixelZoom(graphics, x, y, scale);
    }

    public static void drawBackArrowTint(GuiGraphics graphics, int x, int y, float scale, float red, float green, float blue, float alpha) {
        BACK_ARROW.drawPixelZoomTint(graphics, x, y, scale, red, green, blue, alpha);
    }

    public static void drawForwardArrow(GuiGraphics graphics, int x, int y, float scale) {
        FORWARD_ARROW.drawPixelZoom(graphics, x, y, scale);
    }

    public static void drawForwardArrowTint(GuiGraphics graphics, int x, int y, float scale, float red, float green, float blue, float alpha) {
        FORWARD_ARROW.drawPixelZoomTint(graphics, x, y, scale, red, green, blue, alpha);
    }

    public static void drawCloseButton(GuiGraphics graphics, int x, int y, float scale) {
        CLOSE_BUTTON.drawPixelZoom(graphics, x, y, scale);
    }

    public static void drawSmallCancelButton(GuiGraphics graphics, int x, int y, float scale) {
        CANCEL_BUTTON_SMALL.drawPixelZoom(graphics, x, y, scale);
    }

    public static void drawLargeCancelButton(GuiGraphics graphics, int x, int y, float scale) {
        CANCEL_BUTTON_LARGE.drawPixelZoom(graphics, x, y, scale);
    }

    public static void drawOkCheckSmall(GuiGraphics graphics, int x, int y, float scale) {
        OK_CHECK_SMALL.drawPixelZoom(graphics, x, y, scale);
    }

    public static void drawOkCheckGreen(GuiGraphics graphics, int x, int y, float scale) {
        OK_CHECK_GREEN.drawPixelZoom(graphics, x, y, scale);
    }

    public static void drawOkCheckGreenTint(GuiGraphics graphics, int x, int y, float scale, float red, float green, float blue, float alpha) {
        OK_CHECK_GREEN.drawPixelZoomTint(graphics, x, y, scale, red, green, blue, alpha);
    }

    public static void drawScrollArrowUp(GuiGraphics graphics, int x, int y, float scale) {
        SCROLL_ARROW_UP.drawPixelZoom(graphics, x, y, scale);
    }

    public static void drawScrollArrowUpTint(GuiGraphics graphics, int x, int y, float scale, float red, float green, float blue, float alpha) {
        SCROLL_ARROW_UP.drawPixelZoomTint(graphics, x, y, scale, red, green, blue, alpha);
    }

    public static void drawScrollArrowDown(GuiGraphics graphics, int x, int y, float scale) {
        SCROLL_ARROW_DOWN.drawPixelZoom(graphics, x, y, scale);
    }

    public static void drawScrollArrowDownTint(GuiGraphics graphics, int x, int y, float scale, float red, float green, float blue, float alpha) {
        SCROLL_ARROW_DOWN.drawPixelZoomTint(graphics, x, y, scale, red, green, blue, alpha);
    }

    public static void drawScrollBarThumb(GuiGraphics graphics, int x, int y, float scale) {
        SCROLL_BAR_THUMB.drawPixelZoom(graphics, x, y, scale);
    }

    public static void drawShopCoin(GuiGraphics graphics, int x, int y, float scale) {
        SHOP_COIN.drawPixelZoom(graphics, x, y, scale);
    }

    public static void drawShopCoinTint(GuiGraphics graphics, int x, int y, float scale, float red, float green, float blue, float alpha) {
        SHOP_COIN.drawPixelZoomTint(graphics, x, y, scale, red, green, blue, alpha);
    }

    public static void drawQiCoin(GuiGraphics graphics, int x, int y, float scale) {
        QI_COIN.drawPixelZoom(graphics, x, y, scale);
    }

    public static void drawQiCoinTint(GuiGraphics graphics, int x, int y, float scale,
                                      float red, float green, float blue, float alpha) {
        QI_COIN.drawPixelZoomTint(graphics, x, y, scale, red, green, blue, alpha);
    }

    public static void drawFairStarToken(GuiGraphics graphics, int x, int y, float scale) {
        FAIR_STAR_TOKEN.drawPixelZoom(graphics, x, y, scale);
    }

    public static void drawFairStarTokenTint(GuiGraphics graphics, int x, int y, float scale, float red, float green, float blue, float alpha) {
        FAIR_STAR_TOKEN.drawPixelZoomTint(graphics, x, y, scale, red, green, blue, alpha);
    }

    public static void drawShopPortraitFrame(GuiGraphics graphics, int x, int y, float scale) {
        SHOP_PORTRAIT_FRAME.drawPixelZoom(graphics, x, y, scale);
    }

    public static void drawMoneyBox(GuiGraphics graphics, int x, int y, float scale) {
        MONEY_BOX.drawPixelZoom(graphics, x, y, scale);
    }

    public static void drawDayTimeMoneyBox(GuiGraphics graphics, int x, int y) {
        DAY_TIME_MONEY_BOX.drawAtCurrentPose(graphics, x, y);
    }

    public static void drawMoneyDigitTint(GuiGraphics graphics, int x, int y, int digit, float scale, float red, float green, float blue, float alpha) {
        MONEY_DIGITS[clampFrame(digit, MONEY_DIGITS.length)].drawPixelZoomTint(graphics, x, y, scale, red, green, blue, alpha);
    }

    public static void drawNumberDigitAtCurrentPoseTint(GuiGraphics graphics, int digit, int x, int y, float red, float green, float blue, float alpha) {
        NUMBER_DIGITS[clampFrame(digit, NUMBER_DIGITS.length)].drawAtCurrentPoseTint(graphics, x, y, red, green, blue, alpha);
    }

    public static void drawRewardSlot(GuiGraphics graphics, int x, int y, float scale) {
        REWARD_SLOT.drawPixelZoom(graphics, x, y, scale);
    }

    public static void drawQuestCoin(GuiGraphics graphics, int x, int y, float scale) {
        QUEST_COIN.drawPixelZoom(graphics, x, y, scale);
    }

    public static void drawQuestNew(GuiGraphics graphics, int x, int y, float scale) {
        QUEST_NEW.drawPixelZoom(graphics, x, y, scale);
    }

    public static void drawQuestDone(GuiGraphics graphics, int x, int y, float scale) {
        QUEST_DONE.drawPixelZoom(graphics, x, y, scale);
    }

    public static void drawQuestDot(GuiGraphics graphics, int x, int y, float scale) {
        QUEST_DOT.drawPixelZoom(graphics, x, y, scale);
    }

    public static void drawQuestDotAtCurrentPose(GuiGraphics graphics, int x, int y) {
        QUEST_DOT.drawAtCurrentPose(graphics, x, y);
    }

    public static void drawQuestTimed(GuiGraphics graphics, int x, int y, float scale) {
        QUEST_TIMED.drawPixelZoom(graphics, x, y, scale);
    }

    public static void drawQuestObjectiveArrow(GuiGraphics graphics, int x, int y, float scale) {
        QUEST_OBJECTIVE_ARROW.drawPixelZoom(graphics, x, y, scale);
    }

    public static void drawQuestObjectiveArrowAtCurrentPose(GuiGraphics graphics, int x, int y) {
        QUEST_OBJECTIVE_ARROW.drawAtCurrentPose(graphics, x, y);
    }

    public static void drawQuestProgressBarBackground(GuiGraphics graphics, int x, int y, int width, float scale) {
        int height = Math.round(12 * scale);
        int edge = Math.round(5 * scale);
        int safeWidth = Math.max(Math.round(10 * scale), width);
        graphics.blit(QUEST_PROGRESS_BAR, x, y, edge, height, 0, 0, 5, 12, 47, 12);
        graphics.blit(QUEST_PROGRESS_BAR, x + edge, y, safeWidth - edge * 2, height, 5, 0, 37, 12, 47, 12);
        graphics.blit(QUEST_PROGRESS_BAR, x + safeWidth - edge, y, edge, height, 42, 0, 5, 12, 47, 12);
    }

    public static void drawQuestProgressNotch(GuiGraphics graphics, int x, int y, float scale) {
        QUEST_PROGRESS_NOTCH.drawPixelZoom(graphics, x, y, scale);
    }

    public static void drawQuestHudButton(GuiGraphics graphics, int x, int y, float scale) {
        QUEST_HUD_BUTTON.drawPixelZoom(graphics, x, y, scale);
    }

    public static void drawQuestHudPing(GuiGraphics graphics, int x, int y, int frame, float scale) {
        QUEST_HUD_PING[clampFrame(frame, QUEST_HUD_PING.length)].drawPixelZoom(graphics, x, y, scale);
    }

    public static void drawTextureBox(GuiGraphics graphics, int x, int y, int width, int height, float scale, boolean drawShadow) {
        StardewGuiUtil.drawTextureBox(graphics, TEXTURE_BOX_18, 18, 18, 0, 0, 18, 18, x, y, width, height, scale, drawShadow);
    }

    public static void drawTextureBoxNoShadow(GuiGraphics graphics, int x, int y, int width, int height, float scale) {
        drawTextureBox(graphics, x, y, width, height, scale, false);
    }

    public static void drawEntryBox(GuiGraphics graphics, int x, int y, int width, int height, float scale, boolean drawShadow) {
        StardewGuiUtil.drawTextureBox(graphics, ENTRY_BOX_15, 15, 15, 0, 0, 15, 15, x, y, width, height, scale, drawShadow);
    }

    public static void drawScrollTrackBox(GuiGraphics graphics, int x, int y, int width, int height, float scale) {
        StardewGuiUtil.drawTextureBox(graphics, SCROLL_TRACK_BOX, 6, 6, 0, 0, 6, 6, x, y, width, height, scale, false);
    }

    public static void drawScrollBannerBox(GuiGraphics graphics, int x, int y, int width, int height, float scale) {
        // Stardew's SpriteText banner is not a nine-slice box. It is composed
        // from a 12x18 left cap, a horizontally stretched 1x18 middle strip,
        // and a 12x18 right cap. Treating the 11x18 cursor fragment as a 3x3
        // box clips its lower border and distorts every casino coin banner.
        int capWidth = Math.max(1, Math.round(12.0F * scale));
        int middleWidth = Math.max(1, width - capWidth * 2);
        drawScrollBanner(graphics, x + capWidth, y, middleWidth, scale);
    }

    public static void drawCalendarTodayBox(GuiGraphics graphics, int x, int y, int width, int height, float scale) {
        StardewGuiUtil.drawTextureBox(graphics, CALENDAR_TODAY_BOX, 3, 3, 0, 0, 3, 3, x, y, width, height, scale, false);
    }

    public static void drawOptionHighlightBox(GuiGraphics graphics, int x, int y, int width, int height, float scale) {
        StardewGuiUtil.drawTextureBox(graphics, OPTION_HIGHLIGHT_BOX, 3, 3, 0, 0, 3, 3, x, y, width, height, scale, false);
    }

    public static void drawBillboardAcceptBox(GuiGraphics graphics, int x, int y, int width, int height, float scale) {
        StardewGuiUtil.drawTextureBox(graphics, BILLBOARD_ACCEPT_BOX, 9, 9, 0, 0, 9, 9, x, y, width, height, scale, false);
    }

    public static void drawMenuTextureBox(GuiGraphics graphics, int x, int y, int width, int height, float scale, boolean drawShadow) {
        StardewGuiUtil.drawTextureBox(graphics, MENU_TEXTURE_BOX_60, 60, 60, 0, 0, 60, 60, x, y, width, height, scale, drawShadow);
    }

    public static void drawMenuTextureBoxTint(GuiGraphics graphics, int x, int y, int width, int height,
                                               float scale, boolean drawShadow,
                                               float red, float green, float blue, float alpha) {
        StardewGuiUtil.drawTextureBoxTint(graphics, MENU_TEXTURE_BOX_60, 60, 60, 0, 0, 60, 60,
                x, y, width, height, scale, drawShadow, red, green, blue, alpha,
                Math.max(1, Math.round(8.0F * scale)));
    }

	public static void drawMenuTextureBox(GuiGraphics graphics, int x, int y, int width, int height,
			float scale, boolean drawShadow, float alpha) {
		StardewGuiUtil.drawTextureBox(graphics, MENU_TEXTURE_BOX_60, 60, 60, 0, 0, 60, 60,
			x, y, width, height, scale, drawShadow, alpha);
	}

	public static void drawMenuTextureBox(GuiGraphics graphics, int x, int y, int width, int height,
			float scale, boolean drawShadow, float alpha, int shadowOffset) {
		StardewGuiUtil.drawTextureBox(graphics, MENU_TEXTURE_BOX_60, 60, 60, 0, 0, 60, 60,
			x, y, width, height, scale, drawShadow, alpha, shadowOffset);
	}

    public static void drawMenuTile(GuiGraphics graphics, int x, int y, int width, int height, int tileIndex) {
        StardewGuiUtil.drawMenuTileIndex(graphics, x, y, width, height, tileIndex);
    }

    public static void drawItemSlot18(GuiGraphics graphics, int x, int y, float scale) {
        ITEM_SLOT_18.drawPixelZoom(graphics, x, y, scale);
    }

    public static void drawScrollBanner(GuiGraphics graphics, int textX, int y, int middleWidth, float scale) {
        SCROLL_BANNER_LEFT.drawPixelZoom(graphics, textX - Math.round(12 * scale), y, scale);

        graphics.pose().pushPose();
        graphics.pose().translate(textX, y, 0);
        graphics.pose().scale(middleWidth, scale, 1.0f);
        graphics.blit(SCROLL_BANNER_MID, 0, 0, 0, 0, 1, 18, 1, 18);
        graphics.pose().popPose();

        SCROLL_BANNER_RIGHT.drawPixelZoom(graphics, textX + middleWidth, y, scale);
    }

    public static void drawCarpenterUpgrade(GuiGraphics graphics, int x, int y, float scale) {
        CARPENTER_UPGRADE.drawPixelZoom(graphics, x, y, scale);
    }

    public static void drawGoldCoin16(GuiGraphics graphics, int x, int y, float scale) {
        GOLD_COIN_1_6.drawPixelZoom(graphics, x, y, scale);
    }

    public static void drawSocialGiftIcon(GuiGraphics graphics, int x, int y, float scale, float alpha) {
        SOCIAL_GIFT_ICON.drawPixelZoomTint(graphics, x, y, scale, 1.0f, 1.0f, 1.0f, alpha);
    }

    public static void drawSocialTalkIcon(GuiGraphics graphics, int x, int y, float scale, float alpha) {
        SOCIAL_TALK_ICON.drawPixelZoomTint(graphics, x, y, scale, 1.0f, 1.0f, 1.0f, alpha);
    }

    public static void drawSocialBox(GuiGraphics graphics, int x, int y, boolean filled, float scale, float alpha) {
        SdvTexture texture = filled ? SOCIAL_BOX_FILLED : SOCIAL_BOX_EMPTY;
        texture.drawPixelZoomTint(graphics, x, y, scale, 1.0f, 1.0f, 1.0f, alpha);
    }

    public static void drawAnimalPagePetIcon(GuiGraphics graphics, int x, int y, float scale, float alpha) {
        ANIMAL_PAGE_PET_ICON.drawPixelZoomTint(graphics, x, y, scale, 1.0f, 1.0f, 1.0f, alpha);
    }

    public static void drawAnimalPagePetStatus(GuiGraphics graphics, int x, int y,
                                               int status, float scale, float alpha) {
        ANIMAL_PAGE_PET_STATUS[clampFrame(status, ANIMAL_PAGE_PET_STATUS.length)]
                .drawPixelZoomTint(graphics, x, y, scale, 1.0f, 1.0f, 1.0f, alpha);
    }

    public static void drawAnimalPageCracker(GuiGraphics graphics, int x, int y, float scale, float alpha) {
        ANIMAL_PAGE_CRACKER.drawPixelZoomTint(graphics, x, y, scale, 1.0f, 1.0f, 1.0f, alpha);
    }

    public static void drawCatalogueStarTint(GuiGraphics graphics, int x, int y, float scale, float red, float green, float blue, float alpha) {
        CATALOGUE_STAR.drawPixelZoomTint(graphics, x, y, scale, red, green, blue, alpha);
    }

    public static void drawCatalogueTabIconTint(GuiGraphics graphics, int x, int y, int tabIndex, float scale, float red, float green, float blue, float alpha) {
        CATALOGUE_TAB_ICONS[clampFrame(tabIndex, CATALOGUE_TAB_ICONS.length)].drawPixelZoomTint(graphics, x, y, scale, red, green, blue, alpha);
    }

    public static void drawWardrobeTabIconTint(GuiGraphics graphics, int x, int y, int tabIndex, float scale, float red, float green, float blue, float alpha) {
        WARDROBE_TAB_ICONS[clampFrame(tabIndex, WARDROBE_TAB_ICONS.length)].drawPixelZoomTint(graphics, x, y, scale, red, green, blue, alpha);
    }

    public static void drawCategoryFrame(GuiGraphics graphics, int x, int y, boolean left, float scale, float alpha) {
        SdvTexture texture = left ? CATEGORY_FRAME_LEFT : CATEGORY_FRAME_RIGHT;
        texture.drawPixelZoomTint(graphics, x, y, scale, 1.0f, 1.0f, 1.0f, alpha);
    }

    public static void drawDialogueNextPage(GuiGraphics graphics, int x, int y, int frame, float scale) {
        DIALOGUE_NEXT_PAGE[clampFrame(frame, DIALOGUE_NEXT_PAGE.length)].drawPixelZoom(graphics, x, y, scale);
    }

    public static void drawDialogueEnd(GuiGraphics graphics, int x, int y, int frame, float scale) {
        DIALOGUE_END[clampFrame(frame, DIALOGUE_END.length)].drawPixelZoom(graphics, x, y, scale);
    }

    public static void drawDialogueQuestionCornerTl(GuiGraphics graphics, int x, int y, float scale) {
        DIALOGUE_QUESTION_CORNER_TL.drawPixelZoom(graphics, x, y, scale);
    }

    public static void drawDialogueQuestionCornerTr(GuiGraphics graphics, int x, int y, float scale) {
        DIALOGUE_QUESTION_CORNER_TR.drawPixelZoom(graphics, x, y, scale);
    }

    public static void drawDialogueQuestionCornerBr(GuiGraphics graphics, int x, int y, float scale) {
        DIALOGUE_QUESTION_CORNER_BR.drawPixelZoom(graphics, x, y, scale);
    }

    public static void drawDialogueQuestionCornerBl(GuiGraphics graphics, int x, int y, float scale) {
        DIALOGUE_QUESTION_CORNER_BL.drawPixelZoom(graphics, x, y, scale);
    }

    public static void drawDialogueQuestionFill(GuiGraphics graphics, int x, int y, int width, int height) {
        DIALOGUE_QUESTION_FILL.drawStretchedTint(graphics, x, y, width, height, 1.0f, 1.0f, 1.0f, 1.0f);
    }

    public static void drawDialogueQuestionTop(GuiGraphics graphics, int x, int y, int width, int height) {
        DIALOGUE_QUESTION_TOP.drawStretchedTint(graphics, x, y, width, height, 1.0f, 1.0f, 1.0f, 1.0f);
    }

    public static void drawDialogueQuestionBottom(GuiGraphics graphics, int x, int y, int width, int height) {
        DIALOGUE_QUESTION_BOTTOM.drawStretchedTint(graphics, x, y, width, height, 1.0f, 1.0f, 1.0f, 1.0f);
    }

    public static void drawDialogueQuestionLeft(GuiGraphics graphics, int x, int y, int width, int height) {
        DIALOGUE_QUESTION_LEFT.drawStretchedTint(graphics, x, y, width, height, 1.0f, 1.0f, 1.0f, 1.0f);
    }

    public static void drawDialogueQuestionRight(GuiGraphics graphics, int x, int y, int width, int height) {
        DIALOGUE_QUESTION_RIGHT.drawStretchedTint(graphics, x, y, width, height, 1.0f, 1.0f, 1.0f, 1.0f);
    }

    public static void drawDialoguePortraitDividerTop(GuiGraphics graphics, int x, int y, float scale) {
        DIALOGUE_PORTRAIT_DIVIDER_TOP.drawPixelZoom(graphics, x, y, scale);
    }

    public static void drawDialoguePortraitDividerBottom(GuiGraphics graphics, int x, int y, float scale) {
        DIALOGUE_PORTRAIT_DIVIDER_BOTTOM.drawPixelZoom(graphics, x, y, scale);
    }

    public static void drawDialoguePortraitVerticalStrip(GuiGraphics graphics, int x, int y, int width, int height) {
        DIALOGUE_PORTRAIT_VERTICAL_STRIP.drawStretchedTint(graphics, x, y, width, height, 1.0f, 1.0f, 1.0f, 1.0f);
    }

    public static void drawDialoguePortraitFrame(GuiGraphics graphics, int x, int y, float scale) {
        DIALOGUE_PORTRAIT_FRAME.drawPixelZoom(graphics, x, y, scale);
    }

    public static void drawDialogueBoxFill(GuiGraphics graphics, int x, int y, int width, int height) {
        DIALOGUE_BOX_FILL.drawStretchedTint(graphics, x, y, width, height, 1.0f, 1.0f, 1.0f, 1.0f);
    }

    public static void drawDialogueBoxTop(GuiGraphics graphics, int x, int y, int width, int height) {
        DIALOGUE_BOX_TOP.drawStretchedTint(graphics, x, y, width, height, 1.0f, 1.0f, 1.0f, 1.0f);
    }

    public static void drawDialogueBoxBottom(GuiGraphics graphics, int x, int y, int width, int height) {
        DIALOGUE_BOX_BOTTOM.drawStretchedTint(graphics, x, y, width, height, 1.0f, 1.0f, 1.0f, 1.0f);
    }

    public static void drawDialogueBoxLeft(GuiGraphics graphics, int x, int y, int width, int height) {
        DIALOGUE_BOX_LEFT.drawStretchedTint(graphics, x, y, width, height, 1.0f, 1.0f, 1.0f, 1.0f);
    }

    public static void drawDialogueBoxRight(GuiGraphics graphics, int x, int y, int width, int height) {
        DIALOGUE_BOX_RIGHT.drawStretchedTint(graphics, x, y, width, height, 1.0f, 1.0f, 1.0f, 1.0f);
    }

    public static void drawFriendshipJewel(GuiGraphics graphics, int x, int y, int hearts, int frame, float scale) {
        if (hearts >= 10) {
            FRIENDSHIP_JEWEL_MAX.drawPixelZoom(graphics, x, y, scale);
            return;
        }
        int row = Math.max(0, Math.min(4, hearts / 2));
        FRIENDSHIP_JEWELS[row * 4 + clampFrame(frame, 4)].drawPixelZoom(graphics, x, y, scale);
    }

    public static void drawQuestionExclamation16(GuiGraphics graphics, int x, int y, float scale) {
        QUESTION_EXCLAMATION_1_6.drawPixelZoom(graphics, x, y, scale);
    }

    public static void drawQuestionArrow16(GuiGraphics graphics, int x, int y, int frame, float scale) {
        QUESTION_ARROW_1_6[clampFrame(frame, QUESTION_ARROW_1_6.length)].drawPixelZoom(graphics, x, y, scale);
    }

    public static void drawMasteryIcon16Tint(GuiGraphics graphics, int x, int y, float scale, float red, float green, float blue, float alpha) {
        MASTERY_ICON_1_6.drawPixelZoomTint(graphics, x, y, scale, red, green, blue, alpha);
    }

    public static void drawSkillsMasteryEmpty16Tint(GuiGraphics graphics, int x, int y, float scale, float red, float green, float blue, float alpha) {
        SKILLS_MASTERY_EMPTY_1_6.drawPixelZoomTint(graphics, x, y, scale, red, green, blue, alpha);
    }

    public static void drawSkillsCcRoom16(GuiGraphics graphics, int x, int y, boolean complete, boolean joja, float scale, float alpha) {
        SdvTexture texture;
        if (joja) {
            texture = complete ? SKILLS_JOJA_ROOM_COMPLETE_1_6 : SKILLS_JOJA_ROOM_INCOMPLETE_1_6;
        } else {
            texture = complete ? SKILLS_CC_ROOM_COMPLETE_1_6 : SKILLS_CC_ROOM_INCOMPLETE_1_6;
        }
        texture.drawPixelZoomTint(graphics, x, y, scale, 1.0f, 1.0f, 1.0f, alpha);
    }

    public static void drawSkillsJojaLogo16(GuiGraphics graphics, int x, int y, float scale, float alpha) {
        SKILLS_JOJA_LOGO_1_6.drawPixelZoomTint(graphics, x, y, scale, 1.0f, 1.0f, 1.0f, alpha);
    }

    public static void drawSkillsCcUnknown16(GuiGraphics graphics, int x, int y, float scale, float alpha) {
        SKILLS_CC_UNKNOWN_1_6.drawPixelZoomTint(graphics, x, y, scale, 1.0f, 1.0f, 1.0f, alpha);
    }

    public static void drawSkillsHouseIcon(GuiGraphics graphics, int x, int y, float scale, float alpha) {
        SKILLS_HOUSE_ICON.drawPixelZoomTint(graphics, x, y, scale, 1.0f, 1.0f, 1.0f, alpha);
    }

    public static void drawSkillsMineIcon16(GuiGraphics graphics, int x, int y, boolean reachedMine, float scale, float alpha) {
        SdvTexture texture = reachedMine ? SKILLS_MINE_ICON_1_6 : SKILLS_MINE_EMPTY_1_6;
        texture.drawPixelZoomTint(graphics, x, y, scale, 1.0f, 1.0f, 1.0f, alpha);
    }

    public static void drawSkillsSkullIcon16(GuiGraphics graphics, int x, int y, float scale, float alpha) {
        SKILLS_SKULL_ICON_1_6.drawPixelZoomTint(graphics, x, y, scale, 1.0f, 1.0f, 1.0f, alpha);
    }

    public static void drawSkillsStardropIcon16(GuiGraphics graphics, int x, int y, boolean foundAny, float scale, float alpha) {
        SdvTexture texture = foundAny ? SKILLS_STARDROP_ICON_1_6 : SKILLS_STARDROP_EMPTY_1_6;
        texture.drawPixelZoomTint(graphics, x, y, scale, 1.0f, 1.0f, 1.0f, alpha);
    }

    public static void drawSkillsDoodle16(GuiGraphics graphics, int x, int y, int seasonIndex, float scale, float alpha) {
        SKILLS_DOODLES_1_6[clampFrame(seasonIndex, SKILLS_DOODLES_1_6.length)].drawPixelZoomTint(graphics, x, y, scale, 1.0f, 1.0f, 1.0f, alpha);
    }

    public static void drawGameMenuTab(GuiGraphics graphics, int x, int y, int tabIndex, float scale) {
        GAME_MENU_TABS[clampFrame(tabIndex, GAME_MENU_TABS.length)].drawPixelZoom(graphics, x, y, scale);
    }

    public static void drawGameMenuOrganize(GuiGraphics graphics, int x, int y, float scale) {
        GAME_MENU_ORGANIZE.drawPixelZoom(graphics, x, y, scale);
    }

    public static void drawGameMenuTrashBody(GuiGraphics graphics, int x, int y, float scale) {
        GAME_MENU_TRASH_BODY_0.drawPixelZoom(graphics, x, y, scale);
    }

    public static void drawGameMenuTrashLidAtCurrentPose(GuiGraphics graphics, int x, int y) {
        GAME_MENU_TRASH_LID_0.drawAtCurrentPose(graphics, x, y);
    }

    public static void drawSkillIconTint(GuiGraphics graphics, int x, int y, int skillRow, float scale, float red, float green, float blue, float alpha) {
        SKILL_ICONS[clampFrame(skillRow, SKILL_ICONS.length)].drawPixelZoomTint(graphics, x, y, scale, red, green, blue, alpha);
    }

    public static void drawSkillBarTint(GuiGraphics graphics, int x, int y, boolean big, boolean filled, float scale, float red, float green, float blue, float alpha) {
        SdvTexture texture;
        if (big) {
            texture = filled ? SKILL_BAR_BIG_FILLED : SKILL_BAR_BIG_EMPTY;
        } else {
            texture = filled ? SKILL_BAR_SMALL_FILLED : SKILL_BAR_SMALL_EMPTY;
        }
        texture.drawPixelZoomTint(graphics, x, y, scale, red, green, blue, alpha);
    }

    public static void drawSocialHeartTint(GuiGraphics graphics, int x, int y, boolean filled, float scale, float red, float green, float blue, float alpha) {
        SdvTexture texture = filled ? SOCIAL_HEART_FILLED : SOCIAL_HEART_EMPTY;
        texture.drawPixelZoomTint(graphics, x, y, scale, red, green, blue, alpha);
    }

    /** Draws whole source pixels of the filled 7×6 heart over an empty heart. */
    public static void drawSocialHeartPartial(GuiGraphics graphics, int x, int y,
                                              float progress, float scale, float alpha) {
        int sourceColumns = net.minecraft.util.Mth.clamp(
                (int) Math.floor(progress * 7.0F), 0, 7);
        if (sourceColumns <= 0) {
            return;
        }
        int clippedWidth = Math.max(1, Math.round(sourceColumns * scale));
        int heartHeight = Math.max(1, Math.round(6.0F * scale));
        graphics.enableScissor(x, y, x + clippedWidth, y + heartHeight);
        try {
            SOCIAL_HEART_FILLED.drawPixelZoomTint(
                    graphics, x, y, scale, 1.0F, 1.0F, 1.0F, alpha);
        } finally {
            graphics.disableScissor();
        }
    }

    public static void drawPowerIconTint(GuiGraphics graphics, int x, int y, int powerIndex, float scale, float red, float green, float blue, float alpha) {
        POWER_ICONS[clampFrame(powerIndex, POWER_ICONS.length)].drawPixelZoomTint(graphics, x, y, scale, red, green, blue, alpha);
    }

    public static int itemSize(float scale) {
        return Math.round(16.0f * scale);
    }

    public static void drawItem(GuiGraphics graphics, ItemStack stack, int x, int y, float scale) {
        if (stack.isEmpty()) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.renderItem(stack, 0, 0);
        graphics.pose().popPose();
    }

    public static void drawItemTint(GuiGraphics graphics, ItemStack stack, int x, int y, float scale, float red, float green, float blue, float alpha) {
        RenderSystem.setShaderColor(red, green, blue, alpha);
        drawItem(graphics, stack, x, y, scale);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void drawItemWithDecorations(GuiGraphics graphics, Font font, ItemStack stack, int x, int y, float scale) {
        if (stack.isEmpty()) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.renderItem(stack, 0, 0);
        graphics.renderItemDecorations(font, stack, 0, 0);
        graphics.pose().popPose();
    }

    public static void drawItemDecorations(GuiGraphics graphics, Font font, ItemStack stack, int x, int y, float scale) {
        if (stack.isEmpty()) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.renderItemDecorations(font, stack, 0, 0);
        graphics.pose().popPose();
    }

    public static void drawItemCenteredInBox(GuiGraphics graphics, ItemStack stack, int x, int y, int width, int height, float scale) {
        int itemSize = itemSize(scale);
        drawItem(graphics, stack, x + (width - itemSize) / 2, y + (height - itemSize) / 2, scale);
    }

    public static void drawItemWithDecorationsCenteredInBox(GuiGraphics graphics, Font font, ItemStack stack, int x, int y, int width, int height, float scale) {
        int itemSize = itemSize(scale);
        drawItemWithDecorations(graphics, font, stack, x + (width - itemSize) / 2, y + (height - itemSize) / 2, scale);
    }

    private static SdvTexture[] frames(String prefix, int count, int width, int height) {
        SdvTexture[] textures = new SdvTexture[count];
        for (int index = 0; index < count; index++) {
            textures[index] = SdvTexture.full(common(prefix + index), width, height);
        }
        return textures;
    }

    private static int clampFrame(int frame, int count) {
        return Math.max(0, Math.min(count - 1, frame));
    }

    private static ResourceLocation common(String name) {
        return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/common/" + name + ".png");
    }

    private static ResourceLocation power(String name) {
        return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/powers/" + name + ".png");
    }

    private static ResourceLocation animalQuery(String name) {
        return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/" + name + ".png");
    }

    private static ResourceLocation fairTargetGame(String name) {
        return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/fair/target_game/" + name + ".png");
    }
}

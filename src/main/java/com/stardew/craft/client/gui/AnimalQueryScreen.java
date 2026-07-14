package com.stardew.craft.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.menu.AnimalQueryMenu;
import com.stardew.craft.network.payload.AnimalQueryActionPayload;
import com.stardew.craft.network.payload.AnimalRenamePayload;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nonnull;
import java.util.List;

@SuppressWarnings("null")
public class AnimalQueryScreen extends AbstractContainerScreen<AnimalQueryMenu> {

    private static final int BASE_WIDTH = 677;
    private static final int BASE_HEIGHT = 360;
    private static final float UI_SCALE = 0.5f;

    // Figma absolute layout (relative to the 677x360 frame).
    private static final int FIG_PORTRAIT_X = 34;
    private static final int FIG_PORTRAIT_Y = 26;
    private static final int FIG_PORTRAIT_SIZE = 229;
    private static final int FIG_NAME_X = 355;
    private static final int FIG_NAME_Y = 0;
    private static final int FIG_NAME_W = 322;
    private static final int FIG_NAME_H = 48;
    private static final int FIG_RENAME_Y = 2;
    private static final int FIG_RENAME_SIZE = 48;
    private static final int FIG_ACTION_X = 321;
    private static final int FIG_ACTION_Y = 310;
    private static final int FIG_ACTION_SIZE = 50;
    private static final int FIG_ACTION_SPACING = 70;
    private static final int FIG_HEART_X = 48;
    private static final int FIG_HEART_Y = 329;
    private static final int FIG_HEART_SPACING = 44;
    private static final float FIG_NAME_FONT_SIZE = 48.0f;
    private static final float FIG_INFO_FONT_SIZE = 20.0f;
    private static final int ENTRY_ANIM_MS = 320;

    // Colors sampled from the Figma draft.
    private static final int COLOR_TEXT_MAIN = 0xFFFFFFFF;
    private static final int COLOR_TEXT_SUB = 0xFFF0F0F0;
    private static final int COLOR_TEXT_HINT = 0xFFD2CDD2;
    private static final int COLOR_ACCENT = 0xFFE3A564;
    private static final int COLOR_NAME_UNDERLINE = 0xFF6B6470;

    private static final ResourceLocation HEART_EMPTY = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/heart_empty.png");
    private static final ResourceLocation HEART_HALF_BASE = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/heart_half_base.png");
    private static final ResourceLocation HEART_HALF_FILL = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/heart_half_fill.png");
    private static final ResourceLocation SELL_ICON = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/sell_icon.png");
    private static final ResourceLocation MOVE_ICON = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/move_icon.png");
    private static final ResourceLocation OK_ICON = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/ok_yes_tile46.png");
    private static final ResourceLocation NO_ICON = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/cancel_no_tile47.png");
    private static final ResourceLocation REPRO_OFF_ICON = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/repro_off.png");
    private static final ResourceLocation REPRO_ON_ICON = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/repro_on.png");
    private static final ResourceLocation GOLDEN_CRACKER_ICON = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/item/misc/golden_animal_cracker.png");
    private static final ResourceLocation GOLD_ICON = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/gold_icon.png");
    private static final ResourceLocation RENAME_ICON = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/rename.png");

    private static final ResourceLocation ICON_WHITE_CHICKEN = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/icon_white_chicken.png");
    private static final ResourceLocation ICON_GOLDEN_CHICKEN = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/icon_golden_chicken.png");
    private static final ResourceLocation ICON_DUCK = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/icon_duck.png");
    private static final ResourceLocation ICON_VOID_CHICKEN = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/icon_void_chicken.png");
    private static final ResourceLocation ICON_RABBIT = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/icon_rabbit.png");
    private static final ResourceLocation ICON_OSTRICH = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/icon_ostrich.png");
    private static final ResourceLocation ICON_DINOSAUR = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/icon_dinosaur.png");
    private static final ResourceLocation ICON_COW = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/icon_cow.png");
    private static final ResourceLocation ICON_GOAT = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/icon_goat.png");
    private static final ResourceLocation ICON_SHEEP = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/icon_sheep.png");
    private static final ResourceLocation ICON_PIG = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/icon_pig.png");

    private int panelX;
    private int panelY;

    // Figma-relative anchors.
    private int portraitCX;
    private int portraitCY;
    private int nameX;
    private int nameY;
    private int renameY;
    private int infoX;
    private int infoY;
    private int infoW;

    private int heartStartX;
    private int heartY;

    private int actionStartX;
    private int actionY;
    private int actionSpacing;
    private int actionSize;
    private int renameSize;
    private int renameCenterX;
    private int actionAnimOffsetY;
    private int portraitAnimOffsetY;

    private boolean confirmingSell = false;
    private boolean editingName = false;

    private float yesScale = 3.05f;
    private float noScale = 3.05f;
    private float renameHoverScale = 3.0f * UI_SCALE;
    private float sellScale = 3.125f * UI_SCALE;
    private float moveScale = 3.125f * UI_SCALE;
    private float reproScale = 3.125f * UI_SCALE;
    private float okScale = 3.125f * UI_SCALE;

    private int hoverHotspot = 0;
    private long openedAtMs;

    private String lastSubmittedName = "";
    private String editBuffer = "";
    private int editCursor = 0;

    public AnimalQueryScreen(AnimalQueryMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = BASE_WIDTH;
        this.imageHeight = BASE_HEIGHT;
        this.lastSubmittedName = title.getString();
    }

    @Override
    protected void init() {
        super.init();
        computeLayout();
        this.openedAtMs = System.currentTimeMillis();
        this.hoverHotspot = 0;

        this.editBuffer = this.lastSubmittedName;
        this.editCursor = this.editBuffer.length();
        setEditingName(false);
    }

    @Override
    protected void renderBg(@Nonnull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        computeLayout();
        updateEntryAnimationState();
        updateHoverState(mouseX, mouseY);

        // Keep vanilla scene visible, no custom panel block.
        graphics.pose().pushPose();
        graphics.pose().translate(0, portraitAnimOffsetY, 0);
        drawPortrait(graphics);
        graphics.pose().popPose();

        float rightEase = getEntryEase();
        float rightScaleX = 0.78f + 0.22f * rightEase;
        float rightScaleY = 0.90f + 0.10f * rightEase;
        int rightPivotX = nameX;
        int rightPivotY = nameY + si(4);
        graphics.pose().pushPose();
        graphics.pose().translate(rightPivotX, rightPivotY, 0);
        graphics.pose().scale(rightScaleX, rightScaleY, 1.0f);
        graphics.pose().translate(-rightPivotX, -rightPivotY, 0);
        drawNameArea(graphics, mouseX, mouseY);
        drawInfoLines(graphics);
        graphics.pose().popPose();

        drawFriendshipRow(graphics);
        drawGoldenCrackerIndicator(graphics);
        graphics.pose().pushPose();
        graphics.pose().translate(0, actionAnimOffsetY, 0);
        drawActionRow(graphics, mouseX, mouseY);
        graphics.pose().popPose();

        if (confirmingSell) {
            drawConfirmSellDialog(graphics, mouseX, mouseY);
        }
    }

    @Override
    protected void renderLabels(@Nonnull GuiGraphics graphics, int mouseX, int mouseY) {
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!confirmingSell) {
            renderQuickTooltip(graphics, mouseX, mouseY);
        }
    }

    @Override
    protected void renderTooltip(@Nonnull GuiGraphics graphics, int x, int y) {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        computeLayout();
        int mx = (int) mouseX;
        int my = (int) mouseY;

        if (button == 1) {
            commitNameAndCloseEdit();
            PacketDistributor.sendToServer(new AnimalQueryActionPayload(AnimalQueryActionPayload.Action.CLOSE, false));
            this.onClose();
            return true;
        }

        if (confirmingSell && handleConfirmDialogClick(mx, my)) {
            return true;
        }

        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (inside(mx, my, nameX, nameY, si(FIG_NAME_W), si(FIG_NAME_H))) {
            setEditingName(true);
            playUi(ModSounds.SMALL_SELECT.get(), 0.7f, 1.05f);
            return true;
        }

        int dynamicRenameX = getDynamicRenameCenterX();
        if (inside(mx, my, dynamicRenameX - renameSize / 2, renameY - renameSize / 2, renameSize, renameSize)) {
            setEditingName(true);
            playUi(ModSounds.SMALL_SELECT.get(), 0.7f, 1.05f);
            return true;
        }

        int[] centers = computeActionCenters();
        int actionHitY = actionY + actionAnimOffsetY;
        if (centers.length > 0 && inside(mx, my, centers[0] - actionSize / 2, actionHitY - actionSize / 2, actionSize, actionSize)) {
            confirmingSell = true;
            commitNameAndCloseEdit();
            playUi(ModSounds.SMALL_SELECT.get(), 0.8f, 1.0f);
            return true;
        }
        if (centers.length > 1 && inside(mx, my, centers[1] - actionSize / 2, actionHitY - actionSize / 2, actionSize, actionSize)) {
            commitNameAndCloseEdit();
            PacketDistributor.sendToServer(new AnimalQueryActionPayload(AnimalQueryActionPayload.Action.MOVE_HOME, false));
            playUi(ModSounds.SMALL_SELECT.get(), 0.8f, 1.0f);
            return true;
        }

        if (this.menu.canToggleReproduction()) {
            if (centers.length > 2 && inside(mx, my, centers[2] - actionSize / 2, actionHitY - actionSize / 2, actionSize, actionSize)) {
                boolean next = !this.menu.allowReproduction();
                this.menu.setAllowReproductionValue(next);
                PacketDistributor.sendToServer(new AnimalQueryActionPayload(AnimalQueryActionPayload.Action.TOGGLE_REPRODUCTION, next));
                playUi(ModSounds.DRUMKIT6.get(), 0.85f, 1.0f);
                return true;
            }
            if (centers.length > 3 && inside(mx, my, centers[3] - actionSize / 2, actionHitY - actionSize / 2, actionSize, actionSize)) {
                commitNameAndCloseEdit();
                PacketDistributor.sendToServer(new AnimalQueryActionPayload(AnimalQueryActionPayload.Action.CLOSE, false));
                this.onClose();
                playUi(ModSounds.SMALL_SELECT.get(), 0.8f, 1.0f);
                return true;
            }
        } else {
            if (centers.length > 2 && inside(mx, my, centers[2] - actionSize / 2, actionHitY - actionSize / 2, actionSize, actionSize)) {
                commitNameAndCloseEdit();
                PacketDistributor.sendToServer(new AnimalQueryActionPayload(AnimalQueryActionPayload.Action.CLOSE, false));
                this.onClose();
                playUi(ModSounds.SMALL_SELECT.get(), 0.8f, 1.0f);
                return true;
            }
        }

        if (editingName) {
            commitNameAndCloseEdit();
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (editingName) {
            if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
                commitNameAndCloseEdit();
                return true;
            }
            if (keyCode == InputConstants.KEY_ESCAPE) {
                setEditingName(false);
                return true;
            }
            if (keyCode == InputConstants.KEY_LEFT && editCursor > 0) {
                editCursor--;
                return true;
            }
            if (keyCode == InputConstants.KEY_RIGHT && editCursor < editBuffer.length()) {
                editCursor++;
                return true;
            }
            if (keyCode == InputConstants.KEY_HOME) {
                editCursor = 0;
                return true;
            }
            if (keyCode == InputConstants.KEY_END) {
                editCursor = editBuffer.length();
                return true;
            }
            if (keyCode == InputConstants.KEY_BACKSPACE && editCursor > 0) {
                editBuffer = editBuffer.substring(0, editCursor - 1) + editBuffer.substring(editCursor);
                editCursor--;
                return true;
            }
            if (keyCode == InputConstants.KEY_DELETE && editCursor < editBuffer.length()) {
                editBuffer = editBuffer.substring(0, editCursor) + editBuffer.substring(editCursor + 1);
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (editingName) {
            if (codePoint >= ' ' && codePoint != '\u00A7' && editBuffer.length() < 48) {
                editBuffer = editBuffer.substring(0, editCursor) + codePoint + editBuffer.substring(editCursor);
                editCursor++;
                this.renameCenterX = getDynamicRenameCenterX();
                return true;
            }
            return super.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void removed() {
        commitNameAndCloseEdit();
        super.removed();
    }

    private void drawPortrait(GuiGraphics graphics) {
        int radius = si(FIG_PORTRAIT_SIZE / 2);
        drawFilledCircle(graphics, portraitCX, portraitCY, radius, COLOR_ACCENT);

        ResourceLocation icon = resolveAnimalIcon();
        // Keep cow icon inside the circle like the design draft.
        float scale = 4.85f * UI_SCALE;
        int src = 32;
        int drawX = portraitCX - Math.round(src * scale / 2f);
        int drawY = portraitCY - Math.round(src * scale / 2f);

        graphics.pose().pushPose();
        graphics.pose().translate(drawX, drawY, 0);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.blit(icon, 0, 0, 0, 0, src, src, src, src);
        graphics.pose().popPose();
    }

    private void drawNameArea(GuiGraphics graphics, int mouseX, int mouseY) {
        String name = getCurrentName();
        float nameScale = (FIG_NAME_FONT_SIZE / 9.0f) * UI_SCALE;
        int nameWidthInFontPx = Math.max(1, (int) (si(FIG_NAME_W - 8) / nameScale));
        String shown = this.font.plainSubstrByWidth(name, nameWidthInFontPx);

        graphics.pose().pushPose();
        graphics.pose().translate(nameX, nameY + si(4), 0);
        graphics.pose().scale(nameScale, nameScale, 1.0f);
        graphics.drawString(this.font, Component.literal(shown).withStyle(ChatFormatting.BOLD), 0, 0, COLOR_TEXT_MAIN, false);

        if (editingName) {
            long tick = System.currentTimeMillis() / 500L;
            if ((tick & 1L) == 0L) {
                String raw = this.editBuffer;
                int cursor = Math.max(0, Math.min(raw.length(), this.editCursor));
                String prefix = raw.substring(0, cursor);
                String shownPrefix = this.font.plainSubstrByWidth(prefix, nameWidthInFontPx);
                int cursorX = this.font.width(shownPrefix) + 1;
                graphics.fill(cursorX, -1, cursorX + 1, this.font.lineHeight + 1, COLOR_TEXT_MAIN);
            }
        }

        graphics.pose().popPose();

        int underlineY = nameY + si(FIG_NAME_H) - 1;
        boolean hoverName = inside(mouseX, mouseY, nameX, nameY, si(FIG_NAME_W), si(FIG_NAME_H));
        int underlineColor = (hoverName || editingName) ? 0xFFB8AFC3 : COLOR_NAME_UNDERLINE;
        graphics.fill(nameX, underlineY, nameX + si(FIG_NAME_W), underlineY + 1, underlineColor);

        this.renameCenterX = getDynamicRenameCenterX();
        boolean hoverRename = inside(mouseX, mouseY, this.renameCenterX - renameSize / 2, renameY - renameSize / 2, renameSize, renameSize);
        this.renameHoverScale = approach(this.renameHoverScale, hoverRename ? 3.25f * UI_SCALE : 3.0f * UI_SCALE);
        drawScaledIcon(graphics, RENAME_ICON, this.renameCenterX, renameY, this.renameHoverScale, 16, 16, 16, 16);
    }

    private void drawInfoLines(GuiGraphics graphics) {
        int textX = infoX;
        float lineScale = (FIG_INFO_FONT_SIZE / 9.0f) * UI_SCALE;

        Component ageLine = Component.translatable("stardewcraft.animal.query.age",
                Math.max(1, this.menu.getAgeDays() / 7 + 1));
        Component stageLine = Component.translatable("stardewcraft.animal.query.stage",
                Component.translatable(this.menu.isBaby()
                        ? "stardewcraft.animal.query.stage.baby"
                        : "stardewcraft.animal.query.stage.adult"));
        Component petLine = Component.translatable("stardewcraft.animal.query.petted_today",
                this.menu.wasPetToday() ? "✅" : "❌");
        Component feedLine = Component.translatable("stardewcraft.animal.query.fed_today",
                this.menu.wasFedToday() ? "✅" : "❌");

        drawSingleLineScaled(graphics, ageLine, textX, infoY, COLOR_TEXT_SUB, infoW, lineScale);
        drawSingleLineScaled(graphics, stageLine, textX, infoY + si(32), COLOR_TEXT_SUB, infoW, lineScale);
        drawSingleLineScaled(graphics, petLine, textX, infoY + si(64), COLOR_TEXT_SUB, infoW, lineScale);
        drawSingleLineScaled(graphics, feedLine, textX, infoY + si(96), COLOR_TEXT_SUB, infoW, lineScale);

        // Keep mood text above the bottom action row at the current global UI scale.
        drawSingleLineScaled(graphics, Component.translatable("stardewcraft.animal.query.mood_label"),
                textX, infoY + si(132), COLOR_TEXT_MAIN, infoW, lineScale);
        drawSingleLineScaled(graphics, Component.translatable(this.menu.getMoodTranslationKey()), textX, infoY + si(160), COLOR_TEXT_HINT, infoW, lineScale);

        String price = String.valueOf(this.menu.getEstimatedSellPrice());
        int priceX = infoX + infoW - this.font.width(price) - si(18);
        graphics.pose().pushPose();
        graphics.pose().translate(priceX - si(18), infoY - si(1), 0);
        graphics.pose().scale(UI_SCALE, UI_SCALE, 1.0f);
        graphics.blit(GOLD_ICON, 0, 0, 0, 0, 16, 16, 16, 16);
        graphics.pose().popPose();
        graphics.drawString(this.font, price, priceX, infoY + si(6), COLOR_TEXT_MAIN, false);
    }

    private void drawSingleLineScaled(GuiGraphics graphics, Component text, int x, int y, int color, int maxW, float scale) {
        int fitWidth = Math.max(1, (int) (maxW / scale));
        List<FormattedCharSequence> lines = this.font.split(text, fitWidth);
        if (lines.isEmpty()) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.drawString(this.font, lines.get(0), 0, 0, color, false);
        graphics.pose().popPose();
    }

    private void drawFriendshipRow(GuiGraphics graphics) {
        int friendship = Math.max(0, Math.min(1000, this.menu.getFriendship()));
        int heartPoints = friendship / 100;

        int x = heartStartX;
        // Use uniform scaling to avoid edge artifacts (thin bottom line) on heart sprites.
        float heartScale = 5.0f * UI_SCALE;
        for (int i = 0; i < 5; i++) {
            drawScaledPatch(graphics, HEART_EMPTY, x, heartY, heartScale, 7, 6, 7, 6);
            int need = (i + 1) * 2;
            if (heartPoints >= need) {
                drawScaledPatch(graphics, HEART_HALF_BASE, x, heartY, heartScale, 7, 6, 7, 6);
            } else if (heartPoints == need - 1) {
                drawScaledPatch(graphics, HEART_HALF_BASE, x, heartY, heartScale, 7, 6, 7, 6);
                drawScaledPatch(graphics, HEART_HALF_FILL, x, heartY, heartScale, 4, 6, 4, 6);
            }
            x += si(FIG_HEART_SPACING);
        }
    }

    private void drawGoldenCrackerIndicator(GuiGraphics graphics) {
        if (!this.menu.hasEatenAnimalCracker()) {
            return;
        }
        drawScaledIcon(graphics, GOLDEN_CRACKER_ICON, heartStartX + si(240), heartY + si(11), 1.2f * UI_SCALE, 16, 16, 16, 16);
    }

    private void drawActionRow(GuiGraphics graphics, int mouseX, int mouseY) {
        int[] centers = computeActionCenters();
        if (centers.length == 0) {
            return;
        }
        int drawY = actionY + actionAnimOffsetY;

        boolean hoverSell = inside(mouseX, mouseY, centers[0] - actionSize / 2, drawY - actionSize / 2, actionSize, actionSize);
        this.sellScale = approach(this.sellScale, hoverSell ? 3.3f * UI_SCALE : 3.125f * UI_SCALE);
        drawScaledIcon(graphics, SELL_ICON, centers[0], drawY, this.sellScale, 16, 16, 16, 16);

        if (centers.length > 1) {
            boolean hoverMove = inside(mouseX, mouseY, centers[1] - actionSize / 2, drawY - actionSize / 2, actionSize, actionSize);
            this.moveScale = approach(this.moveScale, hoverMove ? 3.3f * UI_SCALE : 3.125f * UI_SCALE);
            drawScaledIcon(graphics, MOVE_ICON, centers[1], drawY, this.moveScale, 16, 16, 16, 16);
        }

        if (this.menu.canToggleReproduction()) {
            ResourceLocation repro = this.menu.allowReproduction() ? REPRO_ON_ICON : REPRO_OFF_ICON;
            boolean hoverRepro = inside(mouseX, mouseY, centers[2] - actionSize / 2, drawY - actionSize / 2, actionSize, actionSize);
            boolean hoverOk = inside(mouseX, mouseY, centers[3] - actionSize / 2, drawY - actionSize / 2, actionSize, actionSize);
            this.reproScale = approach(this.reproScale, hoverRepro ? 3.3f * UI_SCALE : 3.125f * UI_SCALE);
            this.okScale = approach(this.okScale, hoverOk ? 3.3f * UI_SCALE : 3.125f * UI_SCALE);
            drawScaledIcon(graphics, repro, centers[2], drawY, this.reproScale, 16, 16, 16, 16);
            drawScaledIcon(graphics, OK_ICON, centers[3], drawY, this.okScale, 16, 16, 16, 16);
        } else {
            boolean hoverOk = inside(mouseX, mouseY, centers[2] - actionSize / 2, drawY - actionSize / 2, actionSize, actionSize);
            this.okScale = approach(this.okScale, hoverOk ? 3.3f * UI_SCALE : 3.125f * UI_SCALE);
            drawScaledIcon(graphics, OK_ICON, centers[2], drawY, this.okScale, 16, 16, 16, 16);
        }
    }

    private int[] computeActionCenters() {
        int tweak = si(8);
        int[] full = new int[]{
            actionStartX,
            actionStartX + actionSpacing,
            actionStartX + actionSpacing * 2 + tweak,
            actionStartX + actionSpacing * 3 + tweak
        };

        if (this.menu.canToggleReproduction()) {
            return full;
        }

        int left = full[0];
        int right = full[3];
        int mid = (left + right) / 2 + si(4);
        return new int[]{left, mid, right};
    }

    private void drawConfirmSellDialog(GuiGraphics graphics, int mouseX, int mouseY) {
        int boxW = si(320);
        int boxH = si(154);
        int boxX = panelX + (BASE_WIDTH - boxW) / 2;
        int boxY = panelY + (BASE_HEIGHT - boxH) / 2;

        graphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xF11A1B20);

        Component confirm = Component.translatable("stardewcraft.animal.query.confirm_sell");
        int tx = boxX + (boxW - this.font.width(confirm)) / 2;
        graphics.drawString(this.font, confirm, tx, boxY + si(42), COLOR_TEXT_MAIN, false);

        int yesX = boxX + boxW / 2 - si(40);
        int noX = boxX + boxW / 2 + si(40);
        int btnY = boxY + si(104);

        boolean hoverYes = inside(mouseX, mouseY, yesX - si(24), btnY - si(24), si(48), si(48));
        boolean hoverNo = inside(mouseX, mouseY, noX - si(24), btnY - si(24), si(48), si(48));
        yesScale = approach(yesScale, hoverYes ? 3.2f * UI_SCALE : 3.05f * UI_SCALE);
        noScale = approach(noScale, hoverNo ? 3.2f * UI_SCALE : 3.05f * UI_SCALE);

        drawScaledIcon(graphics, OK_ICON, yesX, btnY, yesScale, 16, 16, 16, 16);
        drawScaledIcon(graphics, NO_ICON, noX, btnY, noScale, 16, 16, 16, 16);
    }

    private boolean handleConfirmDialogClick(int mx, int my) {
        int boxW = si(320);
        int boxH = si(154);
        int boxX = panelX + (BASE_WIDTH - boxW) / 2;
        int boxY = panelY + (BASE_HEIGHT - boxH) / 2;
        int yesX = boxX + boxW / 2 - si(40);
        int noX = boxX + boxW / 2 + si(40);
        int btnY = boxY + si(104);

        if (inside(mx, my, yesX - si(24), btnY - si(24), si(48), si(48))) {
            playUi(ModSounds.NEW_RECIPE.get(), 0.9f, 1.0f);
            playUi(ModSounds.MONEY.get(), 0.9f, 1.0f);
            PacketDistributor.sendToServer(new AnimalQueryActionPayload(AnimalQueryActionPayload.Action.SELL, false));
            this.onClose();
            return true;
        }

        if (inside(mx, my, noX - si(24), btnY - si(24), si(48), si(48))) {
            confirmingSell = false;
            playUi(ModSounds.SMALL_SELECT.get(), 0.8f, 1.0f);
            return true;
        }
        return false;
    }

    private void renderQuickTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int[] centers = computeActionCenters();
        Component tip = null;

        int tooltipY = actionY + actionAnimOffsetY;
        if (centers.length > 0 && inside(mouseX, mouseY, centers[0] - actionSize / 2, tooltipY - actionSize / 2, actionSize, actionSize)) {
            tip = Component.translatable("stardewcraft.animal.query.hover.sell", this.menu.getEstimatedSellPrice());
        } else if (centers.length > 1 && inside(mouseX, mouseY, centers[1] - actionSize / 2, tooltipY - actionSize / 2, actionSize, actionSize)) {
            tip = Component.translatable("stardewcraft.animal.query.hover.move");
        } else if (this.menu.canToggleReproduction() && centers.length > 2 && inside(mouseX, mouseY, centers[2] - actionSize / 2, tooltipY - actionSize / 2, actionSize, actionSize)) {
            tip = Component.translatable("stardewcraft.animal.query.hover.repro");
        } else if ((!this.menu.canToggleReproduction() && centers.length > 2 && inside(mouseX, mouseY, centers[2] - actionSize / 2, tooltipY - actionSize / 2, actionSize, actionSize))
            || (this.menu.canToggleReproduction() && centers.length > 3 && inside(mouseX, mouseY, centers[3] - actionSize / 2, tooltipY - actionSize / 2, actionSize, actionSize))) {
            tip = Component.translatable("gui.done");
        }

        if (tip != null) {
            graphics.renderTooltip(this.font, tip, mouseX, mouseY);
        }
    }

    private void computeLayout() {
        int scaledW = si(BASE_WIDTH);
        int scaledH = si(BASE_HEIGHT);
        this.panelX = (this.width - scaledW) / 2;
        this.panelY = (this.height - scaledH) / 2;

        this.portraitCX = panelX + si(FIG_PORTRAIT_X + FIG_PORTRAIT_SIZE / 2);
        this.portraitCY = panelY + si(FIG_PORTRAIT_Y + FIG_PORTRAIT_SIZE / 2);

        this.nameX = panelX + si(FIG_NAME_X);
        this.nameY = panelY + si(FIG_NAME_Y);
        this.renameY = panelY + si(FIG_RENAME_Y + FIG_RENAME_SIZE / 2);
        this.renameSize = si(FIG_RENAME_SIZE);

        this.infoX = panelX + si(FIG_NAME_X);
        this.infoY = panelY + si(84);
        this.infoW = si(221);

        this.heartStartX = panelX + si(FIG_HEART_X);
        this.heartY = panelY + si(FIG_HEART_Y);

        this.actionStartX = panelX + si(FIG_ACTION_X + FIG_ACTION_SIZE / 2 + 14);
        this.actionY = panelY + si(FIG_ACTION_Y + FIG_ACTION_SIZE / 2);
        this.actionSpacing = si(FIG_ACTION_SPACING);
        this.actionSize = si(FIG_ACTION_SIZE);

        this.renameCenterX = getDynamicRenameCenterX();
    }

    private void setEditingName(boolean editing) {
        this.editingName = editing;
        if (editing) {
            this.editBuffer = this.lastSubmittedName;
            this.editCursor = this.editBuffer.length();
            this.renameCenterX = getDynamicRenameCenterX();
            this.setFocused(null);
        } else {
            this.setFocused(null);
        }
    }

    private void commitNameAndCloseEdit() {
        submitRenameIfChanged();
        setEditingName(false);
    }

    private String getCurrentName() {
        if (this.editingName) {
            return this.editBuffer;
        }
        return this.lastSubmittedName;
    }

    private void submitRenameIfChanged() {
        String normalized = this.editBuffer.trim();
        if (normalized.isBlank()) {
            normalized = this.lastSubmittedName;
            this.editBuffer = normalized;
            this.editCursor = this.editBuffer.length();
        }
        if (normalized.equals(this.lastSubmittedName)) {
            return;
        }
        this.lastSubmittedName = normalized;
        this.renameCenterX = getDynamicRenameCenterX();
        PacketDistributor.sendToServer(new AnimalRenamePayload(this.menu.getAnimalId(), normalized));
    }

    private int getDynamicRenameCenterX() {
        String name = getCurrentName();
        float nameScale = (FIG_NAME_FONT_SIZE / 9.0f) * UI_SCALE;
        int nameWidthInFontPx = Math.max(1, (int) (si(FIG_NAME_W - 8) / nameScale));
        String shown = this.font.plainSubstrByWidth(name, nameWidthInFontPx);
        int textWidthPx = Math.round(this.font.width(shown) * nameScale);
        int minCenter = nameX + si(24);
        int maxCenter = nameX + si(FIG_NAME_W) - si(24);
        int desired = nameX + textWidthPx + si(24);
        return Math.max(minCenter, Math.min(maxCenter, desired));
    }

    private void updateHoverState(int mouseX, int mouseY) {
        int current = detectHoverHotspot(mouseX, mouseY);
        if (current != this.hoverHotspot) {
            this.hoverHotspot = current;
            if (current != 0) {
                playUi(ModSounds.SMALL_SELECT.get(), 0.45f, 1.18f);
            }
        }
    }

    private int detectHoverHotspot(int mouseX, int mouseY) {
        if (this.confirmingSell) {
            int boxW = si(320);
            int boxH = si(154);
            int boxX = panelX + (BASE_WIDTH - boxW) / 2;
            int boxY = panelY + (BASE_HEIGHT - boxH) / 2;
            int yesX = boxX + boxW / 2 - si(40);
            int noX = boxX + boxW / 2 + si(40);
            int btnY = boxY + si(104);
            if (inside(mouseX, mouseY, yesX - si(24), btnY - si(24), si(48), si(48))) {
                return 101;
            }
            if (inside(mouseX, mouseY, noX - si(24), btnY - si(24), si(48), si(48))) {
                return 102;
            }
            return 0;
        }

        if (inside(mouseX, mouseY, nameX, nameY, si(FIG_NAME_W), si(FIG_NAME_H))) {
            return 1;
        }
        int dynamicRenameX = getDynamicRenameCenterX();
        if (inside(mouseX, mouseY, dynamicRenameX - renameSize / 2, renameY - renameSize / 2, renameSize, renameSize)) {
            return 2;
        }

        int[] centers = computeActionCenters();
        int hitY = actionY + actionAnimOffsetY;
        if (centers.length > 0 && inside(mouseX, mouseY, centers[0] - actionSize / 2, hitY - actionSize / 2, actionSize, actionSize)) {
            return 10;
        }
        if (centers.length > 1 && inside(mouseX, mouseY, centers[1] - actionSize / 2, hitY - actionSize / 2, actionSize, actionSize)) {
            return 11;
        }
        if (this.menu.canToggleReproduction()) {
            if (centers.length > 2 && inside(mouseX, mouseY, centers[2] - actionSize / 2, hitY - actionSize / 2, actionSize, actionSize)) {
                return 12;
            }
            if (centers.length > 3 && inside(mouseX, mouseY, centers[3] - actionSize / 2, hitY - actionSize / 2, actionSize, actionSize)) {
                return 13;
            }
        } else if (centers.length > 2 && inside(mouseX, mouseY, centers[2] - actionSize / 2, hitY - actionSize / 2, actionSize, actionSize)) {
            return 13;
        }
        return 0;
    }

    private void updateEntryAnimationState() {
        float ease = getEntryEase();
        this.portraitAnimOffsetY = Math.round((1.0f - ease) * si(44));
        this.actionAnimOffsetY = Math.round((1.0f - ease) * si(34));
    }

    private float getEntryEase() {
        long elapsed = Math.max(0L, System.currentTimeMillis() - this.openedAtMs);
        float t = Math.min(1.0f, elapsed / (float) ENTRY_ANIM_MS);
        float inv = 1.0f - t;
        return 1.0f - inv * inv * inv;
    }

    private ResourceLocation resolveAnimalIcon() {
        return switch (this.menu.getVariantIndex()) {
            case 0 -> ICON_WHITE_CHICKEN;
            case 1 -> ICON_GOLDEN_CHICKEN;
            case 2 -> ICON_DUCK;
            case 3 -> ICON_VOID_CHICKEN;
            case 4 -> ICON_RABBIT;
            case 5 -> ICON_OSTRICH;
            case 6 -> ICON_DINOSAUR;
            case 7 -> ICON_COW;
            case 8 -> ICON_GOAT;
            case 9, 10 -> ICON_SHEEP;
            case 11 -> ICON_PIG;
            default -> ICON_WHITE_CHICKEN;
        };
    }

    private void drawScaledIcon(GuiGraphics graphics, ResourceLocation texture, int centerX, int centerY, float scale, int srcW, int srcH, int texW, int texH) {
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.blit(texture, -srcW / 2, -srcH / 2, 0, 0, srcW, srcH, texW, texH);
        graphics.pose().popPose();
    }

    private void drawScaledPatch(GuiGraphics graphics, ResourceLocation texture, int x, int y, float scale, int srcW, int srcH, int texW, int texH) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.blit(texture, 0, 0, 0, 0, srcW, srcH, texW, texH);
        graphics.pose().popPose();
    }

    private boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private int si(int value) {
        return Math.max(1, Math.round(value * UI_SCALE));
    }

    private float approach(float current, float target) {
        if (current < target) {
            return Math.min(target, current + 0.06f);
        }
        if (current > target) {
            return Math.max(target, current - 0.06f);
        }
        return current;
    }

    private void drawFilledCircle(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int span = (int) Math.floor(Math.sqrt((double) radius * radius - (double) dy * dy));
            graphics.fill(centerX - span, centerY + dy, centerX + span + 1, centerY + dy + 1, color);
        }
    }

    private void playUi(SoundEvent event, float volume, float pitch) {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.playSound(event, volume, pitch);
        }
    }
}

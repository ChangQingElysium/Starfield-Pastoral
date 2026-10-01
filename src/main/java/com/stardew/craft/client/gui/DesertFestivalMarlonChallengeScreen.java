package com.stardew.craft.client.gui;

import com.stardew.craft.client.font.StardewFonts;

import com.stardew.craft.client.gui.common.StardewGuiViewport;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.network.payload.DesertFestivalMarlonChallengeChoicePayload;
import com.stardew.craft.network.payload.OpenDesertFestivalMarlonChallengesPayload;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@SuppressWarnings("null")
public class DesertFestivalMarlonChallengeScreen extends Screen implements com.stardew.craft.port.PortScreen {
    private static final ResourceLocation BOARD_BACKGROUND = new ResourceLocation(
        StardewCraft.MODID, "textures/gui/desert_festival/special_orders_board.png");
    private static final int BOARD_W = 338;
    private static final int BOARD_H = 198;
    private static final int BOARD_SDV_W = BOARD_W * 4;
    private static final int BOARD_SDV_H = BOARD_H * 4;
    private static final int ORDER_SDV_W = 512;
    private static final int TEXT_COLOR = 0xFF3F2A13;
    private static final int FADED_TEXT_COLOR = 0x663F2A13;
    private static final int REWARD_COLOR = 0xFF6A420D;
    private static final int CLOSE_W = 12;
    private static final int CLOSE_H = 12;

    private final List<OpenDesertFestivalMarlonChallengesPayload.ChallengeEntry> entries;
    private final String activeChallengeId;
    private final boolean activeRewardClaimed;
    private String locallyAcceptedId = "";

    private int windowX;
    private int windowY;
    private int windowW;
    private int windowH;
    private float scale;
    private float guiScale;
    private int boardSdvX;
    private int boardSdvY;
    private int closeX;
    private int closeY;
    private int closeW;
    private int closeH;

    public DesertFestivalMarlonChallengeScreen(OpenDesertFestivalMarlonChallengesPayload payload) {
        super(Component.translatable("stardewcraft.desert_festival.marlon.challenge.board_title"));
        this.entries = payload.entries();
        this.activeChallengeId = payload.activeChallengeId();
        this.activeRewardClaimed = payload.activeRewardClaimed();
        playSound(ModSounds.BIG_SELECT.get());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        guiScale = (float) StardewGuiViewport.REFERENCE_SCALE;
        scale = 4.0f / guiScale;
        int sdvViewportW = Math.round(width * guiScale);
        int sdvViewportH = Math.round(height * guiScale);
        boardSdvX = sdvViewportW / 2 - BOARD_SDV_W / 2;
        boardSdvY = sdvViewportH / 2 - BOARD_SDV_H / 2;
        windowX = px(boardSdvX);
        windowY = px(boardSdvY);
        windowW = px(BOARD_SDV_W);
        windowH = px(BOARD_SDV_H);
        closeW = px(CLOSE_W * 4);
        closeH = px(CLOSE_H * 4);
        closeX = px(boardSdvX + BOARD_SDV_W - 20);
        closeY = px(boardSdvY);
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xBF000000);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.blit(BOARD_BACKGROUND, windowX, windowY, windowW, windowH, 0.0f, 0.0f, BOARD_W, BOARD_H, BOARD_W, BOARD_H);

        Component chooseOne = activeId().isBlank()
            ? Component.translatable("stardewcraft.desert_festival.marlon.challenge.choose_one")
            : Component.translatable(activeRewardClaimed
                ? "stardewcraft.desert_festival.marlon.challenge.board_complete"
                : "stardewcraft.desert_festival.marlon.challenge.board_chosen");
        drawScaledCenteredString(graphics, chooseOne, boardSdvX + BOARD_SDV_W / 2, Math.max(10, boardSdvY - 70), 0xFFFFD76A, 1.0F, true);

        int leftX = boardSdvX + 96;
        int rightX = boardSdvX + 736;
        if (!entries.isEmpty()) {
            renderOrder(graphics, entries.get(0), 0, leftX, mouseX, mouseY);
        }
        if (entries.size() > 1) {
            renderOrder(graphics, entries.get(1), 1, rightX, mouseX, mouseY);
        }

        CommonGuiTextures.drawCloseButton(graphics, closeX, closeY, scale);
    }

    private void renderOrder(GuiGraphics graphics, OpenDesertFestivalMarlonChallengesPayload.ChallengeEntry entry,
                             int slot, int x, int mouseX, int mouseY) {
        boolean selected = entry.challengeId().equals(activeId());
        boolean dehighlight = !activeId().isBlank() && !selected;
        float alpha = dehighlight ? 0.25F : 1.0F;
        int textColor = dehighlight ? FADED_TEXT_COLOR : TEXT_COLOR;

        int headerY = boardSdvY + 128;
        int portraitSize = px(36);
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        graphics.blit(new ResourceLocation(StardewCraft.MODID, "textures/mugshots/marlon.png"),
            px(x), px(headerY), portraitSize, portraitSize, 0.0f, 0.0f, 16, 16, 16, 24);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        Component title = Component.translatable(entry.titleKey()).withStyle(net.minecraft.ChatFormatting.BOLD);
        drawScaledCenteredString(graphics, title, x + 256, headerY, textColor, 1.0F, !dehighlight);

        int textX = x;
        int textY = boardSdvY + 192;
        Component description = description(entry);
        float textScale = fittingDescriptionScale(description);
        List<FormattedCharSequence> lines = splitSdv(description, ORDER_SDV_W, textScale);
        int maxLines = Math.min(lines.size(), Math.max(1, (int)((boardSdvY + 552 - textY) / lineStepSdv(textScale))));
        for (int i = 0; i < maxLines; i++) {
            drawScaledString(graphics, lines.get(i), textX, textY, textColor, textScale, !dehighlight);
            textY += lineStepSdv(textScale);
        }

        int dueY = boardSdvY + 576;
        CommonGuiTextures.drawQuestTimed(graphics, px(textX), px(dueY), scale);
        drawScaledString(graphics, Component.translatable("stardewcraft.desert_festival.marlon.challenge.one_day"),
            textX + 48, dueY, textColor, 1.0F, !dehighlight);
        renderReward(graphics, entry, x, dueY, alpha);

        if (activeId().isBlank()) {
            renderAcceptButton(graphics, entry, slot, mouseX, mouseY);
        }
    }

    private void renderAcceptButton(GuiGraphics graphics, OpenDesertFestivalMarlonChallengesPayload.ChallengeEntry entry,
                                    int slot, int mouseX, int mouseY) {
        Component accept = Component.translatable("gui.stardewcraft.billboard.accept").withStyle(net.minecraft.ChatFormatting.BOLD);
        int buttonX = acceptButtonSdvX(slot);
        int buttonY = acceptButtonSdvY();
        int buttonW = acceptButtonSdvW(accept);
        int buttonH = acceptButtonSdvH();
        boolean hover = isIn(mouseX, mouseY, px(buttonX), px(buttonY), px(buttonW), px(buttonH));
        if (hover) {
            graphics.setColor(1.0F, 0.72F, 0.78F, 1.0F);
        }
        CommonGuiTextures.drawBillboardAcceptBox(graphics, px(buttonX), px(buttonY), px(buttonW), px(buttonH), scale);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        drawScaledString(graphics, accept, buttonX + 12, buttonY + 12, TEXT_COLOR, 1.0F, true);
    }

    private void renderReward(GuiGraphics graphics, OpenDesertFestivalMarlonChallengesPayload.ChallengeEntry entry,
                              int orderX, int dueY, float alpha) {
        Component amount = Component.literal(String.valueOf(entry.rewardEggs())).withStyle(net.minecraft.ChatFormatting.BOLD);
        int countWidthSdv = Math.round(font.width(amount) * 4.0F);
        int itemX = orderX + ORDER_SDV_W - countWidthSdv - 72;
        int itemY = dueY - 8;
        CommonGuiTextures.drawItemTint(graphics, new ItemStack(ModItems.CALICO_EGG.get()), px(itemX), px(itemY), scale, 1.0F, 1.0F, 1.0F, alpha);
        drawScaledString(graphics, amount, orderX + ORDER_SDV_W - countWidthSdv - 4, dueY,
            alpha < 1.0F ? FADED_TEXT_COLOR : REWARD_COLOR, 1.0F, alpha >= 1.0F);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int mx = (int) mouseX;
        int my = (int) mouseY;
        if (isIn(mx, my, closeX, closeY, closeW, closeH)) {
            playSound(ModSounds.BIG_DESELECT.get());
            onClose();
            return true;
        }
        if (activeId().isBlank()) {
            for (int i = 0; i < entries.size() && i < 2; i++) {
                OpenDesertFestivalMarlonChallengesPayload.ChallengeEntry entry = entries.get(i);
                Component accept = Component.translatable("gui.stardewcraft.billboard.accept").withStyle(net.minecraft.ChatFormatting.BOLD);
                int buttonX = acceptButtonSdvX(i);
                int buttonY = acceptButtonSdvY();
                int buttonW = acceptButtonSdvW(accept);
                int buttonH = acceptButtonSdvH();
                if (isIn(mx, my, px(buttonX), px(buttonY), px(buttonW), px(buttonH))) {
                    locallyAcceptedId = entry.challengeId();
                    playSound(ModSounds.NEW_ARTIFACT.get());
                    PacketDistributor.sendToServer(new DesertFestivalMarlonChallengeChoicePayload(entry.challengeId(), false));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private Component description(OpenDesertFestivalMarlonChallengesPayload.ChallengeEntry entry) {
        if (entry.targetKey().isBlank()) {
            return Component.translatable(entry.textKey(), entry.targetCount());
        }
        return Component.translatable(entry.textKey(), entry.targetCount(), Component.translatable(entry.targetKey()));
    }

    private String activeId() {
        if (!locallyAcceptedId.isBlank()) return locallyAcceptedId;
        return activeChallengeId == null ? "" : activeChallengeId;
    }

    private boolean isIn(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private int acceptButtonSdvX(int slot) {
        return boardSdvX + (slot == 0 ? BOARD_SDV_W / 4 : BOARD_SDV_W * 3 / 4) - 128;
    }

    private int acceptButtonSdvY() {
        return boardSdvY + BOARD_SDV_H - 128;
    }

    private int acceptButtonSdvW(Component label) {
        return Math.max(112, Math.round(font.width(label) * 4.0F) + 24);
    }

    private int acceptButtonSdvH() {
        return Math.round(StardewFonts.lineHeight(font) * 4.0F) + 24;
    }

    private float fittingDescriptionScale(Component description) {
        float textScale = 1.0F;
        while (textScale > 0.5F) {
            List<FormattedCharSequence> lines = splitSdv(description, ORDER_SDV_W, textScale);
            int height = lines.size() * lineStepSdv(textScale);
            if (height <= 360) {
                return textScale;
            }
            textScale -= 0.05F;
        }
        return textScale;
    }

    private List<FormattedCharSequence> splitSdv(Component text, int sdvWidth, float textScale) {
        int wrappedWidth = Math.max(20, Math.round(sdvWidth / (4.0F * textScale)));
        return font.split(text, wrappedWidth);
    }

    private int lineStepSdv(float textScale) {
        return Math.round(StardewFonts.lineHeight(font) * 4.0F * textScale + 8.0F);
    }

    private int px(int sdvPixels) {
        return Math.round(sdvPixels / guiScale);
    }

    private float px(float sdvPixels) {
        return sdvPixels / guiScale;
    }

    private void drawScaledString(GuiGraphics graphics, Component text, int sdvX, int sdvY, int color, float textScale, boolean shadow) {
        drawScaledString(graphics, text.getVisualOrderText(), sdvX, sdvY, color, textScale, shadow);
    }

    private void drawScaledString(GuiGraphics graphics, FormattedCharSequence text, int sdvX, int sdvY, int color, float textScale, boolean shadow) {
        float drawScale = scale * textScale;
        graphics.pose().pushPose();
        graphics.pose().scale(drawScale, drawScale, 1.0F);
        graphics.drawString(font, text, Math.round(px((float)sdvX) / drawScale), Math.round(px((float)sdvY) / drawScale), color, shadow);
        graphics.pose().popPose();
    }

    private void drawScaledCenteredString(GuiGraphics graphics, Component text, int centerSdvX, int sdvY, int color, float textScale, boolean shadow) {
        FormattedCharSequence sequence = text.getVisualOrderText();
        float drawScale = scale * textScale;
        float x = px((float)centerSdvX) - font.width(sequence) * drawScale / 2.0F;
        graphics.pose().pushPose();
        graphics.pose().scale(drawScale, drawScale, 1.0F);
        graphics.drawString(font, sequence, Math.round(x / drawScale), Math.round(px((float)sdvY) / drawScale), color, shadow);
        graphics.pose().popPose();
    }

    private void playSound(SoundEvent sound) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null && sound != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0F, 1.0F));
        }
    }
}
package com.stardew.craft.client.gui.mastery;

import com.mojang.math.Axis;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.ClientPlayerDataCache;
import com.stardew.craft.client.font.StardewFonts;
import com.stardew.craft.client.gui.overnight.StardewGuiUtil;
import com.stardew.craft.mastery.MasteryProgress;
import com.stardew.craft.mastery.MasteryRewardRegistry;
import com.stardew.craft.network.payload.RequestClaimMasteryRewardPayload;
import com.stardew.craft.player.SkillType;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredHolder;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * SDV 1.6 MasteryTrackerMenu parity screen.
 * Coordinates are held in SDV game pixels and converted through ui()/s4().
 */
public final class MasteryTrackerMenuScreen extends Screen {
    private static final ResourceLocation PANEL_BOX = mastery("panel_box_21");
    private static final ResourceLocation EXP_BOX = mastery("exp_box_15");
    private static final ResourceLocation CORNER_ORNAMENT = mastery("corner_ornament");
    private static final ResourceLocation CLAIM_NORMAL = mastery("claim_button_normal");
    private static final ResourceLocation CLAIM_HOVER = mastery("claim_button_hover");
    private static final ResourceLocation CLAIM_PRESSED = mastery("claim_button_pressed");
    private static final ResourceLocation CANDLE_UNLIT = mastery("candle_unlit");
    private static final ResourceLocation CANDLE_LIT = mastery("candle_lit");
    private static final ResourceLocation PLAQUE_ICON = mastery("mastery_plaque_icon");
    private static final ResourceLocation RECIPE_OVERLAY = mastery("recipe_overlay");
    private static final ResourceLocation[] CANDLE_AVAILABLE = new ResourceLocation[] {
        mastery("candle_available_0"),
        mastery("candle_available_1"),
        mastery("candle_available_2"),
        mastery("candle_available_3"),
        mastery("candle_available_4"),
        mastery("candle_available_5")
    };

    private static final int SDV_WIDTH = 800;
    private static final int SDV_BASE_HEIGHT = 320;
    private static final int PANEL_SRC = 21;
    private static final int EXP_SRC = 15;
    private static final int ORNAMENT_W = 23;
    private static final int ORNAMENT_H = 23;
    private static final int CLAIM_W = 42;
    private static final int CLAIM_H = 21;
    private static final int CANDLE_W = 10;
    private static final int CANDLE_H = 11;
    private static final int PLAQUE_W = 17;
    private static final int PLAQUE_H = 16;
    private static final int RECIPE_W = 16;
    private static final int RECIPE_H = 16;

    private final int whichSkill;
    private final List<MasteryRewardRegistry.RewardEntry> rewards;
    private final List<Integer> rowHeights = new ArrayList<>();

    private float guiScale = 1f;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int contentTop;
    private int contentBottom;
    private int contentHeight;
    private int scrollOffset;
    private int maxScroll;
    private Rect2i claimBtn;
    private Rect2i closeBtn;
    private boolean claimHover;
    private boolean wasClaimHover;
    private long pressedAtMs;
    private boolean secondClaimSoundPlayed;

    private MasteryTrackerMenuScreen(int whichSkill) {
        super(titleFor(whichSkill));
        this.whichSkill = whichSkill;
        SkillType skill = whichSkill < 0 ? null : SkillType.fromId(whichSkill);
        this.rewards = skill == null ? List.of() : MasteryRewardRegistry.rewardsFor(skill);
    }

    public static void open(int whichSkill) {
        Minecraft.getInstance().setScreen(new MasteryTrackerMenuScreen(whichSkill));
    }

    private static Component titleFor(int whichSkill) {
        if (whichSkill < 0) {
            return Component.translatable("stardewcraft.mastery.menu.overview");
        }
        SkillType skill = SkillType.fromId(whichSkill);
        return Component.translatable("stardewcraft.mastery.menu." + (skill == null ? "overview" : skill.name().toLowerCase()));
    }

    @Override
    public void renderBackground(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x99000000);
    }

    @Override
    protected void init() {
        super.init();
        Minecraft mc = Minecraft.getInstance();
        guiScale = (float) mc.getWindow().getGuiScale();

        panelW = ui(SDV_WIDTH);
        rowHeights.clear();
        scrollOffset = 0;
        maxScroll = 0;

        int wantedHeight = ui(SDV_BASE_HEIGHT);
        if (whichSkill >= 0) {
            int maxTextWidth = Math.max(1, panelW - ui(200));
            contentHeight = 0;
            for (MasteryRewardRegistry.RewardEntry entry : rewards) {
                Component label = Component.translatable(entry.descKey());
                int textHeight = wrappedHeight(label, maxTextWidth);
                if (!Component.translatable(entry.nameKey()).getString().isEmpty()) {
                    textHeight += ui(48);
                }
                int rowHeight = Math.max(ui(80), textHeight + ui(12));
                rowHeights.add(rowHeight);
                contentHeight += rowHeight;
            }
            int originalLikeHeight = ui(SDV_BASE_HEIGHT) + contentHeight - ui(112);
            wantedHeight = Math.max(ui(256), originalLikeHeight);
        }

        int screenMargin = ui(32);
        panelH = Math.min(wantedHeight, Math.max(ui(240), this.height - screenMargin * 2));
        panelX = this.width / 2 - panelW / 2;
        panelY = Math.max(screenMargin, this.height / 2 - panelH / 2);

        if (whichSkill >= 0) {
            int btnW = ui(168);
            int btnH = ui(80);
            claimBtn = new Rect2i(panelX + panelW / 2 - btnW / 2, panelY + panelH - ui(112), btnW, btnH);
            contentTop = panelY + ui(144);
            contentBottom = Math.max(contentTop, claimBtn.getY() - ui(16));
            int visibleContent = Math.max(1, contentBottom - contentTop);
            maxScroll = Math.max(0, contentHeight - visibleContent);
        } else {
            claimBtn = null;
            contentTop = panelY;
            contentBottom = panelY + panelH;
        }

        int closeSz = ui(44);
        closeBtn = new Rect2i(panelX + panelW - closeSz - ui(8), panelY - closeSz / 2, closeSz, closeSz);
        playOpenSound();
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        float scale = s4();

        StardewGuiUtil.drawTextureBox(graphics, PANEL_BOX, PANEL_SRC, PANEL_SRC,
            0, 0, PANEL_SRC, PANEL_SRC, panelX, panelY, panelW, panelH, scale, true);
        drawCornerOrnaments(graphics, scale);

        if (whichSkill < 0) {
            drawOverview(graphics);
        } else {
            drawSkillPage(graphics, mouseX, mouseY, scale);
        }

        drawCloseButton(graphics);
    }

    private void drawCornerOrnaments(GuiGraphics graphics, float scale) {
        drawSprite(graphics, CORNER_ORNAMENT, panelX + ui(24), panelY + ui(28), ORNAMENT_W, ORNAMENT_H, scale, 0f, 1f);
        drawSprite(graphics, CORNER_ORNAMENT, panelX + ui(24), panelY + panelH - ui(24), ORNAMENT_W, ORNAMENT_H, scale, 270f, 1f);
        drawSprite(graphics, CORNER_ORNAMENT, panelX + panelW - ui(24), panelY + ui(28), ORNAMENT_W, ORNAMENT_H, scale, 90f, 1f);
        drawSprite(graphics, CORNER_ORNAMENT, panelX + panelW - ui(24), panelY + panelH - ui(24), ORNAMENT_W, ORNAMENT_H, scale, 180f, 1f);
    }

    private void drawOverview(GuiGraphics graphics) {
        Component title = StardewFonts.title(
                Component.translatable("stardewcraft.mastery.menu.overview"));
        graphics.drawString(this.font, title, panelX + panelW / 2 - this.font.width(title) / 2, panelY + ui(48), 0x000000, false);

        StardewGuiUtil.drawTextureBox(graphics, EXP_BOX, EXP_SRC, EXP_SRC,
            0, 0, EXP_SRC, EXP_SRC, panelX + ui(100), panelY + ui(128), ui(600), ui(64), s4(), false);
        drawExpBar(graphics);

        long now = System.currentTimeMillis();
        int levels = MasteryProgress.currentLevel(ClientPlayerDataCache.getMasteryExp());
        int unspent = Math.max(0, levels - ClientPlayerDataCache.getMasteryLevelsSpent());
        int candleY = panelY + ui(220);
        int centerX = panelX + panelW / 2;
        for (int i = 0; i < MasteryProgress.MAX_LEVEL; i++) {
            int x = centerX - ui(110) + i * ui(44);
            ResourceLocation texture;
            if (i >= levels - unspent && i < levels) {
                texture = CANDLE_AVAILABLE[(int) ((now % 600) / 100)];
            } else if (i < levels) {
                texture = CANDLE_LIT;
            } else {
                texture = CANDLE_UNLIT;
            }
            drawSprite(graphics, texture, x, candleY, CANDLE_W, CANDLE_H, s4(), 0f, 1f);
        }
    }

    private void drawExpBar(GuiGraphics graphics) {
        long masteryExp = ClientPlayerDataCache.getMasteryExp();
        int levelsAchieved = MasteryProgress.currentLevel(masteryExp);
        long curIntoLevel = masteryExp - MasteryProgress.expForLevel(levelsAchieved);
        long levelSpan = MasteryProgress.expForLevel(levelsAchieved + 1) - MasteryProgress.expForLevel(levelsAchieved);
        if (levelSpan <= 0) {
            levelSpan = 1;
        }

        int barFillSdv = levelsAchieved >= MasteryProgress.MAX_LEVEL
            ? 576
            : (int) (576f * curIntoLevel / levelSpan);
        if (levelsAchieved < MasteryProgress.MAX_LEVEL && barFillSdv <= 0) {
            return;
        }

        int light;
        int med;
        int medDark;
        int dark;
        if (levelsAchieved >= MasteryProgress.MAX_LEVEL) {
            light = 0xFFDCDCDC;
            med = 0xFF8C8C8C;
            medDark = 0xFF505050;
            dark = med;
        } else {
            light = 0xFF3CB450;
            med = 0xFF00713E;
            medDark = 0xFF005032;
            dark = 0xFF003C1E;
        }

        int x = panelX + ui(112);
        int y = panelY + ui(144);
        int width = ui(barFillSdv);
        int h32 = ui(32);
        int h4 = Math.max(1, ui(4));
        int h28 = ui(28);

        graphics.fill(x, y, x + width, y + h32, med);
        graphics.fill(x, y + h4, x + h4, y + h4 + h28, medDark);

        if (barFillSdv > 8) {
            graphics.fill(x, y + ui(28), x + width - ui(8), y + ui(28) + h4, medDark);
            graphics.fill(x + ui(4), y, x + ui(4) + width - ui(4), y + h4, light);
            graphics.fill(x - ui(8) + width, y, x - ui(8) + width + h4, y + h28, light);
            graphics.fill(x - ui(4) + width, y, x - ui(4) + width + h4, y + h32, dark);
        }

        if (levelsAchieved < MasteryProgress.MAX_LEVEL) {
            String text = curIntoLevel + "/" + levelSpan;
            graphics.drawString(this.font, text, panelX + ui(112) + ui(288) - this.font.width(text) / 2, panelY + ui(146), 0xBFFFFFFF, false);
        }
    }

    private void drawSkillPage(GuiGraphics graphics, int mouseX, int mouseY, float scale) {
        Component title = StardewFonts.title(this.title);
        graphics.drawString(this.font, title, panelX + panelW / 2 - this.font.width(title) / 2, panelY + ui(48), 0x000000, false);

        graphics.enableScissor(panelX + ui(24), contentTop, panelX + panelW - ui(24), contentBottom);
        int rowY = contentTop - scrollOffset;
        for (int i = 0; i < rewards.size(); i++) {
            drawRewardRow(graphics, rewards.get(i), panelX + ui(40), rowY);
            rowY += rowHeights.get(i);
        }
        graphics.disableScissor();

        drawClaimButton(graphics, mouseX, mouseY, scale);
    }

    private void drawRewardRow(GuiGraphics graphics, MasteryRewardRegistry.RewardEntry entry, int iconX, int rowY) {
        int textX = iconX + ui(104);
        int maxTextWidth = Math.max(1, panelW - ui(200));
        ItemStack stack = entry.stack().get();

        if (!stack.isEmpty()) {
            drawItemScaled(graphics, stack, iconX, rowY);
        } else {
            drawSprite(graphics, PLAQUE_ICON, iconX, rowY, PLAQUE_W, PLAQUE_H, s4(), 0f, 1f);
        }

        if (entry.isRecipe()) {
            drawSprite(graphics, RECIPE_OVERLAY, iconX + ui(32), rowY + ui(32), RECIPE_W, RECIPE_H, 3f / guiScale, 0f, 1f);
        }

        Component name = Component.translatable(entry.nameKey());
        String nameText = name.getString();
        int descY = rowY;
        if (!nameText.isEmpty()) {
            graphics.drawString(this.font, name, textX, rowY, 0x000000, false);
            descY += ui(48);
        }
        int y = descY;
        for (FormattedCharSequence line : this.font.split(Component.translatable(entry.descKey()), maxTextWidth)) {
            graphics.drawString(this.font, line, textX, y, 0x000000, false);
            y += this.font.lineHeight + 2;
        }
    }

    private void drawClaimButton(GuiGraphics graphics, int mouseX, int mouseY, float scale) {
        if (claimBtn == null) {
            return;
        }

        int unspent = unspentForView();
        boolean enabled = unspent > 0;
        claimHover = enabled && claimBtn.contains(mouseX, mouseY);
        if (claimHover && !wasClaimHover && pressedAtMs == 0) {
            playSound(ModSounds.COWBOY_GUNSHOT);
        }
        wasClaimHover = claimHover;

        ResourceLocation texture = CLAIM_NORMAL;
        if (pressedAtMs > 0) {
            texture = CLAIM_PRESSED;
        } else if (claimHover) {
            texture = CLAIM_HOVER;
        }

        float alpha = enabled ? 1f : 0.5f;
        drawSprite(graphics, texture, claimBtn.getX(), claimBtn.getY(), CLAIM_W, CLAIM_H, scale, 0f, alpha);

        Component claimText = Component.translatable("stardewcraft.mastery.menu.claim");
        int tx = claimBtn.getX() + claimBtn.getWidth() / 2 - this.font.width(claimText) / 2;
        int ty = claimBtn.getY() + ui(24) + (pressedAtMs > 0 ? ui(8) : 0);
        graphics.drawString(this.font, claimText, tx, ty, enabled ? 0x000000 : 0x80000000, false);
    }

    private int unspentForView() {
        long exp = ClientPlayerDataCache.getMasteryExp();
        int spent = ClientPlayerDataCache.getMasteryLevelsSpent();
        int unspent = MasteryProgress.unspentLevels(exp, spent);
        if (whichSkill < 0) {
            return unspent;
        }
        SkillType skill = SkillType.fromId(whichSkill);
        if (skill != null && ClientPlayerDataCache.hasClaimedMasteryReward(skill)) {
            return 0;
        }
        return unspent;
    }

    private int wrappedHeight(Component text, int maxWidth) {
        List<FormattedCharSequence> lines = this.font.split(text, maxWidth);
        if (lines.isEmpty()) {
            return 0;
        }
        return lines.size() * (this.font.lineHeight + 2);
    }

    private void drawItemScaled(GuiGraphics graphics, ItemStack stack, int x, int y) {
        float scale = ui(64) / 16f;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1f);
        graphics.renderItem(stack, 0, 0);
        graphics.pose().popPose();
    }

    private void drawSprite(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height, float scale, float rotDeg, float alpha) {
        graphics.setColor(1f, 1f, 1f, alpha);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        if (rotDeg != 0f) {
            graphics.pose().mulPose(Axis.ZP.rotationDegrees(rotDeg));
        }
        graphics.pose().scale(scale, scale, 1f);
        graphics.blit(texture, 0, 0, 0, 0, width, height, width, height);
        graphics.pose().popPose();
        graphics.setColor(1f, 1f, 1f, 1f);
    }

    private void drawCloseButton(GuiGraphics graphics) {
        com.stardew.craft.client.gui.common.CommonGuiTextures.drawCloseButton(graphics, closeBtn.getX(), closeBtn.getY(), s4());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && claimBtn != null && claimBtn.contains((int) mouseX, (int) mouseY) && unspentForView() > 0 && pressedAtMs == 0) {
            pressedAtMs = System.currentTimeMillis();
            secondClaimSoundPlayed = false;
            playSound(ModSounds.COWBOY_MONSTERHIT);
            SkillType skill = SkillType.fromId(whichSkill);
            if (skill != null) {
                PacketDistributor.sendToServer(new RequestClaimMasteryRewardPayload(skill.getId()));
            }
            return true;
        }
        if (button == 0 && closeBtn != null && closeBtn.contains((int) mouseX, (int) mouseY)) {
            this.onClose();
            return true;
        }
        if (button == 1) {
            this.onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll <= 0) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) Math.round(scrollY * ui(32))));
        return true;
    }

    @Override
    public void tick() {
        if (pressedAtMs <= 0) {
            return;
        }
        long elapsed = System.currentTimeMillis() - pressedAtMs;
        if (!secondClaimSoundPlayed && elapsed >= 200) {
            secondClaimSoundPlayed = true;
            playSound(ModSounds.COWBOY_MONSTERHIT);
        }
        if (elapsed >= 300) {
            playSound(ModSounds.DISCOVER_MINERAL);
            this.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void playOpenSound() {
        switch (whichSkill) {
            case -1 -> playSound(ModSounds.BOULDER_CRACK);
            case 0 -> playSound(ModSounds.WEED_CUT);
            case 1 -> playSound(ModSounds.WATER_SLOSH);
            case 2 -> playSound(ModSounds.AXCHOP);
            case 3 -> playSound(ModSounds.STONE_CRACK);
            case 4 -> playSound(ModSounds.CAVEDRIP);
            default -> {
            }
        }
    }

    private void playSound(DeferredHolder<SoundEvent, SoundEvent> holder) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(holder.get(), 1.0f));
    }

    private int ui(int sdvPx) {
        return Math.round(sdvPx / guiScale);
    }

    private float s4() {
        return 4.0f / guiScale;
    }

    private static ResourceLocation mastery(String name) {
        return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/mastery/" + name + ".png");
    }
}

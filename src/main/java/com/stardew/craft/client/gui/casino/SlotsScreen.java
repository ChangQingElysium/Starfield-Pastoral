package com.stardew.craft.client.gui.casino;

import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.client.gui.common.SdvFontAdapter;
import com.stardew.craft.network.payload.CasinoGameActionPayload;
import com.stardew.craft.network.payload.CasinoGameStatePayload;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

/**
 * Casino slots rendered in Stardew's physical-pixel coordinate system.
 * Reel movement, stopping conditions, layout, controls and payout display
 * follow StardewValley.Minigames.Slots.
 */
public final class SlotsScreen extends Screen {
    private static final int ICON_COUNT = 8;
    private static final float SLOT_TURN_RATE = 0.008F;
    private static final int[] PAYOUT_ICON_ORDER = {0, 1, 2, 3, 4, 7, 6, 5};
    private static final int[] PAYOUTS = {5, 30, 80, 120, 200, 500, 1000, 2500};

    private CasinoGameStatePayload state;
    private CasinoViewport viewport;
    private final float[] reels = {0.0F, 0.0F, 0.0F};
    private final int[] targets = {0, 0, 0};
    private long lastUpdateMs;
    private int spinsCount;
    private int slotsFinished;
    private int endTimer;
    private boolean spinning;
    private boolean collectSent;
    private boolean showResult;
    private boolean closeSent;
    private CasinoViewport.Rect spin10 = CasinoViewport.Rect.ZERO;
    private CasinoViewport.Rect spin100 = CasinoViewport.Rect.ZERO;
    private CasinoViewport.Rect done = CasinoViewport.Rect.ZERO;

    public SlotsScreen(CasinoGameStatePayload initialState) {
        super(Component.translatable("stardewcraft.casino.slots.title"));
        state = initialState;
        copyTargets(initialState);
        for (int index = 0; index < reels.length; index++) {
            reels[index] = targets[index];
        }
        lastUpdateMs = System.currentTimeMillis();
        play(ModSounds.NEW_ARTIFACT.get());
    }

    public long sessionId() {
        return state.sessionId();
    }

    public void acceptState(CasinoGameStatePayload next) {
        state = next;
        if (next.phase() == CasinoGameStatePayload.PHASE_SLOTS_SPINNING) {
            copyTargets(next);
            spinning = true;
            collectSent = false;
            showResult = false;
            spinsCount = 0;
            slotsFinished = 0;
            endTimer = 0;
            lastUpdateMs = System.currentTimeMillis();
            play(ModSounds.BIG_SELECT.get());
            return;
        }
        if (collectSent) {
            spinning = false;
            showResult = next.payoutMultiplier() > 0;
            play(next.payoutMultiplier() == 0
                    ? ModSounds.BREATHOUT.get()
                    : next.payoutMultiplier() < 5
                    ? ModSounds.NEW_ARTIFACT.get()
                    : next.payoutMultiplier() >= 10
                    ? ModSounds.REWARD.get()
                    : ModSounds.MONEY.get());
        }
    }

    @Override
    protected void init() {
        viewport = new CasinoViewport(
                width, height, (float) Minecraft.getInstance().getWindow().getGuiScale());
        layoutButtons();
    }

    private void layoutButtons() {
        int extra = CasinoGuiTextures.slotButtonExtraWidth();
        int centerX = viewport.centerX(24);
        int top = viewport.centerY(18);
        spin10 = centeredRect(centerX, top, (32 + extra) * 4, 52);
        spin100 = centeredRect(centerX, viewport.centerY(82), (37 + extra) * 4, 52);
        done = centeredRect(centerX, viewport.centerY(146), (30 + extra) * 4, 52);
    }

    private CasinoViewport.Rect centeredRect(
            int centerX, int top, int sourceWidth, int sourceHeight
    ) {
        int width = viewport.ui(sourceWidth);
        return new CasinoViewport.Rect(
                centerX - width / 2, top, width, viewport.ui(sourceHeight));
    }

    @Override
    public void renderBackground(
            @NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick
    ) {
    }

    @Override
    public void render(
            @NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick
    ) {
        if (viewport == null) {
            init();
        }
        layoutButtons();
        updateSpin();
        graphics.fill(0, 0, width, height, 0xFF260007);
        drawMachine(graphics, mouseX, mouseY);
        drawPayoutTable(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void updateSpin() {
        long now = System.currentTimeMillis();
        int elapsedMs = (int) Math.min(250L, Math.max(0L, now - lastUpdateMs));
        lastUpdateMs = now;
        if (!spinning || elapsedMs <= 0) {
            return;
        }
        if (endTimer <= 0) {
            for (int index = slotsFinished; index < reels.length; index++) {
                float old = reels[index];
                float advance = elapsedMs * SLOT_TURN_RATE * (1.0F - index * 0.05F);
                reels[index] = (reels[index] + advance) % ICON_COUNT;
                if (index == 2) {
                    float soundInterval = 0.25F + slotsFinished * 0.5F;
                    if (old % soundInterval > reels[index] % soundInterval) {
                        play(ModSounds.SHINY4.get(), 0.75F);
                    }
                    if (old > reels[index]) {
                        spinsCount++;
                    }
                }
                if (spinsCount > 0
                        && index == slotsFinished
                        && Math.abs(reels[index] - targets[index])
                        <= elapsedMs * SLOT_TURN_RATE) {
                    reels[index] = targets[index];
                    slotsFinished++;
                    spinsCount--;
                    play(ModSounds.COWBOY_GUNSHOT.get());
                }
            }
            if (slotsFinished >= reels.length) {
                endTimer = state.payoutMultiplier() == 0 ? 600 : 1000;
            }
        }
        if (endTimer > 0) {
            endTimer -= elapsedMs;
            if (endTimer <= 0 && !collectSent) {
                collectSent = true;
                send(CasinoGameActionPayload.SLOTS_COLLECT);
            }
        }
    }

    private void drawMachine(GuiGraphics graphics, int mouseX, int mouseY) {
        int titleX = viewport.centerX(-114);
        int titleY = viewport.centerY(-256);
        graphics.blit(
                CasinoGuiTextures.slotTitle(),
                titleX, titleY, viewport.ui(264), viewport.ui(52),
                0, 0, 66, 13, 66, 13);

        int firstSlotX = viewport.centerX(-112);
        int slotY = viewport.centerY(-128);
        int iconSize = viewport.ui(64);
        for (int index = 0; index < reels.length; index++) {
            int x = firstSlotX + viewport.ui(index * 104);
            graphics.blit(
                    CasinoGuiTextures.SLOT_WINDOW,
                    x, slotY, iconSize, iconSize,
                    0, 0, 16, 16, 16, 16);
            drawRollingReel(graphics, index, x, slotY, iconSize);
            graphics.blit(
                    CasinoGuiTextures.SLOT_FRAME,
                    viewport.centerX(-132 + index * 104), viewport.centerY(-192),
                    viewport.ui(104), viewport.ui(192),
                    0, 0, 26, 48, 26, 48);
        }

        drawTextureButton(
                graphics, spin10, CasinoGuiTextures.slotButton10(),
                32 + CasinoGuiTextures.slotButtonExtraWidth(),
                !spinning && state.clubCoins() >= 10,
                spin10.contains(mouseX, mouseY));
        drawTextureButton(
                graphics, spin100, CasinoGuiTextures.slotButton100(),
                37 + CasinoGuiTextures.slotButtonExtraWidth(),
                !spinning && state.clubCoins() >= 100,
                spin100.contains(mouseX, mouseY));
        drawTextureButton(
                graphics, done, CasinoGuiTextures.slotButtonDone(),
                30 + CasinoGuiTextures.slotButtonExtraWidth(),
                !spinning, done.contains(mouseX, mouseY));

        drawCoinBalance(graphics);
        if (showResult) {
            Component result = Component.literal(
                    "+" + state.payoutMultiplier() * state.currentBet());
            drawText(
                    graphics, result,
                    viewport.centerX(-372), spin10.y() - viewport.ui(56),
                    0xFFFFFFFF, 1.0F);
        }
    }

    private void drawRollingReel(
            GuiGraphics graphics, int reel, int x, int y, int iconSize
    ) {
        float faceValue = (reels[reel] + 1.0F) % ICON_COUNT;
        int previous = ((int) faceValue + ICON_COUNT - 1) % ICON_COUNT;
        int current = (previous + 1) % ICON_COUNT;
        int offset = Math.round(iconSize * (faceValue % 1.0F));
        graphics.enableScissor(x, y, x + iconSize, y + iconSize);
        CasinoGuiTextures.drawSlotIcon(graphics, previous, x, y + offset, iconSize);
        CasinoGuiTextures.drawSlotIcon(graphics, current, x, y - iconSize + offset, iconSize);
        graphics.disableScissor();
    }

    private void drawTextureButton(
            GuiGraphics graphics,
            CasinoViewport.Rect rect,
            ResourceLocation texture,
            int textureWidth,
            boolean enabled,
            boolean hovered
    ) {
        float hoverScale = hovered && enabled ? 1.05F : 1.0F;
        graphics.setColor(1.0F, 1.0F, 1.0F, enabled ? 1.0F : 0.5F);
        graphics.blit(
                texture,
                rect.x(), rect.y(),
                Math.round(rect.width() * hoverScale),
                Math.round(rect.height() * hoverScale),
                0, 0, textureWidth, 13, textureWidth, 13);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawCoinBalance(GuiGraphics graphics) {
        Component coins = Component.literal(Integer.toString(state.clubCoins()));
        int x = viewport.centerX(-376);
        int y = viewport.centerY(-120);
        int boxWidth = textWidth(coins, 1.0F) + viewport.ui(92);
        int boxHeight = viewport.ui(72);
        CommonGuiTextures.drawScrollBannerBox(
                graphics, x, y, boxWidth, boxHeight, viewport.pixelZoom());
        CommonGuiTextures.drawQiCoin(
                graphics, x + viewport.ui(4), y + viewport.ui(4), viewport.pixelZoom());
        drawText(
                graphics, coins,
                x + viewport.ui(64),
                y + (boxHeight - renderedTextHeight(1.0F)) / 2,
                0xFF5B2B12, 1.0F);
    }

    private void drawPayoutTable(GuiGraphics graphics) {
        int baseX = viewport.centerX(200);
        int baseY = viewport.centerY(-352);
        CasinoGuiTextures.drawPayoutBox(
                graphics, CasinoGuiTextures.SLOT_PAYOUT_BOX,
                baseX, baseY, viewport.ui(384), viewport.ui(704),
                viewport.pixelZoom(), false, 1.0F, 1.0F, 1.0F, 1.0F);

        drawPayoutIcon(graphics, 7, baseX + viewport.ui(8), baseY + viewport.ui(8));
        drawPayoutText(graphics, "x2", baseX, baseY + viewport.ui(24));
        drawPayoutIcon(graphics, 7, baseX + viewport.ui(8), baseY + viewport.ui(76));
        drawPayoutIcon(graphics, 7, baseX + viewport.ui(76), baseY + viewport.ui(76));
        drawPayoutText(graphics, "x3", baseX, baseY + viewport.ui(92));

        for (int row = 0; row < PAYOUT_ICON_ORDER.length; row++) {
            int y = baseY + viewport.ui(8 + (row + 2) * 68);
            for (int copy = 0; copy < 3; copy++) {
                drawPayoutIcon(
                        graphics, PAYOUT_ICON_ORDER[row],
                        baseX + viewport.ui(8 + copy * 68), y);
            }
            drawPayoutText(
                    graphics, "x" + PAYOUTS[row],
                    baseX, baseY + viewport.ui((row + 2) * 68 + 24));
        }
        drawRedBackdrop(graphics, baseX, baseY);
    }

    private void drawRedBackdrop(GuiGraphics graphics, int baseX, int baseY) {
        CasinoGuiTextures.drawPayoutBox(
                graphics, CasinoGuiTextures.SLOT_BACKDROP_BOX,
                baseX - viewport.ui(640), baseY,
                viewport.ui(1024), viewport.ui(704),
                viewport.pixelZoom(), false, 1.0F, 0.0F, 0.0F, 1.0F);
        for (int glow = 1; glow < 8; glow++) {
            int expansion = viewport.ui(4 * glow);
            CasinoGuiTextures.drawPayoutBox(
                    graphics, CasinoGuiTextures.SLOT_BACKDROP_BOX,
                    baseX - viewport.ui(640) - expansion, baseY - expansion,
                    viewport.ui(1024) + expansion * 2,
                    viewport.ui(704) + expansion * 2,
                    viewport.pixelZoom(), false,
                    1.0F, 0.0F, 0.0F, Math.max(0.0F, 1.0F - glow * 0.15F));
        }
        for (int line = 0; line < 17; line++) {
            int lineWidth = Math.round(
                    608.0F - line * 64.0F * 1.2F + line * line * 4.0F * 0.7F);
            CasinoGuiTextures.drawPayoutBox(
                    graphics, CasinoGuiTextures.SLOT_GRADIENT_LINE,
                    baseX - viewport.ui(632),
                    baseY + viewport.ui(line * 12 + 12),
                    viewport.ui(lineWidth), Math.max(1, viewport.ui(4)),
                    viewport.pixelZoom(), false,
                    clampColor(line * 25) / 255.0F,
                    clampColor(line > 8 ? line * 10 : 0) / 255.0F,
                    clampColor(255 - line * 25) / 255.0F,
                    1.0F);
        }
    }

    private void drawPayoutIcon(GuiGraphics graphics, int icon, int x, int y) {
        CasinoGuiTextures.drawSlotIcon(graphics, icon, x, y, viewport.ui(64));
    }

    private void drawPayoutText(GuiGraphics graphics, String text, int baseX, int y) {
        drawText(
                graphics, Component.literal(text),
                baseX + viewport.ui(208), y,
                0xFFFFFFFF, 0.88F);
    }

    private static int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private float textScale(float multiplier) {
        return SdvFontAdapter.scale(
                font,
                Minecraft.getInstance().getLanguageManager().getSelected(),
                viewport.effectiveGuiScale(),
                SdvFontAdapter.Style.SPRITE_TEXT_COLORED) * multiplier;
    }

    private int textWidth(Component text, float multiplier) {
        return SdvFontAdapter.width(font, text, textScale(multiplier),
                SdvFontAdapter.Style.SPRITE_TEXT_COLORED);
    }

    private int renderedTextHeight(float multiplier) {
        return Math.max(1, Math.round(font.lineHeight * textScale(multiplier)));
    }

    private void drawText(
            GuiGraphics graphics, Component text, int x, int y, int color, float multiplier
    ) {
        SdvFontAdapter.draw(
                graphics, font, text, x, y, textScale(multiplier), color,
                SdvFontAdapter.Style.SPRITE_TEXT_COLORED);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || spinning) {
            return true;
        }
        if (spin10.contains(mouseX, mouseY) && state.clubCoins() >= 10) {
            send(CasinoGameActionPayload.SLOTS_SPIN_10);
            return true;
        }
        if (spin100.contains(mouseX, mouseY) && state.clubCoins() >= 100) {
            send(CasinoGameActionPayload.SLOTS_SPIN_100);
            return true;
        }
        if (done.contains(mouseX, mouseY)) {
            onClose();
            return true;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (spinning && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (!closeSent) {
            closeSent = true;
            play(ModSounds.BIG_DESELECT.get());
            send(CasinoGameActionPayload.CLOSE);
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void copyTargets(CasinoGameStatePayload payload) {
        targets[0] = Math.max(0, payload.slot0());
        targets[1] = Math.max(0, payload.slot1());
        targets[2] = Math.max(0, payload.slot2());
    }

    private void send(int action) {
        PacketDistributor.sendToServer(
                new CasinoGameActionPayload(state.sessionId(), action));
    }

    private void play(SoundEvent sound) {
        play(sound, 1.0F);
    }

    private void play(SoundEvent sound, float volume) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.playSound(sound, volume, 1.0F);
        }
    }
}

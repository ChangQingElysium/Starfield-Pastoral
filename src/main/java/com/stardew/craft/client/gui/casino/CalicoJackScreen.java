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
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Calico Jack rendered in the original physical-pixel coordinate system.
 * Layout, card dimensions, deal order, flip timing and result placement follow
 * StardewValley.Minigames.CalicoJack.
 */
public final class CalicoJackScreen extends Screen {
    private static final int CARD_WIDTH = 96;
    private static final int CARD_HEIGHT = 144;
    private static final int CARD_STEP = 112;
    private static final long START_TIME_MS = 1000L;
    private static final long CARD_FLIP_MS = 400L;

    private CasinoGameStatePayload state;
    private CasinoViewport viewport;
    private long roundStartMs;
    private long lastPlayerCardStartMs = -1L;
    private int lastPlayerCardCount;
    private long resultRevealStartMs;
    private long resultRevealEndMs;
    private int preResultClubCoins;
    private int preResultBet;
    private int lastInitialDealStep;
    private int lastDealerRevealStep;
    private boolean resultSoundPlayed;
    private boolean closeSent;
    private CasinoViewport.Rect hitButton = CasinoViewport.Rect.ZERO;
    private CasinoViewport.Rect standButton = CasinoViewport.Rect.ZERO;
    private CasinoViewport.Rect doubleButton = CasinoViewport.Rect.ZERO;
    private CasinoViewport.Rect replayButton = CasinoViewport.Rect.ZERO;
    private CasinoViewport.Rect quitButton = CasinoViewport.Rect.ZERO;

    public CalicoJackScreen(CasinoGameStatePayload initialState) {
        super(Component.translatable("stardewcraft.casino.calico_jack.title"));
        this.state = initialState;
        this.roundStartMs = System.currentTimeMillis();
        this.lastPlayerCardCount = initialState.playerCards().size();
        this.preResultClubCoins = initialState.clubCoins();
        this.preResultBet = initialState.currentBet();
    }

    public long sessionId() {
        return state.sessionId();
    }

    public void acceptState(CasinoGameStatePayload next) {
        CasinoGameStatePayload previous = state;
        long now = System.currentTimeMillis();
        if (next.playerCards().size() > previous.playerCards().size()) {
            lastPlayerCardStartMs = now;
            lastPlayerCardCount = next.playerCards().size();
            play(ModSounds.SHWIP.get());
        }
        state = next;
        if (next.phase() == CasinoGameStatePayload.PHASE_CALICO_RESULT
                && previous.phase() != CasinoGameStatePayload.PHASE_CALICO_RESULT) {
            preResultClubCoins = previous.clubCoins();
            preResultBet = previous.currentBet();
            resultRevealStartMs = now;
            lastDealerRevealStep = 0;
            resultSoundPlayed = false;
            int playerTotal = total(next.playerCards());
            if (playerTotal >= 21) {
                resultRevealEndMs = now + 1000L;
            } else {
                int extraDealerCards = Math.max(0, next.dealerCards().size() - 2);
                int dealerTotal = total(next.dealerCards());
                resultRevealEndMs = dealerTotal > 21 && extraDealerCards > 0
                        ? now + (extraDealerCards + 1L) * 1000L + 2000L
                        : now + (extraDealerCards + 2L) * 1000L + 50L;
            }
        }
    }

    @Override
    protected void init() {
        viewport = new CasinoViewport(
                width, height, (float) Minecraft.getInstance().getWindow().getGuiScale());
        layoutButtons();
    }

    private void layoutButtons() {
        hitButton = rightButton(
                Component.translatable("stardewcraft.casino.calico_jack.hit"), -64);
        standButton = rightButton(
                Component.translatable("stardewcraft.casino.calico_jack.stand"), 32);
        doubleButton = centeredButton(
                Component.translatable("stardewcraft.casino.calico_jack.double_or_nothing"), 0);
        replayButton = centeredButton(
                Component.translatable("stardewcraft.casino.calico_jack.play_again"), 80);
        quitButton = centeredButton(
                Component.translatable("stardewcraft.casino.calico_jack.quit"), 160);
    }

    private CasinoViewport.Rect rightButton(Component label, int centerYOffset) {
        int buttonWidth = textWidth(label) + viewport.ui(64);
        return new CasinoViewport.Rect(
                width - viewport.ui(128) - textWidth(label),
                viewport.centerY(centerYOffset),
                buttonWidth,
                viewport.ui(64));
    }

    private CasinoViewport.Rect centeredButton(Component label, int centerYOffset) {
        int buttonWidth = textWidth(label) + viewport.ui(64);
        return new CasinoViewport.Rect(
                width / 2 - buttonWidth / 2,
                viewport.centerY(centerYOffset),
                buttonWidth,
                viewport.ui(64));
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
        graphics.fill(0, 0, width, height, state.highStakes() ? 0xFF820052 : 0xFF006400);
        long now = System.currentTimeMillis();
        boolean showResults = state.phase() == CasinoGameStatePayload.PHASE_CALICO_RESULT
                && now >= resultRevealEndMs;
        if (showResults) {
            renderResults(graphics, mouseX, mouseY);
            playResultSoundOnce();
        } else {
            renderTable(graphics, mouseX, mouseY, now);
        }
        renderCoinBalance(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderTable(GuiGraphics graphics, int mouseX, int mouseY, long now) {
        long elapsed = Math.max(0L, now - roundStartMs);
        playInitialDealSounds(elapsed);

        int playerY = viewport.sourceHeight() - 320;
        int visiblePlayerTotal = 0;
        for (int index = 0; index < state.playerCards().size(); index++) {
            long animationStart = playerAnimationStart(index);
            CardVisual visual = cardVisual(now - animationStart, false);
            if (!visual.present()) {
                continue;
            }
            int value = state.playerCards().get(index);
            drawCard(graphics, 128 + index * CARD_STEP, playerY, value, visual);
            if (visual.counted()) {
                visiblePlayerTotal += value;
            }
        }

        boolean resolving = state.phase() == CasinoGameStatePayload.PHASE_CALICO_RESULT;
        int visibleDealerTotal = 0;
        for (int index = 0; index < state.dealerCards().size(); index++) {
            CardVisual visual = dealerVisual(index, resolving, now);
            if (!visual.present()) {
                continue;
            }
            int value = state.dealerCards().get(index);
            drawCard(graphics, 128 + index * CARD_STEP, 128, value, visual);
            if (visual.counted()) {
                visibleDealerTotal += value;
            }
        }
        playDealerRevealSounds(now, resolving);

        boolean dealerValueUnknown = total(state.playerCards()) >= 21
                || !resolving
                || now < resultRevealStartMs + 1000L;
        String dealerValue = dealerValueUnknown
                ? "?"
                : visibleDealerTotal >= 999
                ? "!!!"
                : visibleDealerTotal > 0 ? Integer.toString(visibleDealerTotal) : "?";
        drawScrollLabel(
                graphics,
                Component.translatable("stardewcraft.casino.calico_jack.dealer", dealerValue),
                160, 32, false, false);

        String playerName = minecraft != null && minecraft.player != null
                ? minecraft.player.getGameProfile().getName() : "";
        drawScrollLabel(
                graphics,
                Component.translatable(
                        "stardewcraft.casino.calico_jack.player", playerName, visiblePlayerTotal),
                160, playerY + 176, false, false);
        drawScrollLabel(
                graphics,
                Component.translatable(
                        "stardewcraft.casino.calico_jack.wager",
                        displayedBet(now, resolving)),
                160, viewport.sourceHeight() / 2 - 48, false, true);

        if (!resolving && elapsed >= START_TIME_MS) {
            drawButton(
                    graphics, hitButton,
                    Component.translatable("stardewcraft.casino.calico_jack.hit"),
                    hitButton.contains(mouseX, mouseY));
            drawButton(
                    graphics, standButton,
                    Component.translatable("stardewcraft.casino.calico_jack.stand"),
                    standButton.contains(mouseX, mouseY));
        }
    }

    private long playerAnimationStart(int index) {
        if (index == 0) {
            return roundStartMs + 500L;
        }
        if (index == 1) {
            return roundStartMs + 750L;
        }
        if (index == lastPlayerCardCount - 1 && lastPlayerCardStartMs >= 0L) {
            return lastPlayerCardStartMs;
        }
        return 0L;
    }

    private CardVisual dealerVisual(int index, boolean resolving, long now) {
        if (index == 0) {
            if (!resolving || total(state.playerCards()) >= 21) {
                return new CardVisual(true, true, CARD_HEIGHT, false);
            }
            long revealAge = now - (resultRevealStartMs + 1000L);
            return revealAge < 0L
                    ? new CardVisual(true, true, CARD_HEIGHT, false)
                    : cardVisual(revealAge, false);
        }
        if (index == 1) {
            return cardVisual(now - (roundStartMs + 250L), false);
        }
        if (!resolving) {
            return CardVisual.ABSENT;
        }
        return cardVisual(now - (resultRevealStartMs + index * 1000L), false);
    }

    private int displayedBet(long now, boolean resolving) {
        if (!resolving || state.currentBet() == preResultBet) {
            return state.currentBet();
        }
        for (int index = 0; index < state.dealerCards().size(); index++) {
            if (state.dealerCards().get(index) == 999) {
                return now >= resultRevealStartMs + index * 1000L
                        ? state.currentBet()
                        : preResultBet;
            }
        }
        return state.currentBet();
    }

    private static CardVisual cardVisual(long ageMs, boolean permanentlyHidden) {
        if (ageMs < 0L) {
            return CardVisual.ABSENT;
        }
        if (permanentlyHidden) {
            return new CardVisual(true, true, CARD_HEIGHT, false);
        }
        if (ageMs >= CARD_FLIP_MS) {
            return new CardVisual(true, false, CARD_HEIGHT, true);
        }
        int remaining = (int) (CARD_FLIP_MS - ageMs);
        int height = Math.max(1, Math.round(
                Math.abs(remaining - CARD_FLIP_MS / 2.0F)
                        / (CARD_FLIP_MS / 2.0F) * CARD_HEIGHT));
        return new CardVisual(true, remaining > CARD_FLIP_MS / 2, height, false);
    }

    private void drawCard(
            GuiGraphics graphics, int sourceX, int sourceY, int value, CardVisual visual
    ) {
        int height = viewport.ui(visual.height());
        int x = viewport.ui(sourceX);
        int y = viewport.ui(sourceY + CARD_HEIGHT / 2) - height / 2;
        CasinoGuiTextures.drawCard(
                graphics, visual.hidden(), x, y,
                viewport.ui(CARD_WIDTH), height, viewport.pixelZoom());
        if (!visual.counted()) {
            return;
        }
        if (value == 999) {
            graphics.blit(
                    CasinoGuiTextures.CALICO_BAT,
                    x + viewport.ui(16), viewport.ui(sourceY + 40),
                    viewport.ui(64), viewport.ui(64),
                    0, 0, 16, 16, 16, 16);
            return;
        }
        Component number = Component.literal(Integer.toString(value));
        drawCenteredText(
                graphics, number,
                x + viewport.ui(CARD_WIDTH / 2),
                viewport.ui(sourceY + CARD_HEIGHT / 2) - renderedTextHeight() / 2,
                0xFF6B3410);
    }

    private void renderResults(GuiGraphics graphics, int mouseX, int mouseY) {
        int playerTotal = total(state.playerCards());
        int dealerTotal = total(state.dealerCards());
        boolean won = state.result() == 1;
        boolean tie = state.result() == 2;
        Component title = Component.translatable(tie
                ? "stardewcraft.casino.calico_jack.tie"
                : won
                ? "stardewcraft.casino.calico_jack.win"
                : "stardewcraft.casino.calico_jack.lose");
        Component message;
        if (playerTotal == 21) {
            message = Component.translatable("stardewcraft.casino.calico_jack.blackjack");
        } else if (playerTotal > 21) {
            message = Component.translatable("stardewcraft.casino.calico_jack.player_bust");
        } else if (dealerTotal > 21) {
            message = Component.translatable("stardewcraft.casino.calico_jack.dealer_bust");
        } else if (tie) {
            message = Component.translatable("stardewcraft.casino.calico_jack.push");
        } else {
            message = Component.translatable(
                    won
                            ? "stardewcraft.casino.calico_jack.closest"
                            : "stardewcraft.casino.calico_jack.dealer_closest",
                    21);
        }
        drawCenteredScroll(graphics, message, 48, false);
        drawCenteredScroll(graphics, title, 128, false);
        if (!tie) {
            Component result = Component.translatable(
                    "stardewcraft.casino.calico_jack.result",
                    (won ? "" : "-") + state.currentBet());
            drawCenteredScroll(graphics, result, 256, true);
        }
        if (won) {
            drawButton(
                    graphics, doubleButton,
                    Component.translatable("stardewcraft.casino.calico_jack.double_or_nothing"),
                    doubleButton.contains(mouseX, mouseY));
        }
        if (state.clubCoins() >= state.currentBet()) {
            drawButton(
                    graphics, replayButton,
                    Component.translatable("stardewcraft.casino.calico_jack.play_again"),
                    replayButton.contains(mouseX, mouseY));
        }
        drawButton(
                graphics, quitButton,
                Component.translatable("stardewcraft.casino.calico_jack.quit"),
                quitButton.contains(mouseX, mouseY));
    }

    private void renderCoinBalance(GuiGraphics graphics) {
        int coins = state.phase() == CasinoGameStatePayload.PHASE_CALICO_RESULT
                && System.currentTimeMillis() < resultRevealEndMs
                ? preResultClubCoins
                : state.clubCoins();
        drawScrollLabel(
                graphics,
                Component.literal(Integer.toString(coins)),
                viewport.sourceWidth() - 192, 32, true, false);
    }

    private void drawScrollLabel(
            GuiGraphics graphics, Component text, int sourceX, int sourceY,
            boolean leadingCoin, boolean trailingCoin
    ) {
        int coinSpace = leadingCoin || trailingCoin ? viewport.ui(48) : 0;
        int boxWidth = textWidth(text) + viewport.ui(48) + coinSpace;
        int boxHeight = viewport.ui(72);
        int x = viewport.ui(sourceX);
        int y = viewport.ui(sourceY);
        CommonGuiTextures.drawScrollBannerBox(
                graphics, x, y, boxWidth, boxHeight, viewport.pixelZoom());
        int textX = x + viewport.ui(24) + (leadingCoin ? viewport.ui(44) : 0);
        int textY = y + (boxHeight - renderedTextHeight()) / 2;
        drawText(graphics, text, textX, textY, 0xFF5B2B12);
        if (leadingCoin) {
            CommonGuiTextures.drawQiCoin(
                    graphics, x + viewport.ui(12), y + viewport.ui(16), viewport.pixelZoom());
        } else if (trailingCoin) {
            CommonGuiTextures.drawQiCoin(
                    graphics,
                    textX + textWidth(text) + viewport.ui(8),
                    y + viewport.ui(16),
                    viewport.pixelZoom());
        }
    }

    private void drawCenteredScroll(
            GuiGraphics graphics, Component text, int sourceY, boolean trailingCoin
    ) {
        int coinSpace = trailingCoin ? viewport.ui(48) : 0;
        int boxWidth = textWidth(text) + viewport.ui(80) + coinSpace;
        int boxHeight = viewport.ui(72);
        int x = width / 2 - boxWidth / 2;
        int y = viewport.ui(sourceY);
        CommonGuiTextures.drawScrollBannerBox(
                graphics, x, y, boxWidth, boxHeight, viewport.pixelZoom());
        int textX = x + viewport.ui(40);
        int textY = y + (boxHeight - renderedTextHeight()) / 2;
        drawText(graphics, text, textX, textY, 0xFF5B2B12);
        if (trailingCoin) {
            CommonGuiTextures.drawQiCoin(
                    graphics,
                    textX + textWidth(text) + viewport.ui(8),
                    y + viewport.ui(16),
                    viewport.pixelZoom());
        }
    }

    private void drawButton(
            GuiGraphics graphics, CasinoViewport.Rect rect, Component label, boolean hovered
    ) {
        CommonGuiTextures.drawBillboardAcceptBox(
                graphics, rect.x(), rect.y(), rect.width(), rect.height(),
                viewport.pixelZoom() * (hovered ? 1.25F : 1.0F));
        int x = rect.x() + viewport.ui(8);
        int y = rect.y() + (rect.height() - renderedTextHeight()) / 2;
        drawText(graphics, label, x, y, 0xFF5B2B12);
    }

    private void playInitialDealSounds(long elapsed) {
        int dealStep = Math.min(4, (int) (elapsed / 250L) + 1);
        while (lastInitialDealStep < dealStep) {
            lastInitialDealStep++;
            play(ModSounds.SHWIP.get());
        }
    }

    private void playDealerRevealSounds(long now, boolean resolving) {
        if (!resolving || total(state.playerCards()) >= 21) {
            return;
        }
        int elapsedStep = (int) Math.max(0L, (now - resultRevealStartMs) / 1000L);
        int maxStep = Math.min(state.dealerCards().size() - 1, elapsedStep);
        while (lastDealerRevealStep < maxStep) {
            lastDealerRevealStep++;
            int cardIndex = lastDealerRevealStep == 1 ? 0 : lastDealerRevealStep;
            int value = state.dealerCards().get(Math.min(cardIndex, state.dealerCards().size() - 1));
            play(value == 999 ? ModSounds.BAT_SCREECH.get() : ModSounds.SHWIP.get());
        }
    }

    private void playResultSoundOnce() {
        if (resultSoundPlayed) {
            return;
        }
        resultSoundPlayed = true;
        if (state.result() == 2) {
            return;
        }
        play(state.result() == 1
                ? ModSounds.REWARD.get()
                : ModSounds.FISH_ESCAPE.get());
    }

    private float textScale() {
        return SdvFontAdapter.scale(
                font,
                Minecraft.getInstance().getLanguageManager().getSelected(),
                viewport.effectiveGuiScale(),
                SdvFontAdapter.Style.SPRITE_TEXT_COLORED);
    }

    private int textWidth(Component text) {
        return SdvFontAdapter.width(font, text, textScale(),
            SdvFontAdapter.Style.SPRITE_TEXT_COLORED);
    }

    private int renderedTextHeight() {
        return Math.max(1, Math.round(font.lineHeight * textScale()));
    }

    private void drawText(
            GuiGraphics graphics, Component text, int x, int y, int color
    ) {
        SdvFontAdapter.draw(graphics, font, text, x, y, textScale(), color,
            SdvFontAdapter.Style.SPRITE_TEXT_COLORED);
    }

    private void drawCenteredText(
            GuiGraphics graphics, Component text, int centerX, int y, int color
    ) {
        drawText(graphics, text, centerX - textWidth(text) / 2, y, color);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        long now = System.currentTimeMillis();
        boolean showResults = state.phase() == CasinoGameStatePayload.PHASE_CALICO_RESULT
                && now >= resultRevealEndMs;
        if (!showResults
                && state.phase() == CasinoGameStatePayload.PHASE_CALICO_PLAYING
                && now - roundStartMs >= START_TIME_MS) {
            if (hitButton.contains(mouseX, mouseY)) {
                send(CasinoGameActionPayload.CALICO_HIT);
                return true;
            }
            if (standButton.contains(mouseX, mouseY)) {
                play(ModSounds.COIN.get());
                send(CasinoGameActionPayload.CALICO_STAND);
                return true;
            }
        } else if (showResults) {
            if (state.result() == 1 && doubleButton.contains(mouseX, mouseY)) {
                play(ModSounds.BIG_SELECT.get());
                send(CasinoGameActionPayload.CALICO_DOUBLE_OR_NOTHING);
                return true;
            }
            if (state.clubCoins() >= state.currentBet() && replayButton.contains(mouseX, mouseY)) {
                play(ModSounds.SMALL_SELECT.get());
                send(CasinoGameActionPayload.CALICO_PLAY_AGAIN);
                return true;
            }
            if (quitButton.contains(mouseX, mouseY)) {
                onClose();
                return true;
            }
        }
        return true;
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

    private void send(int action) {
        PacketDistributor.sendToServer(new CasinoGameActionPayload(state.sessionId(), action));
    }

    private void play(SoundEvent sound) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.playSound(sound, 1.0F, 1.0F);
        }
    }

    private static int total(List<Integer> cards) {
        return cards.stream().mapToInt(Integer::intValue).sum();
    }

    private record CardVisual(boolean present, boolean hidden, int height, boolean counted) {
        private static final CardVisual ABSENT = new CardVisual(false, true, 0, false);
    }
}

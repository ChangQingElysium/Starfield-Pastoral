package com.stardew.craft.client.gui;

import com.stardew.craft.client.gui.common.StardewGuiViewport;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.network.payload.PrizeTicketClaimPayload;
import com.stardew.craft.network.payload.PrizeTicketClaimResultPayload;
import com.stardew.craft.network.payload.PrizeTicketRewardPreview;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@SuppressWarnings("null")
public class PrizeTicketMachineScreen extends Screen {
    private static final ResourceLocation BACKGROUND = new ResourceLocation(
        StardewCraft.MODID, "textures/gui/prize_ticket/menu_background.png");
    private static final ResourceLocation REWARD_TRACK = new ResourceLocation(
        StardewCraft.MODID, "textures/gui/prize_ticket/reward_track_overlay.png");
    private static final ResourceLocation BUTTON = new ResourceLocation(
        StardewCraft.MODID, "textures/gui/prize_ticket/button.png");
    private static final ResourceLocation BUTTON_HOVER = new ResourceLocation(
        StardewCraft.MODID, "textures/gui/prize_ticket/button_hover.png");
    private static final ResourceLocation BUTTON_PRESSED = new ResourceLocation(
        StardewCraft.MODID, "textures/gui/prize_ticket/button_pressed.png");

    private static final int SDV_W = 464;
    private static final int SDV_H = 376;
    private static final int SOURCE_W = 116;
    private static final int SOURCE_H = 94;

    private static final int BUTTON_X = 192;
    private static final int BUTTON_Y = 216;
    private static final int BUTTON_W = 92;
    private static final int BUTTON_H = 88;
    private static final int BUTTON_SOURCE_W = 23;
    private static final int BUTTON_SOURCE_H = 22;

    private static final int TRACK_X = 100;
    private static final int TRACK_Y = 72;
    private static final int TRACK_SOURCE_W = 76;
    private static final int TRACK_SOURCE_H = 22;
    private static final int FIRST_REWARD_X = 112;
    private static final int REWARD_STEP_X = 88;
    private static final int REWARD_Y = 84;
    private static final int REWARD_TRACK_CLIP_X = TRACK_X;
    private static final int REWARD_TRACK_CLIP_Y = TRACK_Y;
    private static final int REWARD_TRACK_CLIP_RIGHT_X = FIRST_REWARD_X + REWARD_STEP_X * 3;
    private static final int REWARD_TRACK_CLIP_BOTTOM_Y = TRACK_Y + TRACK_SOURCE_H * 4;

    private static final int GET_REWARD_MS = 2000;
    private static final int DISCOVER_MINERAL_MS = 750;
    private static final int BUTTON_PRESS_MS = 200;
    private static final int TRACK_PRE_MS = 500;
    private static final int TRACK_MOVE_MS = 2000;

    private final Random random = new Random();
    private List<PreviewSlot> currentPrizeTrack;
    private List<PreviewSlot> pendingPrizeTrack = List.of();

    private float guiScale;
    private int x0;
    private int y0;
    private int buttonX;
    private int buttonY;
    private int buttonW;
    private int buttonH;
    private int displayTicketCount;

    private boolean playedOpenSound;
    private boolean wasHoveringButton;
    private boolean gettingReward;
    private boolean movingRewardTrack;
    private boolean waitingClaimResult;
    private boolean claimSent;
    private boolean discoverSoundPlayed;
    private boolean whirSoundPlayed;
    private long buttonPressStart;
    private long rewardStart;
    private long trackMoveStart;

    public PrizeTicketMachineScreen(int ticketPrizesClaimed, List<PrizeTicketRewardPreview> previews) {
        super(Component.empty());
        this.currentPrizeTrack = toSlots(previews);
    }

    private int ui(int value) {
        return Math.round(value / guiScale);
    }

    private float s4() {
        return 4.0f / guiScale;
    }

    @Override
    protected void init() {
        super.init();
        guiScale = (float) StardewGuiViewport.REFERENCE_SCALE;
        int menuW = ui(SDV_W);
        int menuH = ui(SDV_H);
        x0 = width / 2 - menuW / 2;
        y0 = height / 2 - menuH / 2;
        buttonX = x0 + ui(BUTTON_X);
        buttonY = y0 + ui(BUTTON_Y);
        buttonW = ui(BUTTON_W);
        buttonH = ui(BUTTON_H);
        displayTicketCount = countPrizeTickets();
        if (!playedOpenSound) {
            playSound(ModSounds.MACHINE_BELL.get());
            playedOpenSound = true;
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateAnimation();
        graphics.fill(0, 0, width, height, 0x99000000);

        drawFullTexture(graphics, REWARD_TRACK, TRACK_SOURCE_W, TRACK_SOURCE_H, x0 + ui(TRACK_X), y0 + ui(TRACK_Y));
        drawRewardTrack(graphics);
        drawFullTexture(graphics, BACKGROUND, SOURCE_W, SOURCE_H, x0, y0);
        if (gettingReward && !currentPrizeTrack.isEmpty()) {
            drawFlyingReward(graphics, currentPrizeTrack.get(0).stack());
        }
        drawTicketCount(graphics);
        drawButton(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isButtonActive() && isIn(mouseX, mouseY, buttonX, buttonY, buttonW, buttonH)) {
            playSound(ModSounds.BUTTON_PRESS.get());
            buttonPressStart = System.currentTimeMillis();
            if (displayTicketCount > 0 && !currentPrizeTrack.isEmpty()) {
                gettingReward = true;
                waitingClaimResult = false;
                claimSent = false;
                discoverSoundPlayed = false;
                rewardStart = System.currentTimeMillis();
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !gettingReward && !waitingClaimResult;
    }

    @Override
    public void onClose() {
        if (!gettingReward && !waitingClaimResult) {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public void onClaimResult(PrizeTicketClaimResultPayload payload) {
        waitingClaimResult = false;
        if (!payload.success()) {
            gettingReward = false;
            movingRewardTrack = false;
            displayTicketCount = countPrizeTickets();
            return;
        }
        displayTicketCount = Math.max(0, displayTicketCount - 1);
        if (!currentPrizeTrack.isEmpty()) {
            currentPrizeTrack = new ArrayList<>(currentPrizeTrack.subList(1, currentPrizeTrack.size()));
        }
        pendingPrizeTrack = toSlots(payload.previews());
        gettingReward = false;
        movingRewardTrack = true;
        whirSoundPlayed = false;
        trackMoveStart = System.currentTimeMillis();
    }

    private void updateAnimation() {
        long now = System.currentTimeMillis();
        if (gettingReward) {
            long elapsed = now - rewardStart;
            if (!discoverSoundPlayed && elapsed >= DISCOVER_MINERAL_MS) {
                playSound(ModSounds.DISCOVER_MINERAL.get());
                discoverSoundPlayed = true;
            }
            if (!claimSent && elapsed >= GET_REWARD_MS) {
                playSound(ModSounds.COIN.get());
                PacketDistributor.sendToServer(new PrizeTicketClaimPayload());
                claimSent = true;
                waitingClaimResult = true;
            }
        } else if (movingRewardTrack) {
            long elapsed = now - trackMoveStart;
            if (!whirSoundPlayed && elapsed >= TRACK_PRE_MS) {
                playSound(ModSounds.TICKET_MACHINE_WHIR.get());
                whirSoundPlayed = true;
            }
            if (elapsed >= TRACK_PRE_MS + TRACK_MOVE_MS) {
                movingRewardTrack = false;
                currentPrizeTrack = new ArrayList<>(pendingPrizeTrack);
                pendingPrizeTrack = List.of();
            }
        }
    }

    private void drawRewardTrack(GuiGraphics graphics) {
        int slideOffset = 0;
        boolean shaking = false;
        if (movingRewardTrack) {
            long elapsed = Math.max(0L, System.currentTimeMillis() - trackMoveStart);
            long moveElapsed = Math.max(0L, elapsed - TRACK_PRE_MS);
            float xOffset = 88.0f - moveElapsed / 18.0f;
            if (xOffset > 0.0f) {
                slideOffset = Math.round(xOffset);
                shaking = elapsed >= TRACK_PRE_MS;
            }
        }

        graphics.enableScissor(
            x0 + ui(REWARD_TRACK_CLIP_X),
            y0 + ui(REWARD_TRACK_CLIP_Y),
            x0 + ui(REWARD_TRACK_CLIP_RIGHT_X),
            y0 + ui(REWARD_TRACK_CLIP_BOTTOM_Y)
        );
        try {
            for (int index = 0; index < currentPrizeTrack.size(); index++) {
                ItemStack stack = currentPrizeTrack.get(index).stack();
                int jitterX = shaking ? random.nextInt(3) - 1 : 0;
                int jitterY = shaking ? random.nextInt(3) - 1 : 0;
                int x = x0 + ui(FIRST_REWARD_X + REWARD_STEP_X * index + slideOffset + jitterX);
                int y = y0 + ui(REWARD_Y + jitterY);
                if (index == 0) {
                    graphics.fill(x0 + ui(100), y0 + ui(76), x0 + ui(188), y0 + ui(156), 0x54FFFFE0);
                }
                if (!gettingReward || index != 0) {
                    CommonGuiTextures.drawItemWithDecorations(graphics, font, stack, x, y, s4());
                }
            }
        } finally {
            graphics.disableScissor();
        }
    }

    private void drawFlyingReward(GuiGraphics graphics, ItemStack stack) {
        long elapsed = Math.min(GET_REWARD_MS, Math.max(0L, System.currentTimeMillis() - rewardStart));
        int yLift = Math.round(elapsed / 13.0f);
        int jitterX = Math.round(elapsed / 1000.0f * (random.nextInt(3) - 1));
        int jitterY = Math.round(elapsed / 1000.0f * (random.nextInt(3) - 1));
        int x = x0 + ui(FIRST_REWARD_X + jitterX);
        int y = y0 + Math.max(ui(REWARD_Y - yLift + jitterY), 0);
        CommonGuiTextures.drawItem(graphics, stack, x, y, s4());
    }

    private void drawTicketCount(GuiGraphics graphics) {
        String text = Integer.toString(displayTicketCount);
        int digitWidth = 8;
        int drawWidth = text.length() * digitWidth;
        graphics.pose().pushPose();
        graphics.pose().translate(x0 + ui(360), y0 + ui(276), 0);
        graphics.pose().scale(s4(), s4(), 1.0f);
        int x = -drawWidth / 2;
        for (int index = 0; index < text.length(); index++) {
            int digit = text.charAt(index) - '0';
            CommonGuiTextures.drawNumberDigitAtCurrentPoseTint(graphics, digit, x + index * digitWidth, 0,
                0.45f, 0.22f, 0.08f, 1.0f);
        }
        graphics.pose().popPose();
    }

    private void drawButton(GuiGraphics graphics, int mouseX, int mouseY) {
        ResourceLocation texture = BUTTON;
        boolean hovering = isButtonActive() && isIn(mouseX, mouseY, buttonX, buttonY, buttonW, buttonH);
        if (System.currentTimeMillis() - buttonPressStart <= BUTTON_PRESS_MS) {
            texture = BUTTON_PRESSED;
        } else if (hovering) {
            texture = BUTTON_HOVER;
        }
        if (hovering && !wasHoveringButton) {
            playSound(ModSounds.BUTTON_TAP.get());
        }
        wasHoveringButton = hovering;
        drawFullTexture(graphics, texture, BUTTON_SOURCE_W, BUTTON_SOURCE_H, buttonX, buttonY);
    }

    private void drawFullTexture(GuiGraphics graphics, ResourceLocation texture, int sourceW, int sourceH, int x, int y) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(s4(), s4(), 1.0f);
        graphics.blit(texture, 0, 0, 0, 0, sourceW, sourceH, sourceW, sourceH);
        graphics.pose().popPose();
    }

    private boolean isButtonActive() {
        return !gettingReward && !movingRewardTrack && !waitingClaimResult;
    }

    private int countPrizeTickets() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return 0;
        return minecraft.player.getInventory().countItem(ModItems.PRIZE_TICKET.get());
    }

    private static boolean isIn(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static List<PreviewSlot> toSlots(List<PrizeTicketRewardPreview> previews) {
        List<PreviewSlot> slots = new ArrayList<>(previews.size());
        for (PrizeTicketRewardPreview preview : previews) {
            ItemStack stack = toStack(preview);
            if (!stack.isEmpty()) {
                slots.add(new PreviewSlot(preview, stack));
            }
        }
        return slots;
    }

    private static ItemStack toStack(PrizeTicketRewardPreview preview) {
        ResourceLocation id = ResourceLocation.tryParse(preview.itemId());
        if (id == null) return ItemStack.EMPTY;
        return BuiltInRegistries.ITEM.getOptional(id)
            .map(item -> new ItemStack(item, Math.max(1, preview.count())))
            .orElse(ItemStack.EMPTY);
    }

    private static void playSound(SoundEvent sound) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0f, 1.0f));
    }

    private record PreviewSlot(PrizeTicketRewardPreview preview, ItemStack stack) {}
}
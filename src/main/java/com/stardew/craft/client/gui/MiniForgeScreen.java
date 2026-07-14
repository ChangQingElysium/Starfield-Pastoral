package com.stardew.craft.client.gui;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.ClientPlayerDataCache;
import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.client.gui.overnight.StardewGuiUtil;
import com.stardew.craft.forge.ForgeRuleService;
import com.stardew.craft.item.equipment.CombinedRingData;
import com.stardew.craft.menu.MiniForgeMenu;
import com.stardew.craft.network.payload.EquipmentActionPayload;
import com.stardew.craft.sound.ModSounds;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.Util;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

@SuppressWarnings("null")
public class MiniForgeScreen extends AbstractContainerScreen<MiniForgeMenu> {
    private static final ResourceLocation FORGE_MENU = forge("forge_menu");
    private static final ResourceLocation FORGE_BACKGROUND = forge("background");
    private static final ResourceLocation INGREDIENT_SLOT = forge("ingredient_slot");
    private static final ResourceLocation START_BUTTON = forge("start_button");
    private static final ResourceLocation START_BUTTON_ALT = forge("start_button_alt");
    private static final ResourceLocation UNFORGE_BUTTON = forge("unforge");
    private static final ResourceLocation ARROW = forge("arrow");
    private static final ResourceLocation[] COSTS = { forge("cost_10"), forge("cost_15"), forge("cost_20") };
    private static final ResourceLocation RING_FILLED = forge("ring_filled");
    private static final ResourceLocation RING_EMPTY = forge("ring_empty");
    private static final ResourceLocation CURSORS2 = ResourceLocation.fromNamespaceAndPath(
            StardewCraft.MODID, "textures/gui/cursors2.png");
    private static final ResourceLocation OK_BUTTON = ResourceLocation.fromNamespaceAndPath(
            StardewCraft.MODID, "textures/gui/geode/geode_ok_button.png");

    private static final int BORDER = 40;
    private static final int SP_TOP = 96;
    private static final int SP_SIDE = 16;
    private static final int SDV_W = 880;
    private static final int SDV_H = 756;

    private static final int FORGE_BACKGROUND_X = SP_SIDE + BORDER / 2 - 4;
    private static final int FORGE_BACKGROUND_Y = SP_TOP;
    private static final int DESC_X = 576 + 42;
    private static final int DESC_Y = BORDER + SP_TOP - 32;
    private static final int DESC_WIDTH = 224;
    private static final int DESC_HEIGHT = 288;
    private static final float DESC_FONT_SCALE = 0.75f;
    private static final float MIN_DESC_FONT_SCALE = 0.55f;
    private static final int HPART_Y = BORDER + SP_TOP + 256;
    private static final int VPART_X = 576;
    private static final int VPART_H = 328;
    private static final int OK_X = SDV_W + 4;
    private static final int OK_Y = SDV_H - 192 - BORDER;
    private static final int EQUIP_RING_X = -55;
    private static final int EQUIP_RING_Y = 192;
    private static final int EQUIP_RING_GAP = 64;

    private static final int INV_COLS = 9;
    private static final int INV_ROWS = 4;
    private static final int SDV_SLOT = 64;
    private static final int SDV_SLOT_GAP = 4;
    private static final int INV_X = (SDV_W - (INV_COLS * SDV_SLOT + (INV_COLS - 1) * SDV_SLOT_GAP)) / 2;
    private static final int INV_Y = 444;

    private static final int TEXT_COLOR = 0xFF5B2B16;
    private static final int DIM_TEXT_COLOR = 0x88706060;
    private static final int CRAFT_ANIMATION_MS = 1600;
    private static final int SPARKLING_MS = 900;
    private static final int FIRST_CLANK_MS = 300;
    private static final int CLANK_INTERVAL_MS = 450;
    private static final int CRYSTAL_FLIGHT_MS = 260;
    private static final float SDV_PIXEL_SCALE = 4.0f;

    private long craftStartMillis;
    private long sparklingStartMillis;
    private int lastClankElapsed;
    private int lastCrystalSoundIndex;
    private int pendingCrystalCount;
    private boolean pendingForge;
    private boolean pendingUnforge;
    private boolean pendingPrismaticForge;
    private boolean playedOpenSound;
    private final List<ForgeSprite> tempSprites = new ArrayList<>();

    public MiniForgeScreen(MiniForgeMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.inventoryLabelY = 0;
    }

    @Override
    protected void init() {
        this.imageWidth = ui(SDV_W);
        this.imageHeight = ui(SDV_H);
        super.init();

        this.imageWidth = ui(SDV_W);
        this.imageHeight = ui(SDV_H);
        this.leftPos = width / 2 - imageWidth / 2;
        this.topPos = height / 2 - imageHeight / 2;
        if (leftPos < 0) {
            leftPos = 0;
        }
        if (topPos < ui(8)) {
            topPos = ui(8);
        }

        positionSlotsForCustomLayout();
        resetAnimation();
        if (!playedOpenSound) {
            playedOpenSound = true;
            playSound(ModSounds.BIG_SELECT.get());
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        updateForgeAnimation();
        guiGraphics.fill(0, 0, width, height, 0x99000000);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderCustomTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int frameTopOff = ui(64);
        StardewGuiUtil.drawDialogueBoxFrame(guiGraphics, leftPos, topPos + frameTopOff, imageWidth, imageHeight - frameTopOff);
        drawPartitions(guiGraphics);
        drawInventory(guiGraphics, mouseX, mouseY);
        drawEquipmentRingSlots(guiGraphics, mouseX, mouseY);
        drawForgePanel(guiGraphics, mouseX, mouseY);
        renderDescription(guiGraphics);
        drawOkButton(guiGraphics);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }

    @Override
    protected void renderSlot(@Nonnull GuiGraphics guiGraphics, @Nonnull Slot slot) {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isBusy()) {
            return true;
        }
        if (button == 0 && isInsideSdv(mouseX, mouseY, OK_X, OK_Y, 64, 64)) {
            onClose();
            return true;
        }

        int ringSlot = ringSlotAt(mouseX, mouseY);
        if (button == 0 && ringSlot >= 0) {
            ItemStack equipped = equippedRingStack(ringSlot == EquipmentActionPayload.SLOT_LEFT_RING);
            PacketDistributor.sendToServer(new EquipmentActionPayload(ringSlot));
            playSound(menu.getCarried().isEmpty() && !equipped.isEmpty() ? ModSounds.DWOP.get() : ModSounds.CRIT.get());
            return true;
        }

        if (button == 0 && (isInsideSdv(mouseX, mouseY, MiniForgeMenu.LEFT_SLOT_X, MiniForgeMenu.LEFT_SLOT_Y, 64, 64)
                || isInsideSdv(mouseX, mouseY, MiniForgeMenu.RIGHT_SLOT_X, MiniForgeMenu.RIGHT_SLOT_Y, 64, 64))) {
            playSound(ModSounds.STONE_STEP.get());
        }

        if (button == 0 && isInsideSdv(mouseX, mouseY,
                MiniForgeMenu.START_BUTTON_X, MiniForgeMenu.START_BUTTON_Y,
                MiniForgeMenu.START_BUTTON_WIDTH, MiniForgeMenu.START_BUTTON_HEIGHT)) {
            if (currentCraftState() == ForgeRuleService.CraftState.VALID && menu.getCarried().isEmpty()) {
                startForgeAnimation();
            } else {
                playSound(ModSounds.SELL.get());
                logClientForgeFailure();
                requestServerForge();
            }
            return true;
        }

        if (button == 0 && isInsideSdv(mouseX, mouseY,
                MiniForgeMenu.UNFORGE_BUTTON_X, MiniForgeMenu.UNFORGE_BUTTON_Y, 44, 40)) {
            ItemStack left = menu.slots.get(MiniForgeMenu.LEFT_SLOT).getItem();
            ItemStack right = menu.slots.get(MiniForgeMenu.RIGHT_SLOT).getItem();
            if (ForgeRuleService.isValidUnforge(left, right)) {
                startUnforgeAnimation();
            } else {
                StardewCraft.LOGGER.warn("[MiniForgeClient] Unforge button blocked locally: left={}, right={}, validTarget={}",
                        stackSummary(left), stackSummary(right), ForgeRuleService.isValidUnforgeTarget(left));
                playSound(ModSounds.CANCEL.get());
            }
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void drawPartitions(GuiGraphics guiGraphics) {
        int tile = ui(64);
        int hpY = topPos + ui(HPART_Y);
        CommonGuiTextures.drawMenuTile(guiGraphics, leftPos, hpY, tile, tile, 4);
        CommonGuiTextures.drawMenuTile(guiGraphics, leftPos + tile, hpY, Math.max(0, imageWidth - tile * 2), tile, 6);
        CommonGuiTextures.drawMenuTile(guiGraphics, leftPos + imageWidth - tile, hpY, tile, tile, 7);

        int vpX = leftPos + ui(VPART_X);
        CommonGuiTextures.drawMenuTile(guiGraphics, vpX, topPos + ui(64), tile, tile, 44);
        CommonGuiTextures.drawMenuTile(guiGraphics, vpX, topPos + ui(128), tile, ui(VPART_H - 32), 63);
        CommonGuiTextures.drawMenuTile(guiGraphics, vpX, topPos + ui(VPART_H + 64), tile, tile, 39);
    }

    private void drawForgePanel(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        drawTexture(guiGraphics, FORGE_BACKGROUND, 142, 80,
                leftPos + ui(FORGE_BACKGROUND_X), topPos + ui(FORGE_BACKGROUND_Y), s4(), 1.0f);

        drawForgeSlot(guiGraphics, mouseX, mouseY, MiniForgeMenu.LEFT_SLOT, MiniForgeMenu.LEFT_SLOT_X, MiniForgeMenu.LEFT_SLOT_Y);
        drawForgeSlot(guiGraphics, mouseX, mouseY, MiniForgeMenu.RIGHT_SLOT, MiniForgeMenu.RIGHT_SLOT_X, MiniForgeMenu.RIGHT_SLOT_Y);

        ForgeRuleService.CraftState craftState = currentCraftState();
        float arrowAlpha = craftState == ForgeRuleService.CraftState.MISSING_SHARDS ? 0.5f : 1.0f;
        drawTexture(guiGraphics, ARROW, 17, 17, leftPos + ui(276), topPos + ui(300), s4(), arrowAlpha);

        if (shouldShowForgeCost()) {
            int costIndex = Math.max(0, Math.min(2, (currentForgeCost() - 10) / 5));
            float costAlpha = craftState == ForgeRuleService.CraftState.MISSING_SHARDS ? 0.5f : 1.0f;
                drawTexture(guiGraphics, COSTS[costIndex], 17, 10,
                    leftPos + ui(344), topPos + ui(320), s4(), costAlpha);
        }

        if (craftState == ForgeRuleService.CraftState.VALID && !isBusy()) {
            ResourceLocation button = (Util.getMillis() / 200L) % 2L == 0L ? START_BUTTON : START_BUTTON_ALT;
            drawTexture(guiGraphics, button, 13, 14,
                    leftPos + ui(MiniForgeMenu.START_BUTTON_X), topPos + ui(MiniForgeMenu.START_BUTTON_Y), s4(), 1.0f);
        }

        boolean canUnforge = ForgeRuleService.isValidUnforge(
            menu.slots.get(MiniForgeMenu.LEFT_SLOT).getItem(),
            menu.slots.get(MiniForgeMenu.RIGHT_SLOT).getItem());
        drawTexture(guiGraphics, UNFORGE_BUTTON, 11, 10,
            leftPos + ui(MiniForgeMenu.UNFORGE_BUTTON_X), topPos + ui(MiniForgeMenu.UNFORGE_BUTTON_Y),
            s4(), canUnforge && !isBusy() ? 1.0f : 0.45f);

        ItemStack previewResult = currentPreview().result();
        if (craftState == ForgeRuleService.CraftState.VALID && !previewResult.isEmpty() && !isBusy()) {
            drawResult(guiGraphics, previewResult);
        }

        drawForgeAnimation(guiGraphics);
    }

    private void drawForgeSlot(GuiGraphics guiGraphics, int mouseX, int mouseY, int slotIndex, int sdvX, int sdvY) {
        int x = leftPos + ui(sdvX);
        int y = topPos + ui(sdvY);
        int size = ui(SDV_SLOT);
        drawTexture(guiGraphics, INGREDIENT_SLOT, 16, 16, x, y, s4(), 1.0f);
        if (isInside(mouseX, mouseY, x, y, size, size)) {
            guiGraphics.fill(x, y, x + size, y + size, 0x40FFFFFF);
        }
        Slot slot = menu.slots.get(slotIndex);
        if (slot.hasItem()) {
            int shakeX = 0;
            int shakeY = 0;
            if (slotIndex == MiniForgeMenu.LEFT_SLOT && shouldShakeLeftSlot()) {
                int seed = Math.max(0, elapsedCraftMillis() / 45);
                shakeX = ui(((seed * 17) % 3) - 1);
                shakeY = ui(((seed * 31) % 3) - 1);
            }
            CommonGuiTextures.drawItemWithDecorationsCenteredInBox(guiGraphics, font, slot.getItem(), x + shakeX, y + shakeY, size, size, s4());
        }
    }

    private void drawEquipmentRingSlots(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        drawEquipmentRingSlot(guiGraphics, mouseX, mouseY, EquipmentActionPayload.SLOT_LEFT_RING);
        drawEquipmentRingSlot(guiGraphics, mouseX, mouseY, EquipmentActionPayload.SLOT_RIGHT_RING);
    }

    private void drawEquipmentRingSlot(GuiGraphics guiGraphics, int mouseX, int mouseY, int slot) {
        int x = leftPos + ui(EQUIP_RING_X);
        int y = topPos + ui(EQUIP_RING_Y + slot * EQUIP_RING_GAP);
        int size = ui(SDV_SLOT);
        ItemStack stack = equippedRingStack(slot == EquipmentActionPayload.SLOT_LEFT_RING);
        drawTexture(guiGraphics, stack.isEmpty() ? RING_EMPTY : RING_FILLED, 16, 16, x, y, s4(), 1.0f);
        if (!stack.isEmpty()) {
            CommonGuiTextures.drawItemWithDecorationsCenteredInBox(guiGraphics, font, stack, x, y, size, size, s4());
        }
        if (isInside(mouseX, mouseY, x, y, size, size)) {
            guiGraphics.fill(x, y, x + size, y + size, 0x35FFFFFF);
        }
    }

    private void startForgeAnimation() {
        pendingForge = true;
        pendingPrismaticForge = menu.slots.get(MiniForgeMenu.RIGHT_SLOT).getItem().is(com.stardew.craft.item.ModItems.PRISMATIC_SHARD.get());
        pendingCrystalCount = Math.max(1, currentForgeCost());
        craftStartMillis = Util.getMillis();
        sparklingStartMillis = pendingPrismaticForge ? craftStartMillis : 0L;
        lastClankElapsed = 0;
        lastCrystalSoundIndex = -1;
        tempSprites.clear();
        playSound(ModSounds.BIG_SELECT.get());
        queueCinderShardSprites(pendingCrystalCount);
        if (pendingPrismaticForge) {
            playSound(ModSounds.DISCOVER_MINERAL.get());
            queuePrismaticStartSprites();
        }
    }

    private void updateForgeAnimation() {
        if (!isBusy()) {
            return;
        }
        int elapsed = elapsedCraftMillis();
        if (pendingUnforge) {
            if (elapsed >= CRAFT_ANIMATION_MS) {
                finishUnforgeAnimation();
            }
            return;
        }
        playCrystalSounds(elapsed);
        if (!pendingPrismaticForge && elapsed >= FIRST_CLANK_MS
                && elapsed - lastClankElapsed >= (lastClankElapsed == 0 ? FIRST_CLANK_MS : CLANK_INTERVAL_MS)
                && elapsed < CRAFT_ANIMATION_MS) {
            lastClankElapsed = elapsed;
            playSound(ModSounds.CRAFTING.get());
            playSound(ModSounds.CLANK.get());
            queueClankSparkSprites(elapsed);
        }
        if (elapsed >= CRAFT_ANIMATION_MS) {
            finishForgeAnimation();
        }
    }

    private void playCrystalSounds(int elapsed) {
        int duration = Math.max(1, CRAFT_ANIMATION_MS - 200);
        int index = Mth.clamp((elapsed * pendingCrystalCount) / duration, 0, pendingCrystalCount - 1);
        while (lastCrystalSoundIndex < index) {
            lastCrystalSoundIndex++;
            playSound(ModSounds.BOULDER_CRACK.get(), 0.7f, 1.0f);
        }
    }

    private void finishForgeAnimation() {
        if (minecraft != null && minecraft.gameMode != null) {
            requestServerForge();
            playSound(ModSounds.COIN.get());
        }
        resetAnimation();
    }

    private void startUnforgeAnimation() {
        pendingUnforge = true;
        pendingForge = false;
        pendingPrismaticForge = false;
        pendingCrystalCount = Math.max(1, ForgeRuleService.unforgeForReal(
            menu.slots.get(MiniForgeMenu.LEFT_SLOT).getItem(),
            menu.slots.get(MiniForgeMenu.RIGHT_SLOT).getItem()).cinderShardRefund());
        craftStartMillis = Util.getMillis();
        lastClankElapsed = 0;
        lastCrystalSoundIndex = -1;
        tempSprites.clear();
        queueUnforgeShardSprites(pendingCrystalCount);
        playSound(ModSounds.DEBUFF_HIT.get());
    }

    private void finishUnforgeAnimation() {
        executeUnforge();
        resetAnimation();
    }

    private void requestServerForge() {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, MiniForgeMenu.ACTION_FORGE);
        }
    }

    private void logClientForgeFailure() {
        ItemStack left = menu.slots.get(MiniForgeMenu.LEFT_SLOT).getItem();
        ItemStack right = menu.slots.get(MiniForgeMenu.RIGHT_SLOT).getItem();
        StardewCraft.LOGGER.warn("[MiniForgeClient] Forge button blocked locally: state={}, cost={}, shards={}, carried={}, left={}, right={}",
                currentCraftState(), currentForgeCost(), currentCinderShardCount(), stackSummary(menu.getCarried()), stackSummary(left), stackSummary(right));
    }

    private void executeUnforge() {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, MiniForgeMenu.ACTION_UNFORGE);
            playSound(ModSounds.COIN.get());
        }
    }

    private void resetAnimation() {
        pendingForge = false;
        pendingUnforge = false;
        pendingPrismaticForge = false;
        craftStartMillis = 0L;
        sparklingStartMillis = 0L;
        lastClankElapsed = 0;
        lastCrystalSoundIndex = -1;
        pendingCrystalCount = 0;
        tempSprites.clear();
    }

    private boolean isBusy() {
        return pendingForge || pendingUnforge;
    }

    private int elapsedCraftMillis() {
        if (craftStartMillis == 0L) {
            return 0;
        }
        return (int) Math.max(0L, Util.getMillis() - craftStartMillis);
    }

    private boolean shouldShakeLeftSlot() {
        int elapsed = elapsedCraftMillis();
        if (!isBusy()) {
            return false;
        }
        return elapsed < CRAFT_ANIMATION_MS && (pendingUnforge || elapsed < 300 || elapsed - lastClankElapsed < 160);
    }

    private void drawForgeAnimation(GuiGraphics guiGraphics) {
        if (!isBusy()) {
            return;
        }
        int elapsed = elapsedCraftMillis();
        drawTempSprites(guiGraphics, elapsed);
        if (pendingPrismaticForge) {
            drawPrismaticSparkles(guiGraphics, elapsed);
        }
    }

    private void queueCinderShardSprites(int crystals) {
        for (int i = 0; i < crystals; i++) {
            tempSprites.add(new ForgeSprite(FORGE_MENU, 159, 112, 143, 17, 14, 15,
                    276.0f, 300.0f, -4.0f, -4.0f, 0.0f, 0.0f, 4.0f,
                    1400 * i / Math.max(1, crystals), CRYSTAL_FLIGHT_MS, 1.0f, 0.0f));
        }
    }

    private void queueUnforgeShardSprites(int crystals) {
        for (int i = 0; i < crystals; i++) {
            int seed = i + 1;
            float motionX = (seed * 17 % 9) - 4;
            float motionY = (seed * 31 % 9) - 4;
            if (motionX == 0.0f && motionY == 0.0f) {
                motionX = -4.0f;
                motionY = -4.0f;
            }
            tempSprites.add(new ForgeSprite(FORGE_MENU, 159, 112, 143, 17, 14, 15,
                    MiniForgeMenu.LEFT_SLOT_X, MiniForgeMenu.LEFT_SLOT_Y,
                    motionX, motionY, 0.0f, 0.0f, 4.0f,
                    1100 * i / Math.max(1, crystals), 500, 0.01f, 0.1f));
        }
    }

    private void queueClankSparkSprites(int elapsed) {
        int baseX = MiniForgeMenu.LEFT_SLOT_X + 16 + Math.floorMod(elapsed / 37, 22);
        int baseY = MiniForgeMenu.LEFT_SLOT_Y + 16 + Math.floorMod(elapsed / 53, 22);
        tempSprites.add(new ForgeSprite(CURSORS2, 256, 320, 114, 46, 2, 2, baseX, baseY, -1.0f, -10.0f, 0.0f, 0.6f, 4.0f, elapsed, 360, 1.0f, -0.015f));
        tempSprites.add(new ForgeSprite(CURSORS2, 256, 320, 114, 46, 2, 2, baseX, baseY, 0.0f, -8.0f, 0.0f, 0.48f, 4.0f, elapsed, 360, 1.0f, -0.015f));
        tempSprites.add(new ForgeSprite(CURSORS2, 256, 320, 114, 46, 2, 2, baseX, baseY, 1.0f, -10.0f, 0.0f, 0.6f, 4.0f, elapsed, 360, 1.0f, -0.015f));
        tempSprites.add(new ForgeSprite(CURSORS2, 256, 320, 114, 46, 2, 2, baseX, baseY, -2.0f, -8.0f, 0.0f, 0.6f, 2.0f, elapsed, 360, 1.0f, -0.015f));
        tempSprites.add(new ForgeSprite(CURSORS2, 256, 320, 114, 46, 2, 2, baseX, baseY, 2.0f, -8.0f, 0.0f, 0.6f, 2.0f, elapsed, 360, 1.0f, -0.015f));
    }

    private void queuePrismaticStartSprites() {
        for (int i = 0; i < 6; i++) {
            tempSprites.add(new ForgeSprite(CURSORS2, 256, 320, 114, 48, 2, 2,
                    MiniForgeMenu.LEFT_SLOT_X - 32 + Math.floorMod(i * 37, 128),
                    MiniForgeMenu.LEFT_SLOT_Y - 32 + Math.floorMod(i * 53, 128),
                    0.0f, 0.0f, 0.0f, 0.0f, 4.0f, i * 80, 1600, 1.0f, -0.004f));
        }
    }

    private void drawTempSprites(GuiGraphics guiGraphics, int elapsed) {
        for (ForgeSprite sprite : tempSprites) {
            sprite.draw(guiGraphics, elapsed);
        }
    }

    private void drawPrismaticSparkles(GuiGraphics guiGraphics, int elapsed) {
        int sparkleElapsed = sparklingStartMillis == 0L ? elapsed : (int) Math.max(0L, Util.getMillis() - sparklingStartMillis);
        if (sparkleElapsed > CRAFT_ANIMATION_MS + SPARKLING_MS) {
            return;
        }
        drawSparkleArea(guiGraphics, MiniForgeMenu.LEFT_SLOT_X - 32, MiniForgeMenu.LEFT_SLOT_Y - 32, 128, 128, sparkleElapsed, 6);
        drawSparkleArea(guiGraphics, MiniForgeMenu.RIGHT_SLOT_X + 16, MiniForgeMenu.RIGHT_SLOT_Y + 16, 32, 32, sparkleElapsed, 30);
    }

    private void drawSparkleArea(GuiGraphics guiGraphics, int sdvX, int sdvY, int sdvW, int sdvH, int elapsed, int count) {
        for (int i = 0; i < count; i++) {
            int localTime = Math.floorMod(elapsed + i * 113, 1600);
            float progress = localTime / 1600.0f;
            int x = leftPos + ui(sdvX + Math.floorMod(i * 37, Math.max(1, sdvW))) - Math.round(ui((int) (32 * progress)));
            int y = topPos + ui(sdvY + Math.floorMod(i * 53, Math.max(1, sdvH))) + Math.round(ui((int) (Mth.sin(progress * Mth.TWO_PI) * 8)));
            float alpha = 1.0f - progress * 0.5f;
            drawTextureRegion(guiGraphics, CURSORS2, 256, 320, 114, 48, 2, 2, x, y, s4(), alpha);
        }
    }

    private void drawResult(GuiGraphics guiGraphics, ItemStack stack) {
        int x = leftPos + ui(MiniForgeMenu.RESULT_SLOT_X);
        int y = topPos + ui(MiniForgeMenu.RESULT_SLOT_Y);
        String text = Component.translatable("stardewcraft.forge.make_result").getString();
        int centerX = x + ui(32);
        float textScale = Math.min(0.75f, Math.max(0.55f, (ui(112) / (float) Math.max(1, font.width(text)))));
        drawScaledText(guiGraphics, text, centerX - Math.round(font.width(text) * textScale / 2.0f), y - Math.round((font.lineHeight + 4) * textScale), textScale, TEXT_COLOR);
        CommonGuiTextures.drawItemWithDecorationsCenteredInBox(guiGraphics, font, stack, x, y, ui(SDV_SLOT), ui(SDV_SLOT), s4());
    }

    private void drawInventory(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        for (int row = 0; row < INV_ROWS; row++) {
            for (int col = 0; col < INV_COLS; col++) {
                int slotIndex = inventorySlotIndex(row, col);
                Slot slot = menu.slots.get(slotIndex);
                int x = leftPos + ui(INV_X + col * (SDV_SLOT + SDV_SLOT_GAP));
                int y = topPos + ui(INV_Y + row * (SDV_SLOT + SDV_SLOT_GAP));
                int size = ui(SDV_SLOT);
                CommonGuiTextures.drawItemSlot18(guiGraphics, x, y, size / 18.0f);
                if (isInside(mouseX, mouseY, x, y, size, size)) {
                    guiGraphics.fill(x, y, x + size, y + size, 0x40FFFFFF);
                }
                if (slot.hasItem()) {
                    CommonGuiTextures.drawItemWithDecorationsCenteredInBox(guiGraphics, font, slot.getItem(), x, y, size, size, s4());
                }
            }
        }
    }

    private void renderDescription(GuiGraphics guiGraphics) {
        Component description = isBusy()
            ? Component.translatable(pendingPrismaticForge ? "stardewcraft.forge.enchanting" : "stardewcraft.forge.forging")
            : Component.translatable(currentCraftState().translationKey());
        int textX = leftPos + ui(DESC_X);
        int textY = topPos + ui(DESC_Y);
        int maxWidth = ui(DESC_WIDTH);
        int maxHeight = ui(DESC_HEIGHT);
        float textScale = fitTextScale(description, maxWidth, maxHeight, DESC_FONT_SCALE, MIN_DESC_FONT_SCALE);
        List<FormattedCharSequence> lines = font.split(description, Math.max(1, Math.round(maxWidth / textScale)));
        int lineY = textY;
        int lineStep = Math.max(1, Math.round((font.lineHeight + 2) * textScale));
        int bottom = textY + maxHeight;
        for (FormattedCharSequence line : lines) {
            if (lineY + Math.round(font.lineHeight * textScale) > bottom) {
                break;
            }
            drawScaledText(guiGraphics, line, textX, lineY, textScale, TEXT_COLOR);
            lineY += lineStep;
        }

        if (shouldShowForgeCost()) {
            Component shards = Component.translatable("stardewcraft.forge.shards_count", currentCinderShardCount(), currentForgeCost());
            int shardY = lineY + ui(8);
            if (shardY + Math.round(font.lineHeight * textScale) <= bottom) {
                drawScaledText(guiGraphics, shards.getString(), textX, shardY, textScale, DIM_TEXT_COLOR);
            }
        }
    }

    private void positionSlotsForCustomLayout() {
        positionSlot(MiniForgeMenu.LEFT_SLOT, MiniForgeMenu.LEFT_SLOT_X, MiniForgeMenu.LEFT_SLOT_Y, SDV_SLOT);
        positionSlot(MiniForgeMenu.RIGHT_SLOT, MiniForgeMenu.RIGHT_SLOT_X, MiniForgeMenu.RIGHT_SLOT_Y, SDV_SLOT);
        positionSlot(MiniForgeMenu.RESULT_SLOT, MiniForgeMenu.RESULT_SLOT_X, MiniForgeMenu.RESULT_SLOT_Y, SDV_SLOT);
        for (int row = 0; row < INV_ROWS; row++) {
            for (int col = 0; col < INV_COLS; col++) {
                positionSlot(inventorySlotIndex(row, col), INV_X + col * (SDV_SLOT + SDV_SLOT_GAP),
                        INV_Y + row * (SDV_SLOT + SDV_SLOT_GAP), SDV_SLOT);
            }
        }
    }

    private void positionSlot(int slotIndex, int sdvX, int sdvY, int sdvSize) {
        Slot slot = menu.slots.get(slotIndex);
        int offset = Math.max(0, (ui(sdvSize) - 16) / 2);
        slot.x = ui(sdvX) + offset;
        slot.y = ui(sdvY) + offset;
    }

    private boolean shouldShowForgeCost() {
        ForgeRuleService.CraftState state = currentCraftState();
        return currentForgeCost() > 0
                && state != ForgeRuleService.CraftState.MISSING_INGREDIENTS
                && state != ForgeRuleService.CraftState.INVALID_RECIPE;
    }

    private ForgeRuleService.Preview currentPreview() {
        if (minecraft == null || minecraft.player == null) {
            return new ForgeRuleService.Preview(menu.getCraftState(), menu.getForgeCost(), ItemStack.EMPTY);
        }
        return ForgeRuleService.preview(
                menu.slots.get(MiniForgeMenu.LEFT_SLOT).getItem(),
                menu.slots.get(MiniForgeMenu.RIGHT_SLOT).getItem(),
                minecraft.player);
    }

    private ForgeRuleService.CraftState currentCraftState() {
        return currentPreview().state();
    }

    private int currentForgeCost() {
        return currentPreview().cost();
    }

    private int currentCinderShardCount() {
        if (minecraft == null || minecraft.player == null) {
            return menu.getCinderShardCount();
        }
        return ForgeRuleService.countCinderShards(minecraft.player.getInventory());
    }

    private float fitTextScale(Component text, int maxWidth, int maxHeight, float preferredScale, float minScale) {
        float scale = preferredScale;
        while (scale > minScale) {
            int wrapWidth = Math.max(1, Math.round(maxWidth / scale));
            int height = font.split(text, wrapWidth).size() * Math.max(1, Math.round((font.lineHeight + 2) * scale));
            if (height <= maxHeight) {
                return scale;
            }
            scale -= 0.05f;
        }
        return minScale;
    }

    private void drawScaledText(GuiGraphics guiGraphics, String text, int x, int y, float scale, int color) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0);
        guiGraphics.pose().scale(scale, scale, 1.0f);
        guiGraphics.drawString(font, text, 0, 0, color, false);
        guiGraphics.pose().popPose();
    }

    private void drawScaledText(GuiGraphics guiGraphics, FormattedCharSequence text, int x, int y, float scale, int color) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0);
        guiGraphics.pose().scale(scale, scale, 1.0f);
        guiGraphics.drawString(font, text, 0, 0, color, false);
        guiGraphics.pose().popPose();
    }

    private void drawOkButton(GuiGraphics guiGraphics) {
        drawTexture(guiGraphics, OK_BUTTON, 64, 64, leftPos + ui(OK_X), topPos + ui(OK_Y), s1(), 1.0f);
    }

    private void renderCustomTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int ringSlot = ringSlotAt(mouseX, mouseY);
        if (ringSlot >= 0) {
            ItemStack ringStack = equippedRingStack(ringSlot == EquipmentActionPayload.SLOT_LEFT_RING);
            if (!ringStack.isEmpty()) {
                guiGraphics.renderTooltip(font, ringStack, mouseX, mouseY);
            } else {
                guiGraphics.renderTooltip(font, Component.translatable(ringSlot == EquipmentActionPayload.SLOT_LEFT_RING
                        ? "stardewcraft.equipment.slot.left_ring"
                        : "stardewcraft.equipment.slot.right_ring"), mouseX, mouseY);
            }
            return;
        }
        int slotIndex = slotAt(mouseX, mouseY);
        if (slotIndex >= 0) {
            ItemStack stack = menu.slots.get(slotIndex).getItem();
            if (!stack.isEmpty()) {
                guiGraphics.renderTooltip(font, stack, mouseX, mouseY);
            }
        }
    }

    private int slotAt(double mouseX, double mouseY) {
        if (isInsideSdv(mouseX, mouseY, MiniForgeMenu.LEFT_SLOT_X, MiniForgeMenu.LEFT_SLOT_Y, 64, 64)) {
            return MiniForgeMenu.LEFT_SLOT;
        }
        if (isInsideSdv(mouseX, mouseY, MiniForgeMenu.RIGHT_SLOT_X, MiniForgeMenu.RIGHT_SLOT_Y, 64, 64)) {
            return MiniForgeMenu.RIGHT_SLOT;
        }
        if (isInsideSdv(mouseX, mouseY, MiniForgeMenu.RESULT_SLOT_X, MiniForgeMenu.RESULT_SLOT_Y, 64, 64)) {
            return MiniForgeMenu.RESULT_SLOT;
        }
        return inventorySlotAt(mouseX, mouseY);
    }

    private int inventorySlotAt(double mouseX, double mouseY) {
        for (int row = 0; row < INV_ROWS; row++) {
            for (int col = 0; col < INV_COLS; col++) {
                int x = leftPos + ui(INV_X + col * (SDV_SLOT + SDV_SLOT_GAP));
                int y = topPos + ui(INV_Y + row * (SDV_SLOT + SDV_SLOT_GAP));
                if (isInside(mouseX, mouseY, x, y, ui(SDV_SLOT), ui(SDV_SLOT))) {
                    return inventorySlotIndex(row, col);
                }
            }
        }
        return -1;
    }

    private int ringSlotAt(double mouseX, double mouseY) {
        if (isInsideSdv(mouseX, mouseY, EQUIP_RING_X, EQUIP_RING_Y, 64, 64)) {
            return EquipmentActionPayload.SLOT_LEFT_RING;
        }
        if (isInsideSdv(mouseX, mouseY, EQUIP_RING_X, EQUIP_RING_Y + EQUIP_RING_GAP, 64, 64)) {
            return EquipmentActionPayload.SLOT_RIGHT_RING;
        }
        return -1;
    }

    private ItemStack equippedRingStack(boolean left) {
        return left ? ClientPlayerDataCache.getEquippedLeftRingStack() : ClientPlayerDataCache.getEquippedRightRingStack();
    }

    private static int inventorySlotIndex(int row, int col) {
        return MiniForgeMenu.PLAYER_INV_START + row * INV_COLS + col;
    }

    private boolean isInsideSdv(double mouseX, double mouseY, int sdvX, int sdvY, int sdvWidth, int sdvHeight) {
        return isInside(mouseX, mouseY, leftPos + ui(sdvX), topPos + ui(sdvY), ui(sdvWidth), ui(sdvHeight));
    }

    private static boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private void drawTexture(GuiGraphics guiGraphics, ResourceLocation texture, int textureWidth, int textureHeight,
            int x, int y, float scale, float alpha) {
        drawTextureRegion(guiGraphics, texture, textureWidth, textureHeight, 0, 0, textureWidth, textureHeight, x, y, scale, alpha);
    }

    private void drawTextureRegion(GuiGraphics guiGraphics, ResourceLocation texture, int textureWidth, int textureHeight,
            int sourceX, int sourceY, int sourceWidth, int sourceHeight, int x, int y, float scale, float alpha) {
        guiGraphics.setColor(1.0f, 1.0f, 1.0f, alpha);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0);
        guiGraphics.pose().scale(scale, scale, 1.0f);
        guiGraphics.blit(texture, 0, 0, sourceX, sourceY, sourceWidth, sourceHeight, textureWidth, textureHeight);
        guiGraphics.pose().popPose();
        guiGraphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private void playSound(SoundEvent sound) {
        playSound(sound, 1.0f, 1.0f);
    }

    private void playSound(SoundEvent sound, float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    private int ui(int value) {
        return Math.round(value / SDV_PIXEL_SCALE);
    }

    private float s4() {
        return 1.0f;
    }

    private float s1() {
        return 1.0f / SDV_PIXEL_SCALE;
    }

    private final class ForgeSprite {
        private final ResourceLocation texture;
        private final int textureWidth;
        private final int textureHeight;
        private final int sourceX;
        private final int sourceY;
        private final int sourceWidth;
        private final int sourceHeight;
        private final float startX;
        private final float startY;
        private final float motionX;
        private final float motionY;
        private final float accelerationX;
        private final float accelerationY;
        private final float sdvScale;
        private final int delay;
        private final int duration;
        private final float startAlpha;
        private final float alphaFadePerFrame;

        private ForgeSprite(ResourceLocation texture, int textureWidth, int textureHeight, int sourceX, int sourceY,
                int sourceWidth, int sourceHeight, float startX, float startY, float motionX, float motionY,
                float accelerationX, float accelerationY, float sdvScale, int delay, int duration,
                float startAlpha, float alphaFadePerFrame) {
            this.texture = texture;
            this.textureWidth = textureWidth;
            this.textureHeight = textureHeight;
            this.sourceX = sourceX;
            this.sourceY = sourceY;
            this.sourceWidth = sourceWidth;
            this.sourceHeight = sourceHeight;
            this.startX = startX;
            this.startY = startY;
            this.motionX = motionX;
            this.motionY = motionY;
            this.accelerationX = accelerationX;
            this.accelerationY = accelerationY;
            this.sdvScale = sdvScale;
            this.delay = delay;
            this.duration = duration;
            this.startAlpha = startAlpha;
            this.alphaFadePerFrame = alphaFadePerFrame;
        }

        private void draw(GuiGraphics guiGraphics, int elapsed) {
            int age = elapsed - delay;
            if (age < 0 || age > duration) {
                return;
            }
            float frames = age / (1000.0f / 60.0f);
            float sdvX = startX + motionX * frames + 0.5f * accelerationX * frames * frames;
            float sdvY = startY + motionY * frames + 0.5f * accelerationY * frames * frames;
            float alpha = Mth.clamp(startAlpha + alphaFadePerFrame * frames, 0.0f, 1.0f);
            if (alpha <= 0.0f) {
                return;
            }
            drawTextureRegion(guiGraphics, texture, textureWidth, textureHeight,
                    sourceX, sourceY, sourceWidth, sourceHeight,
                    leftPos + ui(Math.round(sdvX)), topPos + ui(Math.round(sdvY)), sdvScale / SDV_PIXEL_SCALE, alpha);
        }
    }

    private static String stackSummary(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "empty";
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()) + "x" + stack.getCount();
    }

    private static ResourceLocation forge(String name) {
        return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/forge/" + name + ".png");
    }
}

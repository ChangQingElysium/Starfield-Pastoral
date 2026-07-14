package com.stardew.craft.communitycenter.client;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.gui.common.GuiText;
import com.stardew.craft.client.gui.LocalizedGuiAssets;
import com.stardew.craft.client.gui.common.StardewRenderMapping;
import com.stardew.craft.client.gui.overnight.StardewGuiUtil;
import com.stardew.craft.communitycenter.data.BundleDataManager;
import com.stardew.craft.communitycenter.data.BundleDefinition;
import com.stardew.craft.communitycenter.data.BundleIngredient;
import com.stardew.craft.communitycenter.data.BundleItemResolver;
import com.stardew.craft.communitycenter.menu.BundleMenu;
import com.stardew.craft.communitycenter.network.BundleClientData;
import com.stardew.craft.communitycenter.network.BundleDepositPayload;
import com.stardew.craft.communitycenter.network.BundlePartialDepositPayload;
import com.stardew.craft.communitycenter.network.BundlePartialRetrievePayload;
import com.stardew.craft.communitycenter.network.BundlePurchasePayload;
import com.stardew.craft.item.quality.QualityHelper;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Client-side GUI for Community Center bundle interface.
 * Pixel-perfect replication of SDV JunimoNoteMenu.cs draw().
 *
 * Two-page mode:
 * 1. Room overview (specificBundlePage = false): shows all bundle orbs
 * 2. Bundle detail (specificBundlePage = true): shows ingredient slots + inventory
 */
@SuppressWarnings("null")
public class BundleScreen extends AbstractContainerScreen<BundleMenu> {

    // ── Texture constants ──
    private static final ResourceLocation JUNIMO_NOTE = ResourceLocation.fromNamespaceAndPath(
            StardewCraft.MODID, "textures/gui/junimo_note.png");
    private static final int TEX_WIDTH = 640;
    private static final int TEX_HEIGHT = 308;

    // Area Next/Back arrows (SDV mouseCursors 352,495,12,11 / 365,495,12,11)
    private static final ResourceLocation AREA_BACK_ARROW = ResourceLocation.fromNamespaceAndPath(
            StardewCraft.MODID, "textures/gui/area_back_arrow.png");
    private static final ResourceLocation AREA_NEXT_ARROW = ResourceLocation.fromNamespaceAndPath(
            StardewCraft.MODID, "textures/gui/area_next_arrow.png");
    private static final int AREA_ARROW_W = 12;
    private static final int AREA_ARROW_H = 11;

    // SDV menu base dimensions (sprite pixels, before ×4 scale)
    private static final int BASE_W = 320;
    private static final int BASE_H = 180;

    // ── State ──
    private StardewRenderMapping mapping;
    private float s4;
    private int menuX, menuY;

    private boolean specificBundlePage = false;
    private int selectedBundleId = -1;
    private int lastSyncedAreaId = -1;
    private int lastDataVersion = -1;

    // Bundle orbs for room overview
    private final List<BundleOrb> bundleOrbs = new ArrayList<>();

    // Ingredient slots for detail page (submission slots)
    private final List<IngredientSlotVisual> ingredientSlots = new ArrayList<>();
    // Ingredient list for detail page (required items display)
    private final List<IngredientListEntry> ingredientList = new ArrayList<>();

    // Present button state (reward claiming)
    private boolean showPresentButton = false;
    private int presentAnimFrame = 0;
    private float presentAnimTimer = 0;

    // Animation
    private long lastTickMs = 0;

    // canClick guard — disabled during completion animation (SDV parity)
    private boolean canClick = true;

    // Completion tracking (for client-side feedback)
    private java.util.Set<Integer> previouslyCompleteBundles = new java.util.HashSet<>();

    // SDV ScreenSwipe — bundle completion banner
    private ScreenSwipe screenSwipe = null;
    // Tracks if user clicked during swipe (to skip pause)
    private boolean screenSwipeClicked = false;

    // tempSprites system (SDV parity: deposit animations, sparkles, etc.)
    private final List<TempSprite> tempSprites = new ArrayList<>();

    // ── SDV Partial Donation State (client-side mirror) ──
    // Tracks which ingredient slot has a partial donation, and the accumulated count.
    // The actual items are managed server-side; client only tracks visuals.
    @Nullable private ItemStack partialDonationItem = null;
    private int partialIngredientIndex = -1; // index into ingredients list
    private int partialSlotIndex = -1;       // index into ingredientSlots visual list
    private final List<ItemStack> partialDonationComponents = new ArrayList<>();

    // SDV parity: track locally-completed ingredients (client prediction).
    // SDV marks ingredient.completed = true in-place; we can't mutate BundleDefinition,
    // so we keep a local set. Cleared when page is closed/rebuilt.
    private final java.util.Set<Integer> localCompletedIngredients = new java.util.HashSet<>();

    // ── SDV Bundle orb positions (from getBundleLocationFromNumber) ──
    // These are pixel offsets from (xPositionOnScreen, yPositionOnScreen)
    private static final int[][] BUNDLE_POSITIONS = {
            {592, 136},  // 0
            {392, 384},  // 1
            {784, 388},  // 2
            {304, 252},  // 3
            {892, 252},  // 4
            {588, 276},  // 5
            {588, 380},  // 6
            {440, 164},  // 7
            {776, 164},  // 8
    };

    public BundleScreen(BundleMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();

        float guiScale = (float) this.minecraft.getWindow().getGuiScale();
        this.mapping = new StardewRenderMapping(this.width, this.height, guiScale);
        this.s4 = mapping.s4();

        // SDV: xPositionOnScreen = viewport.Width/2 - 640, yPositionOnScreen = viewport.Height/2 - 360
        this.menuX = (this.width - mapping.ui(1280)) / 2;
        this.menuY = (this.height - mapping.ui(720)) / 2;

        // Anchor inventory slot rendering relative to menu origin
        this.imageWidth = mapping.ui(1280);
        this.imageHeight = mapping.ui(720);
        this.leftPos = menuX;
        this.topPos = menuY;

        // Reposition MC inventory slots to match SDV InventoryMenu(x+128, y+140, capacity=36, rows=6).
        // SDV: 6 columns × 6 rows, slot spacing 72 screen px.
        // Slot spacing must scale with guiScale to match the texture grid at all GUI scales.
        int invOffX = mapping.ui(128);
        int invOffY = mapping.ui(140);
        int slotSpacing = mapping.ui(72);  // 72 SDV screen px → matches texture grid
        for (int i = 0; i < this.menu.slots.size(); i++) {
            net.minecraft.world.inventory.Slot slot = this.menu.slots.get(i);
            int col = i % 6;
            int row = i / 6;
            slot.x = invOffX + col * slotSpacing;
            slot.y = invOffY + row * slotSpacing;
        }

        buildBundleOrbs();

        this.lastTickMs = System.currentTimeMillis();
    }

    private void buildBundleOrbs() {
        bundleOrbs.clear();
        int areaId = this.menu.getAreaId();
        List<BundleDefinition> defs = BundleDataManager.getBundlesForArea(areaId);
        BundleClientData cd = BundleClientData.INSTANCE;

        for (int i = 0; i < defs.size(); i++) {
            BundleDefinition def = defs.get(i);
            int[] pos = (i < BUNDLE_POSITIONS.length) ? BUNDLE_POSITIONS[i] : BUNDLE_POSITIONS[0];

            // SDV positions are screen-pixel offsets from menu origin
            int orbX = menuX + mapping.ui(pos[0]);
            int orbY = menuY + mapping.ui(pos[1]);

            // Sprite source rect: (bundleColor * 256 % 512, 244 + bundleColor * 256 / 512 * 16, 16, 16)
            int color = def.color();
            int srcX = (color * 256) % 512;
            int srcY = 244 + (color * 256 / 512) * 16;

            boolean complete = cd.isBundleComplete(def.bundleId());
            int filledCount = cd.countFilledSlots(def.bundleId());
            bundleOrbs.add(new BundleOrb(def, orbX, orbY, srcX, srcY, complete, filledCount));
        }

        // Check for present button (any bundle in this area has claimable reward)
        // Hidden in read-only viewer mode (SDV: JunimoNoteMenu.fromGameMenu blocks rewards).
        showPresentButton = !this.menu.isReadOnly() && cd.hasAnyRewardForArea(areaId);
    }

    /**
     * Inventory rendering is handled by the parent class.
     * On overview page we skip super.render() to hide inventory.
     * On detail page we call super.render() which draws inventory.
     * Slot positions are fixed in BundleMenu constructor.
     */

    // ═════════════════════════════════════════════════════════════════════════
    //  RENDER — mirrors SDV JunimoNoteMenu.draw() line by line
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    public void renderBackground(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No-op — we handle background dimming in render() to avoid double overlay
        // (super.render() calls renderBackground() internally)
    }

    @Override
    public void render(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Detect areaId sync from DataSlot and rebuild
        int currentArea = this.menu.getAreaId();
        int currentVersion = BundleClientData.INSTANCE.getVersion();
        if (currentArea != lastSyncedAreaId || currentVersion != lastDataVersion) {
            boolean firstSync = (lastSyncedAreaId == -1);
            lastSyncedAreaId = currentArea;
            lastDataVersion = currentVersion;

            // Rebuild orbs first so we can target specific ones for animation
            buildBundleOrbs();
            if (specificBundlePage) {
                refreshDetailPage();
            }

            // On first sync, seed previouslyCompleteBundles with ALL already-complete bundles
            // (across every area) so navigating to other areas in the read-only viewer doesn't
            // re-trigger completion animations for bundles that were already done.
            BundleClientData cd = BundleClientData.INSTANCE;
            if (firstSync) {
                for (BundleDefinition bd : BundleDataManager.getAllBundles()) {
                    if (cd.isBundleComplete(bd.bundleId())) {
                        previouslyCompleteBundles.add(bd.bundleId());
                    }
                }
                showPresentButton = !this.menu.isReadOnly() && cd.hasAnyRewardForArea(currentArea);
            } else {
                // Check for newly completed bundles → screenSwipe + completion animation
                for (BundleDefinition bd : BundleDataManager.getBundlesForArea(currentArea)) {
                    if (cd.isBundleComplete(bd.bundleId()) && !previouslyCompleteBundles.contains(bd.bundleId())) {
                        previouslyCompleteBundles.add(bd.bundleId());
                        // SDV: screenSwipe = new ScreenSwipe(0, -1f, -1, width, height)
                        screenSwipe = new ScreenSwipe(this.width, this.height, s4, this::playSoundById);
                        canClick = false;
                        // SDV: Bundle.completionAnimation(playSound=true) → Game1.playSound("dwop")
                        playSound(ModSounds.DWOP);
                        // SDV: currentPageBundle.completionAnimation(this, true, 400) — 400ms delay
                        for (BundleOrb orb : bundleOrbs) {
                            if (orb.def.bundleId() == bd.bundleId()) {
                                orb.startCompletionAnimation(400);
                                break;
                            }
                        }
                        // SDV: takeDownBundleSpecificPage()
                        if (specificBundlePage) {
                            specificBundlePage = false;
                            selectedBundleId = -1;
                            ingredientSlots.clear();
                            ingredientList.clear();
                            localCompletedIngredients.clear();
                            tempSprites.clear();
                        }
                        // SDV: checkForRewards()
                        showPresentButton = !this.menu.isReadOnly() && cd.hasAnyRewardForArea(currentArea);
                    }
                }
            }
        }

        // Update animations
        long now = System.currentTimeMillis();
        float deltaMs = now - lastTickMs;
        lastTickMs = now;
        for (BundleOrb orb : bundleOrbs) {
            orb.tick(deltaMs);
            // SDV endFunction: completion animation ends → "leafrustle" sound
            if (orb.completionJustFinished) {
                orb.completionJustFinished = false;
                playSound(ModSounds.LEAFRUSTLE);
            }
        }
        // Present button animation: 4 frames, 70ms
        if (showPresentButton) {
            presentAnimTimer += deltaMs;
            if (presentAnimTimer >= 70) {
                presentAnimTimer = 0;
                presentAnimFrame = (presentAnimFrame + 1) % 4;
            }
        }

        // SDV: screenSwipe.update() — when done, null it and re-enable clicks
        if (screenSwipe != null) {
            canClick = false;
            if (screenSwipe.update(deltaMs, screenSwipeClicked)) {
                screenSwipe = null;
                canClick = true;
            }
            screenSwipeClicked = false;
        }

        // Tick tempSprites and remove expired
        tempSprites.removeIf(ts -> ts.tick(deltaMs));

        // SDV-style flat 50% black overlay (dimming the game world)
        g.fill(0, 0, this.width, this.height, 0x80000000);

        if (!specificBundlePage) {
            renderRoomOverview(g, mouseX, mouseY, partialTick);
        } else {
            renderBundleDetail(g, mouseX, mouseY, partialTick);
        }

        // SDV: getRewardNameForArea drawn AFTER both pages (not inside if/else)
        String rewardKey = getRewardNameForArea(this.menu.getAreaId());
        if (!rewardKey.isEmpty()) {
            int rewardX = menuX + mapping.ui(640);
            int rewardY = Math.min(menuY + mapping.ui(740),
                    this.height - mapping.ui(72));
            drawStringWithScrollCenteredAt(g, Component.translatable(rewardKey), rewardX, rewardY);
        }

        // SDV: canClick check → drawMouse, hoverText
        // SDV: screenSwipe?.draw(b) — drawn LAST, on top of everything
        if (screenSwipe != null) {
            screenSwipe.draw(g);
        }
    }

    /**
     * SDV: HighlightObjects — dim inventory items that don't match current bundle requirements.
     * SDV: when partialDonation is active, only highlight items matching the partial ingredient.
     * SDV parity: InventoryMenu.cs line 558 — drawInMenu(transparency: 0.25f).
     * Non-matching items are rendered at 25% opacity using translucent render types.
     */
    @Override
    protected void renderSlot(@Nonnull GuiGraphics g, @Nonnull net.minecraft.world.inventory.Slot slot) {
        // SDV parity: fromGameMenu viewer has no inventory rendered
        if (this.menu.isReadOnly()) {
            return;
        }
        if (specificBundlePage && selectedBundleId >= 0) {
            BundleDefinition def = BundleDataManager.getBundle(selectedBundleId);
            ItemStack stack = slot.getItem();
            if (def != null && !def.isVaultBundle()) {
                boolean matches = false;
                if (!stack.isEmpty()) {
                    BundleClientData cd = BundleClientData.INSTANCE;

                    // SDV parity: if partial donation active, only highlight items matching the partial ingredient
                    if (partialDonationItem != null && partialIngredientIndex >= 0) {
                        BundleIngredient ing = def.ingredients().get(partialIngredientIndex);
                        matches = BundleMenu.isItemTypeMatch(stack, ing);
                    } else {
                        for (int i = 0; i < def.ingredients().size(); i++) {
                            if (cd.isSlotComplete(def.bundleId(), i)) continue;
                            if (BundleMenu.isItemTypeMatch(stack, def.ingredients().get(i))) {
                                matches = true;
                                break;
                            }
                        }
                    }
                }

                if (!matches && !stack.isEmpty()) {
                    renderDimmedSlot(g, slot, stack);
                } else {
                    super.renderSlot(g, slot);
                }
                return;
            }
        }
        super.renderSlot(g, slot);
    }

    /**
     * Render an inventory slot at 25% opacity (SDV parity).
     * entitySolid / entityCutout render types have NO_TRANSPARENCY (GL blend off),
     * so setShaderColor alpha is invisible. We remap them to itemEntityTranslucentCull
     * which has TRANSLUCENT_TRANSPARENCY (GL blend on), making alpha work for ALL items
     * including 3D block models.
     */
    private void renderDimmedSlot(GuiGraphics g, net.minecraft.world.inventory.Slot slot, ItemStack stack) {
        Minecraft mc = this.minecraft;
        int x = slot.x;
        int y = slot.y;
        int seed = x + y * this.imageWidth;

        // Flush any prior batches so setShaderColor doesn't affect them
        g.flush();

        // Outer pose: z=100 (same as AbstractContainerScreen.renderSlot)
        g.pose().pushPose();
        g.pose().translate(0, 0, 100);

        // ── Render the item model at 25% opacity ──
        BakedModel model = mc.getItemRenderer().getModel(stack, mc.level, mc.player, seed);
        g.pose().pushPose();
        g.pose().translate(x + 8f, y + 8f, 150f + (model.isGui3d() ? 0 : 0));
        g.pose().scale(16f, -16f, 16f);

        boolean flatLighting = !model.usesBlockLight();
        if (flatLighting) Lighting.setupForFlatItems();

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 0.25f);

        // Wrap buffer source: remap opaque entity types to translucent for blending
        RenderType solidBlock = Sheets.solidBlockSheet();
        RenderType cutoutBlock = Sheets.cutoutBlockSheet();
        RenderType translucentTarget = Sheets.translucentItemSheet();
        MultiBufferSource dimmedSource = renderType -> {
            if (renderType == solidBlock || renderType == cutoutBlock) {
                return g.bufferSource().getBuffer(translucentTarget);
            }
            return g.bufferSource().getBuffer(renderType);
        };

        mc.getItemRenderer().render(stack, ItemDisplayContext.GUI, false,
                g.pose(), dimmedSource, 15728880, OverlayTexture.NO_OVERLAY, model);
        g.flush();

        if (flatLighting) Lighting.setupFor3DItems();
        g.pose().popPose();

        // ── Render item decorations (count text, durability bar) also dimmed ──
        g.renderItemDecorations(this.font, stack, x, y);
        g.flush();

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        g.pose().popPose();
    }

    @Override
    protected void renderBg(@Nonnull GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        // Handled in render() directly
    }

    @Override
    protected void renderLabels(@Nonnull GuiGraphics g, int mouseX, int mouseY) {
        // Don't render default labels
    }

    // ── Room Overview Page ──
    // SDV draw() lines: background → room name → bundle orbs → present button → temp sprites → arrows

    private void renderRoomOverview(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int areaId = this.menu.getAreaId();

        // 1. Room overview background: noteTexture (0, 0, 320, 180) scale ×4
        g.pose().pushPose();
        g.pose().translate(menuX, menuY, 0.1f);
        g.pose().scale(s4, s4, 1.0f);
        g.blit(JUNIMO_NOTE, 0, 0, 0, 0, BASE_W, BASE_H, TEX_WIDTH, TEX_HEIGHT);
        g.pose().popPose();

        // SDV parity: scrambledText path — draw Junimo font title + message, then RETURN
        // SDV: if (scrambledText) { drawString(junimoText:true, areaName, ...); drawString(junimoText:true, message, ...); return; }
        if (!BundleClientData.INSTANCE.canReadJunimoText()) {
            // Title: area name in Junimo font, centered
            // SDV uses English area name (not localized) for the junimoText path
            String areaName = BundleDataManager.getAreaName(areaId);
            if (areaName == null) areaName = "???";
            int titleX = menuX + mapping.ui(656);
            int titleY = menuY + mapping.ui(12);
            // SDV: fontPixelZoom = 3, then scaled by s4/4 to match GUI
            float junimoScale = 3.0f * s4 / 4.0f;
            g.pose().pushPose();
            g.pose().translate(0, 0, 0.2f);
            JunimoTextRenderer.drawStringCenteredClamped(g, areaName, titleX, titleY,
                mapping.ui(640), junimoScale, 1.0f);
            g.pose().popPose();

            // SDV message: "We will fix the community center for you! Sincerely, Junimos"
            // Drawn at (xPos + 96, yPos + 96) in junimoText, wrapped to stay on the note.
            String junimoMessage = "We will fix the community center for you! Sincerely, Junimos";
            int msgX = menuX + mapping.ui(96);
            int msgY = menuY + mapping.ui(96);
            g.pose().pushPose();
            g.pose().translate(0, 0, 0.2f);
            JunimoTextRenderer.drawStringWrapped(g, junimoMessage, msgX, msgY,
                mapping.ui(1088), junimoScale, 1.0f);
            g.pose().popPose();

            // SDV: returns here — NO bundles, NO present button, NO hover
            return;
        }

        // 3. Room name centered (readable path)
        // SDV: SpriteText.drawStringHorizontallyCenteredAt(b, areaName, xPos + width/2 + 16, yPos + 12, ...)
        // width = 1280, so xPos + 640 + 16 = xPos + 656
        String displayKey = BundleDataManager.getAreaDisplayNameKey(areaId);
        Component displayName;
        if (displayKey != null) {
            displayName = Component.translatable(displayKey);
        } else {
            String areaName = BundleDataManager.getAreaName(areaId);
            displayName = Component.literal(areaName != null ? areaName : "Area " + areaId);
        }

        int titleX = menuX + mapping.ui(656);
        int titleY = menuY + mapping.ui(12);
        // SDV SpriteText default colour is dark brown; white is invisible on the parchment background.
        int titleShadow = 0xFF3B3022;
        int titleColor = 0xFF5B5045;
        int titleShadowOff = Math.max(1, mapping.ui(2));
        int titleMaxWidth = mapping.ui(560);
        g.pose().pushPose();
        g.pose().translate(0, 0, 0.2f);
        GuiText.drawCenteredClamped(g, this.font, displayName,
            titleX + titleShadowOff, titleY + titleShadowOff, titleMaxWidth, titleShadow, false);
        GuiText.drawCenteredClamped(g, this.font, displayName,
            titleX, titleY + titleShadowOff, titleMaxWidth, titleShadow, false);
        GuiText.drawCenteredClamped(g, this.font, displayName,
            titleX + titleShadowOff, titleY, titleMaxWidth, titleShadow, false);
        GuiText.drawCenteredClamped(g, this.font, displayName,
            titleX, titleY, titleMaxWidth, titleColor, false);
        g.pose().popPose();

        // 5. Bundle orbs
        for (BundleOrb orb : bundleOrbs) {
            orb.draw(g, s4, mouseX, mouseY);
        }

        // 5b. Area Next/Back arrows (SDV parity: only in fromGameMenu / readOnly mode)
        if (this.menu.isReadOnly()) {
            drawAreaArrows(g, mouseX, mouseY);
        }

        // 6. Present button (gift icon animation) — SDV: (548, 262, 18, 20), 4 frames, 70ms
        // SDV position: (xPos + 592, yPos + 512, 72, 72)
        if (showPresentButton) {
            int presX = menuX + mapping.ui(592);
            int presY = menuY + mapping.ui(512);
            int presU = 548 + presentAnimFrame * 18;
            int presV = 262;
            drawJunimoNote(g, presX, presY, presU, presV, 18, 20, s4);
        }

        // 6b. tempSprites (SDV draw order: bundles → presentButton → tempSprites)
        for (TempSprite ts : tempSprites) {
            ts.draw(g, s4, 1.0f);
        }

        // (Reward text drawn in outer render() after both pages — SDV parity)

        // Hover text for present button — SDV: hoverText = presentButton.tryHover(x, y) → "Rewards"
        if (showPresentButton) {
            int presX = menuX + mapping.ui(592);
            int presY = menuY + mapping.ui(512);
            int presSize = mapping.ui(72);
            if (mouseX >= presX && mouseX <= presX + presSize
                    && mouseY >= presY && mouseY <= presY + presSize) {
                g.renderTooltip(this.font, Component.translatable("stardewcraft.bundle.rewards"), mouseX, mouseY);
            }
        }

        // Hover text for bundle orbs — SDV: JunimoNote_BundleName format
        for (BundleOrb orb : bundleOrbs) {
            if (orb.isHovered(mouseX, mouseY, s4)) {
                g.renderTooltip(this.font,
                        Component.translatable("stardewcraft.bundle.name_format",
                                Component.translatable(orb.def.displayNameKey())),
                        mouseX, mouseY);
            }
        }

        // 10. Close button (upper right X) — handled by MC's AbstractContainerScreen
    }

    // ── Area Next/Back arrows (SDV parity, readOnly viewer only) ──

    /** SDV: areaBackButton rect (xPos + 64, yPos, 48, 44). */
    private int areaBackButtonX() { return menuX + mapping.ui(64); }
    private int areaBackButtonY() { return menuY; }
    /** SDV: areaNextButton rect (xPos + width - 128, yPos, 48, 44). width = 1280. */
    private int areaNextButtonX() { return menuX + mapping.ui(1280 - 128); }
    private int areaNextButtonY() { return menuY; }
    private int areaButtonW() { return mapping.ui(48); }
    private int areaButtonH() { return mapping.ui(44); }

    private boolean areaBackContains(double mx, double my) {
        return mx >= areaBackButtonX() && mx < areaBackButtonX() + areaButtonW()
            && my >= areaBackButtonY() && my < areaBackButtonY() + areaButtonH();
    }
    private boolean areaNextContains(double mx, double my) {
        return mx >= areaNextButtonX() && mx < areaNextButtonX() + areaButtonW()
            && my >= areaNextButtonY() && my < areaNextButtonY() + areaButtonH();
    }

    private void drawAreaArrows(GuiGraphics g, int mouseX, int mouseY) {
        // Sprite is 12×11, drawn at scale 4 centered in a 48×44 hover box.
        int spriteW = mapping.ui(AREA_ARROW_W * 4);
        int spriteH = mapping.ui(AREA_ARROW_H * 4);

        // Back arrow
        int bx = areaBackButtonX() + (areaButtonW() - spriteW) / 2;
        int by = areaBackButtonY() + (areaButtonH() - spriteH) / 2;
        g.pose().pushPose();
        g.pose().translate(0, 0, 0.2f);
        g.blit(AREA_BACK_ARROW, bx, by, 0, 0, spriteW, spriteH, spriteW, spriteH);
        g.pose().popPose();

        // Next arrow
        int nx = areaNextButtonX() + (areaButtonW() - spriteW) / 2;
        int ny = areaNextButtonY() + (areaButtonH() - spriteH) / 2;
        g.pose().pushPose();
        g.pose().translate(0, 0, 0.2f);
        g.blit(AREA_NEXT_ARROW, nx, ny, 0, 0, spriteW, spriteH, spriteW, spriteH);
        g.pose().popPose();
    }

    // ── Bundle Detail Page ──
    // SDV draw() lines: detail bg → big icon → name label → back button → purchase button →
    //   temp sprites → ingredient slots → ingredient list → inventory → reward text → held item

    private void renderBundleDetail(GuiGraphics g, int mouseX, int mouseY, float partialTick) {

        // 1. Detail page background: noteTexture (320, 0, 320, 180) scale ×4
        g.pose().pushPose();
        g.pose().translate(menuX, menuY, 0.1f);
        g.pose().scale(s4, s4, 1.0f);
        g.blit(JUNIMO_NOTE, 0, 0, 320, 0, BASE_W, BASE_H, TEX_WIDTH, TEX_HEIGHT);
        g.pose().popPose();

        BundleDefinition def = BundleDataManager.getBundle(selectedBundleId);
        if (def == null) return;

        // 2. Bundle detail icon (32×32)
        // SDV: b.Draw(noteTexture, new Vector2(xPos + 872, yPos + 88),
        //      new Rectangle(bundleIndex*16*2 % texWidth, 180 + 32*(bundleIndex*16*2/texWidth), 32, 32),
        //      Color.White, 0, Zero, 4f, None, 0.15f)
        int iconIdx = def.bundleId();
        int iconU = (iconIdx * 32) % TEX_WIDTH;
        int iconV = 180 + 32 * ((iconIdx * 32) / TEX_WIDTH);
        int iconX = menuX + mapping.ui(872);
        int iconY = menuY + mapping.ui(88);

        g.pose().pushPose();
        g.pose().translate(iconX, iconY, 0.15f);
        g.pose().scale(s4, s4, 1.0f);
        g.blit(JUNIMO_NOTE, 0, 0, iconU, iconV, 32, 32, TEX_WIDTH, TEX_HEIGHT);
        g.pose().popPose();

        // 3. Bundle name label (three-part horizontal bar + text with 3-layer shadow)
        // SDV: text measured with dialogueFont, bar centered at xPos + 936
        // SDV: Game1.content.LoadString("Strings\\UI:JunimoNote_BundleName", label) → "{0} Bundle"
        Component bundleName = BundleClientData.INSTANCE.canReadJunimoText()
                ? Component.translatable("stardewcraft.bundle.name_format",
                        Component.translatable(def.displayNameKey()))
                : Component.literal("???");
        int barCenterX = menuX + mapping.ui(936);
        int barY = menuY + mapping.ui(228);
        int labelMaxWidth = mapping.ui(320);
        Component shownBundleName = GuiText.ellipsize(this.font, bundleName, labelMaxWidth);
        int textWidth = this.font.width(shownBundleName);

        // Left end: (517, 266, 4, 17) ×4
        int barLeftX = barCenterX - textWidth / 2 - mapping.ui(16);
        drawJunimoNote(g, barLeftX, barY, 517, 266, 4, 17, s4);

        // Middle stretch: (520, 266, 1, 17) stretched to text width
        int barMiddleX = barLeftX + mapping.ui(16);
        g.pose().pushPose();
        g.pose().translate(barMiddleX, barY, 0.1f);
        g.pose().scale(s4, s4, 1.0f);
        // Stretch 1px wide to cover text width
        int stretchPx = Math.max(1, (int) (textWidth / s4));
        g.blit(JUNIMO_NOTE, 0, 0, stretchPx, 17, 520, 266, 1, 17, TEX_WIDTH, TEX_HEIGHT);
        g.pose().popPose();

        // Right end: (524, 266, 4, 17) ×4
        int barRightX = barCenterX + textWidth / 2;
        drawJunimoNote(g, barRightX, barY, 524, 266, 4, 17, s4);

        // Text with 3-layer shadow (SDV: textShadowColor = 0x3B3022, textColor * 0.9f = ~0x5B5045)
        int textX = barCenterX - textWidth / 2;
        int textY = barY + mapping.ui(8);
        int shadowColor = 0xFF3B3022;
        int textColor = 0xFF5B5045;
        int shadowOff = Math.max(1, mapping.ui(2));

        // SDV shadow offsets: (2,2), (0,2), (2,0) in screen pixels
        g.drawString(this.font, shownBundleName, textX + shadowOff, textY + shadowOff, shadowColor, false);
        g.drawString(this.font, shownBundleName, textX, textY + shadowOff, shadowColor, false);
        g.drawString(this.font, shownBundleName, textX + shadowOff, textY, shadowColor, false);
        g.drawString(this.font, shownBundleName, textX, textY, textColor, false);

        // 4. Back button (Cursors tile #44)
        // SDV: xPos + borderWidth*2 + 8, yPos + borderWidth*2 + 4 (borderWidth=32)
        // getSourceRectForStandardTileSheet(mouseCursors, 44): 44*64%704=0, 44*64/704*64=256 → (0,256,64,64)
        // SDV scale = 1.0f → 64×64 screen px; MC: poseScale = 1/guiScale = s4/4
        int backX = menuX + mapping.ui(72);
        int backY = menuY + mapping.ui(68);
        StardewGuiUtil.drawFromCursors(g, backX, backY, 0, 256, 64, 64, s4 / 4.0f);

        // 5. Purchase button (Vault only)
        if (def.isVaultBundle() && !BundleClientData.INSTANCE.isBundleComplete(def.bundleId())) {
            int purchX = menuX + mapping.ui(800);
            int purchY = menuY + mapping.ui(504);
            drawLocalizedPurchaseButton(g, purchX, purchY, s4);
        }

        // 6b. tempSprites — SDV: drawn with completed_slot_alpha when partial donation active
        float completedSlotAlpha = partialDonationItem != null ? 0.25f : 1.0f;
        for (TempSprite ts : tempSprites) {
            ts.draw(g, s4, completedSlotAlpha);
        }

        // 7. Ingredient slots (submission slots) — SDV: dim non-partial slots when partial active
        for (int i = 0; i < ingredientSlots.size(); i++) {
            IngredientSlotVisual slot = ingredientSlots.get(i);
            float alphaMult = 1.0f;
            if (partialDonationItem != null && i != partialSlotIndex) {
                alphaMult = 0.25f;
            }
            slot.draw(g, s4, mouseX, mouseY, this, alphaMult);
        }

        // 8. Ingredient list — SDV: dim non-partial ingredients when partial active
        for (int i = 0; i < ingredientList.size(); i++) {
            IngredientListEntry entry = ingredientList.get(i);
            float alphaMult = 1.0f;
            if (partialIngredientIndex >= 0 && partialIngredientIndex != i) {
                alphaMult = 0.25f;
            }
            entry.draw(g, s4, this.font, this, alphaMult);
        }

        // 9. Player inventory (MC handles via super)
        super.render(g, mouseX, mouseY, partialTick);

        // (Reward text drawn in outer render() after both pages — SDV parity)

        // 12. Held item follows mouse — MC handles this automatically

        // 13. Tooltips — MC native ItemStack tooltip for ingredient list items
        for (IngredientListEntry entry : ingredientList) {
            if (entry.isHovered(mouseX, mouseY) && !entry.displayStack.isEmpty()) {
                g.renderTooltip(this.font, entry.displayStack, mouseX, mouseY);
            }
        }
        // Ingredient slot deposited item tooltip
        for (IngredientSlotVisual slot : ingredientSlots) {
            if (slot.filled && slot.depositedItem != null && !slot.depositedItem.isEmpty()
                    && slot.isHovered(mouseX, mouseY)) {
                g.renderTooltip(this.font, slot.depositedItem, mouseX, mouseY);
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  CLICK HANDLING
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // SDV: if (!canClick) return;
        if (!canClick) {
            if (screenSwipe != null) {
                screenSwipeClicked = true;
            }
            return false;
        }

        // SDV parity: if (scrambledText) return;
        if (!BundleClientData.INSTANCE.canReadJunimoText()) {
            return false;
        }

        // SDV parity: read-only viewer mode (fromGameMenu=true)
        // allows navigating orbs + back button + close, blocks all donations/purchases.
        final boolean readOnly = this.menu.isReadOnly();

        if (button == 0) {
            if (!specificBundlePage) {
                // Area Next/Back arrows (readOnly viewer only)
                if (readOnly) {
                    if (areaNextContains(mouseX, mouseY)) {
                        PacketDistributor.sendToServer(
                            new com.stardew.craft.communitycenter.network.SwitchBundleViewerAreaPayload(1));
                        playSound(ModSounds.SMALL_SELECT);
                        return true;
                    }
                    if (areaBackContains(mouseX, mouseY)) {
                        PacketDistributor.sendToServer(
                            new com.stardew.craft.communitycenter.network.SwitchBundleViewerAreaPayload(-1));
                        playSound(ModSounds.SMALL_SELECT);
                        return true;
                    }
                }

                // Room overview: click bundle orb → open detail
                for (BundleOrb orb : bundleOrbs) {
                    if (orb.isHovered((int) mouseX, (int) mouseY, s4) && !orb.complete) {
                        openBundleDetail(orb.def);
                        return true;
                    }
                }

                // Present button click (hidden in readOnly)
                if (showPresentButton && !readOnly) {
                    int presX = menuX + mapping.ui(592);
                    int presY = menuY + mapping.ui(512);
                    int presSize = mapping.ui(72);
                    if (mouseX >= presX && mouseX <= presX + presSize
                            && mouseY >= presY && mouseY <= presY + presSize) {
                        PacketDistributor.sendToServer(new com.stardew.craft.communitycenter.network.OpenBundleRewardsPayload(this.menu.getAreaId()));
                        playSound(ModSounds.SMALL_SELECT);
                        return true;
                    }
                }
            } else {
                BundleDefinition def = BundleDataManager.getBundle(selectedBundleId);
                if (def == null) return false;

                // SDV: heldItem = inventory.leftClick(x, y, heldItem)
                // MC handles inventory clicks via super.mouseClicked

                // Back button — SDV: only works when heldItem == null
                int backX = menuX + mapping.ui(72);
                int backY = menuY + mapping.ui(68);
                int backSize = mapping.ui(64);
                if (mouseX >= backX && mouseX <= backX + backSize
                        && mouseY >= backY && mouseY <= backY + backSize
                        && this.menu.getCarried().isEmpty()) {
                    closeBundleDetail();
                    return true;
                }

                // Read-only: no purchase / deposit / partial — block remaining interactions.
                if (readOnly) {
                    return false;
                }

                // Purchase button (Vault only)
                if (def.isVaultBundle() && !BundleClientData.INSTANCE.isBundleComplete(def.bundleId())) {
                    int purchX = menuX + mapping.ui(800);
                    int purchY = menuY + mapping.ui(504);
                    int purchW = mapping.ui(260);
                    int purchH = mapping.ui(80);
                    if (mouseX >= purchX && mouseX <= purchX + purchW
                            && mouseY >= purchY && mouseY <= purchY + purchH) {
                        PacketDistributor.sendToServer(new BundlePurchasePayload(def.bundleId()));
                        playSound(ModSounds.SELECT);
                        return true;
                    }
                }

                // ── SDV receiveLeftClick: ingredient slot interaction ──
                ItemStack carried = this.menu.getCarried();
                boolean shiftHeld = hasShiftDown();

                if (!def.isVaultBundle()) {
                    // SDV: if (partialDonationItem != null)
                    if (partialDonationItem != null) {
                        if (!carried.isEmpty() && shiftHeld) {
                            // SDV: Shift+click with item while partial active → add to partial slot
                            for (int i = 0; i < ingredientSlots.size(); i++) {
                                if (i == partialSlotIndex) {
                                    handlePartialDonation(def, carried, i);
                                    return true;
                                }
                            }
                        } else {
                            // SDV: click on partial slot → if holding item, add; if empty hand, return
                            for (int i = 0; i < ingredientSlots.size(); i++) {
                                IngredientSlotVisual slot = ingredientSlots.get(i);
                                if (slot.isHovered((int) mouseX, (int) mouseY) && i == partialSlotIndex) {
                                    if (!carried.isEmpty()) {
                                        handlePartialDonation(def, carried, i);
                                    } else {
                                        // SDV: ReturnPartialDonations — shift pressed = return to inventory
                                        returnPartialDonations(!shiftHeld);
                                    }
                                    return true;
                                }
                            }
                        }
                    } else if (!carried.isEmpty()) {
                        // SDV: Shift+click quick deposit — iterate all slots
                        if (shiftHeld) {
                            for (int i = 0; i < ingredientSlots.size(); i++) {
                                IngredientSlotVisual slot = ingredientSlots.get(i);
                                if (slot.filled) continue;
                                // SDV: canAcceptThisItem(heldItem, slot) — single-pass check
                                int matchedIngIdx = findMatchingIngredientIndex(def, carried);
                                if (matchedIngIdx >= 0) {
                                    BundleIngredient ing = def.ingredients().get(matchedIngIdx);
                                    if (carried.getCount() >= ing.stack()) {
                                        // SDV: tryToDepositThisItem → return
                                        tryDepositIntoSlot(def, carried, slot, matchedIngIdx);
                                        return true;
                                    }
                                }
                                // SDV: else if (slot.item == null) HandlePartialDonation — no return
                                handlePartialDonation(def, carried, i);
                                if (partialDonationItem != null) return true;
                            }
                        }

                        // SDV: normal click on ingredient slot
                        for (int i = 0; i < ingredientSlots.size(); i++) {
                            IngredientSlotVisual slot = ingredientSlots.get(i);
                            if (slot.isHovered((int) mouseX, (int) mouseY)) {
                                if (slot.filled) break; // SDV: slot.item != null → skip
                                // SDV parity: single-pass check + deposit
                                int matchedIngIdx = findMatchingIngredientIndex(def, carried);
                                if (matchedIngIdx >= 0) {
                                    BundleIngredient ing = def.ingredients().get(matchedIngIdx);
                                    if (carried.getCount() >= ing.stack()) {
                                        // Full deposit
                                        tryDepositIntoSlot(def, carried, slot, matchedIngIdx);
                                        return true;
                                    } else {
                                        // SDV: HandlePartialDonation — item count < required
                                        handlePartialDonation(def, carried, i);
                                        if (partialDonationItem != null) return true;
                                    }
                                }
                                break; // Found the hovered slot, stop looking
                            }
                        }
                    }
                }
            }
        }

        // SDV: right-click handling
        if (button == 1) {
            if (specificBundlePage) {
                if (readOnly) {
                    return false;
                }
                // SDV: receiveRightClick — inventory right-click (pick up half stack)
                // MC handles this via super.mouseClicked

                // SDV: right-click on partial donation slot → retrieve 1 item
                if (partialDonationItem != null) {
                    for (int i = 0; i < ingredientSlots.size(); i++) {
                        IngredientSlotVisual slot = ingredientSlots.get(i);
                        if (slot.isHovered((int) mouseX, (int) mouseY) && i == partialSlotIndex) {
                            if (partialDonationComponents.isEmpty()) break;
                            retrieveOneFromPartial();
                            return true;
                        }
                    }
                }

                // QoL: right-click anywhere else on detail page (outside ingredient slots /
                // outside the inventory grid) returns to the room overview when the cursor is
                // empty. ESC works but is easy to miss; the back button sprite is small.
                if (this.menu.getCarried().isEmpty() && this.getSlotUnderMouse() == null) {
                    boolean onIngredientSlot = false;
                    for (IngredientSlotVisual slot : ingredientSlots) {
                        if (slot.isHovered((int) mouseX, (int) mouseY)) { onIngredientSlot = true; break; }
                    }
                    if (!onIngredientSlot) {
                        closeBundleDetail();
                        return true;
                    }
                }
            } else {
                // Right-click on overview → close menu (SDV behavior)
                if (isReadyToCloseMenuOrBundle()) {
                    this.onClose();
                    return true;
                }
            }
        }

        // SDV: heldItem outside bounds → discard as item debris
        if (button == 0 && !this.menu.getCarried().isEmpty() && !isWithinBounds((int) mouseX, (int) mouseY)) {
            // SDV: Game1.playSound("throwDownITem"); Game1.createItemDebris(heldItem, ...)
            // In MC, clicking outside with carried item returns it — handled by MC's container system
        }

        // Only pass clicks to AbstractContainerScreen on detail page (inventory interaction)
        return specificBundlePage ? super.mouseClicked(mouseX, mouseY, button) : false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // ESC on detail page → back to overview (not close)
        if (keyCode == 256 && specificBundlePage) { // 256 = GLFW_KEY_ESCAPE
            closeBundleDetail();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void openBundleDetail(BundleDefinition def) {
        this.specificBundlePage = true;
        this.selectedBundleId = def.bundleId();
        buildIngredientSlots(def);
        buildIngredientList(def);
        playSound(ModSounds.SHWIP);
    }

    private void closeBundleDetail() {
        // SDV parity: if partial donation active, return items first
        if (partialDonationItem != null) {
            returnPartialDonations(false);
        } else if (specificBundlePage) {
            if (!this.menu.getCarried().isEmpty()) {
                // SDV: heldItem = inventory.tryToAddItem(heldItem) — can't close with held item
                // MC handles this differently — carried item goes back automatically
            }
        }
        this.specificBundlePage = false;
        this.selectedBundleId = -1;
        this.ingredientSlots.clear();
        this.ingredientList.clear();
        this.localCompletedIngredients.clear();
        // Clear any lingering deposit/sparkle animations so they don't re-render as ghost
        // overlays the next time the player opens a bundle detail page.
        this.tempSprites.clear();
        resetPartialDonation();
        buildBundleOrbs();
        playSound(ModSounds.SHWIP);
    }

    /**
     * SDV parity: incremental refresh — only assign newly-completed ingredients to empty slots.
     * SDV never rebuilds ingredientSlots on sync; it creates a new menu instance via reOpenThisMenu().
     * Our approach: skip full rebuild, just fill in any gaps from server sync.
     */
    private void refreshDetailPage() {
        if (!specificBundlePage || selectedBundleId < 0) return;
        BundleDefinition def = BundleDataManager.getBundle(selectedBundleId);
        if (def == null) return;

        // If slots haven't been built yet (e.g. first sync), do a full build
        if (ingredientSlots.isEmpty() && !def.isVaultBundle()) {
            buildIngredientSlots(def);
            buildIngredientList(def);
            return;
        }

        // Incremental: find newly completed ingredients (from server sync) not yet in a visual slot
        BundleClientData cd = BundleClientData.INSTANCE;
        for (int i = 0; i < def.ingredients().size(); i++) {
            if (!cd.isSlotComplete(def.bundleId(), i)) continue;
            if (localCompletedIngredients.contains(i)) continue;

            // This ingredient was completed (possibly by another player) but not yet in a slot
            localCompletedIngredients.add(i);
            // Find first empty visual slot
            for (IngredientSlotVisual slot : ingredientSlots) {
                if (!slot.filled) {
                    fillSlotWithIngredient(slot, def.ingredients().get(i), i);
                    break;
                }
            }
        }

        // Always refresh the ingredient list (shows completion checkmarks)
        buildIngredientList(def);
    }

    /**
     * Find the ingredient index that matches the given item.
     * SDV parity: skips ingredients that are completed (server) or locally predicted as completed.
     */
    private int findMatchingIngredientIndex(BundleDefinition def, ItemStack stack) {
        BundleClientData cd = BundleClientData.INSTANCE;
        List<BundleIngredient> ingredients = def.ingredients();
        for (int i = 0; i < ingredients.size(); i++) {
            if (cd.isSlotComplete(def.bundleId(), i)) continue;
            if (localCompletedIngredients.contains(i)) continue;
            if (BundleMenu.isValidItem(stack, ingredients.get(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * SDV parity: tryToDepositThisItem — deposit item into the given visual slot.
     * Sends packet to server, does client prediction (fill slot, mark ingredient complete locally),
     * plays animation and sound. SDV: Bundle.tryToDepositThisItem(item, slot, ...)
     */
    private void tryDepositIntoSlot(BundleDefinition def, ItemStack carried, IngredientSlotVisual slot, int matchedIngIdx) {
        BundleIngredient ing = def.ingredients().get(matchedIngIdx);

        // Send to server
        PacketDistributor.sendToServer(new BundleDepositPayload(def.bundleId(), matchedIngIdx));

        // Client prediction: SDV directly sets slot.item and marks ingredient.completed = true
        carried.shrink(ing.stack());
        localCompletedIngredients.add(matchedIngIdx);

        // SDV: slot.item = ItemRegistry.Create(GetRepresentativeItemId(ingredient), stack, quality)
        // Shows the ingredient definition item, not the actual player item
        slot.filled = true;
        slot.ingredientDataIndex = matchedIngIdx;
        slot.depositedItem = BundleItemResolver.resolveItemStack(ing.itemId());
        if (!slot.depositedItem.isEmpty()) {
            slot.depositedItem.setCount(ing.stack());
        }

        // SDV: ingredientDepositAnimation(slot, ...)
        addDepositAnimation(slot);
        // SDV: Game1.playSound("newArtifact")
        playSound(ModSounds.NEW_ARTIFACT);
    }

    /**
     * SDV parity: CanBePartiallyOrFullyDonated — check if total available items
     * (cursor + inventory + existing partial) can reach the required count.
     */
    private boolean canBePartiallyOrFullyDonated(BundleDefinition def, ItemStack item) {
        int ingIdx = findMatchingIngredientIndex(def, item);
        if (ingIdx < 0) return false;
        BundleIngredient ingredient = def.ingredients().get(ingIdx);

        int count = 0;
        if (BundleMenu.isItemTypeMatch(item, ingredient)) {
            count += item.getCount();
        }
        // Count matching items in player inventory
        assert this.minecraft != null && this.minecraft.player != null;
        for (ItemStack invStack : this.minecraft.player.getInventory().items) {
            if (!invStack.isEmpty() && BundleMenu.isItemTypeMatch(invStack, ingredient)) {
                count += invStack.getCount();
            }
        }
        // Add existing partial
        if (ingIdx == partialIngredientIndex && partialDonationItem != null) {
            count += partialDonationItem.getCount();
        }
        return count >= ingredient.stack();
    }

    /**
     * SDV parity: HandlePartialDonation — client-side handling of partial item deposit.
     * Sends packet to server for actual item movement, updates client visual state.
     */
    private void handlePartialDonation(BundleDefinition def, ItemStack carried, int visualSlotIndex) {
        if (!canBePartiallyOrFullyDonated(def, carried)) return;

        int ingIdx;
        if (partialIngredientIndex >= 0) {
            ingIdx = partialIngredientIndex;
        } else {
            ingIdx = findMatchingIngredientIndex(def, carried);
            if (ingIdx < 0) return;
        }

        BundleIngredient ingredient = def.ingredients().get(ingIdx);
        if (!BundleMenu.isItemTypeMatch(carried, ingredient)) return;

        int amountToTake;
        if (partialDonationItem == null) {
            amountToTake = Math.min(ingredient.stack(), carried.getCount());
        } else {
            int remaining = ingredient.stack() - partialDonationItem.getCount();
            amountToTake = Math.min(remaining, carried.getCount());
        }
        if (amountToTake <= 0) return;

        // Send to server
        PacketDistributor.sendToServer(
                new BundlePartialDepositPayload(def.bundleId(), ingIdx, amountToTake));

        // Client-side prediction: update visual state
        if (partialDonationItem == null) {
            partialDonationItem = carried.copyWithCount(amountToTake);
            partialIngredientIndex = ingIdx;
            partialSlotIndex = visualSlotIndex;

            // Track component
            partialDonationComponents.add(carried.copyWithCount(amountToTake));

            // Update slot visual
            IngredientSlotVisual slot = ingredientSlots.get(visualSlotIndex);
            slot.filled = true;
            slot.depositedItem = partialDonationItem.copy();
            slot.isPartial = true;
            slot.ingredientDataIndex = ingIdx;
        } else {
            partialDonationItem.grow(amountToTake);

            // Track component
            boolean merged = false;
            for (ItemStack existing : partialDonationComponents) {
                if (ItemStack.isSameItemSameComponents(existing, carried)) {
                    existing.grow(amountToTake);
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                partialDonationComponents.add(carried.copyWithCount(amountToTake));
            }

            // Update slot visual
            if (partialSlotIndex >= 0 && partialSlotIndex < ingredientSlots.size()) {
                IngredientSlotVisual slot = ingredientSlots.get(partialSlotIndex);
                slot.depositedItem = partialDonationItem.copy();
            }
        }

        // Client prediction: shrink carried
        carried.shrink(amountToTake);

        // SDV: if partial reaches required count → complete deposit
        // SDV flow: slot.item = null → tryToDepositThisItem(partialDonationItem, slot)
        //   which sets slot.item = ingredient definition item and marks ingredient.completed = true
        if (partialDonationItem.getCount() >= ingredient.stack()) {
            int completedIngIdx = partialIngredientIndex;
            int completedSlotIdx = partialSlotIndex;

            // Reset partial state first (SDV: slot.item = null before tryToDeposit)
            partialDonationComponents.clear();
            partialDonationItem = null;
            partialIngredientIndex = -1;
            partialSlotIndex = -1;

            // Now fill the slot as a completed deposit (SDV: tryToDepositThisItem path)
            localCompletedIngredients.add(completedIngIdx);
            if (completedSlotIdx >= 0 && completedSlotIdx < ingredientSlots.size()) {
                IngredientSlotVisual slot = ingredientSlots.get(completedSlotIdx);
                slot.isPartial = false;
                fillSlotWithIngredient(slot, ingredient, completedIngIdx);
                addDepositAnimation(slot);
            }
            playSound(ModSounds.NEW_ARTIFACT);
        } else {
            playSound(ModSounds.SELL); // SDV: Game1.playSound("sell") for partial
        }
    }

    /**
     * SDV parity: ReturnPartialDonations — return accumulated partial items.
     * Client sends packet to server; server returns items.
     */
    private void returnPartialDonations(boolean toCursor) {
        if (partialDonationItem == null) return;
        PacketDistributor.sendToServer(
                new BundlePartialRetrievePayload(selectedBundleId, BundlePartialRetrievePayload.MODE_RETURN_ALL));
        playSound(ModSounds.DWOP);
        resetPartialDonation();
        // Refresh visual after reset
        if (specificBundlePage && selectedBundleId >= 0) {
            refreshDetailPage();
        }
    }

    /**
     * SDV parity: right-click retrieval — take 1 item from partial.
     */
    private void retrieveOneFromPartial() {
        if (partialDonationItem == null || partialDonationComponents.isEmpty()) return;
        PacketDistributor.sendToServer(
                new BundlePartialRetrievePayload(selectedBundleId, BundlePartialRetrievePayload.MODE_RETURN_ONE));
        playSound(ModSounds.DWOP);

        // Client prediction: decrease partial
        ItemStack first = partialDonationComponents.get(0);
        first.shrink(1);
        if (first.isEmpty()) {
            partialDonationComponents.remove(0);
        }

        int newTotal = 0;
        for (ItemStack comp : partialDonationComponents) {
            newTotal += comp.getCount();
        }
        if (newTotal <= 0) {
            resetPartialDonation();
            if (specificBundlePage && selectedBundleId >= 0) {
                refreshDetailPage();
            }
        } else {
            partialDonationItem.setCount(newTotal);
            if (partialSlotIndex >= 0 && partialSlotIndex < ingredientSlots.size()) {
                ingredientSlots.get(partialSlotIndex).depositedItem = partialDonationItem.copy();
            }
        }
    }

    /**
     * Reset client-side partial donation state.
     */
    private void resetPartialDonation() {
        partialDonationComponents.clear();
        if (partialSlotIndex >= 0 && partialSlotIndex < ingredientSlots.size()) {
            IngredientSlotVisual slot = ingredientSlots.get(partialSlotIndex);
            if (slot.isPartial) {
                slot.filled = false;
                slot.depositedItem = ItemStack.EMPTY;
                slot.isPartial = false;
                slot.ingredientDataIndex = -1;
            }
        }
        partialDonationItem = null;
        partialIngredientIndex = -1;
        partialSlotIndex = -1;
    }

    /**
     * SDV parity: isReadyToCloseMenuOrBundle
     */
    private boolean isReadyToCloseMenuOrBundle() {
        return this.menu.getCarried().isEmpty() && partialDonationItem == null;
    }

    /**
     * Check if point is within menu bounds.
     */
    private boolean isWithinBounds(int x, int y) {
        return x >= menuX && x <= menuX + mapping.ui(1280)
                && y >= menuY && y <= menuY + mapping.ui(720);
    }

    /**
     * Add deposit animation to target slot.
     */
    private void addDepositAnimation(IngredientSlotVisual slot) {
        // SDV: deposit animation plays once then disappears.
        // holdLastFrame=false ensures the TempSprite is removed when it reaches the last frame,
        // otherwise these sprites accumulate every deposit and stack as ghost icons over slots.
        TempSprite depositAnim = new TempSprite(
                slot.x, slot.y,
                530, 244, 18, 18,
                6, 50f, false);
        depositAnim.endSound = ModSounds.COWBOY_MONSTERHIT;
        tempSprites.add(depositAnim);
    }

    private String getRewardNameForArea(int areaId) {
        return switch (areaId) {
            case 0 -> "stardewcraft.cc.reward.pantry";
            case 1 -> "stardewcraft.cc.reward.crafts_room";
            case 2 -> "stardewcraft.cc.reward.fish_tank";
            case 3 -> "stardewcraft.cc.reward.boiler_room";
            case 4 -> "stardewcraft.cc.reward.vault";
            case 5 -> "stardewcraft.cc.reward.bulletin_board";
            default -> "";
        };
    }

    /**
     * SDV: SpriteText.drawStringWithScrollCenteredAt — draws text with a scroll-style background.
     * Uses Cursors (325,318,12,18) left cap, (337,318,1,18) middle, (338,318,12,18) right cap.
     */
    private void drawStringWithScrollCenteredAt(GuiGraphics g, Component text, int centerX, int centerY) {
        int maxTextWidth = Math.min(mapping.ui(620), this.width - mapping.ui(160));
        Component shownText = GuiText.ellipsize(this.font, text, maxTextWidth);
        int textWidth = this.font.width(shownText);
        int scrollWidth = textWidth;
        float scrollScale = s4; // SDV draws scroll at 4× scale

        int x = centerX - textWidth / 2;
        int y = centerY;

        // Left cap: (325, 318, 12, 18) at (-12, -3) * 4 = (-48, -12) offset
        StardewGuiUtil.drawFromCursors(g, x - (int)(12 * scrollScale), y - (int)(3 * scrollScale),
                325, 318, 12, 18, scrollScale);

        // Middle stretch: (337, 318, 1, 18) stretched to cover text width
        g.pose().pushPose();
        g.pose().translate(x, y - (int)(3 * scrollScale), 0);
        // Scale X by textWidth/1, Y by scrollScale
        g.pose().scale(textWidth, scrollScale, 1.0f);
        g.blit(StardewGuiUtil.CURSORS, 0, 0, 337, 318, 1, 18,
                StardewGuiUtil.CURSORS_WIDTH, StardewGuiUtil.CURSORS_HEIGHT);
        g.pose().popPose();

        // Right cap: (338, 318, 12, 18) at (scroll_width, -12) offset
        StardewGuiUtil.drawFromCursors(g, x + scrollWidth, y - (int)(3 * scrollScale),
                338, 318, 12, 18, scrollScale);

        // Draw text centered on top
        g.drawCenteredString(this.font, shownText, centerX, y, 0xFF5B5045);
    }

    private void playSound(net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.sounds.SoundEvent, net.minecraft.sounds.SoundEvent> sound) {
        if (this.minecraft != null && this.minecraft.getSoundManager() != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound.get(), 1.0f));
        }
    }

    /**
     * Play a sound by SDV sound ID string. Used by ScreenSwipe callback.
     * Maps SDV sound names to our mod sounds.
     */
    private void playSoundById(String soundId) {
        switch (soundId) {
            case "throw" -> playSound(ModSounds.SHWIP);
            case "newRecord" -> playSound(ModSounds.PURCHASE_CLICK);
            case "tinyWhip" -> playSound(ModSounds.SHWIP);
            default -> { /* unknown sound, ignore */ }
        }
    }

    /**
     * Check if the carried item can be deposited (fully or partially) into any slot.
     * SDV parity: CanBePartiallyOrFullyDonated — used for hover highlight.
     */
    boolean canAcceptDeposit() {
        ItemStack carried = this.menu.getCarried();
        if (carried.isEmpty() || selectedBundleId < 0) return false;
        BundleDefinition def = BundleDataManager.getBundle(selectedBundleId);
        if (def == null || def.isVaultBundle()) return false;
        // SDV: CanBePartiallyOrFullyDonated checks if total available >= required
        return canBePartiallyOrFullyDonated(def, carried);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  LAYOUT — mirrors SDV addRectangleRowsToList / createRowOfBoxesCenteredAt
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Build ingredient submission slots.
     * SDV: addRectangleRowsToList(slots, numberOfIngredientSlots, 932, 540)
     * Each slot is 72×72 with 12px gap.
     */
    private void buildIngredientSlots(BundleDefinition def) {
        ingredientSlots.clear();
        localCompletedIngredients.clear();
        if (def.isVaultBundle()) return;

        // SDV: numberOfIngredientSlots = requiredCount (can be < ingredients.Count)
        int numSlots = def.requiredCount();
        List<int[]> positions = layoutRows(numSlots, 932, 540);

        // Create empty slots
        for (int[] pos : positions) {
            ingredientSlots.add(new IngredientSlotVisual(
                    menuX + mapping.ui(pos[0]),
                    menuY + mapping.ui(pos[1]),
                    mapping.ui(72),
                    false,
                    ItemStack.EMPTY
            ));
        }

        // SDV updateIngredientSlots(): fill slots left-to-right with completed ingredients
        BundleClientData cd = BundleClientData.INSTANCE;
        int slotNumber = 0;
        for (int i = 0; i < def.ingredients().size(); i++) {
            if (cd.isSlotComplete(def.bundleId(), i) && slotNumber < ingredientSlots.size()) {
                fillSlotWithIngredient(ingredientSlots.get(slotNumber), def.ingredients().get(i), i);
                localCompletedIngredients.add(i);
                slotNumber++;
            }
        }
    }

    /** Helper: fill a visual slot with a completed ingredient's data. */
    private void fillSlotWithIngredient(IngredientSlotVisual slot, BundleIngredient ing, int ingredientIdx) {
        ItemStack depositedItem = ItemStack.EMPTY;
        if (ing.itemId() != null) {
            depositedItem = BundleItemResolver.resolveItemStack(ing.itemId());
            if (!depositedItem.isEmpty()) {
                depositedItem.setCount(ing.stack());
                if (ing.quality() > 0) {
                    QualityHelper.setQuality(depositedItem, ing.quality());
                }
            }
        }
        slot.filled = true;
        slot.depositedItem = depositedItem;
        slot.ingredientDataIndex = ingredientIdx;
    }

    /**
     * Build ingredient requirement display list.
     * SDV: addRectangleRowsToList(list, ingredients.Count, 932, 364)
     */
    private void buildIngredientList(BundleDefinition def) {
        ingredientList.clear();
        if (def.isVaultBundle()) return;

        List<BundleIngredient> ingredients = def.ingredients();
        List<int[]> positions = layoutRows(ingredients.size(), 932, 364);
        BundleClientData cd = BundleClientData.INSTANCE;

        for (int i = 0; i < ingredients.size() && i < positions.size(); i++) {
            BundleIngredient ing = ingredients.get(i);
            int[] pos = positions.get(i);
            boolean completed = cd.isSlotComplete(def.bundleId(), i);

            ItemStack displayStack = ItemStack.EMPTY;
            if (ing.itemId() != null) {
                displayStack = BundleItemResolver.resolveItemStack(ing.itemId());
                if (!displayStack.isEmpty()) {
                    displayStack.setCount(ing.stack());
                    if (ing.quality() > 0) {
                        QualityHelper.setQuality(displayStack, ing.quality());
                    }
                }
            }

            Component name = displayStack.isEmpty()
                    ? Component.literal(ing.sdvId())
                    : displayStack.getHoverName();

            // SDV parity: detail page is unreachable when !canReadJunimoText (clicks blocked),
            // but as a safety fallback, show "???" like SDV does.
            if (!BundleClientData.INSTANCE.canReadJunimoText()) {
                name = Component.literal("???");
            }

            // Build rich tooltip: "Name ×count" + quality suffix if > 0
            Component tooltip = name.copy();
            if (ing.stack() > 1) {
                tooltip = tooltip.copy().append(Component.literal(" ×" + ing.stack()));
            }
            if (ing.quality() > 0) {
                tooltip = tooltip.copy().append(Component.literal(" ("))
                        .append(QualityHelper.getQualityName(ing.quality()))
                        .append(Component.literal("+)"));
            }

            ingredientList.add(new IngredientListEntry(
                    menuX + mapping.ui(pos[0]),
                    menuY + mapping.ui(pos[1]),
                    mapping.ui(72),
                    displayStack,
                    tooltip,
                    completed,
                    ing.quality()
            ));
        }
    }

    /**
     * Mirrors SDV addRectangleRowsToList + createRowOfBoxesCenteredAt.
     * Returns SDV screen-pixel positions relative to (0,0).
     */
    private List<int[]> layoutRows(int numItems, int centerX, int centerY) {
        List<int[]> result = new ArrayList<>();
        int boxW = 72, gap = 12;

        switch (numItems) {
            case 1 -> addRow(result, centerX, centerY, 1, boxW, gap);
            case 2 -> addRow(result, centerX, centerY, 2, boxW, gap);
            case 3 -> addRow(result, centerX, centerY, 3, boxW, gap);
            case 4 -> addRow(result, centerX, centerY, 4, boxW, gap);
            case 5 -> {
                addRow(result, centerX, centerY - 36, 3, boxW, gap);
                addRow(result, centerX, centerY + 40, 2, boxW, gap);
            }
            case 6 -> {
                addRow(result, centerX, centerY - 36, 3, boxW, gap);
                addRow(result, centerX, centerY + 40, 3, boxW, gap);
            }
            case 7 -> {
                addRow(result, centerX, centerY - 36, 4, boxW, gap);
                addRow(result, centerX, centerY + 40, 3, boxW, gap);
            }
            case 8 -> {
                addRow(result, centerX, centerY - 36, 4, boxW, gap);
                addRow(result, centerX, centerY + 40, 4, boxW, gap);
            }
            case 9 -> {
                addRow(result, centerX, centerY - 36, 5, boxW, gap);
                addRow(result, centerX, centerY + 40, 4, boxW, gap);
            }
            case 10 -> {
                addRow(result, centerX, centerY - 36, 5, boxW, gap);
                addRow(result, centerX, centerY + 40, 5, boxW, gap);
            }
            case 11 -> {
                addRow(result, centerX, centerY - 36, 6, boxW, gap);
                addRow(result, centerX, centerY + 40, 5, boxW, gap);
            }
            case 12 -> {
                addRow(result, centerX, centerY - 36, 6, boxW, gap);
                addRow(result, centerX, centerY + 40, 6, boxW, gap);
            }
        }
        return result;
    }

    private void addRow(List<int[]> result, int centerX, int centerY, int count, int boxW, int gap) {
        // SDV: createRowOfBoxesCenteredAt
        int totalW = count * (boxW + gap);
        int startX = centerX - totalW / 2;
        int startY = centerY - boxW / 2;
        for (int i = 0; i < count; i++) {
            result.add(new int[]{startX + i * (boxW + gap), startY});
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  HELPER — draw from JunimoNote texture
    // ═════════════════════════════════════════════════════════════════════════

    private void drawJunimoNote(GuiGraphics g, int x, int y, int u, int v, int w, int h, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0.5f);
        g.pose().scale(scale, scale, 1.0f);
        g.blit(JUNIMO_NOTE, 0, 0, u, v, w, h, TEX_WIDTH, TEX_HEIGHT);
        g.pose().popPose();
    }

    private void drawLocalizedPurchaseButton(GuiGraphics g, int x, int y, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0.5f);
        g.pose().scale(scale, scale, 1.0f);
        g.blit(LocalizedGuiAssets.texture("bundle/purchase.png"),
                0, 0, 0, 0, 65, 20, 65, 20);
        g.pose().popPose();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  INNER CLASSES — Bundle orb, ingredient slot, ingredient list entry
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * A bundle orb on the room overview page.
     * Mirrors SDV Bundle's TemporaryAnimatedSprite for the colored ball.
     */
    @SuppressWarnings("unused")
    private static class BundleOrb {
        final BundleDefinition def;
        final int x, y;       // GUI coords
        final int srcX, srcY; // texture source origin
        boolean complete;
        final int filledCount;
        final int requiredCount;

        // Animation state
        int animFrame = 1;     // SDV starts at frame 1 (paused after reset)
        float animTimer = 0;
        boolean pingPong = true;
        boolean animForward = true;
        boolean animPaused = true;
        boolean hovered = false;

        // Shake state
        float rotation = 0;
        float maxShake = 0;
        boolean shakeLeft = false;

        // Completion animation state (SDV: Bundle.completionAnimation)
        boolean playingCompletionAnim = false;
        boolean completionJustFinished = false; // set true when completion anim ends (for sound callback)
        int completionTimer = 0; // delay before animation starts (SDV: 400ms)

        BundleOrb(BundleDefinition def, int x, int y, int srcX, int srcY, boolean complete, int filledCount) {
            this.def = def;
            this.x = x;
            this.y = y;
            this.srcX = srcX;
            this.srcY = srcY;
            this.complete = complete;
            this.filledCount = filledCount;
            this.requiredCount = def.requiredCount();

            if (complete) {
                // Show completion frame (last frame of 15-frame animation)
                animFrame = 14;
                animPaused = true;
            }

            // SDV: seasonal shake on construction
            // if (name.ContainsIgnoreCase(Game1.currentSeason) && !complete) { shake(); }
            if (!complete && isSeasonalBundle()) {
                maxShake = (float) Math.PI * 3f / 128f;
            }
        }

        /**
         * SDV: bundle name contains current season → seasonal shake.
         * Checks if this bundle's internal name contains the current in-game season.
         */
        boolean isSeasonalBundle() {
            // TODO: hook into StardewCraft season system when available
            // For now, check against vanilla MC weather/calendar or just return false
            return false;
        }

        /**
         * SDV: Bundle.completionAnimation(menu, playSound=true, delay=400)
         * Starts the flower-opening animation with a delay.
         */
        void startCompletionAnimation(int delay) {
            this.completionTimer = delay;
        }

        void tick(float deltaMs) {
            // Completion delay timer (SDV: completionTimer counts down then triggers animation)
            if (completionTimer > 0) {
                completionTimer -= (int) deltaMs;
                if (completionTimer <= 0) {
                    // SDV: completionAnimation(playSound=true) — start flower animation
                    playingCompletionAnim = true;
                    pingPong = false;
                    animPaused = false;
                    animFrame = 1; // Start from frame 1
                    animTimer = 0;
                    complete = true;
                }
            }

            // Completion animation: 15 frames at 50ms each (SDV: interval=50, animationLength=15)
            if (playingCompletionAnim && !animPaused) {
                animTimer += deltaMs;
                if (animTimer >= 50) {
                    animTimer = 0;
                    animFrame++;
                    if (animFrame >= 15) {
                        // Animation done — hold last frame, trigger shake
                        // SDV endFunction: shake(extraInfo=1) → "leafrustle" + particles
                        animFrame = 14;
                        animPaused = true;
                        playingCompletionAnim = false;
                        completionJustFinished = true; // signal to BundleScreen for sound
                        maxShake = (float) Math.PI * 3f / 128f;
                    }
                }
                return; // Skip normal animation logic during completion
            }

            // Normal hover animation (not during completion)
            if (!animPaused && !playingCompletionAnim) {
                animTimer += deltaMs;
                if (animTimer >= 70) { // 70ms per frame (SDV)
                    animTimer = 0;
                    if (pingPong) {
                        if (animForward) {
                            animFrame++;
                            if (animFrame >= 2) { animForward = false; }
                        } else {
                            animFrame--;
                            if (animFrame <= 0) { animForward = true; }
                        }
                    }
                }
            }

            // Shake decay
            if (maxShake > 0) {
                float shakeRate = (float) Math.PI / 200f;
                rotation += (shakeLeft ? -1 : 1) * shakeRate;
                if (Math.abs(rotation) >= maxShake) {
                    shakeLeft = !shakeLeft;
                }
                maxShake = Math.max(0, maxShake - 0.0007669904f * deltaMs / 16.67f);
                if (maxShake <= 0) {
                    rotation = 0;
                }
            }

            // SDV: Random shake (0.5% chance per tick)
            // SDV condition: complete OR bundle name contains current season
            if ((complete || isSeasonalBundle()) && Math.random() < 0.005) {
                maxShake = (float) Math.PI * 3f / 128f;
            }
        }

        void draw(GuiGraphics g, float s4, int mouseX, int mouseY) {
            // Check hover
            boolean wasHovered = hovered;
            hovered = isHovered(mouseX, mouseY, s4) && !complete;

            if (hovered && !wasHovered) {
                animPaused = false;
                animForward = true;
            } else if (!hovered && wasHovered && !complete) {
                // Reset to idle
                animFrame = 1;
                animPaused = true;
            }

            // Source rect: base + frame * 16px
            int frameU = srcX + animFrame * 16;
            int frameV = srcY;

            g.pose().pushPose();
            g.pose().translate(x + 8 * s4, y + 8 * s4, 0.8f);
            if (rotation != 0) {
                g.pose().mulPose(com.mojang.math.Axis.ZP.rotation(rotation));
            }
            g.pose().scale(s4, s4, 1.0f);
            g.blit(JUNIMO_NOTE, -8, -8, frameU, frameV, 16, 16, TEX_WIDTH, TEX_HEIGHT);
            g.pose().popPose();
        }

        boolean isHovered(int mouseX, int mouseY, float s4) {
            // SDV bounds: (position.X, position.Y, 64, 64) in screen pixels
            float hitSize = 16 * s4; // 64 screen px → GUI units
            return mouseX >= x && mouseX <= x + hitSize
                    && mouseY >= y && mouseY <= y + hitSize;
        }
    }

    /**
     * Visual representation of an ingredient submission slot.
     * Mirrors SDV's ClickableTextureComponent for ingredient slots.
     *
     * SDV behavior:
     * - Empty unfocused: (512, 244, 18, 18) at alpha 0.5
     * - Empty hovered + holding matching item: (512, 244, 18, 18) at alpha 1.0
     * - Filled: no slot background drawn, only item icon at offset (1,1) sprite px
     */
    private static class IngredientSlotVisual {
        final int x, y, size;
        boolean filled;
        @javax.annotation.Nullable ItemStack depositedItem;
        boolean isPartial; // SDV: tracks if this slot holds a partial donation
        @SuppressWarnings("unused")
        int ingredientDataIndex = -1; // which ingredient data index this slot holds (-1 = empty/unassigned)

        IngredientSlotVisual(int x, int y, int size, boolean filled,
                             @javax.annotation.Nullable ItemStack depositedItem) {
            this.x = x;
            this.y = y;
            this.size = size;
            this.filled = filled;
            this.depositedItem = depositedItem;
            this.isPartial = false;
        }

        void draw(GuiGraphics g, float s4, int mouseX, int mouseY, BundleScreen screen, float alphaMult) {
            int u, v;

            if (filled && !isPartial) {
                // SDV: filled slot (completed) — DON'T draw slot background, only draw item
            } else {
                // SDV performHoverAction: hovered + can accept → (530, 262); otherwise → (512, 244)
                // SDV: partial donation slot also responds to hover for adding more
                if (isHovered(mouseX, mouseY) && screen.canAcceptDeposit()
                        && (screen.partialDonationItem == null || isPartial)) {
                    u = 530;
                    v = 262;
                } else {
                    u = 512;
                    v = 244;
                }

                g.pose().pushPose();
                g.pose().translate(x, y, 0.89f);
                g.pose().scale(s4, s4, 1.0f);
                g.setColor(1, 1, 1, alphaMult);
                g.blit(JUNIMO_NOTE, 0, 0, u, v, 18, 18, TEX_WIDTH, TEX_HEIGHT);
                g.setColor(1, 1, 1, 1);
                g.pose().popPose();
            }

            // SDV: c.drawItem(b, 4, 4) — item offset (4,4) screen px = (1,1) sprite px at 4× scale
            if (filled && depositedItem != null && !depositedItem.isEmpty()) {
                g.pose().pushPose();
                g.pose().translate(x, y, 0.9f);
                g.pose().scale(s4, s4, 1.0f);
                g.setColor(1, 1, 1, alphaMult);
                g.renderItem(depositedItem, 1, 1);
                g.renderItemDecorations(screen.font, depositedItem, 1, 1);
                g.setColor(1, 1, 1, 1);
                g.pose().popPose();
            }
        }

        boolean isHovered(int mouseX, int mouseY) {
            return mouseX >= x && mouseX <= x + size
                    && mouseY >= y && mouseY <= y + size;
        }
    }

    /**
     * Visual representation of a required ingredient in the list.
     * Shows the item icon + name tooltip + completion state.
     */
    @SuppressWarnings("unused")
    private static class IngredientListEntry {
        final int x, y, size;
        final ItemStack displayStack;
        final Component displayName;
        final boolean completed;
        final int requiredQuality;

        IngredientListEntry(int x, int y, int size, ItemStack displayStack, Component displayName,
                            boolean completed, int requiredQuality) {
            this.x = x;
            this.y = y;
            this.size = size;
            this.displayStack = displayStack;
            this.displayName = displayName;
            this.completed = completed;
            this.requiredQuality = requiredQuality;
        }

        void draw(GuiGraphics g, float s4, net.minecraft.client.gui.Font font, BundleScreen screen, float alphaMult) {
            if (displayStack.isEmpty()) return;

            // SDV: completed items drawn at alpha 0.25
            float alpha = completed ? 0.25f : alphaMult;

            if (alpha < 1.0f) {
                g.setColor(1, 1, 1, alpha);
            }

            // SDV: shadow blob under non-completed items
            // b.Draw(shadowTexture, center - shadowW*4/2 - 4, center + 4, bounds, White*alpha, 0, Zero, 4f)
            if (!completed) {
                int shadowW = (int)(6 * s4); // shadow texture ~12×6, drawn at 4× → 48×24 screen px, half = 24
                int shadowH = (int)(3 * s4);
                int shadowX = x + (int)(8 * s4) - shadowW - (int)(1 * s4);
                int shadowY = y + (int)(8 * s4) + (int)(1 * s4);
                int shadowAlpha = (int)(60 * alpha);
                g.fill(shadowX, shadowY, shadowX + shadowW * 2, shadowY + shadowH,
                        (shadowAlpha << 24)); // semi-transparent black ellipse approximation
            }

            // SDV: item.drawInMenu(b, pos, scale/4f=1.0, alpha, 0.9f, StackDraw, Color, noShadow)
            g.pose().pushPose();
            g.pose().translate(x, y, 0.9f);
            g.pose().scale(s4, s4, 1.0f);
            g.renderItem(displayStack, 0, 0);
            g.renderItemDecorations(font, displayStack, 0, 0);
            g.pose().popPose();

            // Quality star indicator — SDV uses Cursors sprite at bottom-left of item icon
            // SDV: Object.drawInMenu draws quality star at (12, 33) using:
            //   Silver: (338, 400, 8, 8)  Gold: (346, 400, 8, 8)  Iridium: (346, 392, 8, 8)
            if (requiredQuality > 0 && !completed) {
                int starU, starV;
                switch (requiredQuality) {
                    case 1 -> { starU = 338; starV = 400; } // Silver
                    case 2 -> { starU = 346; starV = 400; } // Gold
                    default -> { starU = 346; starV = 392; } // Iridium (quality >= 3)
                }
                // SDV draws at (pos.X + 12, pos.Y + 33) with scale based on quality
                float starScale = (requiredQuality >= 3) ? s4 * 0.5f : s4 * 0.4f;
                int starX = x + (int)(2 * s4);
                int starY = y + (int)(10 * s4);
                StardewGuiUtil.drawFromCursors(g, starX, starY, starU, starV, 8, 8, starScale);
            }

            if (alpha < 1.0f) {
                g.setColor(1, 1, 1, 1);
            }
        }

        boolean isHovered(int mouseX, int mouseY) {
            return mouseX >= x && mouseX <= x + size
                    && mouseY >= y && mouseY <= y + size;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  TempSprite — lightweight animated sprite (SDV TemporaryAnimatedSprite parity)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Lightweight stand-in for SDV's TemporaryAnimatedSprite.
     * Used for ingredient deposit animations and completion sparkles.
     */
    private static class TempSprite {
        int x, y;
        int u, v, frameW, frameH;
        int totalFrames;
        float interval; // ms per frame
        float timer;
        int currentFrame;
        boolean holdLastFrame;
        float lifetime; // ms total, -1 = until animation ends
        float age;
        /** SDV: endSound — played when animation reaches last frame (null = none) */
        @javax.annotation.Nullable
        net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.sounds.SoundEvent, net.minecraft.sounds.SoundEvent> endSound;
        private boolean endSoundPlayed = false;

        TempSprite(int x, int y, int u, int v, int frameW, int frameH,
                   int totalFrames, float interval, boolean holdLastFrame) {
            this.x = x;
            this.y = y;
            this.u = u;
            this.v = v;
            this.frameW = frameW;
            this.frameH = frameH;
            this.totalFrames = totalFrames;
            this.interval = interval;
            this.holdLastFrame = holdLastFrame;
            this.lifetime = -1;
            this.age = 0;
        }

        /** @return true if this sprite should be removed */
        boolean tick(float deltaMs) {
            age += deltaMs;
            timer += deltaMs;
            if (timer >= interval) {
                timer -= interval;
                if (currentFrame < totalFrames - 1) {
                    currentFrame++;
                } else if (!holdLastFrame) {
                    return true; // animation done, remove
                }
                // SDV: play endSound when reaching last frame
                if (currentFrame >= totalFrames - 1 && endSound != null && !endSoundPlayed) {
                    endSoundPlayed = true;
                    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                    if (mc != null && mc.getSoundManager() != null) {
                        mc.getSoundManager().play(SimpleSoundInstance.forUI(endSound.get(), 1.0f));
                    }
                }
            }
            if (lifetime > 0 && age >= lifetime) return true;
            return false;
        }

        void draw(GuiGraphics g, float s4, float alphaMult) {
            int frameU = u + currentFrame * frameW;
            g.pose().pushPose();
            g.pose().translate(x, y, 0.88f);
            g.pose().scale(s4, s4, 1.0f);
            if (alphaMult < 1.0f) g.setColor(1, 1, 1, alphaMult);
            g.blit(JUNIMO_NOTE, 0, 0, frameU, v, frameW, frameH, TEX_WIDTH, TEX_HEIGHT);
            if (alphaMult < 1.0f) g.setColor(1, 1, 1, 1);
            g.pose().popPose();
        }
    }
}

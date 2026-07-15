package com.stardew.craft.client.gui;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.ClientPlayerDataCache;
import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.client.gui.overnight.StardewGuiUtil;
import com.stardew.craft.network.payload.GeodeCrackPayload;
import com.stardew.craft.network.payload.GeodeClaimPayload;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * SDV GeodeMenu parity — exact 1:1 replica.
 * <p>
 * SDV coordinate system: all UI positions in SDV "screen pixels" (uiViewport coords).
 * Sprites from cursors drawn at 4× scale, menu tiles at 1× scale (= 64 SDV px per tile).
 * <p>
 * Conversion to MC GUI coordinates:
 * <pre>
 *   ui(sdvPx)  = Math.round(sdvPx / guiScale)  — SDV screen px → MC GUI px
 *   s4()       = 4.0f / guiScale                — sprite 4× scale (cursors, items)
 *   s1()       = 1.0f / guiScale                — UI-pixel-resolution (animations tilesheet, OK button)
 * </pre>
 * <p>
 * SDV IClickableMenu constants (SDV screen px):
 *   borderWidth = 40, spaceToClearTopBorder = 96, spaceToClearSideBorder = 16
 * <p>
 * SDV MenuWithInventory(okButton:true, trashCan:true, inventoryXOffset=12, inventoryYOffset=132):
 *   width  = 800 + borderWidth*2 = 880
 *   height = 600 + borderWidth*2 = 680
 */
@SuppressWarnings({"null", "unused"})
public class GeodeMenuScreen extends Screen {

    // ── Texture paths ──
    private static final ResourceLocation CLINT_HAMMERING = ResourceLocation.fromNamespaceAndPath(
        StardewCraft.MODID, "textures/gui/geode/clint_hammering.png");
    private static final ResourceLocation GEODE_BREAK_REGULAR = ResourceLocation.fromNamespaceAndPath(
        StardewCraft.MODID, "textures/gui/geode/geode_break_regular.png");
    private static final ResourceLocation GEODE_BREAK_FROZEN = ResourceLocation.fromNamespaceAndPath(
        StardewCraft.MODID, "textures/gui/geode/geode_break_frozen.png");
    private static final ResourceLocation GEODE_BREAK_MAGMA = ResourceLocation.fromNamespaceAndPath(
        StardewCraft.MODID, "textures/gui/geode/geode_break_magma.png");
    private static final ResourceLocation ARTIFACT_TROVE_BREAK = ResourceLocation.fromNamespaceAndPath(
        StardewCraft.MODID, "textures/gui/geode/artifact_trove_break.png");
    private static final ResourceLocation MYSTERY_BOX_BREAK = ResourceLocation.fromNamespaceAndPath(
        StardewCraft.MODID, "textures/gui/geode/mystery_box_break.png");
    private static final ResourceLocation GOLDEN_MYSTERY_BOX_BREAK = ResourceLocation.fromNamespaceAndPath(
        StardewCraft.MODID, "textures/gui/geode/golden_mystery_box_break.png");
    private static final ResourceLocation SPARKLE_TEX = ResourceLocation.fromNamespaceAndPath(
        StardewCraft.MODID, "textures/gui/geode/sparkle.png");
    private static final ResourceLocation FLUFF_SMOKE = ResourceLocation.fromNamespaceAndPath(
        StardewCraft.MODID, "textures/gui/geode/fluff_smoke.png");
    private static final ResourceLocation FLUFF_ARTIFACT_SHARD = ResourceLocation.fromNamespaceAndPath(
        StardewCraft.MODID, "textures/gui/geode/fluff_artifact_shard.png");

    // ── SDV IClickableMenu constants (SDV screen px) ──
    private static final int BORDER   = 40;
    private static final int SP_TOP   = 96;
    private static final int SP_SIDE  = 16;

    // ── SDV MenuWithInventory dimensions ──
    // width = 800 + borderWidth*2 = 880
    // height = 680 (SDV 12×3) → 756 for 9×4 inventory (extra 76 SDV px: 4 rows w/gaps vs 3 rows no gap)
    private static final int SDV_W = 880;
    private static final int SDV_H = 756;

    // ── GeodeSpot (xPos + spSide + border/2, yPos + spTop + 4) ──
    private static final int GS_OX = SP_SIDE + BORDER / 2;  // 36
    private static final int GS_OY = SP_TOP + 4;             // 100
    private static final int GS_W  = 560;
    private static final int GS_H  = 312;                    // 78×4

    // ── Inventory (matching ShopScreen 9×4 layout, inside the dialogue box) ──
    private static final int INV_COLS = 9;
    private static final int INV_ROWS = 4;
    private static final int SLOT_SIZE = 64;
    private static final int SLOT_GAP = 4;
    // SDV invY = yPos + spTop + border + 192 - 16 + inventoryYOffset(132) = yPos + 444
    private static final int INV_OY = 444;
    // Center 9-col grid (608 wide) in 880-wide frame: (880-608)/2 = 136
    private static final int INV_OX = (SDV_W - (INV_COLS * SLOT_SIZE + (INV_COLS - 1) * SLOT_GAP)) / 2;

    // ── OK button: xPos + width + 4, yPos + height - 192 - borderWidth ──
    private static final int OK_OX = SDV_W + 4;
    private static final int OK_OY = SDV_H - 192 - BORDER;  // 524

    // ── Horizontal partition: yPos + border + spTop + 256 = yPos + 392 ──
    // SDV: drawHorizontalPartition(b, yPos + borderWidth + spaceToClearTopBorder + 256)
    private static final int HPART_OY = BORDER + SP_TOP + 256;  // 392

    // ── Vertical upper partition at xPos + 576, height 328 ──
    private static final int VPART_OX = 576;
    private static final int VPART_H  = 328;

    // ── Description text at (xPos + 618, yPos + 104) ──
    private static final int DESC_OX = VPART_OX + 42;          // 618
    private static final int DESC_OY = BORDER + SP_TOP - 32;   // 104

    private static final int GEODE_COST = 25;

    // Items with price ≤ 75 → play newArtifact; rest → discoverMineral
    // SDV: if(!(treasure is Object) || price > 75 || mysteryBox) → discoverMineral + sparkle
    //      else → newArtifact (NO sparkle)
    // We approximate with a static set for items known to have price ≤ 75.
    // Mystery boxes always play discoverMineral regardless of treasure price.
    private static final Set<String> CHEAP_ITEMS = Set.of(
        "stardewcraft:stone", "stardewcraft:clay", "stardewcraft:copper_ore",
        "stardewcraft:iron_ore", "stardewcraft:coal", "stardewcraft:gold_ore",
        "stardewcraft:earth_crystal", "stardewcraft:frozen_tear"
    );

    // Clint frames: SDV (8,300)(9,200)(10,80)(11,200)(12,100)(8,300)
    private static final int[] CLINT_FR = {0, 1, 2, 3, 4, 0};
    private static final int[] CLINT_MS = {300, 200, 80, 200, 100, 300};

    // ── Instance state ──
    private float gs; // guiScale

    // Layout (MC GUI px)
    private int mx0, my0;        // menu origin (single dialogue box)
    private int gsX, gsY;        // geode spot origin
    private int invGridX, invGridY;
    private int slotSzGui;
    private int okX, okY, okW, okH;

    // Animation
    private int geodeAnimTimer;
    private long animStart;
    private int clintIdx;
    private long clintStart;
    private boolean clintAnim;

    // Destruction
    private String curGeoType;
    private int destFrame = -1;
    private long destStart;
    private boolean destDone;
    private boolean isSpecial;

    // Treasure
    private ItemStack geoTreasure = ItemStack.EMPTY;
    private String treasureId = "";
    private int yPosGem;
    private boolean tReveal;

    // Sparkle
    private int spkFrame = -1;
    private long spkStart;

    // Fluff
    private final List<FluffParticle> fluff = new ArrayList<>();

    // Geode on anvil
    private ItemStack geoSpotItem = ItemStack.EMPTY;

    // Held item from inventory
    private ItemStack held = ItemStack.EMPTY;
    private int heldSlot = -1;

    // Delay
    private float delayArt;
    // For special items: tracks time elapsed after delay ends, to model SDV’s invisible dummy anim
    private long destDelayElapsed;

    // Alert / description
    private int alertTimer;
    private int wiggleTimer;
    private String descText = "";

    // Hovered inventory slot
    private int hovSlot = -1;

    // Server
    private boolean waitServer;

    private final Random rng = new Random();

    public GeodeMenuScreen() {
        super(Component.empty());
    }

    // ── Coordinate conversion ──
    private int ui(int v) { return Math.round(v / gs); }
    private float s4() { return 4f / gs; }
    private float s1() { return 1f / gs; }

    @Override
    protected void init() {
        super.init();
        gs = (float) Minecraft.getInstance().getWindow().getGuiScale();

        // Single dialogue box — SDV_W × SDV_H, centered on screen
        int mw = ui(SDV_W);
        int mh = ui(SDV_H);
        mx0 = width / 2 - mw / 2;
        my0 = height / 2 - mh / 2;
        if (my0 < ui(8)) my0 = ui(8);

        // Geode spot origin
        gsX = mx0 + ui(GS_OX);
        gsY = my0 + ui(GS_OY);

        // Inventory grid inside the dialogue box
        slotSzGui = ui(SLOT_SIZE);
        invGridX = mx0 + ui(INV_OX);
        invGridY = my0 + ui(INV_OY);

        // OK button (right of dialogue box)
        okW = ui(64);
        okH = ui(64);
        okX = mx0 + ui(OK_OX);
        okY = my0 + ui(OK_OY);
    }

    // ── API for GeodeCrackResultPayload ──
    public void onCrackResult(String treasureItemId, String geodeType, int newMoney) {
        waitServer = false;
        curGeoType = geodeType;
        this.treasureId = treasureItemId;

        ResourceLocation rid = ResourceLocation.parse(treasureItemId);
        var opt = BuiltInRegistries.ITEM.getOptional(rid);
        geoTreasure = opt.map(ItemStack::new).orElse(ItemStack.EMPTY);

        ClientPlayerDataCache.setMoney(newMoney);

        geodeAnimTimer = 2700;
        animStart = System.currentTimeMillis();
        clintIdx = 0;
        clintStart = animStart;
        clintAnim = true;

        destFrame = -1;
        destDone = false;
        isSpecial = "artifact_trove".equals(geodeType) || geodeType.contains("mystery_box");
        yPosGem = 0;
        tReveal = false;
        spkFrame = -1;
        fluff.clear();
        delayArt = 0;
        destDelayElapsed = 0;

        playSound(ModSounds.STONE_STEP.get());
    }

    // ── Input ──

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 && button != 1) return super.mouseClicked(mouseX, mouseY, button);
        int mx = (int) mouseX, my = (int) mouseY;
        if (waitServer) return true;

        // OK button
        if (isIn(mx, my, okX, okY, okW, okH) && geodeAnimTimer <= 0 && held.isEmpty()) {
            playSound(ModSounds.BIG_DESELECT.get());
            onClose();
            return true;
        }

        // Inventory
        if (hovSlot >= 0 && geodeAnimTimer <= 0) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return true;
            ItemStack ss = mc.player.getInventory().getItem(hovSlot);

            if (held.isEmpty()) {
                if (!ss.isEmpty()) {
                    held = (button == 0) ? ss.copy() : ss.copyWithCount(1);
                    heldSlot = hovSlot;
                    playSound(ModSounds.DWOP.get());
                }
            } else {
                if (hovSlot == heldSlot || ss.isEmpty()) {
                    held = ItemStack.EMPTY;
                    heldSlot = -1;
                    playSound(ModSounds.DWOP.get());
                }
            }
        }

        // Geode spot
        if (isIn(mx, my, gsX, gsY, ui(GS_W), ui(GS_H))) {
            if (!held.isEmpty() && isGeode(held) && ClientPlayerDataCache.getMoney() >= GEODE_COST
                && geodeAnimTimer <= 0) {
                int free = countFreeSlots();
                if (free > 1 || (free == 1 && held.getCount() == 1)) {
                    geoSpotItem = held.copyWithCount(1);
                    held.shrink(1);
                    if (held.isEmpty()) held = ItemStack.EMPTY;
                    ClientPlayerDataCache.setMoney(ClientPlayerDataCache.getMoney() - GEODE_COST);
                    waitServer = true;
                    PacketDistributor.sendToServer(new GeodeCrackPayload(heldSlot));
                } else {
                    descText = Component.translatable("stardewcraft.geode.inventory_full").getString();
                    wiggleTimer = 500;
                    alertTimer = 1500;
                }
            } else if (ClientPlayerDataCache.getMoney() < GEODE_COST) {
                wiggleTimer = 500;
                playSound(ModSounds.CANCEL.get());
            }
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int kc, int sc, int mod) {
        if (geodeAnimTimer > 0) return true;
        if (kc == 256) {
            if (held.isEmpty() && !waitServer) onClose();
            return true;
        }
        return super.keyPressed(kc, sc, mod);
    }

    @Override
    public void onClose() {
        Minecraft mc = Minecraft.getInstance();
        if (!held.isEmpty() && mc.player != null) {
            mc.player.getInventory().add(held);
            held = ItemStack.EMPTY;
        }
        super.onClose();
    }

    // ── Tick ──

    @Override
    public void tick() {
        super.tick();
        if (alertTimer > 0) alertTimer -= 50;
        if (wiggleTimer > 0) wiggleTimer -= 50;
        if (geodeAnimTimer <= 0) return;

        long now = System.currentTimeMillis();
        geodeAnimTimer = Math.max(0, 2700 - (int)(now - animStart));

        // Clint
        if (clintAnim) {
            if (now - clintStart >= CLINT_MS[clintIdx]) {
                int prev = CLINT_FR[clintIdx];
                clintIdx++;
                if (clintIdx >= CLINT_FR.length) {
                    clintAnim = false;
                    clintIdx = CLINT_FR.length - 1;
                } else {
                    clintStart = now;
                    if (CLINT_FR[clintIdx] == 3 && prev != 3) onHammerHit();
                }
            }
        }

        // Destruction animation + reveal logic
        // SDV has two phases for special items (artifact_trove, mystery_box):
        //   Phase 1: destruction anim plays for 500ms (delayBeforeShowArtifactTimer)
        //            - visual destruction frames advance normally
        //            - yPosGem does NOT move
        //   Phase 2: after delay, invisible dummy anim counts 0→5 at 100ms/frame
        //            - yPosGem starts moving
        //            - reveal triggers at dummy frame 5
        // For regular geodes: no delay, destruction anim runs 0→7, reveal at frame 7
        if (destFrame >= 0 && !destDone) {
            // Visual destruction frame (for rendering only)
            int maxVisF = isSpecial ? 6 : 8;
            int visFrame = Math.min((int)((now - destStart) / 100), maxVisF - 1);
            destFrame = visFrame;

            if (isSpecial) {
                // Special item: two-phase logic
                if (delayArt > 0) {
                    // Phase 1: delay active, no gem movement, no reveal
                } else {
                    // Phase 2: after delay, count reveal frames separately
                    destDelayElapsed += 50; // ~1 MC tick
                    int revealFrame = Math.min((int)(destDelayElapsed / 100), 5);
                    if (revealFrame < 5) {
                        // SDV: if(frame<3) yPosGem--; yPosGem--; per 60fps frame
                        // frame<3: -2/SDVframe × 3 = -6/MCtick, frame>=3: -1/SDVframe × 3 = -3/MCtick
                        yPosGem -= (revealFrame < 3) ? 6 : 3;
                    }
                    if (revealFrame >= 5 && !tReveal) {
                        tReveal = true;
                        // SDV: mystery boxes always play discoverMineral + sparkle
                        boolean expensive = !CHEAP_ITEMS.contains(treasureId);
                        boolean isMystery = curGeoType != null && curGeoType.contains("mystery_box");
                        if (expensive || isMystery) {
                            spkFrame = 0;
                            spkStart = now;
                            playSound(ModSounds.DISCOVER_MINERAL.get());
                        } else {
                            playSound(ModSounds.NEW_ARTIFACT.get());
                            // SDV: no sparkle for newArtifact
                        }
                    }
                }
            } else {
                // Regular geode: single-phase logic
                int spkAt = 7;
                if (visFrame >= maxVisF - 1) destDone = true;
                if (delayArt <= 0 && visFrame < spkAt) {
                    // SDV: if(frame<3) yPosGem--; yPosGem--; per 60fps frame
                    // frame<3: -2/SDVframe × 3 = -6/MCtick, frame>=3: -1/SDVframe × 3 = -3/MCtick
                    yPosGem -= (visFrame < 3) ? 6 : 3;
                }
                if (visFrame >= spkAt && !tReveal) {
                    tReveal = true;
                    boolean expensive = !CHEAP_ITEMS.contains(treasureId);
                    if (expensive) {
                        spkFrame = 0;
                        spkStart = now;
                        playSound(ModSounds.DISCOVER_MINERAL.get());
                    } else {
                        playSound(ModSounds.NEW_ARTIFACT.get());
                        // SDV: no sparkle for newArtifact
                    }
                }
            }
        }

        if (delayArt > 0) {
            delayArt -= 50;
            if (delayArt < 0) delayArt = 0;
        }

        if (spkFrame >= 0) {
            spkFrame = (int)((now - spkStart) / 100);
            if (spkFrame >= 8) spkFrame = -1;
        }

        fluff.removeIf(p -> p.update(now));

        if (geodeAnimTimer <= 0) finalizeCrack();
    }

    private void onHammerHit() {
        if (curGeoType != null && ("artifact_trove".equals(curGeoType) || curGeoType.contains("mystery_box"))) {
            // SDV: hammer + woodWhack for artifact trove / mystery box
            playSound(ModSounds.HAMMER.get());
            // TODO: add woodWhack sound when asset is available
            // playSound(ModSounds.WOOD_WHACK.get());
        } else {
            // SDV: hammer + stoneCrack for regular geodes
            playSound(ModSounds.HAMMER.get());
            playSound(ModSounds.STONE_CRACK.get());
        }
        destFrame = 0;
        destStart = System.currentTimeMillis();
        // SDV: for special items (artifact_trove, mystery), delayBeforeShowArtifactTimer = 500
        // During this delay the destruction anim plays but yPosGem doesn't move.
        // After delay, a new invisible dummy anim starts counting frames for the reveal.
        // We model this with delayArt + destDelayElapsed to separate visual destruction from reveal timing.
        if (isSpecial) {
            delayArt = 500;
            destDelayElapsed = 0;
        }
        spawnFluff();
    }

    private void spawnFluff() {
        long now = System.currentTimeMillis();
        boolean myst = curGeoType != null && curGeoType.contains("mystery_box");
        // SDV smoke base: regular=(392-32=360, 192-16=176), mystery=(392-48=344, 192-24=168)
        int bx = myst ? 344 : 360;
        int by = myst ? 168 : 176;
        int rr = myst ? 32 : 21;

        for (int i = 0; i < 6; i++) {
            // Smoke: Cursors(372,1956,10,10) scale=3, color=(255,222,198), alphaFade=0.02/frame
            float smx = (rng.nextInt(41) - 20) / 10f;
            float smy = (rng.nextInt(16) + 5) / 10f;
            fluff.add(new FluffParticle(FLUFF_SMOKE,
                gsX + ui(bx + rng.nextInt(rr)), gsY + ui(by),
                smx / gs, smy / gs, 0, 0,
                0.02f, 3f / gs, 0.01f / gs,
                (rng.nextInt(11) - 5) * (float)Math.PI / 256f,
                now + i * 20L, 10, 10,
                1f, 222f/255f, 198f/255f));

            // Shard: (499,132,5,5) scale=4, alphaFade=0.015, accelY=0.25
            float shx = (rng.nextInt(61) - 30) / 10f;
            float shy = -(rng.nextInt(4) + 4);
            fluff.add(new FluffParticle(FLUFF_ARTIFACT_SHARD,
                gsX + ui(bx + rng.nextInt(rr)), gsY + ui(by),
                shx / gs, shy / gs, 0, 0.25f / gs,
                0.015f, 4f / gs, 0,
                (rng.nextInt(11) - 5) * (float)Math.PI / 256f,
                now + i * 10L, 5, 5,
                1, 1, 1));
        }
    }

    private void finalizeCrack() {
        // SDV: Game1.player.addItemToInventoryBool(geodeTreasure) at geodeAnimationTimer <= 0
        // Send claim to server to receive the actual treasure item.
        PacketDistributor.sendToServer(new GeodeClaimPayload());
        geoSpotItem = ItemStack.EMPTY;
        geoTreasure = ItemStack.EMPTY;
        curGeoType = null;
        destFrame = -1;
        destDone = false;
        yPosGem = 0;
        tReveal = false;
        spkFrame = -1;
        fluff.clear();
        delayArt = 0;
        destDelayElapsed = 0;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  RENDERING — matches SDV GeodeMenu.draw() order exactly
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        float s4 = s4();
        int mw = ui(SDV_W);
        int mh = ui(SDV_H);

        // ═══ 1. Dark background — SDV: Color.Black * 0.4f ═══
        g.fill(0, 0, width, height, 0x66000000);

        // ═══ 2. Dialogue box frame — ONE box, full SDV_W × SDV_H ═══
        // SDV drawDialogueBox uses addedTileHeightForQuestions = -1 for non-question menus,
        // which shifts the TOP of the frame DOWN by 64 SDV px (= -64 * -1).
        // The bottom stays at yPos + height. So the visible frame height = SDV_H - 64.
        int frameTopOff = ui(64);
        StardewGuiUtil.drawDialogueBoxFrame(g, mx0, my0 + frameTopOff, mw, mh - frameTopOff);

        // ═══ 3. Horizontal partition at yPos + 392 ═══
        // SDV: drawHorizontalPartition(b, yPos + borderWidth + spaceToClearTopBorder + 256)
        // Uses menuTexture tiles 4/6/7 at 64×64 SDV px each
        {
            int hpY = my0 + ui(HPART_OY);
            int tu = ui(64);
            CommonGuiTextures.drawMenuTile(g, mx0, hpY, tu, tu, 4);
            CommonGuiTextures.drawMenuTile(g, mx0 + tu, hpY, Math.max(0, mw - tu * 2), tu, 6);
            CommonGuiTextures.drawMenuTile(g, mx0 + mw - tu, hpY, tu, tu, 7);
        }

        // ═══ 4. Vertical upper intersecting partition at xPos + 576, 328px tall ═══
        // SDV: drawVerticalUpperIntersectingPartition(b, xPos + 576, 328)
        // IClickableMenu source:
        //   tile 44 at (xPos, yPos + 64)
        //   tile 63 at (xPos, yPos + 128), height = partitionHeight - 32 = 296
        //   tile 39 at (xPos, yPos + partitionHeight + 64) = (xPos, yPos + 392)
        {
            int vpX = mx0 + ui(VPART_OX);
            int tu = ui(64);
            CommonGuiTextures.drawMenuTile(g, vpX, my0 + ui(64), tu, tu, 44);
            CommonGuiTextures.drawMenuTile(g, vpX, my0 + ui(128), tu, ui(VPART_H - 32), 63);
            CommonGuiTextures.drawMenuTile(g, vpX, my0 + ui(VPART_H + 64), tu, tu, 39);
        }

        // ═══ 5. Description text (right panel above partition) ═══
        drawDesc(g);

        // ═══ 6. OK button ═══
        drawOk(g, mouseX, mouseY);

        // ═══ 7. Player inventory grid (9×4 inside the dialogue box) ═══
        drawInv(g, mouseX, mouseY, s4);

        // ═══ 8. Geode background — cursors(0,512,140,78) at 4× ═══
        GeodeMenuTextures.drawGeodeSpotBackground(g, gsX, gsY, s4);

        // ═══ 9. Geode item / destruction / treasure / sparkle ═══
        if (!geoSpotItem.isEmpty()) {
            if (destFrame < 0) {
                // SDV: item.drawInMenu(b, (geodeSpot.X+360, geodeSpot.Y+160)+offset, 1f)
                int iox = 360, ioy = 160;
                if (curGeoType != null) {
                    if ("artifact_trove".equals(curGeoType)) { iox -= 2; ioy += 2; }
                    else if (curGeoType.contains("mystery_box")) { iox -= 7; ioy += 4; }
                }
                drawSdvItem(g, geoSpotItem, gsX + ui(iox), gsY + ui(ioy), s4);
            } else {
                drawDestruction(g);
            }

            for (FluffParticle p : fluff) p.draw(g);

            if (!geoTreasure.isEmpty() && delayArt <= 0 && destFrame >= 0) {
                // SDV: (geodeSpot.X + (mystery?86:90)*4, geodeSpot.Y + 160 + yPositionOfGem)
                // yPosGem is in SDV screen pixels (decremented by 1-2 per 60fps frame)
                boolean myst = curGeoType != null && curGeoType.contains("mystery_box");
                int tox = myst ? 344 : 360;
                drawSdvItem(g, geoTreasure, gsX + ui(tox), gsY + ui(160 + yPosGem), s4);
            }

            if (spkFrame >= 0 && spkFrame < 8) drawSparkle(g);
        }

        // ═══ 10. Clint — 32×48 at 4× ═══
        drawClint(g, s4);

        // ═══ 11. Held item on cursor (SDV: mouseX+8, mouseY+8) ═══
        if (!held.isEmpty()) {
            drawSdvItem(g, held, mouseX + ui(8), mouseY + ui(8), s4);
            if (held.getCount() > 1) {
                String cnt = String.valueOf(held.getCount());
                int cw = font.width(cnt);
                g.drawString(font, cnt,
                    mouseX + ui(8) + ui(64) - cw - 2,
                    mouseY + ui(8) + ui(64) - font.lineHeight,
                    0xFFFFFF, false);
            }
        }

        // ═══ 12. Tooltip ═══
        Minecraft mc = Minecraft.getInstance();
        if (hovSlot >= 0 && held.isEmpty() && mc.player != null) {
            ItemStack st = mc.player.getInventory().getItem(hovSlot);
            if (!st.isEmpty()) g.renderTooltip(font, st, mouseX, mouseY);
        }
    }

    // ── Draw item at SDV drawInMenu scale (16px × s4 = 64 SDV px) ──
    private void drawSdvItem(GuiGraphics g, ItemStack stack, int x, int y, float s4) {
        CommonGuiTextures.drawItem(g, stack, x, y, s4);
    }

    // ── Description text ──
    // SDV: (xPos+618, yPos+104), Game1.textColor * 0.75f, wrapped to 224px
    private void drawDesc(GuiGraphics g) {
        if (alertTimer > 0) return;
        String t = descText.isEmpty()
            ? (ClientPlayerDataCache.getMoney() < GEODE_COST
                ? Component.translatable("stardewcraft.geode.not_enough_money").getString()
                : Component.translatable("stardewcraft.geode.description").getString())
            : descText;
        int wg = (wiggleTimer > 0) ? (rng.nextInt(5) - 2) : 0;
        int tx = mx0 + ui(DESC_OX) + wg;
        int ty = my0 + ui(DESC_OY) + wg;
        // SDV wraps to max_width=224 SDV px → ui(224)
        // Use font.split() for proper CJK/Chinese character-level line breaking
        int mw = ui(224);
        List<FormattedCharSequence> lines = font.split(Component.literal(t), mw);
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, tx, ty, 0xBF5C2B00, false);
            ty += font.lineHeight + 2;
        }
    }

    // ── OK button ── cursors (128,256,64,64) at scale 1 (SDV ClickableTextureComponent scale=1)
    private void drawOk(GuiGraphics g, int mx, int my) {
        boolean hov = isIn(mx, my, okX, okY, okW, okH);
        g.pose().pushPose();
        if (hov) {
            float cx = okX + okW / 2f, cy = okY + okH / 2f;
            g.pose().translate(cx, cy, 0);
            g.pose().scale(1.1f, 1.1f, 1f);
            g.pose().translate(-cx, -cy, 0);
        }
        GeodeMenuTextures.drawOkButton(g, okX, okY, s1());
        g.pose().popPose();
    }

    // ── Clint: (geodeSpot.X + 384, geodeSpot.Y + 64), 32×48 at 4× ──
    private void drawClint(GuiGraphics g, float s4) {
        int fr = clintAnim ? CLINT_FR[clintIdx] : 0;
        int sx = fr * 32;
        g.pose().pushPose();
        g.pose().translate(gsX + ui(384), gsY + ui(64), 0);
        g.pose().scale(s4, s4, 1f);
        g.blit(CLINT_HAMMERING, 0, 0, sx, 0, 32, 48, 160, 48);
        g.pose().popPose();
    }

    // ── Destruction animation ──
    private void drawDestruction(GuiGraphics g) {
        if (destFrame < 0 || curGeoType == null) return;

        ResourceLocation tex;
        int fw, fh, nf, sw, sh;
        float ds;

        switch (curGeoType) {
            case "frozen_geode":
                // SDV: animations(0, 512, 64, 64) scale=1
                tex = GEODE_BREAK_FROZEN; fw = 64; fh = 64; nf = 8; sw = 512; sh = 64;
                ds = s1();
                break;
            case "magma_geode":
                tex = GEODE_BREAK_MAGMA; fw = 64; fh = 64; nf = 8; sw = 512; sh = 64;
                ds = s1();
                break;
            case "artifact_trove":
                // SDV: temporary_sprites_1(388,123,18,21) scale=4
                tex = ARTIFACT_TROVE_BREAK; fw = 18; fh = 21; nf = 6; sw = 108; sh = 21;
                ds = s4();
                break;
            case "mystery_box":
                // SDV: Cursors_1_6(0,27,24,24) scale=4
                tex = MYSTERY_BOX_BREAK; fw = 24; fh = 24; nf = 8; sw = 192; sh = 24;
                ds = s4();
                break;
            case "golden_mystery_box":
                tex = GOLDEN_MYSTERY_BOX_BREAK; fw = 24; fh = 24; nf = 8; sw = 192; sh = 24;
                ds = s4();
                break;
            default: // geode, omni_geode
                // SDV: animations(0, 448, 64, 64) scale=1
                tex = GEODE_BREAK_REGULAR; fw = 64; fh = 64; nf = 8; sw = 512; sh = 64;
                ds = s1();
                break;
        }

        int fi = Math.min(destFrame, nf - 1);
        int su = fi * fw;

        // SDV positions relative to geodeSpot:
        //   regular:  (392-32, 192-32) = (360, 160)
        //   artifact: (380-32, 192-32) = (348, 160)
        //   mystery:  (380-48, 192-48) = (332, 144)
        int ox, oy;
        if ("artifact_trove".equals(curGeoType)) { ox = 348; oy = 160; }
        else if (curGeoType.contains("mystery_box")) { ox = 332; oy = 144; }
        else { ox = 360; oy = 160; }

        g.pose().pushPose();
        g.pose().translate(gsX + ui(ox), gsY + ui(oy), 0);
        g.pose().scale(ds, ds, 1f);
        g.blit(tex, 0, 0, su, 0, fw, fh, sw, sh);
        g.pose().popPose();
    }

    // ── Sparkle: animations(0,640,64,64) scale=1 ──
    // SDV: position = (geodeSpot.X + (mystery?94:98)*4 - 32, geodeSpot.Y + 192 + yPositionOfGem - 32)
    // = (geodeSpot.X + (mystery?344:360), geodeSpot.Y + 160 + yPositionOfGem)
    // Note: 98*4-32=360, 94*4-32=344; 192-32=160. yPositionOfGem is SDV screen px.
    private void drawSparkle(GuiGraphics g) {
        if (spkFrame < 0 || spkFrame >= 8) return;
        boolean myst = curGeoType != null && curGeoType.contains("mystery_box");
        int sox = myst ? 344 : 360;
        int soy = 160 + yPosGem;
        g.pose().pushPose();
        g.pose().translate(gsX + ui(sox), gsY + ui(soy), 0);
        g.pose().scale(s1(), s1(), 1f);
        g.blit(SPARKLE_TEX, 0, 0, spkFrame * 64, 0, 64, 64, 512, 64);
        g.pose().popPose();
    }

    // ── Inventory (matching ShopScreen 9×4 layout) ──
    // Row 3 = hotbar (slots 0-8), rows 0-2 = main inventory (9-35)
    private void drawInv(GuiGraphics g, int mouseX, int mouseY, float s4) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int sz  = slotSzGui;
        int gap = ui(SLOT_GAP);
        hovSlot = -1;

        for (int row = 0; row < INV_ROWS; row++) {
            for (int col = 0; col < INV_COLS; col++) {
                // row 3 = hotbar (slots 0-8), rows 0-2 = main inventory (9-35)
                int mcSlot = (row == 3) ? col : (9 + row * 9 + col);
                int sx = invGridX + col * (sz + gap);
                int sy = invGridY + row * (sz + gap);
                boolean hov = isIn(mouseX, mouseY, sx, sy, sz, sz);
                if (hov) hovSlot = mcSlot;

                // Get the actual inventory item, adjusting for held item visual sync
                ItemStack stack = mc.player.getInventory().getItem(mcSlot);
                if (mcSlot == heldSlot && !held.isEmpty() && !stack.isEmpty()) {
                    int remaining = stack.getCount() - held.getCount();
                    if (remaining <= 0) {
                        stack = ItemStack.EMPTY;
                    } else {
                        stack = stack.copyWithCount(remaining);
                    }
                }

                // SDV highlightGeodes: geodes bright when nothing held, all bright when holding
                boolean highlight = !held.isEmpty() || isGeode(stack);

                CommonGuiTextures.drawMenuTile(g, sx, sy, sz, sz, 10);
                if (hov) {
                    g.fill(sx, sy, sx + sz, sy + sz, 0x35FFFFFF);
                }

                if (!stack.isEmpty()) {
                    int ix = sx + (sz - CommonGuiTextures.itemSize(s4)) / 2;
                    int iy = sy + (sz - CommonGuiTextures.itemSize(s4)) / 2;
                    if (!highlight) g.setColor(0.62f, 0.62f, 0.62f, 1f);
                    CommonGuiTextures.drawItemWithDecorations(g, font, stack, ix, iy, s4);
                    if (!highlight) g.setColor(1f, 1f, 1f, 1f);
                }
            }
        }
    }

    // ── Helpers ──

    private boolean isGeode(ItemStack s) {
        if (s.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(s.getItem());
        String p = id.getPath();
        return "geode".equals(p) || "frozen_geode".equals(p)
            || "magma_geode".equals(p) || "omni_geode".equals(p)
            || "artifact_trove".equals(p) || "mystery_box".equals(p)
            || "golden_mystery_box".equals(p);
    }

    private int countFreeSlots() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        int c = 0;
        for (int i = 0; i < 36; i++) if (mc.player.getInventory().getItem(i).isEmpty()) c++;
        return c;
    }

    private boolean isIn(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void playSound(SoundEvent ev) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ev, 1f, 1f));
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ══════════════════════════════════════════════════════════════════════
    //  Fluff particle
    // ══════════════════════════════════════════════════════════════════════
    private static class FluffParticle {
        final ResourceLocation tex;
        float x, y, vx, vy, ax, ay;
        final float fade;  // alpha decrease per SDV 60fps frame
        float scale, dScale;
        float rot, dRot;
        final long t0;
        float alpha = 1f;
        final int tw, th;
        boolean dead;
        final float tR, tG, tB;

        FluffParticle(ResourceLocation tex, float x, float y,
                      float vx, float vy, float ax, float ay,
                      float fade, float scale, float dScale,
                      float dRot, long t0, int tw, int th,
                      float tR, float tG, float tB) {
            this.tex = tex; this.x = x; this.y = y;
            this.vx = vx; this.vy = vy; this.ax = ax; this.ay = ay;
            this.fade = fade; this.scale = scale; this.dScale = dScale;
            this.rot = 0; this.dRot = dRot;
            this.t0 = t0; this.tw = tw; this.th = th;
            this.tR = tR; this.tG = tG; this.tB = tB;
        }

        boolean update(long now) {
            if (now < t0) return false;
            // 3 SDV frames per MC tick
            for (int i = 0; i < 3; i++) {
                x += vx; y += vy;
                vx += ax; vy += ay;
                alpha -= fade;
                scale += dScale;
                rot += dRot;
            }
            if (alpha <= 0 || scale <= 0) dead = true;
            return dead;
        }

        void draw(GuiGraphics g) {
            if (dead || alpha <= 0) return;
            g.setColor(tR, tG, tB, Math.max(0, alpha));
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(scale, scale, 1f);
            if (rot != 0) g.pose().mulPose(com.mojang.math.Axis.ZP.rotation(rot));
            g.blit(tex, -tw / 2, -th / 2, 0, 0, tw, th, tw, th);
            g.pose().popPose();
            g.setColor(1f, 1f, 1f, 1f);
        }
    }
}

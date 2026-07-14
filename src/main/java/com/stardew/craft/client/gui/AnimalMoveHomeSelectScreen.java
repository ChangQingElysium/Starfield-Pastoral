package com.stardew.craft.client.gui;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.network.payload.AnimalMoveHomeSelectPayload;
import com.stardew.craft.network.payload.OpenAnimalMoveHomeScreenPayload;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nonnull;
import java.util.List;

@SuppressWarnings("null")
public class AnimalMoveHomeSelectScreen extends Screen {

    // Use the same base frame as AnimalQuery so both screens feel contiguous.
    private static final int BASE_W = 677;
    private static final int BASE_H = 360;
    private static final float UI_SCALE = 0.5f;

    private static final int FIG_PORTRAIT_CX = 148;
    private static final int FIG_PORTRAIT_CY = 156;
    private static final int FIG_PORTRAIT_SIZE = 308;

    private static final int FIG_ROW_TOP_X = 355;
    private static final int FIG_ROW_TOP_Y = 84;
    private static final int FIG_ROW_MID_X = 383;
    private static final int FIG_ROW_MID_Y = FIG_PORTRAIT_CY;
    private static final int FIG_ROW_BOT_Y = 228;
    private static final int FIG_ROW_TOP_W = 300;
    private static final int FIG_ROW_MID_W = 322;

    private static final int FIG_ARROW_CX = 326;
    private static final int FIG_ARROW_CY = FIG_PORTRAIT_CY;

    private static final int FIG_PAGE_UP_CX = 720;
    private static final int FIG_PAGE_UP_CY = 42;
    private static final int FIG_PAGE_DOWN_CX = 720;
    private static final int FIG_PAGE_DOWN_CY = 196;

    private static final int FIG_CANCEL_CX = 430;
    private static final int FIG_CANCEL_CY = 310;
    private static final int FIG_OK_CX = 534;
    private static final int FIG_OK_CY = 310;

    private static final int BRIDGE_QUERY_OUT_MS = 220;
    private static final int BRIDGE_LIST_IN_DELAY_MS = 120;
    private static final int BRIDGE_LIST_IN_MS = 260;

    private static final int COLOR_OVERLAY = 0x66121216;
    private static final int COLOR_CIRCLE = 0xFFE3B275;
    private static final int COLOR_TEXT_MAIN = 0xFFF2F2F2;
    private static final int COLOR_TEXT_SUB = 0xFF9A9A9A;
    private static final int COLOR_TEXT_DIM = 0x6E737373;
    private static final int COLOR_LINE = 0x9DA4A4A4;

    private static final ResourceLocation PAGE_UP = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/page_up.png");
    private static final ResourceLocation PAGE_DOWN = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/page_down.png");
    private static final ResourceLocation OK_ICON = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/ok_yes_tile46.png");
    private static final ResourceLocation NO_ICON = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/cancel_no_tile47.png");
    private static final ResourceLocation SELL_ICON = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/sell_icon.png");
    private static final ResourceLocation MOVE_ICON = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/move_icon.png");
    private static final ResourceLocation REPRO_OFF_ICON = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/animal_query/repro_off.png");

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

    private final OpenAnimalMoveHomeScreenPayload payload;
    private final List<OpenAnimalMoveHomeScreenPayload.BuildingOption> options;

    private int baseX;
    private int baseY;

    private int selectedIndex = -1;
    private float visualIndex = 0.0f;
    private float overscrollOffset = 0.0f;
    private float overscrollVelocity = 0.0f;

    private float upScale = 1.52f;
    private float downScale = 1.52f;
    private float okScale = 1.58f;
    private float cancelScale = 1.58f;
    private float listRevealAlpha = 1.0f;

    private int hoverHotspot = 0;
    private long openedAtMs;

    public AnimalMoveHomeSelectScreen(OpenAnimalMoveHomeScreenPayload payload) {
        super(Component.translatable("container.stardew_craft.animal_move_home"));
        this.payload = payload;
        this.options = payload.options();
    }

    @Override
    protected void init() {
        super.init();
        computeLayout();

        this.selectedIndex = findInitialSelection();
        this.visualIndex = this.selectedIndex < 0 ? 0.0f : this.selectedIndex;
        this.hoverHotspot = 0;
        this.openedAtMs = System.currentTimeMillis();
    }

    @Override
    public void tick() {
        super.tick();
        float target = this.selectedIndex < 0 ? 0.0f : this.selectedIndex;
        this.visualIndex += (target + this.overscrollOffset - this.visualIndex) * 0.24f;
        if (Math.abs(target - this.visualIndex) < 0.001f) {
            this.visualIndex = target;
        }

        // Critically damped-ish spring to make edge scrolling bounce back smoothly.
        this.overscrollVelocity += (-this.overscrollOffset) * 0.24f;
        this.overscrollVelocity *= 0.68f;
        this.overscrollOffset += this.overscrollVelocity;
        if (Math.abs(this.overscrollOffset) < 0.001f && Math.abs(this.overscrollVelocity) < 0.001f) {
            this.overscrollOffset = 0.0f;
            this.overscrollVelocity = 0.0f;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY > 0) {
            scrollBy(-1);
            return true;
        }
        if (scrollY < 0) {
            scrollBy(1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        int mx = (int) mouseX;
        int my = (int) mouseY;

        int upCx = sx(FIG_PAGE_UP_CX);
        int upCy = sy(FIG_PAGE_UP_CY);
        int downCx = sx(FIG_PAGE_DOWN_CX);
        int downCy = sy(FIG_PAGE_DOWN_CY);
        int okCx = sx(FIG_OK_CX);
        int okCy = sy(FIG_OK_CY);
        int cancelCx = sx(FIG_CANCEL_CX);
        int cancelCy = sy(FIG_CANCEL_CY);

        int pageHit = si(48);
        int actionHit = si(56);

        if (inside(mx, my, upCx - pageHit / 2, upCy - pageHit / 2, pageHit, pageHit)) {
            scrollBy(-1);
            playUi(ModSounds.SMALL_SELECT.get(), 0.78f, 1.05f);
            return true;
        }
        if (inside(mx, my, downCx - pageHit / 2, downCy - pageHit / 2, pageHit, pageHit)) {
            scrollBy(1);
            playUi(ModSounds.SMALL_SELECT.get(), 0.78f, 0.98f);
            return true;
        }
        if (inside(mx, my, okCx - actionHit / 2, okCy - actionHit / 2, actionHit, actionHit)) {
            confirmMove();
            return true;
        }
        if (inside(mx, my, cancelCx - actionHit / 2, cancelCy - actionHit / 2, actionHit, actionHit)) {
            playUi(ModSounds.SMALL_SELECT.get(), 0.8f, 1.0f);
            onClose();
            return true;
        }

        int hit = pickRowAt(mx, my);
        if (hit >= 0 && hit < options.size()) {
            this.selectedIndex = hit;
            playUi(ModSounds.SMALL_SELECT.get(), 0.72f, 1.08f);
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 265) {
            scrollBy(-1);
            return true;
        }
        if (keyCode == 264) {
            scrollBy(1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        computeLayout();
        updateHoverState(mouseX, mouseY);

        this.renderTransparentBackground(graphics);
        graphics.fill(0, 0, this.width, this.height, COLOR_OVERLAY);

        long elapsed = Math.max(0L, System.currentTimeMillis() - this.openedAtMs);
        float queryOut = easeOutCubic(Math.min(1.0f, elapsed / (float) BRIDGE_QUERY_OUT_MS));
        float listIn = easeOutCubic(Math.min(1.0f, Math.max(0.0f, (elapsed - BRIDGE_LIST_IN_DELAY_MS) / (float) BRIDGE_LIST_IN_MS)));
        this.listRevealAlpha = listIn;

        if (queryOut < 1.0f) {
            drawQueryGhostTransition(graphics, queryOut);
        }

        graphics.pose().pushPose();
        graphics.pose().translate(0, Math.round((1.0f - queryOut) * si(10)), 0);
        drawPortrait(graphics, queryOut);
        graphics.pose().popPose();

        int pivotX = sx(FIG_ROW_MID_X);
        int pivotY = sy(FIG_ROW_MID_Y + 20);
        graphics.pose().pushPose();
        graphics.pose().translate(Math.round((1.0f - listIn) * si(42)), 0, 0);
        graphics.pose().translate(pivotX, pivotY, 0);
        graphics.pose().scale(0.90f + 0.10f * listIn, 0.95f + 0.05f * listIn, 1.0f);
        graphics.pose().translate(-pivotX, -pivotY, 0);
        drawCurvedRows(graphics);
        graphics.pose().popPose();

        graphics.pose().pushPose();
        graphics.pose().translate(0, Math.round((1.0f - listIn) * si(26)), 0);
        drawPageControls(graphics, mouseX, mouseY);
        drawBottomButtons(graphics, mouseX, mouseY);
        graphics.pose().popPose();
    }

    private void computeLayout() {
        this.baseX = (this.width - si(BASE_W)) / 2;
        this.baseY = (this.height - si(BASE_H)) / 2;
    }

    private void drawPortrait(GuiGraphics graphics, float bridgeProgress) {
        int cx = sx(FIG_PORTRAIT_CX);
        int queryCy = sy(140);
        int targetCy = sy(FIG_PORTRAIT_CY);
        int cy = Math.round(queryCy + (targetCy - queryCy) * bridgeProgress);

        int queryRadius = si(229 / 2);
        int targetRadius = si(Math.round(FIG_PORTRAIT_SIZE / 2f));
        int radius = Math.round(queryRadius + (targetRadius - queryRadius) * bridgeProgress);

        drawFilledCircle(graphics, cx, cy, radius, COLOR_CIRCLE);

        ResourceLocation icon = resolveAnimalIcon();
        // Slightly bigger than AnimalQuery portrait to occupy the old heart-strip area.
        float scale = 2.42f + (2.65f - 2.42f) * bridgeProgress;
        int src = 32;
        int drawX = cx - Math.round(src * scale / 2.0f);
        int drawY = cy - Math.round(src * scale / 2.0f);

        graphics.pose().pushPose();
        graphics.pose().translate(drawX, drawY, 0);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.blit(icon, 0, 0, 0, 0, src, src, src, src);
        graphics.pose().popPose();
    }

    private void drawCurvedRows(GuiGraphics graphics) {
        int floor = (int) Math.floor(this.visualIndex);
        for (int i = floor - 2; i <= floor + 2; i++) {
            float d = i - this.visualIndex;
            float abs = Math.abs(d);
            if (abs > 1.35f) {
                continue;
            }

            float progress = Math.max(0.0f, 1.0f - abs);
            float x = sx(FIG_ROW_TOP_X) + (sx(FIG_ROW_MID_X) - sx(FIG_ROW_TOP_X)) * progress;
            float y;
            if (d < 0) {
                y = sy(FIG_ROW_MID_Y) + d * (sy(FIG_ROW_MID_Y) - sy(FIG_ROW_TOP_Y));
            } else {
                y = sy(FIG_ROW_MID_Y) + d * (sy(FIG_ROW_BOT_Y) - sy(FIG_ROW_MID_Y));
            }

            float sizeScale = 0.84f + 0.16f * progress;
            float alpha = (0.26f + 0.74f * progress) * this.listRevealAlpha;
            boolean selected = abs < 0.34f;

            OpenAnimalMoveHomeScreenPayload.BuildingOption option = (i >= 0 && i < options.size()) ? options.get(i) : null;
            drawSingleRow(graphics, option, x, y, sizeScale, alpha, selected);
        }

        // Draw pointer last so it is never clipped by row elements.
        drawSelectionArrow(graphics);
    }

    private void drawSingleRow(
        GuiGraphics graphics,
        OpenAnimalMoveHomeScreenPayload.BuildingOption option,
        float x,
        float y,
        float scale,
        float alpha,
        boolean selected
    ) {
        int rowX = Math.round(x);
        int rowY = Math.round(y);

        if (option == null) {
            return;
        }

        int bullet = selected ? si(4) : si(3);
        drawFilledCircle(graphics, rowX - si(24), rowY + si(10), bullet, alphaColor(selected ? 0xFFFFFFFF : 0xFF9C9C9C, alpha));

        int lineW = selected ? si(FIG_ROW_MID_W - 150) : si(FIG_ROW_TOP_W - 150);
        graphics.fill(rowX + si(6), rowY + si(8), rowX + si(6) + lineW, rowY + si(9), alphaColor(COLOR_LINE, alpha));

        String occupancy = option.animalCount() + "/" + option.capacity();
        Component status = Component.translatable(option.selectable()
            ? "stardewcraft.animal.query.move_status.available"
            : (option.buildingId().equals(payload.currentBuildingId())
                ? "stardewcraft.animal.query.move_status.current"
                : "stardewcraft.animal.query.move_status.full"));
        int statusColor = option.selectable() ? 0xFF92E472 : (option.buildingId().equals(payload.currentBuildingId()) ? 0xFFB6A58A : 0xFFCF6E62);

        float titleScale = (selected ? 0.92f : 0.78f) * scale;
        float subScale = (selected ? 0.70f : 0.62f) * scale;

        drawScaledText(graphics, trim(option.displayName(), 16), rowX, rowY - si(14), titleScale, alphaColor(selected ? COLOR_TEXT_MAIN : COLOR_TEXT_SUB, alpha));
        drawScaledText(graphics, trim(option.buildingId(), 18), rowX, rowY + si(14), subScale, alphaColor(selected ? COLOR_TEXT_SUB : COLOR_TEXT_DIM, alpha));
        drawScaledText(graphics, occupancy, rowX + si(164), rowY - si(14), titleScale, alphaColor(selected ? COLOR_TEXT_MAIN : COLOR_TEXT_SUB, alpha));
        drawScaledText(graphics, status, rowX + si(285), rowY - si(2), subScale, alphaColor(statusColor, alpha));
    }

    private void drawSelectionArrow(GuiGraphics graphics) {
        int cx = sx(FIG_ARROW_CX);
        int cy = sy(FIG_ARROW_CY);

        int halfH = si(14);
        int bodyW = si(6);
        int tipW = si(11);
        int color = 0xFFE5E5E5;

        // Solid right-pointing triangle + small stem for a clean pointer silhouette.
        int step = Math.max(1, halfH / 6);
        for (int dy = -halfH; dy <= halfH; dy += step) {
            float t = 1.0f - (Math.abs(dy + step / 2.0f) / (float) halfH);
            int w = Math.max(1, Math.round(bodyW + (tipW - bodyW) * Math.max(0, t)));
            int h = Math.min(step, halfH - dy + 1);
            if (h > 0) {
                graphics.fill(cx - bodyW, cy + dy, cx - bodyW + w, cy + dy + h, color);
            }
        }
    }

    private void drawPageControls(GuiGraphics graphics, int mouseX, int mouseY) {
        int upCx = sx(FIG_PAGE_UP_CX);
        int upCy = sy(FIG_PAGE_UP_CY);
        int downCx = sx(FIG_PAGE_DOWN_CX);
        int downCy = sy(FIG_PAGE_DOWN_CY);

        int pageHit = si(48);
        boolean hoverUp = inside(mouseX, mouseY, upCx - pageHit / 2, upCy - pageHit / 2, pageHit, pageHit);
        boolean hoverDown = inside(mouseX, mouseY, downCx - pageHit / 2, downCy - pageHit / 2, pageHit, pageHit);

        this.upScale = approach(this.upScale, hoverUp ? 1.64f : 1.52f);
        this.downScale = approach(this.downScale, hoverDown ? 1.64f : 1.52f);

        drawScaledIcon(graphics, PAGE_UP, upCx, upCy, this.upScale, tintAlpha(this.selectedIndex > 0 ? 0xFFFFFFFF : 0x66FFFFFF, this.listRevealAlpha));
        drawScaledIcon(graphics, PAGE_DOWN, downCx, downCy, this.downScale, tintAlpha(this.selectedIndex < options.size() - 1 ? 0xFFFFFFFF : 0x66FFFFFF, this.listRevealAlpha));
    }

    private void drawBottomButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        int okCx = sx(FIG_OK_CX);
        int okCy = sy(FIG_OK_CY);
        int cancelCx = sx(FIG_CANCEL_CX);
        int cancelCy = sy(FIG_CANCEL_CY);

        int actionHit = si(56);
        boolean hoverOk = inside(mouseX, mouseY, okCx - actionHit / 2, okCy - actionHit / 2, actionHit, actionHit);
        boolean hoverCancel = inside(mouseX, mouseY, cancelCx - actionHit / 2, cancelCy - actionHit / 2, actionHit, actionHit);

        this.okScale = approach(this.okScale, hoverOk ? 1.74f : 1.58f);
        this.cancelScale = approach(this.cancelScale, hoverCancel ? 1.74f : 1.58f);

        drawScaledIcon(graphics, NO_ICON, cancelCx, cancelCy, this.cancelScale, tintAlpha(0xFFFFFFFF, this.listRevealAlpha));
        drawScaledIcon(graphics, OK_ICON, okCx, okCy, this.okScale, tintAlpha(canMoveSelection() ? 0xFFFFFFFF : 0x77FFFFFF, this.listRevealAlpha));
    }

    private void scrollBy(int delta) {
        if (options.isEmpty()) {
            return;
        }
        int previous = this.selectedIndex;
        int next = Math.max(0, Math.min(options.size() - 1, this.selectedIndex + delta));
        if (next != this.selectedIndex) {
            this.selectedIndex = next;
            playUi(ModSounds.SMALL_SELECT.get(), 0.66f, delta > 0 ? 0.98f : 1.06f);
            return;
        }

        // Edge reached: trigger a small spring impulse then bounce back.
        if (previous == 0 && delta < 0) {
            this.overscrollVelocity -= 0.10f;
            playUi(ModSounds.SMALL_SELECT.get(), 0.55f, 0.9f);
        } else if (previous == options.size() - 1 && delta > 0) {
            this.overscrollVelocity += 0.10f;
            playUi(ModSounds.SMALL_SELECT.get(), 0.55f, 0.9f);
        }
    }

    private int findInitialSelection() {
        if (options.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).buildingId().equals(payload.currentBuildingId())) {
                return i;
            }
        }
        return 0;
    }

    private int pickRowAt(int mx, int my) {
        int floor = (int) Math.floor(this.visualIndex);
        int bestIndex = -1;
        double bestDist = Double.MAX_VALUE;

        for (int i = floor - 2; i <= floor + 2; i++) {
            if (i < 0 || i >= options.size()) {
                continue;
            }
            float d = i - this.visualIndex;
            float abs = Math.abs(d);
            if (abs > 1.35f) {
                continue;
            }

            float progress = Math.max(0.0f, 1.0f - abs);
            float x = sx(FIG_ROW_TOP_X) + (sx(FIG_ROW_MID_X) - sx(FIG_ROW_TOP_X)) * progress;
            float y;
            if (d < 0) {
                y = sy(FIG_ROW_MID_Y) + d * (sy(FIG_ROW_MID_Y) - sy(FIG_ROW_TOP_Y));
            } else {
                y = sy(FIG_ROW_MID_Y) + d * (sy(FIG_ROW_BOT_Y) - sy(FIG_ROW_MID_Y));
            }

            int bx = Math.round(x) - si(22);
            int by = Math.round(y) - si(30);
            int bw = si(420);
            int bh = si(70);
            if (!inside(mx, my, bx, by, bw, bh)) {
                continue;
            }

            double dist = Math.hypot(mx - x, my - y);
            if (dist < bestDist) {
                bestDist = dist;
                bestIndex = i;
            }
        }

        return bestIndex;
    }

    private void confirmMove() {
        if (!canMoveSelection()) {
            playUi(ModSounds.SMALL_SELECT.get(), 0.72f, 0.84f);
            return;
        }
        OpenAnimalMoveHomeScreenPayload.BuildingOption selected = options.get(selectedIndex);
        PacketDistributor.sendToServer(new AnimalMoveHomeSelectPayload(payload.animalId(), selected.buildingId()));
        playUi(ModSounds.NEW_RECIPE.get(), 0.88f, 1.0f);
        onClose();
    }

    private boolean canMoveSelection() {
        if (selectedIndex < 0 || selectedIndex >= options.size()) {
            return false;
        }
        return options.get(selectedIndex).selectable();
    }

    private ResourceLocation resolveAnimalIcon() {
        return switch (payload.animalTypeId()) {
            case "white_chicken" -> ICON_WHITE_CHICKEN;
            case "golden_chicken" -> ICON_GOLDEN_CHICKEN;
            case "duck" -> ICON_DUCK;
            case "void_chicken" -> ICON_VOID_CHICKEN;
            case "rabbit" -> ICON_RABBIT;
            case "ostrich" -> ICON_OSTRICH;
            case "dinosaur" -> ICON_DINOSAUR;
            case "cow" -> ICON_COW;
            case "goat" -> ICON_GOAT;
            case "sheep" -> ICON_SHEEP;
            case "pig" -> ICON_PIG;
            default -> ICON_COW;
        };
    }

    private void updateHoverState(int mouseX, int mouseY) {
        int hotspot = detectHotspot(mouseX, mouseY);
        if (hotspot != this.hoverHotspot) {
            this.hoverHotspot = hotspot;
            if (hotspot != 0) {
                playUi(ModSounds.SMALL_SELECT.get(), 0.44f, 1.16f);
            }
        }
    }

    private int detectHotspot(int mouseX, int mouseY) {
        int upCx = sx(FIG_PAGE_UP_CX);
        int upCy = sy(FIG_PAGE_UP_CY);
        int downCx = sx(FIG_PAGE_DOWN_CX);
        int downCy = sy(FIG_PAGE_DOWN_CY);
        int okCx = sx(FIG_OK_CX);
        int okCy = sy(FIG_OK_CY);
        int cancelCx = sx(FIG_CANCEL_CX);
        int cancelCy = sy(FIG_CANCEL_CY);

        int pageHit = si(48);
        int actionHit = si(56);
        if (inside(mouseX, mouseY, upCx - pageHit / 2, upCy - pageHit / 2, pageHit, pageHit)) {
            return 1;
        }
        if (inside(mouseX, mouseY, downCx - pageHit / 2, downCy - pageHit / 2, pageHit, pageHit)) {
            return 2;
        }
        if (inside(mouseX, mouseY, okCx - actionHit / 2, okCy - actionHit / 2, actionHit, actionHit)) {
            return 3;
        }
        if (inside(mouseX, mouseY, cancelCx - actionHit / 2, cancelCy - actionHit / 2, actionHit, actionHit)) {
            return 4;
        }
        int row = pickRowAt(mouseX, mouseY);
        return row >= 0 ? 100 + row : 0;
    }

    private int sx(int figX) {
        return baseX + si(figX);
    }

    private int sy(int figY) {
        return baseY + si(figY);
    }

    private int si(int value) {
        return Math.max(1, Math.round(value * UI_SCALE));
    }

    private String trim(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, Math.max(0, maxChars - 1)) + "...";
    }

    private void drawScaledIcon(GuiGraphics graphics, ResourceLocation icon, int centerX, int centerY, float scale, int tint) {
        int a = (tint >>> 24) & 0xFF;
        int r = (tint >>> 16) & 0xFF;
        int g = (tint >>> 8) & 0xFF;
        int b = tint & 0xFF;
        graphics.setColor(r / 255f, g / 255f, b / 255f, a / 255f);

        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.blit(icon, -8, -8, 0, 0, 16, 16, 16, 16);
        graphics.pose().popPose();

        graphics.setColor(1f, 1f, 1f, 1f);
    }

    private void drawScaledText(GuiGraphics graphics, String text, int x, int y, float scale, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.drawString(this.font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private void drawScaledText(GuiGraphics graphics, Component text, int x, int y, float scale, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.drawString(this.font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private int alphaColor(int color, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(((color >>> 24) & 0xFF) * alpha)));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private int tintAlpha(int color, float alphaMul) {
        int baseA = (color >>> 24) & 0xFF;
        int outA = Math.max(0, Math.min(255, Math.round(baseA * alphaMul)));
        return (outA << 24) | (color & 0x00FFFFFF);
    }

    private float easeOutCubic(float t) {
        float inv = 1.0f - t;
        return 1.0f - inv * inv * inv;
    }

    private void drawQueryGhostTransition(GuiGraphics graphics, float out) {
        float alpha = 1.0f - out;
        int dy = Math.round(out * si(48));

        int nameX = sx(355);
        int nameY = sy(0) + dy;
        int nameW = si(322);
        int underlineY = nameY + si(48) - 1;
        graphics.fill(nameX, underlineY, nameX + nameW, underlineY + 1, alphaColor(0xFF6B6470, alpha));

        int infoX = sx(355);
        int infoY = sy(84) + dy;
        graphics.fill(infoX, infoY, infoX + si(120), infoY + 1, alphaColor(COLOR_LINE, alpha));
        graphics.fill(infoX, infoY + si(24), infoX + si(150), infoY + si(25), alphaColor(COLOR_LINE, alpha));
        graphics.fill(infoX, infoY + si(48), infoX + si(136), infoY + si(49), alphaColor(COLOR_LINE, alpha));
        graphics.fill(infoX, infoY + si(72), infoX + si(128), infoY + si(73), alphaColor(COLOR_LINE, alpha));

        int actionY = sy(335) + dy;
        int a0 = sx(346);
        int spacing = si(70);
        drawScaledIcon(graphics, SELL_ICON, a0, actionY, 1.56f, tintAlpha(0xFFFFFFFF, alpha));
        drawScaledIcon(graphics, MOVE_ICON, a0 + spacing, actionY, 1.56f, tintAlpha(0xFFFFFFFF, alpha));
        drawScaledIcon(graphics, REPRO_OFF_ICON, a0 + spacing * 2, actionY, 1.56f, tintAlpha(0xFFFFFFFF, alpha));
        drawScaledIcon(graphics, OK_ICON, a0 + spacing * 3, actionY, 1.56f, tintAlpha(0xFFFFFFFF, alpha));
    }

    private float approach(float current, float target) {
        // smooth exponential interpolation instead of linear, eliminating stutter
        if (Math.abs(current - target) < 0.001f) {
            return target;
        }
        return current + (target - current) * 0.24f;
    }

    private void drawFilledCircle(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        if (radius <= 0) return;
        // Batch rows by 4-5 pixels for massive vertex reduction
        int step = Math.max(1, radius / 12);
        for (int dy = -radius; dy <= radius; dy += step) {
            double effectiveY = Math.min(Math.abs(dy) + step / 2.0, radius);
            int span = (int) Math.floor(Math.sqrt((double) radius * radius - effectiveY * effectiveY));
            int h = Math.min(step, radius - dy + 1);
            if (h > 0) {
                graphics.fill(centerX - span, centerY + dy, centerX + span + 1, centerY + dy + h, color);
            }
        }
    }

    private boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void playUi(SoundEvent event, float volume, float pitch) {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.playSound(event, volume, pitch);
        }
    }
}

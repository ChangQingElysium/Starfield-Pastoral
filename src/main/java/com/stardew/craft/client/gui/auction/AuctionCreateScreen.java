package com.stardew.craft.client.gui.auction;

import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.client.gui.overnight.StardewGuiUtil;
import com.stardew.craft.network.payload.AuctionCreateSubmitPayload;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nonnull;

@SuppressWarnings("null")
public class AuctionCreateScreen extends Screen {
    private static final int DAY_START = 8 * 60;
    private static final int DAY_END = 22 * 60;
    private static final int DAY_SPAN = DAY_END - DAY_START;

    // Fixed design canvas. The whole screen is laid out at this size, then scaled uniformly to fit the
    // real screen (like ShopScreen anchors to guiScale) so the layout is identical at every GUI scale
    // and can never reflow into overlap. 720x540 is wide/tall enough for the comfortable two-column form.
    private static final int DESIGN_W = 720;
    private static final int DESIGN_H = 540;

    private final int currentDay;
    private final int currentMinute;
    private final int occupiedDayMask;

    private int panelX, panelY, panelW, panelH;
    private int contentX, contentY, contentW, contentH;
    private int ribbonCx, bodyY, footerY, footerH;
    private int itemX, itemY, itemW, itemH;
    private int ledgerX, ledgerY, ledgerW, ledgerH;
    private int scheduleX, scheduleY, scheduleW, scheduleH;
    private int previewX, previewY, previewSize;
    private int pickBtnX, pickBtnY, pickBtnW, pickBtnH;
    private int submitX, submitY, submitW, submitH;
    private int nameBoxX, nameBoxY, nameBoxW, nameBoxH;
    private int promoBoxX, promoBoxY, promoBoxW, promoBoxH;
    private int priceBoxX, priceBoxY, priceBoxW, priceBoxH;
    private int calendarX, calendarY, calendarW, calendarH, chipW, chipH;
    private int timeX, timeY, timeW, timeH;
    private int trackX, trackY, trackW, trackH, knobW, knobH;
    private int tabY, tabH, tabW, itemTabX, formTabX, scheduleTabX;
    private int selectedSlot = -1;
    private int dayOffset = 1;
    private int startMinute = 9 * 60;
    private int compactPage;
    private boolean singleColumn;
    private boolean draggingTime;
    private boolean opened;
    private String draftName = "";
    private String draftPromo = "";
    private String draftPrice = "100";
    private EditBox nameField;
    private EditBox promoField;
    private EditBox priceField;
    private float fitScale = 1.0f;
    private int fitOriginX, fitOriginY;
    private long lastSliderTickMs;

    public AuctionCreateScreen(int currentDay, int currentMinute, int occupiedDayMask) {
        super(Component.translatable("stardewcraft.auction.create.title"));
        this.currentDay = currentDay;
        this.currentMinute = currentMinute;
        this.occupiedDayMask = occupiedDayMask;
        this.dayOffset = firstFreeDay();
    }

    private boolean isDayOccupied(int offset) {
        return offset >= 1 && offset <= 14 && (occupiedDayMask & (1 << (offset - 1))) != 0;
    }

    private int firstFreeDay() {
        for (int offset = 1; offset <= 14; offset++) {
            if (!isDayOccupied(offset)) {
                return offset;
            }
        }
        return 1;
    }

    @Override
    protected void init() {
        // Scale the fixed design canvas to fit the real screen, leaving a small margin, capped so it
        // stays a sensible centered box on very large screens. Everything below lays out in design space.
        fitScale = Math.min(1.5f, 0.94f * Math.min(width / (float) DESIGN_W, height / (float) DESIGN_H));
        fitOriginX = Math.round((width - DESIGN_W * fitScale) / 2f);
        fitOriginY = Math.round((height - DESIGN_H * fitScale) / 2f);

        panelW = DESIGN_W;
        panelH = DESIGN_H;
        panelX = 0;
        panelY = 0;

        int pad = Math.max(30, Math.min(50, panelW / 15));
        contentX = panelX + pad;
        contentY = panelY + pad;
        contentW = panelW - pad * 2;
        contentH = panelH - pad * 2;
        ribbonCx = contentX + contentW / 2;

        footerH = 40;
        bodyY = contentY + 42;
        footerY = contentY + contentH - footerH;
        int bodyH = footerY - bodyY - 8;

        // Heights the right column genuinely needs (measured: header 34 + 3*(label13+field24) + 2*gap8 + pad).
        // Two columns only when there's room for the ledger AND the schedule below it; otherwise fall back to
        // the roomy step-by-step pager so nothing is crammed.
        int ledgerNeed = 172;
        int scheduleNeed = 150;
        int gap = 14;
        singleColumn = contentW < 600 || bodyH < ledgerNeed + gap + scheduleNeed;

        if (singleColumn) {
            tabH = 24;
            tabY = bodyY;
            tabW = Math.max(80, (contentW - 12) / 3);
            itemTabX = contentX;
            formTabX = itemTabX + tabW + 6;
            scheduleTabX = formTabX + tabW + 6;
            int pageY = bodyY + tabH + 10;
            int pageH = Math.max(1, footerY - pageY - 8);
            itemX = ledgerX = scheduleX = contentX;
            itemY = ledgerY = scheduleY = pageY;
            itemW = ledgerW = scheduleW = contentW;
            itemH = ledgerH = scheduleH = pageH;
        } else {
            itemX = contentX;
            itemY = bodyY;
            itemW = Math.min(300, Math.max(240, (contentW - gap) * 42 / 100));
            itemH = bodyH;
            int rightX = itemX + itemW + gap;
            int rightW = contentW - itemW - gap;
            ledgerX = rightX;
            ledgerY = bodyY;
            ledgerW = rightW;
            ledgerH = ledgerNeed;
            scheduleX = rightX;
            scheduleY = bodyY + ledgerH + gap;
            scheduleW = rightW;
            scheduleH = bodyY + bodyH - scheduleY;
        }

        layoutItemArea();
        layoutFields();
        layoutSchedule();

        submitW = Math.min(210, Math.max(150, contentW / 3));
        submitH = 30;
        submitX = contentX + contentW - submitW;
        submitY = footerY + (footerH - submitH) / 2;

        if (!opened) {
            opened = true;
            playOpen();
        }
    }

    private void layoutItemArea() {
        previewSize = 56;
        int blockH = previewSize + 8 + 14 + 22 + 28; // preview + name + status + gap + button
        int top = itemY + 34 + Math.max(6, (itemH - 34 - blockH) / 2);
        previewX = itemX + (itemW - previewSize) / 2;
        previewY = top;
        pickBtnW = Math.min(180, Math.max(120, itemW - 44));
        pickBtnH = 28;
        pickBtnX = itemX + (itemW - pickBtnW) / 2;
        pickBtnY = previewY + previewSize + 8 + 14 + 22;
    }

    private void layoutFields() {
        // Each field block stacks as: label (13px above) + input box (fh) + 8px gap before the next label.
        // First label sits at ledgerY+34 so it clears the band's "账簿" header at ledgerY+9.
        int fx = ledgerX + 14;
        int fw = Math.max(140, ledgerW - 28);
        int fh = Math.max(24, font.lineHeight + 14);
        int step = fh + 21;
        int fy = ledgerY + 47;
        nameBoxX = fx; nameBoxY = fy; nameBoxW = fw; nameBoxH = fh;
        promoBoxX = fx; promoBoxY = nameBoxY + step; promoBoxW = fw; promoBoxH = fh;
        priceBoxX = fx; priceBoxY = promoBoxY + step; priceBoxW = fw; priceBoxH = fh;

        nameField = edit(nameBoxX + 9, nameBoxY + (fh - font.lineHeight) / 2, fw - 18, font.lineHeight,
            "stardewcraft.auction.create.name_hint", 64);
        promoField = edit(promoBoxX + 9, promoBoxY + (fh - font.lineHeight) / 2, fw - 18, font.lineHeight,
            "stardewcraft.auction.create.promo_hint", 96);
        priceField = edit(priceBoxX + 31, priceBoxY + (fh - font.lineHeight) / 2, fw - 43, font.lineHeight,
            "stardewcraft.auction.create.price_hint", 9);
        nameField.setValue(draftName);
        promoField.setValue(draftPromo);
        priceField.setValue(draftPrice);
    }

    private void layoutSchedule() {
        calendarX = scheduleX + 14;
        calendarY = scheduleY + 28;
        calendarW = scheduleW - 28;
        chipW = Math.max(24, (calendarW - 6) / 7);
        chipH = 18;
        calendarH = 2 * (chipH + 4);

        timeX = scheduleX + 14;
        timeW = scheduleW - 28;
        timeY = calendarY + calendarH + 16;
        timeH = Math.max(54, scheduleY + scheduleH - timeY - 12);

        trackX = timeX + 6;
        trackW = timeW - 12;
        trackY = timeY + 28;
        trackH = 6;
        knobW = 10;
        knobH = 18;
    }

    private EditBox edit(int x, int y, int w, int h, String hintKey, int maxLength) {
        EditBox box = new EditBox(com.stardew.craft.client.font.StardewFonts.small(), x, y, w, h, Component.translatable(hintKey));
        box.setBordered(false);
        box.setTextShadow(false);
        box.setTextColor(0xFF3F2411);
        box.setTextColorUneditable(0xFF3F2411);
        box.setHint(Component.translatable(hintKey));
        box.setMaxLength(maxLength);
        addWidget(box);
        return box;
    }

    @Override
    public void render(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(g); // full-screen dim stays in real coords
        int mx = lmx(mouseX);
        int my = lmy(mouseY);
        g.pose().pushPose();
        g.pose().translate(fitOriginX, fitOriginY, 0);
        g.pose().scale(fitScale, fitScale, 1f);
        StardewGuiUtil.drawDialogueBoxFrame(g, panelX, panelY, panelW, panelH);
        AuctionUi.ribbon(g, font, Component.translatable("stardewcraft.auction.create.title"), ribbonCx, contentY);
        AuctionUi.drawCentered(g, font, Component.translatable("stardewcraft.auction.create.subtitle"),
            ribbonCx, contentY + 22, contentW - 12, AuctionUi.MUTED);

        if (singleColumn) {
            drawStepTabs(g, mx, my);
            switch (compactPage) {
                case 0 -> drawItemBand(g, mx, my);
                case 1 -> drawLedgerBand(g, mx, my);
                default -> drawScheduleBand(g, mx, my);
            }
        } else {
            drawItemBand(g, mx, my);
            drawLedgerBand(g, mx, my);
            drawScheduleBand(g, mx, my);
        }
        drawFooter(g, mx, my);
        renderFields(g, mx, my, partialTick);
        g.pose().popPose();
    }

    // Real screen coords -> design-space coords (and back-scaled), so input matches the scaled render.
    private int lmx(double mouseX) { return (int) Math.round((mouseX - fitOriginX) / fitScale); }
    private int lmy(double mouseY) { return (int) Math.round((mouseY - fitOriginY) / fitScale); }
    private double ldx(double mouseX) { return (mouseX - fitOriginX) / fitScale; }
    private double ldy(double mouseY) { return (mouseY - fitOriginY) / fitScale; }

    private void drawStepTabs(GuiGraphics g, int mouseX, int mouseY) {
        drawStepTab(g, mouseX, mouseY, itemTabX, 0, Component.translatable("stardewcraft.auction.create.step.item"));
        drawStepTab(g, mouseX, mouseY, formTabX, 1, Component.translatable("stardewcraft.auction.create.step.ledger"));
        drawStepTab(g, mouseX, mouseY, scheduleTabX, 2, Component.translatable("stardewcraft.auction.create.step.schedule"));
    }

    private void drawStepTab(GuiGraphics g, int mouseX, int mouseY, int x, int page, Component label) {
        boolean selected = compactPage == page;
        boolean hover = AuctionUi.inside(mouseX, mouseY, x, tabY, tabW, tabH);
        AuctionUi.plainButton(g, font, label, x, tabY, tabW, tabH, true, selected || hover);
        if (selected) {
            g.fill(x + 9, tabY + tabH - 7, x + tabW - 9, tabY + tabH - 5, AuctionUi.GOLD_BRIGHT);
        }
    }

    private void drawItemBand(GuiGraphics g, int mouseX, int mouseY) {
        AuctionUi.band(g, itemX, itemY, itemW, itemH);
        AuctionUi.sectionLabel(g, font, Component.translatable("stardewcraft.auction.create.item"), itemX + 14, itemY + 12, itemW - 28);
        ItemStack sel = selectedStack();
        AuctionUi.slot(g, font, sel, previewX, previewY, previewSize, true, false);
        int cx = itemX + itemW / 2;
        if (sel.isEmpty()) {
            AuctionUi.drawCentered(g, font, Component.translatable("stardewcraft.auction.create.item_empty"),
                cx, previewY + previewSize + 8, itemW - 28, AuctionUi.MUTED);
            AuctionUi.drawCentered(g, font, Component.translatable("stardewcraft.auction.create.item_empty_hint"),
                cx, previewY + previewSize + 22, itemW - 28, AuctionUi.MUTED);
        } else {
            AuctionUi.drawCentered(g, font, sel.getHoverName(), cx, previewY + previewSize + 8, itemW - 28, AuctionUi.INK);
            AuctionUi.drawCentered(g, font, Component.translatable("stardewcraft.auction.create.item_ready"),
                cx, previewY + previewSize + 22, itemW - 28, AuctionUi.GOLD);
        }
        boolean hover = AuctionUi.inside(mouseX, mouseY, pickBtnX, pickBtnY, pickBtnW, pickBtnH);
        AuctionUi.plainButton(g, font, Component.translatable("stardewcraft.auction.create.pick_item"),
            pickBtnX, pickBtnY, pickBtnW, pickBtnH, true, hover);
    }

    private void drawLedgerBand(GuiGraphics g, int mouseX, int mouseY) {
        AuctionUi.band(g, ledgerX, ledgerY, ledgerW, ledgerH);
        AuctionUi.sectionLabel(g, font, Component.translatable("stardewcraft.auction.create.step.ledger"), ledgerX + 14, ledgerY + 9, ledgerW - 28);
        label(g, nameBoxX, nameBoxY - 13, "stardewcraft.auction.create.name");
        AuctionUi.inputBox(g, nameBoxX, nameBoxY, nameBoxW, nameBoxH, nameField.isFocused(),
            AuctionUi.inside(mouseX, mouseY, nameBoxX, nameBoxY, nameBoxW, nameBoxH), false);
        label(g, promoBoxX, promoBoxY - 13, "stardewcraft.auction.create.promo");
        AuctionUi.inputBox(g, promoBoxX, promoBoxY, promoBoxW, promoBoxH, promoField.isFocused(),
            AuctionUi.inside(mouseX, mouseY, promoBoxX, promoBoxY, promoBoxW, promoBoxH), false);
        label(g, priceBoxX, priceBoxY - 13, "stardewcraft.auction.create.price");
        AuctionUi.inputBox(g, priceBoxX, priceBoxY, priceBoxW, priceBoxH, priceField.isFocused(),
            AuctionUi.inside(mouseX, mouseY, priceBoxX, priceBoxY, priceBoxW, priceBoxH), parsePrice() <= 0);
        CommonGuiTextures.drawGoldCoin16(g, priceBoxX + 10, priceBoxY + (priceBoxH - 14) / 2, 0.58f);
        g.fill(priceBoxX + 28, priceBoxY + 7, priceBoxX + 29, priceBoxY + priceBoxH - 7, 0x55A66B26);
    }

    private void drawScheduleBand(GuiGraphics g, int mouseX, int mouseY) {
        AuctionUi.band(g, scheduleX, scheduleY, scheduleW, scheduleH);
        drawCalendar(g, mouseX, mouseY);
        AuctionUi.divider(g, scheduleX + 12, timeY - 8, scheduleW - 24);
        drawTimeSlider(g, mouseX, mouseY);
    }

    private void drawCalendar(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, Component.translatable("stardewcraft.auction.create.day"), calendarX, scheduleY + 10, AuctionUi.BODY, false);
        String selectedDay = formatDate(currentDay + dayOffset).getString();
        g.drawString(font, selectedDay, calendarX + calendarW - font.width(selectedDay), scheduleY + 10, AuctionUi.GOLD, false);
        for (int i = 1; i <= 14; i++) {
            int col = (i - 1) % 7;
            int row = (i - 1) / 7;
            int x = calendarX + col * chipW;
            int y = calendarY + row * (chipH + 4);
            boolean occupied = isDayOccupied(i);
            boolean selected = !occupied && dayOffset == i;
            boolean hover = !occupied && AuctionUi.inside(mouseX, mouseY, x, y, chipW - 3, chipH);
            CommonGuiTextures.drawCalendarTodayBox(g, x, y, chipW - 3, chipH, 1.0f);
            int fill = occupied ? 0xFFE7C58A : selected ? AuctionUi.SELECTED_BAND : hover ? AuctionUi.CARD_STRONG : 0xFFFFDFA1;
            g.fill(x + 2, y + 2, x + chipW - 5, y + chipH - 2, fill);
            String text = String.valueOf(dayOfSeason(currentDay + i));
            g.drawString(font, text, x + (chipW - 3 - font.width(text)) / 2, y + (chipH - font.lineHeight) / 2 + 1,
                occupied ? AuctionUi.DISABLED : selected ? AuctionUi.INK : AuctionUi.BODY, false);
            if (occupied) {
                g.fill(x + 4, y + chipH / 2, x + chipW - 7, y + chipH / 2 + 1, AuctionUi.DISABLED);
            }
        }
    }

    private void drawTimeSlider(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, Component.translatable("stardewcraft.auction.create.time"), timeX, timeY, AuctionUi.BODY, false);
        String readout = formatTime(startMinute);
        g.drawString(font, readout, timeX + timeW - font.width(readout), timeY, AuctionUi.GOLD_BRIGHT, false);

        AuctionUi.inset(g, trackX - 4, trackY - 4, trackW + 8, trackH + 8);
        g.fill(trackX, trackY, trackX + trackW, trackY + trackH, 0x66A66B26);
        float frac = (startMinute - DAY_START) / (float) DAY_SPAN;
        int knobCx = trackX + Math.round(frac * trackW);
        g.fill(trackX, trackY, knobCx, trackY + trackH, AuctionUi.GOLD);

        for (int hour = 8; hour <= 22; hour += 2) {
            int x = trackX + Math.round((hour * 60 - DAY_START) / (float) DAY_SPAN * trackW);
            g.fill(x, trackY + trackH + 3, x + 1, trackY + trackH + 6, AuctionUi.LINE_SOFT);
        }
        g.drawString(font, "08:00", trackX, trackY + trackH + 9, AuctionUi.MUTED, false);
        g.drawString(font, "22:00", trackX + trackW - font.width("22:00"), trackY + trackH + 9, AuctionUi.MUTED, false);

        boolean active = draggingTime || AuctionUi.inside(mouseX, mouseY, knobCx - knobW, trackY - 8, knobW * 2, knobH + 8);
        int kx = knobCx - knobW / 2;
        int ky = trackY + trackH / 2 - knobH / 2;
        AuctionUi.plainButton(g, font, Component.empty(), kx, ky, knobW, knobH, true, active);
        g.fill(kx + knobW / 2 - 1, ky + 4, kx + knobW / 2 + 1, ky + knobH - 4, active ? AuctionUi.GOLD_BRIGHT : AuctionUi.GOLD);
    }

    private void drawFooter(GuiGraphics g, int mouseX, int mouseY) {
        AuctionUi.divider(g, contentX + 8, footerY - 4, contentW - 16);
        boolean enabled = footerActionEnabled();
        boolean hover = enabled && AuctionUi.inside(mouseX, mouseY, submitX, submitY, submitW, submitH);
        AuctionUi.drawClamped(g, font, Component.translatable(submitHintKey()), contentX + 4, submitY + (submitH - font.lineHeight) / 2,
            Math.max(40, submitX - contentX - 14), enabled ? AuctionUi.MUTED : AuctionUi.ERROR);
        AuctionUi.actionButton(g, font,
            Component.translatable(singleColumn && compactPage < 2 ? "stardewcraft.auction.ui.next" : "stardewcraft.auction.create.submit"),
            submitX, submitY, submitW, submitH, enabled, hover);
    }

    private void label(GuiGraphics g, int x, int y, String key) {
        g.drawString(font, Component.translatable(key), x, y, AuctionUi.BODY, false);
    }

    private String submitHintKey() {
        if (singleColumn && compactPage == 0 && selectedSlot >= 0) {
            return "stardewcraft.auction.create.next_ledger";
        }
        if (selectedSlot < 0) {
            return "stardewcraft.auction.create.need_item";
        }
        if (singleColumn && compactPage == 1 && parsePrice() > 0) {
            return "stardewcraft.auction.create.next_schedule";
        }
        if (parsePrice() <= 0) {
            return "stardewcraft.auction.create.need_price";
        }
        if ((!singleColumn || compactPage == 2) && isDayOccupied(dayOffset)) {
            return "stardewcraft.auction.error.day_taken";
        }
        return "stardewcraft.auction.create.ready_hint";
    }

    private boolean footerActionEnabled() {
        if (!singleColumn) {
            return selectedSlot >= 0 && parsePrice() > 0 && !isDayOccupied(dayOffset);
        }
        return switch (compactPage) {
            case 0 -> selectedSlot >= 0;
            case 1 -> parsePrice() > 0;
            default -> selectedSlot >= 0 && parsePrice() > 0 && !isDayOccupied(dayOffset);
        };
    }

    @Override
    public boolean mouseClicked(double rawX, double rawY, int button) {
        double mouseX = ldx(rawX);
        double mouseY = ldy(rawY);
        if (singleColumn && clickStepTab(mouseX, mouseY)) {
            return true;
        }
        if (!singleColumn || compactPage == 0) {
            if (AuctionUi.inside(mouseX, mouseY, pickBtnX, pickBtnY, pickBtnW, pickBtnH)) {
                openPicker();
                return true;
            }
        }
        if (!singleColumn || compactPage == 1) {
            if (clickField(mouseX, mouseY, nameField, nameBoxX, nameBoxY, nameBoxW, nameBoxH, button)) return true;
            if (clickField(mouseX, mouseY, promoField, promoBoxX, promoBoxY, promoBoxW, promoBoxH, button)) return true;
            if (clickField(mouseX, mouseY, priceField, priceBoxX, priceBoxY, priceBoxW, priceBoxH, button)) return true;
        }
        if (!singleColumn || compactPage == 2) {
            if (clickCalendar(mouseX, mouseY)) return true;
            if (clickTimeTrack(mouseX, mouseY)) return true;
        }
        if (AuctionUi.inside(mouseX, mouseY, submitX, submitY, submitW, submitH)) {
            footerAction();
            return true;
        }
        clearTextFocus();
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double rawX, double rawY, int button, double dragX, double dragY) {
        if (draggingTime) {
            setTimeFromMouse(ldx(rawX));
            return true;
        }
        return super.mouseDragged(ldx(rawX), ldy(rawY), button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double rawX, double rawY, int button) {
        draggingTime = false;
        return super.mouseReleased(ldx(rawX), ldy(rawY), button);
    }

    private boolean clickStepTab(double mouseX, double mouseY) {
        if (AuctionUi.inside(mouseX, mouseY, itemTabX, tabY, tabW, tabH)) { changeCompactPage(0); return true; }
        if (AuctionUi.inside(mouseX, mouseY, formTabX, tabY, tabW, tabH)) { changeCompactPage(1); return true; }
        if (AuctionUi.inside(mouseX, mouseY, scheduleTabX, tabY, tabW, tabH)) { changeCompactPage(2); return true; }
        return false;
    }

    private void changeCompactPage(int page) {
        compactPage = Math.max(0, Math.min(2, page));
        clearTextFocus();
        playSelect();
    }

    private boolean clickField(double mouseX, double mouseY, EditBox field, int x, int y, int w, int h, int button) {
        if (field == null || !AuctionUi.inside(mouseX, mouseY, x, y, w, h)) {
            return false;
        }
        clearTextFocus();
        field.setFocused(true);
        setFocused(field);
        return field.mouseClicked(mouseX, mouseY, button) || true;
    }

    private void clearTextFocus() {
        if (nameField != null) nameField.setFocused(false);
        if (promoField != null) promoField.setFocused(false);
        if (priceField != null) priceField.setFocused(false);
    }

    private void renderFields(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (singleColumn && compactPage != 1) {
            return;
        }
        if (nameField != null) nameField.render(g, mouseX, mouseY, partialTick);
        if (promoField != null) promoField.render(g, mouseX, mouseY, partialTick);
        if (priceField != null) priceField.render(g, mouseX, mouseY, partialTick);
    }

    private boolean clickCalendar(double mouseX, double mouseY) {
        for (int i = 1; i <= 14; i++) {
            int col = (i - 1) % 7;
            int row = (i - 1) / 7;
            int x = calendarX + col * chipW;
            int y = calendarY + row * (chipH + 4);
            if (AuctionUi.inside(mouseX, mouseY, x, y, chipW - 3, chipH)) {
                if (isDayOccupied(i)) {
                    playCancel();
                } else {
                    dayOffset = i;
                    playSelect();
                }
                return true;
            }
        }
        return false;
    }

    private boolean clickTimeTrack(double mouseX, double mouseY) {
        if (AuctionUi.inside(mouseX, mouseY, trackX - 8, trackY - 9, trackW + 16, knobH + 6)) {
            draggingTime = true;
            setTimeFromMouse(mouseX);
            return true;
        }
        return false;
    }

    private void setTimeFromMouse(double mouseX) {
        float frac = (float) ((mouseX - trackX) / Math.max(1, trackW));
        frac = Math.max(0.0f, Math.min(1.0f, frac));
        int minute = DAY_START + Math.round(frac * (DAY_SPAN / 10.0f)) * 10;
        minute = Math.max(DAY_START, Math.min(DAY_END, minute));
        if (minute != startMinute) {
            startMinute = minute;
            playSliderTick();
        }
    }

    // A fast drag crosses many 10-minute steps per frame; without throttling that machine-guns the click
    // sound. Rate-limit to one gentle tick per ~55ms so dragging stays crisp but never harsh.
    private void playSliderTick() {
        long now = System.currentTimeMillis();
        if (now - lastSliderTickMs < 55) {
            return;
        }
        lastSliderTickMs = now;
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.SMALL_SELECT.get(), 0.5f, 1.0f));
        }
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (priceField != null && priceField.isFocused() && !Character.isDigit(codePoint)) {
            return false;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            footerAction();
            return true;
        }
        if (isTextFocused()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (singleColumn && compactPage != 2) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == 263 && dayOffset > 1) {
            int d = dayOffset - 1;
            while (d >= 1 && isDayOccupied(d)) d--;
            if (d >= 1) { dayOffset = d; playSelect(); }
            return true;
        }
        if (keyCode == 262 && dayOffset < 14) {
            int d = dayOffset + 1;
            while (d <= 14 && isDayOccupied(d)) d++;
            if (d <= 14) { dayOffset = d; playSelect(); }
            return true;
        }
        if (keyCode == 264 && startMinute > DAY_START) {
            startMinute = Math.max(DAY_START, startMinute - 10);
            playSelect();
            return true;
        }
        if (keyCode == 265 && startMinute < DAY_END) {
            startMinute = Math.min(DAY_END, startMinute + 10);
            playSelect();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void openPicker() {
        playSelect();
        draftName = nameField == null ? draftName : nameField.getValue();
        draftPromo = promoField == null ? draftPromo : promoField.getValue();
        draftPrice = priceField == null ? draftPrice : priceField.getValue();
        if (minecraft != null) {
            minecraft.setScreen(new AuctionItemPickerScreen(this, slot -> this.selectedSlot = slot));
        }
    }

    private void submit() {
        int price = parsePrice();
        if (selectedSlot < 0 || price <= 0 || isDayOccupied(dayOffset)) {
            playCancel();
            return;
        }
        PacketDistributor.sendToServer(new AuctionCreateSubmitPayload(selectedSlot, dayOffset, startMinute, price,
            nameField == null ? "" : nameField.getValue(), promoField == null ? "" : promoField.getValue()));
        playSelect();
        onClose();
    }

    private void footerAction() {
        if (!footerActionEnabled()) {
            playCancel();
            return;
        }
        if (singleColumn && compactPage < 2) {
            changeCompactPage(compactPage + 1);
            return;
        }
        submit();
    }

    private ItemStack selectedStack() {
        return minecraft != null && minecraft.player != null && selectedSlot >= 0
            ? minecraft.player.getInventory().getItem(selectedSlot)
            : ItemStack.EMPTY;
    }

    private int parsePrice() {
        try {
            return Math.max(0, Integer.parseInt(priceField.getValue().trim()));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private boolean isTextFocused() {
        return (nameField != null && nameField.isFocused())
            || (promoField != null && promoField.isFocused())
            || (priceField != null && priceField.isFocused());
    }

    private static String formatTime(int minute) {
        return String.format(java.util.Locale.ROOT, "%02d:%02d", minute / 60, minute % 60);
    }

    private static Component formatDate(int absoluteDay) {
        return Component.translatable("stardewcraft.auction.create.selected_day",
            seasonName(absoluteDay), dayOfSeason(absoluteDay));
    }

    private static int dayOfSeason(int absoluteDay) {
        return Math.max(0, absoluteDay - 1) % 28 + 1;
    }

    private static Component seasonName(int absoluteDay) {
        int season = (Math.max(0, absoluteDay - 1) / 28) % 4;
        return Component.translatable("stardewcraft.season." + switch (season) {
            case 1 -> "summer";
            case 2 -> "fall";
            case 3 -> "winter";
            default -> "spring";
        });
    }

    private void playSelect() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.SMALL_SELECT.get(), 0.7f, 1.05f));
        }
    }

    private void playOpen() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.BOOK_READ.get(), 0.88f, 0.72f));
        }
    }

    private void playCancel() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.CANCEL.get(), 0.92f, 0.45f));
        }
    }
}

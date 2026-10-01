package com.stardew.craft.client.gui.auction;

import com.stardew.craft.network.payload.AuctionCreateSubmitPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

@SuppressWarnings("null")
public class AuctionCreateScreen extends AuctionScreen {
    private static final String[] WEEK = {"monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"};
    private final int currentDay, occupiedDayMask;
    private int selectedSlot = -1, dayOffset = 1, startMinute = 540, page, monthStart;
    private String draftName = "", draftPromo = "", draftPrice = "100";
    private int nameX, nameY, nameW, promoY, priceX, priceY, priceW, noteY, pickY;
    private int calendarW, calendarY, cellH, timeX, timeW, hourY, minuteY, hourCols, minuteCols, timeCellH;
    private boolean wide;
    private Button next;
    public AuctionCreateScreen(int currentDay, int currentMinute, int occupiedDayMask) {
        super("stardewcraft.auction.create.title");
        this.currentDay = currentDay; this.occupiedDayMask = occupiedDayMask;
        for (int d = 1; d <= 14; d++) if (!occupied(d)) { dayOffset = d; break; }
        monthStart = AuctionLayout.seasonStart(currentDay + dayOffset);
    }
    @Override protected int preferredWidth() { return 416; }
    @Override protected int preferredHeight() { return 342; }
    private boolean occupied(int offset) { return offset >= 1 && offset <= 14 && (occupiedDayMask & (1 << (offset - 1))) != 0; }
    @Override protected Component heading() {
        if (page == 2) return tr("create.time").copy().append(" · ").append(tr("create.selected_day",
                AuctionJoinListScreen.season(currentDay + dayOffset), Math.max(0, currentDay + dayOffset - 1) % 28 + 1));
        return tr(page == 0 ? "create.title" : wide ? "create.step.schedule" : "create.day");
    }
    @Override protected void layout() {
        wide = contentW >= 350;
        if (wide && page == 2) page = 1;
        if (page == 0) layoutDetails();
        else layoutBooking();
        footer(tr("picker.cancel"), false, false, () -> {
            if (page == 0) onClose(); else { page--; resetScroll(); rebuild(); }
        });
        next = footer(tr(page == 0 || page == 1 && !wide ? "ui.next" : "create.submit"), true, true, this::advance);
    }
    private void pick() { minecraft.setScreen(new AuctionItemPickerScreen(this, slot -> selectedSlot = slot)); }
    private void layoutDetails() {
        if (wide) {
            nameX = 152; nameW = contentW - nameX;
            nameY = 0;
            field(nameX, nameY + line + 5, nameW, tr("create.name"), draftName, 64, false, v -> draftName = v);
            priceX = nameX; priceW = nameW;
            priceY = line + controlH + 20;
            int priceLabelH = wrappedHeight(tr("create.price"), priceW);
            field(priceX, priceY + priceLabelH + 4, priceW, tr("create.price"), draftPrice, 9, true, v -> draftPrice = v);
            pickY = 82 + line;
            var pick = button(0, pickY, 130, tr("create.pick_item"), this::pick);
            promoY = Math.max(priceY + priceLabelH + controlH + 18, pickY + pick.getHeight() + 16);
        } else {
            pickY = line + 12;
            var pick = button(92, pickY, contentW - 92, tr("create.pick_item"), this::pick);
            nameY = Math.max(60, pickY + pick.getHeight() + 12);
            nameX = 0; nameW = contentW - 108; priceX = nameW + 14; priceW = contentW - priceX; priceY = nameY;
            int labels = Math.max(wrappedHeight(tr("create.name"), nameW), wrappedHeight(tr("create.price"), priceW));
            field(0, nameY + labels + 4, nameW, tr("create.name"), draftName, 64, false, v -> draftName = v);
            field(priceX, priceY + labels + 4, priceW, tr("create.price"), draftPrice, 9, true, v -> draftPrice = v);
            promoY = nameY + labels + controlH + 18;
        }
        field(0, promoY + line + 5, contentW, tr("create.promo"), draftPromo, 96, false, v -> draftPromo = v);
        noteY = promoY + line + controlH + 17;
        contentHeight = noteY + Math.max(wrappedHeight(tr("create.ready_hint"), contentW),
                wrappedHeight(tr("create.need_price"), contentW)) + 6;
    }
    private void layoutBooking() {
        var geometry = AuctionLayout.booking(contentW, line, Math.max(26, font.width("50") + 12), page == 2);
        calendarW = geometry.calendarWidth(); calendarY = geometry.calendarY(); cellH = geometry.cellHeight();
        timeX = geometry.timeX(); timeW = geometry.timeWidth(); timeCellH = geometry.timeCellHeight(); hourCols = geometry.hourColumns();
        minuteCols = geometry.minuteColumns(); hourY = geometry.hourY(); minuteY = geometry.minuteY(); noteY = geometry.noteY();
        if (page == 1) layoutCalendar();
        if (wide || page == 2) {
            for (int h = 8; h <= 22; h++) {
                int hour = h, index = h - 8, cw = timeW / hourCols;
                timeChoice(timeX + index % hourCols * cw, hourY + index / hourCols * (timeCellH + 3), cw - 3,
                        String.format(java.util.Locale.ROOT, "%02d", h), tr("schedule.hour"),
                        () -> startMinute = hour * 60 + (hour == 22 ? 0 : startMinute % 60), true, hour);
            }
            for (int m = 0; m <= 50; m += 10) {
                int minute = m, index = m / 10, cw = timeW / minuteCols;
                timeChoice(timeX + index % minuteCols * cw, minuteY + index / minuteCols * (timeCellH + 3), cw - 3,
                        String.format(java.util.Locale.ROOT, "%02d", m), tr("schedule.minute"),
                        () -> { if (startMinute / 60 < 22 || minute == 0) startMinute = startMinute / 60 * 60 + minute; }, false, minute);
            }
        }
        contentHeight = noteY + wrappedHeight(tr("create.subtitle"), contentW) + 8;
    }
    private void layoutCalendar() {
        arrow(0, -1, "schedule.previous_season");
        arrow(calendarW - 24, 1, "schedule.next_season");
        int cw = calendarW / 7;
        for (int day = 1; day <= 28; day++) {
            int date = monthStart + day - 1, dayNumber = day, offset = date - currentDay;
            Component label = tr("create.selected_day", AuctionJoinListScreen.season(date), day);
            boolean booked = occupied(offset);
            Button b = actionArea((day - 1) % 7 * cw, calendarY + (day - 1) / 7 * cellH, cw - 1, cellH - 2,
                    label, () -> dayOffset = offset, (g, cell) -> {
                boolean selected = date == currentDay + dayOffset && cell.active;
                int x = cell.getX(), y = cell.getY();
                if (selected) AuctionUi.sprite(g, "date_selected", x + (cell.getWidth() - 24) / 2, y + (cell.getHeight() - 24) / 2, 24, 24);
                else if (cell.isHoveredOrFocused() && cell.active) g.fill(x + 3, y + 2, x + cell.getWidth() - 3, y + cell.getHeight() - 2, 0x55DDC58F);
                String text = String.valueOf(dayNumber);
                g.drawString(font, text, x + (cell.getWidth() - font.width(text)) / 2,
                        y + (cell.getHeight() - line) / 2, selected ? AuctionUi.CREAM : cell.active ? AuctionUi.INK : 0xFF99856C, false);
                if (booked) g.fill(x + 5, y + cell.getHeight() / 2, x + cell.getWidth() - 5, y + cell.getHeight() / 2 + 1, AuctionUi.ERROR);
                if (date == currentDay) g.fill(x + 7, y + cell.getHeight() - 2, x + cell.getWidth() - 7, y + cell.getHeight() - 1, AuctionUi.GOLD);
                if (cell.isHovered()) tooltip(booked ? label.copy().append("\n").append(tr("error.day_taken")) : label);
            });
            b.active = AuctionLayout.bookable(date, currentDay, occupiedDayMask);
        }
    }
    private void arrow(int x, int direction, String label) {
        Button b = actionArea(x, 0, 24, wide ? controlH : line + 4, tr(label), () -> { monthStart += direction * 28; rebuild(); }, (g, a) -> {
            int cx = a.getX() + 12, cy = a.getY() + a.getHeight() / 2;
            for (int i = 0; i < 5; i++) {
                int xx = cx + direction * (2 - i);
                g.fill(xx, cy - i, xx + 2, cy - i + 1, a.active ? AuctionUi.GOLD : 0xFFB6A085);
                g.fill(xx, cy + i, xx + 2, cy + i + 1, a.active ? AuctionUi.GOLD : 0xFFB6A085);
            }
            if (a.isHovered()) tooltip(a.getMessage());
        });
        int targetMonth = monthStart + direction * 28;
        b.active = targetMonth >= AuctionLayout.seasonStart(currentDay + 1)
                && targetMonth <= AuctionLayout.seasonStart(currentDay + 14);
    }
    private void timeChoice(int x, int y, int w, String label, Component kind, Runnable action, boolean hour, int value) {
        actionArea(x, y, w, timeCellH, kind.copy().append(": " + label), action, (g, b) -> {
            b.active = hour || startMinute / 60 < 22 || value == 0;
            boolean chosen = hour ? startMinute / 60 == value : startMinute % 60 == value;
            if (chosen) AuctionUi.box(g, "button", b.getX(), b.getY(), w, timeCellH);
            else if (b.isHoveredOrFocused() && b.active) g.fill(b.getX() + 1, b.getY() + 1, b.getX() + w - 1, b.getY() + timeCellH - 1, 0x55DDC58F);
            g.drawString(font, label, b.getX() + (w - font.width(label)) / 2, b.getY() + (timeCellH - line) / 2,
                    chosen ? AuctionUi.CREAM : b.active ? AuctionUi.INK : 0xFF99856C, false);
            if (!chosen) g.fill(b.getX() + 5, b.getY() + timeCellH - 3, b.getX() + w - 5, b.getY() + timeCellH - 2, 0xFFC6AD88);
        });
    }
    @Override protected void drawBody(GuiGraphics g, float partialTick) {
        if (page == 0) {
            if (wide) {
                AuctionUi.sprite(g, "pedestal", 24, 5, 80, 52); item(g, inventory(selectedSlot), 48, 6, 2);
                text(g, inventory(selectedSlot).isEmpty() ? tr("create.item_empty") : inventory(selectedSlot).getHoverName(), 0, 68, 130, AuctionUi.INK);
            } else {
                AuctionUi.sprite(g, "pedestal", 0, 0, 80, 52); item(g, inventory(selectedSlot), 24, 1, 2);
                text(g, inventory(selectedSlot).isEmpty() ? tr("create.item_empty") : inventory(selectedSlot).getHoverName(), 92, 2, contentW - 92, AuctionUi.INK);
            }
            paragraph(g, tr("create.name"), nameX, nameY, nameW, AuctionUi.INK);
            paragraph(g, tr("create.price"), priceX, priceY, priceW, AuctionUi.INK);
            text(g, tr("create.promo"), 0, promoY, contentW, AuctionUi.INK);
            paragraph(g, tr(amount(draftPrice) > 0 ? "create.ready_hint" : "create.need_price"), 0, noteY, contentW,
                    amount(draftPrice) > 0 ? AuctionUi.MUTED : AuctionUi.ERROR);
            return;
        }
        if (page == 1) {
            Component season = AuctionJoinListScreen.season(monthStart);
            String heading = font.plainSubstrByWidth(season.getString(), calendarW - 56);
            g.drawString(font, heading, (calendarW - font.width(heading)) / 2, ((wide ? controlH : line + 4) - line) / 2, AuctionUi.INK, false);
            int cw = calendarW / 7;
            for (int i = 0; i < 7; i++) {
                Component weekday = Component.translatable("stardewcraft.hud." + WEEK[i]);
                String s = font.plainSubstrByWidth(weekday.getString(), cw - 2);
                g.drawString(font, s, i * cw + (cw - font.width(s)) / 2, (wide ? controlH + 7 : line + 6), AuctionUi.MUTED, false);
            }
            text(g, tr("create.selected_day", AuctionJoinListScreen.season(currentDay + dayOffset),
                    Math.max(0, currentDay + dayOffset - 1) % 28 + 1), 4, calendarY + 4 * cellH + 8, calendarW - 8, AuctionUi.GOLD);
        }
        if (wide || page == 2) {
            String time = AuctionJoinListScreen.time(startMinute);
            int scale = wide && font.width(time) * 2 <= timeW ? 2 : 1;
            number(g, time, timeX + (wide ? (timeW - font.width(time) * scale) / 2 : timeW - font.width(time)), wide ? 2 : 0, scale, AuctionUi.INK);
            text(g, tr("schedule.hour"), timeX, wide ? hourY - line - 7 : 0, timeW, AuctionUi.MUTED);
            text(g, tr("schedule.minute"), timeX, minuteY - line - 7, timeW, AuctionUi.MUTED);
        }
        paragraph(g, tr("create.subtitle"), 0, noteY, contentW, AuctionUi.MUTED);
    }
    @Override protected void updateState() {
        next.active = !inventory(selectedSlot).isEmpty() && amount(draftPrice) > 0 && (page == 0 || !occupied(dayOffset));
    }
    private void advance() {
        if (inventory(selectedSlot).isEmpty() || amount(draftPrice) <= 0) { cancelSound(); return; }
        if (page == 0 || page == 1 && !wide) { page++; resetScroll(); rebuild(); return; }
        if (occupied(dayOffset)) { cancelSound(); return; }
        PacketDistributor.sendToServer(new AuctionCreateSubmitPayload(selectedSlot, dayOffset, startMinute,
                amount(draftPrice), draftName, draftPromo));
        onClose();
    }
}

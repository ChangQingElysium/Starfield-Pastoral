package com.stardew.craft.client.building;

import com.stardew.craft.building.runtime.BuildingRecord;
import com.stardew.craft.client.gui.FarmFolioScreen;
import com.stardew.craft.network.payload.BuildingWorkRequestPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

public final class BuildingWorkScreen extends FarmFolioScreen {
    private CompoundTag data;
    private int selected, page;
    private java.util.UUID pendingRequest;

    public BuildingWorkScreen(CompoundTag data) {
        super(Component.translatable("building.stardewcraft.manage_prefabs"), null);
        this.data = data;
    }

    @Override
    protected void layout() {
        var entries = data.getList("Entries", 10);
        int count = Math.min(3, Math.max(1, (h - 112) / 48));
        int side = Math.min(140, w / 3);
        page = com.stardew.craft.port.PortJava.clamp(page, 0, Math.max(0, (entries.size() - 1) / count));
        selected = entries.isEmpty() ? -1 : com.stardew.craft.port.PortJava.clamp(selected, page * count, Math.min(entries.size(), (page + 1) * count) - 1);
        for (int i = page * count; i < Math.min(entries.size(), (page + 1) * count); i++) {
            int index = i;
            var record = BuildingRecord.load(entries.getCompound(i));
            button(
                    record.title(),
                    x + 12,
                    y + 42 + (i - page * count) * 48,
                    side,
                    40,
                    selected == i ? "packet_selected" : "tab",
                    "home",
                    () -> {
                        selected = index;
                        init();
                    }).active = pendingRequest == null;
        }
        arrow(
                false,
                x + 16,
                y + h - 70,
                pendingRequest == null && page > 0,
                () -> {
                    page--;
                    init();
                });
        arrow(
                true,
                x + side - 16,
                y + h - 70,
                pendingRequest == null && (page + 1) * count < entries.size(),
                () -> {
                    page++;
                    init();
                });
        button(Component.translatable("gui.back"), x + 16, y + h - 34, 90, 24, this::onClose);
        if (!entries.isEmpty()) {
            var row = entries.getCompound(selected);
            var record = BuildingRecord.load(row);
            button(
                                    Component.translatable("building.stardewcraft.preview_upgrade"),
                                    x + side + 24,
                                    y + h - 70,
                                    w - side - 40,
                                    26,
                                    () -> send("preview", row))
                            .active =
                    pendingRequest == null && row.getBoolean("CanUpgrade");
            button(
                                    Component.translatable("building.stardewcraft.move_building"),
                                    x + w - 188,
                                    y + h - 34,
                                    172,
                                    24,
                                    "button",
                                    "move",
                                    () -> send("move", row))
                            .active =
                    pendingRequest == null && record.phase() == BuildingRecord.Phase.READY;
        }
    }

    private void send(String action, CompoundTag row) {
        if (pendingRequest != null) return;
        pendingRequest = java.util.UUID.randomUUID();
        init();
        PacketDistributor.sendToServer(
                new BuildingWorkRequestPayload(
                        new ResourceLocation(data.getString("Family")),
                        data.getLong("Catalog"),
                        row.getUUID("Id"),
                        row.getLong("Revision"),
                        action,
                        pendingRequest));
    }

    public boolean acceptsReply(java.util.UUID request) {
        return request.equals(pendingRequest);
    }

    public void applyReply(CompoundTag reply) {
        pendingRequest = null;
        if (reply.getBoolean("Close")) {
            onClose();
            return;
        }
        var oldRows = data.getList("Entries", 10);
        var selectedId = selected >= 0 && selected < oldRows.size()
                ? oldRows.getCompound(selected).getUUID("Id") : null;
        data = reply.copy();
        var rows = data.getList("Entries", 10);
        for (int i = 0; i < rows.size(); i++) {
            if (rows.getCompound(i).getUUID("Id").equals(selectedId)) {
                selected = i;
                page = i / Math.min(3, Math.max(1, (h - 112) / 48));
                break;
            }
        }
        init();
    }

    @Override
    protected void paint(GuiGraphics g) {
        int side = Math.min(140, w / 3), px = x + side + 24, pw = w - side - 40;
        paper(g, px, y + 36, pw, h - 116);
        var entries = data.getList("Entries", 10);
        if (entries.isEmpty()) {
            paragraph(
                    g,
                    Component.translatable("building.stardewcraft.no_prefabs"),
                    px + 12,
                    y + 50,
                    pw - 24,
                    y + h - 90,
                    MUTED);
            return;
        }
        var row = entries.getCompound(selected);
        var r = BuildingRecord.load(row);
        label(g, r.title(), px + 14, y + 50, pw - 28, INK);
        RobinCatalogArt.draw(
                g,
                r.family().toString() + (r.tier() > 1 ? "_upgrade_" + r.tier() : ""),
                px + 14,
                y + 76,
                pw - 28,
                Math.max(34, h - 192));
        label(
                g,
                Component.translatable(
                        "building.stardewcraft.ledger_state",
                        r.tier(),
                        Component.translatable(
                                "building.stardewcraft.phase."
                                        + r.phase().name().toLowerCase(java.util.Locale.ROOT))),
                px + 14,
                y + h - 108,
                pw - 28,
                MUTED);
    }
}

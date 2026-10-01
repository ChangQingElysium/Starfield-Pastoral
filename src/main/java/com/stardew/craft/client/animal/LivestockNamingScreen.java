package com.stardew.craft.client.animal;

import com.stardew.craft.client.gui.*;
import com.stardew.craft.network.payload.IncubatorClaimSubmitPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

public final class LivestockNamingScreen extends FarmFolioScreen {
    private final BlockPos incubator;
    private final java.util.UUID newborn;
    private final CompoundTag data;
    private String value = "";
    private EditBox input;
    private net.minecraft.client.gui.components.Button confirm;

    public LivestockNamingScreen(BlockPos pos) {
        this(tag(pos));
    }

    public LivestockNamingScreen(java.util.UUID id) {
        this(tag(id));
    }

    private static CompoundTag tag(BlockPos pos) {
        var t = new CompoundTag();
        t.putLong("Position", pos.asLong());
        return t;
    }

    private static CompoundTag tag(java.util.UUID id) {
        var t = new CompoundTag();
        t.putUUID("Id", id);
        return t;
    }

    public LivestockNamingScreen(CompoundTag data) {
        super(ui("newborn"), null);
        this.data = data;
        incubator = data.contains("Position") ? BlockPos.of(data.getLong("Position")) : null;
        newborn = data.hasUUID("Id") ? data.getUUID("Id") : null;
    }

    @Override
    protected int preferredWidth() {
        return 280;
    }

    @Override
    protected int preferredHeight() {
        return 305;
    }

    @Override
    protected void layout() {
        input =
                field(
                        Component.translatable("livestock.stardewcraft.name_required"),
                        value,
                        x + 24,
                        y + h - 116,
                        w - 90,
                        32);
        input.setResponder(
                s -> {
                    value = s;
                    if (confirm != null) confirm.active = FarmRenameScreen.valid(s);
                });
        tile(
                ui("random_name"),
                x + w - 56,
                y + h - 116,
                32,
                32,
                () -> input.setValue(FarmAnimalNames.next()),
                (g, b) -> {
                    box(g, "button", b.getX(), b.getY(), 32, 32);
                    glyph(g, "dice", b.getX() + 8, b.getY() + 8);
                });
        button(ui("later"), x + 16, y + h - 38, (w - 42) / 2, 26, this::onClose);
        confirm =
                button(
                        Component.translatable("gui.done"),
                        x + w / 2 + 5,
                        y + h - 38,
                        (w - 42) / 2,
                        26,
                        () -> {
                            if (newborn == null)
                                PacketDistributor.sendToServer(
                                        new IncubatorClaimSubmitPayload(incubator, value));
                            else
                                PacketDistributor.sendToServer(
                                        new com.stardew.craft.animal.runtime.LivestockManagePayload(
                                                newborn,
                                                newborn,
                                                new java.util.UUID(0, 0),
                                                "birth",
                                                value));
                            onClose();
                        });
        confirm.active = FarmRenameScreen.valid(value);
        setInitialFocus(input);
    }

    @Override
    protected void paint(GuiGraphics g) {
        paper(g, x + 16, y + 36, w - 32, h - 90);
        LivestockPortrait.draw(g, data, x + 40, y + 50, w - 80, Math.max(30, h - 194), false);
        label(
                g,
                Component.translatable("livestock.stardewcraft.name_required"),
                x + 24,
                y + h - 138,
                w - 48,
                INK);
        if (!data.getString("BuildingName").isBlank())
            label(
                    g,
                    Component.literal(data.getString("BuildingName")),
                    x + 24,
                    y + h - 72,
                    w - 48,
                    MUTED);
    }
}

package com.stardew.craft.client.gui;

import com.stardew.craft.network.payload.*;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

/** Public API adapter uses the named-home picker and its explicit confirmation. */
public final class AnimalMoveHomeSelectScreen extends FarmFolioScreen {
    private final OpenAnimalMoveHomeScreenPayload source;

    public AnimalMoveHomeSelectScreen(OpenAnimalMoveHomeScreenPayload source) {
        super(ui("choose_home"), null);
        this.source = source;
    }

    @Override
    protected void layout() {}

    @Override
    public void tick() {
        var rows =
                source.options().stream()
                        .map(
                                h -> {
                                    var t = new CompoundTag();
                                    t.putString("StableId", h.buildingId());
                                    t.putString("BuildingName", h.displayName());
                                    t.putInt("Used", h.animalCount());
                                    t.putInt("Capacity", h.capacity());
                                    t.putBoolean("Selectable", h.selectable());
                                    return t;
                                })
                        .toList();
        minecraft.setScreen(
                new BuildingChoiceScreen(
                        null,
                        rows,
                        home -> {
                            PacketDistributor.sendToServer(
                                    new AnimalMoveHomeSelectPayload(
                                            source.animalId(), home.getString("StableId")));
                        }));
    }

    @Override
    protected void paint(GuiGraphics g) {}
}

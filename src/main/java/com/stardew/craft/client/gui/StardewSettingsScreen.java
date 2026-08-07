package com.stardew.craft.client.gui;

import com.stardew.craft.Config;
import com.stardew.craft.client.hud.StardewHudLayoutEditorScreen;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Player-facing settings only; internal compatibility and HUD state stay out of the raw config UI. */
public final class StardewSettingsScreen extends OptionsSubScreen {
    private final OptionInstance<Boolean> weaponSpecialEffects = booleanOption(
            "config.stardewcraft.client.weapon_special_effects",
            Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get(),
            Config.ENABLE_WEAPON_SPECIAL_EFFECTS::set,
            Config.CLIENT_SPEC::save);
    private final OptionInstance<Boolean> weaponPostEffects = booleanOption(
            "config.stardewcraft.client.weapon_post_effects",
            Config.ENABLE_WEAPON_POST_EFFECTS.get(),
            Config.ENABLE_WEAPON_POST_EFFECTS::set,
            Config.CLIENT_SPEC::save);
    private final OptionInstance<Boolean> monsterHpBars = booleanOption(
            "config.stardewcraft.client.show_monster_hp_bar",
            Config.SHOW_MONSTER_HP_BAR.get(),
            Config.SHOW_MONSTER_HP_BAR::set,
            Config.CLIENT_SPEC::save);
    private final OptionInstance<Boolean> stardewFonts = booleanOption(
            "config.stardewcraft.client.enable_stardew_fonts",
            Config.ENABLE_STARDEW_FONTS.get(),
            Config.ENABLE_STARDEW_FONTS::set,
            Config.CLIENT_SPEC::save);

    public StardewSettingsScreen(Screen parent) {
        super(parent, net.minecraft.client.Minecraft.getInstance().options,
                Component.translatable("stardewcraft.settings.title"));
    }

    @Override
    protected void addOptions() {
        list.addSmall(weaponSpecialEffects, weaponPostEffects, monsterHpBars, stardewFonts);
        list.addSmall(List.of(
                Button.builder(Component.translatable("stardewcraft.settings.hud_layout"),
                                button -> minecraft.setScreen(new StardewHudLayoutEditorScreen(this)))
                        .build(),
                Button.builder(Component.translatable("stardewcraft.settings.key_bindings"),
                                button -> minecraft.setScreen(new KeyBindsScreen(this, options)))
                        .build()));

        if (Config.SERVER_SPEC.isLoaded() && minecraft.getSingleplayerServer() != null) {
            list.addSmall(
                    booleanOption("config.stardewcraft.server.enable_fishing_minigame",
                            Config.ENABLE_FISHING_MINIGAME.get(), Config.ENABLE_FISHING_MINIGAME::set,
                            Config.SERVER_SPEC::save),
                    booleanOption("config.stardewcraft.server.enable_update_checks",
                            Config.ENABLE_UPDATE_CHECKS.get(), Config.ENABLE_UPDATE_CHECKS::set,
                            Config.SERVER_SPEC::save),
                    booleanOption("config.stardewcraft.server.show_community_announcement",
                            Config.SHOW_COMMUNITY_ANNOUNCEMENT.get(), Config.SHOW_COMMUNITY_ANNOUNCEMENT::set,
                            Config.SERVER_SPEC::save));
            EditBox clockSpeed = new EditBox(com.stardew.craft.client.font.StardewFonts.small(), 150, 20,
                    Component.translatable("config.stardewcraft.server.time_speed_multiplier"));
            clockSpeed.setValue(Double.toString(Config.TIME_SPEED_MULTIPLIER.get()));
            clockSpeed.setResponder(value -> updateClockSpeed(clockSpeed, value));
            list.addSmall(new StringWidget(150, 20,
                            Component.translatable("config.stardewcraft.server.time_speed_multiplier"), font)
                            .alignLeft(), clockSpeed);
        }
    }

    @Override
    public void removed() {
        Config.CLIENT_SPEC.save();
        super.removed();
    }

    private static OptionInstance<Boolean> booleanOption(
            String translationKey,
            boolean initialValue,
            java.util.function.Consumer<Boolean> setter,
            Runnable save
    ) {
        return OptionInstance.createBoolean(translationKey, initialValue, value -> {
            setter.accept(value);
            save.run();
        });
    }

    private static void updateClockSpeed(EditBox input, String value) {
        try {
            double parsed = Double.parseDouble(value);
            if (parsed < 0.1D || parsed > 100.0D) {
                throw new NumberFormatException();
            }
            input.setTextColor(EditBox.DEFAULT_TEXT_COLOR);
            Config.TIME_SPEED_MULTIPLIER.set(parsed);
            Config.SERVER_SPEC.save();
        } catch (NumberFormatException ignored) {
            input.setTextColor(0xFFFF5555);
        }
    }
}

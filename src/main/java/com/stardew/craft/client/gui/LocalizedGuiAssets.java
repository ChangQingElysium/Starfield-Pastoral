package com.stardew.craft.client.gui;

import com.stardew.craft.StardewCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Set;

/** Resolves language-specific GUI textures extracted from Stardew Valley's official assets. */
public final class LocalizedGuiAssets {
    private static final String DEFAULT_LANGUAGE = "en_us";
    private static final Set<String> STARDEW_LANGUAGES = Set.of(
            "en_us",
            "de_de",
            "es_es",
            "fr_fr",
            "hu_hu",
            "it_it",
            "ja_jp",
            "ko_kr",
            "pt_br",
            "ru_ru",
            "tr_tr",
            "zh_cn"
    );

    private LocalizedGuiAssets() {
    }

    public static ResourceLocation texture(String relativePath) {
        return ResourceLocation.fromNamespaceAndPath(
                StardewCraft.MODID,
                "textures/gui/localized/" + currentLanguage() + "/" + relativePath
        );
    }

    private static String currentLanguage() {
        String selected = Minecraft.getInstance().getLanguageManager().getSelected()
                .toLowerCase(Locale.ROOT)
                .replace('-', '_');
        return STARDEW_LANGUAGES.contains(selected) ? selected : DEFAULT_LANGUAGE;
    }
}

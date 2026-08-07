package com.stardew.craft.client.font;

import com.mojang.blaze3d.font.GlyphProvider;
import com.stardew.craft.Config;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.mixin.FontFontSetAccessor;
import com.stardew.craft.mixin.FontSetProvidersAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;

/** Owns the source-exact Stardew glyph providers and rebuilds them after MC fonts. */
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
final class StardewFontManager implements ResourceManagerReloadListener {
    private record LoadedFont(StardewFontData data, StardewGlyphProvider provider, FontSet fontSet) {
        void close() {
            fontSet.close();
            provider.close();
        }
    }

    private static final StardewFontManager INSTANCE = new StardewFontManager();
    private final Map<StardewFonts.Role, LoadedFont> loaded = new EnumMap<>(StardewFonts.Role.class);

    private StardewFontManager() {
    }

    @SubscribeEvent
    static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        // Minecraft registers FontManager before this mod-bus event. Resource
        // reload order therefore guarantees that fallback providers are ready.
        event.registerReloadListener(INSTANCE);
    }

    @Override
    public void onResourceManagerReload(ResourceManager resources) {
        loaded.values().forEach(LoadedFont::close);
        loaded.clear();

        Minecraft minecraft = Minecraft.getInstance();
        FontSet vanillaDefault = ((FontFontSetAccessor) (Object) minecraft.font)
                .stardewcraft$getFontSet(Minecraft.DEFAULT_FONT);
        List<GlyphProvider> fallbackProviders =
                ((FontSetProvidersAccessor) (Object) vanillaDefault).stardewcraft$getActiveProviders();
        String language = selectedLanguage(minecraft);

        try {
            load(resources, fallbackProviders, StardewFonts.Role.DIALOGUE, language);
            load(resources, fallbackProviders, StardewFonts.Role.SMALL, language);
            load(resources, fallbackProviders, StardewFonts.Role.TINY, "base");
            deriveTooltip(resources, fallbackProviders, StardewFonts.Role.TOOLTIP_TITLE,
                    StardewFonts.Role.DIALOGUE);
            deriveTooltip(resources, fallbackProviders, StardewFonts.Role.TOOLTIP_BODY,
                    StardewFonts.Role.SMALL);

            String spriteVariant = switch (language) {
                case "ja_jp", "ko_kr", "zh_cn", "zh_cn_round" -> language;
                default -> "latin_bold";
            };
            String coloredVariant = switch (language) {
                case "ja_jp", "ko_kr", "zh_cn", "zh_cn_round" -> language;
                default -> "latin_colored";
            };
            load(resources, fallbackProviders, StardewFonts.Role.SPRITE_TEXT, spriteVariant);
            load(resources, fallbackProviders, StardewFonts.Role.SPRITE_TEXT_COLORED, coloredVariant);
        } catch (IOException exception) {
            loaded.values().forEach(LoadedFont::close);
            loaded.clear();
            throw new IllegalStateException("Failed to load authored Stardew fonts", exception);
        }
    }

    private void load(ResourceManager resources, List<GlyphProvider> fallbacks,
                      StardewFonts.Role role, String variant) throws IOException {
        String definitionRole = role == StardewFonts.Role.SPRITE_TEXT_COLORED
                ? StardewFonts.Role.SPRITE_TEXT.path()
                : role.path();
        ResourceLocation metrics = ResourceLocation.fromNamespaceAndPath(
                StardewCraft.MODID,
                "stardew_font_metrics/" + definitionRole + "/" + variant + ".json");
        StardewFontData data = StardewFontData.load(resources, metrics);
        StardewGlyphProvider provider = new StardewGlyphProvider(data, resources);
        FontSet fontSet = new FontSet(Minecraft.getInstance().getTextureManager(), role.id());
        List<GlyphProvider.Conditional> providers = new ArrayList<>(fallbacks.size() + 1);
        providers.add(new GlyphProvider.Conditional(provider, FontOption.Filter.ALWAYS_PASS));
        for (GlyphProvider fallback : fallbacks) {
            providers.add(new GlyphProvider.Conditional(fallback, FontOption.Filter.ALWAYS_PASS));
        }
        fontSet.reload(providers, Set.of());
        loaded.put(role, new LoadedFont(data, provider, fontSet));
    }

    private void deriveTooltip(ResourceManager resources, List<GlyphProvider> fallbacks,
                               StardewFonts.Role target, StardewFonts.Role source) throws IOException {
        LoadedFont authored = loaded.get(source);
        if (authored == null) {
            throw new IOException("Missing authored font role " + source);
        }
        // MC's default font has an 8px visible face in a 10px tooltip row.
        // Derive the scale from the actual atlas bounds instead of guessing per language.
        float referenceHeight = referenceGlyphHeight(authored.data());
        StardewFontData data = authored.data().withLayout(8.0F / referenceHeight, 10.0F);
        StardewGlyphProvider provider = new StardewGlyphProvider(data, resources);
        FontSet fontSet = new FontSet(Minecraft.getInstance().getTextureManager(), target.id());
        List<GlyphProvider.Conditional> providers = new ArrayList<>(fallbacks.size() + 1);
        providers.add(new GlyphProvider.Conditional(provider, FontOption.Filter.ALWAYS_PASS));
        for (GlyphProvider fallback : fallbacks) {
            providers.add(new GlyphProvider.Conditional(fallback, FontOption.Filter.ALWAYS_PASS));
        }
        fontSet.reload(providers, Set.of());
        loaded.put(target, new LoadedFont(data, provider, fontSet));
    }

    private static float referenceGlyphHeight(StardewFontData data) {
        int[] cjkReferences = "田中国日人水山大小".codePoints()
                .map(codepoint -> glyphHeight(data, codepoint)).filter(height -> height > 0).toArray();
        int[] latinReferences = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".codePoints()
                .map(codepoint -> glyphHeight(data, codepoint)).filter(height -> height > 0).toArray();
        int[] samples = cjkReferences.length >= 3 ? cjkReferences : latinReferences;
        if (samples.length == 0) {
            return Math.max(1.0F, data.sourceLineHeight());
        }
        Arrays.sort(samples);
        return Math.max(1.0F, samples[samples.length / 2]);
    }

    private static int glyphHeight(StardewFontData data, int codepoint) {
        StardewFontData.Glyph glyph = data.glyph(codepoint);
        return glyph == null ? 0 : glyph.heightPixels();
    }

    static FontSet fontSet(StardewFonts.Role role) {
        LoadedFont font = INSTANCE.loaded.get(role);
        return font == null ? null : font.fontSet();
    }

    static StardewFontData data(StardewFonts.Role role) {
        if (!StardewFonts.enabled()) {
            return null;
        }
        LoadedFont font = INSTANCE.loaded.get(role);
        return font == null ? null : font.data();
    }

    static StardewFonts.Role role(ResourceLocation id) {
        for (StardewFonts.Role role : StardewFonts.Role.values()) {
            if (role.id().equals(id)) {
                return role;
            }
        }
        return null;
    }

    private static String selectedLanguage(Minecraft minecraft) {
        String language = minecraft.getLanguageManager().getSelected()
                .toLowerCase(Locale.ROOT).replace('-', '_');
        if ("it_it".equals(language)) {
            language = "en_us";
        } else if (!Set.of(
                "en_us", "de_de", "es_es", "fr_fr", "hu_hu", "ja_jp",
                "ko_kr", "pt_br", "ru_ru", "tr_tr", "zh_cn").contains(language)) {
            language = "en_us";
        }
        if ("zh_cn".equals(language) && useChineseSmoothFont()) {
            return "zh_cn_round";
        }
        return language;
    }

    private static boolean useChineseSmoothFont() {
        try {
            return Config.USE_CHINESE_SMOOTH_FONT.get();
        } catch (IllegalStateException ignored) {
            return false;
        }
    }
}

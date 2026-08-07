package com.stardew.craft.client.font;

import com.stardew.craft.Config;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.mixin.FontFontSetAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/** Semantic Stardew fonts backed by the current-language dynamic font pack. */
public final class StardewFonts {
    public enum Role {
        DIALOGUE("dialogue"),
        SMALL("small"),
        TINY("tiny"),
        TOOLTIP_TITLE("tooltip_title"),
        TOOLTIP_BODY("tooltip_body"),
        SPRITE_TEXT("sprite_text"),
        SPRITE_TEXT_COLORED("sprite_text_colored");

        private final String path;

        Role(String path) {
            this.path = path;
        }

        public String path() {
            return path;
        }

        public ResourceLocation id() {
            return fontResource(path);
        }
    }

    public static final ResourceLocation DIALOGUE_ID = fontResource("dialogue");
    public static final ResourceLocation SMALL_ID = fontResource("small");
    public static final ResourceLocation TINY_ID = fontResource("tiny");
    public static final ResourceLocation TOOLTIP_TITLE_ID = fontResource("tooltip_title");
    public static final ResourceLocation TOOLTIP_BODY_ID = fontResource("tooltip_body");
    public static final ResourceLocation SPRITE_TEXT_ID = fontResource("sprite_text");
    public static final ResourceLocation SPRITE_TEXT_COLORED_ID = fontResource("sprite_text_colored");

    private static Font dialogue;
    private static Font small;
    private static Font tiny;
    private static Font tooltip;
    private static Font spriteText;
    private static Font spriteTextColored;

    private StardewFonts() {
    }

    public static Font dialogue() {
        if (dialogue == null) {
            dialogue = create(DIALOGUE_ID);
        }
        return dialogue;
    }

    public static Font small() {
        if (small == null) {
            small = create(SMALL_ID);
        }
        return small;
    }

    public static Font tiny() {
        if (tiny == null) {
            tiny = create(TINY_ID);
        }
        return tiny;
    }

    /** Minecraft-native tooltip metrics with Stardew's authored glyph shapes. */
    public static Font tooltip() {
        if (tooltip == null) {
            tooltip = create(TOOLTIP_BODY_ID);
        }
        return tooltip;
    }

    public static Font spriteText() {
        if (spriteText == null) {
            spriteText = create(SPRITE_TEXT_ID);
        }
        return spriteText;
    }

    public static Font spriteTextColored() {
        if (spriteTextColored == null) {
            spriteTextColored = create(SPRITE_TEXT_COLORED_ID);
        }
        return spriteTextColored;
    }

    /** Use Stardew's tintable SpriteText sheet for a colored screen heading. */
    public static MutableComponent title(Component text) {
        return text.copy().withStyle(style -> style
                .withFont(SPRITE_TEXT_COLORED_ID)
                .withBold(false));
    }

    /** Use Stardew's baked gold/orange SpriteText sheet for scroll and banner headings. */
    public static MutableComponent bannerTitle(Component text) {
        return text.copy().withStyle(style -> style
                .withFont(SPRITE_TEXT_ID)
                .withColor(TextColor.fromRgb(spriteTextDefaultRgb()))
                .withBold(false));
    }

    /** SpriteText is baked gold for Latin; BMFont languages use SDV's brown tint. */
    public static int spriteTextDefaultRgb() {
        Minecraft minecraft = Minecraft.getInstance();
        String language = minecraft == null || minecraft.getLanguageManager() == null
                ? "en_us"
                : minecraft.getLanguageManager().getSelected();
        language = language.toLowerCase(Locale.ROOT).replace('-', '_');
        return language.startsWith("ja")
                || language.startsWith("ko")
                || language.startsWith("zh")
                ? 0x56160C
                : 0xFFFFFF;
    }

    public static Font forRole(Role role) {
        return switch (role) {
            case DIALOGUE -> dialogue();
            case SMALL -> small();
            case TINY -> tiny();
            case TOOLTIP_TITLE, TOOLTIP_BODY -> tooltip();
            case SPRITE_TEXT -> spriteText();
            case SPRITE_TEXT_COLORED -> spriteTextColored();
        };
    }

    public static Role role(ResourceLocation id) {
        return StardewFontManager.role(id);
    }

    /** Used by the FontManager mixin so styled text is measured before tooltip rendering. */
    public static FontSet resolveFontSet(ResourceLocation id) {
        Role role = StardewFontManager.role(id);
        if (role == null) {
            return null;
        }
        if (enabled()) {
            return StardewFontManager.fontSet(role);
        }
        return minecraftFontSet(Minecraft.DEFAULT_FONT);
    }

    /** Authored logical line height normalized to Minecraft's 9px font grid. */
    public static float lineHeight(Role role) {
        StardewFontData data = StardewFontManager.data(role);
        return data == null ? 9.0F : data.lineHeight();
    }

    private static Font create(ResourceLocation defaultFont) {
        StardewFonts.Role defaultRole = StardewFontManager.role(defaultFont);
        return new StardewFont(defaultRole, requested -> fontSet(
                Style.DEFAULT_FONT.equals(requested) ? defaultFont : requested), false);
    }

    private static FontSet fontSet(ResourceLocation id) {
        StardewFonts.Role role = StardewFontManager.role(id);
        FontSet authored = role == null || !enabled() ? null : StardewFontManager.fontSet(role);
        if (authored != null) {
            return authored;
        }
        return minecraftFontSet(role == null ? id : Minecraft.DEFAULT_FONT);
    }

    static boolean enabled() {
        try {
            return Config.ENABLE_STARDEW_FONTS.get();
        } catch (IllegalStateException ignored) {
            // Keep the current default while the client config is not loaded yet.
            return true;
        }
    }

    private static FontSet minecraftFontSet(ResourceLocation id) {
        Font minecraftFont = Minecraft.getInstance().font;
        return ((FontFontSetAccessor) (Object) minecraftFont).stardewcraft$getFontSet(id);
    }

    private static ResourceLocation fontResource(String role) {
        return ResourceLocation.fromNamespaceAndPath(
                StardewCraft.MODID, "stardew/" + role);
    }
}

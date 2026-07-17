package com.stardew.craft.item;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;
import java.util.List;

/** A permanent special item which represents one entry on the Powers page. */
public final class PowerSpecialItem extends Item implements IStardewItem {
    private static final float NAME_SWEEP_SPEED = 0.38F;
    private static final float NAME_SWEEP_WIDTH = 0.28F;

    private final String mailFlag;
    private final String specialItemId;
    private final Theme theme;

    public PowerSpecialItem(Properties properties, String mailFlag, String specialItemId, Theme theme) {
        super(properties);
        this.mailFlag = mailFlag;
        this.specialItemId = specialItemId;
        this.theme = theme;
    }

    public String mailFlag() {
        return mailFlag;
    }

    public String specialItemId() {
        return specialItemId;
    }

    public Theme theme() {
        return theme;
    }

    @Override
    public String getItemTypeKey() {
        return "stardewcraft.type.special";
    }

    @Override
    public int getSellPrice(ItemStack stack) {
        return -1;
    }

    @Override
    public void inventoryTick(@Nonnull ItemStack stack, @Nonnull Level level, @Nonnull Entity entity,
                              int slot, boolean selected) {
        if (!level.isClientSide && entity instanceof ServerPlayer player) {
            PowerSpecialItemService.grantFromItem(player, this);
        }
    }

    @Override
    public boolean isFoil(@Nonnull ItemStack stack) {
        return true;
    }

    @Override
    public boolean canBeHurtBy(@Nonnull ItemStack stack,
                               @Nonnull net.minecraft.world.damagesource.DamageSource source) {
        return false;
    }

    @Override
    public Component getName(@Nonnull ItemStack stack) {
        String raw = Component.translatable(getDescriptionId(stack)).getString();
        float pos = ((((System.currentTimeMillis() % 60_000L) / 1000.0F) * NAME_SWEEP_SPEED)
                % (1.0F + NAME_SWEEP_WIDTH * 2.0F)) - NAME_SWEEP_WIDTH;
        MutableComponent result = Component.empty();
        for (int i = 0; i < raw.length(); i++) {
            float u = raw.length() > 1 ? (float) i / (raw.length() - 1) : 0.5F;
            float k = Math.max(0.0F, 1.0F - Math.abs(u - pos) / NAME_SWEEP_WIDTH);
            k = k * k * (3.0F - 2.0F * k);
            result.append(Component.literal(String.valueOf(raw.charAt(i))).withStyle(
                    Style.EMPTY.withColor(TextColor.fromRgb(lerpRgb(theme.nameBaseRgb(),
                            theme.nameHighlightRgb(), k))).withBold(true)));
        }
        return result;
    }

    @Override
    public void appendHoverText(@Nonnull ItemStack stack, @Nonnull TooltipContext context,
                                @Nonnull List<Component> tooltipComponents, @Nonnull TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable(getDescriptionId(stack) + ".tooltip.flavor")
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(theme.flavorRgb()))));
        tooltipComponents.add(Component.translatable(getDescriptionId(stack) + ".tooltip.granted")
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(theme.grantedRgb())).withBold(true)));
    }

    private static int lerpRgb(int a, int b, float k) {
        k = Mth.clamp(k, 0.0F, 1.0F);
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = Math.round(ar + (br - ar) * k);
        int g = Math.round(ag + (bg - ag) * k);
        int bl = Math.round(ab + (bb - ab) * k);
        return (r << 16) | (g << 8) | bl;
    }

    public enum Theme {
        FOREST(0x3D8B4F, 0xD8FFC2, 0x6DAE72, 0xBDEFA5,
                0xFF4B9A58, 0xFFD8FFC2, 0xF008160B, 0xF0030A05),
        CLUB(0x8A315A, 0xFFD5E7, 0xB65A82, 0xF2A7C8,
                0xFFA44270, 0xFFFFD1E4, 0xF0180710, 0xF0090307),
        DARK(0x4D326E, 0xD9C1FF, 0x76549A, 0xBDA0E6,
                0xFF5A3B7D, 0xFFD8C0FF, 0xF00B0612, 0xF0040208),
        INK(0x315A9B, 0xC9E7FF, 0x527DB8, 0x9CCCF3,
                0xFF3D69A8, 0xFFC8E8FF, 0xF0050C19, 0xF002050B),
        ONION(0x3D8E58, 0xF1FFD0, 0x6EB073, 0xD9F5A8,
                0xFF4B9D60, 0xFFF0FFD1, 0xF007150A, 0xF0020904),
        TOWN(0xA06B27, 0xFFF0B3, 0xBA853E, 0xF2D17C,
                0xFFB87A2C, 0xFFFFEAB0, 0xF0180E03, 0xF0090501);

        private final int nameBaseRgb;
        private final int nameHighlightRgb;
        private final int flavorRgb;
        private final int grantedRgb;
        private final int borderBaseArgb;
        private final int borderHighlightArgb;
        private final int backgroundStartArgb;
        private final int backgroundEndArgb;

        Theme(int nameBaseRgb, int nameHighlightRgb, int flavorRgb, int grantedRgb,
              int borderBaseArgb, int borderHighlightArgb,
              int backgroundStartArgb, int backgroundEndArgb) {
            this.nameBaseRgb = nameBaseRgb;
            this.nameHighlightRgb = nameHighlightRgb;
            this.flavorRgb = flavorRgb;
            this.grantedRgb = grantedRgb;
            this.borderBaseArgb = borderBaseArgb;
            this.borderHighlightArgb = borderHighlightArgb;
            this.backgroundStartArgb = backgroundStartArgb;
            this.backgroundEndArgb = backgroundEndArgb;
        }

        public int nameBaseRgb() { return nameBaseRgb; }
        public int nameHighlightRgb() { return nameHighlightRgb; }
        public int flavorRgb() { return flavorRgb; }
        public int grantedRgb() { return grantedRgb; }
        public int borderBaseArgb() { return borderBaseArgb; }
        public int borderHighlightArgb() { return borderHighlightArgb; }
        public int backgroundStartArgb() { return backgroundStartArgb; }
        public int backgroundEndArgb() { return backgroundEndArgb; }
    }
}

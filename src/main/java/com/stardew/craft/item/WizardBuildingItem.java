package com.stardew.craft.item;

import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.player.PlayerDisplayName;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * A non-transferable Wizard building item whose ownership is fixed on acquisition.
 */
@SuppressWarnings("null")
public final class WizardBuildingItem extends StardewBlockItem {
    private static final String TAG_OWNER = "WizardBuildingOwner";
    private static final String TAG_OWNER_NAME = "WizardBuildingOwnerName";
    private static final float NAME_SWEEP_SPEED = 0.32F;
    private static final float NAME_SWEEP_WIDTH = 0.30F;
    private final Kind kind;

    public WizardBuildingItem(Block block, Kind kind, Properties properties) {
        // The common Stardew tooltip path supplies <descriptionId>.desc. Passing a
        // description key here would render the same description twice.
        super(block, "stardewcraft.type.wizard_building", -1, properties);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public static void bindTo(ItemStack stack, ServerPlayer player) {
        bindTo(stack, player.getUUID(), PlayerDisplayName.get(player));
    }

    /**
     * Binds an unowned stack. Existing ownership is intentionally immutable.
     */
    public static void bindTo(ItemStack stack, UUID owner, String ownerName) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.hasUUID(TAG_OWNER)) {
            return;
        }
        tag.putUUID(TAG_OWNER, owner);
        tag.putString(TAG_OWNER_NAME, ownerName == null ? "" : ownerName);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Nullable
    public static UUID getOwner(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.hasUUID(TAG_OWNER) ? tag.getUUID(TAG_OWNER) : null;
    }

    public static String getOwnerName(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.getString(TAG_OWNER_NAME);
    }

    public static boolean isOwnedBy(ItemStack stack, UUID playerId) {
        UUID owner = getOwner(stack);
        return owner != null && owner.equals(playerId);
    }

    @Override
    public void inventoryTick(@Nonnull ItemStack stack, @Nonnull Level level, @Nonnull Entity entity,
                              int slot, boolean selected) {
        if (!level.isClientSide && entity instanceof ServerPlayer player && getOwner(stack) == null) {
            bindTo(stack, player);
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer player) {
            ItemStack stack = context.getItemInHand();
            if (getOwner(stack) == null) {
                bindTo(stack, player);
            }
            if (!isOwnedBy(stack, player.getUUID())) {
                player.displayClientMessage(Component.translatable(
                        "message.stardewcraft.wizard_building.owner_only", ownerLabel(stack)), true);
                return InteractionResult.FAIL;
            }
            FarmInstance farm = FarmInstanceRegistry.get().getFarmForPlayer(player.getUUID());
            if (farm == null) {
                player.displayClientMessage(Component.translatable(
                        "message.stardewcraft.wizard_building.farm_only"), true);
                return InteractionResult.FAIL;
            }
        }
        return super.useOn(context);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        UUID owner = getOwner(stack);
        if (owner == null) {
            tooltipComponents.add(Component.translatable(
                    "tooltip.stardewcraft.wizard_building.owner_unassigned")
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(kind.effectRgb()))));
        } else {
            tooltipComponents.add(Component.translatable(
                    "tooltip.stardewcraft.wizard_building.owner", ownerLabel(stack))
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(kind.effectRgb()))));
        }
        tooltipComponents.add(Component.translatable(
                "tooltip.stardewcraft.wizard_building.bound")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltipComponents.add(Component.translatable(
                "tooltip.stardewcraft.wizard_building.farm_only")
                .withStyle(ChatFormatting.DARK_GRAY));
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
                    Style.EMPTY.withColor(TextColor.fromRgb(lerpRgb(
                            kind.nameBaseRgb(), kind.nameHighlightRgb(), k))).withBold(true)));
        }
        return result;
    }

    @Override
    public boolean isFoil(@Nonnull ItemStack stack) {
        return true;
    }

    @Override
    public boolean canBeHurtBy(ItemStack stack, net.minecraft.world.damagesource.DamageSource source) {
        return false;
    }

    private static String ownerLabel(ItemStack stack) {
        String name = getOwnerName(stack).trim();
        if (!name.isBlank()) {
            return name;
        }
        UUID owner = getOwner(stack);
        return owner == null ? "?" : owner.toString();
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

    public enum Kind {
        JUNIMO_HUT(0x438C50, 0xD9FFC1, 0x96D878,
                0xFF4B9A58, 0xFFD8FFC2, 0xF008160B, 0xF0030A05),
        EARTH_OBELISK(0x8B6A3C, 0xF6E1A4, 0xD7B76E,
                0xFFA77A3C, 0xFFFFE2A2, 0xF0171006, 0xF0080502),
        WATER_OBELISK(0x2F78A6, 0xC7F1FF, 0x72CBE8,
                0xFF368BB8, 0xFFC9F3FF, 0xF0041018, 0xF001070B),
        DESERT_OBELISK(0xB5792B, 0xFFF0A8, 0xE6BE5B,
                0xFFD09135, 0xFFFFE9A2, 0xF01B1003, 0xF00A0501),
        ISLAND_OBELISK(0x278875, 0xC9FFE7, 0x65D6B2,
                0xFF2E9F87, 0xFFC8FFE9, 0xF0031510, 0xF0010806),
        GOLD_CLOCK(0xA57818, 0xFFF2A1, 0xE8C94C,
                0xFFD5A51E, 0xFFFFE995, 0xF01C1302, 0xF00A0701);

        private final int nameBaseRgb;
        private final int nameHighlightRgb;
        private final int effectRgb;
        private final int borderBaseArgb;
        private final int borderHighlightArgb;
        private final int backgroundStartArgb;
        private final int backgroundEndArgb;

        Kind(int nameBaseRgb, int nameHighlightRgb, int effectRgb,
             int borderBaseArgb, int borderHighlightArgb,
             int backgroundStartArgb, int backgroundEndArgb) {
            this.nameBaseRgb = nameBaseRgb;
            this.nameHighlightRgb = nameHighlightRgb;
            this.effectRgb = effectRgb;
            this.borderBaseArgb = borderBaseArgb;
            this.borderHighlightArgb = borderHighlightArgb;
            this.backgroundStartArgb = backgroundStartArgb;
            this.backgroundEndArgb = backgroundEndArgb;
        }

        public int nameBaseRgb() { return nameBaseRgb; }
        public int nameHighlightRgb() { return nameHighlightRgb; }
        public int effectRgb() { return effectRgb; }
        public int borderBaseArgb() { return borderBaseArgb; }
        public int borderHighlightArgb() { return borderHighlightArgb; }
        public int backgroundStartArgb() { return backgroundStartArgb; }
        public int backgroundEndArgb() { return backgroundEndArgb; }
    }
}

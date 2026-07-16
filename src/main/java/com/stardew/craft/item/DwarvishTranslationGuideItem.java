package com.stardew.craft.item;

import com.stardew.craft.book.BookService;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.shop.DwarfService;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Dwarvish Translation Guide — permanent special item.
 */
public class DwarvishTranslationGuideItem extends Item implements IStardewItem {
    private static final int USE_DURATION_TICKS = 21;

    private static final float NAME_SWEEP_SPEED = 0.36F;
    private static final float NAME_SWEEP_WIDTH = 0.26F;
    private static final int NAME_BASE_RGB = 0x8B5A2B;
    private static final int NAME_HIGHLIGHT_RGB = 0xE4B77A;

    public DwarvishTranslationGuideItem(Properties properties) {
        super(properties);
    }

    @Override
    public String getItemTypeKey() {
        return "stardewcraft.type.special";
    }

    @Override
    public int getSellPrice(ItemStack stack) {
        return -1; // not sellable
    }

    @SuppressWarnings("null")
    @Override
    public InteractionResultHolder<ItemStack> use(@Nonnull Level level, @Nonnull Player player, @Nonnull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            int token = BookService.startReadingVisual(serverPlayer, USE_DURATION_TICKS);
            com.stardew.craft.time.StardewSimulationTaskScheduler.schedule(
                    serverPlayer.serverLevel(), USE_DURATION_TICKS,
                    () -> {
                        if (serverPlayer.isRemoved() || !BookService.isReadingTokenActive(serverPlayer, token)) {
                            return;
                        }
                        ItemStack current = serverPlayer.getItemInHand(hand);
                        if (current.is(this)) {
                            finishUsingItem(current, serverPlayer.level(), serverPlayer);
                        } else {
                            BookService.finishReadingVisual(serverPlayer);
                        }
                    });
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(@Nonnull ItemStack stack, @Nonnull LivingEntity entity) {
        return USE_DURATION_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(@Nonnull ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public @Nonnull ItemStack finishUsingItem(@Nonnull ItemStack stack, @Nonnull Level level,
                                              @Nonnull LivingEntity livingEntity) {
        if (!level.isClientSide && livingEntity instanceof ServerPlayer sp) {
            BookService.finishReadingVisual(sp);
            PlayerStardewData data = PlayerDataManager.getPlayerData(sp);
            boolean changed = false;
            if (!data.hasMailFlag(DwarfService.MAIL_FLAG)) {
                data.addMailFlag(DwarfService.MAIL_FLAG);
                changed = true;
            }
            if (!data.hasSpecialItem(DwarfService.SPECIAL_ITEM_ID)) {
                data.addSpecialItem(DwarfService.SPECIAL_ITEM_ID);
                changed = true;
            }
            if (changed) {
                PlayerDataManager.get().savePlayerData(sp.getUUID(), data);
                PlayerDataEventHandler.syncPlayerData(sp, data);
                sp.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                        "stardewcraft.item.dwarvish_translation_guide.learned"));
            } else {
                sp.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                        "stardewcraft.item.dwarvish_translation_guide.already_learned"));
            }
            sp.awardStat(Stats.ITEM_USED.get(this));
        }
        return stack;
    }

    @Override
    public boolean isFoil(@Nonnull ItemStack stack) {
        return true;
    }

    @Override
    public boolean canBeHurtBy(@Nonnull ItemStack stack, @Nonnull net.minecraft.world.damagesource.DamageSource source) {
        return false;
    }

    @Override
    public Component getName(@Nonnull ItemStack stack) {
        String raw = Component.translatable(this.getDescriptionId(stack)).getString();
        return sweepHighlight(raw, NAME_BASE_RGB, NAME_HIGHLIGHT_RGB, NAME_SWEEP_SPEED, NAME_SWEEP_WIDTH, true);
    }

    @Override
    public void appendHoverText(@Nonnull ItemStack stack, @Nonnull TooltipContext context,
                                @Nonnull List<Component> tooltipComponents, @Nonnull TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("stardewcraft.item.dwarvish_translation_guide.tooltip.flavor")
            .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x9B6A3A))));
        tooltipComponents.add(Component.translatable("stardewcraft.item.dwarvish_translation_guide.tooltip.granted")
            .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xD8A15F)).withBold(true)));
    }

    private static MutableComponent sweepHighlight(String raw, int baseRgb, int hiRgb,
                                                   float speedPerSec, float halfWidth, boolean bold) {
        long ms = System.currentTimeMillis();
        float t = (ms % 60_000L) / 1000.0F;
        float span = 1.0F + halfWidth * 2.0F;
        float pos = ((t * speedPerSec) % span) - halfWidth;

        int n = raw.length();
        MutableComponent out = Component.empty();
        for (int i = 0; i < n; i++) {
            float u = n > 1 ? (float) i / (n - 1) : 0.5F;
            float dist = Math.abs(u - pos);
            float k = Math.max(0.0F, 1.0F - dist / halfWidth);
            k = k * k * (3.0F - 2.0F * k);
            int rgb = lerpRgb(baseRgb, hiRgb, k);
            Style style = Style.EMPTY.withColor(TextColor.fromRgb(rgb));
            if (bold) {
                style = style.withBold(true);
            }
            out.append(Component.literal(String.valueOf(raw.charAt(i))).withStyle(style));
        }
        return out;
    }

    private static int lerpRgb(int a, int b, float k) {
        k = Mth.clamp(k, 0.0F, 1.0F);
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;
        int r = Math.round(ar + (br - ar) * k);
        int g = Math.round(ag + (bg - ag) * k);
        int bl = Math.round(ab + (bb - ab) * k);
        return (r << 16) | (g << 8) | bl;
    }
}
